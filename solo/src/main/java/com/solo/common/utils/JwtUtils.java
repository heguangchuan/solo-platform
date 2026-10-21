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

@Component
public class JwtUtils {

    /** 单个用户在线信息 hash 的 key 前缀 */
    public static final String ONLINE_KEY_PREFIX = "auth:online:";
    /** 在线用户名集合 key */
    public static final String ONLINE_INDEX_KEY = "auth:online";
    /** 单个用户当前 refresh token 的 key 前缀 */
    public static final String REFRESH_KEY_PREFIX = "auth:refresh:";

    /** 在线信息 hash 的字段名 */
    public static final String FIELD_TOKEN = "token";
    public static final String FIELD_USER_ID = "userId";
    public static final String FIELD_NICKNAME = "nickname";
    public static final String FIELD_LOGIN_TIME = "loginTime";
    public static final String FIELD_LOGIN_IP = "loginIp";
    public static final String FIELD_USER_AGENT = "userAgent";
    public static final String FIELD_CLIENT_SOURCE = "clientSource";

    /** token 类型 claim：access / refresh，防止两类 token 互用 */
    public static final String CLAIM_TOKEN_TYPE = "type";
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtProperties jwtProperties;

    public JwtUtils(RedisTemplate<String, Object> redisTemplate, JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.jwtProperties = jwtProperties;
    }

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }


    public String generateToken(String username) {
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getAccessExpiration()))
                .signWith(getSecretKey())
                .compact();
    }


    public String generateRefreshToken(String username) {
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getRefreshExpiration()))
                .signWith(getSecretKey())
                .compact();
    }


    public String getUsernameFromToken(String token) {
        return getClaims(token).getSubject();
    }

    public Date getExpirationFromToken(String token) {
        return getClaims(token).getExpiration();
    }

    public Claims getClaimsFromToken(String token) {
        return getClaims(token);
    }

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

    public boolean isTokenExpired(String token) {
        return getClaims(token).getExpiration().before(new Date());
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /* ============================== 会话存储（access token） ============================== */

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

    public String getRefreshToken(String username) {
        Object v = redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + username);
        return v != null ? v.toString() : null;
    }

    public void removeRefreshToken(String username) {
        redisTemplate.delete(REFRESH_KEY_PREFIX + username);
    }
}
