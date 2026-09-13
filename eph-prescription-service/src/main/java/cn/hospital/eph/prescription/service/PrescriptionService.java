package cn.hospital.eph.prescription.service;

import cn.hospital.eph.common.canonical.CanonicalPayloadBuilder;
import cn.hospital.eph.common.canonical.CanonicalRx;
import cn.hospital.eph.common.crypto.CryptoSupport;
import cn.hospital.eph.common.enums.RxStatus;
import cn.hospital.eph.common.enums.SignatureStatus;
import cn.hospital.eph.common.enums.SignerRole;
import cn.hospital.eph.common.event.Events;
import cn.hospital.eph.common.mq.MqTopology;
import cn.hospital.eph.common.mq.OutboxService;
import cn.hospital.eph.common.security.LoginUser;
import cn.hospital.eph.common.security.SignGrantService;
import cn.hospital.eph.common.web.BizException;
import cn.hospital.eph.common.web.ErrorCode;
import cn.hospital.eph.prescription.api.PrescriptionDtos;
import cn.hospital.eph.prescription.entity.Prescription;
import cn.hospital.eph.prescription.entity.PrescriptionDiagnosis;
import cn.hospital.eph.prescription.entity.PrescriptionItem;
import cn.hospital.eph.prescription.entity.RxStatusLog;
import cn.hospital.eph.prescription.entity.SignatureRecord;
import cn.hospital.eph.prescription.mapper.PrescriptionDiagnosisMapper;
import cn.hospital.eph.prescription.mapper.PrescriptionItemMapper;
import cn.hospital.eph.prescription.mapper.PrescriptionMapper;
import cn.hospital.eph.prescription.mapper.RxStatusLogMapper;
import cn.hospital.eph.prescription.mapper.SignatureRecordMapper;
import cn.hospital.eph.common.security.InternalSignClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** 处方开方、签名提交、状态流转核心服务 */
@Service
@RequiredArgsConstructor
public class PrescriptionService {

    private final PrescriptionMapper rxMapper;
    private final PrescriptionItemMapper itemMapper;
    private final PrescriptionDiagnosisMapper diagnosisMapper;
    private final SignatureRecordMapper signatureMapper;
    private final RxStatusLogMapper logMapper;
    private final OutboxService outboxService;
    private final SignGrantService signGrantService;
    private final InternalSignClient signClient;
    private final StringRedisTemplate redis;

    @Transactional
    public Prescription createDraft(PrescriptionDtos.SaveRequest req, LoginUser doctor) {
        Prescription rx = new Prescription();
        rx.setRxNo(generateRxNo());
        rx.setRxVersion(1);
        applyPatientSnapshot(rx, req);
        rx.setDoctorId(doctor.getUserId());
        rx.setDoctorName(doctor.getRealName());
        rx.setDeptCode(req.getDeptCode());
        rx.setDeptName(req.getDeptName());
        rx.setRxCategory(req.getRxCategory() == null ? 1 : req.getRxCategory());
        rx.setDiagnosisSummary(summarizeDiagnoses(req));
        rx.setRxStatus(RxStatus.DRAFT.name());
        rx.setReviewStatus("NONE");
        rx.setDoctorSigned(0);
        rx.setPharmacistSigned(0);
        rx.setAmendmentCount(0);
        rx.setPdfStatus("NONE");
        rx.setVersion(0);
        LocalDateTime now = LocalDateTime.now();
        rx.setCreatedAt(now);
        rx.setUpdatedAt(now);
        rxMapper.insert(rx);
        replaceChildren(rx, req);
        writeLog(rx, null, RxStatus.DRAFT, doctor.getUserId(), "DOCTOR", "CREATE", null);
        return rx;
    }

    @Transactional
    public Prescription update(String rxNo, PrescriptionDtos.SaveRequest req, LoginUser doctor) {
        Prescription rx = mustGet(rxNo);
        checkOwner(rx, doctor.getUserId());
        RxStatus st = RxStatus.valueOf(rx.getRxStatus());
        // 补正场景修改即升版本，旧签名全部失效，需重新签名
        if (st == RxStatus.AMENDMENT_REQUESTED) {
            supersedeSignatures(rx);
            rx.setRxVersion(rx.getRxVersion() + 1);
            rx.setAmendmentCount(rx.getAmendmentCount() + 1);
            rx.setDoctorSigned(0);
            rx.setPharmacistSigned(0);
            rx.setReviewStatus("AMENDMENT");
        } else if (st != RxStatus.DRAFT) {
            throw new BizException(ErrorCode.RX_STATE_ILLEGAL, "仅草稿/补正状态可修改");
        }
        applyPatientSnapshot(rx, req);
        rx.setDeptCode(req.getDeptCode());
        rx.setDeptName(req.getDeptName());
        if (req.getRxCategory() != null) {
            rx.setRxCategory(req.getRxCategory());
        }
        rx.setDiagnosisSummary(summarizeDiagnoses(req));
        rx.setUpdatedAt(LocalDateTime.now());
        rxMapper.updateById(rx);
        replaceChildren(rx, req);
        return rx;
    }

