import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import {
  describeLayoutAnnotations,
  generationStatus,
  visualRegressionStatus,
  sectionVisualRegression,
  contrastRatio,
  meetsWcagAaContrast,
  applyComponentSwaps,
  planPlatformLayout,
  planViewportFixtures,
  isUserOverridden,
  mappedProperties,
  planFallback,
  planMultiScreenApply,
  previewLegacyMigration,
  reconcile,
  runAtomicApply,
  registryFor,
  selectVariantName,
  validateBundle
} from "../dist-test/core.mjs";

test("R0-028/BASE-18: policy plan calculates grid and rejects freeform frames before mutation", () => {
  const result = planPlatformLayout(
    { platform: "DESKTOP", viewportWidth: 1440, gridColumns: 12, gapPx: 24, paddingPx: 40 },
    { width: 1440, layoutMode: "NONE" },
  );
  assert.equal(result.contentWidth, 1360);
  assert.equal(result.usableWidth, 1096);
  assert.equal(result.columnWidth, 91.33333333333333);
  assert.deepEqual(result.issues, ["AUTO_LAYOUT_REQUIRED"]);
});

test("R0-028/BASE-18: policy plan accepts a matching auto-layout frame", () => {
  const result = planPlatformLayout(
    { platform: "MOBILE", viewportWidth: 390, gridColumns: 4, gapPx: 12, paddingPx: 16 },
    { width: 390, layoutMode: "VERTICAL" },
  );
  assert.deepEqual(result.issues, []);
  assert.equal(result.columnWidth, 80.5);
});

test("R0-028/BASE-18: fixture plan creates deterministic Tablet/Mobile policies only from Desktop", () => {
  assert.deepEqual(planViewportFixtures(1440), [
    { platform: "TABLET", width: 768, gridColumns: 8, gapPx: 16, paddingPx: 24, nameSuffix: " · TABLET" },
    { platform: "MOBILE", width: 390, gridColumns: 4, gapPx: 12, paddingPx: 16, nameSuffix: " · MOBILE" },
  ]);
  assert.deepEqual(planViewportFixtures(768), []);
});

test("contrastRatio is 21:1 for pure black on pure white", () => {
  const ratio = contrastRatio({ r: 0, g: 0, b: 0 }, { r: 1, g: 1, b: 1 });
  assert.ok(Math.abs(ratio - 21) < 0.01, `expected ~21, got ${ratio}`);
});

test("contrastRatio is 1:1 for identical colors", () => {
  const ratio = contrastRatio({ r: 0.5, g: 0.5, b: 0.5 }, { r: 0.5, g: 0.5, b: 0.5 });
  assert.ok(Math.abs(ratio - 1) < 0.001);
});

test("contrastRatio is symmetric regardless of foreground/background order", () => {
  const a = contrastRatio({ r: 0.2, g: 0.4, b: 0.6 }, { r: 0.9, g: 0.9, b: 0.9 });
  const b = contrastRatio({ r: 0.9, g: 0.9, b: 0.9 }, { r: 0.2, g: 0.4, b: 0.6 });
  assert.ok(Math.abs(a - b) < 1e-9);
});

test("meetsWcagAaContrast requires 4.5:1 for normal-size text", () => {
  assert.equal(meetsWcagAaContrast(4.5, 14, false), true);
  assert.equal(meetsWcagAaContrast(4.49, 14, false), false);
});

test("meetsWcagAaContrast relaxes to 3:1 for large text (24px+ or 18.66px+ bold)", () => {
  assert.equal(meetsWcagAaContrast(3, 24, false), true);
  assert.equal(meetsWcagAaContrast(2.99, 24, false), false);
  assert.equal(meetsWcagAaContrast(3, 19, true), true);
  assert.equal(meetsWcagAaContrast(3, 16, true), false);
});

test("section visual regression creates baseline on first run", () => {
  const evidence = [{ sectionId: "header", hash: "h1" }, { sectionId: "table", hash: "h2" }];
  const result = sectionVisualRegression(evidence, null);
  assert.equal(result.status, "BASELINE_CREATED");
  assert.equal(result.diffRatio, 0);
  assert.deepEqual(result.changedSections, []);
});

test("section visual regression requires baseline when flagged", () => {
  const evidence = [{ sectionId: "header", hash: "h1" }];
  assert.equal(sectionVisualRegression(evidence, null, 0, true).status, "FAILED");
});

test("section visual regression reports a real diffRatio instead of only 0 or 1", () => {
  const baseline = [
    { sectionId: "header", hash: "h1" },
    { sectionId: "search", hash: "h2" },
    { sectionId: "table", hash: "h3" },
    { sectionId: "action", hash: "h4" },
  ];
  const evidence = [
    { sectionId: "header", hash: "h1" },
    { sectionId: "search", hash: "h2" },
    { sectionId: "table", hash: "h3-changed" },
    { sectionId: "action", hash: "h4" },
  ];
  const result = sectionVisualRegression(evidence, baseline);
  assert.equal(result.status, "FAILED");
  assert.equal(result.diffRatio, 0.25);
  assert.deepEqual(result.changedSections, ["table"]);
});

test("section visual regression counts added and removed sections as changed", () => {
  const baseline = [{ sectionId: "header", hash: "h1" }, { sectionId: "footer", hash: "h9" }];
  const evidence = [{ sectionId: "header", hash: "h1" }, { sectionId: "table", hash: "h3" }];
  const result = sectionVisualRegression(evidence, baseline);
  assert.equal(result.diffRatio, 2 / 3);
  assert.deepEqual(result.changedSections, ["footer", "table"]);
});

