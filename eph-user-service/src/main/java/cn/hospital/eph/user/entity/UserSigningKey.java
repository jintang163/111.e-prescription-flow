package cn.hospital.eph.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_signing_key")
public class UserSigningKey {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer keyVersion;
    private String alg;
    private Integer keySize;
    private String certSerial;
    private String privateKeyEnc;
    /** ACTIVE / ROTATED / REVOKED */
    private String status;
    private LocalDateTime activatedAt;
    private LocalDateTime rotatedAt;
}
