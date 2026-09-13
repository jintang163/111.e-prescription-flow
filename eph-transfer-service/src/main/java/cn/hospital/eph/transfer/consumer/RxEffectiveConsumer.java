package cn.hospital.eph.transfer.consumer;

import cn.hospital.eph.common.event.Events;
import cn.hospital.eph.common.mq.InboxService;
import cn.hospital.eph.common.mq.MqTopology;
import cn.hospital.eph.transfer.service.OrderDispatchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

/** 消费处方生效事件，触发派单 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RxEffectiveConsumer {

    private static final String HANDLER = "transfer-rx-effective";

    private final InboxService inboxService;
    private final OrderDispatchService dispatchService;
    private final TransactionTemplate txTemplate;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = MqTopology.Q_TRANSFER_DISPATCH, concurrency = "1")
    public void onMessage(Map<String, Object> msg) {
        String eventId = (String) msg.get("eventId");
        if (!inboxService.firstTime(eventId, HANDLER)) {
            return;
        }
        Events.RxEffective rx = objectMapper.convertValue(msg.get("payload"), Events.RxEffective.class);
        try {
            txTemplate.executeWithoutResult(t -> dispatchService.dispatch(rx));
        } catch (Exception e) {
            log.error("派单处理失败 rxNo={}，将由重试/对账兜底", rx.rxNo(), e);
            throw e;
        }
    }
}
