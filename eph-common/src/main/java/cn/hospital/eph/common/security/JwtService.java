package cn.hospital.eph.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/** JWT 签发/解析（HS256）。claim: uid/username/name/role，签名许可短期令牌 scope=sign */
@Slf4j
@Component
public class JwtService {

    @Value("${eph.jwt.secret:eph-dev-secret-key-change-me-please-0123456789abcdef}")
    private String secret;

    @Value("${eph.jwt.access-ttl-seconds:7200}")
    private long accessTtlSeconds;

    private SecretKey key;

    @PostConstruct
    void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(long userId, String username, String realName, String role) {
        return build(Map.of("uid", userId, "username", username, "name", realName, "role", role), accessTtlSeconds);
    }

    /** 签名重认证后的短时许可（独立短 TTL，claim scope=sign） */
    public String issueSignGrant(long userId, String username, String role, long ttlSeconds) {
        return build(Map.of("uid", userId, "username", username, "role", role, "scope", "sign"), ttlSeconds);
    }

    private String build(Map<String, Object> claims, long ttlSeconds) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlSeconds * 1000))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }
}
