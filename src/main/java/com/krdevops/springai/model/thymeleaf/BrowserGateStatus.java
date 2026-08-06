package com.krdevops.springai.model.thymeleaf;

/** WP8 브라우저 Gate의 실행 및 판정 상태. */
public enum BrowserGateStatus {
    PASSED,
    FAILED,
    BASELINE_MISSING,
    INFRA_ERROR
}
