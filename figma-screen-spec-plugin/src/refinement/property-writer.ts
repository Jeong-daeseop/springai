import type { RefinementPatch } from "../types";
import type { RgbaColor } from "./property-normalizer";

/**
 * MR-R02: `apply-planner.ts`가 APPLY로 판정한 Patch 하나를 실제 Figma 노드에 반영한다.
 * `refinement/policy.ts`의 MVP 속성 화이트리스트와 1:1로 대응하며, 목록에 없는 propertyPath는
 * 아무 것도 하지 않는다(정상 Apply 경로에서는 이미 BLOCKED로 걸러지므로 여기 도달하지 않는다).
 */
export function applyPatchToNode(node: SceneNode, patch: RefinementPatch): void {
  switch (patch.propertyPath) {
    case "fill":
      if ("fills" in node) node.fills = [solidPaint(patch.after as RgbaColor)];
      return;
    case "stroke":
      if ("strokes" in node) node.strokes = [solidPaint(patch.after as RgbaColor)];
      return;
    case "opacity":
      if ("opacity" in node) node.opacity = patch.after as number;
      return;
    case "cornerRadius":
      if ("cornerRadius" in node) (node as unknown as { cornerRadius: number }).cornerRadius = patch.after as number;
      return;
    case "itemSpacing":
      if ("itemSpacing" in node) node.itemSpacing = patch.after as number;
      return;
    case "padding.top":
      if ("paddingTop" in node) node.paddingTop = patch.after as number;
      return;
    case "padding.right":
      if ("paddingRight" in node) node.paddingRight = patch.after as number;
      return;
    case "padding.bottom":
      if ("paddingBottom" in node) node.paddingBottom = patch.after as number;
      return;
    case "padding.left":
      if ("paddingLeft" in node) node.paddingLeft = patch.after as number;
      return;
    case "width":
      if ("resize" in node && "height" in node) node.resize(patch.after as number, node.height);
      return;
    case "height":
      if ("resize" in node && "width" in node) node.resize(node.width, patch.after as number);
      return;
    case "minWidth":
      if ("minWidth" in node) node.minWidth = patch.after as number;
      return;
    case "minHeight":
      if ("minHeight" in node) node.minHeight = patch.after as number;
      return;
    case "layoutGrow":
      if ("layoutGrow" in node) node.layoutGrow = patch.after as number;
      return;
    case "layoutAlign":
      if ("layoutAlign" in node) node.layoutAlign = patch.after as typeof node.layoutAlign;
      return;
    case "textAlign":
      if (node.type === "TEXT") node.textAlignHorizontal = patch.after as typeof node.textAlignHorizontal;
      return;
    case "typography.fontSize":
      if (node.type === "TEXT") node.fontSize = patch.after as number;
      return;
    case "typography.letterSpacing":
      if (node.type === "TEXT") node.letterSpacing = { value: patch.after as number, unit: "PIXELS" };
      return;
    case "typography.lineHeight":
      if (node.type === "TEXT") node.lineHeight = { value: patch.after as number, unit: "PIXELS" };
      return;
    default:
      return;
  }
}

function solidPaint(color: RgbaColor): SolidPaint {
  return { type: "SOLID", color: { r: color.r, g: color.g, b: color.b }, opacity: color.a ?? 1 };
}
