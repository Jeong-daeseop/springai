import test from "node:test";
import assert from "node:assert/strict";
import { classifyScope, isExplicitlyBlocked, defaultOwnerForCapture } from "../../dist-test/refinement/policy.mjs";

test("MVP allowed properties are classified as ALLOWED", () => {
  for (const path of [
    "fill", "stroke", "opacity", "cornerRadius",
    "typography.fontFamily", "typography.fontStyle", "typography.fontSize",
    "typography.letterSpacing", "typography.lineHeight",
    "padding.top", "padding.right", "padding.bottom", "padding.left",
    "itemSpacing", "textAlign",
  ]) {
    assert.equal(classifyScope(path), "ALLOWED", `expected ALLOWED for ${path}`);
  }
});

test("MVP conditional properties are classified as CONDITIONAL", () => {
  for (const path of ["width", "height", "minWidth", "minHeight", "layoutGrow", "layoutAlign"]) {
    assert.equal(classifyScope(path), "CONDITIONAL", `expected CONDITIONAL for ${path}`);
  }
});

test("unknown or explicitly blocked properties default to BLOCKED (whitelist policy)", () => {
  assert.equal(classifyScope("layoutMode"), "BLOCKED");
  assert.equal(classifyScope("visible"), "BLOCKED");
  assert.equal(classifyScope("logicalNodeId"), "BLOCKED");
  assert.equal(classifyScope("screenVersion"), "BLOCKED");
  assert.equal(classifyScope("someRandomFutureProperty"), "BLOCKED");
});

test("isExplicitlyBlocked flags the documented blocked property names", () => {
  assert.equal(isExplicitlyBlocked("layoutMode"), true);
  assert.equal(isExplicitlyBlocked("visible"), true);
  assert.equal(isExplicitlyBlocked("fill"), false);
});

test("captured patches are always owned by MANUAL_REFINEMENT", () => {
  assert.equal(defaultOwnerForCapture(), "MANUAL_REFINEMENT");
});
