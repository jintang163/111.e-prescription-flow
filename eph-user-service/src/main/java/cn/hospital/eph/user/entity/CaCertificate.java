package cn.hospital.eph.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ca_certificate")
public class CaCertificate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String subjectCn;
    private String certSerial;
    private String certPem;
    /** ROOT / USER */
    private String certType;
    private Long ownerUserId;
    private String issuerSerial;
    private LocalDateTime notBefore;
    private LocalDateTime notAfter;
    private Integer revoked;
    private LocalDateTime revokedAt;
    private LocalDateTime createdAt;
}
