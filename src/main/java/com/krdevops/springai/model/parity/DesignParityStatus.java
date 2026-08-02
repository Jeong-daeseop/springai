package com.krdevops.springai.model.parity;

/**
 * ARCH-0207: {@code DesignParityValidationUseCase} 결과 상태.
 * 구현계획서 §8 재구현 계약의 4개 상태를 그대로 따른다.
 */
public enum DesignParityStatus {
    /** Figma/Thymeleaf 양쪽 Artifact가 실존·승인 상태이고 선언된 content hash가 일치. */
    VERIFIED,
    /** 양쪽 모두 실존하지만 content hash가 일치하지 않음. */
    MISMATCH,
    /** Operation이 승인/적용 가능한 상태가 아니거나(FAILED/REJECTED/CONFLICT 등) source가 충돌. */
    CONFLICT,
    /** 이 UseCase가 검증할 수 없는 요청 — 예: 시각적 Component/Token/Layout 비교, evidence 미제공. */
    UNSUPPORTED
}
