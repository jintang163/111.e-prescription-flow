package cn.hospital.eph.common.mq;

/** RabbitMQ 拓扑：交换机 / 队列 / 路由键（事件类型）常量 */
public final class MqTopology {
    private MqTopology() {
    }

    public static final String RX_EXCHANGE = "eph.rx";
    public static final String DLX_EXCHANGE = "eph.dlx";

    // 路由键 = 事件类型
    public static final String RK_RX_SUBMITTED = "rx.submitted";
    public static final String RK_RX_RESUBMITTED = "rx.resubmitted";
    public static final String RK_RX_APPROVED = "rx.approved";
    public static final String RK_RX_REJECTED = "rx.rejected";
    public static final String RK_RX_AMENDMENT_REQUESTED = "rx.amendment.requested";
    public static final String RK_RX_EFFECTIVE = "rx.effective";
    public static final String RK_FULFILLMENT_STATUS = "fulfillment.status";

    public static final String Q_REVIEW_RX = "q.review.rx";
    public static final String Q_PRESCRIPTION_LIFECYCLE = "q.prescription.lifecycle";
    public static final String Q_PRESCRIPTION_REVIEW = "q.prescription.review";
    public static final String Q_TRANSFER_DISPATCH = "q.transfer.dispatch";
    public static final String Q_PRESCRIPTION_FULFILLMENT = "q.prescription.fulfillment";
    public static final String Q_NOTIFY_FULFILLMENT = "q.notify.fulfillment";

    public static final String HEADER_ATTEMPTS = "x-eph-attempts";
    public static final int MAX_ATTEMPTS = 3;
    public static final long RETRY_TTL_MS = 10_000L;

    /** 一级重试队列名（TTL 到期经 DLX 以原路由键回到主交换机） */
    public static String retryQueue(String mainQueue) {
        return mainQueue + ".retry.10s";
    }

    public static String dlqQueue(String mainQueue) {
        return mainQueue + ".dlq";
    }
}
