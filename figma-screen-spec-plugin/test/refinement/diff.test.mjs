import test from "node:test";
import assert from "node:assert/strict";
import { diffSnapshots } from "../../dist-test/refinement/diff.mjs";
import { MIXED_VALUE } from "../../dist-test/refinement/property-normalizer.mjs";

function snapshot(logicalNodeId, logicalType, properties) {
  return { logicalNodeId, logicalType, properties };
}

test("identical baseline and current snapshots produce no patches", () => {
  const baseline = [snapshot("qna-detail/detail/contact", "krds.detailRow", { width: 160, fill: { r: 1, g: 1, b: 1, a: 1 } })];
  const current = [snapshot("qna-detail/detail/contact", "krds.detailRow", { width: 160, fill: { r: 1, g: 1, b: 1, a: 1 } })];

  assert.deepEqual(diffSnapshots(baseline, current), []);
});

test("float precision noise alone does not produce a patch", () => {
  const baseline = [snapshot("n1", "krds.detailRow", { width: 160.0000001, fill: { r: 0.3, g: 0.3, b: 0.3, a: 1 } })];
  const current = [snapshot("n1", "krds.detailRow", { width: 160, fill: { r: 0.30000001, g: 0.3, b: 0.29999999, a: 1 } })];

  assert.deepEqual(diffSnapshots(baseline, current), []);
});

test("a real width change produces exactly one CONDITIONAL-scope NUMBER patch", () => {
  const baseline = [snapshot("qna-detail/detail/contact", "krds.detailRow", { width: 160 })];
  const current = [snapshot("qna-detail/detail/contact", "krds.detailRow", { width: 176 })];

  const patches = diffSnapshots(baseline, current);

  assert.equal(patches.length, 1);
  assert.equal(patches[0].propertyPath, "width");
  assert.equal(patches[0].propertyType, "NUMBER");
  assert.equal(patches[0].before, 160);
  assert.equal(patches[0].after, 176);
  assert.equal(patches[0].scope, "CONDITIONAL");
  assert.equal(patches[0].owner, "MANUAL_REFINEMENT");
  assert.equal(patches[0].conflictStatus, "NONE");
  assert.equal(patches[0].baselineLogicalType, "krds.detailRow");
});

test("a color change produces a COLOR patch with ALLOWED scope", () => {
  const baseline = [snapshot("n1", "krds.button", { fill: { r: 1, g: 1, b: 1, a: 1 } })];
  const current = [snapshot("n1", "krds.button", { fill: { r: 0.96, g: 0.97, b: 0.98, a: 1 } })];

  const patches = diffSnapshots(baseline, current);

  assert.equal(patches.length, 1);
  assert.equal(patches[0].propertyPath, "fill");
  assert.equal(patches[0].propertyType, "COLOR");
  assert.equal(patches[0].scope, "ALLOWED");
});

test("a blocked property (layoutMode) still produces a patch but scoped BLOCKED", () => {
  const baseline = [snapshot("n1", "krds.detailRow", { layoutMode: "VERTICAL" })];
  const current = [snapshot("n1", "krds.detailRow", { layoutMode: "HORIZONTAL" })];

  const patches = diffSnapshots(baseline, current);

  assert.equal(patches.length, 1);
  assert.equal(patches[0].scope, "BLOCKED");
});

test("mixed values on either side are skipped (MIXED_VALUE_UNSUPPORTED)", () => {
  const baseline = [snapshot("n1", "krds.textField", { "typography.fontSize": 14 })];
  const current = [snapshot("n1", "krds.textField", { "typography.fontSize": MIXED_VALUE })];

  assert.deepEqual(diffSnapshots(baseline, current), []);
});

test("a node missing from the current tree (removed) produces no patch here", () => {
  const baseline = [snapshot("n1", "krds.button", { opacity: 1 })];
  const current = [];

  assert.deepEqual(diffSnapshots(baseline, current), []);
});

test("multiple changed properties on one node each produce their own patch", () => {
  const baseline = [snapshot("n1", "krds.detailRow", { width: 160, itemSpacing: 8 })];
  const current = [snapshot("n1", "krds.detailRow", { width: 176, itemSpacing: 16 })];

  const patches = diffSnapshots(baseline, current);

  assert.equal(patches.length, 2);
  assert.deepEqual(patches.map(p => p.propertyPath).sort(), ["itemSpacing", "width"]);
});
