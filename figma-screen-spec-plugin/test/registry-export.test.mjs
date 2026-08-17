import test from "node:test";
import assert from "node:assert/strict";
import { buildRegistryV3BindingCandidate } from "../dist-test/registry-export.mjs";

test("Author Plugin export emits Binding-only Registry v3 candidate", () => {
  const candidate = buildRegistryV3BindingCandidate({
    profileId: "krds", profileVersion: "2.0.0", registryVersion: "3.0.0",
    catalogVersion: "2.0.0", library: { fileKey: "LIB", name: "KRDS" },
    sourceRevision: "figma:file:rev-1",
    observations: [{ logicalType: "krds.button", componentSetKey: "SET",
      componentName: "Button", variants: { primary: "PRIMARY" }, properties: { Label: "TEXT" } }],
  });

  assert.equal(candidate.schemaVersion, "component-registry-v3");
  assert.deepEqual(candidate.bindings["krds.button"], {
    componentSetKey: "SET", componentName: "Button", publishStatus: "CURRENT",
    lifecycleStatus: "CURRENT", variants: { primary: "PRIMARY" },
  });
  assert.equal("properties" in candidate.bindings["krds.button"], false);
  assert.equal("aliases" in candidate.bindings["krds.button"], false);
});
