import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import {
  describeLayoutAnnotations,
  generationStatus,
  visualRegressionStatus,
  mappedProperties,
  planFallback,
  previewLegacyMigration,
  reconcile,
  runAtomicApply,
  selectVariantName,
  validateBundle
} from "../dist-test/core.mjs";

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
