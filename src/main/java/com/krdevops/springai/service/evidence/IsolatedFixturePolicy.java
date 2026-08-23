package com.krdevops.springai.service.evidence;

import org.springframework.stereotype.Service;

/** Evidence 실행을 격리 Fixture·Rollback 전제에서만 허용한다. */
@Service
public class IsolatedFixturePolicy {
    public void requireIsolated(boolean fixture, boolean transaction, boolean rollback) {
        if (!fixture || !transaction || !rollback) {
            throw new IllegalStateException("Evidence 실행은 격리 Fixture·Test Transaction·Rollback 정책이 필요합니다.");
        }
    }
}
