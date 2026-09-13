package cn.hospital.eph.common.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static cn.hospital.eph.common.mq.MqTopology.*;

/** 全局拓扑（幂等声明，各服务启动均执行一次）；消费失败经容器 Spring Retry 后进 DLQ */
@Configuration
public class MqTopologyConfig {

    @Bean
    TopicExchange rxExchange() {
        return new TopicExchange(RX_EXCHANGE, true, false);
    }

    @Bean
    TopicExchange dlxExchange() {
        return new TopicExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    Queue reviewRxQueue() {
        return mainQueue(Q_REVIEW_RX);
    }

    @Bean
    Queue prescriptionReviewQueue() {
        return mainQueue(Q_PRESCRIPTION_REVIEW);
    }

    @Bean
    Queue prescriptionLifecycleQueue() {
        return mainQueue(Q_PRESCRIPTION_LIFECYCLE);
    }

    @Bean
    Queue transferDispatchQueue() {
        return mainQueue(Q_TRANSFER_DISPATCH);
    }

    @Bean
    Queue prescriptionFulfillmentQueue() {
        return mainQueue(Q_PRESCRIPTION_FULFILLMENT);
    }

    @Bean
    Queue prescriptionLifecycleDlq() {
        return QueueBuilder.durable(dlqQueue(Q_PRESCRIPTION_LIFECYCLE)).build();
    }

    @Bean
    Queue notifyFulfillmentQueue() {
        return mainQueue(Q_NOTIFY_FULFILLMENT);
    }

    private Queue mainQueue(String name) {
        return QueueBuilder.durable(name)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", dlqQueue(name))
                .build();
    }

    private Queue dlqBean(String name) {
        return QueueBuilder.durable(dlqQueue(name)).build();
    }

    @Bean
    Queue reviewRxDlq() {
        return dlqBean(Q_REVIEW_RX);
    }

    @Bean
    Queue prescriptionReviewDlq() {
        return dlqBean(Q_PRESCRIPTION_REVIEW);
    }

    @Bean
    Queue transferDispatchDlq() {
        return dlqBean(Q_TRANSFER_DISPATCH);
    }

    @Bean
    Queue prescriptionFulfillmentDlq() {
        return dlqBean(Q_PRESCRIPTION_FULFILLMENT);
    }

    @Bean
    Queue notifyFulfillmentDlq() {
        return dlqBean(Q_NOTIFY_FULFILLMENT);
    }

    @Bean
    Binding bReviewRx() {
        return BindingBuilder.bind(reviewRxQueue()).to(rxExchange()).with(RK_RX_SUBMITTED);
    }

    @Bean
    Binding bReviewRxResubmitted() {
        return BindingBuilder.bind(reviewRxQueue()).to(rxExchange()).with(RK_RX_RESUBMITTED);
    }

    @Bean
    Binding bLifecycleSubmitted() {
        return BindingBuilder.bind(prescriptionLifecycleQueue()).to(rxExchange()).with(RK_RX_SUBMITTED);
    }

    @Bean
    Binding bLifecycleResubmitted() {
        return BindingBuilder.bind(prescriptionLifecycleQueue()).to(rxExchange()).with(RK_RX_RESUBMITTED);
    }

    @Bean
    Binding dlbLifecycle() {
        return BindingBuilder.bind(QueueBuilder.durable(dlqQueue(Q_PRESCRIPTION_LIFECYCLE)).build())
                .to(dlxExchange()).with(dlqQueue(Q_PRESCRIPTION_LIFECYCLE));
    }

    @Bean
    Binding bPrescriptionReviewApproved() {
        return BindingBuilder.bind(prescriptionReviewQueue()).to(rxExchange()).with(RK_RX_APPROVED);
    }

    @Bean
    Binding bPrescriptionReviewRejected() {
        return BindingBuilder.bind(prescriptionReviewQueue()).to(rxExchange()).with(RK_RX_REJECTED);
    }

    @Bean
    Binding bPrescriptionReviewAmendment() {
        return BindingBuilder.bind(prescriptionReviewQueue()).to(rxExchange()).with(RK_RX_AMENDMENT_REQUESTED);
    }

    @Bean
    Binding bTransferDispatch() {
        return BindingBuilder.bind(transferDispatchQueue()).to(rxExchange()).with(RK_RX_EFFECTIVE);
    }

    @Bean
    Binding bPrescriptionFulfillment() {
        return BindingBuilder.bind(prescriptionFulfillmentQueue()).to(rxExchange()).with(RK_FULFILLMENT_STATUS);
    }

    @Bean
    Binding bNotifyFulfillment() {
        return BindingBuilder.bind(notifyFulfillmentQueue()).to(rxExchange()).with(RK_FULFILLMENT_STATUS);
    }

    @Bean
    Binding dlbReviewRx() {
        return BindingBuilder.bind(reviewRxDlq()).to(dlxExchange()).with(dlqQueue(Q_REVIEW_RX));
    }

    @Bean
    Binding dlbPrescriptionReview() {
        return BindingBuilder.bind(prescriptionReviewDlq()).to(dlxExchange()).with(dlqQueue(Q_PRESCRIPTION_REVIEW));
    }

    @Bean
    Binding dlbTransferDispatch() {
        return BindingBuilder.bind(transferDispatchDlq()).to(dlxExchange()).with(dlqQueue(Q_TRANSFER_DISPATCH));
    }

    @Bean
    Binding dlbPrescriptionFulfillment() {
        return BindingBuilder.bind(prescriptionFulfillmentDlq()).to(dlxExchange()).with(dlqQueue(Q_PRESCRIPTION_FULFILLMENT));
    }

    @Bean
    Binding dlbNotifyFulfillment() {
        return BindingBuilder.bind(notifyFulfillmentDlq()).to(dlxExchange()).with(dlqQueue(Q_NOTIFY_FULFILLMENT));
    }
}
