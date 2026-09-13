package cn.hospital.eph.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 网关统一鉴权：公开路径白名单 + Bearer JWT 校验（角色级鉴权在各服务 @RequireRole） */
@Component
public class JwtGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> PUBLIC_PATTERNS = List.of(
            "/api/auth/login",
            "/api/pharmacies/*/callbacks/**"
    );

    private final AntPathMatcher matcher = new AntPathMatcher();

    @Value("${eph.jwt.secret}")
    private String secret;

    private SecretKey key;

    @PostConstruct
    void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        String path = req.getURI().getPath();
        if (!path.startsWith("/api/") || isPublic(path)) {
            return chain.filter(exchange);
        }
        String auth = req.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, 40100, "未登录或登录已过期");
        }
        try {
            Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(auth.substring(7)).getPayload();
        } catch (Exception e) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, 40100, "未登录或登录已过期");
        }
        return chain.filter(exchange);
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATTERNS.stream().anyMatch(p -> matcher.match(p, path));
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, int code, String msg) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":" + code + ",\"message\":\"" + msg + "\"}";
        DataBuffer buf = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buf));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
