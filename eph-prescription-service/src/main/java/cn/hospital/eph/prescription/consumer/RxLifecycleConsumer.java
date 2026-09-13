package cn.hospital.eph.prescription.consumer;

import cn.hospital.eph.common.enums.RxStatus;
import cn.hospital.eph.common.event.Events;
import cn.hospital.eph.common.mq.InboxService;
import cn.hospital.eph.common.mq.MqTopology;
import cn.hospital.eph.prescription.entity.Prescription;
import cn.hospital.eph.prescription.mapper.PrescriptionMapper;
import cn.hospital.eph.prescription.service.PrescriptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

/** 本服务提交后消费自身事件，推进 SUBMITTED → REVIEWING */
@Slf4j
@Component
@RequiredArgsConstructor
public class RxLifecycleConsumer {

    private static final String HANDLER = "prescription-lifecycle";

    private final InboxService inboxService;
    private final PrescriptionService prescriptionService;
    private final PrescriptionMapper rxMapper;
    private final TransactionTemplate txTemplate;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = MqTopology.Q_PRESCRIPTION_LIFECYCLE, concurrency = "1")
    public void onMessage(Map<String, Object> msg) {
        String eventId = (String) msg.get("eventId");
        if (!inboxService.firstTime(eventId, HANDLER)) {
            return;
        }
        Events.RxSubmitted s = objectMapper.convertValue(msg.get("payload"), Events.RxSubmitted.class);
        txTemplate.executeWithoutResult(t -> {
            Prescription rx = prescriptionService.mustGet(s.rxNo());
            if (RxStatus.SUBMITTED.name().equals(rx.getRxStatus())) {
                prescriptionService.transition(rx, RxStatus.REVIEWING, s.doctorId(),
                        "DOCTOR", "ENTER_REVIEW", null);
                rxMapper.updateById(rx);
            }
        });
    }
}
