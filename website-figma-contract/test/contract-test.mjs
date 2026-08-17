import fs from "node:fs";
import crypto from "node:crypto";
import assert from "node:assert/strict";
import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";

const here = new URL("../", import.meta.url);
const read = path => JSON.parse(fs.readFileSync(new URL(path, here), "utf8"));
const schemaNames = [
  "figma-common-v1.schema.json",
  "common-contract-v1.schema.json",
  "rendered-design-document-v1.schema.json",
  "figpack-v1.schema.json",
  "figma-screen-spec-v1.schema.json",
  "figma-screen-spec-v2.schema.json",
  "design-system-spec-v1.schema.json",
  "design-system-profile-v1.schema.json",
  "component-registry-v1.schema.json",
  "component-registry-v2.schema.json",
  "component-registry-v3.schema.json",
  "screen-patterns-v1.schema.json",
  "screen-suite-manifest-v1.schema.json",
  "variant-rule-set-v1.schema.json",
  "component-catalog-v1.schema.json",
  "component-catalog-v2.schema.json",
  "figma-generation-report-v1.schema.json",
  "figma-export-bundle-v1.schema.json",
  "figma-export-bundle-v2.schema.json",
  "figma-design-request-v1.schema.json",
  "figma-design-operation-v1.schema.json",
  "legacy-conversion-request-v1.schema.json",
  "legacy-screen-analysis-v1.schema.json",
  "thymeleaf-binding-contract-v1.schema.json",
  "qna-screen-specification-v2.schema.json",
  "qna-screen-specification-v3.schema.json",
  "figma-refinement-patch-set-v1.schema.json",
  "figma-refinement-preview-v1.schema.json",
  "figma-generation-report-v2.schema.json",
];
const schemas = schemaNames.map(read);
const ajv = new Ajv2020({allErrors:true, strict:true});
addFormats(ajv);
for (const schema of schemas) ajv.addSchema(schema);
const validator = fileName => {
  const schema = read(fileName);
  return ajv.getSchema(schema.$id);
};
const validateDocument = validator("rendered-design-document-v1.schema.json");
const validateManifest = validator("figpack-v1.schema.json");
const validateScreen = validator("figma-screen-spec-v1.schema.json");
const validateScreenV2 = validator("figma-screen-spec-v2.schema.json");
const validateDesignSystemSpec = validator("design-system-spec-v1.schema.json");
const validateProfile = validator("design-system-profile-v1.schema.json");
const validateRegistry = validator("component-registry-v1.schema.json");
const validateRegistryV2 = validator("component-registry-v2.schema.json");
const validateRegistryV3 = validator("component-registry-v3.schema.json");
const validatePatterns = validator("screen-patterns-v1.schema.json");
const validateScreenSuite = validator("screen-suite-manifest-v1.schema.json");
const validateVariantRules = validator("variant-rule-set-v1.schema.json");
const validateCatalog = validator("component-catalog-v1.schema.json");
const validateCatalogV2 = validator("component-catalog-v2.schema.json");
const validateGenerationReport = validator("figma-generation-report-v1.schema.json");
const validateGenerationReportV2 = validator("figma-generation-report-v2.schema.json");
const validateRefinementPatchSet = validator("figma-refinement-patch-set-v1.schema.json");
const validateRefinementPreview = validator("figma-refinement-preview-v1.schema.json");
const validateBundle = validator("figma-export-bundle-v1.schema.json");
const validateBundleV2 = validator("figma-export-bundle-v2.schema.json");
const validateDesignRequest = validator("figma-design-request-v1.schema.json");
const validateDesignOperation = validator("figma-design-operation-v1.schema.json");
const validateLegacyConversionRequest = validator("legacy-conversion-request-v1.schema.json");
const validateLegacyScreenAnalysis = validator("legacy-screen-analysis-v1.schema.json");
const validateBindingContract = validator("thymeleaf-binding-contract-v1.schema.json");
const validateQnaBusinessSpec = validator("qna-screen-specification-v2.schema.json");
const validateQnaBusinessSpecV3 = validator("qna-screen-specification-v3.schema.json");

