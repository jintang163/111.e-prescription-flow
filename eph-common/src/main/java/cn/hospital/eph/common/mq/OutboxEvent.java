package cn.hospital.eph.common.mq;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 事务发件箱（各业务库同构表 outbox_event） */
@Data
@TableName("outbox_event")
public class OutboxEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private String eventType;
    private String bizKey;
    private Long aggregateId;
    private String payload;
    /** NEW / PUBLISHED / DEAD */
    private String status;
    private Integer publishAttempts;
    private LocalDateTime nextRetryAt;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
}
