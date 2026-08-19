package com.krdevops.springai.model.capture;

import java.util.Map;

/**
 * R8(04번 문서 §11): {@code selectorHint}(R2-05) 일치를 기준으로 Desktop/Tablet/Mobile 문서
 * 사이에서 "같은 컴포넌트"로 판정된 노드 묶음. 단순 CSS 미디어쿼리 기반 반응형(같은 DOM, CSS만
 * 다름)에서 잘 동작하며, viewport마다 DOM 구조 자체가 크게 다른 adaptive 설계는 정확도가 떨어질
 * 수 있다(선언된 한계).
 */
public record ComponentMatch(
        String selectorHint, Map<String, String> nodeIdsByViewport, Status status) {

    /** 확실한 근거(부모 selectorHint 비교)가 있는 것만 분류한다 — 임의 픽셀 임계값을 쓰지 않는다. */
    public enum Status {
        /** 캡처된 모든 viewport에 존재하고, 각 viewport에서 부모 selectorHint가 동일하다. */
        MATCHED_ALL,
        /** 캡처된 viewport 중 일부에서만 발견된다(나머지에서는 숨겨졌거나 없음). */
        HIDDEN_IN_SOME,
        /** 모든 viewport에 존재하지만 부모 selectorHint가 달라(재배치/DOM 이동) MATCHED_ALL로 볼 수 없다. */
        MOVED
    }

    public ComponentMatch {
        nodeIdsByViewport = nodeIdsByViewport == null ? Map.of() : Map.copyOf(nodeIdsByViewport);
    }
}
