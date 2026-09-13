package cn.hospital.eph.review.client;

import cn.hospital.eph.common.web.BizException;
import cn.hospital.eph.common.web.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** 调处方服务内部接口取当前版本医生签名上下文 */
@Slf4j
@Component
public class PrescriptionContextClient {

    private final RestClient restClient;
    private final String internalSecret;

    public PrescriptionContextClient(@Value("${eph.internal.prescription-base-url}") String baseUrl,
                                     @Value("${eph.internal.secret}") String internalSecret) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.internalSecret = internalSecret;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> signingContext(String rxNo) {
        try {
            Map<String, Object> resp = restClient.get()
                    .uri("/internal/prescriptions/{rxNo}/signing-context", rxNo)
                    .header("X-Internal-Secret", internalSecret)
                    .retrieve().body(Map.class);
            Map<String, Object> data = (Map<String, Object>) resp.get("data");
            if (data == null || data.get("canonicalPayload") == null) {
                throw new BizException(ErrorCode.SYS_ERROR, "处方签名上下文缺失");
            }
            return data;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取处方签名上下文失败 rxNo={}", rxNo, e);
            throw new BizException(ErrorCode.SYS_ERROR, "处方服务不可用");
        }
    }
}
