package com.krdevops.springai.model.thymeleaf;

/**
 * I-4C 2단계 산출물: {@link ThymeleafSkeleton}(구조)과 {@link ThymeleafBindingContract}(값)을
 * 결합한 결과. 렌더러는 이 타입만 소비하며, 모든 {@code th:field}/{@code th:text}/{@code th:each}는
 * {@code contract}에서 그대로 나오므로 provenance를 항상 되짚을 수 있다.
 */
public record BoundThymeleafView(
        ThymeleafSkeleton skeleton,
        ThymeleafBindingContract contract
) {
    public BoundThymeleafView {
        if (skeleton == null) {
            throw new IllegalArgumentException("skeleton은 필수입니다.");
        }
        if (contract == null) {
            throw new IllegalArgumentException("contract는 필수입니다.");
        }
        if (!skeleton.screenId().equals(contract.screenId())) {
            throw new IllegalArgumentException(
                    "SCREEN_ID_MISMATCH: skeleton=" + skeleton.screenId() + ", contract=" + contract.screenId());
        }
        if (skeleton.screenRole() != contract.screenRole()) {
            throw new IllegalArgumentException(
                    "SCREEN_ROLE_MISMATCH: skeleton=" + skeleton.screenRole() + ", contract=" + contract.screenRole());
        }
    }
}
