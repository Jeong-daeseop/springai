// Figma Desktop에서 수동 QA할 때 Plugin UI에 올릴 샘플 DesignSystemSpec을 만든다.
// design-system-spec-v1.schema.json(website-figma-contract/)과 정합.
import { writeFileSync, mkdirSync } from "node:fs";

const spec = {
  id: "ftc-krds",
  name: "FTC 정부 포털 Design System",
  version: "1.0.0",
  tokens: [
    { category: "COLOR", name: "color.primary", value: "#0B5FFF" },
    { category: "SPACING", name: "spacing.16", value: "16" },
  ],
  variableCollections: [
    {
      name: "Colors",
      modes: ["Light", "Dark"],
      valuesByMode: {
        "color.primary": { Light: "#0B5FFF", Dark: "#4C8DFF" },
      },
    },
  ],
  components: [
    {
      id: "krds.button",
      name: "KRDS/Button",
      description: "KRDS 주요·보조 업무 액션 버튼",
      developer: {
        codeComponent: "KrdsButton",
        documentationUrl: "https://www.krds.go.kr/",
        packageName: "com.krdevops.ui",
      },
      layout: {
        mode: "HORIZONTAL", paddingX: "16", paddingY: "12", gap: "8", alignment: "CENTER",
        minWidth: "80", maxWidth: "320", minHeight: "40", maxHeight: "56",
      },
      properties: [{ name: "Label", type: "TEXT", defaultValue: "버튼" }],
      variants: { Type: ["Primary", "Secondary"], Size: ["Small", "Medium", "Large"] },
    },
    {
      id: "krds.textField",
      name: "KRDS/TextField",
      layout: { mode: "VERTICAL", paddingX: "12", paddingY: "8", gap: "4", alignment: "CENTER" },
      properties: [{ name: "Label", type: "TEXT", defaultValue: "라벨" }, { name: "Required", type: "BOOLEAN", defaultValue: "false" }],
      variants: { State: ["Default", "Focus", "Error", "Disabled"] },
    },
    {
      id: "krds.select",
      name: "KRDS/Select",
      layout: { mode: "VERTICAL", paddingX: "12", paddingY: "8", gap: "4", alignment: "CENTER" },
      properties: [{ name: "Label", type: "TEXT", defaultValue: "선택" }, { name: "Required", type: "BOOLEAN", defaultValue: "false" }],
      variants: { State: ["Default", "Focus", "Error", "Disabled"] },
    },
    {
      id: "krds.checkbox",
      name: "KRDS/Checkbox",
      layout: { mode: "HORIZONTAL", paddingX: "8", paddingY: "8", gap: "8", alignment: "CENTER" },
      properties: [{ name: "Label", type: "TEXT", defaultValue: "선택" }, { name: "Checked", type: "BOOLEAN", defaultValue: "false" }],
      variants: { State: ["Default", "Focus", "Disabled"] },
    },
    {
      id: "krds.pagination",
      name: "KRDS/Pagination",
      layout: { mode: "HORIZONTAL", paddingX: "8", paddingY: "8", gap: "4", alignment: "CENTER" },
      properties: [],
      variants: { State: ["Default"] },
    },
    {
      id: "egov.listPage",
      name: "eGovFrame/ListPage",
      layout: { mode: "VERTICAL", paddingX: "32", paddingY: "32", gap: "24", alignment: "CENTER" },
      properties: [],
      variants: { Density: ["Comfortable", "Compact"] },
    },
    {
      id: "egov.formPage",
      name: "eGovFrame/FormPage",
      layout: { mode: "VERTICAL", paddingX: "32", paddingY: "32", gap: "24", alignment: "CENTER" },
      properties: [],
      variants: { Density: ["Comfortable", "Compact"] },
    },
  ],
  patterns: [
    { id: "egov.pageHeader", name: "eGovFrame/PageHeader", composedOf: [] },
    { id: "egov.searchPanel", name: "eGovFrame/SearchPanel", composedOf: ["krds.textField", "krds.select", "krds.button"] },
    { id: "egov.dataTable", name: "eGovFrame/DataTable", composedOf: ["krds.checkbox"] },
    { id: "egov.formSection", name: "eGovFrame/FormSection", composedOf: ["krds.textField", "krds.select", "krds.checkbox"] },
    { id: "egov.actionArea", name: "ActionArea", composedOf: ["krds.button"] },
  ],
  issues: [],
};

mkdirSync("dist", { recursive: true });
writeFileSync("dist/sample-design-system-spec.json", JSON.stringify(spec, null, 2));
console.log("dist/sample-design-system-spec.json 생성 완료");
