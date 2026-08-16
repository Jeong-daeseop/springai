/**
 * MR-P02/MR-C05: Figma 속성값을 결정적으로 정규화한다. Figma API 타입에 의존하지 않는 순수
 * 함수로 유지해 `node --test`로 직접 검증한다(`CONTRACT_RULES.md` §10.5 정규화 규칙과 동일).
 */

export type RgbaColor = { r: number; g: number; b: number; a?: number };

const MIXED = "__FIGMA_MIXED__" as const;

/** 호출자가 `value === figma.mixed`로 판정한 결과를 이 심볼로 넘기면 아래 함수들이 일관되게 처리한다. */
export const MIXED_VALUE = MIXED;

export function isMixedValue(value: unknown): boolean {
  return value === MIXED;
}

/** 0~1 float RGBA를 소수점 4자리로 반올림한 뒤 #RRGGBBAA 문자열로 정규화한다. */
export function normalizeColor(color: RgbaColor): string {
  const toHex = (component: number) => {
    const rounded = Math.round(round(component, 4) * 255);
    return Math.max(0, Math.min(255, rounded)).toString(16).padStart(2, "0").toUpperCase();
  };
  const alpha = color.a === undefined ? 1 : color.a;
  return `#${toHex(color.r)}${toHex(color.g)}${toHex(color.b)}${toHex(alpha)}`;
}

/** Paint 배열을 type → opacity → color(정규화된 hex) 순으로 정렬해 Figma가 반환하는 원래 배열 순서에 의존하지 않게 한다. */
export function normalizePaintOrder<T extends { type: string; opacity?: number; color?: RgbaColor }>(
  paints: T[],
): T[] {
  return [...paints].sort((a, b) => {
    if (a.type !== b.type) return a.type < b.type ? -1 : 1;
    const opacityA = a.opacity ?? 1;
    const opacityB = b.opacity ?? 1;
    if (opacityA !== opacityB) return opacityA - opacityB;
    const colorA = a.color ? normalizeColor(a.color) : "";
    const colorB = b.color ? normalizeColor(b.color) : "";
    return colorA < colorB ? -1 : colorA > colorB ? 1 : 0;
  });
}

function round(value: number, digits: number): number {
  const factor = 10 ** digits;
  return Math.round(value * factor) / factor;
}

/** 숫자 속성(width/height/padding/itemSpacing 등)은 소수점 2자리로 반올림한 뒤 비교한다. */
export function normalizeNumber(value: number): number {
  return round(value, 2);
}

/** float 오차만 다른 두 숫자를 같은 값으로 취급한다. */
export function numbersEqual(a: number, b: number): boolean {
  return normalizeNumber(a) === normalizeNumber(b);
}

export function colorsEqual(a: RgbaColor, b: RgbaColor): boolean {
  return normalizeColor(a) === normalizeColor(b);
}
