import fs from "node:fs";
import crypto from "node:crypto";
import assert from "node:assert/strict";
import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";

const here = new URL("../", import.meta.url);
const read = path => JSON.parse(fs.readFileSync(new URL(path, here), "utf8"));
const schemaNames = [
  "figma-common-v1.schema.json",
  "rendered-design-document-v1.schema.json",
  "figpack-v1.schema.json",
  "figma-screen-spec-v1.schema.json",
  "design-system-spec-v1.schema.json",
  "design-system-profile-v1.schema.json",
  "component-registry-v1.schema.json",
  "component-catalog-v1.schema.json",
  "figma-generation-report-v1.schema.json",
  "figma-export-bundle-v1.schema.json",
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
const validateDesignSystemSpec = validator("design-system-spec-v1.schema.json");
const validateProfile = validator("design-system-profile-v1.schema.json");
const validateRegistry = validator("component-registry-v1.schema.json");
const validateCatalog = validator("component-catalog-v1.schema.json");
const validateGenerationReport = validator("figma-generation-report-v1.schema.json");
const validateBundle = validator("figma-export-bundle-v1.schema.json");

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
expectValid(validateDesignSystemSpec, "valid-design-system-spec.json");
expectValid(validateProfile, "valid-design-system-profile.json");
expectValid(validateRegistry, "valid-krds-component-registry.json");
expectValid(validateGenerationReport, "valid-figma-generation-report.json");
expectValid(validateBundle, "valid-figma-export-bundle.json");
expectValid(validateCatalog, "../component-catalog-v1.json");

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
  "egov.pageHeader", "egov.searchPanel", "egov.dataTable", "egov.formSection",
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
  return [...new Set(checks.filter(([left, right]) => left !== right)
    .map(([, , code]) => code))];
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
