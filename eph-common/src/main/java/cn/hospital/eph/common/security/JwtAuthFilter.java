package cn.hospital.eph.common.security;

import cn.hospital.eph.common.web.BizException;
import cn.hospital.eph.common.web.ErrorCode;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** 解析 Bearer JWT 并写入 UserContext（只认证不鉴权，方法级用 @RequireRole） */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                Claims c = jwtService.parse(auth.substring(7));
                if (!"sign".equals(c.get("scope"))) {
                    LoginUser user = new LoginUser(
                            ((Number) c.get("uid")).longValue(),
                            (String) c.get("username"),
                            (String) c.get("name"),
                            (String) c.get("role"));
                    UserContext.set(user);
                }
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":40100,\"message\":\"未登录或登录已过期\"}");
                return;
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }
}
