package cn.hospital.eph.transfer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pharmacy_callback_log")
public class PharmacyCallbackLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String pharmacyCode;
    private String headers;
    private String rawBody;
    private String signature;
    private String verifyResult;
    private String parsedEvent;
    private Integer processed;
    private LocalDateTime createdAt;
}
