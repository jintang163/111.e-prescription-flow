package cn.hospital.eph.prescription.consumer;

import cn.hospital.eph.common.event.Events;
import cn.hospital.eph.common.mq.MqTopology;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 患者消息推送占位实现（演示环境记录站内通知日志，生产替换为订阅消息/短信/公众号模板） */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationStubConsumer {

    private final ObjectMapper objectMapper;

    @RabbitListener(queues = MqTopology.Q_NOTIFY_FULFILLMENT)
    public void onMessage(Map<String, Object> msg) {
        Events.FulfillmentStatus fs =
                objectMapper.convertValue(msg.get("payload"), Events.FulfillmentStatus.class);
        log.info("[患者通知] 处方 {} 履约状态更新：{}（{}），药店：{}",
                fs.rxNo(), fs.status(), fs.statusText(), fs.pharmacyName());
    }
}
