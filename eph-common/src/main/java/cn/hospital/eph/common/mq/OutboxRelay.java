package cn.hospital.eph.common.mq;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 定时扫描 NEW 发件箱事件并发布（publisher confirm 由 CachingConnectionFactory 开启后保障） */
@Slf4j
@RequiredArgsConstructor
public class OutboxRelay {

    private final RabbitTemplate rabbitTemplate;
    private final OutboxEventMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Scheduled(fixedDelay = 1000)
    public void relay() {
        List<OutboxEvent> pending = mapper.selectList(new QueryWrapper<OutboxEvent>()
                .in("status", "NEW", "DEAD")
                .le("next_retry_at", LocalDateTime.now())
                .orderByAsc("id")
                .last("limit 100"));
        for (OutboxEvent e : pending) {
            try {
                Object payload = objectMapper.readValue(e.getPayload(), Object.class);
                Map<String, Object> message = Map.of(
                        "eventId", e.getEventId(),
                        "eventType", e.getEventType(),
                        "bizKey", e.getBizKey() == null ? "" : e.getBizKey(),
                        "aggregateId", e.getAggregateId() == null ? 0L : e.getAggregateId(),
                        "payload", payload);
                MessagePostProcessor persistent = m -> {
                    m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return m;
                };
                rabbitTemplate.convertAndSend(MqTopology.RX_EXCHANGE, e.getEventType(), message, persistent);
                e.setStatus("PUBLISHED");
                e.setPublishedAt(LocalDateTime.now());
                mapper.updateById(e);
            } catch (Exception ex) {
                int attempts = (e.getPublishAttempts() == null ? 0 : e.getPublishAttempts()) + 1;
                e.setPublishAttempts(attempts);
                e.setNextRetryAt(LocalDateTime.now().plusSeconds(Math.min(60, 5L * attempts)));
                if (attempts >= 10) {
                    e.setStatus("DEAD");
                }
                mapper.updateById(e);
                log.warn("outbox relay failed eventId={} type={} attempts={} err={}",
                        e.getEventId(), e.getEventType(), attempts, ex.getMessage());
            }
        }
    }
}