test("section visual regression passes when nothing changed", () => {
  const sections = [{ sectionId: "header", hash: "h1" }, { sectionId: "table", hash: "h2" }];
  const result = sectionVisualRegression(sections, sections);
  assert.equal(result.status, "PASSED");
  assert.equal(result.diffRatio, 0);
});

test("section visual regression respects a nonzero threshold", () => {
  const baseline = [
    { sectionId: "a", hash: "1" }, { sectionId: "b", hash: "2" },
    { sectionId: "c", hash: "3" }, { sectionId: "d", hash: "4" },
  ];
  const evidence = [
    { sectionId: "a", hash: "1" }, { sectionId: "b", hash: "2-changed" },
    { sectionId: "c", hash: "3" }, { sectionId: "d", hash: "4" },
  ];
  assert.equal(sectionVisualRegression(evidence, baseline, 0).status, "FAILED");
  assert.equal(sectionVisualRegression(evidence, baseline, 0.25).status, "PASSED");
});

test("visual regression creates first baseline then blocks changed pixel evidence", () => {
  assert.equal(visualRegressionStatus("hash-a", null), "BASELINE_CREATED");
  assert.equal(visualRegressionStatus("hash-a", null, true), "FAILED");
  assert.equal(visualRegressionStatus("hash-a", "hash-a"), "PASSED");
  assert.equal(visualRegressionStatus("hash-b", "hash-a"), "FAILED");
});

test("fallback is a failed generation instead of partial success", () => {
  assert.equal(generationStatus(false, 1), "FAILED");
  assert.equal(generationStatus(false, 0), "SUCCESS");
  assert.equal(generationStatus(true, 0), "FAILED");
});

test("atomic apply commits only after staging population and post-validation", async () => {
  const events = [];
  const result = await runAtomicApply({
    createBackup: async () => { events.push("backup"); return {id:"backup"}; },
    createStaging: async () => { events.push("staging"); return {id:"staging"}; },
    populateStaging: async () => { events.push("populate"); },
    validateStaging: async () => { events.push("validate"); },
    commit: async () => { events.push("commit"); return "success"; },
    rollback: async () => { events.push("rollback"); },
  });
  assert.equal(result, "success");
  assert.deepEqual(events, ["backup", "staging", "populate", "validate", "commit"]);
});

test("atomic apply rolls back staging when property application fails", async () => {
  const state = {existing:"original", staging:false, committed:false};
  await assert.rejects(() => runAtomicApply({
    createBackup: async () => ({existing:state.existing}),
    createStaging: async () => { state.staging = true; return {id:"staging"}; },
    populateStaging: async () => { throw new Error("PROPERTY_APPLY_FAILED"); },
    validateStaging: async () => { throw new Error("must not run"); },
    commit: async () => { state.committed = true; },
    rollback: async () => { state.staging = false; state.existing = "original"; },
  }), /PROPERTY_APPLY_FAILED/);
  assert.deepEqual(state, {existing:"original", staging:false, committed:false});
});

test("atomic apply restores existing root when post-validation fails", async () => {
  const state = {existing:"original", staging:false, committed:false};
  await assert.rejects(() => runAtomicApply({
    createBackup: async () => ({existing:state.existing}),
    createStaging: async () => { state.staging = true; return {id:"staging"}; },
    populateStaging: async () => {},
    validateStaging: async () => { throw new Error("POST_VALIDATION_FAILED"); },
    commit: async () => { state.existing = "archived"; state.committed = true; },
    rollback: async (_staging, backup) => {
      state.staging = false;
      state.existing = backup?.existing ?? "missing";
    },
  }), /POST_VALIDATION_FAILED/);
  assert.deepEqual(state, {existing:"original", staging:false, committed:false});
});

test("atomic apply restores archived root when commit fails midway", async () => {
  const state = {existing:"original", staging:false, activeRoot:"original"};
  await assert.rejects(() => runAtomicApply({
    createBackup: async () => ({existing:state.existing}),
    createStaging: async () => { state.staging = true; return {id:"staging"}; },
    populateStaging: async () => {},
    validateStaging: async () => {},
    commit: async () => {
      state.existing = "archived";
      state.activeRoot = "staging";
      throw new Error("COMMIT_INTERRUPTED");
    },
    rollback: async (_staging, backup) => {
      state.staging = false;
      state.existing = backup?.existing ?? "missing";
      state.activeRoot = backup?.existing ?? "missing";
    },
  }), /COMMIT_INTERRUPTED/);
  assert.deepEqual(state, {existing:"original", staging:false, activeRoot:"original"});
});

const registryEntry = {
  componentSetKey: "BUTTON_SET_KEY",
  publishStatus: "CURRENT",
  variants: {
    "Style=Primary, Size=Medium": "BUTTON_PRIMARY_KEY",
    "Style=Secondary, Size=Medium": "BUTTON_SECONDARY_KEY"
  },
  properties: {
    style: {figmaProperty: "Style", type: "VARIANT", values: {primary: "Primary", secondary: "Secondary"}},
    size: {figmaProperty: "Size", type: "VARIANT", values: {medium: "Medium"}},
    label: {figmaProperty: "Label", type: "TEXT", values: {}},
    disabled: {figmaProperty: "Disabled", type: "BOOLEAN", values: {}}
  }
};

