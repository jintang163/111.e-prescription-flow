package cn.hospital.eph.prescription.consumer;

import cn.hospital.eph.common.canonical.CanonicalPayloadBuilder;
import cn.hospital.eph.common.crypto.CryptoSupport;
import cn.hospital.eph.common.enums.FulfillmentStatus;
import cn.hospital.eph.common.enums.ReviewDecision;
import cn.hospital.eph.common.enums.RxStatus;
import cn.hospital.eph.common.enums.SignerRole;
import cn.hospital.eph.common.enums.SignatureStatus;
import cn.hospital.eph.common.event.Events;
import cn.hospital.eph.common.mq.InboxService;
import cn.hospital.eph.common.mq.MqTopology;
import cn.hospital.eph.common.mq.OutboxService;
import cn.hospital.eph.common.web.BizException;
import cn.hospital.eph.common.web.ErrorCode;
import cn.hospital.eph.prescription.entity.Prescription;
import cn.hospital.eph.prescription.entity.PrescriptionDiagnosis;
import cn.hospital.eph.prescription.entity.PrescriptionItem;
import cn.hospital.eph.prescription.entity.ReviewRecord;
import cn.hospital.eph.prescription.entity.RxStatusLog;
import cn.hospital.eph.prescription.entity.SignatureRecord;
import cn.hospital.eph.prescription.mapper.PrescriptionMapper;
import cn.hospital.eph.prescription.mapper.ReviewRecordMapper;
import cn.hospital.eph.prescription.mapper.SignatureRecordMapper;
import cn.hospital.eph.prescription.pdf.PdfService;
import cn.hospital.eph.prescription.service.PrescriptionService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** 消费审方决定事件：驳回/补正落状态；通过则校验双签后置 EFFECTIVE 并发 rx.effective */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewDecisionConsumer {

    private static final String HANDLER = "prescription-review-decision";

    private final InboxService inboxService;
    private final PrescriptionMapper rxMapper;
    private final SignatureRecordMapper signatureMapper;
    private final ReviewRecordMapper reviewRecordMapper;
    private final OutboxService outboxService;
    private final PrescriptionService prescriptionService;
    private final PdfService pdfService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;

    @Value("${eph.rx.expire-days:3}")
    private int expireDays;

    @RabbitListener(queues = MqTopology.Q_PRESCRIPTION_REVIEW, concurrency = "1")
    public void onMessage(Map<String, Object> msg) {
        String eventId = (String) msg.get("eventId");
        String eventType = (String) msg.get("eventType");
        if (!inboxService.firstTime(eventId, HANDLER)) {
            return;
        }
        Events.RxReviewDecided d = objectMapper.convertValue(msg.get("payload"), Events.RxReviewDecided.class);
        switch (eventType) {
            case MqTopology.RK_RX_APPROVED -> txTemplate.executeWithoutResult(s -> approve(d));
            case MqTopology.RK_RX_REJECTED -> txTemplate.executeWithoutResult(s -> reject(d));
            case MqTopology.RK_RX_AMENDMENT_REQUESTED -> txTemplate.executeWithoutResult(s -> requestAmendment(d));
            default -> log.warn("未知审方事件类型: {}", eventType);
        }
    }

    public void approve(Events.RxReviewDecided d) {
        Prescription rx = prescriptionService.mustGet(d.rxNo());
        if (RxStatus.EFFECTIVE.name().equals(rx.getRxStatus())) {
            return; // 重复投递
        }
        if (!rx.getRxVersion().equals(d.rxVersion())) {
            throw new BizException(ErrorCode.RX_VERSION_CONFLICT, "审方事件版本与处方不一致");
        }
        // 校验药师密码学签名（验签不过绝不生效）
        boolean sigOk = CryptoSupport.verifySha256Rsa(
                CryptoSupport.loadX509PublicKey(PrescriptionService.extractPublicKeyBase64(d.pharmacistCertPem())),
                d.pharmacistCanonical().getBytes(StandardCharsets.UTF_8),
                Base64.getDecoder().decode(d.pharmacistSignature()));
        if (!sigOk) {
            log.error("药师签名验签失败 rxNo={}，处方不得生效", d.rxNo());
            throw new BizException(ErrorCode.SIGN_INVALID, "药师签名验签失败");
        }
        // 校验同版本医生签名也存在且 ACTIVE
        SignatureRecord doctorSig = signatureMapper.selectOne(new QueryWrapper<SignatureRecord>()
                .eq("rx_id", rx.getId()).eq("rx_version", d.rxVersion())
                .eq("signer_role", SignerRole.DOCTOR.name())
                .eq("sign_status", SignatureStatus.ACTIVE.name()).last("limit 1"));
        if (doctorSig == null) {
            throw new BizException(ErrorCode.SIGN_INVALID, "缺少医生签名，双签不成立");
        }

        SignatureRecord ps = new SignatureRecord();
        ps.setRxId(rx.getId());
        ps.setRxNo(rx.getRxNo());
        ps.setRxVersion(d.rxVersion());
        ps.setSignerId(d.pharmacistId());
        ps.setSignerName(d.pharmacistName());
        ps.setSignerRole(SignerRole.PHARMACIST.name());
        ps.setCertSerial(d.pharmacistCertSerial());
        ps.setCertPemSnapshot(d.pharmacistCertPem());
        ps.setAlg("SHA256withRSA");
        ps.setCanonicalPayload(d.pharmacistCanonical());
        ps.setPayloadSha256(CryptoSupport.sha256Hex(d.pharmacistCanonical()));
        ps.setSignatureValue(d.pharmacistSignature());
        ps.setSignStatus(SignatureStatus.ACTIVE.name());
        ps.setFlowAction("APPROVE");
        ps.setSignTime(LocalDateTime.now());
        ps.setCreatedAt(LocalDateTime.now());
        signatureMapper.insert(ps);

        saveReviewRecord(rx, d);

        LocalDateTime now = LocalDateTime.now();
        prescriptionService.transition(rx, RxStatus.EFFECTIVE, d.pharmacistId(), "PHARMACIST",
                "APPROVE", d.comment());
        rx.setReviewStatus("APPROVED");
        rx.setPharmacistSigned(1);
        rx.setEffectiveAt(now);
        rx.setExpireAt(now.plusDays(expireDays));
        rxMapper.updateById(rx);

        Events.RxEffective effective = buildEffectiveEvent(rx);
        outboxService.enlist(MqTopology.RK_RX_EFFECTIVE, rx.getRxNo(), rx.getId(), effective);
        log.info("处方双签生效 rxNo={} version={}", rx.getRxNo(), rx.getRxVersion());

        // PDF 在事务提交后异步生成（失败不影响法律生效，pdf_status 独立可重试）
        pdfService.generateAsync(rx.getRxNo());
    }

    public void reject(Events.RxReviewDecided d) {
        Prescription rx = prescriptionService.mustGet(d.rxNo());
        if (RxStatus.REJECTED.name().equals(rx.getRxStatus())) {
            return;
        }
        saveReviewRecord(rx, d);
        prescriptionService.transition(rx, RxStatus.REJECTED, d.pharmacistId(), "PHARMACIST",
                "REJECT", d.comment());
        rx.setReviewStatus("REJECTED");
        rx.setRejectReason(d.comment());
        rxMapper.updateById(rx);
    }

    public void requestAmendment(Events.RxReviewDecided d) {
        Prescription rx = prescriptionService.mustGet(d.rxNo());
        if (RxStatus.AMENDMENT_REQUESTED.name().equals(rx.getRxStatus())) {
            return;
        }
        saveReviewRecord(rx, d);
        prescriptionService.transition(rx, RxStatus.AMENDMENT_REQUESTED, d.pharmacistId(), "PHARMACIST",
                "REQUEST_AMENDMENT", d.comment());
        rx.setReviewStatus("AMENDMENT");
        rx.setRejectReason(d.comment());
        rxMapper.updateById(rx);
    }

    private void saveReviewRecord(Prescription rx, Events.RxReviewDecided d) {
        ReviewRecord rec = new ReviewRecord();
        rec.setRxId(rx.getId());
        rec.setRxNo(rx.getRxNo());
        rec.setRxVersion(d.rxVersion());
        rec.setPharmacistId(d.pharmacistId());
        rec.setPharmacistName(d.pharmacistName());
        rec.setDecision(d.decision());
        rec.setComment(d.comment());
        rec.setCreatedAt(LocalDateTime.now());
        reviewRecordMapper.insert(rec);
    }

    private Events.RxEffective buildEffectiveEvent(Prescription rx) {
        List<PrescriptionItem> items = prescriptionService.listItems(rx.getId());
        List<PrescriptionDiagnosis> diagnoses = prescriptionService.listDiagnoses(rx.getId());
        String summary = diagnoses.stream()
                .map(PrescriptionDiagnosis::getDiagnosisName)
                .reduce((a, b) -> a + "；" + b).orElse("");
        List<Events.RxItem> eventItems = items.stream().map(it -> new Events.RxItem(
                it.getDrugCode(), it.getDrugName(), it.getSpec(), it.getDosageForm(),
                it.getQty().toPlainString(), it.getUnit(), it.getSingleDose(), it.getFrequency(),
                it.getAdministrationRoute(), it.getDays(), it.getSkinTestFlag())).toList();
        return new Events.RxEffective(
                rx.getRxNo(), rx.getRxVersion(), rx.getPatientId(), rx.getPatientName(),
                rx.getPatientPhone(), rx.getPatientAge(), rx.getPatientGender(),
                rx.getDoctorId(), rx.getDoctorName(), rx.getDeptName(), rx.getRxCategory(),
                eventItems,
                rx.getEffectiveAt().atOffset(ZoneOffset.ofHours(8)).toString(),
                rx.getExpireAt().atOffset(ZoneOffset.ofHours(8)).toString());
    }
}
