package cn.hospital.eph.transfer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pharmacy_order")
public class PharmacyOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String rxNo;
    private Integer rxVersion;
    private Long pharmacyId;
    private String pharmacyCode;
    private String adapterType;
    private String status;
    private String stockHoldNo;
    private String externalOrderNo;
    private String idempotencyKey;
    private String failReason;
    private Integer attempts;
    private LocalDateTime nextRetryAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime dispensedAt;
    private LocalDateTime pickedUpAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