function node(logicalNodeId, type, properties = {}, children = [], nodeType = "COMPONENT") {
  return {logicalNodeId, nodeType, type, properties, children};
}

function validBundle() {
  const content = node("user-list", "egov.listPage", {}, [
    {
      ...node("user-list/action/create", "krds.button", {
        semanticRole:"action.primary", label:"등록", state:"DEFAULT"
      }),
      componentResolution: {
        role:"action.primary", logicalType:"krds.button", componentSetKey:"BUTTON_SET_KEY",
        variantKey:"BUTTON_PRIMARY_KEY", variantProperties:{Style:"Primary", Size:"Medium"},
        componentProperties:{Label:"등록"}, contractVersion:"2.0.0", ruleSetVersion:"1.0.0",
        ruleId:"button-primary", contextHash:"a".repeat(64)
      }
    }
  ], "PAGE");
  return {
    figmaScreenSpec: {
      screenId:"user-list", screenVersion:1, screenSpecificationId:"users", screenSpecificationVersion:3,
      screenType:"LIST", layoutPattern:"STANDARD", name:"사용자 목록", viewport:"DESKTOP", status:"APPROVED",
      designSystem:{profileId:"ftc-krds", profileVersion:"1.0.0", registryVersion:"registry-1"},
      content, issues:[], semanticPattern:"crud.list", screenPatternVersion:"1.0.0",
      variantRuleSetVersion:"1.0.0", componentContractVersion:"2.0.0"
    },
    designSystemProfile: {
      profile:{id:"ftc-krds", version:"1.0.0", registryVersion:"registry-1", status:"PUBLISHED"},
      snapshotAt:"2026-07-27T00:00:00Z"
    },
    componentRegistry: {
      registry:{
        profileId:"ftc-krds", profileVersion:"1.0.0", registryVersion:"registry-1",
        components:{
          "egov.listPage": {...registryEntry, componentSetKey:"LIST_PAGE_SET_KEY"},
          "krds.button": registryEntry
        }
      },
      snapshotAt:"2026-07-27T00:00:00Z"
    },
    screenPattern: {
      pattern:{pattern:"crud.list", version:"1.0.0", status:"PUBLISHED", slots:[]},
      snapshotAt:"2026-07-27T00:00:00Z"
    },
    variantRuleSet: {
      ruleSet:{
        id:"rules", version:"1.0.0", profileId:"ftc-krds", registryVersion:"registry-1",
        status:"PUBLISHED", rules:[{ruleId:"button-primary"}]
      },
      snapshotAt:"2026-07-27T00:00:00Z"
    },
    metadata:{
      exportedAt:"2026-07-27T00:00:00Z", figmaScreenSpecSchemaVersion:"figma-screen-spec-v2",
      screenSpecificationVersion:3, designSystemProfileVersion:"1.0.0", registryVersion:"registry-1",
      screenPatternVersion:"1.0.0", variantRuleSetVersion:"1.0.0", componentContractVersion:"2.0.0"
    }
  };
}

function legacyV1Bundle() {
  const bundle = validBundle();
  bundle.metadata.figmaScreenSpecSchemaVersion = "figma-screen-spec-v1";
  delete bundle.metadata.screenPatternVersion;
  delete bundle.metadata.variantRuleSetVersion;
  delete bundle.metadata.componentContractVersion;
  delete bundle.figmaScreenSpec.semanticPattern;
  delete bundle.figmaScreenSpec.screenPatternVersion;
  delete bundle.figmaScreenSpec.variantRuleSetVersion;
  delete bundle.figmaScreenSpec.componentContractVersion;
  delete bundle.screenPattern;
  delete bundle.variantRuleSet;
  for (const {node: current} of flattenForTest(bundle.figmaScreenSpec.content)) {
    delete current.componentResolution;
  }
  return bundle;
}

function flattenForTest(root) {
  const result = [];
  const visit = current => {
    result.push({node: current});
    for (const child of current.children ?? []) visit(child);
  };
  visit(root);
  return result;
}

test("valid published bundle passes validation", () => {
  const result = validateBundle(validBundle());
  assert.ok(result.parsed);
  assert.equal(result.contractMode, "V2_APPLY");
  assert.deepEqual(result.issues, []);
});

test("SSOT evidence must be complete and use SHA-256 hashes", () => {
  const incomplete = validBundle();
  incomplete.metadata.catalogVersion = "2.0.0";
  assert.equal(validateBundle(incomplete).issues.some(issue =>
    issue.code === "SSOT_EVIDENCE_INCOMPLETE"), true);

  const invalidHash = validBundle();
  invalidHash.metadata.catalogVersion = "2.0.0";
  invalidHash.metadata.catalogHash = "not-a-sha256";
  invalidHash.metadata.registryHash = "b".repeat(64);
  assert.equal(validateBundle(invalidHash).issues.some(issue =>
    issue.code === "SSOT_EVIDENCE_HASH_INVALID"), true);

  const valid = validBundle();
  valid.metadata.catalogVersion = "2.0.0";
  valid.metadata.catalogHash = "a".repeat(64);
  valid.metadata.registryHash = "b".repeat(64);
  valid.resolvedComponentRegistry = {
    profileId: "ftc-krds",
    profileVersion: "1.0.0",
    registryVersion: "registry-1",
    catalogVersion: "2.0.0",
    catalogHash: "a".repeat(64),
    registryHash: "b".repeat(64),
    components: valid.componentRegistry.registry.components,
  };
  assert.equal(validateBundle(valid).issues.some(issue =>
    issue.code.startsWith("SSOT_EVIDENCE_")), false);
  assert.equal(validateBundle(valid).issues.some(issue =>
    issue.code.startsWith("RESOLVED_COMPONENT_REGISTRY_")), false);
});

