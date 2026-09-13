package cn.hospital.eph.prescription.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rx_status_log")
public class RxStatusLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long rxId;
    private String rxNo;
    private String fromStatus;
    private String toStatus;
    private Long actorId;
    private String actorRole;
    private String action;
    private String comment;
    private LocalDateTime createdAt;
}
