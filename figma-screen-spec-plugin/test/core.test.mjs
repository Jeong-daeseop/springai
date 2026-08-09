import test from "node:test";
import assert from "node:assert/strict";
import {
  describeLayoutAnnotations,
  generationStatus,
  mappedProperties,
  planFallback,
  previewLegacyMigration,
  reconcile,
  selectVariantName,
  validateBundle
} from "../dist-test/core.mjs";

test("Desktop fallback is reported as PARTIAL instead of SUCCESS", () => {
  assert.equal(generationStatus(false, 1), "PARTIAL");
  assert.equal(generationStatus(false, 0), "SUCCESS");
  assert.equal(generationStatus(true, 0), "FAILED");
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
    node("user-list/action/create", "krds.button", {style:"primary", size:"medium", label:"등록"})
  ], "PAGE");
  return {
    figmaScreenSpec: {
      screenId:"user-list", screenVersion:1, screenSpecificationId:"users", screenSpecificationVersion:3,
      screenType:"LIST", layoutPattern:"STANDARD", name:"사용자 목록", viewport:"DESKTOP", status:"APPROVED",
      designSystem:{profileId:"ftc-krds", profileVersion:"1.0.0", registryVersion:"registry-1"},
      content, issues:[]
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
      exportedAt:"2026-07-27T00:00:00Z", figmaScreenSpecSchemaVersion:"figma-screen-spec-v1",
      screenSpecificationVersion:3, designSystemProfileVersion:"1.0.0", registryVersion:"registry-1"
    }
  };
}

test("valid published bundle passes validation", () => {
  const result = validateBundle(validBundle());
  assert.ok(result.parsed);
  assert.deepEqual(result.issues, []);
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
