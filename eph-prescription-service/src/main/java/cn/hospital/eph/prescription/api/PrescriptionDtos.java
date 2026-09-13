package cn.hospital.eph.prescription.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

public class PrescriptionDtos {

    @Data
    public static class DiagnosisDto {
        private Integer seq;
        private String icd10Code;
        @NotBlank
        private String diagnosisName;
    }

    @Data
    public static class ItemDto {
        private Integer seq;
        @NotBlank
        private String drugCode;
        @NotBlank
        private String drugName;
        private String spec;
        private String dosageForm;
        @NotNull
        private BigDecimal qty;
        private String unit;
        @NotBlank
        private String singleDose;
        private String doseUnit;
        @NotBlank
        private String frequency;
        private String administrationRoute;
        private Integer days;
        private Integer skinTestFlag;
        private String remark;
    }

    @Data
    public static class SaveRequest {
        @NotNull
        private Long patientId;
        @NotBlank
        private String patientName;
        private String patientIdCardMask;
        private Integer patientAge;
        private Integer patientGender;
        private String patientPhone;
        private String deptCode;
        private String deptName;
        private Integer rxCategory;
        @Valid
        @NotEmpty
        private List<DiagnosisDto> diagnoses;
        @Valid
        @NotEmpty
        private List<ItemDto> items;
        private String remark;
    }

    @Data
    public static class SubmitRequest {
        /** 前端确认签名意图（弹窗确认），服务端另校验 sign-grant */
        private Boolean confirmed;
    }

    @Data
    public static class RejectRequest {
        @NotBlank
        private String reason;
    }

    @Data
    public static class AmendmentRequest {
        @NotBlank
        private String comment;
    }
}
