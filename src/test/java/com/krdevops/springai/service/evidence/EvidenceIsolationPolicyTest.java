package com.krdevops.springai.service.evidence;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenceIsolationPolicyTest {
    @Test void 격리되지_않은_fixture를_차단한다() {
        assertThatThrownBy(() -> new IsolatedFixturePolicy().requireIsolated(false, true, true))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void 운영_DB_쓰기_시도를_차단한다() {
        assertThatThrownBy(() -> new OperationalDatabaseWriteGuard().requireNonOperationalDatabase(true, true))
                .isInstanceOf(OperationalDatabaseWriteGuard.OperationalDatabaseWriteBlockedException.class);
    }
}
