package cn.hospital.eph.prescription.pdf;

import cn.hospital.eph.common.crypto.CryptoSupport;
import cn.hospital.eph.prescription.entity.Prescription;
import cn.hospital.eph.prescription.entity.PrescriptionDiagnosis;
import cn.hospital.eph.prescription.entity.PrescriptionItem;
import cn.hospital.eph.prescription.entity.SignatureRecord;
import cn.hospital.eph.prescription.service.PrescriptionService;
import cn.hospital.eph.prescription.sign.PdfSignClient;
import cn.hospital.eph.prescription.storage.StorageService;
import cn.hospital.eph.prescription.mapper.PrescriptionMapper;
import cn.hospital.eph.prescription.mapper.SignatureRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/** 处方 PDF：渲染 → 医生/药师两次 PAdES 数字签章（私钥在 user-service 托管）→ 存 OSS */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfService {

    private final PrescriptionMapper rxMapper;
    private final SignatureRecordMapper signatureMapper;
    private final PrescriptionService prescriptionService;
    private final PdfRenderer renderer;
    private final PdfSignClient signClient;
    private final StorageService storage;

    @Async
    public void generateAsync(String rxNo) {
        try {
            generate(rxNo);
        } catch (Exception e) {
            log.error("PDF 生成失败 rxNo={}（可重试，不影响处方生效）", rxNo, e);
        }
    }

    public void generate(String rxNo) {
        Prescription rx = prescriptionService.mustGet(rxNo);
        List<PrescriptionItem> items = prescriptionService.listItems(rx.getId());
        List<PrescriptionDiagnosis> diagnoses = prescriptionService.listDiagnoses(rx.getId());
        List<SignatureRecord> sigs = signatureMapper.selectList(new QueryWrapper<SignatureRecord>()
                .eq("rx_id", rx.getId())
                .eq("rx_version", rx.getRxVersion())
                .eq("sign_status", "ACTIVE")
                .orderByAsc("id"));
        SignatureRecord doctor = sigs.stream().filter(s -> "DOCTOR".equals(s.getSignerRole())).findFirst().orElse(null);
        SignatureRecord pharmacist = sigs.stream().filter(s -> "PHARMACIST".equals(s.getSignerRole())).findFirst().orElse(null);
        if (doctor == null || pharmacist == null) {
            throw new IllegalStateException("双签未齐备，无法生成签章 PDF");
        }

        byte[] pdf = renderer.render(rx, items, diagnoses, doctor, pharmacist);
        // 依次调用托管签名服务做 PAdES 数字签章（医生 → 药师）
        pdf = signClient.signPdfDetached(doctor.getSignerId(), pdf,
                "电子处方医生签名 " + doctor.getSignerName(), "doctorSig");
        pdf = signClient.signPdfDetached(pharmacist.getSignerId(), pdf,
                "电子处方药师审核签名 " + pharmacist.getSignerName(), "pharmacistSig");

        String sha = CryptoSupport.sha256Hex(pdf);
        String key = "rx/" + rx.getRxVersion() + "/" + rxNo + ".pdf";
        storage.putObject(key, pdf, "application/pdf");

        rx.setPdfOssKey(key);
        rx.setPdfSha256(sha);
        rx.setPdfStatus("UPLOADED");
        rxMapper.updateById(rx);
        log.info("签章 PDF 已生成 rxNo={} sha256={}", rxNo, sha);
    }
}
