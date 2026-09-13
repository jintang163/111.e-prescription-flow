package cn.hospital.eph.common.canonical;

import cn.hospital.eph.common.crypto.CryptoSupport;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * 签名原文规范化（JCS 风格）：
 * 字段白名单 DTO → key 字典序、无空白、null 剔除、时间 ISO-8601、UTF-8 直出、明细按 seq 排列；
 * 药师签名 = 同一 JSON 合并 review 声明后整体规范化。
 */
public final class CanonicalPayloadBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private CanonicalPayloadBuilder() {
    }

    public static String build(CanonicalRx rx) {
        try {
            return MAPPER.writeValueAsString(rx);
        } catch (Exception e) {
            throw new IllegalArgumentException("canonical 序列化失败", e);
        }
    }

    /** 在医生签名原文基础上合并审核声明，得到药师签名原文 */
    public static String buildWithReview(String rxCanonicalJson, CanonicalRx.ReviewDeclaration review) {
        try {
            ObjectNode node = (ObjectNode) MAPPER.readTree(rxCanonicalJson);
            ObjectNode reviewNode = node.objectNode();
            reviewNode.put("decision", review.decision());
            if (review.comment() != null) {
                reviewNode.put("comment", review.comment());
            }
            reviewNode.put("reviewedAt", review.reviewedAt());
            node.set("review", reviewNode);
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalArgumentException("药师 canonical 构造失败", e);
        }
    }

    public static String sha256(String canonicalJson) {
        return CryptoSupport.sha256Hex(canonicalJson);
    }
}
