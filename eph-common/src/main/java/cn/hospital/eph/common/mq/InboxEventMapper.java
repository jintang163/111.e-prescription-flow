package cn.hospital.eph.common.mq;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InboxEventMapper extends BaseMapper<InboxEvent> {
}