function expectValid(validate, fixture) {
  assert.equal(validate(read(`fixtures/${fixture}`)), true,
    `${fixture}: ${JSON.stringify(validate.errors)}`);
}

function expectInvalid(validate, fixture) {
  assert.equal(validate(read(`fixtures/${fixture}`)), false,
    `${fixture} must be rejected`);
  assert.ok(validate.errors?.length, `${fixture} must return a precise schema error`);
}

expectValid(validateDocument, "valid-minimal.json");
for (const name of ["invalid-enum.json", "invalid-source-content-hash.json"]) {
  expectInvalid(validateDocument, name);
}
const invalidReference = read("fixtures/invalid-reference.json");
assert.equal(validateDocument(invalidReference), true,
  "reference integrity is a domain validation after JSON Schema");
assert.equal(
  invalidReference.nodes.some(node =>
    node.children.some(id => !invalidReference.nodes.some(candidate => candidate.id === id))),
  true,
  "invalid-reference fixture must violate domain references",
);
expectValid(validateManifest, "valid-figpack-manifest.json");
expectInvalid(validateManifest, "invalid-figpack-path.json");

expectValid(validateScreen, "valid-figma-screen-spec.json");
expectValid(validateScreen, "valid-boundary-figma-screen-spec.json");
expectInvalid(validateScreen, "invalid-figma-screen-id.json");
expectInvalid(validateScreen, "invalid-screen-version.json");
expectInvalid(validateScreen, "invalid-logical-node-id.json");
expectValid(validateScreenV2, "valid-figma-screen-spec-v2.json");
const qnaBusinessSpec = read("fixtures/qna/qna-screen-specification-v2.json");
assert.equal(validateQnaBusinessSpec(qnaBusinessSpec), true,
  `Q&A 업무 ScreenSpecification: ${JSON.stringify(validateQnaBusinessSpec.errors)}`);
assert.equal(new Set(qnaBusinessSpec.pages.map(page => page.id)).size, 6,
  "Q&A 업무 ScreenSpecification Page ID는 정확히 6개이며 중복될 수 없습니다.");
const qnaBusinessSpecV3 = read("fixtures/qna/qna-screen-specification-v3.json");
assert.equal(validateQnaBusinessSpecV3(qnaBusinessSpecV3), true,
  `Q&A v3 업무 ScreenSpecification: ${JSON.stringify(validateQnaBusinessSpecV3.errors)}`);
assert.equal(qnaBusinessSpecV3.version, 3, "Q&A v3 ScreenSpecification 버전은 3이어야 한다");
assert.equal(qnaBusinessSpecV3.pages.length, 7, "Q&A v3 ScreenSpecification은 7개 화면이어야 한다");
assert.equal(qnaBusinessSpecV3.pages.some(page => page.id === "qna-update"), true,
  "Q&A v3 ScreenSpecification에 qna-update가 필요하다");
expectValid(validateDesignSystemSpec, "valid-design-system-spec.json");
expectValid(validateProfile, "valid-design-system-profile.json");
expectValid(validateRegistry, "valid-krds-component-registry.json");
expectValid(validateRegistryV2, "valid-component-registry-v2.json");
expectInvalid(validateRegistryV2, "invalid-component-registry-v2-no-role.json");
expectValid(validateRegistryV3, "valid-component-registry-v3.json");
assert.equal(validatePatterns(read("screen-patterns-v1.json")), true,
  `screen-patterns-v1.json: ${JSON.stringify(validatePatterns.errors)}`);
assert.equal(validateVariantRules(read("variant-rule-set-krds-v1.json")), true,
  `variant-rule-set-krds-v1.json: ${JSON.stringify(validateVariantRules.errors)}`);
