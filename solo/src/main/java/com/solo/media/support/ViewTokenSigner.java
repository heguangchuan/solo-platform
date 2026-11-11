package com.solo.media.support;

import com.solo.common.utils.UserHolderUtils;
import com.solo.config.JwtProperties;
import com.solo.media.properties.MediaProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.crypto.SecretKey;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 播放地址上的观看令牌 wt：点播时按观看者签，ZLM on_play 回调时验签认人。
 * 验签不过只跳过记录，不拦播放。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ViewTokenSigner {

    /** URL 参数名 */
    public static final String PARAM = "wt";

    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_VIEW = "view";
    private static final String CLAIM_ACTOR_TYPE = "at";
    private static final String CLAIM_ACTOR_ID = "aid";
    private static final String CLAIM_ACTOR_NAME = "an";
    private static final String CLAIM_STREAM = "sid";

    private final JwtProperties jwtProperties;
    private final MediaProperties mediaProperties;

    /** 给播放地址拼令牌；开关关了或没有观看者就原样返回 */
    public String appendToken(String url, ViewActor actor, String streamId) {
        if (url == null || url.isBlank() || actor == null || !mediaProperties.getViewRecord().isEnabled()) {
            return url;
        }
        String token = sign(actor, streamId);
        char sep = url.indexOf('?') >= 0 ? '&' : '?';
        // HLS 切片请求不带 query 无所谓，on_play 在 m3u8 建会话时只来一次
        return url + sep + PARAM + "=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    /** 验签，过不了统一返 null（过期、签名错、流对不上、格式坏） */
    public ViewToken verify(String token, String expectedStreamId) {
        if (token == null || token.isBlank() || expectedStreamId == null) {
            return null;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!TYPE_VIEW.equals(claims.get(CLAIM_TYPE))) {
                return null;
            }
            if (!expectedStreamId.equals(claims.get(CLAIM_STREAM))) {
                log.warn("[观看令牌] 流绑定不一致 expect={} actual={}", expectedStreamId, claims.get(CLAIM_STREAM));
                return null;
            }
            Number actorType = claims.get(CLAIM_ACTOR_TYPE, Number.class);
            Number actorId = claims.get(CLAIM_ACTOR_ID, Number.class);
            String actorName = claims.get(CLAIM_ACTOR_NAME, String.class);
            if (actorType == null || actorId == null) {
                return null;
            }
            return new ViewToken(actorType.intValue(), actorId.longValue(), actorName,
                    claims.get(CLAIM_STREAM, String.class));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("[观看令牌] 校验失败 reason={}", e.getMessage());
            return null;
        }
    }

    /** 当前请求上的平台用户；非 HTTP 线程（级联取流、按需拉流）返 null */
    public ViewActor currentUserActor() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes)) {
            return null;
        }
        Long userId = UserHolderUtils.getCurrentUserId();
        if (userId == null) {
            return null;
        }
        return ViewActor.user(userId, UserHolderUtils.getCurrentUsername());
    }

    private String sign(ViewActor actor, String streamId) {
        long now = System.currentTimeMillis();
        long exp = now + mediaProperties.getViewRecord().getTokenTtlSeconds() * 1000L;
        return Jwts.builder()
                .claim(CLAIM_TYPE, TYPE_VIEW)
                .claim(CLAIM_ACTOR_TYPE, actor.actorType())
                .claim(CLAIM_ACTOR_ID, actor.actorId())
                .claim(CLAIM_ACTOR_NAME, actor.actorName())
                .claim(CLAIM_STREAM, streamId)
                .issuedAt(new Date(now))
                .expiration(new Date(exp))
                .signWith(secretKey())
                .compact();
    }

    private SecretKey secretKey() {
        // 与 JwtUtils 同口径：secret 字符串的 UTF-8 字节直接作为 HMAC 密钥
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** 验签通过后的令牌内容，比 ViewActor 多带绑定的流 */
    public record ViewToken(int actorType, long actorId, String actorName, String streamId) {
    }
}
