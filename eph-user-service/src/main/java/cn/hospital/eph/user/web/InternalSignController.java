package cn.hospital.eph.user.web;

import cn.hospital.eph.user.ca.CaService;
import cn.hospital.eph.user.ca.PdfSigningService;
import cn.hospital.eph.common.web.BizException;
import cn.hospital.eph.common.web.ErrorCode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

/** 服务间内部签名接口（不经网关，X-Internal-Secret 保护） */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalSignController {

    private final CaService caService;
    private final PdfSigningService pdfSigningService;

    @Value("${eph.internal.secret}")
    private String internalSecret;

    @Data
    public static class SignRequest {
        private Long userId;
        private String contentBase64;
    }

    @Data
    public static class SignResponse {
        private String signatureBase64;
        private String certSerial;
        private String certPem;
    }

    @Data
    public static class SignPdfRequest {
        private Long userId;
        private String pdfBase64;
        private String fieldName;
        private String reason;
        private Boolean leftSide;
    }

    @Data
    public static class SignPdfResponse {
        private String pdfBase64;
    }

    @PostMapping("/sign")
    public cn.hospital.eph.common.web.Result<SignResponse> sign(
            @RequestHeader("X-Internal-Secret") String secret,
            @RequestBody SignRequest req) {
        checkSecret(secret);
        byte[] content = Base64.getDecoder().decode(req.getContentBase64());
        CaService.SignResult r = caService.sign(req.getUserId(), content);
        SignResponse resp = new SignResponse();
        resp.setSignatureBase64(r.signatureBase64());
        resp.setCertSerial(r.certSerial());
        resp.setCertPem(r.certPem());
        return cn.hospital.eph.common.web.Result.ok(resp);
    }

    @PostMapping("/sign-pdf")
    public cn.hospital.eph.common.web.Result<SignPdfResponse> signPdf(
            @RequestHeader("X-Internal-Secret") String secret,
            @RequestBody SignPdfRequest req) {
        checkSecret(secret);
        byte[] pdf = Base64.getDecoder().decode(req.getPdfBase64());
        byte[] signed = pdfSigningService.signDetached(req.getUserId(), pdf,
                req.getFieldName(), req.getReason(), Boolean.TRUE.equals(req.getLeftSide()));
        SignPdfResponse resp = new SignPdfResponse();
        resp.setPdfBase64(Base64.getEncoder().encodeToString(signed));
        return cn.hospital.eph.common.web.Result.ok(resp);
    }

    private void checkSecret(String secret) {
        if (!cn.hospital.eph.common.crypto.CryptoSupport.constantTimeEquals(internalSecret, secret)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }
}
