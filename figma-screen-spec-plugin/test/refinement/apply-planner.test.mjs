import test from "node:test";
import assert from "node:assert/strict";
import { planPatchApplication, applicableDecisions } from "../../dist-test/refinement/apply-planner.mjs";

function patch(overrides) {
  return {
    logicalNodeId: "qna-detail/detail/contact",
    baselineLogicalType: "krds.detailRow",
    propertyPath: "width",
    propertyType: "NUMBER",
    before: 160,
    after: 176,
    owner: "MANUAL_REFINEMENT",
    scope: "CONDITIONAL",
    conflictStatus: "NONE",
    ...overrides,
  };
}

function patchSet(patches) {
  return {
    patchSetId: "qna-detail-refine-1",
    screenId: "qna-detail",
    baseScreenVersion: 3,
    baseMaterializationHash: "fnv1a32:aaaa:1",
    status: "APPROVED",
    patches,
  };
}

test("a patch whose baseline value still matches the current tree is APPLY", () => {
  const set = patchSet([patch()]);
  const tree = [{ logicalNodeId: "qna-detail/detail/contact", logicalType: "krds.detailRow", properties: { width: 160 } }];

  const decisions = planPatchApplication(set, tree);

  assert.equal(decisions.length, 1);
  assert.equal(decisions[0].action, "APPLY");
  assert.equal(applicableDecisions(decisions).length, 1);
});

test("SYSTEM_LAYOUT owner is always SKIP_BLOCKED regardless of current tree state", () => {
  const set = patchSet([patch({ owner: "SYSTEM_LAYOUT", scope: "ALLOWED" })]);
  const tree = [{ logicalNodeId: "qna-detail/detail/contact", logicalType: "krds.detailRow", properties: { width: 160 } }];

  const decisions = planPatchApplication(set, tree);

  assert.equal(decisions[0].action, "SKIP_BLOCKED");
});

test("BLOCKED scope is always SKIP_BLOCKED", () => {
  const set = patchSet([patch({ scope: "BLOCKED" })]);
  const tree = [{ logicalNodeId: "qna-detail/detail/contact", logicalType: "krds.detailRow", properties: { width: 160 } }];

  assert.equal(planPatchApplication(set, tree)[0].action, "SKIP_BLOCKED");
});

test("a target node missing from the new tree is SKIP_REMOVED", () => {
  const set = patchSet([patch()]);
  const decisions = planPatchApplication(set, []);

  assert.equal(decisions[0].action, "SKIP_REMOVED");
});

test("a REPLACE rematerialized tree reapplies the approved patch by stable logicalNodeId", () => {
  const set = patchSet([patch({ logicalNodeId: "screen/detail/contact", propertyPath: "opacity", before: 1, after: 0.8 })]);
  // REPLACE starts with an empty existing tree, then syncNode() creates this fresh tree
  // with the same logical identity. The planner must treat it exactly like MERGE.
  const rematerializedTree = [
    { logicalNodeId: "screen/detail/contact", logicalType: "krds.detailRow", properties: { opacity: 1 } },
  ];

  const decisions = planPatchApplication(set, rematerializedTree);

  assert.equal(decisions[0].action, "APPLY");
  assert.equal(applicableDecisions(decisions).length, 1);
});

test("a target node whose logical type changed is SKIP_TYPE_CHANGED", () => {
  const set = patchSet([patch()]);
  const tree = [{ logicalNodeId: "qna-detail/detail/contact", logicalType: "krds.button", properties: { width: 160 } }];

  assert.equal(planPatchApplication(set, tree)[0].action, "SKIP_TYPE_CHANGED");
});

test("a baseline value that no longer matches the current Screen Spec value is SKIP_CONFLICT", () => {
  const set = patchSet([patch({ before: 160 })]);
  const tree = [{ logicalNodeId: "qna-detail/detail/contact", logicalType: "krds.detailRow", properties: { width: 200 } }];

  const decisions = planPatchApplication(set, tree);

  assert.equal(decisions[0].action, "SKIP_CONFLICT");
});

test("a property absent from the current snapshot does not block application", () => {
  const set = patchSet([patch({ propertyPath: "itemSpacing", before: 8, after: 16 })]);
  const tree = [{ logicalNodeId: "qna-detail/detail/contact", logicalType: "krds.detailRow", properties: {} }];

  assert.equal(planPatchApplication(set, tree)[0].action, "APPLY");
});

test("decisions are sorted deterministically by logicalNodeId then propertyPath", () => {
  const set = patchSet([
    patch({ logicalNodeId: "n2", propertyPath: "opacity", before: 1, after: 0.5 }),
    patch({ logicalNodeId: "n1", propertyPath: "width", before: 160, after: 176 }),
    patch({ logicalNodeId: "n1", propertyPath: "height", before: 44, after: 56 }),
  ]);
  const tree = [
    { logicalNodeId: "n1", logicalType: "krds.detailRow", properties: { width: 160, height: 44 } },
    { logicalNodeId: "n2", logicalType: "krds.detailRow", properties: { opacity: 1 } },
  ];

  const decisions = planPatchApplication(set, tree);

  assert.deepEqual(
    decisions.map(d => `${d.patch.logicalNodeId}/${d.patch.propertyPath}`),
    ["n1/height", "n1/width", "n2/opacity"],
  );
});

test("planning is a pure function: same input always produces the same ordered output", () => {
  const set = patchSet([
    patch({ logicalNodeId: "n2", propertyPath: "opacity" }),
    patch({ logicalNodeId: "n1", propertyPath: "width" }),
  ]);
  const tree = [
    { logicalNodeId: "n1", logicalType: "krds.detailRow", properties: { width: 160 } },
    { logicalNodeId: "n2", logicalType: "krds.detailRow", properties: { opacity: 1 } },
  ];

  const first = planPatchApplication(set, tree).map(d => d.patch.logicalNodeId);
  const second = planPatchApplication(set, tree).map(d => d.patch.logicalNodeId);

  assert.deepEqual(first, second);
});
