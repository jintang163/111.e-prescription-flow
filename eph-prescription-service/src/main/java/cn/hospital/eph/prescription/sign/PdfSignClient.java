package cn.hospital.eph.prescription.sign;

import cn.hospital.eph.common.web.BizException;
import cn.hospital.eph.common.web.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.Map;

/** 调用 user-service 对 PDF 追加 PAdES 数字签章 */
@Slf4j
@Component
public class PdfSignClient {

    private final RestClient restClient;
    private final String internalSecret;

    public PdfSignClient(@Value("${eph.internal.user-base-url}") String baseUrl,
                         @Value("${eph.internal.secret}") String internalSecret) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.internalSecret = internalSecret;
    }

    public byte[] signPdfDetached(long userId, byte[] pdf, String reason, String fieldName) {
        try {
            Map<?, ?> resp = restClient.post()
                    .uri("/internal/sign-pdf")
                    .header("X-Internal-Secret", internalSecret)
                    .body(Map.of(
                            "userId", userId,
                            "pdfBase64", Base64.getEncoder().encodeToString(pdf),
                            "fieldName", fieldName,
                            "reason", reason,
                            "leftSide", fieldName.startsWith("doctor")))
                    .retrieve()
                    .body(Map.class);
            Map<?, ?> data = (Map<?, ?>) resp.get("data");
            if (data == null || data.get("pdfBase64") == null) {
                throw new BizException(ErrorCode.SYS_ERROR, "PDF 签章服务返回为空");
            }
            return Base64.getDecoder().decode((String) data.get("pdfBase64"));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("PDF 签章调用失败 userId={}", userId, e);
            throw new BizException(ErrorCode.SYS_ERROR, "PDF 签章服务不可用");
        }
    }
}
