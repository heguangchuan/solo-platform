package com.solo.common.utils;

import com.solo.system.constant.SystemConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordUtilsTest {

    @Test
    void bcryptUsesRandomSaltAndVerifiesRawPassword() {
        String first = PasswordUtils.encode("SafePass123");
        String second = PasswordUtils.encode("SafePass123");

        assertThat(first).startsWith("$2");
        assertThat(first).isNotEqualTo(second);
        assertThat(PasswordUtils.matches("SafePass123", first)).isTrue();
        assertThat(PasswordUtils.matches("wrong", first)).isFalse();
        assertThat(PasswordUtils.matches(null, first)).isFalse();
        assertThat(PasswordUtils.matches("SafePass123", "broken-hash")).isFalse();
    }

    @Test
    void legacyMd5AndBothHistoricalDefaultPasswordsAreRecognized() {
        assertThat(PasswordUtils.matches("admin123", SystemConstants.DEFAULT_PASSWORD)).isTrue();
        assertThat(PasswordUtils.matches("admin1234", SystemConstants.DEFAULT_PASSWORD)).isFalse();
        assertThat(PasswordUtils.isDefaultPassword("admin", SystemConstants.DEFAULT_PASSWORD)).isTrue();

        String legacyUserHash = "7a71d003e0a701f39a63bcd960e957a3";
        assertThat(PasswordUtils.matches("reader@123456", legacyUserHash)).isTrue();
        assertThat(PasswordUtils.isDefaultPassword("reader", legacyUserHash)).isTrue();
    }
}
