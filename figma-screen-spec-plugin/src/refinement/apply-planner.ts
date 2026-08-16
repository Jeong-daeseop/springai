import type { RefinementPatch, RefinementPatchSet, RefinementSnapshotEntry } from "../types";
import { valuesEqual } from "./diff";

export type ApplyAction = "APPLY" | "SKIP_REMOVED" | "SKIP_TYPE_CHANGED" | "SKIP_CONFLICT" | "SKIP_BLOCKED";

export type ApplyDecision = {
  patch: RefinementPatch;
  action: ApplyAction;
  reason: string;
};

/**
 * MR-R03~06: 승인된 Patch Set을 실제로 재적용하기 전, 순수 함수로 다음을 한 번에 판정한다.
 * - 적용 순서를 {@code patchSetId → logicalNodeId → propertyPath}로 결정적으로 고정한다(MR-R03).
 * - SYSTEM_LAYOUT 소유이거나 BLOCKED scope인 Patch는 승인됐어도 적용하지 않는다(MR-R06).
 * - Patch 대상 노드가 새 트리에서 사라졌으면 제외한다(MR-R05, TARGET_REMOVED).
 * - Patch 대상 노드의 논리 타입이 baseline과 달라졌으면 제외한다(MR-R05, TYPE_CHANGED).
 * - baseline 값과 새 Screen Spec의 현재 값이 이미 달라졌다면(=서버가 같은 속성을 이미 바꿨다면)
 *   자동 적용하지 않고 충돌로 보고한다(MR-R04, UPSTREAM_CHANGED).
 *
 * 실제 Figma 노드에 값을 쓰는 부수효과는 이 함수가 아니라 호출자(`code.ts`)가 담당한다.
 */
export function planPatchApplication(
  patchSet: RefinementPatchSet,
  currentTree: RefinementSnapshotEntry[],
): ApplyDecision[] {
  const currentById = new Map(currentTree.map(entry => [entry.logicalNodeId, entry]));
  const sorted = [...patchSet.patches].sort((a, b) => {
    if (a.logicalNodeId !== b.logicalNodeId) return a.logicalNodeId < b.logicalNodeId ? -1 : 1;
    return a.propertyPath < b.propertyPath ? -1 : a.propertyPath > b.propertyPath ? 1 : 0;
  });

  return sorted.map(patch => {
    if (patch.owner === "SYSTEM_LAYOUT" || patch.scope === "BLOCKED") {
      return { patch, action: "SKIP_BLOCKED", reason: "SYSTEM_LAYOUT 소유 또는 BLOCKED scope 속성은 승인돼도 적용하지 않음" };
    }
    const current = currentById.get(patch.logicalNodeId);
    if (!current) {
      return { patch, action: "SKIP_REMOVED", reason: "Patch 대상 logicalNodeId가 새 화면 트리에서 삭제됨" };
    }
    if (current.logicalType !== patch.baselineLogicalType) {
      return { patch, action: "SKIP_TYPE_CHANGED", reason: "Patch 대상 노드의 논리 타입이 baseline과 다름" };
    }
    const currentValue = current.properties[patch.propertyPath];
    if (currentValue !== undefined && !valuesEqual(currentValue, patch.before)) {
      return { patch, action: "SKIP_CONFLICT", reason: "baseline 값과 새 Screen Spec 값이 같은 속성을 다르게 변경함" };
    }
    return { patch, action: "APPLY", reason: "baseline과 현재 값이 일치해 안전하게 재적용 가능" };
  });
}

export function applicableDecisions(decisions: ApplyDecision[]): ApplyDecision[] {
  return decisions.filter(decision => decision.action === "APPLY");
}
