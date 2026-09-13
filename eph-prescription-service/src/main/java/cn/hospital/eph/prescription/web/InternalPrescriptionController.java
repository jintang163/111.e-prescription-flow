package cn.hospital.eph.prescription.web;

import cn.hospital.eph.common.crypto.CryptoSupport;
import cn.hospital.eph.common.enums.SignatureStatus;
import cn.hospital.eph.common.enums.SignerRole;
import cn.hospital.eph.prescription.entity.Prescription;
import cn.hospital.eph.prescription.entity.SignatureRecord;
import cn.hospital.eph.prescription.mapper.SignatureRecordMapper;
import cn.hospital.eph.prescription.service.PrescriptionService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/** 供 review-service 内部获取当前版本的医生签名原文（补正循环后版本已变化） */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalPrescriptionController {

    private final PrescriptionService prescriptionService;
    private final SignatureRecordMapper signatureMapper;

    @Value("${eph.internal.secret}")
    private String internalSecret;

    @GetMapping("/prescriptions/{rxNo}/signing-context")
    public cn.hospital.eph.common.web.Result<Map<String, Object>> signingContext(@RequestHeader("X-Internal-Secret") String secret,
                                              @PathVariable String rxNo) {
        if (!CryptoSupport.constantTimeEquals(internalSecret, secret)) {
            throw new cn.hospital.eph.common.web.BizException(cn.hospital.eph.common.web.ErrorCode.FORBIDDEN);
        }
        Prescription rx = prescriptionService.mustGet(rxNo);
        SignatureRecord doctor = signatureMapper.selectOne(new QueryWrapper<SignatureRecord>()
                .eq("rx_id", rx.getId())
                .eq("rx_version", rx.getRxVersion())
                .eq("signer_role", SignerRole.DOCTOR.name())
                .eq("sign_status", SignatureStatus.ACTIVE.name())
                .last("limit 1"));
        Map<String, Object> data = new HashMap<>();
        data.put("rxNo", rx.getRxNo());
        data.put("rxVersion", rx.getRxVersion());
        data.put("rxStatus", rx.getRxStatus());
        data.put("doctorId", rx.getDoctorId());
        data.put("doctorName", rx.getDoctorName());
        if (doctor != null) {
            data.put("canonicalPayload", doctor.getCanonicalPayload());
            data.put("payloadSha256", doctor.getPayloadSha256());
            data.put("doctorSignature", doctor.getSignatureValue());
            data.put("doctorCertSerial", doctor.getCertSerial());
        }
        return cn.hospital.eph.common.web.Result.ok(data);
    }
}
