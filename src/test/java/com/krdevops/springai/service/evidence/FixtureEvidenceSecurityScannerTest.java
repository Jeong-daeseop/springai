package com.krdevops.springai.service.evidence;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FixtureEvidenceSecurityScannerTest {
    @Test void 비밀정보가_포함된_fixture를_탐지한다() {
        assertThat(new FixtureEvidenceSecurityScanner().scan("apiKey=abc123").safe()).isFalse();
        assertThat(new FixtureEvidenceSecurityScanner().scan("button=true").safe()).isTrue();
    }
}
