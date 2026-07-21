import fs from "node:fs";
import crypto from "node:crypto";
import assert from "node:assert/strict";
import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";

const here = new URL("../", import.meta.url);
const read = path => JSON.parse(fs.readFileSync(new URL(path, here), "utf8"));
const ajv = new Ajv2020({allErrors:true,strict:true}); addFormats(ajv);
const validate = ajv.compile(read("rendered-design-document-v1.schema.json"));
const validateManifest = ajv.compile(read("figpack-v1.schema.json"));
assert.equal(validate(read("fixtures/valid-minimal.json")), true, JSON.stringify(validate.errors));
for (const name of ["invalid-enum.json", "invalid-source-content-hash.json"]) {
  assert.equal(validate(read(`fixtures/${name}`)), false, `${name} must be rejected`);
}
const invalidReference=read("fixtures/invalid-reference.json");
assert.equal(validate(invalidReference), true, "reference integrity is a domain validation after JSON Schema");
assert.equal(invalidReference.nodes.some(node=>node.children.some(id=>!invalidReference.nodes.some(candidate=>candidate.id===id))),true,"invalid-reference fixture must violate domain references");
assert.equal(validateManifest(read("fixtures/valid-figpack-manifest.json")),true,JSON.stringify(validateManifest.errors));
assert.equal(validateManifest(read("fixtures/invalid-figpack-path.json")),false,"path traversal manifest must be rejected");
const schemaBytes=fs.readFileSync(new URL("rendered-design-document-v1.schema.json",here));
const checksum=crypto.createHash("sha256").update(schemaBytes).digest("hex");
assert.match(checksum,/^[a-f0-9]{64}$/);
console.log(`contract OK: schemaSha256=${checksum}`);
