package cn.hospital.eph.common.security;

import cn.hospital.eph.common.web.BizException;
import cn.hospital.eph.common.web.ErrorCode;

/** 线程级当前用户持有器，由各服务 JWT 过滤器写入 */
public final class UserContext {
    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        LoginUser u = HOLDER.get();
        if (u == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return u;
    }

    public static long currentUserId() {
        return get().getUserId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
