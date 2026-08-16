package com.krdevops.springai.service.figma;

import org.junit.jupiter.api.Test;

import java.util.Set;

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

    @Test
    void legacyDefaultIssueTokenOnlyHasScreensReadScope() {
        FigmaRestTokenService service = new FigmaRestTokenService("secret", 900);

        String token = service.issue().token();
        FigmaRestTokenService.VerificationResult result = service.verifyWithScopes(token);

        assertThat(result.valid()).isTrue();
        assertThat(result.hasScope(FigmaRestTokenService.SCOPE_SCREENS_READ)).isTrue();
        assertThat(result.hasScope(FigmaRestTokenService.SCOPE_REFINEMENTS_WRITE)).isFalse();
    }

    @Test
    void tokenIssuedWithMultipleScopesCarriesAllOfThem() {
        FigmaRestTokenService service = new FigmaRestTokenService("secret", 900);

        String token = service.issue(Set.of(
                FigmaRestTokenService.SCOPE_REFINEMENTS_WRITE,
                FigmaRestTokenService.SCOPE_REPORTS_WRITE)).token();
        FigmaRestTokenService.VerificationResult result = service.verifyWithScopes(token);

        assertThat(result.hasScope(FigmaRestTokenService.SCOPE_REFINEMENTS_WRITE)).isTrue();
        assertThat(result.hasScope(FigmaRestTokenService.SCOPE_REPORTS_WRITE)).isTrue();
        assertThat(result.hasScope(FigmaRestTokenService.SCOPE_SCREENS_READ)).isFalse();
    }

    @Test
    void tokenWithoutRequiredScopeIsRejectedByHasScope() {
        FigmaRestTokenService service = new FigmaRestTokenService("secret", 900);

        String token = service.issue(Set.of(FigmaRestTokenService.SCOPE_SCREENS_READ)).token();

        assertThat(service.verifyWithScopes(token).hasScope(FigmaRestTokenService.SCOPE_REFINEMENTS_WRITE)).isFalse();
    }

    @Test
    void issueRejectsEmptyScopeSet() {
        FigmaRestTokenService service = new FigmaRestTokenService("secret", 900);

        assertThatThrownBy(() -> service.issue(Set.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidTokenVerificationResultHasNoScopes() {
        FigmaRestTokenService service = new FigmaRestTokenService("secret", 900);

        FigmaRestTokenService.VerificationResult result = service.verifyWithScopes("not-a-token");

        assertThat(result.valid()).isFalse();
        assertThat(result.hasScope(FigmaRestTokenService.SCOPE_SCREENS_READ)).isFalse();
    }
}