test("SSOT bundle rejects missing or mismatched resolved registry projection", () => {
  const missing = validBundle();
  missing.metadata.catalogVersion = "2.0.0";
  missing.metadata.catalogHash = "a".repeat(64);
  missing.metadata.registryHash = "b".repeat(64);
  assert.equal(validateBundle(missing).issues.some(issue =>
    issue.code === "RESOLVED_COMPONENT_REGISTRY_MISSING"), true);

  const mismatched = validBundle();
  mismatched.metadata.catalogVersion = "2.0.0";
  mismatched.metadata.catalogHash = "a".repeat(64);
  mismatched.metadata.registryHash = "b".repeat(64);
  mismatched.resolvedComponentRegistry = {
    profileId: "ftc-krds", profileVersion: "1.0.0", registryVersion: "registry-1",
    catalogVersion: "2.0.0", catalogHash: "c".repeat(64), registryHash: "b".repeat(64),
    components: mismatched.componentRegistry.registry.components,
  };
  assert.equal(validateBundle(mismatched).issues.some(issue =>
    issue.code === "RESOLVED_COMPONENT_REGISTRY_EVIDENCE_MISMATCH"), true);
});

test("SSOT preview and apply registry selection both prefer the server-resolved projection", () => {
  const bundle = validBundle();
  bundle.metadata.catalogVersion = "2.0.0";
  bundle.metadata.catalogHash = "a".repeat(64);
  bundle.metadata.registryHash = "b".repeat(64);
  bundle.resolvedComponentRegistry = {
    profileId: "ftc-krds", profileVersion: "1.0.0", registryVersion: "registry-1",
    catalogVersion: "2.0.0", catalogHash: "a".repeat(64), registryHash: "b".repeat(64),
    components: {
      ...bundle.componentRegistry.registry.components,
      "krds.button": {
        ...bundle.componentRegistry.registry.components["krds.button"],
        componentSetKey: "SERVER_RESOLVED_BUTTON_SET",
      },
    },
  };
  for (const {node} of flattenForTest(bundle.figmaScreenSpec.content)) {
    if (node.type === "krds.button" && node.componentResolution) {
      node.componentResolution.componentSetKey = "SERVER_RESOLVED_BUTTON_SET";
    }
  }

  assert.deepEqual(validateBundle(bundle).issues, []);
  assert.equal(registryFor(bundle).components["krds.button"].componentSetKey,
    "SERVER_RESOLVED_BUTTON_SET");
});

test("v2 bundle requires pattern and rule set snapshots", () => {
  const bundle = validBundle();
  delete bundle.screenPattern;
  delete bundle.variantRuleSet;

  const result = validateBundle(bundle);

  assert.deepEqual(result.issues.map(issue => issue.code), [
    "SCREEN_PATTERN_SNAPSHOT_MISSING", "VARIANT_RULE_SET_SNAPSHOT_MISSING"
  ]);
});

test("v2 bundle rejects mismatched or unpublished snapshots", () => {
  const bundle = validBundle();
  bundle.screenPattern.pattern.version = "9.9.9";
  bundle.variantRuleSet.ruleSet.status = "DRAFT";

  const result = validateBundle(bundle);

  assert.deepEqual(result.issues.map(issue => issue.code), [
    "SCREEN_PATTERN_SNAPSHOT_MISMATCH", "VARIANT_RULE_SET_NOT_PUBLISHED"
  ]);
});

test("v1 bundle is accepted only for legacy migration preview", () => {
  const result = validateBundle(legacyV1Bundle());
  assert.ok(result.parsed);
  assert.equal(result.contractMode, "V1_MIGRATION_PREVIEW");
  assert.equal(result.issues.some(issue =>
    issue.code === "LEGACY_SCHEMA_MIGRATION_PREVIEW_ONLY" && issue.severity === "WARNING"), true);
  assert.equal(result.issues.some(issue => issue.severity === "FATAL" || issue.severity === "ERROR"), false);
});

test("v2 bundle requires Role and Variant contract fields", () => {
  const bundle = validBundle();
  delete bundle.figmaScreenSpec.variantRuleSetVersion;
  const result = validateBundle(bundle);
  assert.equal(result.contractMode, "V2_APPLY");
  assert.equal(result.issues.some(issue => issue.code === "SCREEN_SPEC_V2_REQUIRED"), true);
});

test("unknown schema version is rejected", () => {
  const bundle = validBundle();
  bundle.metadata.figmaScreenSpecSchemaVersion = "figma-screen-spec-v3";
  const result = validateBundle(bundle);
  assert.equal(result.contractMode, undefined);
  assert.equal(result.issues.some(issue => issue.code === "SCHEMA_VERSION_UNSUPPORTED"), true);
});

