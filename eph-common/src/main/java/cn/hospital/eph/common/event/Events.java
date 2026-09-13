package cn.hospital.eph.common.event;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

/** 跨服务事件契约（事件携带业务快照，消费方无需回查） */
public final class Events {
    private Events() {
    }

    public record EventEnvelope(String eventId, String eventType, String bizKey, Long aggregateId,
                                Object payload, OffsetDateTime occurredAt) implements Serializable {
    }

    public record RxItem(String drugCode, String drugName, String spec, String dosageForm,
                         String qty, String unit, String singleDose, String frequency,
                         String administrationRoute, Integer days, Integer skinTestFlag) implements Serializable {
    }

    /** rx.submitted：启动审方流程所需最小信息 + 医生签名原文（药师签名需基于同一原文构造） */
    public record RxSubmitted(String rxNo, int rxVersion, Long doctorId, String doctorName,
                              Long patientId, String patientName, Integer rxCategory,
                              String canonicalPayload, String payloadSha256,
                              String doctorSignature, String doctorCertSerial) implements Serializable {
    }

    /** rx.approved / rx.rejected / rx.amendment.requested */
    public record RxReviewDecided(String rxNo, int rxVersion, String decision, String comment,
                                  Long pharmacistId, String pharmacistName,
                                  String pharmacistCertSerial, String pharmacistCertPem,
                                  String pharmacistSignature, String canonicalPayload,
                                  String pharmacistCanonical, String payloadSha256,
                                  String reviewedAt) implements Serializable {
    }

    /** rx.effective：派药所需完整快照 */
    public record RxEffective(String rxNo, int rxVersion, Long patientId, String patientName,
                              String patientPhone, Integer patientAge, Integer patientGender,
                              Long doctorId, String doctorName, String deptName,
                              Integer rxCategory, List<RxItem> items,
                              String effectiveAt, String expireAt) implements Serializable {
    }

    /** fulfillment.status：履约状态回传到处方服务/通知 */
    public record FulfillmentStatus(String rxNo, String orderNo, Long pharmacyId, String pharmacyCode,
                                    String pharmacyName, String status, String statusText,
                                    String externalEventId, String occurredAt) implements Serializable {
    }
}
