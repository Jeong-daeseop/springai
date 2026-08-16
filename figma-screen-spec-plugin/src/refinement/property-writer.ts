import type { RefinementPatch } from "../types";
import type { RgbaColor } from "./property-normalizer";

/**
 * MR-R02: `apply-planner.ts`가 APPLY로 판정한 Patch 하나를 실제 Figma 노드에 반영한다.
 * `refinement/policy.ts`의 MVP 속성 화이트리스트와 1:1로 대응하며, 목록에 없는 propertyPath는
 * 아무 것도 하지 않는다(정상 Apply 경로에서는 이미 BLOCKED로 걸러지므로 여기 도달하지 않는다).
 */
export async function applyPatchToNode(node: SceneNode, patch: RefinementPatch): Promise<boolean> {
  switch (patch.propertyPath) {
    case "fill":
      if ("fills" in node) { node.fills = [solidPaint(patch.after as RgbaColor)]; return true; }
      return false;
    case "stroke":
      if ("strokes" in node) { node.strokes = [solidPaint(patch.after as RgbaColor)]; return true; }
      return false;
    case "opacity":
      if ("opacity" in node) { node.opacity = patch.after as number; return true; }
      return false;
    case "cornerRadius":
      if ("cornerRadius" in node) { (node as unknown as { cornerRadius: number }).cornerRadius = patch.after as number; return true; }
      return false;
    case "itemSpacing":
      if ("itemSpacing" in node) { node.itemSpacing = patch.after as number; return true; }
      return false;
    case "padding.top":
      if ("paddingTop" in node) { node.paddingTop = patch.after as number; return true; }
      return false;
    case "padding.right":
      if ("paddingRight" in node) { node.paddingRight = patch.after as number; return true; }
      return false;
    case "padding.bottom":
      if ("paddingBottom" in node) { node.paddingBottom = patch.after as number; return true; }
      return false;
    case "padding.left":
      if ("paddingLeft" in node) { node.paddingLeft = patch.after as number; return true; }
      return false;
    case "width":
      if ("resize" in node && "height" in node) { node.resize(patch.after as number, node.height); return true; }
      return false;
    case "height":
      if ("resize" in node && "width" in node) { node.resize(node.width, patch.after as number); return true; }
      return false;
    case "minWidth":
      if ("minWidth" in node) { node.minWidth = patch.after as number; return true; }
      return false;
    case "minHeight":
      if ("minHeight" in node) { node.minHeight = patch.after as number; return true; }
      return false;
    case "layoutGrow":
      if ("layoutGrow" in node) { node.layoutGrow = patch.after as number; return true; }
      return false;
    case "layoutAlign":
      if ("layoutAlign" in node) { node.layoutAlign = patch.after as typeof node.layoutAlign; return true; }
      return false;
    case "textAlign":
      if (node.type === "TEXT") { node.textAlignHorizontal = patch.after as typeof node.textAlignHorizontal; return true; }
      return false;
    case "typography.fontSize":
      if (node.type === "TEXT") { await loadCurrentFont(node); node.fontSize = patch.after as number; return true; }
      return false;
    case "typography.letterSpacing":
      if (node.type === "TEXT") { await loadCurrentFont(node); node.letterSpacing = { value: patch.after as number, unit: "PIXELS" }; return true; }
      return false;
    case "typography.lineHeight":
      if (node.type === "TEXT") { await loadCurrentFont(node); node.lineHeight = { value: patch.after as number, unit: "PIXELS" }; return true; }
      return false;
    case "typography.fontFamily":
      if (node.type === "TEXT" && node.fontName !== figma.mixed) {
        const font = { family: patch.after as string, style: node.fontName.style };
        await figma.loadFontAsync(font); node.fontName = font; return true;
      }
      return false;
    case "typography.fontStyle":
      if (node.type === "TEXT" && node.fontName !== figma.mixed) {
        const font = { family: node.fontName.family, style: patch.after as string };
        await figma.loadFontAsync(font); node.fontName = font; return true;
      }
      return false;
    default:
      return false;
  }
}

async function loadCurrentFont(node: TextNode): Promise<void> {
  if (node.fontName === figma.mixed) throw new Error("MIXED_FONT_REFINEMENT_UNSUPPORTED");
  await figma.loadFontAsync(node.fontName);
}

function solidPaint(color: RgbaColor): SolidPaint {
  return { type: "SOLID", color: { r: color.r, g: color.g, b: color.b }, opacity: color.a ?? 1 };
}
