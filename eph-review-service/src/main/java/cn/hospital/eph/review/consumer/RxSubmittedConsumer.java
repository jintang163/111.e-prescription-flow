package cn.hospital.eph.review.consumer;

import cn.hospital.eph.common.event.Events;
import cn.hospital.eph.common.mq.InboxService;
import cn.hospital.eph.common.mq.MqTopology;
import cn.hospital.eph.review.service.ReviewFlowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

/** 消费处方提交/重提事件，驱动 Flowable 流程启动与补正循环 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RxSubmittedConsumer {

    private static final String HANDLER = "review-rx-submitted";

    private final InboxService inboxService;
    private final ReviewFlowService flowService;
    private final TransactionTemplate txTemplate;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = MqTopology.Q_REVIEW_RX, concurrency = "1")
    public void onMessage(Map<String, Object> msg) {
        String eventId = (String) msg.get("eventId");
        String eventType = (String) msg.get("eventType");
        if (!inboxService.firstTime(eventId, HANDLER)) {
            return;
        }
        Events.RxSubmitted s = objectMapper.convertValue(msg.get("payload"), Events.RxSubmitted.class);
        if (MqTopology.RK_RX_SUBMITTED.equals(eventType)) {
            txTemplate.executeWithoutResult(t -> flowService.startReview(s));
        } else if (MqTopology.RK_RX_RESUBMITTED.equals(eventType)) {
            txTemplate.executeWithoutResult(t -> flowService.resumeAfterAmendment(s));
        } else {
            log.warn("审方服务忽略未知事件: {}", eventType);
        }
    }
}
