package cn.hospital.eph.prescription.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("signature_record")
public class SignatureRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long rxId;
    private String rxNo;
    private Integer rxVersion;
    private Long signerId;
    private String signerName;
    private String signerRole;
    private String certSerial;
    private String certPemSnapshot;
    private String alg;
    private String canonicalPayload;
    private String payloadSha256;
    private String signatureValue;
    private String signStatus;
    private String flowAction;
    private LocalDateTime signTime;
    private LocalDateTime createdAt;
}
