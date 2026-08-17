package com.solo.common.utils;

import com.solo.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * JWT工具类，负责生成、解析和验证 access / refresh Token
 *
 * <p>Token 类型：
 * <ul>
 *   <li>access token：业务请求所携带，短期（默认 30 分钟）</li>
 *   <li>refresh token：登录后由前端单独保存，仅用于换发新的 access token，长期（默认 7 天）</li>
 * </ul>
 *
 * <p>Redis 数据结构：
 * <ul>
 *   <li>{@code auth:online:{username}}：Hash，存放当前会话信息（token / userId / nickname / loginTime / loginIp / userAgent / clientSource），TTL = access 有效期</li>
 *   <li>{@code auth:online}：Set，所有在线 username 的索引，便于枚举在线用户</li>
 *   <li>{@code auth:refresh:{username}}：String，当前用户的 refresh token，TTL = refresh 有效期；滚动刷新时被新 refresh token 覆盖</li>
 * </ul>
 */
@Component
public class JwtUtils {

    /** 单个用户在线信息 hash 的 key 前缀 */
    public static final String ONLINE_KEY_PREFIX = "auth:online:";
    /** 在线用户名集合 key */
    public static final String ONLINE_INDEX_KEY = "auth:online";
    /** 单个用户当前 refresh token 的 key 前缀 */
    public static final String REFRESH_KEY_PREFIX = "auth:refresh:";

    /** Hash 字段名 */
    public static final String FIELD_TOKEN = "token";
    public static final String FIELD_USER_ID = "userId";
    public static final String FIELD_NICKNAME = "nickname";
    public static final String FIELD_LOGIN_TIME = "loginTime";
    public static final String FIELD_LOGIN_IP = "loginIp";
    public static final String FIELD_USER_AGENT = "userAgent";
    public static final String FIELD_CLIENT_SOURCE = "clientSource";

    /** Token 类型 claim：access / refresh，防止两类 token 互用 */
    public static final String CLAIM_TOKEN_TYPE = "type";
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtProperties jwtProperties;

    public JwtUtils(RedisTemplate<String, Object> redisTemplate, JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.jwtProperties = jwtProperties;
    }

    /**
     * 生成密钥
     */
    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /* ============================== Token 生成 ============================== */

    /**
     * 生成 access token
     */
    public String generateToken(String username) {
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getAccessExpiration()))
                .signWith(getSecretKey())
                .compact();
    }

    /**
     * 生成 refresh token
     */
    public String generateRefreshToken(String username) {
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getRefreshExpiration()))
                .signWith(getSecretKey())
                .compact();
    }

    /* ============================== Token 解析 ============================== */

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
     * 获取 token 的类型（access / refresh），缺失或异常返回 null
     */
    public String getTokenType(String token) {
        Object type = getClaims(token).get(CLAIM_TOKEN_TYPE);
        return type != null ? type.toString() : null;
    }

    /**
     * 验证 access token 是否有效（用户名匹配 + 未过期 + type=access）
     */
    public boolean validateToken(String token, String username) {
        try {
            Claims claims = getClaims(token);
            if (!username.equals(claims.getSubject())) return false;
            if (claims.getExpiration().before(new Date())) return false;
            Object type = claims.get(CLAIM_TOKEN_TYPE);
            // 兼容历史无 type claim 的旧 token：缺失视为 access
            return type == null || TOKEN_TYPE_ACCESS.equals(type.toString());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 验证 refresh token 是否有效：签名 + 未过期 + type=refresh + Redis 中保存的 refresh token 与之一致（防止旧 refresh 被滚动刷新失效后再次使用）
     */
    public boolean validateRefreshToken(String token, String username) {
        try {
            Claims claims = getClaims(token);
            if (!username.equals(claims.getSubject())) return false;
            if (claims.getExpiration().before(new Date())) return false;
            Object type = claims.get(CLAIM_TOKEN_TYPE);
            if (type == null || !TOKEN_TYPE_REFRESH.equals(type.toString())) return false;
            String cached = getRefreshToken(username);
            return cached != null && cached.equals(token);
        } catch (Exception e) {
            return false;
        }
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

    /* ============================== 会话存储（access token） ============================== */

    /**
     * 保存会话信息（登录时调用）
     */
    public void saveSession(String username, String token, Map<String, Object> meta) {
        String key = ONLINE_KEY_PREFIX + username;
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_TOKEN, token);
        if (meta != null) data.putAll(meta);
        redisTemplate.opsForHash().putAll(key, data);
        redisTemplate.expire(key, Duration.ofMillis(jwtProperties.getAccessExpiration()));
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
     * 删除用户的Token及在线索引（踢人下线 / 自主登出时调用），同时清理 refresh token 防止对方再换发
     */
    public void removeToken(String username) {
        redisTemplate.delete(ONLINE_KEY_PREFIX + username);
        redisTemplate.opsForSet().remove(ONLINE_INDEX_KEY, username);
        removeRefreshToken(username);
    }

    /* ============================== 会话存储（refresh token） ============================== */

    /**
     * 保存 / 覆盖 refresh token（滚动刷新会重复覆盖）
     */
    public void saveRefreshToken(String username, String refreshToken) {
        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + username,
                refreshToken,
                Duration.ofMillis(jwtProperties.getRefreshExpiration()));
    }

    /**
     * 获取当前用户的 refresh token，不存在返回 null
     */
    public String getRefreshToken(String username) {
        Object v = redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + username);
        return v != null ? v.toString() : null;
    }

    /**
     * 删除当前用户的 refresh token
     */
    public void removeRefreshToken(String username) {
        redisTemplate.delete(REFRESH_KEY_PREFIX + username);
    }
}
