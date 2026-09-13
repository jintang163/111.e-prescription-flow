package cn.hospital.eph.transfer.web;

import cn.hospital.eph.common.web.BizException;
import cn.hospital.eph.common.web.ErrorCode;
import cn.hospital.eph.transfer.entity.Pharmacy;
import cn.hospital.eph.transfer.entity.PharmacyCallbackLog;
import cn.hospital.eph.transfer.mapper.PharmacyCallbackLogMapper;
import cn.hospital.eph.transfer.mapper.PharmacyMapper;
import cn.hospital.eph.transfer.service.OrderDispatchService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;

/** 药店状态回传：HMAC 验签 + 时间戳/nonce 防重放，验签通过后落状态（不走 JWT） */
@Slf4j
@RestController
@RequestMapping("/api/pharmacies")
@RequiredArgsConstructor
public class PharmacyCallbackController {

    private final PharmacyMapper pharmacyMapper;
    private final PharmacyCallbackLogMapper callbackLogMapper;
    private final OrderDispatchService dispatchService;
    private final StringRedisTemplate redis;

    @Data
    public static class CallbackBody {
        private String rxNo;
        private String orderNo;
        /** 外部状态码：ACCEPTED/DISPENSING/DISPENSED/READY/PICKED_UP/REJECTED... */
        private String status;
        private String eventId;
        private String statusText;
    }

    @PostMapping("/{code}/callbacks/orders")
    public Map<String, Object> callback(@PathVariable String code,
                                        @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
                                        @RequestHeader(value = "X-Nonce", required = false) String nonce,
                                        @RequestHeader(value = "X-Signature", required = false) String signature,
                                        @RequestBody String rawBody) {
        Pharmacy pharmacy = pharmacyMapper.selectOne(new QueryWrapper<Pharmacy>()
                .eq("code", code).last("limit 1"));

        CallbackBody body;
        try {
            body = new com.fasterxml.jackson.databind.ObjectMapper().readValue(rawBody, CallbackBody.class);
        } catch (Exception e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "回调报文无法解析");
        }

        boolean verifyOk = pharmacy != null
                && validTimestamp(timestamp)
                && nonceFresh(code, nonce)
                && dispatchService.verifyCallbackSignature(pharmacy, timestamp, nonce, rawBody, signature);

        PharmacyCallbackLog logRow = new PharmacyCallbackLog();
        logRow.setPharmacyCode(code);
        logRow.setRawBody(rawBody);
        logRow.setSignature(signature);
        logRow.setVerifyResult(verifyOk ? "PASS" : "FAIL");
        logRow.setParsedEvent(body.getStatus());
        logRow.setProcessed(0);
        logRow.setCreatedAt(LocalDateTime.now());
        callbackLogMapper.insert(logRow);

        if (!verifyOk) {
            log.warn("药店回调验签失败 code={} orderNo={}", code, body.getOrderNo());
            throw new BizException(ErrorCode.PHARMACY_CALLBACK_UNSIGNED);
        }

        dispatchService.applyCallback(body.getRxNo(), body.getOrderNo(), body.getStatus(),
                body.getEventId(), body.getStatusText());
        logRow.setProcessed(1);
        callbackLogMapper.updateById(logRow);
        return Map.of("code", 0, "message", "accepted");
    }

    private boolean validTimestamp(String ts) {
        try {
            long delta = Math.abs(System.currentTimeMillis() - Long.parseLong(ts));
            return delta < Duration.ofMinutes(5).toMillis();
        } catch (Exception e) {
            return false;
        }
    }

    /** nonce 10 分钟内不可重复 */
    private boolean nonceFresh(String code, String nonce) {
        if (nonce == null || nonce.isBlank()) return false;
        Boolean first = redis.opsForValue().setIfAbsent(
                "callback:nonce:" + code + ":" + nonce, "1", Duration.ofMinutes(10));
        return Boolean.TRUE.equals(first);
    }
}
