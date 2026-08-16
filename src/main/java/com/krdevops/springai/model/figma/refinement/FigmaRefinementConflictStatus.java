package com.krdevops.springai.model.figma.refinement;

/** MR-S07: Manual Refinement Patch 재적용 시 감지 가능한 충돌 유형. */
public enum FigmaRefinementConflictStatus {
    /** 충돌 없음. 정상 적용 대상. */
    NONE,
    /** baseline 값과 새 Screen Spec 값이 같은 속성을 다르게 변경함. */
    UPSTREAM_CHANGED,
    /** Patch 대상 logicalNodeId가 새 화면 트리에서 삭제됨. */
    TARGET_REMOVED,
    /** Patch 대상 logicalNodeId의 노드 타입이 바뀌어 같은 속성 의미가 아님. */
    TYPE_CHANGED,
    /** MVP 차단 속성(Owner=SYSTEM_LAYOUT 등)이라 승인돼도 적용하지 않음. */
    POLICY_BLOCKED,
    /** baseMaterializationHash가 현재 화면 상태와 달라 재계산 없이는 신뢰할 수 없음. */
    BASE_STALE
}
