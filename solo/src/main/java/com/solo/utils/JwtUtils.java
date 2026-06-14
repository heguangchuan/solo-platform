package com.solo.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.Date;

/**
 * JWT工具类，负责生成、解析和验证JWT Token
 */
@Component
public class JwtUtils {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    public JwtUtils(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 生成密钥
     */
    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成JWT Token
     *
     * @param username 用户名
     * @return JWT Token字符串
     */
    public String generateToken(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSecretKey())
                .compact();
    }

    /**
     * 从Token中获取用户名
     *
     * @param token JWT Token
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * 从Token中获取过期时间
     *
     * @param token JWT Token
     * @return 过期时间
     */
    public Date getExpirationFromToken(String token) {
        return getClaims(token).getExpiration();
    }

    /**
     * 从Token中获取完整的Claims对象
     */
    public Claims getClaimsFromToken(String token) {
        return getClaims(token);
    }

    /**
     * 验证Token是否有效
     *
     * @param token    JWT Token
     * @param username 用户名（用于比对）
     * @return 是否有效
     */
    public boolean validateToken(String token, String username) {
        String tokenUsername = getUsernameFromToken(token);
        return tokenUsername.equals(username) && !isTokenExpired(token);
    }

    /**
     * 判断Token是否过期
     *
     * @param token JWT Token
     * @return 是否已过期
     */
    public boolean isTokenExpired(String token) {
        return getClaims(token).getExpiration().before(new Date());
    }

    /**
     * 解析Token获取Claims
     *
     * @param token JWT Token
     * @return Claims对象
     */
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 将Token存入Redis（登录时调用）
     *
     * @param username 用户名
     * @param token    JWT Token
     */
    public void saveToken(String username, String token) {
        String key = "auth:token:" + username;
        redisTemplate.opsForValue().set(key, token, expiration, TimeUnit.MILLISECONDS);
    }

    /**
     * 根据用户名获取当前Token（拦截器校验时调用）
     *
     * @param username 用户名
     * @return Token字符串，不存在则返回null
     */
    public String getToken(String username) {
        String key = "auth:token:" + username;
        Object token = redisTemplate.opsForValue().get(key);
        return token != null ? token.toString() : null;
    }

    /**
     * 删除用户的Token（踢人下线时调用）
     *
     * @param username 用户名
     */
    public void removeToken(String username) {
        String key = "auth:token:" + username;
        redisTemplate.delete(key);
    }
}
