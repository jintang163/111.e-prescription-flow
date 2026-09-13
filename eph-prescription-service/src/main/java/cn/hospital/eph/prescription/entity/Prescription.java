package cn.hospital.eph.prescription.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prescription")
public class Prescription {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String rxNo;
    private Integer rxVersion;
    private Long patientId;
    private String patientName;
    private String patientIdCardMask;
    private Integer patientAge;
    private Integer patientGender;
    private String patientPhone;
    private Long doctorId;
    private String doctorName;
    private String deptCode;
    private String deptName;
    private Integer rxCategory;
    private String diagnosisSummary;
    private String rxStatus;
    private String reviewStatus;
    private Integer doctorSigned;
    private Integer pharmacistSigned;
    private Integer amendmentCount;
    private String rejectReason;
    private String pdfOssKey;
    private String pdfSha256;
    private String pdfStatus;
    private LocalDateTime effectiveAt;
    private LocalDateTime expireAt;
    private String fulfillmentStatus;
    private Long currentPharmacyId;
    private String currentPharmacyName;
    private String currentOrderNo;
    private String relatedRxNo;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
