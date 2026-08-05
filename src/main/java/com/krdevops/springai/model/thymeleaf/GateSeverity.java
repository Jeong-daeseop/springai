package com.krdevops.springai.model.thymeleaf;

/**
 * WP8/ARCH-0801: {@link ValidationGateType}별 실패 처리 정책. {@code BLOCK}은 Preview/Apply를
 * 막아야 하는 실결함(구조 깨짐, 렌더 실패, 바인딩/라우트 불일치), {@code WARN}은 사람이 검토하되
 * 자동으로 막지는 않는 휴리스틱(예: 반응형 폭 초과 — 실제 판정은 WP8 브라우저 Gate 몫)이다.
 */
public enum GateSeverity {
    BLOCK,
    WARN
}
