package cn.hospital.eph.common.security;

import cn.hospital.eph.common.web.BizException;
import cn.hospital.eph.common.web.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** 签名重认证短时许可（5 分钟），跨服务共享 Redis */
@Service
@RequiredArgsConstructor
public class SignGrantService {

    public static final Duration GRANT_TTL = Duration.ofMinutes(5);
    private static final String KEY_PREFIX = "sign:grant:";

    private final StringRedisTemplate redis;

    public void grant(long userId) {
        redis.opsForValue().set(KEY_PREFIX + userId, "1", GRANT_TTL);
    }

    /** 校验并立即消费（一次性），防止同一授权重复签名 */
    public void requireAndConsume(long userId) {
        Boolean ok = redis.delete(KEY_PREFIX + userId);
        if (!Boolean.TRUE.equals(ok)) {
            throw new BizException(ErrorCode.SIGN_GRANT_REQUIRED);
        }
    }

    public boolean has(long userId) {
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + userId));
    }
}
