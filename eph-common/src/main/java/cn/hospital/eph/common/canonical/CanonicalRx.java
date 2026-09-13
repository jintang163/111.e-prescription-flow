package cn.hospital.eph.common.canonical;

import java.util.List;

/** 处方签名载荷白名单 DTO（显式排除状态/审计/PDF 等非处方内容字段） */
public record CanonicalRx(String rxNo, int rxVersion, String category,
                          Patient patient, Doctor doctor,
                          List<Diagnosis> diagnoses, List<Item> items,
                          String createdAt) {

    public record Patient(Long id, String name, String idCardMask, Integer age, Integer gender) {
    }

    public record Doctor(Long id, String name, String deptCode, String deptName) {
    }

    public record Diagnosis(int seq, String icd10Code, String diagnosisName) {
    }

    public record Item(int seq, String drugCode, String drugName, String spec, String dosageForm,
                       String qty, String unit, String singleDose, String frequency,
                       String administrationRoute, Integer days, Integer skinTestFlag) {
    }

    /** 药师签名附加的审核声明 */
    public record ReviewDeclaration(String decision, String comment, String reviewedAt) {
    }
}
