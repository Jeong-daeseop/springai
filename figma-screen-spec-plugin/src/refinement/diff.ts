import type { RefinementPatch, RefinementPropertyType, RefinementSnapshotEntry } from "../types";
import { classifyScope, defaultOwnerForCapture } from "./policy";
import { colorsEqual, isMixedValue, numbersEqual, type RgbaColor } from "./property-normalizer";

/**
 * MR-P04: 기준(Capture 직후) Snapshot과 현재 Figma 상태의 속성 단위 Diff를 계산한다.
 * Figma API에 의존하지 않는 순수 함수이며, 실제 값 읽기는 `refinement/snapshot.ts`(code.ts 쪽)가
 * 담당하고 이 함수는 이미 읽어낸 두 스냅샷만 비교한다.
 */
export function diffSnapshots(
  baseline: RefinementSnapshotEntry[],
  current: RefinementSnapshotEntry[],
): RefinementPatch[] {
  const currentById = new Map(current.map(entry => [entry.logicalNodeId, entry]));
  const patches: RefinementPatch[] = [];

  for (const before of baseline) {
    const after = currentById.get(before.logicalNodeId);
    if (!after) continue; // 노드 자체가 사라진 경우는 Diff 대상이 아니라 별도 정책(Removed Node)으로 다룬다.

    for (const [propertyPath, beforeValue] of Object.entries(before.properties)) {
      const afterValue = after.properties[propertyPath];
      if (afterValue === undefined) continue;
      if (isMixedValue(afterValue) || isMixedValue(beforeValue)) continue; // MIXED_VALUE_UNSUPPORTED
      if (valuesEqual(beforeValue, afterValue)) continue;

      patches.push({
        logicalNodeId: before.logicalNodeId,
        baselineLogicalType: before.logicalType,
        propertyPath,
        propertyType: inferPropertyType(afterValue),
        before: beforeValue,
        after: afterValue,
        owner: defaultOwnerForCapture(),
        scope: classifyScope(propertyPath),
        conflictStatus: "NONE",
      });
    }
  }
  return patches;
}

export function valuesEqual(a: unknown, b: unknown): boolean {
  if (typeof a === "number" && typeof b === "number") return numbersEqual(a, b);
  if (isRgbaColor(a) && isRgbaColor(b)) return colorsEqual(a, b);
  return a === b;
}

function isRgbaColor(value: unknown): value is RgbaColor {
  return typeof value === "object" && value !== null
    && "r" in value && "g" in value && "b" in value;
}

function inferPropertyType(value: unknown): RefinementPropertyType {
  if (isRgbaColor(value)) return "COLOR";
  if (typeof value === "number") return "NUMBER";
  if (typeof value === "boolean") return "BOOLEAN";
  if (typeof value === "string" && /^#[0-9A-Fa-f]{6,8}$/.test(value)) return "COLOR";
  return "STRING";
}
