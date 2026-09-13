package cn.hospital.eph.user.web;

import cn.hospital.eph.common.crypto.CryptoSupport;
import cn.hospital.eph.user.entity.CaCertificate;
import cn.hospital.eph.user.mapper.CaCertificateMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 证书状态内部查询（验签时检查吊销/有效期） */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalCertController {

    private final CaCertificateMapper certMapper;

    @Value("${eph.internal.secret}")
    private String internalSecret;

    @Data
    public static class CertValidity {
        private boolean exists;
        private boolean revoked;
        private String notAfter;
    }

    @GetMapping("/certificates/{serial}/validity")
    public cn.hospital.eph.common.web.Result<CertValidity> validity(
            @RequestHeader("X-Internal-Secret") String secret,
            @PathVariable String serial) {
        if (!CryptoSupport.constantTimeEquals(internalSecret, secret)) {
            throw new cn.hospital.eph.common.web.BizException(cn.hospital.eph.common.web.ErrorCode.FORBIDDEN);
        }
        CaCertificate cert = certMapper.selectOne(new QueryWrapper<CaCertificate>()
                .eq("cert_serial", serial).last("limit 1"));
        CertValidity v = new CertValidity();
        v.setExists(cert != null);
        v.setRevoked(cert != null && Integer.valueOf(1).equals(cert.getRevoked()));
        v.setNotAfter(cert == null ? null : cert.getNotAfter().toString());
        return cn.hospital.eph.common.web.Result.ok(v);
    }
}
