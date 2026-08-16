import test from "node:test";
import assert from "node:assert/strict";
import {
  normalizeColor, normalizePaintOrder, normalizeNumber, numbersEqual, colorsEqual,
  isMixedValue, MIXED_VALUE,
} from "../../dist-test/refinement/property-normalizer.mjs";

test("normalizeColor rounds float RGBA to a stable hex string", () => {
  assert.equal(normalizeColor({ r: 1, g: 0, b: 0, a: 1 }), "#FF0000FF");
  assert.equal(normalizeColor({ r: 0, g: 0, b: 0 }), "#000000FF");
});

test("normalizeColor treats float precision noise as the same color", () => {
  const a = normalizeColor({ r: 0.4999999999, g: 0.5, b: 0.5, a: 1 });
  const b = normalizeColor({ r: 0.5, g: 0.5, b: 0.5, a: 1 });
  assert.equal(a, b);
});

test("colorsEqual is decision-stable across repeated calls", () => {
  const color = { r: 0.29999999, g: 0.3, b: 0.30000001, a: 1 };
  for (let i = 0; i < 20; i++) {
    assert.equal(colorsEqual(color, { r: 0.3, g: 0.3, b: 0.3, a: 1 }), true);
  }
});

test("normalizeNumber rounds to two decimal places deterministically", () => {
  assert.equal(normalizeNumber(160.001), 160);
  assert.equal(normalizeNumber(160.005), 160.01);
  assert.equal(normalizeNumber(176), 176);
});

test("numbersEqual treats float rounding noise as equal", () => {
  assert.equal(numbersEqual(160.0000001, 160), true);
  assert.equal(numbersEqual(160, 176), false);
});

test("normalizePaintOrder sorts paints independent of original array order", () => {
  const paintsA = [
    { type: "SOLID", opacity: 1, color: { r: 1, g: 0, b: 0 } },
    { type: "SOLID", opacity: 0.5, color: { r: 0, g: 0, b: 1 } },
  ];
  const paintsB = [paintsA[1], paintsA[0]];

  assert.deepEqual(normalizePaintOrder(paintsA), normalizePaintOrder(paintsB));
});

test("isMixedValue recognizes the shared MIXED_VALUE sentinel only", () => {
  assert.equal(isMixedValue(MIXED_VALUE), true);
  assert.equal(isMixedValue("normal-string"), false);
  assert.equal(isMixedValue(undefined), false);
});
