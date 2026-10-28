package com.solo.openapi.auth;

import com.solo.openapi.config.OpenApiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiClientIpResolverTest {

    private static OpenApiClientIpResolver resolver(String trustedProxies) {
        OpenApiProperties properties = new OpenApiProperties();
        properties.setTrustedProxies(trustedProxies);
        return new OpenApiClientIpResolver(properties);
    }

    private static MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    @Test
    void ignoresForwardedHeadersFromUntrustedPeer() {
        MockHttpServletRequest request = request("203.0.113.9");
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        assertThat(resolver("127.0.0.1").resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void neverTrustsHeadersWithoutConfiguredProxies() {
        MockHttpServletRequest request = request("203.0.113.9");
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        assertThat(resolver("").resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void takesRightmostUntrustedHop() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 198.51.100.7");
        assertThat(resolver("127.0.0.1").resolve(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void skipsTrustedHops() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.7, 10.0.0.2");
        assertThat(resolver("127.0.0.1,10.0.0.0/8").resolve(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void fallsBackToRemoteWhenChainIsUnusable() {
        MockHttpServletRequest allTrusted = request("127.0.0.1");
        allTrusted.addHeader("X-Forwarded-For", "10.0.0.2");
        assertThat(resolver("127.0.0.1,10.0.0.0/8").resolve(allTrusted)).isEqualTo("127.0.0.1");

        MockHttpServletRequest garbage = request("127.0.0.1");
        garbage.addHeader("X-Forwarded-For", "not-an-ip");
        assertThat(resolver("127.0.0.1").resolve(garbage)).isEqualTo("127.0.0.1");
    }

    @Test
    void fallsBackToXRealIp() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Real-IP", "198.51.100.7");
        assertThat(resolver("127.0.0.1").resolve(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void normalizesMappedAndLoopbackAddresses() {
        assertThat(resolver("").resolve(request("::ffff:203.0.113.9"))).isEqualTo("203.0.113.9");
        assertThat(resolver("").resolve(request("0:0:0:0:0:0:0:1"))).isEqualTo("127.0.0.1");
    }
}
