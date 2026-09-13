package cn.hospital.eph.common.mq;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 消费幂等表（event_id + handler 唯一） */
@Data
@TableName("inbox_event")
public class InboxEvent {
    @TableId(type = IdType.INPUT)
    private String eventId;
    private String handler;
    private LocalDateTime consumedAt;
}