    /** 医生签名并提交审方 */
    @Transactional
    public SignatureView submit(String rxNo, LoginUser doctor) {
        Prescription rx = mustGet(rxNo);
        checkOwner(rx, doctor.getUserId());
        RxStatus st = RxStatus.valueOf(rx.getRxStatus());
        if (st != RxStatus.DRAFT && st != RxStatus.AMENDMENT_REQUESTED) {
            throw new BizException(ErrorCode.RX_STATE_ILLEGAL, "当前状态不可提交");
        }
        // 一次性签名重认证许可
        signGrantService.requireAndConsume(doctor.getUserId());

        List<PrescriptionItem> items = listItems(rx.getId());
        List<PrescriptionDiagnosis> diagnoses = listDiagnoses(rx.getId());
        CanonicalRx canonical = toCanonical(rx, items, diagnoses);
        String canonicalJson = CanonicalPayloadBuilder.build(canonical);
        String sha = CanonicalPayloadBuilder.sha256(canonicalJson);

        InternalSignClient.SignResult signResult = signClient.sign(doctor.getUserId(), canonicalJson);
        // 本地先用证书快照验签，失败立即中止，不产生无效提交
        boolean valid = CryptoSupport.verifySha256Rsa(
                CryptoSupport.loadX509PublicKey(extractPublicKeyBase64(signResult.certPem())),
                canonicalJson.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                signResult.signatureBytes());
        if (!valid) {
            throw new BizException(ErrorCode.SIGN_INVALID, "医生签名自验失败");
        }

        persistSignature(rx, doctor.getUserId(), doctor.getRealName(), SignerRole.DOCTOR,
                "SUBMIT", canonicalJson, sha, signResult);

        boolean amendment = st == RxStatus.AMENDMENT_REQUESTED;
        transition(rx, RxStatus.SUBMITTED, doctor.getUserId(), "DOCTOR",
                amendment ? "RESUBMIT" : "SUBMIT", null);
        rx.setDoctorSigned(1);
        rx.setReviewStatus("PENDING");
        rx.setRejectReason(null);
        rxMapper.updateById(rx);

        Events.RxSubmitted payload = new Events.RxSubmitted(
                rx.getRxNo(), rx.getRxVersion(), rx.getDoctorId(), rx.getDoctorName(),
                rx.getPatientId(), rx.getPatientName(), rx.getRxCategory(),
                canonicalJson, sha, signResult.signatureBase64(), signResult.certSerial());
        String routingKey = amendment ? MqTopology.RK_RX_RESUBMITTED : MqTopology.RK_RX_SUBMITTED;
        outboxService.enlist(routingKey, rx.getRxNo(), rx.getId(), payload);
        return new SignatureView(signResult.certSerial(), sha, signResult.signatureBase64());
    }

    @Transactional
    public void cancel(String rxNo, LoginUser doctor, String reason) {
        Prescription rx = mustGet(rxNo);
        checkOwner(rx, doctor.getUserId());
        transition(rx, RxStatus.CANCELLED, doctor.getUserId(), "DOCTOR", "CANCEL", reason);
    }