expectValid(validateScreenSuite, "qna/qna-screen-suite-v1.json");
const qnaSuite = read("fixtures/qna/qna-screen-suite-v1.json");
assert.equal(qnaSuite.screens.length, 6, "Q&A 회귀 Suite는 정확히 6개 화면이어야 한다");
assert.equal(new Set(qnaSuite.screens.map(screen => screen.screenId)).size, 6,
  "Q&A 회귀 Suite의 Screen ID는 중복될 수 없다");
assert.equal(validateRegistryV2(read("fixtures/qna/krds-component-registry-v2.json")), true,
  `Q&A KRDS registry: ${JSON.stringify(validateRegistryV2.errors)}`);
assert.equal(validateRegistryV2(read("fixtures/qna/krds-component-registry-v2.2.0-candidate.json")), true,
  `Q&A KRDS registry 2.2.0 candidate: ${JSON.stringify(validateRegistryV2.errors)}`);
assert.equal(read("fixtures/qna/krds-component-registry-v2.2.0-candidate.json").registryVersion, "2.2.0",
  "Q&A KRDS registry 후보 버전은 2.2.0이어야 한다");
const qnaVariantRules = read("fixtures/qna/variant-rule-set-krds-v2-candidate.json");
assert.equal(validateVariantRules(qnaVariantRules), true,
  `Q&A KRDS variant rules: ${JSON.stringify(validateVariantRules.errors)}`);
assert.deepEqual(
  qnaVariantRules.rules.filter(rule => rule.role === "search.panel"),
  [{
    ruleId: "search-panel-default", priority: 100, role: "search.panel",
    when: {pattern: "crud.list", platform: "DESKTOP"},
    result: {type: "simple", size: "Medium", state: "Default"},
  }],
  "search.panel은 정확히 하나의 결정형 Published Variant Rule을 가져야 한다",
);
const qnaV2ScreenFiles = [
  "qna-list.json", "qna-create.json", "qna-detail.json",
  "qna-answer-list.json", "qna-answer-detail.json", "qna-answer-create.json",
];
const qnaV2Screens = qnaV2ScreenFiles.map(name =>
  read(`fixtures/qna/v2/${name}`));
for (const [index, screen] of qnaV2Screens.entries()) {
  assert.equal(validateScreenV2(screen), true,
    `${qnaV2ScreenFiles[index]}: ${JSON.stringify(validateScreenV2.errors)}`);
  const nodes = collectNodes(screen.content);
  assert.equal(nodes.some(node => node.properties?.semanticRole === "search.region"), false,
    `${qnaV2ScreenFiles[index]}: 비표준 search.region Role을 사용할 수 없다`);
  for (const searchPanel of nodes.filter(node => node.properties?.semanticRole === "search.panel")) {
    assert.equal(searchPanel.nodeType, "COMPONENT",
      `${qnaV2ScreenFiles[index]}: search.panel은 COMPONENT여야 한다`);
    assert.equal(searchPanel.type, "krds.searchPanel",
      `${qnaV2ScreenFiles[index]}: search.panel은 Published krds.searchPanel을 사용해야 한다`);
    assert.equal(searchPanel.children.length, 0,
      `${qnaV2ScreenFiles[index]}: Published SearchPanel 내부를 개별 Field/Button으로 재구성할 수 없다`);
    assert.equal(searchPanel.componentResolution?.ruleId, "search-panel-default",
      `${qnaV2ScreenFiles[index]}: SearchPanel Variant Rule이 적용되어야 한다`);
  }
}
assert.equal(new Set(qnaV2Screens.map(screen => screen.screenId)).size, 6,
  "Q&A v2 Screen Spec은 정확히 6개의 고유 화면이어야 한다");
assert.equal(qnaV2Screens.every(screen => screen.status === "REVIEW_REQUIRED"), true,
  "사람의 승인 전 Q&A v2 Screen Spec 상태는 REVIEW_REQUIRED여야 한다");
