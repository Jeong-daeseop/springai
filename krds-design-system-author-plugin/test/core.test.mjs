import test from "node:test";
import assert from "node:assert/strict";
import {
  componentSnapshot,
  figmaVariableName,
  isAbsoluteHttpUrl,
  normalizePluginError,
  planComponentChange,
  transitionReviewStatus,
  utf8Bytes,
  validateSpec,
} from "../dist-test/core.mjs";

const button = {
  id: "krds.button",
  name: "KRDS/Button",
  description: "업무 버튼",
  developer: {
    codeComponent: "KrdsButton",
    documentationUrl: "https://www.krds.go.kr/",
    packageName: "com.krdevops.ui",
  },
  layout: {
    mode: "HORIZONTAL",
    paddingX: "16",
    paddingY: "12",
    gap: "8",
    alignment: "CENTER",
    minWidth: "80",
    maxWidth: "320",
  },
  properties: [{ name: "Label", type: "TEXT", defaultValue: "버튼" }],
  variants: { Type: ["Primary", "Secondary"] },
};

function spec(components = [button]) {
  return {
    id: "ftc-krds",
    name: "FTC KRDS",
    version: "1.0.0",
    tokens: [],
    variableCollections: [],
    components,
    patterns: [],
    issues: [],
  };
}

test("sample DesignSystemSpec validates and preserves component count", () => {
  const result = validateSpec(spec());
  assert.equal(result.errors.length, 0);
  assert.equal(result.parsed.components.length, 1);
});

test("documentation URL validation works without the browser URL global", () => {
  assert.equal(isAbsoluteHttpUrl("https://www.krds.go.kr/"), true);
  assert.equal(isAbsoluteHttpUrl("http://localhost:8080/docs"), true);
  assert.equal(isAbsoluteHttpUrl("/relative/docs"), false);
  assert.equal(isAbsoluteHttpUrl("javascript:alert(1)"), false);
});

test("UTF-8 encoding works without the browser TextEncoder global", () => {
  assert.deepEqual([...utf8Bytes("A한")], [65, 237, 149, 156]);
});

test("logical token ids are normalized to valid Figma variable paths", () => {
  assert.equal(figmaVariableName("color.primary"), "color/primary");
  assert.equal(figmaVariableName(" spacing..16 "), "spacing/16");
  assert.equal(figmaVariableName(" typography / body / font weight "), "typography/body/font-weight");
  assert.equal(figmaVariableName("../"), "token/unnamed");
  assert.equal(figmaVariableName("\u0000\u0001"), "token/unnamed");
});

test("validation error contains precise path and target id", () => {
  const invalid = structuredClone(button);
  invalid.layout.minWidth = "500";
  invalid.layout.maxWidth = "100";
  const result = validateSpec(spec([invalid]));
  assert.deepEqual(
    result.errors.map(error => [error.code, error.path, error.targetId]),
    [["INVALID_LAYOUT_RANGE", "/components/0/layout", "krds.button"]],
  );
});

test("same component plan is idempotent and does not add duplicates", () => {
  const snapshot = componentSnapshot(button);
  const planned = planComponentChange(snapshot, structuredClone(button));
  assert.equal(planned.kind, "NO_CHANGE");
  assert.deepEqual(planned.comparisons, []);
});

test("property addition updates in place while removal is breaking", () => {
  const snapshot = componentSnapshot(button);
  const added = structuredClone(button);
  added.properties.push({ name: "Disabled", type: "BOOLEAN", defaultValue: "false" });
  assert.equal(planComponentChange(snapshot, added).kind, "UPDATE");

  const removed = structuredClone(button);
  removed.properties = [];
  assert.equal(planComponentChange(snapshot, removed).kind, "BREAKING");
});

test("deprecated component requires an existing replacement and non-conflicting aliases", () => {
  const deprecated = structuredClone(button);
  deprecated.lifecycleStatus = "DEPRECATED";
  deprecated.replacementLogicalType = "krds.action-button";
  deprecated.aliases = ["egov.button"];
  const replacement = structuredClone(button);
  replacement.id = "krds.action-button";
  replacement.name = "KRDS/ActionButton";

  assert.equal(validateSpec(spec([deprecated, replacement])).errors.length, 0);

  deprecated.replacementLogicalType = "krds.missing";
  assert.deepEqual(
    validateSpec(spec([deprecated, replacement])).errors.map(error => error.code),
    ["INVALID_COMPONENT_REPLACEMENT"],
  );
});

test("review lifecycle requires review before approval", () => {
  assert.equal(transitionReviewStatus("DRAFT", "REVIEW"), "IN_REVIEW");
  assert.equal(transitionReviewStatus("IN_REVIEW", "APPROVAL"), "APPROVED");
  assert.equal(transitionReviewStatus("IN_REVIEW", "REJECTION"), "REJECTED");
  assert.throws(
    () => transitionReviewStatus("DRAFT", "APPROVAL"),
    /IN_REVIEW/,
  );
});

test("Figma permission and rate limit failures are reported distinctly", () => {
  assert.deepEqual(normalizePluginError(new Error("Permission denied")), {
    code: "FIGMA_PERMISSION_DENIED",
    message: "Permission denied",
    retryable: false,
  });
  assert.deepEqual(normalizePluginError(new Error("Rate limit exceeded")), {
    code: "FIGMA_API_LIMIT",
    message: "Rate limit exceeded",
    retryable: true,
  });
});
