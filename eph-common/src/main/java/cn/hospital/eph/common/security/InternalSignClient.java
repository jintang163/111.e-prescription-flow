package cn.hospital.eph.common.security;

import cn.hospital.eph.common.web.BizException;
import cn.hospital.eph.common.web.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/** 调用 user-service 的内部托管签名接口（/internal/sign） */
@Slf4j
@Component
public class InternalSignClient {

    private final RestClient restClient;
    private final String internalSecret;

    public InternalSignClient(@Value("${eph.internal.user-base-url:http://localhost:8081}") String baseUrl,
                              @Value("${eph.internal.secret:eph-internal-dev-secret}") String internalSecret) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.internalSecret = internalSecret;
    }

    public SignResult sign(long userId, byte[] content) {
        try {
            Map<?, ?> resp = restClient.post()
                    .uri("/internal/sign")
                    .header("X-Internal-Secret", internalSecret)
                    .body(Map.of("userId", userId, "contentBase64",
                            Base64.getEncoder().encodeToString(content)))
                    .retrieve()
                    .body(Map.class);
            Map<?, ?> data = (Map<?, ?>) resp.get("data");
            if (data == null) {
                throw new BizException(ErrorCode.SYS_ERROR, "签名服务返回为空");
            }
            return new SignResult((String) data.get("signatureBase64"),
                    (String) data.get("certSerial"),
                    (String) data.get("certPem"));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用内部签名服务失败 userId={}", userId, e);
            throw new BizException(ErrorCode.SYS_ERROR, "签名服务不可用");
        }
    }

    public SignResult sign(long userId, String content) {
        return sign(userId, content.getBytes(StandardCharsets.UTF_8));
    }

    public record SignResult(String signatureBase64, String certSerial, String certPem) {
        public byte[] signatureBytes() {
            return Base64.getDecoder().decode(signatureBase64);
        }
    }
}
