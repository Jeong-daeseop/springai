package com.krdevops.springai.model.capture;

import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * R7(04번 문서 §10): 사전 등록된 6종만 허용하는 닫힌 interaction step — extractor의
 * {@code server.ts} 검증과 동일한 규칙을 springai 쪽에서도 먼저 검사해(MCP 호출 시점에
 * 즉시 실패) 잘못된 요청이 extractor까지 가지 않도록 한다. 임의 selector/action을 런타임에
 * 주입할 수 없다는 원칙은 두 계층 모두에서 강제된다.
 */
public record InteractionStep(String type, @Nullable String selector, @Nullable String value) {
    private static final Set<String> TYPES = Set.of("click", "fill", "select", "scroll", "hover", "keydown");
    private static final Set<String> REQUIRES_SELECTOR = Set.of("click", "fill", "select", "hover");
    private static final Set<String> REQUIRES_VALUE = Set.of("fill", "select", "keydown");

    public InteractionStep {
        if (type == null || !TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    "interaction type은 click/fill/select/scroll/hover/keydown 중 하나여야 합니다: " + type);
        }
        if (REQUIRES_SELECTOR.contains(type) && (selector == null || selector.isBlank())) {
            throw new IllegalArgumentException("interaction type '" + type + "'에는 selector가 필요합니다.");
        }
        if (REQUIRES_VALUE.contains(type) && (value == null || value.isBlank())) {
            throw new IllegalArgumentException("interaction type '" + type + "'에는 value가 필요합니다.");
        }
    }
}
