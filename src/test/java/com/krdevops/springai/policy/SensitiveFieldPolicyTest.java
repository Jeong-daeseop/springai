package com.krdevops.springai.policy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveFieldPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"CLOCK_TM", "UNIQUE_NM", "CERTIFICATE_TITLE"})
    void normalBusinessColumnsAreNotSensitive(String column) {
        assertThat(SensitiveFieldPolicy.isSensitiveDisplayField(null, column)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "USER_LOCK_YN", "BIZ_UNIQ_NO", "CERT_NO", "PASSWORD_HASH", "SECRET_KEY",
            "USER_CERT_VALUE", "ACCOUNT_DN_VALUE", "IHIDNUM", "CERTDN", "USERDN", "UNIQID"
    })
    void authenticationAndIdentityColumnsAreSensitive(String column) {
        assertThat(SensitiveFieldPolicy.isSensitiveDisplayField(null, column)).isTrue();
    }
}