test("Q&A six runtime v2 bundles pass plugin validation and preview reconciliation", () => {
  const fixtureRoot = new URL("../../website-figma-contract/fixtures/qna/", import.meta.url);
  const registry = JSON.parse(fs.readFileSync(new URL("krds-component-registry-v2.json", fixtureRoot), "utf8"));
  const ruleSet = JSON.parse(fs.readFileSync(new URL("variant-rule-set-krds-v2-candidate.json", fixtureRoot), "utf8"));
  const files = [
    "qna-list.json", "qna-create.json", "qna-detail.json",
    "qna-answer-list.json", "qna-answer-detail.json", "qna-answer-create.json",
  ];
  for (const file of files) {
    const screen = JSON.parse(fs.readFileSync(new URL(`v2/${file}`, fixtureRoot), "utf8"));
    const bundle = {
      figmaScreenSpec: {...screen, status:"APPROVED"},
      designSystemProfile: {
        profile:{
          id:registry.profileId, version:registry.profileVersion,
          registryVersion:registry.registryVersion, status:"PUBLISHED",
          libraryFileKey:registry.library.fileKey,
        },
        snapshotAt:"2026-08-12T00:00:00Z",
      },
      componentRegistry:{registry, snapshotAt:"2026-08-12T00:00:00Z"},
      screenPattern:{
        pattern:{pattern:screen.semanticPattern, version:screen.screenPatternVersion, status:"PUBLISHED", slots:[]},
        snapshotAt:"2026-08-12T00:00:00Z",
      },
      variantRuleSet:{ruleSet:{...ruleSet, status:"PUBLISHED"}, snapshotAt:"2026-08-12T00:00:00Z"},
      metadata:{
        exportedAt:"2026-08-12T00:00:00Z",
        figmaScreenSpecSchemaVersion:"figma-screen-spec-v2",
        screenSpecificationVersion:screen.screenSpecificationVersion,
        designSystemProfileVersion:registry.profileVersion,
        registryVersion:registry.registryVersion,
        screenPatternVersion:screen.screenPatternVersion,
        variantRuleSetVersion:screen.variantRuleSetVersion,
        componentContractVersion:screen.componentContractVersion,
      },
    };
    const validated = validateBundle(bundle);
    assert.equal(validated.contractMode, "V2_APPLY", file);
    assert.deepEqual(validated.issues, [], `${file}: ${JSON.stringify(validated.issues)}`);
    const changes = reconcile(screen.content, []);
    assert.ok(changes.length > 0, `${file}: Preview 변경 목록이 비어 있습니다.`);
    assert.equal(changes.every(change => change.changeType === "ADD"), true, file);
  }
});

test("R5-T08: preview and reconciliation preserve logical identity across a swap scenario", () => {
  const bundle = validBundle();
  const desired = bundle.figmaScreenSpec.content;
  desired.children.push(node("user-list/table", "egov.dataTable", {}, [
    node("user-list/table/row-1", "egov.tableRow", {label: "첫 번째"}, [], "FRAME"),
  ], "FRAME"));
  const existing = [
    {logicalNodeId: "user-list", logicalType: "egov.listPage", parentLogicalNodeId: null, order: 0},
    {logicalNodeId: "user-list/action/create", logicalType: "krds.button", parentLogicalNodeId: "user-list", order: 0},
    {logicalNodeId: "user-list/table", logicalType: "egov.dataTable", parentLogicalNodeId: "user-list", order: 1},
  ];
  const changes = reconcile(desired, existing);
  assert.deepEqual(changes.map(change => [change.logicalNodeId, change.changeType]), [
    ["user-list", "REUSE"],
    ["user-list/action/create", "REUSE"],
    ["user-list/table", "REUSE"],
    ["user-list/table/row-1", "ADD"],
  ]);
  const swapped = applyComponentSwaps(desired, bundle.componentRegistry.registry, [
    {requestedLogicalType: "krds.button", resolvedLogicalType: "krds.button", swapped: true},
  ]);
  assert.equal(swapped.children[0].logicalNodeId, "user-list/action/create");
  assert.equal(reconcile(swapped, existing).filter(change => change.changeType === "CONFLICT").length, 0);
});

test("R5-T03: 신규 logicalNodeId만 ADD로 판정되고 기존 노드는 REUSE된다", () => {
  const desired = validBundle().figmaScreenSpec.content;
  const existing = flattenForTest(desired).map(({node}, index) => ({
    logicalNodeId: node.logicalNodeId,
    logicalType: node.type,
    parentLogicalNodeId: index === 0 ? null : "user-list",
    order: index,
  }));
  desired.children.push(node("user-list/new-row", "egov.tableRow", {}, [], "FRAME"));
  const changes = reconcile(desired, existing);
  assert.equal(changes.filter(change => change.changeType === "ADD").map(change => change.logicalNodeId)
    .join(","), "user-list/new-row");
  assert.ok(changes.filter(change => change.changeType === "REUSE").length >= 1);
});

test("R5-T08: 7개 요청을 순차 적용해도 Preview diff와 Reconciliation 결과가 일치한다", () => {
  let existing = [];
  for (let request = 1; request <= 7; request += 1) {
    const desired = validBundle().figmaScreenSpec.content;
    desired.children.push(node(`user-list/request-${request}`, "egov.tableRow", {}, [], "FRAME"));
    const preview = reconcile(desired, existing);
    const expectedAdds = request === 1 ? 3 : 1;
    assert.equal(preview.filter(change => change.changeType === "ADD").length, expectedAdds);
    assert.equal(preview.filter(change => change.changeType === "CONFLICT").length, 0);
    existing = flattenForTest(desired).map(({node: current}, index) => ({
      logicalNodeId: current.logicalNodeId,
      logicalType: current.type,
      parentLogicalNodeId: index === 0 ? null : "user-list",
      order: index,
    }));
  }
});

