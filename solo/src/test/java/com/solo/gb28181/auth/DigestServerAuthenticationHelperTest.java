package com.solo.gb28181.auth;

import com.solo.gb28181.constant.GbRedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;

import javax.sip.address.SipURI;
import javax.sip.header.AuthorizationHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.WWWAuthenticateHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DigestServerAuthenticationHelperTest {

    private static final String DEVICE_ID = "34020000001320000001";
    private static final String REALM = "3402000000";
    private static final String PASSWORD = "test-password";
    private static final String NONCE = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String CNONCE = "device-cnonce";
    private static final String NC = "0000000A";
    private static final String NONCE_KEY = GbRedisKeys.REGISTER_NONCE_PREFIX + NONCE;

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private Request request;
    @Mock
    private AuthorizationHeader authorization;
    @Mock
    private SipURI requestUri;

    private DigestServerAuthenticationHelper helper;

    @BeforeEach
    void setUp() {
        helper = new DigestServerAuthenticationHelper(redisTemplate);
        lenient.when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient.when(valueOperations.get(NONCE_KEY)).thenReturn(DEVICE_ID + "\n" + REALM);
        lenient.when(request.getHeader(AuthorizationHeader.NAME)).thenReturn(authorization);
        lenient.when(request.getRequestURI()).thenReturn(requestUri);
        lenient.when(request.getMethod()).thenReturn(Request.REGISTER);
        lenient.when(authorization.getUsername()).thenReturn(DEVICE_ID);
        lenient.when(authorization.getRealm()).thenReturn(REALM);
        lenient.when(authorization.getNonce()).thenReturn(NONCE);
        lenient.when(authorization.getURI()).thenReturn(requestUri);
        lenient.when(authorization.getAlgorithm()).thenReturn("MD5");
        lenient.when(authorization.getQop()).thenReturn("auth");
        lenient.when(authorization.getParameter("nc")).thenReturn(NC);
        lenient.when(authorization.getCNonce()).thenReturn(CNONCE);
    }

    @Test
    void acceptsValidQopAuthDigestAndUppercaseResponse() {
        when(authorization.getResponse()).thenReturn(response(PASSWORD).toUpperCase());

        assertThat(helper.authenticate(request, DEVICE_ID, REALM, PASSWORD)).isTrue();
    }

    @Test
    void acceptsLegacyDigestWithoutQop() {
        when(authorization.getQop()).thenReturn(null);
        String ha1 = md5(DEVICE_ID + ":" + REALM + ":" + PASSWORD);
        String ha2 = md5(Request.REGISTER + ":" + requestUri);
        when(authorization.getResponse()).thenReturn(md5(ha1 + ":" + NONCE + ":" + ha2));

        assertThat(helper.authenticate(request, DEVICE_ID, REALM, PASSWORD)).isTrue();
    }

    @Test
    void rejectsDigestCalculatedWithWrongPassword() {
        respondUsing("wrong-password");

        assertThat(helper.authenticate(request, DEVICE_ID, REALM, PASSWORD)).isFalse();
    }

    @Test
    void rejectsNonceNotIssuedByPlatform() {
        respondUsing(PASSWORD);
        when(valueOperations.get(NONCE_KEY)).thenReturn(null);

        assertThat(helper.authenticate(request, DEVICE_ID, REALM, PASSWORD)).isFalse();
    }

    @Test
    void rejectsNonceIssuedToAnotherDevice() {
        respondUsing(PASSWORD);
        when(valueOperations.get(NONCE_KEY)).thenReturn("34020000001320000002\n" + REALM);

        assertThat(helper.authenticate(request, DEVICE_ID, REALM, PASSWORD)).isFalse();
    }

    @Test
    void rejectsMismatchedUsernameAndRealm() {
        respondUsing(PASSWORD);
        when(authorization.getUsername()).thenReturn("34020000001320000002");
        assertThat(helper.authenticate(request, DEVICE_ID, REALM, PASSWORD)).isFalse();

        when(authorization.getUsername()).thenReturn(DEVICE_ID);
        when(authorization.getRealm()).thenReturn("another-realm");
        assertThat(helper.authenticate(request, DEVICE_ID, REALM, PASSWORD)).isFalse();
    }

    @Test
    void rejectsAuthorizationUriDifferentFromRequestUri() {
        respondUsing(PASSWORD);
        when(authorization.getURI()).thenReturn(mock(SipURI.class));

        assertThat(helper.authenticate(request, DEVICE_ID, REALM, PASSWORD)).isFalse();
    }

    @Test
    void rejectsUnsupportedQopAndIncompleteAuthParams() {
        respondUsing(PASSWORD);
        when(authorization.getQop()).thenReturn("auth-int");
        assertThat(helper.authenticate(request, DEVICE_ID, REALM, PASSWORD)).isFalse();

        when(authorization.getQop()).thenReturn("auth");
        when(authorization.getCNonce()).thenReturn(null);
        assertThat(helper.authenticate(request, DEVICE_ID, REALM, PASSWORD)).isFalse();
    }

    @Test
    void challengeStoresRandomNonceBoundToDeviceAndRealm() throws Exception {
        HeaderFactory headerFactory = mock(HeaderFactory.class);
        Response response = mock(Response.class);
        WWWAuthenticateHeader challenge = mock(WWWAuthenticateHeader.class);
        when(headerFactory.createWWWAuthenticateHeader("Digest")).thenReturn(challenge);

        helper.appendChallenge(headerFactory, response, DEVICE_ID, REALM);

        ArgumentCaptor<String> nonce = ArgumentCaptor.forClass(String.class);
        verify(challenge).setParameter(eq("nonce"), nonce.capture());
        assertThat(nonce.getValue()).matches("[0-9a-f]{64}");
        verify(valueOperations).set(
                GbRedisKeys.REGISTER_NONCE_PREFIX + nonce.getValue(),
                DEVICE_ID + "\n" + REALM,
                Duration.ofMinutes(5));
        verify(challenge).setParameter("realm", REALM);
        verify(challenge).setParameter("qop", "auth");
        verify(challenge).setParameter("algorithm", "MD5");
        verify(response).setHeader(challenge);
    }

    private void respondUsing(String password) {
        when(authorization.getResponse()).thenReturn(response(password));
    }

    private String response(String password) {
        String ha1 = md5(DEVICE_ID + ":" + REALM + ":" + password);
        String ha2 = md5(Request.REGISTER + ":" + requestUri);
        return md5(ha1 + ":" + NONCE + ":" + NC + ":" + CNONCE + ":auth:" + ha2);
    }

    private static String md5(String value) {
        return DigestUtils.md5DigestAsHex(value.getBytes(StandardCharsets.UTF_8));
    }
}