for (const expected of qnaSuite.screens) {
  const screen = qnaV2Screens.find(candidate => candidate.screenId === expected.screenId);
  assert.ok(screen, `Q&A v2 Screen Spec 누락: ${expected.screenId}`);
  const actualRoles = collectSemanticRoles(screen.content);
  for (const requiredRole of expected.requiredRoles) {
    assert.equal(actualRoles.has(requiredRole), true,
      `${expected.screenId}: 필수 Role 누락 ${requiredRole}`);
  }
}
expectValid(validateGenerationReport, "valid-figma-generation-report.json");
expectValid(validateGenerationReportV2, "valid-figma-generation-report-v2-with-refinement.json");
expectValid(validateGenerationReportV2, "valid-figma-generation-report-v2-without-refinement.json");
expectValid(validateRefinementPatchSet, "valid-figma-refinement-patch-set.json");
expectInvalid(validateRefinementPatchSet, "invalid-figma-refinement-patch-set-unknown-status.json");
expectInvalid(validateRefinementPatchSet, "invalid-figma-refinement-patch-missing-owner.json");
expectValid(validateRefinementPreview, "valid-figma-refinement-preview.json");
expectInvalid(validateRefinementPreview, "invalid-figma-refinement-preview-missing-reason.json");
expectValid(validateBundle, "valid-figma-export-bundle.json");
const screenV2 = read("fixtures/valid-figma-screen-spec-v2.json");
const registryV2 = read("fixtures/valid-component-registry-v2.json");
const bundleV2 = {
  figmaScreenSpec: screenV2,
  designSystemProfile: {
    profile: {
      id:"krds", name:"KRDS", version:"2.0.0", registryVersion:"2.0.0",
      libraryFileKey:"KRDS_LIBRARY_FILE_KEY", status:"PUBLISHED", components:{}, variables:{}
    },
    snapshotAt:"2026-08-11T00:00:00Z"
  },
  componentRegistry: {registry:registryV2, snapshotAt:"2026-08-11T00:00:00Z"},
  screenPattern: {
    pattern: {pattern:"crud.list", version:"1.0.0", slots:[]},
    snapshotAt:"2026-08-11T00:00:00Z"
  },
  variantRuleSet: {
    ruleSet: {
      id:"krds-v2", version:"1.0.0", profileId:"krds", registryVersion:"2.0.0",
      status:"PUBLISHED", rules:[{
        ruleId:"list-primary", priority:100, role:"action.primary",
        when:{pattern:"crud.list"}, result:{type:"Primary"}
      }]
    },
    snapshotAt:"2026-08-11T00:00:00Z"
  },
  metadata: {
    exportedAt:"2026-08-11T00:00:00Z", figmaScreenSpecSchemaVersion:"figma-screen-spec-v2",
    screenSpecificationVersion:1, designSystemProfileVersion:"2.0.0", registryVersion:"2.0.0",
    screenPatternVersion:"1.0.0", variantRuleSetVersion:"1.0.0", componentContractVersion:"2.0.0"
  }
};
assert.equal(validateBundleV2(bundleV2), true,
  `v2 bundle: ${JSON.stringify(validateBundleV2.errors)}`);
assert.deepEqual(bundleConsistencyIssues(bundleV2), []);
const mismatchedSnapshotBundle = structuredClone(bundleV2);
mismatchedSnapshotBundle.screenPattern.pattern.version = "9.9.9";
mismatchedSnapshotBundle.variantRuleSet.ruleSet.registryVersion = "registry-other";
assert.deepEqual(bundleConsistencyIssues(mismatchedSnapshotBundle), [
  "SCREEN_PATTERN_SNAPSHOT_VERSION_MISMATCH",
  "RULE_SET_REGISTRY_VERSION_MISMATCH",
]);

for (const name of [
  "valid-figma-design-request-text.json",
  "valid-figma-design-request-reference.json",
  "valid-figma-design-request-modify.json",
  "valid-figma-design-request-image.json",
  "valid-figma-design-request-multi-screen.json",
  "valid-figma-design-request-with-components.json",
  "valid-figma-design-request-convert-platform.json",
]) {
  expectValid(validateDesignRequest, name);
}
for (const name of [
  "invalid-figma-design-request-empty-screens.json",
  "invalid-figma-design-request-missing-snapshot.json",
]) {
  expectInvalid(validateDesignRequest, name);
}