test("v1 legacy migration computes mappings but never enables migration apply", () => {
  const preview = previewLegacyMigration(legacyV1Bundle(), [
    {nodeId:"1:1", name:"user-list egov listPage", nodeType:"FRAME", logicalNodeId:null, hasLocalInstance:false},
    {nodeId:"1:2", name:"create 등록 button", nodeType:"FRAME", logicalNodeId:null, hasLocalInstance:true}
  ]);
  assert.equal(preview.operations.length > 0, true);
  assert.equal(preview.canApply, false);
  assert.equal(preview.issues.some(issue => issue.code === "LEGACY_SCHEMA_MIGRATION_PREVIEW_ONLY"), true);
});

test("structural semantic role does not require a published component resolution", () => {
  const bundle = validBundle();
  bundle.figmaScreenSpec.content.properties.semanticRole = "form.container";
  const result = validateBundle(bundle);
  assert.equal(result.issues.some(issue => issue.code === "ROLE_NOT_RESOLVED"), false);
});

test("missing required registry component is fatal", () => {
  const bundle = validBundle();
  delete bundle.componentRegistry.registry.components["krds.button"];
  const result = validateBundle(bundle);
  assert.equal(result.issues.some(issue => issue.code === "REQUIRED_COMPONENT_MISSING"), true);
});

test("profile and registry version mismatch is fatal", () => {
  const bundle = validBundle();
  bundle.metadata.registryVersion = "registry-other";
  const result = validateBundle(bundle);
  assert.equal(result.issues.some(issue => issue.code === "METADATA_REGISTRY_VERSION_MISMATCH"), true);
});

test("reconciliation reuses, moves, adds and archives deterministically", () => {
  const root = validBundle().figmaScreenSpec.content;
  root.children.push(node("user-list/action/delete", "krds.button"));
  const changes = reconcile(root, [
    {logicalNodeId:"user-list", logicalType:"egov.listPage", parentLogicalNodeId:null, order:0},
    {logicalNodeId:"user-list/action/create", logicalType:"krds.button", parentLogicalNodeId:"old-parent", order:0},
    {logicalNodeId:"user-list/removed", logicalType:"krds.button", parentLogicalNodeId:"user-list", order:1}
  ]);
  assert.deepEqual(
    changes.map(change => [change.logicalNodeId, change.changeType]),
    [
      ["user-list", "REUSE"],
      ["user-list/action/create", "MOVE"],
      ["user-list/action/delete", "ADD"],
      ["user-list/removed", "ARCHIVE"]
    ]
  );
});

test("R5-T05: MERGE(existing populated)과 REPLACE(existing=[])가 동일한 logicalNodeId 집합을 부여한다", () => {
  const root = validBundle().figmaScreenSpec.content;
  root.children.push(node("user-list/action/delete", "krds.button"));

  const mergeChanges = reconcile(root, [
    {logicalNodeId:"user-list", logicalType:"egov.listPage", parentLogicalNodeId:null, order:0},
    {logicalNodeId:"user-list/action/create", logicalType:"krds.button", parentLogicalNodeId:"user-list", order:0}
  ]);
  const replaceChanges = reconcile(root, []);

  const mergeIds = mergeChanges
    .filter(change => change.changeType !== "ARCHIVE")
    .map(change => change.logicalNodeId)
    .sort();
  const replaceIds = replaceChanges
    .filter(change => change.changeType !== "ARCHIVE")
    .map(change => change.logicalNodeId)
    .sort();

  // 17번 문서 MR-R09가 의존하는 전제: logicalNodeId는 desired 트리(flattenSpec)에서만
  // 파생되고 existing(MERGE의 기존 노드 목록)과 무관하다. REPLACE는 existing=[]이므로
  // 모든 노드가 ADD로 판정되지만, 그 노드들의 logicalNodeId 집합은 MERGE와 완전히 동일해야
  // Manual Refinement Patch(logicalNodeId 키 기준)가 REPLACE 후에도 정확히 같은 노드를
  // 찾아 재적용할 수 있다. 12번 문서 R5-T05(MERGE·REPLACE 결과 비교 검증)를 해소한다.
  assert.deepEqual(replaceIds, mergeIds);
  assert.equal(replaceChanges.every(change => change.changeType === "ADD"), true);
});

test("R5-T04: isUserOverridden은 이전 관리값과 현재값이 다를 때만 true를 반환한다", () => {
  // 서버가 아직 이 속성을 적용한 적 없음(undefined) → 사용자 override 아님, 서버가 정상 적용
  assert.equal(isUserOverridden(undefined, "저장"), false);
  // 서버가 적용한 값과 현재 값이 동일 → 사용자가 안 바꿈, 서버가 재적용해도 무방
  assert.equal(isUserOverridden("저장", "저장"), false);
  // 서버가 적용한 값과 현재 값이 다름 → 사용자가 직접 바꾼 것으로 판단해 보존
  assert.equal(isUserOverridden("저장", "저장하기"), true);
  // boolean 속성(Checkbox 등)에도 동일하게 적용
  assert.equal(isUserOverridden(false, true), true);
  assert.equal(isUserOverridden(true, true), false);
});

