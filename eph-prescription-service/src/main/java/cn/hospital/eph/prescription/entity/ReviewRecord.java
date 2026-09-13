package cn.hospital.eph.prescription.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("review_record")
public class ReviewRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long rxId;
    private String rxNo;
    private Integer rxVersion;
    private Long pharmacistId;
    private String pharmacistName;
    private String decision;
    private String comment;
    private Long durationMs;
    private LocalDateTime createdAt;
}
