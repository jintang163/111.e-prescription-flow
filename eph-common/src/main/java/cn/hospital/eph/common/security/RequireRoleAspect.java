package cn.hospital.eph.common.security;

import cn.hospital.eph.common.web.BizException;
import cn.hospital.eph.common.web.ErrorCode;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
public class RequireRoleAspect {

    @Pointcut("@annotation(cn.hospital.eph.common.security.RequireRole) "
            + "|| @within(cn.hospital.eph.common.security.RequireRole)")
    public void guarded() {
    }

    @Before("guarded()")
    public void check(JoinPoint pjp) {
        LoginUser user = UserContext.get();
        RequireRole annotation = resolve(pjp);
        if (annotation == null) {
            return;
        }
        if (Arrays.stream(annotation.value()).noneMatch(r -> r.equals(user.getRole()))) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private RequireRole resolve(JoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        RequireRole onMethod = signature.getMethod().getAnnotation(RequireRole.class);
        if (onMethod != null) {
            return onMethod;
        }
        Class<?> c = pjp.getTarget().getClass();
        while (c != null) {
            RequireRole a = c.getAnnotation(RequireRole.class);
            if (a != null) return a;
            c = c.getSuperclass();
        }
        return null;
    }
}
