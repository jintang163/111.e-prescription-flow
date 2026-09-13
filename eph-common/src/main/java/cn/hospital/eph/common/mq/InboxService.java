package cn.hospital.eph.common.mq;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 消费幂等：首次处理返回 true，重复事件返回 false */
@Service
@RequiredArgsConstructor
public class InboxService {

    private final InboxEventMapper mapper;

    public boolean firstTime(String eventId, String handler) {
        InboxEvent e = new InboxEvent();
        e.setEventId(eventId + ":" + handler);
        e.setHandler(handler);
        e.setConsumedAt(LocalDateTime.now());
        try {
            mapper.insert(e);
            return true;
        } catch (DuplicateKeyException dup) {
            return false;
        }
    }
}
