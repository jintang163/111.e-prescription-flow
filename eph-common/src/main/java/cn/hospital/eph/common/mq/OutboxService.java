package cn.hospital.eph.common.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/** 与业务数据同事务写入发件箱 */
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** @param payload 事件载荷对象，序列化为 JSON 存库 */
    public OutboxEvent enlist(String eventType, String bizKey, Long aggregateId, Object payload) {
        OutboxEvent e = new OutboxEvent();
        e.setEventId(UUID.randomUUID().toString().replace("-", ""));
        e.setEventType(eventType);
        e.setBizKey(bizKey);
        e.setAggregateId(aggregateId);
        try {
            e.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("事件序列化失败: " + eventType, ex);
        }
        e.setStatus("NEW");
        e.setPublishAttempts(0);
        e.setNextRetryAt(LocalDateTime.now());
        e.setCreatedAt(LocalDateTime.now());
        mapper.insert(e);
        return e;
    }
}