test("logical properties map to figma properties and select published variant", () => {
  const properties = mappedProperties(
    {style:"primary", size:"medium", label:"저장", disabled:true},
    registryEntry
  );
  assert.deepEqual(properties, {Style:"Primary", Size:"Medium", Label:"저장", Disabled:true});
  assert.equal(selectVariantName(properties, registryEntry), "Style=Primary, Size=Medium");
});

test("variant selection never falls back to the first published variant", () => {
  assert.equal(selectVariantName({}, registryEntry), null);
  assert.equal(selectVariantName({Style:"Unknown", Size:"Medium"}, registryEntry), null);
});

test("DETAIL v2 bundle is supported", () => {
  const bundle = validBundle();
  bundle.figmaScreenSpec.screenType = "DETAIL";
  bundle.figmaScreenSpec.semanticPattern = "crud.detail";
  const result = validateBundle(bundle);
  assert.equal(result.issues.some(issue => issue.code === "SCREEN_TYPE_UNSUPPORTED"), false);
});

test("R5-014: instance swap property resolves logical value to componentKey via values table", () => {
  const entryWithSwap = {
    ...registryEntry,
    properties: {
      ...registryEntry.properties,
      icon: {figmaProperty: "Icon", type: "INSTANCE_SWAP", values: {search: "ICON_SEARCH_KEY", close: "ICON_CLOSE_KEY"}}
    }
  };
  const properties = mappedProperties({style: "primary", size: "medium", icon: "search"}, entryWithSwap);
  assert.equal(properties.Icon, "ICON_SEARCH_KEY");
});

test("R2 actionType and variant aliases map to Button Label and Style", () => {
  const properties = mappedProperties(
    {actionType:"CREATE", variant:"primary"},
    registryEntry
  );
  assert.equal(properties.Label, "등록");
  assert.equal(properties.Style, "Primary");
});

test("legacy migration preview assigns logical ids and replaces local instances", () => {
  const preview = previewLegacyMigration(validBundle(), [
    {nodeId:"1:1", name:"user-list egov listPage", nodeType:"FRAME", logicalNodeId:null, hasLocalInstance:false},
    {nodeId:"1:2", name:"create 등록 button", nodeType:"FRAME", logicalNodeId:null, hasLocalInstance:true}
  ]);
  assert.equal(preview.canApply, true);
  assert.equal(preview.backupRequired, true);
  assert.deepEqual(
    preview.operations.map(operation => [operation.logicalNodeId, operation.action]),
    [
      ["user-list", "ASSIGN"],
      ["user-list/action/create", "ASSIGN_AND_REPLACE"]
    ]
  );
  assert.equal(preview.operations[1].componentSetKey, "BUTTON_SET_KEY");
});

test("R5-016: optional component missing from registry plans a warning fallback", () => {
  const registry = validBundle().componentRegistry.registry;
  const plan = planFallback(node("user-list/detail/note", "egov.detailField", {label: "비고"}), registry);
  assert.ok(plan);
  assert.match(plan.label, /egov\.detailField/);
  assert.deepEqual(plan.issue, {
    code: "OPTIONAL_COMPONENT_NOT_IN_REGISTRY",
    severity: "WARNING",
    message: "선택 Component가 Registry에 없어 시각적 fallback으로 대체했습니다: egov.detailField",
    logicalNodeId: "user-list/detail/note"
  });
});

test("R5-016: component already in registry needs no fallback", () => {
  const registry = validBundle().componentRegistry.registry;
  assert.equal(planFallback(node("user-list/action/create", "krds.button"), registry), null);
});

test("R5-016: required component missing from registry is not a fallback case (blocked earlier as FATAL)", () => {
  const registry = validBundle().componentRegistry.registry;
  delete registry.components["krds.button"];
  assert.equal(planFallback(node("user-list/action/create", "krds.button"), registry), null);
});

test("R5-016: non-component node types never get a fallback placeholder", () => {
  const registry = validBundle().componentRegistry.registry;
  assert.equal(planFallback(node("user-list/table/row", "egov.dataTable.row", {}, [], "REPEAT"), registry), null);
});

test("R5-033: sticky/fixed position is preserved as name annotation and pluginData", () => {
  const annotation = describeLayoutAnnotations({position: "sticky"});
  assert.equal(annotation.nameSuffix, " [sticky]");
  assert.deepEqual(annotation.pluginData, {position: "sticky"});
});

test("R5-033: overflow other than visible is preserved, but visible is not annotated", () => {
  assert.deepEqual(describeLayoutAnnotations({overflow: "hidden"}).pluginData, {overflow: "hidden"});
  assert.deepEqual(describeLayoutAnnotations({overflow: "visible"}).pluginData, {});
  assert.equal(describeLayoutAnnotations({overflow: "visible"}).nameSuffix, "");
});

test("R5-032: responsive breakpoints are preserved as JSON pluginData and named tag", () => {
  const annotation = describeLayoutAnnotations({
    responsiveBreakpoints: {mobile: {columns: 1}, tablet: {columns: 2}}
  });
  assert.equal(annotation.nameSuffix, " [bp:mobile,tablet]");
  assert.deepEqual(JSON.parse(annotation.pluginData.responsiveBreakpoints), {mobile: {columns: 1}, tablet: {columns: 2}});
});

