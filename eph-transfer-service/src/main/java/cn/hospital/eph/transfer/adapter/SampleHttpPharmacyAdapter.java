package cn.hospital.eph.transfer.adapter;

import cn.hospital.eph.common.crypto.CryptoSupport;
import cn.hospital.eph.common.event.Events;
import cn.hospital.eph.transfer.entity.Pharmacy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 样例 HTTP 药店适配器（真实厂商对接模板）：
 * 出站请求 HMAC 签名头 X-App-Key/X-Timestamp/X-Nonce/X-Signature，
 * 签名串 = METHOD\npath\ntimestamp\nnonce\nsha256(body)。
 * 入站回传统一走 /api/pharmacies/{code}/callbacks/orders 验签，协议见 README。
 */
@Slf4j
@Component
public class SampleHttpPharmacyAdapter implements PharmacyAdapter {

    @Override
    public String type() {
        return "SAMPLE_HTTP";
    }

    @Override
    public StockCheckResult checkStock(AdapterContext ctx, List<Events.RxItem> items) {
        Map<String, Object> body = Map.of("rxNo", ctx.rxNo(), "items",
                items.stream().map(i -> Map.of("drugCode", i.drugCode(), "qty", i.qty())).toList());
        Map<?, ?> respRaw = post(ctx, "/api/v1/stock/check", body);
        Map<String, Object> resp = cast(respRaw);
        boolean all = Boolean.TRUE.equals(resp.get("allAvailable"));
        return new StockCheckResult(all, String.valueOf(resp.getOrDefault("detail", "")));
    }

    @Override
    public StockHoldResult hold(AdapterContext ctx, List<Events.RxItem> items) {
        Map<String, Object> resp = cast(post(ctx, "/api/v1/stock/hold",
                Map.of("rxNo", ctx.rxNo(), "orderNo", ctx.orderNo(),
                        "items", items.stream().map(i -> Map.of("drugCode", i.drugCode(), "qty", i.qty())).toList(),
                        "ttlSeconds", 900)));
        return new StockHoldResult(Boolean.TRUE.equals(resp.get("success")),
                (String) resp.get("holdNo"), String.valueOf(resp.getOrDefault("detail", "")));
    }

    @Override
    public OrderCreateResult createOrder(AdapterContext ctx, List<Events.RxItem> items, String idempotencyKey) {
        Map<String, Object> resp = cast(post(ctx, "/api/v1/orders",
                Map.of("rxNo", ctx.rxNo(), "orderNo", ctx.orderNo(),
                        "idempotencyKey", idempotencyKey,
                        "items", items.stream().map(i -> Map.of("drugCode", i.drugCode(), "qty", i.qty())).toList())));
        return new OrderCreateResult(Boolean.TRUE.equals(resp.get("success")),
                (String) resp.get("externalOrderNo"), String.valueOf(resp.getOrDefault("detail", "")));
    }

    @Override
    public String queryStatus(AdapterContext ctx, String externalOrderNo) {
        Pharmacy p = ctx.pharmacy();
        String path = "/api/v1/orders/" + externalOrderNo;
        String ts = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString();
        String sig = sign(p, "GET", path, ts, nonce, "");
        Map<?, ?> resp = client(p).get().uri(path)
                .header("X-App-Key", nz(p.getAppKey()))
                .header("X-Timestamp", ts).header("X-Nonce", nonce).header("X-Signature", sig)
                .retrieve().body(Map.class);
        return resp == null ? null : (String) resp.get("status");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object o) { return (Map<String, Object>) o; }

    private Map<?, ?> post(AdapterContext ctx, String path, Object bodyObj) {
        try {
            Pharmacy p = ctx.pharmacy();
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(bodyObj);
            String ts = String.valueOf(System.currentTimeMillis());
            String nonce = UUID.randomUUID().toString();
            String sig = sign(p, "POST", path, ts, nonce, json);
            return client(p).post().uri(path)
                    .header("X-App-Key", nz(p.getAppKey()))
                    .header("X-Timestamp", ts).header("X-Nonce", nonce).header("X-Signature", sig)
                    .header("Idempotency-Key", ctx.rxNo() + "#" + p.getCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve().body(Map.class);
        } catch (Exception e) {
            log.error("样例药店出站调用失败 rxNo={} path={}", ctx.rxNo(), path, e);
            throw new IllegalStateException("药店接口调用失败: " + e.getMessage(), e);
        }
    }

    private String sign(Pharmacy p, String method, String path, String ts, String nonce, String body) {
        String bodyHash = CryptoSupport.sha256Hex(body);
        String raw = method + "\n" + path + "\n" + ts + "\n" + nonce + "\n" + bodyHash;
        return CryptoSupport.hmacSha256Hex(nz(p.getSignSecret()), raw);
    }

    private RestClient client(Pharmacy p) {
        return RestClient.builder()
                .baseUrl(p.getBaseUrl())
                .requestFactory(buildFactory())
                .build();
    }

    private org.springframework.http.client.ClientHttpRequestFactory buildFactory() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
        return factory;
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }
}
