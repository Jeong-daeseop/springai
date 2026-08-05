package com.krdevops.springai.model.write;

/**
 * WP7/ARCH-0702: {@link ProjectChangeSet} 적용 시 drift(변경 이후 원본이 바뀐 경우) 처리 방식.
 *
 * <ul>
 *   <li>{@link #ATOMIC_APPROVED} — 모든 대상 파일의 {@code beforeHash}가 현재 파일과 일치할 때만
 *       적용한다. 하나라도 drift가 있으면 아무 것도 쓰지 않고 CONFLICT를 반환한다(전부 아니면
 *       전무). Thymeleaf Apply가 지금까지 써온 방식과 동일하다.</li>
 *   <li>{@link #BEST_EFFORT_COMPATIBILITY} — drift 검사를 건너뛰고 즉시 덮어쓴다. 승인·hash 검증
 *       없이 즉시 파일을 쓰던 기존 Tool({@code CodeSaverTool} 등)과의 호환 모드 전용이며, 신규
 *       Generation 경로의 기본값이 되어서는 안 된다.</li>
 * </ul>
 */
public enum ProjectWritePolicy {
    ATOMIC_APPROVED,
    BEST_EFFORT_COMPATIBILITY
}
