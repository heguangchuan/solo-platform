package com.solo.media.support;

import com.solo.common.ServiceException;
import com.solo.config.JwtProperties;
import com.solo.media.properties.MediaProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ViewTokenSignerTest {

    private static final String JWT_SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String PLAY_SECRET = "abcdef0123456789abcdef0123456789abcdef0123456789";

    private JwtProperties jwtProperties;
    private MediaProperties mediaProperties;
    private ViewTokenSigner signer;
    private ViewActor actor;
    private PlayTarget target;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret(JWT_SECRET);
        mediaProperties = new MediaProperties();
        mediaProperties.getPlayAuth().setTokenTtlSeconds(3600L);
        signer = new ViewTokenSigner(jwtProperties, mediaProperties);
        actor = ViewActor.user(7L, "tester");
        target = PlayTarget.httpFlv("server-1", "rtp", "device_channel");
    }

    @Test
    void signsAndVerifiesEverySupportedProtocol() {
        List<PlayTarget> targets = List.of(
                PlayTarget.httpFlv("server-1", "rtp", "device_channel"),
                PlayTarget.wsFlv("server-1", "rtp", "device_channel"),
                PlayTarget.hls("server-1", "rtp", "device_channel"),
                PlayTarget.rtc("server-1", "rtp", "device_channel"));

        for (PlayTarget current : targets) {
            String token = signer.issueToken(actor, current);
            ViewTokenSigner.ViewToken verified = signer.verify(token, current);

            assertThat(verified).isNotNull();
            assertThat(verified.actorId()).isEqualTo(7L);
            assertThat(verified.actorName()).isEqualTo("tester");
            assertThat(verified.mediaServerId()).isEqualTo(current.mediaServerId());
            assertThat(verified.app()).isEqualTo(current.app());
            assertThat(verified.streamId()).isEqualTo(current.streamId());
            assertThat(verified.schema()).isEqualTo(current.schema());
        }
    }

    @Test
    void verifiesEitherFlvProtocolWhenHookOmitsTransport() {
        PlayTarget wsTarget = PlayTarget.wsFlv("server-1", "rtp", "device_channel");
        List<PlayTarget> candidates = List.of(target, wsTarget);
        String httpToken = signer.issueToken(actor, target);
        String wsToken = signer.issueToken(actor, wsTarget);
        String hlsToken = signer.issueToken(actor, PlayTarget.hls("server-1", "rtp", "device_channel"));

        assertThat(signer.verifyAny(httpToken, candidates)).isNotNull();
        assertThat(signer.verifyAny(wsToken, candidates)).isNotNull();
        assertThat(signer.verifyAny(hlsToken, candidates)).isNull();
    }

    @Test
    void appendsEncodedTokenToPlainAndQueryUrls() {
        String plain = signer.appendToken("http://media/rtp/device_channel.live.flv", actor, target);
        String query = signer.appendToken("http://media/index/api/webrtc?type=play", actor, target);

        String plainToken = plain.substring(plain.indexOf("wt=") + 3);
        String queryToken = query.substring(query.indexOf("wt=") + 3);
        assertThat(plain).contains("?wt=");
        assertThat(query).contains("?type=play&wt=");
        assertThat(signer.verify(URLDecoder.decode(plainToken, StandardCharsets.UTF_8), target)).isNotNull();
        assertThat(signer.verify(URLDecoder.decode(queryToken, StandardCharsets.UTF_8), target)).isNotNull();
        assertThat(signer.appendToken(null, actor, target)).isNull();
        assertThat(signer.appendToken("", actor, target)).isEmpty();
    }

    @Test
    void missingActorCannotProduceBareUrl() {
        assertThatThrownBy(() -> signer.appendToken("http://media/rtp/s.live.flv", null, target))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("播放授权身份");
        assertThatThrownBy(() -> signer.requireCurrentUserActor())
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void rejectsMissingMalformedExpiredAndWrongTypeTokens() {
        String expired = signedToken("play", target, Date.from(Instant.now().minusSeconds(60)), true);
        String wrongType = signedToken("access", target, Date.from(Instant.now().plusSeconds(60)), true);

        assertThat(signer.verify(null, target)).isNull();
        assertThat(signer.verify("", target)).isNull();
        assertThat(signer.verify("not-a-jwt", target)).isNull();
        assertThat(signer.verify(expired, target)).isNull();
        assertThat(signer.verify(wrongType, target)).isNull();
    }

    @Test
    void rejectsLegacyStreamOnlyTokenAndTokenWithoutActor() {
        String legacy = Jwts.builder()
                .claim("typ", "view")
                .claim("at", 1)
                .claim("aid", 7)
                .claim("sid", target.streamId())
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(secretKey(JWT_SECRET))
                .compact();
        String noActor = signedToken("play", target, Date.from(Instant.now().plusSeconds(60)), false);

        assertThat(signer.verify(legacy, target)).isNull();
        assertThat(signer.verify(noActor, target)).isNull();
    }

    @Test
    void rejectsValidlySignedTokenForEveryDifferentTargetDimension() {
        String token = signer.issueToken(actor, target);
        List<PlayTarget> wrongTargets = List.of(
                new PlayTarget("server-2", target.app(), target.streamId(), target.schema(), target.protocol()),
                new PlayTarget(target.mediaServerId(), "live", target.streamId(), target.schema(), target.protocol()),
                new PlayTarget(target.mediaServerId(), target.app(), "other_stream", target.schema(), target.protocol()),
                new PlayTarget(target.mediaServerId(), target.app(), target.streamId(), PlayTarget.SCHEMA_HLS),
                PlayTarget.wsFlv(target.mediaServerId(), target.app(), target.streamId()));

        for (PlayTarget wrongTarget : wrongTargets) {
            assertThat(signer.verify(token, wrongTarget)).isNull();
        }
    }

    @Test
    void tlsAliasesStayInTheirOwnFlvProtocolFamily() {
        String httpToken = signer.issueToken(actor, PlayTarget.httpFlv("server-1", "rtp", "device_channel"));
        String wsToken = signer.issueToken(actor, PlayTarget.wsFlv("server-1", "rtp", "device_channel"));

        assertThat(signer.verify(httpToken,
                new PlayTarget("server-1", "rtp", "device_channel", "rtmp", "https"))).isNotNull();
        assertThat(signer.verify(wsToken,
                new PlayTarget("server-1", "rtp", "device_channel", "rtmp", "wss"))).isNotNull();
        assertThat(signer.verify(httpToken,
                new PlayTarget("server-1", "rtp", "device_channel", "rtmp", "wss"))).isNull();
    }

    @Test
    void disablingViewRecordsDoesNotDisablePlaybackAuthorization() {
        mediaProperties.getViewRecord().setEnabled(false);
        String token = signer.issueToken(actor, target);

        assertThat(signer.verify(token, target)).isNotNull();
        assertThat(signer.verify("bad-token", target)).isNull();
    }

    @Test
    void independentPlaySecretIsUsedAndRotationRejectsOldTokens() {
        mediaProperties.getPlayAuth().setSecret(PLAY_SECRET);
        String token = signer.issueToken(actor, target);
        assertThat(signer.verify(token, target)).isNotNull();

        mediaProperties.getPlayAuth().setSecret(JWT_SECRET + "rotated");
        assertThat(signer.verify(token, target)).isNull();
    }

    @Test
    void newTtlOverridesLegacyViewRecordTtlAndLegacyFallbackStillWorks() {
        mediaProperties.getPlayAuth().setTokenTtlSeconds(60L);
        mediaProperties.getViewRecord().setTokenTtlSeconds(7200L);
        String token = signer.issueToken(actor, target);
        long ttlMillis = claims(token, JWT_SECRET).getExpiration().getTime()
                - claims(token, JWT_SECRET).getIssuedAt().getTime();
        assertThat(ttlMillis).isEqualTo(60_000L);

        mediaProperties.getPlayAuth().setTokenTtlSeconds(null);
        mediaProperties.getViewRecord().setTokenTtlSeconds(30L);
        String fallbackToken = signer.issueToken(actor, target);
        long fallbackTtl = claims(fallbackToken, JWT_SECRET).getExpiration().getTime()
                - claims(fallbackToken, JWT_SECRET).getIssuedAt().getTime();
        assertThat(fallbackTtl).isEqualTo(30_000L);
    }

    @Test
    void invalidSecretAndTtlFailClosed() {
        mediaProperties.getPlayAuth().setTokenTtlSeconds(0L);
        assertThatThrownBy(() -> signer.issueToken(actor, target))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("必须大于 0");

        mediaProperties.getPlayAuth().setTokenTtlSeconds(60L);
        mediaProperties.getPlayAuth().setSecret("too-short");
        assertThatThrownBy(() -> signer.issueToken(actor, target))
                .isInstanceOf(RuntimeException.class);

        mediaProperties.getPlayAuth().setSecret("");
        jwtProperties.setSecret("");
        assertThatThrownBy(() -> signer.issueToken(actor, target))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("密钥未配置");
    }

    private String signedToken(String type, PlayTarget tokenTarget, Date expiration, boolean includeActor) {
        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                .claim("typ", type)
                .claim("ms", tokenTarget.mediaServerId())
                .claim("app", tokenTarget.app())
                .claim("sid", tokenTarget.streamId())
                .claim("sch", tokenTarget.schema())
                .claim("pr", tokenTarget.protocol())
                .issuedAt(Date.from(expiration.toInstant().minusSeconds(120)))
                .expiration(expiration)
                .signWith(secretKey(JWT_SECRET));
        if (includeActor) {
            builder.claim("at", 1).claim("aid", 7).claim("an", "tester");
        }
        return builder.compact();
    }

    private static io.jsonwebtoken.Claims claims(String token, String secret) {
        return Jwts.parser().verifyWith(secretKey(secret)).build().parseSignedClaims(token).getPayload();
    }

    private static SecretKey secretKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
