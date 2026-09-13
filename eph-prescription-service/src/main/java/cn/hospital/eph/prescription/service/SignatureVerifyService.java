package cn.hospital.eph.prescription.service;

import cn.hospital.eph.common.crypto.CryptoSupport;
import cn.hospital.eph.prescription.entity.ReviewRecord;
import cn.hospital.eph.prescription.entity.RxStatusLog;
import cn.hospital.eph.prescription.entity.SignatureRecord;
import cn.hospital.eph.prescription.mapper.ReviewRecordMapper;
import cn.hospital.eph.prescription.mapper.RxStatusLogMapper;
import cn.hospital.eph.prescription.mapper.SignatureRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 数字签名验签：密码学验证 + 证书有效期 + CA 侧吊销状态 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignatureVerifyService {

    private final SignatureRecordMapper signatureMapper;
    private final RxStatusLogMapper logMapper;
    private final ReviewRecordMapper reviewRecordMapper;

    @Value("${eph.internal.user-base-url}")
    private String userBaseUrl;

    @Value("${eph.internal.secret}")
    private String internalSecret;

    public Map<String, Object> verify(String rxNo, Long rxId) {
        List<SignatureRecord> sigs = signatureMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SignatureRecord>()
                        .eq("rx_id", rxId).orderByAsc("id"));
        List<Map<String, Object>> items = new ArrayList<>();
        boolean allValid = !sigs.isEmpty();
        for (SignatureRecord s : sigs) {
            Map<String, Object> r = new HashMap<>();
            r.put("signerRole", s.getSignerRole());
            r.put("signerName", s.getSignerName());
            r.put("certSerial", s.getCertSerial());
            r.put("rxVersion", s.getRxVersion());
            r.put("alg", s.getAlg());
            r.put("signTime", s.getSignTime());
            r.put("status", s.getSignStatus());

            boolean cryptoOk;
            String certTimeOk = "UNKNOWN";
            try {
                X509Certificate cert = parseCert(s.getCertPemSnapshot());
                cert.checkValidity(new Date());
                certTimeOk = "VALID";
                byte[] content = s.getCanonicalPayload().getBytes(StandardCharsets.UTF_8);
                cryptoOk = CryptoSupport.verifySha256Rsa(cert.getPublicKey(), content,
                        Base64.getDecoder().decode(s.getSignatureValue()));
            } catch (Exception e) {
                cryptoOk = false;
                certTimeOk = "EXPIRED_OR_INVALID";
            }
            boolean revoked = checkRevoked(s.getCertSerial());
            boolean valid = cryptoOk && "VALID".equals(certTimeOk) && !revoked
                    && "ACTIVE".equals(s.getSignStatus());
            allValid &= valid;

            r.put("cryptoValid", cryptoOk);
            r.put("certValidity", certTimeOk);
            r.put("certRevoked", revoked);
            r.put("verifyPassed", valid);
            items.add(r);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("rxNo", rxNo);
        result.put("verifiedAt", LocalDateTime.now().atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime().toString());
        result.put("allSignaturesValid", allValid);
        result.put("signatures", items);
        return result;
    }

    public List<RxStatusLog> logs(long rxId) {
        return logMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RxStatusLog>()
                .eq("rx_id", rxId).orderByAsc("id"));
    }

    public List<ReviewRecord> reviews(long rxId) {
        return reviewRecordMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ReviewRecord>()
                .eq("rx_id", rxId).orderByAsc("id"));
    }

    private boolean checkRevoked(String serial) {
        try {
            Map<?, ?> resp = RestClient.builder().baseUrl(userBaseUrl).build().get()
                    .uri("/internal/certificates/{serial}/validity", serial)
                    .header("X-Internal-Secret", internalSecret)
                    .retrieve().body(Map.class);
            Map<?, ?> data = (Map<?, ?>) resp.get("data");
            return data != null && Boolean.TRUE.equals(data.get("revoked"));
        } catch (Exception e) {
            log.warn("吊销状态查询失败 serial={}: {}", serial, e.getMessage());
            return false;
        }
    }

    private X509Certificate parseCert(String pem) throws Exception {
        String b64 = pem.replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(b64)));
    }
}
