import type { RefinementOwner, RefinementScope } from "../types";

/**
 * MR-P05/MR-DEC-04: MVP 허용·조건부·차단 속성 정책. `CONTRACT_RULES.md` §10.5와 동일 목록을
 * 코드 한 곳에서 관리한다. 여기 없는 propertyPath는 전부 BLOCKED로 취급한다(화이트리스트 방식).
 */
const ALLOWED_PROPERTIES = new Set([
  "fill", "stroke", "opacity", "cornerRadius",
  "typography.fontFamily", "typography.fontStyle", "typography.fontSize",
  "typography.letterSpacing", "typography.lineHeight",
  "padding.top", "padding.right", "padding.bottom", "padding.left",
  "itemSpacing", "textAlign",
]);

const CONDITIONAL_PROPERTIES = new Set([
  "width", "height", "minWidth", "minHeight", "layoutGrow", "layoutAlign",
]);

/** 화이트리스트에 없는 값도 명시적으로 BLOCKED임을 보여주기 위해 등록해 둔다(참고용, 실제 판정은 미포함=BLOCKED). */
const EXPLICITLY_BLOCKED_PROPERTIES = new Set([
  "logicalNodeId", "screenVersion", "instanceDetach", "nodeDeleted", "visible", "layoutMode",
]);

export function classifyScope(propertyPath: string): RefinementScope {
  if (ALLOWED_PROPERTIES.has(propertyPath)) return "ALLOWED";
  if (CONDITIONAL_PROPERTIES.has(propertyPath)) return "CONDITIONAL";
  return "BLOCKED";
}

export function isExplicitlyBlocked(propertyPath: string): boolean {
  return EXPLICITLY_BLOCKED_PROPERTIES.has(propertyPath);
}

/**
 * MR-P05: Capture 시점의 Patch 소유자는 항상 MANUAL_REFINEMENT다(사람이 Figma에서 직접
 * 조정한 값이므로). SYSTEM_LAYOUT은 서버가 재적용 시 강제로 재분류하는 값이며 Plugin이
 * Capture 단계에서 스스로 부여하지 않는다.
 */
export function defaultOwnerForCapture(): RefinementOwner {
  return "MANUAL_REFINEMENT";
}
