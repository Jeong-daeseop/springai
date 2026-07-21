import fs from "node:fs";
import path from "node:path";
import { unzipSync, zipSync, strFromU8, strToU8 } from "fflate";
import { sha256 } from "@noble/hashes/sha256";
import { bytesToHex } from "@noble/hashes/utils";

const input = path.resolve(process.argv[2] ?? "../build/figma-e2e/list.figpack");
const output = path.resolve(process.argv[3] ?? "../build/figma-e2e/cleanup-failure.figpack");
const files = unzipSync(new Uint8Array(fs.readFileSync(input)));
const document = JSON.parse(strFromU8(files["document.json"]));
const manifest = JSON.parse(strFromU8(files["manifest.json"]));

document.page.title = "정리 실패 검증";
const svgAsset = document.assets.find(asset => asset.mimeType === "image/svg+xml");
files[svgAsset.path] = strToU8("<svg><path></svg");
svgAsset.byteLength = files[svgAsset.path].length;
svgAsset.contentHash = hashBytes(files[svgAsset.path]);
const hashInput = structuredClone(document);
delete hashInput.contentHash;
delete hashInput.captureId;
delete hashInput.source.capturedAt;
document.contentHash = hashJson(hashInput);
manifest.contentHash = document.contentHash;

files["document.json"] = strToU8(JSON.stringify(document));
const documentEntry = manifest.entries.find(entry => entry.path === "document.json");
documentEntry.byteLength = files["document.json"].length;
documentEntry.sha256 = hashBytes(files["document.json"]);
const svgEntry = manifest.entries.find(entry => entry.path === svgAsset.path);
svgEntry.byteLength = files[svgAsset.path].length;
svgEntry.sha256 = hashBytes(files[svgAsset.path]);
files["manifest.json"] = strToU8(JSON.stringify(manifest));

fs.mkdirSync(path.dirname(output), {recursive:true});
fs.writeFileSync(output, zipSync(files, {level:6}));
console.log(`cleanup failure fixture: ${output}`);

function hashJson(value) {
  return hashBytes(strToU8(JSON.stringify(canonical(value))));
}

function hashBytes(value) {
  return bytesToHex(sha256(value));
}

function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value).sort(([left],[right]) => left.localeCompare(right))
      .map(([key,item]) => [key,canonical(item)]));
  }
  return value;
}
