package com.krdevops.springai.service.figma;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FigmaRestTokenServiceTest {

    @Test
    void disabledWithoutSecretAndRefusesToIssue() {
        FigmaRestTokenService service = new FigmaRestTokenService("", 900);

        assertThat(service.isEnabled()).isFalse();
        assertThatThrownBy(service::issue).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void issuedTokenVerifiesSuccessfully() {
        FigmaRestTokenService service = new FigmaRestTokenService("secret", 900);

        String token = service.issue().token();

        assertThat(service.verify(token)).isTrue();
    }

    @Test
    void expiredTokenFailsVerification() {
        FigmaRestTokenService service = new FigmaRestTokenService("secret", -1);

        String token = service.issue().token();

        assertThat(service.verify(token)).isFalse();
    }

    @Test
    void tokenSignedWithDifferentSecretFailsVerification() {
        FigmaRestTokenService issuer = new FigmaRestTokenService("secret-a", 900);
        FigmaRestTokenService verifier = new FigmaRestTokenService("secret-b", 900);

        String token = issuer.issue().token();

        assertThat(verifier.verify(token)).isFalse();
    }

    @Test
    void malformedTokenFailsVerification() {
        FigmaRestTokenService service = new FigmaRestTokenService("secret", 900);

        assertThat(service.verify("not-a-token")).isFalse();
        assertThat(service.verify(null)).isFalse();
    }
}
