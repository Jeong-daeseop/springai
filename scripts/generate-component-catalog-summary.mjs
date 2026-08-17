#!/usr/bin/env node
import fs from "node:fs";

const source = process.argv[2] ?? "website-figma-contract/component-catalog-v2.json";
const output = process.argv[3] ?? "docs/figma/Component_Catalog_Summary.generated.md";
const catalog = JSON.parse(fs.readFileSync(source, "utf8"));
const rows = Object.entries(catalog.components ?? {}).map(([logicalType, entry]) => {
  const properties = Object.entries(entry.properties ?? {}).map(([name, property]) =>
    `${name} (${property.type})`).join(", ") || "-";
  return `| ${logicalType} | ${entry.kind} | ${entry.requirement} | ${properties} | ${(entry.composition ?? []).join(", ") || "-"} |`;
}).sort();
const markdown = `<!-- GENERATED FILE. Do not edit manually. Source: ${source} -->\n\n# Component Catalog v2 요약\n\n| logicalType | kind | requirement | properties | composition |\n|---|---|---|---|---|\n${rows.join("\n")}\n`;
fs.mkdirSync(output.substring(0, output.lastIndexOf("/")), { recursive: true });
fs.writeFileSync(output, markdown);
console.log(`generated ${output}: ${rows.length} components`);
