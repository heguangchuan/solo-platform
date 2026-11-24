package com.solo.gb28181.auth;

import com.solo.gb28181.constant.GbRedisKeys;
import gov.nist.javax.sip.message.SIPRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;

import javax.sip.SipFactory;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.AuthorizationHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.WWWAuthenticateHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DigestServerAuthenticationHelperTest {

    private static final String DEVICE_ID = "34020000001320000001";
    private static final String ANOTHER_DEVICE_ID = "34020000001320000002";
    private static final String REALM = "3402000000";
    private static final String PASSWORD = "test-password";
    private static final String NONCE = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String CNONCE = "device-cnonce";
    private static final String NC = "0000000A";
    private static final Duration NONCE_TTL = Duration.ofMinutes(5);

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private DigestServerAuthenticationHelper helper;
    private HeaderFactory headerFactory;
    private AddressFactory addressFactory;
    private SIPRequest request;
    private AuthorizationHeader authorization;
    private SipURI requestUri;

    @BeforeEach
    void setUp() throws Exception {
        helper = new DigestServerAuthenticationHelper(redisTemplate);

        SipFactory sipFactory = SipFactory.getInstance();
        sipFactory.setPathName("gov.nist");
        headerFactory = sipFactory.createHeaderFactory();
        addressFactory = sipFactory.createAddressFactory();

        requestUri = addressFactory.createSipURI(DEVICE_ID, "192.0.2.10:5060");
        request = new SIPRequest();
        request.setMethod(Request.REGISTER);
        request.setRequestURI(requestUri);

        // 使用真实 SIP 对象，避免 mock 的 toString() 掩盖 URI 和摘要计算问题。
        authorization = headerFactory.createAuthorizationHeader("Digest");
        authorization.setUsername(DEVICE_ID);
        authorization.setRealm(REALM);
        authorization.setNonce(NONCE);
        authorization.setURI(requestUri);
        authorization.setAlgorithm("MD5");
        authorization.setQop("auth");
        authorization.setCNonce(CNONCE);
        authorization.setParameter("nc", NC);
        request.setHeader(authorization);
    }

    @Test
    void acceptsValidQopAuthDigestAndUppercaseResponse() throws Exception {
        givenIssuedNonce(NONCE, DEVICE_ID, REALM);
        respondUsing(PASSWORD);
        authorization.setResponse(authorization.getResponse().toUpperCase(Locale.ROOT));

        assertThat(authenticate()).isTrue();
    }

    @Test
    void acceptsLegacyDigestWithoutQop() throws Exception {
        authorization.removeParameter("qop");
        givenIssuedNonce(NONCE, DEVICE_ID, REALM);
        respondUsing(PASSWORD);

        assertThat(authenticate()).isTrue();
    }

    @Test
    void rejectsMissingAuthorizationHeader() {
        request.removeHeader(AuthorizationHeader.NAME);

        assertThat(authenticate()).isFalse();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void rejectsDigestCalculatedWithWrongPassword() throws Exception {
        givenIssuedNonce(NONCE, DEVICE_ID, REALM);
        respondUsing("wrong-password");

        assertThat(authenticate()).isFalse();
    }

    @Test
    void rejectsNonceNotIssuedByPlatform() throws Exception {
        givenStoredNonceBinding(NONCE, null);
        respondUsing(PASSWORD);

        assertThat(authenticate()).isFalse();
    }

    @Test
    void rejectsNonceIssuedToAnotherDevice() throws Exception {
        givenIssuedNonce(NONCE, ANOTHER_DEVICE_ID, REALM);
        respondUsing(PASSWORD);

        assertThat(authenticate()).isFalse();
    }

    @Test
    void rejectsNonceIssuedToAnotherRealm() throws Exception {
        givenIssuedNonce(NONCE, DEVICE_ID, "another-realm");
        respondUsing(PASSWORD);

        assertThat(authenticate()).isFalse();
    }

    @Test
    void rejectsMismatchedUsernameEvenWhenDigestUsesThatUsername() throws Exception {
        authorization.setUsername(ANOTHER_DEVICE_ID);
        respondUsing(PASSWORD);

        assertThat(authenticate()).isFalse();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void rejectsMismatchedRealmEvenWhenDigestUsesThatRealm() throws Exception {
        authorization.setRealm("another-realm");
        respondUsing(PASSWORD);

        assertThat(authenticate()).isFalse();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void rejectsAuthorizationUriDifferentFromRequestUriEvenWhenDigestUsesThatUri() throws Exception {
        authorization.setURI(addressFactory.createSipURI(DEVICE_ID, "192.0.2.20:5060"));
        respondUsing(PASSWORD);

        assertThat(authenticate()).isFalse();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void rejectsUnsupportedQopEvenWhenDigestUsesThatQop() throws Exception {
        authorization.setQop("auth-int");
        givenIssuedNonce(NONCE, DEVICE_ID, REALM);
        respondUsing(PASSWORD);

        assertThat(authenticate()).isFalse();
    }

    @Test
    void rejectsMissingCnonce() throws Exception {
        authorization.removeParameter("cnonce");
        givenIssuedNonce(NONCE, DEVICE_ID, REALM);
        respondUsing(PASSWORD);

        assertThat(authenticate()).isFalse();
    }

    @Test
    void rejectsInvalidNonceCount() throws Exception {
        authorization.setParameter("nc", "1");
        givenIssuedNonce(NONCE, DEVICE_ID, REALM);
        respondUsing(PASSWORD);

        assertThat(authenticate()).isFalse();
    }

    @Test
    void rejectsUnsupportedAlgorithm() throws Exception {
        authorization.setAlgorithm("SHA-256");
        respondUsing(PASSWORD);

        assertThat(authenticate()).isFalse();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void rejectsMalformedNonceBeforeReadingRedis() throws Exception {
        authorization.setNonce("not-a-platform-nonce");
        respondUsing(PASSWORD);

        assertThat(authenticate()).isFalse();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void rejectsMalformedResponse() throws Exception {
        givenIssuedNonce(NONCE, DEVICE_ID, REALM);
        authorization.setResponse("not-an-md5-response");

        assertThat(authenticate()).isFalse();
    }

    @Test
    void propagatesRedisFailureInsteadOfAcceptingRegistration() throws Exception {
        respondUsing(PASSWORD);
        RedisConnectionFailureException failure = new RedisConnectionFailureException("Redis unavailable");
        when(redisTemplate.opsForValue()).thenThrow(failure);

        assertThatThrownBy(this::authenticate).isSameAs(failure);
    }

    @Test
    void challengeStoresRandomNonceBoundToDeviceAndRealm() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Response response = mock(Response.class);

        helper.appendChallenge(headerFactory, response, DEVICE_ID, REALM);

        ArgumentCaptor<WWWAuthenticateHeader> challengeCaptor = ArgumentCaptor.forClass(WWWAuthenticateHeader.class);
        verify(response).setHeader(challengeCaptor.capture());
        WWWAuthenticateHeader challenge = challengeCaptor.getValue();
        String nonce = challenge.getNonce();
        assertThat(nonce).matches("[0-9a-f]{64}");
        assertThat(challenge.getRealm()).isEqualTo(REALM);
        assertThat(challenge.getQop()).isEqualTo("auth");
        assertThat(challenge.getAlgorithm()).isEqualTo("MD5");
        verify(valueOperations).set(nonceKey(nonce), nonceBinding(DEVICE_ID, REALM), NONCE_TTL);
    }

    @Test
    void doesNotAddChallengeWhenNonceCannotBeStored() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        RedisConnectionFailureException failure = new RedisConnectionFailureException("Redis unavailable");
        doThrow(failure).when(valueOperations).set(anyString(), anyString(), eq(NONCE_TTL));
        Response response = mock(Response.class);

        assertThatThrownBy(() -> helper.appendChallenge(headerFactory, response, DEVICE_ID, REALM))
                .isSameAs(failure);
        verifyNoInteractions(response);
    }

    private boolean authenticate() {
        return helper.authenticate(request, DEVICE_ID, REALM, PASSWORD);
    }

    private void givenIssuedNonce(String nonce, String deviceId, String realm) {
        givenStoredNonceBinding(nonce, nonceBinding(deviceId, realm));
    }

    private void givenStoredNonceBinding(String nonce, String binding) {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(nonceKey(nonce))).thenReturn(binding);
    }

    /**
     * 按 Authorization 头的当前内容计算摘要。负向用例先篡改字段再调用此方法，
     * 确保拒绝原因是对应校验规则，而不是沿用了旧字段导致的摘要不匹配。
     */
    private void respondUsing(String password) throws Exception {
        String ha1 = md5(authorization.getUsername() + ":" + authorization.getRealm() + ":" + password);
        String ha2 = md5(request.getMethod().toUpperCase(Locale.ROOT) + ":" + authorization.getURI());
        StringBuilder kd = new StringBuilder(ha1).append(':').append(authorization.getNonce());
        String qop = authorization.getQop();
        if (qop != null && !qop.isBlank()) {
            kd.append(':').append(authorization.getParameter("nc"))
                    .append(':').append(authorization.getCNonce())
                    .append(':').append(qop);
        }
        kd.append(':').append(ha2);
        authorization.setResponse(md5(kd.toString()));
    }

    private static String nonceKey(String nonce) {
        return GbRedisKeys.REGISTER_NONCE_PREFIX + nonce;
    }

    private static String nonceBinding(String deviceId, String realm) {
        return deviceId + "\n" + realm;
    }

    private static String md5(String value) {
        return DigestUtils.md5DigestAsHex(value.getBytes(StandardCharsets.UTF_8));
    }
}
