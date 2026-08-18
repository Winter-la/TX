package com.thz.spectrum.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具类
 * - 生成 Token：Payload 仅含用户标识/用户名/过期时间，不含密码等敏感信息
 * - 解析/验证 Token：验签 + 过期检查
 */
@Component
public class JwtUtil {

    /** Token 有效期（毫秒）：2 小时 */
    private static final long EXPIRATION_MS = 2 * 60 * 60 * 1000L;

    /** 签名密钥（HS256 要求 ≥ 32 字节；必须显式配置，禁止默认兜底） */
    @Value("${app.jwt.secret:}")
    private String secret;

    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32
                || secret.contains("please-change") || secret.equals("thz-spectrum-jwt-secret-key-please-change-2026")) {
            throw new IllegalStateException(
                "app.jwt.secret 未配置或强度不足：请在 application.properties 中设置至少32字节的随机密钥");
        }
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 Token
     * 注意：仅写入非敏感信息（sub/username/iat/exp），严禁放入密码等敏感数据
     */
    public String generateToken(String username, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + EXPIRATION_MS);
        return Jwts.builder()
                .subject(username)
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key())
                .compact();
    }

    /**
     * 解析并验证 Token
     * 验签失败或过期会抛出 JwtException / ExpiredJwtException
     * @return Claims（含 username）
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 便捷：从 Token 取用户名（验证通过的前提）
     */
    public String getUsername(String token) {
        return parseToken(token).get("username", String.class);
    }

    /** 便捷：构造用户上下文 Map，供请求上下文注入 */
    public Map<String, Object> userContext(String username) {
        return Map.of("username", username);
    }
}
