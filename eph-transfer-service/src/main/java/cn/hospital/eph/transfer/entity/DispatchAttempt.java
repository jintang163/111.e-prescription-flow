package cn.hospital.eph.transfer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dispatch_attempt")
public class DispatchAttempt {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String rxNo;
    private Long pharmacyId;
    private String result;
    private String detail;
    private LocalDateTime createdAt;
}