    public Prescription mustGet(String rxNo) {
        Prescription rx = rxMapper.selectOne(new QueryWrapper<Prescription>().eq("rx_no", rxNo).last("limit 1"));
        if (rx == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "处方不存在: " + rxNo);
        }
        return rx;
    }

    public List<PrescriptionItem> listItems(long rxId) {
        return itemMapper.selectList(new QueryWrapper<PrescriptionItem>().eq("rx_id", rxId).orderByAsc("seq"));
    }

    public List<PrescriptionDiagnosis> listDiagnoses(long rxId) {
        return diagnosisMapper.selectList(new QueryWrapper<PrescriptionDiagnosis>().eq("rx_id", rxId).orderByAsc("seq"));
    }

    public List<SignatureRecord> listSignatures(long rxId) {
        return signatureMapper.selectList(new QueryWrapper<SignatureRecord>().eq("rx_id", rxId).orderByAsc("id"));
    }

    public List<RxStatusLog> listLogs(long rxId) {
        return logMapper.selectList(new QueryWrapper<RxStatusLog>().eq("rx_id", rxId).orderByAsc("id"));
    }

    public String generateRxNo() {
        String day = LocalDateTime.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Long seq = redis.opsForValue().increment("rx:seq:" + day);
        return "RX" + day + String.format("%06d", seq == null ? 1 : seq);
    }

    public void transition(Prescription rx, RxStatus target, Long actorId, String actorRole,
                           String action, String comment) {
        RxStatus from = RxStatus.valueOf(rx.getRxStatus());
        if (!from.canTransitionTo(target)) {
            throw new BizException(ErrorCode.RX_STATE_ILLEGAL, from + " → " + target);
        }
        rx.setRxStatus(target.name());
        rx.setUpdatedAt(LocalDateTime.now());
        int rows = rxMapper.updateById(rx);
        if (rows == 0) {
            throw new BizException(ErrorCode.RX_VERSION_CONFLICT);
        }
        writeLog(rx, from, target, actorId, actorRole, action, comment);
    }

    private void writeLog(Prescription rx, RxStatus from, RxStatus to, Long actorId,
                          String actorRole, String action, String comment) {
        RxStatusLog l = new RxStatusLog();
        l.setRxId(rx.getId());
        l.setRxNo(rx.getRxNo());
        l.setFromStatus(from == null ? null : from.name());
        l.setToStatus(to.name());
        l.setActorId(actorId);
        l.setActorRole(actorRole);
        l.setAction(action);
        l.setComment(comment);
        l.setCreatedAt(LocalDateTime.now());
        logMapper.insert(l);
    }

    private void persistSignature(Prescription rx, long signerId, String signerName, SignerRole role,
                                  String flowAction, String canonicalJson, String sha,
                                  InternalSignClient.SignResult sr) {
        SignatureRecord rec = new SignatureRecord();
        rec.setRxId(rx.getId());
        rec.setRxNo(rx.getRxNo());
        rec.setRxVersion(rx.getRxVersion());
        rec.setSignerId(signerId);
        rec.setSignerName(signerName);
        rec.setSignerRole(role.name());
        rec.setCertSerial(sr.certSerial());
        rec.setCertPemSnapshot(sr.certPem());
        rec.setAlg("SHA256withRSA");
        rec.setCanonicalPayload(canonicalJson);
        rec.setPayloadSha256(sha);
        rec.setSignatureValue(sr.signatureBase64());
        rec.setSignStatus(SignatureStatus.ACTIVE.name());
        rec.setFlowAction(flowAction);
        rec.setSignTime(LocalDateTime.now());
        rec.setCreatedAt(LocalDateTime.now());
        signatureMapper.insert(rec);
    }

    private void supersedeSignatures(Prescription rx) {
        List<SignatureRecord> recs = signatureMapper.selectList(new QueryWrapper<SignatureRecord>()
                .eq("rx_id", rx.getId())
                .eq("rx_version", rx.getRxVersion())
                .eq("sign_status", SignatureStatus.ACTIVE.name()));
        for (SignatureRecord r : recs) {
            r.setSignStatus(SignatureStatus.SUPERSEDED.name());
            signatureMapper.updateById(r);
        }
    }

    private void checkOwner(Prescription rx, long doctorId) {
        if (!rx.getDoctorId().equals(doctorId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能操作本人开具的处方");
        }
    }

    private void applyPatientSnapshot(Prescription rx, PrescriptionDtos.SaveRequest req) {
        rx.setPatientId(req.getPatientId());
        rx.setPatientName(req.getPatientName());
        rx.setPatientIdCardMask(req.getPatientIdCardMask());
        rx.setPatientAge(req.getPatientAge());
        rx.setPatientGender(req.getPatientGender());
        rx.setPatientPhone(req.getPatientPhone());
    }

    private String summarizeDiagnoses(PrescriptionDtos.SaveRequest req) {
        return req.getDiagnoses().stream()
                .map(PrescriptionDtos.DiagnosisDto::getDiagnosisName)
                .reduce((a, b) -> a + "；" + b).orElse("");
    }

    private void replaceChildren(Prescription rx, PrescriptionDtos.SaveRequest req) {
        itemMapper.delete(new QueryWrapper<PrescriptionItem>().eq("rx_id", rx.getId()));
        diagnosisMapper.delete(new QueryWrapper<PrescriptionDiagnosis>().eq("rx_id", rx.getId()));
        int i = 1;
        for (PrescriptionDtos.DiagnosisDto d : req.getDiagnoses()) {
            PrescriptionDiagnosis e = new PrescriptionDiagnosis();
            e.setRxId(rx.getId());
            e.setSeq(d.getSeq() == null ? i : d.getSeq());
            e.setIcd10Code(d.getIcd10Code());
            e.setDiagnosisName(d.getDiagnosisName());
            diagnosisMapper.insert(e);
            i++;
        }
        i = 1;
        for (PrescriptionDtos.ItemDto it : req.getItems()) {
            PrescriptionItem e = new PrescriptionItem();
            e.setRxId(rx.getId());
            e.setSeq(it.getSeq() == null ? i : it.getSeq());
            e.setDrugCode(it.getDrugCode());
            e.setDrugName(it.getDrugName());
            e.setSpec(it.getSpec());
            e.setDosageForm(it.getDosageForm());
            e.setQty(it.getQty());
            e.setUnit(it.getUnit());
            e.setSingleDose(it.getSingleDose());
            e.setDoseUnit(it.getDoseUnit());
            e.setFrequency(it.getFrequency());
            e.setAdministrationRoute(it.getAdministrationRoute());
            e.setDays(it.getDays());
            e.setSkinTestFlag(it.getSkinTestFlag() == null ? 0 : it.getSkinTestFlag());
            e.setRemark(it.getRemark());
            itemMapper.insert(e);
            i++;
        }
    }

    public CanonicalRx toCanonical(Prescription rx, List<PrescriptionItem> items,
                                   List<PrescriptionDiagnosis> diagnoses) {
        List<CanonicalRx.Diagnosis> dx = new ArrayList<>();
        for (PrescriptionDiagnosis d : diagnoses) {
            dx.add(new CanonicalRx.Diagnosis(d.getSeq(), d.getIcd10Code(), d.getDiagnosisName()));
        }
        List<CanonicalRx.Item> its = new ArrayList<>();
        for (PrescriptionItem it : items) {
            its.add(new CanonicalRx.Item(it.getSeq(), it.getDrugCode(), it.getDrugName(), it.getSpec(),
                    it.getDosageForm(), it.getQty().toPlainString(), it.getUnit(), it.getSingleDose(),
                    it.getFrequency(), it.getAdministrationRoute(), it.getDays(), it.getSkinTestFlag()));
        }
        String category = switch (rx.getRxCategory() == null ? 1 : rx.getRxCategory()) {
            case 2 -> "EMERGENCY";
            case 3 -> "PEDIATRIC";
            case 4 -> "NARCOTIC";
            default -> "NORMAL";
        };
        String createdAtIso = rx.getCreatedAt().atOffset(ZoneOffset.ofHours(8)).toString();
        return new CanonicalRx(rx.getRxNo(), rx.getRxVersion(), category,
                new CanonicalRx.Patient(rx.getPatientId(), rx.getPatientName(), rx.getPatientIdCardMask(),
                        rx.getPatientAge(), rx.getPatientGender()),
                new CanonicalRx.Doctor(rx.getDoctorId(), rx.getDoctorName(), rx.getDeptCode(), rx.getDeptName()),
                dx, its, createdAtIso);
    }

    /** 从 PEM 证书中提取 X.509 SubjectPublicKeyInfo 的 Base64 */
    public static String extractPublicKeyBase64(String certPem) {
        try {
            String b64 = certPem.replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
            byte[] der = java.util.Base64.getDecoder().decode(b64);
            java.security.cert.X509Certificate cert = (java.security.cert.X509Certificate)
                    java.security.cert.CertificateFactory.getInstance("X.509")
                            .generateCertificate(new java.io.ByteArrayInputStream(der));
            return java.util.Base64.getEncoder().encodeToString(cert.getPublicKey().getEncoded());
        } catch (Exception e) {
            throw new IllegalArgumentException("证书解析失败", e);
        }
    }

    public record SignatureView(String certSerial, String payloadSha256, String signatureBase64) {
    }
}