expectValid(validateDesignOperation, "valid-figma-design-operation-analyzed.json");
expectValid(validateDesignOperation, "valid-figma-design-operation-applied.json");
for (const name of [
  "invalid-figma-design-operation-bad-status.json",
  "invalid-figma-design-operation-missing-request.json",
]) {
  expectInvalid(validateDesignOperation, name);
}

expectValid(validateLegacyConversionRequest, "valid-legacy-conversion-request.json");
expectInvalid(validateLegacyConversionRequest, "invalid-legacy-conversion-request-missing-vo-path.json");

expectValid(validateLegacyScreenAnalysis, "valid-legacy-screen-analysis.json");
expectInvalid(validateLegacyScreenAnalysis, "invalid-legacy-screen-analysis-bad-role.json");

expectValid(validateBindingContract, "valid-thymeleaf-binding-contract.json");
expectInvalid(validateBindingContract, "invalid-thymeleaf-binding-contract-bad-confidence.json");
expectValid(validateCatalog, "../component-catalog-v1.json");
assert.equal(validateCatalogV2(read("component-catalog-v2.json")), true,
  `component-catalog-v2.json: ${JSON.stringify(validateCatalogV2.errors)}`);
const catalogV2 = read("component-catalog-v2.json");
const registryV3 = read("fixtures/valid-component-registry-v3.json");
assert.deepEqual(catalogV2Issues(catalogV2), []);
assert.deepEqual(registryV3Issues(catalogV2, registryV3), []);
for (const binding of Object.values(registryV3.bindings)) {
  assert.deepEqual(Object.keys(binding).sort(),
    ["componentName", "componentSetKey", "lifecycleStatus", "publishStatus", "variants"].sort(),
    "Registry v3 Binding은 Catalog 논리 계약 필드를 중복 저장하지 않아야 한다");
}
const aliasConflictCatalogV2 = structuredClone(catalogV2);
aliasConflictCatalogV2.components["krds.checkbox"].aliases.push("button");
assert.deepEqual(catalogV2Issues(aliasConflictCatalogV2),
  ["ALIAS_CONFLICT:krds.checkbox:button"]);
const compositionCycleCatalogV2 = structuredClone(catalogV2);
compositionCycleCatalogV2.components["krds.button"].composition.push("egov.actionArea");
assert.deepEqual(catalogV2Issues(compositionCycleCatalogV2),
  ["COMPOSITION_CYCLE:krds.button"]);
assert.deepEqual(
  registryV3Issues(catalogV2, read("fixtures/invalid-component-registry-v3-unknown-binding.json")),
  ["REQUIRED_BINDING_MISSING:krds.button", "REQUIRED_BINDING_MISSING:krds.checkbox",
    "REQUIRED_BINDING_MISSING:krds.pageHeader",
    "REQUIRED_BINDING_MISSING:krds.pagination", "REQUIRED_BINDING_MISSING:krds.searchPanel",
    "REQUIRED_BINDING_MISSING:krds.select", "REQUIRED_BINDING_MISSING:krds.tableCell",
    "REQUIRED_BINDING_MISSING:krds.tableHeader", "REQUIRED_BINDING_MISSING:krds.textField",
    "UNKNOWN_LOGICAL_TYPE:krds.unknown"]);

const validBundle = read("fixtures/valid-figma-export-bundle.json");
assert.deepEqual(bundleConsistencyIssues(validBundle), []);
const mismatchedBundle = read("fixtures/invalid-bundle-version-mismatch.json");
assert.equal(validateBundle(mismatchedBundle), true,
  "cross-document version equality is a domain validation");
assert.deepEqual(bundleConsistencyIssues(mismatchedBundle), [
  "SCREEN_REGISTRY_VERSION_MISMATCH",
]);