test("describeLayoutAnnotations combines multiple tags and returns no-op for plain properties", () => {
  const combined = describeLayoutAnnotations({position: "fixed", overflow: "scroll", responsiveBreakpoints: {mobile: {}}});
  assert.equal(combined.nameSuffix, " [fixed][overflow:scroll][bp:mobile]");
  assert.deepEqual(describeLayoutAnnotations({label: "사용자명"}), {nameSuffix: "", pluginData: {}});
});

test("ambiguous legacy migration remains manual review and cannot apply", () => {
  const preview = previewLegacyMigration(validBundle(), [
    {nodeId:"1:1", name:"user-list egov listPage", nodeType:"FRAME", logicalNodeId:null, hasLocalInstance:false},
    {nodeId:"1:2", name:"create 등록 button", nodeType:"FRAME", logicalNodeId:null, hasLocalInstance:true},
    {nodeId:"1:3", name:"create 등록 button copy", nodeType:"FRAME", logicalNodeId:null, hasLocalInstance:true}
  ]);
  assert.equal(preview.canApply, false);
  assert.equal(
    preview.operations.some(operation => operation.action === "MANUAL_REVIEW"),
    true
  );
  assert.equal(
    preview.issues.some(issue => issue.code === "MIGRATION_MAPPING_INCOMPLETE"),
    true
  );
});

// R5-T09: 멀티 스크린 Apply는 전체 Preview 성공 확인 후에만 시작한다(하나라도 실패하면 어떤
// 화면도 건드리지 않는다). 실제 캔버스 mutation(applyMultiScreenBundles)은 Figma Desktop
// 런타임이 필요해 여기서는 그 진입 조건을 결정하는 순수 함수만 검증한다.
test("R5-043/T09: planMultiScreenApply approves only when every screen is APPROVED with no FATAL/ERROR issues", () => {
  const plan = planMultiScreenApply([
    { screenId: "user-list", issues: [], status: "APPROVED" },
    { screenId: "user-detail", issues: [{ code: "X", severity: "WARNING", message: "경고" }], status: "APPROVED" },
  ]);
  assert.equal(plan.canApply, true);
});

test("R5-043/T09: planMultiScreenApply blocks the whole batch when one screen has a FATAL issue", () => {
  const plan = planMultiScreenApply([
    { screenId: "user-list", issues: [], status: "APPROVED" },
    { screenId: "user-detail", issues: [{ code: "X", severity: "FATAL", message: "치명적 오류" }], status: "APPROVED" },
  ]);
  assert.equal(plan.canApply, false);
  assert.equal(plan.blockingScreenId, "user-detail");
});

test("R5-043/T09: planMultiScreenApply blocks the whole batch when one screen is not APPROVED", () => {
  const plan = planMultiScreenApply([
    { screenId: "user-list", issues: [], status: "APPROVED" },
    { screenId: "user-detail", issues: [], status: "REVIEW_REQUIRED" },
  ]);
  assert.equal(plan.canApply, false);
  assert.equal(plan.blockingScreenId, "user-detail");
});

test("R5-043/T09: planMultiScreenApply rejects an empty screen list without touching anything", () => {
  const plan = planMultiScreenApply([]);
  assert.equal(plan.canApply, false);
  assert.equal(plan.blockingScreenId, undefined);
});

// R5-044: FigmaPlatformConversionService.convert()가 계산한 Component Swap 결정을 노드 트리에
// 실제로 반영하는 순수 함수. Java 쪽 결과 shape({requestedLogicalType, resolvedLogicalType, swapped})을 그대로 받는다.
test("R5-044: applyComponentSwaps rewrites logicalType and componentSetKey when a swap rule matches", () => {
  const bundle = validBundle();
  const registry = bundle.componentRegistry.registry;
  registry.components["krds.card-list"] = { ...registryEntry, componentSetKey: "CARD_LIST_SET_KEY" };

  const swapped = applyComponentSwaps(bundle.figmaScreenSpec.content, registry, [
    { requestedLogicalType: "krds.button", resolvedLogicalType: "krds.card-list", swapped: true },
  ]);

  const swappedChild = swapped.children[0];
  assert.equal(swappedChild.componentResolution.logicalType, "krds.card-list");
  assert.equal(swappedChild.componentResolution.componentSetKey, "CARD_LIST_SET_KEY");
  assert.equal(swappedChild.componentResolution.variantKey, "");
});

test("R5-044: applyComponentSwaps leaves the tree untouched when no decision is marked swapped", () => {
  const bundle = validBundle();
  const registry = bundle.componentRegistry.registry;

  const result = applyComponentSwaps(bundle.figmaScreenSpec.content, registry, [
    { requestedLogicalType: "krds.button", resolvedLogicalType: "krds.card-list", swapped: false },
  ]);

  assert.equal(result.children[0].componentResolution.logicalType, "krds.button");
  assert.equal(result.children[0].componentResolution.componentSetKey, "BUTTON_SET_KEY");
});

test("R5-044: applyComponentSwaps keeps the original component when the swap target is missing from the registry", () => {
  const bundle = validBundle();
  const registry = bundle.componentRegistry.registry;

  const result = applyComponentSwaps(bundle.figmaScreenSpec.content, registry, [
    { requestedLogicalType: "krds.button", resolvedLogicalType: "krds.not-published-yet", swapped: true },
  ]);

  assert.equal(result.children[0].componentResolution.logicalType, "krds.button");
  assert.equal(result.children[0].componentResolution.componentSetKey, "BUTTON_SET_KEY");
});
