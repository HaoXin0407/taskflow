package com.taskflow.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 令牌工具类。
 *
 * <p>JWT 由三段组成：Header.Payload.Signature，这里用到 jjwt 0.12 的新 API。</p>
 * <p>Payload 中放 userId / username，前端每次请求带上令牌，后端解析后即可识别用户，
 * 无需 Session —— 这就是「无状态认证」的核心价值。</p>
 */
@Slf4j
@Component
public class JwtUtils {

    @Value("${taskflow.jwt.secret}")
    private String secret;

    @Value("${taskflow.jwt.expire}")
    private Long expire;

    @Value("${taskflow.jwt.header}")
    private String header;

    @Value("${taskflow.jwt.prefix}")
    private String prefix;

    /**
     * 缓存的签名密钥。
     * <p>用 volatile 是因为这个方法会被多个请求线程并发调用，
     * 而密钥是懒加载的 —— 不加 volatile 可能出现「多个线程同时看到 null，
     * 各自创建一份密钥」的情况，浪费且难以排查。</p>
     */
    private volatile SecretKey signKey;

    /**
     * 生成签名密钥。
     *
     * <p>HS256 算法要求密钥长度必须 >= 32 字节，否则 jjwt 会抛
     * WeakKeyException。如果配置里的 secret 不足 32 字节，
     * 用 SHA-256 做一次摘要派生出 32 字节，保证强度。</p>
     *
     * <p>注意：MessageDigest.getInstance 声明的是受检异常 NoSuchAlgorithmException，
     * 必须捕获或声明，否则编译报「未报告的异常」。</p>
     */
    private SecretKey key() {
        // 双重检查锁定：避免每次请求都加锁
        if (signKey == null) {
            synchronized (this) {
                if (signKey == null) {
                    signKey = buildKey();
                }
            }
        }
        return signKey;
    }

    /**
     * 实际构建密钥，失败时统一抛 IllegalStateException（免检异常）。
     */
    private SecretKey buildKey() {
        try {
            byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);

            // 不足 32 字节则用 SHA-256 摘要派生（摘要固定输出 32 字节）
            if (keyBytes.length < 32) {
                log.warn("JWT 密钥长度不足 32 字节（当前 {} 字节），已使用 SHA-256 派生补足。" +
                        "生产环境请配置更长的密钥。", keyBytes.length);
                keyBytes = MessageDigest.getInstance("SHA-256").digest(keyBytes);
            }

            return Keys.hmacShaKeyFor(keyBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 标准算法，不可能找不到；真出现说明运行环境异常
            throw new IllegalStateException("当前 JVM 不支持 SHA-256 算法", e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("生成 JWT 签名密钥失败，请检查 taskflow.jwt.secret 配置", e);
        }
    }

    /**
     * 生成令牌。
     *
     * @param userId   用户ID
     * @param username 登录账号
     * @return JWT 字符串
     */
    public String generateToken(Long userId, String username) {
        Map<String, Object> claims = new HashMap<>(4);
        claims.put("userId", userId);
        claims.put("username", username);

        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expire);

        return Jwts.builder()
                .claims(claims)          // jjwt 0.12 推荐用 claims()，setClaims() 已废弃
                .subject(username)       // 同理，subject() 取代 setSubject()
                .issuedAt(now)
                .expiration(expireDate)
                .signWith(key(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析令牌，失败返回 null。
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(stripPrefix(token))
                    .getPayload();
        } catch (Exception e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从令牌中取用户ID，失败返回 null。
     */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        Object userId = claims.get("userId");
        return userId == null ? null : Long.valueOf(userId.toString());
    }

    /**
     * 从令牌中取登录账号。
     */
    public String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims == null ? null : claims.getSubject();
    }

    /**
     * 去掉 "Bearer " 前缀。
     */
    public String stripPrefix(String token) {
        if (token == null) {
            return null;
        }
        if (token.startsWith(prefix)) {
            return token.substring(prefix.length());
        }
        return token;
    }

    /**
     * 判断令牌是否已过期。
     */
    public boolean isExpired(String token) {
        Claims claims = parseToken(token);
        return claims == null || claims.getExpiration().before(new Date());
    }

    public String getHeader() {
        return header;
    }

    public String getPrefix() {
        return prefix;
    }

    public Long getExpire() {
        return expire;
    }
}