const catalog = read("component-catalog-v1.json");
assert.deepEqual(catalogIssues(catalog), []);
const unknownRequired = read("fixtures/invalid-unknown-required-component.json");
assert.equal(validateScreen(unknownRequired), true,
  "logical component membership is validated against component-catalog-v1");
assert.deepEqual(screenCatalogIssues(unknownRequired, catalog), [
  "UNKNOWN_LOGICAL_COMPONENT:egov.unknownRequired",
]);

const requiredLogicalTypes = new Set(
  catalog.requiredComponents.map(component => component.logicalType));
for (const required of [
  "krds.button", "krds.textField", "krds.select", "krds.checkbox", "krds.pagination",
  "krds.searchPanel", "egov.pageHeader", "egov.dataTable", "egov.formSection",
  "egov.actionArea", "egov.listPage", "egov.formPage",
]) {
  assert.equal(requiredLogicalTypes.has(required), true,
    `required component catalog is missing ${required}`);
}

const schemaBytes = fs.readFileSync(
  new URL("rendered-design-document-v1.schema.json", here));
const checksum = crypto.createHash("sha256").update(schemaBytes).digest("hex");
assert.match(checksum, /^[a-f0-9]{64}$/);
console.log(
  `contract OK: schemas=${schemas.length}, schemaSha256=${checksum}`);

function bundleConsistencyIssues(bundle) {
  const screen = bundle.figmaScreenSpec;
  const profile = bundle.designSystemProfile.profile;
  const registry = bundle.componentRegistry.registry;
  const metadata = bundle.metadata;
  const checks = [
    [screen.designSystem.profileId, profile.id, "SCREEN_PROFILE_ID_MISMATCH"],
    [screen.designSystem.profileId, registry.profileId, "REGISTRY_PROFILE_ID_MISMATCH"],
    [screen.designSystem.profileVersion, profile.version, "SCREEN_PROFILE_VERSION_MISMATCH"],
    [screen.designSystem.profileVersion, registry.profileVersion, "REGISTRY_PROFILE_VERSION_MISMATCH"],
    [screen.designSystem.registryVersion, profile.registryVersion, "SCREEN_REGISTRY_VERSION_MISMATCH"],
    [screen.designSystem.registryVersion, registry.registryVersion, "SCREEN_REGISTRY_VERSION_MISMATCH"],
    [screen.screenSpecificationVersion, metadata.screenSpecificationVersion,
      "SCREEN_SPECIFICATION_VERSION_MISMATCH"],
    [profile.version, metadata.designSystemProfileVersion,
      "METADATA_PROFILE_VERSION_MISMATCH"],
    [registry.registryVersion, metadata.registryVersion,
      "METADATA_REGISTRY_VERSION_MISMATCH"],
  ];
  if (bundle.screenPattern?.pattern) {
    checks.push(
      [screen.semanticPattern, bundle.screenPattern.pattern.pattern, "SCREEN_PATTERN_SNAPSHOT_MISMATCH"],
      [screen.screenPatternVersion, bundle.screenPattern.pattern.version,
        "SCREEN_PATTERN_SNAPSHOT_VERSION_MISMATCH"]);
  }
  if (bundle.variantRuleSet?.ruleSet) {
    checks.push(
      [screen.variantRuleSetVersion, bundle.variantRuleSet.ruleSet.version,
        "RULE_SET_SNAPSHOT_VERSION_MISMATCH"],
      [screen.designSystem.profileId, bundle.variantRuleSet.ruleSet.profileId,
        "RULE_SET_PROFILE_ID_MISMATCH"],
      [screen.designSystem.registryVersion, bundle.variantRuleSet.ruleSet.registryVersion,
        "RULE_SET_REGISTRY_VERSION_MISMATCH"]);
  }
  return [...new Set(checks.filter(([left, right]) => left !== right)
    .map(([, , code]) => code))];
}

