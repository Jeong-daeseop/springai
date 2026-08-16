import { MIXED_VALUE } from "./property-normalizer";

/**
 * MR-P01/MR-P07: 지원 노드의 실제 속성만 안전하게 읽는다. Figma의 selection outline이나
 * Plugin UI overlay는 노드 속성이 아니므로(SceneNode API에 존재하지 않음) 애초에 이 함수의
 * 반환값에 섞일 수 없다. 실제 `figma.mixed` 심볼은 Figma API에만 존재하므로, Figma API에
 * 의존하지 않는 `diff.ts`가 이해할 수 있게 공용 {@link MIXED_VALUE}로 변환해 반환한다.
 */
export function readSupportedProperties(node: SceneNode): Record<string, unknown> {
  const properties: Record<string, unknown> = {};

  if ("fills" in node && Array.isArray(node.fills)) {
    const solid = node.fills.find(paint => paint.type === "SOLID" && paint.visible !== false);
    if (solid && solid.type === "SOLID") {
      properties.fill = solid.color === figma.mixed
        ? MIXED_VALUE
        : { r: solid.color.r, g: solid.color.g, b: solid.color.b, a: solid.opacity ?? 1 };
    }
  }
  if ("strokes" in node && Array.isArray(node.strokes)) {
    const solid = node.strokes.find(paint => paint.type === "SOLID" && paint.visible !== false);
    if (solid && solid.type === "SOLID") {
      properties.stroke = solid.color === figma.mixed
        ? MIXED_VALUE
        : { r: solid.color.r, g: solid.color.g, b: solid.color.b, a: solid.opacity ?? 1 };
    }
  }
  if ("opacity" in node && typeof node.opacity === "number") {
    properties.opacity = node.opacity;
  }
  if ("cornerRadius" in node) {
    properties.cornerRadius = node.cornerRadius === figma.mixed ? MIXED_VALUE : node.cornerRadius;
  }
  if ("paddingTop" in node) properties["padding.top"] = node.paddingTop;
  if ("paddingRight" in node) properties["padding.right"] = node.paddingRight;
  if ("paddingBottom" in node) properties["padding.bottom"] = node.paddingBottom;
  if ("paddingLeft" in node) properties["padding.left"] = node.paddingLeft;
  if ("itemSpacing" in node && typeof node.itemSpacing === "number") {
    properties.itemSpacing = node.itemSpacing;
  }
  if ("width" in node && typeof node.width === "number") properties.width = node.width;
  if ("height" in node && typeof node.height === "number") properties.height = node.height;
  if ("minWidth" in node && node.minWidth != null) properties.minWidth = node.minWidth;
  if ("minHeight" in node && node.minHeight != null) properties.minHeight = node.minHeight;
  if ("layoutGrow" in node && typeof node.layoutGrow === "number") properties.layoutGrow = node.layoutGrow;
  if ("layoutAlign" in node) properties.layoutAlign = node.layoutAlign;

  if (node.type === "TEXT") {
    if (typeof node.textAlignHorizontal === "string") properties.textAlign = node.textAlignHorizontal;
    properties["typography.fontSize"] = node.fontSize === figma.mixed ? MIXED_VALUE : node.fontSize;
    if (node.letterSpacing !== figma.mixed) {
      properties["typography.letterSpacing"] = node.letterSpacing.value;
    } else {
      properties["typography.letterSpacing"] = MIXED_VALUE;
    }
    if (node.lineHeight !== figma.mixed && node.lineHeight.unit !== "AUTO") {
      properties["typography.lineHeight"] = node.lineHeight.value;
    }
    if (node.fontName !== figma.mixed) {
      properties["typography.fontFamily"] = node.fontName.family;
      properties["typography.fontStyle"] = node.fontName.style;
    } else {
      properties["typography.fontFamily"] = MIXED_VALUE;
      properties["typography.fontStyle"] = MIXED_VALUE;
    }
  }

  return properties;
}
