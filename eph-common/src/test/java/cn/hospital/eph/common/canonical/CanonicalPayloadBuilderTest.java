package cn.hospital.eph.common.canonical;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalPayloadBuilderTest {

    private CanonicalRx sample(String rxNo, int version, String drug) {
        return new CanonicalRx(rxNo, version, "NORMAL",
                new CanonicalRx.Patient(4L, "王患者", "1101**********0000", 41, 1),
                new CanonicalRx.Doctor(2L, "张明华", "CARD", "心血管内科"),
                List.of(new CanonicalRx.Diagnosis(1, "I10.x00", "原发性高血压")),
                List.of(new CanonicalRx.Item(1, "DRUG-AMLOD", drug, "5mg*7片", "片剂",
                        "2", "盒", "5mg", "QD", "口服", 14, 0)),
                "2026-09-13T10:00:00+08:00");
    }

    @Test
    void deterministicAndStable() {
        String a = CanonicalPayloadBuilder.build(sample("RX1", 1, "氨氯地平"));
        String b = CanonicalPayloadBuilder.build(sample("RX1", 1, "氨氯地平"));
        assertEquals(a, b);
        // 无多余空白
        // 无 JSON 语法空白（紧凑序列化；本用例值中也无 ASCII 空格）
        assertTrue(!a.contains(": ") && !a.contains(", "));
    }

    @Test
    void keysSortedAlphabetically() {
        String json = CanonicalPayloadBuilder.build(sample("RX1", 1, "氨氯地平"));
        // 顶层键应字典序：items < patient < rxNo ...
        int idItems = json.indexOf("\"items\"");
        int idPatient = json.indexOf("\"patient\"");
        int idRxNo = json.indexOf("\"rxNo\"");
        assertTrue(idItems < idPatient && idPatient < idRxNo, "顶层键需按字典序排列");
    }

    @Test
    void unicodeNotEscaped() {
        String json = CanonicalPayloadBuilder.build(sample("RX1", 1, "苯磺酸氨氯地平片"));
        assertTrue(json.contains("苯磺酸氨氯地平片"), "中文应 UTF-8 直出而非 \\u 转义");
    }

    @Test
    void versionChangeChangesHash() {
        String v1 = CanonicalPayloadBuilder.build(sample("RX1", 1, "氨氯地平"));
        String v2 = CanonicalPayloadBuilder.build(sample("RX1", 2, "氨氯地平"));
        assertNotEquals(CanonicalPayloadBuilder.sha256(v1), CanonicalPayloadBuilder.sha256(v2),
                "补正升版本后签名载荷摘要必须变化");
    }

    @Test
    void pharmacistCanonicalMergesReview() {
        String doctorJson = CanonicalPayloadBuilder.build(sample("RX1", 1, "氨氯地平"));
        String pharmacistJson = CanonicalPayloadBuilder.buildWithReview(doctorJson,
                new CanonicalRx.ReviewDeclaration("APPROVED", "同意", "2026-09-13T10:05:00+08:00"));
        assertTrue(pharmacistJson.contains("\"review\":{"));
        assertTrue(pharmacistJson.contains("\"decision\":\"APPROVED\""));
        // 药师签名内容包含完整处方原文
        assertTrue(pharmacistJson.contains("\"rxNo\":\"RX1\""));
        assertNotEquals(doctorJson, pharmacistJson);
    }
}
