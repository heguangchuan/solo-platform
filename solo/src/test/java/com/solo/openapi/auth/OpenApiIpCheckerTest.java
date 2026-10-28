package com.solo.openapi.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiIpCheckerTest {

    private final OpenApiIpChecker checker = new OpenApiIpChecker();

    @Test
    void emptyWhitelistAllowsEverything() {
        assertThat(checker.allowed(null, "1.2.3.4")).isTrue();
        assertThat(checker.allowed("", "1.2.3.4")).isTrue();
    }

    @Test
    void matchesExactIpAndCidr() {
        assertThat(checker.allowed("1.2.3.4", "1.2.3.4")).isTrue();
        assertThat(checker.allowed("1.2.3.4", "1.2.3.5")).isFalse();
        assertThat(checker.allowed("10.0.0.0/8", "10.9.9.9")).isTrue();
        assertThat(checker.allowed("10.0.0.0/8", "11.0.0.1")).isFalse();
        assertThat(checker.allowed("0.0.0.0/0", "8.8.8.8")).isTrue();
        assertThat(checker.allowed("192.168.1.0/24", "192.168.1.255")).isTrue();
        assertThat(checker.allowed("192.168.1.0/24", "192.168.2.1")).isFalse();
    }

    @Test
    void malformedEntriesNeverThrow() {
        assertThat(checker.allowed("1.2.3.4/33", "1.2.3.4")).isFalse();
        assertThat(checker.allowed("1.2.3.4/abc", "1.2.3.4")).isFalse();
        assertThat(checker.allowed("1.2.3.4/24/8", "1.2.3.4")).isFalse();
        assertThat(checker.allowed("example.com", "1.2.3.4")).isFalse();
        assertThat(checker.allowed("::/129", "::1")).isFalse();
        assertThat(checker.allowed("1.2.3.4", "not-an-ip")).isFalse();
    }

    @Test
    void badEntryDoesNotBlockValidOnes() {
        assertThat(checker.allowed("1.2.3.4/abc,10.0.0.0/8", "10.1.2.3")).isTrue();
    }

    @Test
    void normalizesIpv4MappedIpv6() {
        assertThat(checker.allowed("10.0.0.0/8", "::ffff:10.1.2.3")).isTrue();
        assertThat(checker.allowed("::ffff:10.1.2.3", "10.1.2.3")).isTrue();
    }

    @Test
    void matchesIpv6AndLoopback() {
        assertThat(checker.allowed("2001:db8::/32", "2001:db8::1")).isTrue();
        assertThat(checker.allowed("2001:db8::/32", "2001:db9::1")).isFalse();
        assertThat(checker.allowed("2001:db8::1", "2001:DB8::1")).isTrue();
        assertThat(checker.allowed("::1", "127.0.0.1")).isTrue();
        assertThat(checker.allowed("::1", "0:0:0:0:0:0:0:1")).isTrue();
    }

    @Test
    void reportsInvalidEntriesForSaveValidation() {
        assertThat(checker.invalidEntries(null)).isEmpty();
        assertThat(checker.invalidEntries("1.2.3.4, 10.0.0.0/8, ::1")).isEmpty();
        assertThat(checker.invalidEntries("1.2.3.4/33")).singleElement()
                .asString().contains("1.2.3.4/33").contains("0-32");
        assertThat(checker.invalidEntries("1.2.3.4, example.com")).singleElement()
                .asString().contains("example.com");
        assertThat(checker.invalidEntries("::ffff:10.0.0.0/104")).singleElement()
                .asString().contains("0-32");
    }
}
