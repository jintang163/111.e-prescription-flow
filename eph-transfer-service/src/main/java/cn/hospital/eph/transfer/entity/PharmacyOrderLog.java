package cn.hospital.eph.transfer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pharmacy_order_log")
public class PharmacyOrderLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String fromStatus;
    private String toStatus;
    private String detail;
    private LocalDateTime createdAt;
}