function collectSemanticRoles(node, roles = new Set()) {
  if (typeof node.properties?.semanticRole === "string") {
    roles.add(node.properties.semanticRole);
  }
  for (const child of node.children ?? []) collectSemanticRoles(child, roles);
  return roles;
}

function collectNodes(node, nodes = []) {
  nodes.push(node);
  for (const child of node.children ?? []) collectNodes(child, nodes);
  return nodes;
}

function catalogIssues(value) {
  const groups = [
    [...value.requiredComponents, ...value.optionalComponents],
    value.patterns,
    value.pageTemplates,
  ];
  const issues = [];
  for (const entries of groups) {
    const seen = new Set();
    for (const entry of entries) {
      if (seen.has(entry.logicalType)) {
        issues.push(`DUPLICATE_LOGICAL_TYPE:${entry.logicalType}`);
      }
      seen.add(entry.logicalType);
    }
  }
  const entries = groups.flat();
  const known = new Set(entries.map(entry => entry.logicalType));
  for (const entry of entries) {
    if (entry.replacement && !known.has(entry.replacement)) {
      issues.push(`UNKNOWN_REPLACEMENT:${entry.logicalType}:${entry.replacement}`);
    }
  }
  return issues;
}

function catalogV2Issues(value) {
  const issues = [];
  const entries = value.components;
  const aliasOwners = new Map();
  for (const [logicalType, entry] of Object.entries(entries)) {
    for (const alias of entry.aliases) {
      const owner = aliasOwners.get(alias);
      if (entries[alias] || owner) issues.push(`ALIAS_CONFLICT:${logicalType}:${alias}`);
      aliasOwners.set(alias, logicalType);
    }
    if (entry.replacementLogicalType && !entries[entry.replacementLogicalType]) {
      issues.push(`UNKNOWN_REPLACEMENT:${logicalType}:${entry.replacementLogicalType}`);
    }
    for (const target of entry.composition) {
      if (!entries[target]) issues.push(`COMPOSITION_TARGET_MISSING:${logicalType}:${target}`);
    }
    for (const required of entry.requiredProperties) {
      if (!entry.properties[required]) issues.push(`REQUIRED_PROPERTY_MISSING:${logicalType}:${required}`);
    }
  }
  const visiting = new Set();
  const visited = new Set();
  const visit = logicalType => {
    if (visiting.has(logicalType)) {
      issues.push(`COMPOSITION_CYCLE:${logicalType}`);
      return;
    }
    if (visited.has(logicalType)) return;
    visiting.add(logicalType);
    for (const target of entries[logicalType]?.composition ?? []) visit(target);
    visiting.delete(logicalType);
    visited.add(logicalType);
  };
  Object.keys(entries).forEach(visit);
  return [...new Set(issues)].sort();
}

function registryV3Issues(catalogValue, registry) {
  const issues = [];
  const bindings = registry.bindings;
  for (const logicalType of Object.keys(bindings)) {
    if (!catalogValue.components[logicalType]) issues.push(`UNKNOWN_LOGICAL_TYPE:${logicalType}`);
  }
  for (const [logicalType, entry] of Object.entries(catalogValue.components)) {
    if (entry.kind === "COMPONENT" && entry.requirement === "REQUIRED" && !bindings[logicalType]) {
      issues.push(`REQUIRED_BINDING_MISSING:${logicalType}`);
    }
  }
  return issues.sort();
}

function screenCatalogIssues(screen, catalogValue) {
  const known = new Set([
    ...catalogValue.requiredComponents,
    ...catalogValue.optionalComponents,
    ...catalogValue.patterns,
    ...catalogValue.pageTemplates,
  ].map(entry => entry.logicalType));
  const issues = [];
  const visit = node => {
    if ((node.type.startsWith("krds.") || node.type.startsWith("egov."))
        && !known.has(node.type)) {
      issues.push(`UNKNOWN_LOGICAL_COMPONENT:${node.type}`);
    }
    node.children.forEach(visit);
  };
  visit(screen.content);
  return issues;
}
