package com.solo.media.support;

import com.solo.common.ServiceException;
import com.solo.common.utils.UserHolderUtils;
import com.solo.config.JwtProperties;
import com.solo.media.properties.MediaProperties;
import com.solo.system.constant.SystemConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.crypto.SecretKey;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Objects;

// wt 绑死观看者、节点、app、流和播放方式，ZLM 回调时逐项对。
// 它只拦新播放，已经连上的不会因为 wt 到期被踢。
@Slf4j
@Component
@RequiredArgsConstructor
public class ViewTokenSigner {

    public static final String PARAM = "wt";

    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_PLAY = "play";
    private static final String CLAIM_ACTOR_TYPE = "at";
    private static final String CLAIM_ACTOR_ID = "aid";
    private static final String CLAIM_ACTOR_NAME = "an";
    private static final String CLAIM_MEDIA_SERVER = "ms";
    private static final String CLAIM_APP = "app";
    private static final String CLAIM_STREAM = "sid";
    private static final String CLAIM_SCHEMA = "sch";
    private static final String CLAIM_PROTOCOL = "pr";

    private final JwtProperties jwtProperties;
    private final MediaProperties mediaProperties;

    // 没身份或目标就直接抛，别退回裸地址
    public String appendToken(String url, ViewActor actor, PlayTarget target) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String token = issueToken(actor, target);
        char sep = url.indexOf('?') >= 0 ? '&' : '?';
        return url + sep + PARAM + "=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    public String issueToken(ViewActor actor, PlayTarget target) {
        if (actor == null || actor.actorId() == null) {
            throw new ServiceException(SystemConstants.HTTP_UNAUTHORIZED, "缺少播放授权身份");
        }
        Objects.requireNonNull(target, "播放令牌目标不能为空");
        long ttlSeconds = tokenTtlSeconds();
        long now = System.currentTimeMillis();
        final long expiresAt;
        try {
            expiresAt = Math.addExact(now, Math.multiplyExact(ttlSeconds, 1000L));
        } catch (ArithmeticException e) {
            throw new IllegalStateException("播放令牌有效期超出支持范围", e);
        }
        return Jwts.builder()
                .claim(CLAIM_TYPE, TYPE_PLAY)
                .claim(CLAIM_ACTOR_TYPE, actor.actorType())
                .claim(CLAIM_ACTOR_ID, actor.actorId())
                .claim(CLAIM_ACTOR_NAME, actor.actorName())
                .claim(CLAIM_MEDIA_SERVER, target.mediaServerId())
                .claim(CLAIM_APP, target.app())
                .claim(CLAIM_STREAM, target.streamId())
                .claim(CLAIM_SCHEMA, target.schema())
                .claim(CLAIM_PROTOCOL, target.protocol())
                .issuedAt(new Date(now))
                .expiration(new Date(expiresAt))
                .signWith(secretKey())
                .compact();
    }

    // 过期、签名错、目标对不上或报文坏了，都按没通过处理
    public ViewToken verify(String token, PlayTarget expected) {
        return expected == null ? null : verifyAny(token, List.of(expected));
    }

    public ViewToken verifyAny(String token, List<PlayTarget> expected) {
        if (token == null || token.isBlank() || expected == null || expected.isEmpty()
                || expected.stream().anyMatch(Objects::isNull)) {
            return null;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!TYPE_PLAY.equals(claims.get(CLAIM_TYPE))) {
                return null;
            }
            String mediaServerId = claims.get(CLAIM_MEDIA_SERVER, String.class);
            String app = claims.get(CLAIM_APP, String.class);
            String streamId = claims.get(CLAIM_STREAM, String.class);
            String schema = claims.get(CLAIM_SCHEMA, String.class);
            String protocol = claims.get(CLAIM_PROTOCOL, String.class);
            boolean matched = expected.stream().anyMatch(target ->
                    target.mediaServerId().equals(mediaServerId)
                            && target.app().equals(app)
                            && target.streamId().equals(streamId)
                            && target.schema().equals(schema)
                            && target.protocol().equals(protocol));
            if (!matched) {
                log.warn("[播放鉴权] 令牌目标不一致 expected={} actual={}/{}/{}/{}/{}",
                        expected, mediaServerId, app, streamId, schema, protocol);
                return null;
            }
            Number actorType = claims.get(CLAIM_ACTOR_TYPE, Number.class);
            Number actorId = claims.get(CLAIM_ACTOR_ID, Number.class);
            String actorName = claims.get(CLAIM_ACTOR_NAME, String.class);
            if (actorType == null || actorId == null) {
                return null;
            }
            return new ViewToken(actorType.intValue(), actorId.longValue(), actorName,
                    mediaServerId, app, streamId, schema);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("[播放鉴权] 令牌校验失败 reason={}", e.getMessage());
            return null;
        }
    }

    public ViewActor requireCurrentUserActor() {
        ViewActor actor = currentUserActor();
        if (actor == null) {
            throw new ServiceException(SystemConstants.HTTP_UNAUTHORIZED, "缺少平台用户播放授权身份");
        }
        return actor;
    }

    private ViewActor currentUserActor() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes)) {
            return null;
        }
        Long userId = UserHolderUtils.getCurrentUserId();
        if (userId == null) {
            return null;
        }
        return ViewActor.user(userId, UserHolderUtils.getCurrentUsername());
    }

    private long tokenTtlSeconds() {
        Long configured = mediaProperties.getPlayAuth().getTokenTtlSeconds();
        long ttl = configured != null ? configured : mediaProperties.getViewRecord().getTokenTtlSeconds();
        if (ttl <= 0) {
            throw new IllegalStateException("media.play-auth.token-ttl-seconds 必须大于 0");
        }
        return ttl;
    }

    private SecretKey secretKey() {
        String configured = mediaProperties.getPlayAuth().getSecret();
        String secret = StringUtils.hasText(configured) ? configured : jwtProperties.getSecret();
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("播放鉴权密钥未配置，请设置 media.play-auth.secret 或 jwt.secret");
        }
        // 和 JwtUtils 一样，直接拿 secret 的 UTF-8 字节做 HMAC 密钥
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public record ViewToken(int actorType, long actorId, String actorName,
                            String mediaServerId, String app, String streamId, String schema) {
    }
}
