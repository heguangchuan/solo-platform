package com.solo.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * JWT工具类，负责生成、解析和验证JWT Token
 *
 * <p>Redis 数据结构：
 * <ul>
 *   <li>{@code auth:online:{username}}：Hash，存放当前会话信息（token / userId / nickname / loginTime / loginIp / userAgent / clientSource）</li>
 *   <li>{@code auth:online}：Set，所有在线 username 的索引，便于枚举在线用户</li>
 * </ul>
 */
@Component
public class JwtUtils {

    /** 单个用户在线信息 hash 的 key 前缀 */
    public static final String ONLINE_KEY_PREFIX = "auth:online:";
    /** 在线用户名集合 key */
    public static final String ONLINE_INDEX_KEY = "auth:online";

    /** Hash 字段名 */
    public static final String FIELD_TOKEN = "token";
    public static final String FIELD_USER_ID = "userId";
    public static final String FIELD_NICKNAME = "nickname";
    public static final String FIELD_LOGIN_TIME = "loginTime";
    public static final String FIELD_LOGIN_IP = "loginIp";
    public static final String FIELD_USER_AGENT = "userAgent";
    public static final String FIELD_CLIENT_SOURCE = "clientSource";

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
     */
    public String getUsernameFromToken(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * 从Token中获取过期时间
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
     */
    public boolean validateToken(String token, String username) {
        String tokenUsername = getUsernameFromToken(token);
        return tokenUsername.equals(username) && !isTokenExpired(token);
    }

    /**
     * 判断Token是否过期
     */
    public boolean isTokenExpired(String token) {
        return getClaims(token).getExpiration().before(new Date());
    }

    /**
     * 解析Token获取Claims
     */
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /* ============================== 会话存储 ============================== */

    /**
     * 保存会话信息（登录时调用）
     */
    public void saveSession(String username, String token, Map<String, Object> meta) {
        String key = ONLINE_KEY_PREFIX + username;
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_TOKEN, token);
        if (meta != null) data.putAll(meta);
        redisTemplate.opsForHash().putAll(key, data);
        redisTemplate.expire(key, expiration, TimeUnit.MILLISECONDS);
        redisTemplate.opsForSet().add(ONLINE_INDEX_KEY, username);
    }

    /**
     * 简化版：仅写 token，无元数据。保留兼容旧调用。
     */
    public void saveToken(String username, String token) {
        saveSession(username, token, null);
    }

    /**
     * 根据用户名获取当前Token
     */
    public String getToken(String username) {
        Object token = redisTemplate.opsForHash().get(ONLINE_KEY_PREFIX + username, FIELD_TOKEN);
        return token != null ? token.toString() : null;
    }

    /**
     * 获取整张会话 hash（用于在线用户列表）
     */
    public Map<Object, Object> getSession(String username) {
        return redisTemplate.opsForHash().entries(ONLINE_KEY_PREFIX + username);
    }

    /**
     * 枚举所有在线用户名
     */
    public Set<String> listOnlineUsernames() {
        Set<Object> raw = redisTemplate.opsForSet().members(ONLINE_INDEX_KEY);
        Set<String> result = new LinkedHashSet<>();
        if (raw == null) return result;
        for (Object o : raw) {
            if (o != null) result.add(o.toString());
        }
        return result;
    }

    /**
     * 删除用户的Token及在线索引（踢人下线 / 自主登出时调用）
     */
    public void removeToken(String username) {
        redisTemplate.delete(ONLINE_KEY_PREFIX + username);
        redisTemplate.opsForSet().remove(ONLINE_INDEX_KEY, username);
    }
}
