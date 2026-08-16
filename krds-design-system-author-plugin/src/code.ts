import { sha256 } from "@noble/hashes/sha256";
import { bytesToHex } from "@noble/hashes/utils";
import {
  compareSnapshots,
  componentSnapshot,
  figmaVariableName,
  normalizePluginError,
  planComponentChange,
  transitionReviewStatus,
  utf8Bytes,
  validateSpec,
} from "./core";
import type {
  Comparison,
  ComponentDefinition,
  ComponentLayout,
  ComponentProperty,
  DesignSystemSpec,
  DiffKind,
  PatternDefinition,
  PropertyType,
  ReviewEvent,
  ReviewStatus,
  TokenCategory,
  ValidationIssue,
} from "./core";

// ── DesignSystemSpec 타입 (website-figma-contract/design-system-spec-v1.schema.json과 동기화) ──

type DiffEntry = {
  logicalId: string;
  name: string;
  kind: DiffKind;
  detail: string;
  comparisons: Comparison[];
};
type RegistryPublishStatus = "UNPUBLISHED" | "CURRENT" | "CHANGED";
type RegistryExportOptions = { fileKey: string; libraryName: string; registryVersion: string };
type ComponentRegistryExport = {
  profileId: string;
  profileVersion: string;
  registryVersion: string;
  library: { fileKey: string; name: string };
  components: Record<string, {
    componentSetKey: string;
    componentName: string;
    publishStatus: RegistryPublishStatus;
    lifecycleStatus: "ACTIVE" | "DEPRECATED";
    replacementLogicalType: string | null;
    aliases: string[];
    variants: Record<string, string>;
    properties: Record<string, {
      figmaProperty: string;
      type: PropertyType;
      values: Record<string, string>;
    }>;
  }>;
  variables: Record<string, {
    variableKey: string;
    variableName: string;
    collectionKey: string;
    collectionName: string;
    resolvedType: string;
    publishStatus: RegistryPublishStatus;
  }>;
};
type RegistryV2 = {
  profileId: string;
  profileVersion: string;
  registryVersion: string;
  components: Record<string, {
    componentSetKey: string;
    componentName?: string;
    roles?: string[];
    variants: Record<string, string>;
    properties: Record<string, { figmaProperty: string; type: string; values?: Record<string, string> }>;
    variantAxes: Record<string, { logicalName: string; figmaProperty: string; allowedValues: string[]; required: boolean }>;
    [key: string]: unknown;
  }>;
  [key: string]: unknown;
};

const PLUGIN_DATA_NS_ID = "designSystemId";
const PLUGIN_DATA_LOGICAL_ID = "logicalId";
const PLUGIN_DATA_CONTENT_HASH = "contentHash";
const PLUGIN_DATA_DEFINITION = "definitionSnapshot";
const PLUGIN_DATA_REVIEW = "reviewState";

figma.showUI(__html__, { width: 420, height: 680 });

// ── 결정론적 contentHash: 업데이트 필요 여부 판정에 사용 ──

async function contentHash(value: unknown): Promise<string> {
  const json = JSON.stringify(value, Object.keys(value as object).sort());
  return bytesToHex(sha256(utf8Bytes(json)));
}

// ── R3-017: designSystemId+logicalId로 태깅된 기존 노드 탐색 ──

function findTaggedComponentSets(designSystemId: string): Map<string, ComponentSetNode> {
  const result = new Map<string, ComponentSetNode>();
  for (const node of figma.root.findAll(n => n.type === "COMPONENT_SET")) {
    const set = node as ComponentSetNode;
    if (set.getPluginData(PLUGIN_DATA_NS_ID) === designSystemId) {
      const logicalId = set.getPluginData(PLUGIN_DATA_LOGICAL_ID);
      if (logicalId) result.set(logicalId, set);
    }
  }
  return result;
}

function normalizedVariantName(properties: Record<string, string>): string {
  return Object.entries(properties).map(([name, value]) => `${name}=${value}`).join(", ");
}

function variantPropertiesFromName(component: ComponentNode): Record<string, string> {
  let properties: Record<string, string> = {};
  try {
    properties = { ...(component.variantProperties ?? {}) } as Record<string, string>;
  } catch {
    properties = {};
  }
  if (Object.keys(properties).length > 0) return properties;
  for (const part of component.name.split(",")) {
    const separator = part.indexOf("=");
    if (separator > 0) properties[part.slice(0, separator).trim()] = part.slice(separator + 1).trim();
  }
  return properties;
}

function normalizeFocusVariantNames(set: ComponentSetNode, ensureDefaultState = false): void {
  const names = new Set(set.children.filter(child => child.type === "COMPONENT").map(child => child.name.toLowerCase()));
  for (const child of set.children) {
    if (child.type !== "COMPONENT") continue;
    let variantProperties: Record<string, string> = {};
    try { variantProperties = { ...(child.variantProperties ?? {}) } as Record<string, string>; } catch { variantProperties = {}; }
    const stateEntry = Object.entries(variantProperties).find(([name, value]) =>
      name.toLowerCase() === "state" && value.toLowerCase() === "focused");
    let nextName = stateEntry
      ? normalizedVariantName({ ...variantProperties, [stateEntry[0]]: "focus" })
      : child.name.replace(/(State=)focused\b/gi, "$1focus");
    if (ensureDefaultState && !/(?:^|,\s*)State\s*=/i.test(nextName)) {
      nextName = `${nextName}, State=default`;
    }
    if (nextName === child.name) continue;
    if (names.has(nextName.toLowerCase())) {
      child.remove();
      continue;
    }
    child.name = nextName;
    names.add(nextName.toLowerCase());
  }
}

function ensureVariantProperty(set: ComponentSetNode, name: string, defaultValue: string): boolean {
  let definitions: ComponentPropertyDefinitions = {};
  try {
    definitions = set.componentPropertyDefinitions ?? {};
  } catch {
    // Figma는 기존 Component Set 오류가 있는 동안 Property API를 거부한다.
    // 이 경우 원본을 더 손상시키지 않고 Inventory 수집 단계로 진행한다.
    return false;
  }
  if (Object.keys(definitions).some(propertyName => propertyName.toLowerCase() === name.toLowerCase())) {
    return false;
  }
  set.addComponentProperty(name, "VARIANT", defaultValue);
  return true;
}

function rebuildCheckboxComponentSet(set: ComponentSetNode, logicalId: string): ComponentSetNode {
  const page = set.parent?.type === "PAGE" ? set.parent as PageNode : figma.currentPage;
  const clones = set.children
    .filter(child => child.type === "COMPONENT")
    .map(child => (child as ComponentNode).clone());
  if (clones.length === 0) throw new Error("Checkbox Component Set에 재구성할 Variant가 없습니다.");
  for (const clone of clones) {
    page.appendChild(clone);
    const props = variantPropertiesFromName(clone);
    const state = Object.entries(props).find(([key]) => key.toLowerCase() === "state")?.[1] || "default";
    const check = /checked/i.test(state) ? "on" : "off";
    clone.name = normalizedVariantName({ State: state.toLowerCase(), Size: "medium", Check: check });
  }
  const oldName = set.name;
  const owner = set.getPluginData(PLUGIN_DATA_NS_ID);
  set.remove();
  const rebuilt = figma.combineAsVariants(clones, page);
  rebuilt.name = oldName;
  if (owner) rebuilt.setPluginData(PLUGIN_DATA_NS_ID, owner);
  rebuilt.setPluginData(PLUGIN_DATA_LOGICAL_ID, logicalId);
  return rebuilt;
}

function repairSelectedCheckboxComponentSet(): { created: number; key: string } {
  const selected = figma.currentPage.selection[0];
  const set = selected?.type === "COMPONENT_SET"
    ? selected
    : selected?.parent?.type === "COMPONENT_SET"
      ? selected.parent
      : undefined;
  if (!set || set.type !== "COMPONENT_SET") {
    throw new Error("Checkbox Component Set 또는 그 Variant를 먼저 선택하세요.");
  }
  const page = set.parent?.type === "PAGE" ? set.parent as PageNode : figma.currentPage;
  const sourceByKey = new Map<string, ComponentNode>();
  for (const child of set.children) {
    if (child.type !== "COMPONENT") continue;
    const props = variantPropertiesFromName(child);
    const rawState = Object.entries(props).find(([name]) => name.toLowerCase() === "state")?.[1]?.toLowerCase() ?? "default";
    const rawCheck = Object.entries(props).find(([name]) => name.toLowerCase() === "check")?.[1]?.toLowerCase() ?? "off";
    if (rawCheck === "indeterminate") continue;
    const state = rawState === "default" ? (rawCheck === "on" ? "checked" : "unchecked") : rawState;
    if (!["unchecked", "checked", "focus", "disabled", "error", "readonly"].includes(state)) continue;
    const check = state === "unchecked" || state === "checked" ? "on" : (rawCheck === "on" ? "on" : "off");
    const key = `State=${state}, Size=medium, Check=${check}`;
    if (!sourceByKey.has(key)) sourceByKey.set(key, child);
  }
  const targets = [
    "State=unchecked, Size=medium, Check=on",
    "State=checked, Size=medium, Check=on",
    "State=focus, Size=medium, Check=off",
    "State=disabled, Size=medium, Check=off",
    "State=error, Size=medium, Check=off",
    "State=readonly, Size=medium, Check=off",
    "State=focus, Size=medium, Check=on",
    "State=disabled, Size=medium, Check=on",
    "State=error, Size=medium, Check=on",
    "State=readonly, Size=medium, Check=on",
  ];
  const fallback = [...sourceByKey.values()][0];
  if (!fallback) throw new Error("Checkbox Component Set에 복제할 Component가 없습니다.");
  const oldX = set.x;
  const oldY = set.y;
  const clones = targets.map(target => {
    const source = sourceByKey.get(target) ?? sourceByKey.get(target.replace("Check=on", "Check=off")) ?? fallback;
    const clone = source.clone();
    page.appendChild(clone);
    clone.name = target;
    return clone;
  });
  const oldName = set.name;
  const logicalId = set.getPluginData(PLUGIN_DATA_LOGICAL_ID) || "krds.checkbox";
  set.remove();
  const rebuilt = figma.combineAsVariants(clones, page);
  rebuilt.name = oldName;
  rebuilt.x = oldX;
  rebuilt.y = oldY;
  rebuilt.setPluginData(PLUGIN_DATA_LOGICAL_ID, logicalId);
  rebuilt.setPluginData("checkboxRebuilt", "true");
  figma.currentPage.selection = [rebuilt];
  return { created: clones.length, key: rebuilt.key };
}

function desiredAccessibilityStates(entry: RegistryV2["components"][string]): string[] {
  const hasState = Object.values(entry.variantAxes ?? {}).some(axis => axis.logicalName.toLowerCase() === "state");
  if (!hasState) return [];
  const field = (entry.roles ?? []).some(role => role.startsWith("field."));
  // 입력 계열은 오류 표시뿐 아니라 조회 전용(Read-only) 상태도 계약상 필요하다.
  // Figma의 State 값은 공백·하이픈을 피하기 위해 `readonly`로 통일하고,
  // 서버 Validator가 `readonly`/`read-only`/`view`를 호환 처리한다.
  return field ? ["focus", "disabled", "error", "readonly"] : ["focus", "disabled"];
}

async function ensureTextareaStructure(component: ComponentNode, state: string): Promise<boolean> {
  await figma.loadFontAsync({ family: "Inter", style: "Regular" });
  const existing = new Set(component.children.map(child => child.name));
  const addText = (name: string, characters: string, x: number, y: number, color: RGB, visible = true): TextNode => {
    const text = figma.createText();
    text.name = name;
    text.fontName = { family: "Inter", style: "Regular" };
    text.fontSize = name === "Label" ? 14 : 12;
    text.characters = characters;
    text.fills = [{ type: "SOLID", color }];
    text.opacity = visible ? 1 : 0;
    try { text.layoutPositioning = "ABSOLUTE"; } catch { /* older Figma runtime */ }
    text.x = x;
    text.y = y;
    component.appendChild(text);
    return text;
  };
  let changed = false;
  if (!existing.has("Label")) {
    addText("Label", "내용", 12, 8, { r: 0.12, g: 0.14, b: 0.18 });
    changed = true;
  }
  if (!existing.has("Placeholder")) {
    addText("Placeholder", "여러 줄로 입력하세요", 16, 48, { r: 0.38, g: 0.41, b: 0.46 });
    changed = true;
  }
  if (!existing.has("Helper")) {
    addText("Helper", "최대 500자까지 입력할 수 있습니다.", 12, 136, { r: 0.38, g: 0.41, b: 0.46 }, state.toLowerCase() !== "error");
    changed = true;
  }
  if (!existing.has("Error")) {
    addText("Error", "내용을 입력해 주세요.", 12, 136, { r: 0.72, g: 0.12, b: 0.12 }, state.toLowerCase() === "error");
    changed = true;
  }
  return changed;
}

async function repairAccessibilityStates(registry: RegistryV2): Promise<{
  registry: RegistryV2; inventory: Record<string, unknown>; changedSets: number; createdVariants: number; skipped: string[];
}> {
  if (!registry || registry.profileId !== "krds" || !registry.components) {
    throw new Error("KRDS Component Registry v2 JSON이 필요합니다.");
  }
  await figma.loadAllPagesAsync();
  const sets = figma.root.findAll(node => node.type === "COMPONENT_SET") as ComponentSetNode[];
  const byKey = new Map(sets.map(set => [set.key, set]));
  const normalizeName = (value: string) => value.trim().toLowerCase().replace(/[_\s-]+/g, "");
  const byName = new Map<string, ComponentSetNode>();
  for (const set of sets) byName.set(normalizeName(set.name), set);
  let changedSets = 0;
  let createdVariants = 0;
  const skipped: string[] = [];
  const inventoryComponents: Record<string, unknown> = {};

  for (const [logicalType, entry] of Object.entries(registry.components)) {
    const desired = desiredAccessibilityStates(entry);
    const logicalShortName = logicalType.replace(/^krds\./, "");
    const nameAliases: Record<string, string[]> = {
      searchPanel: ["Search Panel", "Search Filter Panel"],
      textField: ["Text Field", "Text Input"],
      textarea: ["Textarea", "Text Area"],
      select: ["Select"],
      checkbox: ["Checkbox"],
      button: ["Button"],
    };
    const candidateNames = [entry.componentName ?? "", logicalShortName, ...(nameAliases[logicalShortName] ?? [])]
      .map(normalizeName)
      .filter(Boolean);
    let set = byKey.get(entry.componentSetKey)
      ?? candidateNames.map(name => byName.get(name)).find(Boolean);
    if (!set && desired.length > 0) {
      skipped.push(logicalType);
      continue;
    }
    if (!set) {
      const actualProperties: Record<string, { type: string; values: string[] }> = {};
      for (const property of Object.values(entry.properties ?? {})) {
        actualProperties[property.figmaProperty] = {
          type: property.type,
          values: property.type.toUpperCase() === "VARIANT" ? Object.values(property.values ?? {}) : [],
        };
      }
      inventoryComponents[logicalType] = {
        componentSetKey: entry.componentSetKey,
        properties: actualProperties,
        variants: entry.variants,
      };
      continue;
    }
    entry.componentSetKey = set.key;
    if (logicalShortName === "checkbox" && set.getPluginData("checkboxRebuilt") !== "true") {
      const rebuilt = rebuildCheckboxComponentSet(set, logicalType);
      rebuilt.setPluginData("checkboxRebuilt", "true");
      set = rebuilt;
      entry.componentSetKey = rebuilt.key;
    }
    normalizeFocusVariantNames(set, Object.values(entry.variantAxes ?? {}).some(axis => axis.logicalName.toLowerCase() === "state"));
    let changed = false;
    if (["checkbox", "textField", "select", "searchPanel"].includes(logicalShortName)) {
      // 운영 Checkbox가 상태만 가지고 있어도 계약이 요구하는 축을 실제
      // Component Set Property로 만든다. 추가 Property는 기존 Variant에
      // 기본값을 부여하고, 이후 상태 복제도 동일 축을 유지한다.
      changed = ensureVariantProperty(set, "Size", "medium") || changed;
      if (logicalShortName === "checkbox") {
        changed = ensureVariantProperty(set, "Check", "off") || changed;
      }
    }
    const components = set.children.filter(child => child.type === "COMPONENT") as ComponentNode[];
    if (logicalShortName === "checkbox") {
      // Property를 추가하면 기존 Variant에는 기본값(off)이 들어간다.
      // 운영 원본의 Checked/Unchecked 의미를 유지하도록 Check 축을
      // 실제 상태에 맞춰 정규화한다.
      for (const component of components) {
        const props = variantPropertiesFromName(component);
        const state = Object.entries(props).find(([key]) => key.toLowerCase() === "state")?.[1]
          ?? component.name;
        const checkKey = Object.keys(props).find(key => key.toLowerCase() === "check") ?? "Check";
        const sizeKey = Object.keys(props).find(key => key.toLowerCase() === "size") ?? "Size";
        props[checkKey] = /checked/i.test(state) ? "on" : "off";
        props[sizeKey] = "medium";
        const nextName = normalizedVariantName(props);
        if (nextName !== component.name) {
          component.name = nextName;
          changed = true;
        }
      }
    }
    if (logicalShortName === "textarea") {
      for (const component of components) {
        let properties = variantPropertiesFromName(component);
        const state = Object.entries(properties).find(([name]) => name.toLowerCase() === "state")?.[1] ?? "default";
        if (await ensureTextareaStructure(component, state)) changed = true;
      }
    }
    const existingNames = new Set(components.map(component => component.name.toLowerCase()));
    const defaultSources = components.filter(component => {
      let state = component.name.match(/(?:^|,\s*)State=([^,]+)/i)?.[1] ?? "default";
      try { state = component.variantProperties?.State ?? component.variantProperties?.state ?? state; } catch { /* name fallback */ }
      return state.toLowerCase() === "default";
    });
    // Checkbox처럼 기본 상태가 `Unchecked`/`Checked`로 표현되는 Set은
    // State=default Variant가 없으므로 모든 기존 상태를 복제 원본으로 사용한다.
    // 그래야 checked·unchecked 각각에 disabled/error/readonly를 생성할 수 있다.
    if (defaultSources.length === 0 && logicalShortName === "checkbox") {
      defaultSources.push(...components);
    }
    if (desired.includes("focus") && defaultSources.length === 0) {
      const namedDefault = components.filter(component => /State\s*=\s*default/i.test(component.name));
      if (namedDefault.length > 0) defaultSources.push(...namedDefault);
    }

    for (const state of desired) {
      for (const source of defaultSources) {
        const props = variantPropertiesFromName(source);
        const stateKey = Object.keys(props).find(key => key.toLowerCase() === "state") ?? "State";
        props[stateKey] = state;
        const name = normalizedVariantName(props);
        if (existingNames.has(name.toLowerCase())) continue;
        const clone = source.clone();
        if (clone.parent !== set) set.appendChild(clone);
        clone.name = name;
        existingNames.add(name.toLowerCase());
        createdVariants++;
        changed = true;
      }
    }
    if (desired.includes("focus") && !set.children.some(child =>
      child.type === "COMPONENT" && /(?:^|,\s*)State=focus(?:,|$)/i.test(child.name))) {
      const source = defaultSources[0] ?? components[0];
      if (source) {
        const props = variantPropertiesFromName(source);
        const stateKey = Object.keys(props).find(key => key.toLowerCase() === "state") ?? "State";
        props[stateKey] = "focus";
        const clone = source.clone();
        if (clone.parent !== set) set.appendChild(clone);
        clone.name = normalizedVariantName(props);
        createdVariants++;
        changed = true;
      }
    }
    if (changed) changedSets++;

    const refreshed = set.children.filter(child => child.type === "COMPONENT") as ComponentNode[];
    const variants: Record<string, string> = {};
    const propertyValues: Record<string, Set<string>> = {};
    for (const component of refreshed) {
      variants[component.name] = component.key;
      let variantProperties: Record<string, string> = {};
      try { variantProperties = { ...(component.variantProperties ?? {}) } as Record<string, string>; } catch { variantProperties = variantPropertiesFromName(component); }
      for (const [name, value] of Object.entries(variantProperties)) {
        // Figma가 Component Property를 추가하는 과정에서 이름 없는
        // 임시 Variant 축을 `undefined`로 반환하는 경우가 있다.
        // Inventory 계약에는 실제 Property만 기록해야 하므로 제거한다.
        if (!name || name === "undefined" || value == null || value === "undefined") continue;
        (propertyValues[name] ??= new Set()).add(value.toLowerCase());
      }
    }
    entry.variants = variants;
    const stateAxis = Object.values(entry.variantAxes ?? {}).find(axis => axis.logicalName.toLowerCase() === "state");
    if (stateAxis) {
      stateAxis.allowedValues = [...(propertyValues[stateAxis.figmaProperty] ?? new Set())];
      const mapping = Object.values(entry.properties ?? {}).find(property => property.figmaProperty === stateAxis.figmaProperty);
      if (mapping) mapping.values = Object.fromEntries(stateAxis.allowedValues.map(value => [value, value]));
    }
    const actualProperties: Record<string, { type: string; values: string[] }> = {};
    for (const [name, values] of Object.entries(propertyValues)) {
      actualProperties[name] = { type: "VARIANT", values: [...values] };
    }
    for (const property of Object.values(entry.properties ?? {})) {
      if (!actualProperties[property.figmaProperty]) {
        actualProperties[property.figmaProperty] = { type: property.type, values: [] };
      }
    }
    inventoryComponents[logicalType] = { componentSetKey: set.key, properties: actualProperties, variants };
  }

  // 운영 파일에 Foundation 변수가 아직 없으면 Registry 계약의 논리 ID와
  // 타입으로 최소 변수를 생성한다. 실제 값은 Publish 전 디자인 토큰
  // 검토자가 채우며, 여기서는 Collection Key가 Inventory에 남도록 한다.
  const registryVariables = (registry as RegistryV2 & { variables?: Record<string, {
    variableName?: string; collectionName?: string; resolvedType?: string;
  }> }).variables ?? {};
  for (const [logicalId, definition] of Object.entries(registryVariables)) {
    const collectionName = definition.collectionName || "Foundation";
    const collection = await findOrCreateCollection(collectionName, ["Default"]);
    const variableName = definition.variableName || logicalId;
    const resolvedType = (definition.resolvedType || "STRING") as VariableResolvedDataType;
    const variable = await findOrCreateVariable(collection, variableName, resolvedType, registry.profileId);
    variable.setPluginData(PLUGIN_DATA_NS_ID, registry.profileId);
    variable.setPluginData(PLUGIN_DATA_LOGICAL_ID, logicalId);
  }

  const stamp = new Date().toISOString();
  const inventoryVariables: Record<string, unknown> = {};
  for (const collection of await figma.variables.getLocalVariableCollectionsAsync()) {
    for (const variableId of collection.variableIds) {
      const variable = await figma.variables.getVariableByIdAsync(variableId);
      if (!variable) continue;
      const logicalId = variable.getPluginData(PLUGIN_DATA_LOGICAL_ID) || variable.name;
      if (!logicalId) continue;
      inventoryVariables[logicalId] = {
        variableKey: variable.key,
        variableName: variable.name,
        collectionKey: collection.key,
        collectionName: collection.name,
        resolvedType: variable.resolvedType,
        publishStatus: await variable.getPublishStatusAsync(),
      };
    }
  }
  return {
    registry,
    inventory: {
      profileId: registry.profileId,
      registryVersion: registry.registryVersion,
      inventoryVersion: `figma-${stamp.replace(/[-:.TZ]/g, "").slice(0, 14)}`,
      capturedAt: stamp,
      components: inventoryComponents,
      variables: inventoryVariables,
    },
    changedSets,
    createdVariants,
    skipped,
  };
}

async function findTaggedVariables(designSystemId: string): Promise<Map<string, Variable>> {
  const result = new Map<string, Variable>();
  for (const collection of await figma.variables.getLocalVariableCollectionsAsync()) {
    for (const variableId of collection.variableIds) {
      const variable = await figma.variables.getVariableByIdAsync(variableId);
      if (variable && variable.getPluginData(PLUGIN_DATA_NS_ID) === designSystemId) {
        const logicalId = variable.getPluginData(PLUGIN_DATA_LOGICAL_ID);
        if (logicalId) result.set(logicalId, variable);
      }
    }
  }
  return result;
}

function patternAsComponentDefinition(pattern: PatternDefinition): ComponentDefinition {
  return {
    id: pattern.id,
    name: pattern.name,
    layout: { mode: "VERTICAL", paddingX: "24", paddingY: "24", gap: "16", alignment: "CENTER" },
    properties: [{ name: "State", type: "VARIANT", defaultValue: "Default" }],
    variants: { State: ["Default"] },
  };
}

function allRegistryDefinitions(spec: DesignSystemSpec): ComponentDefinition[] {
  return [...spec.components, ...spec.patterns.map(patternAsComponentDefinition)];
}

// ── R3-021: Preview(diff) 계산 — 실제 노드는 만들지 않고 ADD/UPDATE/NO_CHANGE/BREAKING/DEPRECATE만 판정 ──

async function computeDiff(spec: DesignSystemSpec): Promise<DiffEntry[]> {
  const entries: DiffEntry[] = [];
  const existingSets = findTaggedComponentSets(spec.id);
  const existingVars = await findTaggedVariables(spec.id);
  const definitions = allRegistryDefinitions(spec);
  const incomingComponentIds = new Set(definitions.map(def => def.id));
  const incomingTokenNames = new Set([
    ...spec.tokens.map(t => t.name),
    ...spec.variableCollections.flatMap(vc => Object.keys(vc.valuesByMode)),
  ]);
  const patternById = new Map(spec.patterns.map(pattern => [pattern.id, pattern]));

  for (const definition of definitions) {
    const existing = existingSets.get(definition.id);
    const hashSource = patternById.get(definition.id) ?? definition;
    const newHash = await contentHash(hashSource);
    if (!existing) {
      entries.push({
        logicalId: definition.id,
        name: definition.name,
        kind: "ADD",
        detail: "신규 컴포넌트/패턴",
        comparisons: [],
      });
      continue;
    }
    const oldHash = existing.getPluginData(PLUGIN_DATA_CONTENT_HASH);
    if (oldHash === newHash) {
      entries.push({
        logicalId: definition.id,
        name: definition.name,
        kind: "NO_CHANGE",
        detail: "변경 없음",
        comparisons: [],
      });
      continue;
    }
    const before = readDefinitionSnapshot(existing);
    const planned = patternById.has(definition.id)
      ? {
          kind: "UPDATE" as DiffKind,
          comparisons: compareSnapshots(before, componentSnapshot(definition)),
        }
      : planComponentChange(before, definition);
    entries.push({
      logicalId: definition.id, name: definition.name,
      kind: planned.kind,
      detail: planned.kind === "BREAKING"
        ? "기존 Property/Variant 옵션이 제거되었습니다."
        : "속성·Variant·Layout·메타데이터 또는 Pattern 구성이 갱신됩니다.",
      comparisons: planned.comparisons,
    });
  }
  for (const [logicalId, set] of existingSets) {
    if (!incomingComponentIds.has(logicalId)) {
      entries.push({
        logicalId,
        name: set.name,
        kind: "DEPRECATE",
        detail: "새 Spec에서 빠졌습니다. 자동 삭제하지 않습니다.",
        comparisons: [],
      });
    }
  }
  for (const [logicalId, variable] of existingVars) {
    if (!incomingTokenNames.has(logicalId)) {
      entries.push({
        logicalId,
        name: variable.name,
        kind: "DEPRECATE",
        detail: "새 Spec에서 빠진 토큰입니다. 자동 삭제하지 않습니다.",
        comparisons: [],
      });
    }
  }
  return entries;
}

function readDefinitionSnapshot(existing: ComponentSetNode): Record<string, unknown> {
  const stored = existing.getPluginData(PLUGIN_DATA_DEFINITION);
  if (stored) {
    try {
      return JSON.parse(stored) as Record<string, unknown>;
    } catch {
      // 이전 Plugin 버전의 손상된 snapshot은 현재 Figma 노드에서 복구한다.
    }
  }
  return {
    name: existing.name,
    description: existing.description ?? "",
    developer: {},
    layout: {},
    properties: Object.entries(existing.componentPropertyDefinitions ?? {}).map(([name, value]) => ({
      name: name.split("#")[0],
      type: value.type,
      defaultValue: String(value.defaultValue ?? ""),
    })),
    variants: Object.fromEntries(
      Object.entries(existing.variantGroupProperties ?? {})
        .map(([name, value]) => [name, value.values])),
  };
}

// ── R3-010/011: 토큰·Variable Collection 생성/갱신 ──

function resolvedTypeFor(category: TokenCategory): VariableResolvedDataType {
  if (category === "COLOR") return "COLOR";
  if (category === "SPACING" || category === "RADIUS") return "FLOAT";
  return "STRING";
}

function parseColor(value: string): RGBA | null {
  const match = value.match(/#([0-9a-fA-F]{6})([0-9a-fA-F]{2})?/);
  if (!match) return null;
  const hex = match[1];
  const alphaHex = match[2];
  return {
    r: parseInt(hex.slice(0, 2), 16) / 255,
    g: parseInt(hex.slice(2, 4), 16) / 255,
    b: parseInt(hex.slice(4, 6), 16) / 255,
    a: alphaHex ? parseInt(alphaHex, 16) / 255 : 1,
  };
}

function parseVariableValue(category: TokenCategory, raw: string): VariableValue {
  if (category === "COLOR") return parseColor(raw) ?? { r: 0, g: 0, b: 0, a: 1 };
  if (category === "SPACING" || category === "RADIUS") {
    const numeric = parseFloat(raw.replace(/[^0-9.-]/g, ""));
    return Number.isFinite(numeric) ? numeric : 0;
  }
  return raw;
}

async function findOrCreateCollection(name: string, modes: string[]): Promise<VariableCollection> {
  const existing = (await figma.variables.getLocalVariableCollectionsAsync()).find(c => c.name === name);
  const collection = existing ?? figma.variables.createVariableCollection(name);
  const desiredModes = modes.length > 0 ? modes : ["Default"];
  if (collection.modes.length === 1 && collection.modes[0].name !== desiredModes[0]) {
    collection.renameMode(collection.modes[0].modeId, desiredModes[0]);
  }
  for (const modeName of desiredModes.slice(1)) {
    if (!collection.modes.some(m => m.name === modeName)) collection.addMode(modeName);
  }
  return collection;
}

async function findOrCreateVariable(
  collection: VariableCollection,
  name: string,
  type: VariableResolvedDataType,
  ownerDesignSystemId?: string,
): Promise<Variable> {
  const variableName = figmaVariableName(name);
  for (const variableId of collection.variableIds) {
    const variable = await figma.variables.getVariableByIdAsync(variableId);
    if (!variable) continue;
    const sameLogicalVariable = variable.getPluginData(PLUGIN_DATA_LOGICAL_ID) === name
      || variable.name === variableName;
    if (!sameLogicalVariable) continue;
    if (variable.resolvedType === type) return variable;
    if (ownerDesignSystemId && variable.getPluginData(PLUGIN_DATA_NS_ID) === ownerDesignSystemId) {
      // 이전 실행이 중간 실패해 남긴 동일 논리 변수의 타입만 복구한다.
      // 다른 Design System이 소유한 변수는 삭제하지 않고 명시적인 오류로 중단한다.
      variable.remove();
      break;
    }
    if (ownerDesignSystemId && !variable.getPluginData(PLUGIN_DATA_NS_ID)) {
      // 구버전 Plugin이 createVariable 직후 중단되어 소유 표식 없이 남긴 잔여 변수는
      // 원본을 보존한 채 legacy 경로로 이동하고, 요청 타입의 새 변수를 만든다.
      variable.name = `legacy/${variableName}/${variable.resolvedType.toLowerCase()}`;
      break;
    }
    throw new Error(
      `Variable 타입이 일치하지 않습니다: ${name} (기존 ${variable.resolvedType}, 요청 ${type})`,
    );
  }
  return figma.variables.createVariable(variableName, collection, type);
}

async function applyTokens(spec: DesignSystemSpec): Promise<void> {
  if (spec.tokens.length === 0) return;
  const collection = await findOrCreateCollection("Foundation", ["Default"]);
  const defaultModeId = collection.modes[0].modeId;
  for (const token of spec.tokens) {
    const type = resolvedTypeFor(token.category);
    const variable = await findOrCreateVariable(collection, token.name, type, spec.id);
    variable.setValueForMode(defaultModeId, parseVariableValue(token.category, token.value));
    variable.setPluginData(PLUGIN_DATA_NS_ID, spec.id);
    variable.setPluginData(PLUGIN_DATA_LOGICAL_ID, token.name);
    variable.setPluginData(PLUGIN_DATA_CONTENT_HASH, await contentHash(token));
  }
}

async function applyVariableCollections(spec: DesignSystemSpec): Promise<void> {
  const tokenTypeByName = new Map(
    spec.tokens.map(token => [token.name, resolvedTypeFor(token.category)]),
  );
  for (const vc of spec.variableCollections) {
    const collection = await findOrCreateCollection(vc.name, vc.modes);
    const modeIdByName = new Map(collection.modes.map(m => [m.name, m.modeId]));
    for (const [variableName, valuesByMode] of Object.entries(vc.valuesByMode)) {
      const variable = await findOrCreateVariable(
        collection,
        variableName,
        tokenTypeByName.get(variableName) ?? "STRING",
        spec.id,
      );
      for (const [modeName, rawValue] of Object.entries(valuesByMode)) {
        const modeId = modeIdByName.get(modeName);
        if (!modeId) continue;
        const numeric = parseFloat(rawValue);
        const color = parseColor(rawValue);
        variable.setValueForMode(modeId, color ?? (Number.isFinite(numeric) && /^-?[\d.]+$/.test(rawValue) ? numeric : rawValue));
      }
      variable.setPluginData(PLUGIN_DATA_NS_ID, spec.id);
      variable.setPluginData(PLUGIN_DATA_LOGICAL_ID, variableName);
      variable.setPluginData(PLUGIN_DATA_CONTENT_HASH, await contentHash(valuesByMode));
    }
  }
}

// ── R3-012/013/014/018: 컴포넌트·Variant·Property 생성/갱신(제자리 Update, 파괴적 재생성 금지) ──

function variantCombinations(variants: Record<string, string[]>): Record<string, string>[] {
  const keys = Object.keys(variants);
  if (keys.length === 0) return [{}];
  let combos: Record<string, string>[] = [{}];
  for (const key of keys) {
    const next: Record<string, string>[] = [];
    for (const combo of combos) {
      for (const value of variants[key]) next.push({ ...combo, [key]: value });
    }
    combos = next;
  }
  return combos;
}

function variantName(combo: Record<string, string>): string {
  return Object.entries(combo).map(([k, v]) => `${k}=${v}`).join(", ");
}

function applyLayout(node: FrameNode | ComponentNode, layout?: ComponentLayout | null): void {
  if (!layout) return;
  node.layoutMode = layout.mode === "VERTICAL" ? "VERTICAL" : "HORIZONTAL";
  node.paddingLeft = node.paddingRight = pxValue(layout.paddingX);
  node.paddingTop = node.paddingBottom = pxValue(layout.paddingY);
  node.itemSpacing = pxValue(layout.gap);
  node.primaryAxisSizingMode = "AUTO";
  node.counterAxisSizingMode = "AUTO";
  if (layout.alignment === "CENTER") node.counterAxisAlignItems = "CENTER";
  node.minWidth = optionalPx(layout.minWidth);
  node.maxWidth = optionalPx(layout.maxWidth);
  node.minHeight = optionalPx(layout.minHeight);
  node.maxHeight = optionalPx(layout.maxHeight);
}

function pxValue(raw?: string): number {
  if (!raw) return 0;
  const numeric = parseFloat(raw.replace(/[^0-9.-]/g, ""));
  return Number.isFinite(numeric) ? numeric : 0;
}

function optionalPx(raw?: string): number | null {
  if (!raw) return null;
  const value = pxValue(raw);
  return value > 0 ? value : null;
}

async function buildVariantComponent(
  defName: string,
  combo: Record<string, string>,
  layout?: ComponentLayout | null,
): Promise<ComponentNode> {
  const component = figma.createComponent();
  component.name = variantName(combo);
  component.resize(120, 40);
  const label = figma.createText();
  await figma.loadFontAsync(label.fontName as FontName);
  label.characters = `${defName} · ${variantName(combo)}`;
  component.appendChild(label);
  applyLayout(component, layout
    ?? { mode: "HORIZONTAL", paddingX: "16", paddingY: "12", gap: "8", alignment: "CENTER" });
  return component;
}

function applyNonVariantProperties(target: ComponentSetNode, _properties: ComponentProperty[]): void {
  // Component Set에만 추가한 TEXT/BOOLEAN 속성은 실제 내부 레이어에 연결되지
  // 않으면 Figma Publish 단계에서 "Unused properties"로 거부된다. 현재 Author
  // 샘플은 해당 속성을 내부 레이어에 바인딩하지 않으므로, Publish 가능한
  // Component Set을 유지하기 위해 기존의 연결되지 않은 속성을 제거한다.
  for (const [propertyName, definition] of Object.entries(target.componentPropertyDefinitions ?? {})) {
    if (definition.type !== "VARIANT" && definition.type !== "SLOT") {
      target.deleteComponentProperty(propertyName);
    }
  }
}

async function applyComponent(spec: DesignSystemSpec, def: ComponentDefinition, existingSets: Map<string, ComponentSetNode>): Promise<void> {
  const existing = existingSets.get(def.id);
  if (!existing) {
    const combos = variantCombinations(def.variants);
    const variantComponents = await Promise.all(combos.map(combo =>
      buildVariantComponent(def.name, combo, def.layout)));
    for (const c of variantComponents) figma.currentPage.appendChild(c);
    const set = figma.combineAsVariants(variantComponents, figma.currentPage);
    set.name = def.name;
    applyComponentMetadata(set, def);
    set.setPluginData(PLUGIN_DATA_NS_ID, spec.id);
    set.setPluginData(PLUGIN_DATA_LOGICAL_ID, def.id);
    applyNonVariantProperties(set, def.properties);
    set.setPluginData(PLUGIN_DATA_CONTENT_HASH, await contentHash(def));
    set.setPluginData(PLUGIN_DATA_DEFINITION, JSON.stringify(componentSnapshot(def)));
    return;
  }

  // 제자리 Update: Component Key는 유지한 채 이름·description·신규 Property·신규 Variant만 반영한다.
  existing.name = def.name;
  applyComponentMetadata(existing, def);
  applyNonVariantProperties(existing, def.properties);
  for (const child of existing.children) {
    if (child.type === "COMPONENT") applyLayout(child, def.layout);
  }
  const existingCombos = new Set(existing.children.map(child => child.name));
  for (const combo of variantCombinations(def.variants)) {
    const name = variantName(combo);
    if (existingCombos.has(name)) continue;
    const created = await buildVariantComponent(def.name, combo, def.layout);
    existing.appendChild(created);
  }
  existing.setPluginData(PLUGIN_DATA_CONTENT_HASH, await contentHash(def));
  existing.setPluginData(PLUGIN_DATA_DEFINITION, JSON.stringify(componentSnapshot(def)));
}

function applyComponentMetadata(target: ComponentSetNode, def: ComponentDefinition): void {
  const developer = def.developer ?? {};
  const details = [
    def.description?.trim(),
    developer.codeComponent ? `Code: ${developer.codeComponent}` : "",
    developer.packageName ? `Package: ${developer.packageName}` : "",
  ].filter(Boolean);
  target.description = details.join("\n");
  target.documentationLinks = developer.documentationUrl
    ? [{ uri: developer.documentationUrl }]
    : [];
  target.setPluginData("developerMetadata", JSON.stringify(developer));
}

async function populatePatternComponent(
  component: ComponentNode,
  pattern: PatternDefinition,
  existingSets: Map<string, ComponentSetNode>,
): Promise<void> {
  for (const child of [...component.children]) child.remove();
  component.name = "State=Default";
  applyLayout(component, { mode: "VERTICAL", paddingX: "24", paddingY: "24", gap: "16", alignment: "CENTER" });
  for (const dependencyId of pattern.composedOf) {
    const dependency = existingSets.get(dependencyId);
    const defaultVariant = dependency?.children.find(child => child.type === "COMPONENT") as ComponentNode | undefined;
    if (!defaultVariant) throw new Error(`Pattern ${pattern.id}의 구성 컴포넌트를 찾을 수 없습니다: ${dependencyId}`);
    component.appendChild(defaultVariant.createInstance());
  }
}

async function applyPattern(
  spec: DesignSystemSpec,
  pattern: PatternDefinition,
  existingSets: Map<string, ComponentSetNode>,
): Promise<void> {
  const existing = existingSets.get(pattern.id);
  if (!existing) {
    const component = figma.createComponent();
    await populatePatternComponent(component, pattern, existingSets);
    figma.currentPage.appendChild(component);
    const set = figma.combineAsVariants([component], figma.currentPage);
    set.name = pattern.name;
    set.setPluginData(PLUGIN_DATA_NS_ID, spec.id);
    set.setPluginData(PLUGIN_DATA_LOGICAL_ID, pattern.id);
    set.setPluginData(PLUGIN_DATA_CONTENT_HASH, await contentHash(pattern));
    set.setPluginData(
      PLUGIN_DATA_DEFINITION,
      JSON.stringify(componentSnapshot(patternAsComponentDefinition(pattern))),
    );
    existingSets.set(pattern.id, set);
    return;
  }

  existing.name = pattern.name;
  const defaultVariant = existing.children.find(child => child.type === "COMPONENT") as ComponentNode | undefined;
  if (!defaultVariant) throw new Error(`Pattern Component Set에 기본 Variant가 없습니다: ${pattern.id}`);
  await populatePatternComponent(defaultVariant, pattern, existingSets);
  existing.setPluginData(PLUGIN_DATA_CONTENT_HASH, await contentHash(pattern));
  existing.setPluginData(
    PLUGIN_DATA_DEFINITION,
    JSON.stringify(componentSnapshot(patternAsComponentDefinition(pattern))),
  );
}

// ── R3-020: 대표 상태를 모은 Preview 페이지 생성/갱신 ──

async function buildPreviewPage(spec: DesignSystemSpec, diff: DiffEntry[]): Promise<void> {
  const pageName = `🔍 KRDS Preview — ${spec.id}`;
  let page = figma.root.children.find(p => p.type === "PAGE" && p.name === pageName) as PageNode | undefined;
  if (!page) {
    page = figma.createPage();
    page.name = pageName;
  } else {
    page.children.forEach(child => child.remove());
  }

  const summary = figma.createText();
  await figma.loadFontAsync(summary.fontName as FontName);
  summary.characters = diff.map(d => {
    const comparisonRows = d.comparisons.map(comparison =>
      `  ${comparison.change} ${comparison.field}: ${comparison.before} → ${comparison.after}`);
    return [`[${d.kind}] ${d.name} (${d.logicalId}) — ${d.detail}`, ...comparisonRows].join("\n");
  }).join("\n");
  summary.x = 0;
  summary.y = 0;
  page.appendChild(summary);

  const existingSets = findTaggedComponentSets(spec.id);
  let offsetY = 200;
  for (const def of allRegistryDefinitions(spec)) {
    const set = existingSets.get(def.id);
    if (!set) continue;
    for (const child of set.children) {
      if (child.type !== "COMPONENT") continue;
      const instance = (child as ComponentNode).createInstance();
      instance.x = 0;
      instance.y = offsetY;
      page.appendChild(instance);
      offsetY += 60;
    }
    offsetY += 40;
  }
}

// ── R4-001~006: 사람 Publish 후 공개 Key와 상태를 ComponentRegistry 후보로 내보낸다. ──

function propertyMappings(_def: ComponentDefinition): ComponentRegistryExport["components"][string]["properties"] {
  // Registry에는 실제 Figma 인스턴스에 연결된 속성만 노출한다. 현재 샘플
  // Component Set은 variant key로만 동기화하며, 연결되지 않은 속성을 내보내면
  // 대상 플러그인이 존재하지 않는 속성을 setProperties() 하게 된다.
  return {};
}

async function buildRegistryExport(
  spec: DesignSystemSpec,
  options: RegistryExportOptions,
): Promise<ComponentRegistryExport> {
  if (!options.fileKey.trim()) throw new Error("Published Library fileKey를 입력하세요.");
  if (!options.registryVersion.trim()) throw new Error("Registry version을 입력하세요.");

  const componentSets = findTaggedComponentSets(spec.id);
  const components: ComponentRegistryExport["components"] = {};
  for (const def of allRegistryDefinitions(spec)) {
    const set = componentSets.get(def.id);
    if (!set) throw new Error(`논리 컴포넌트를 찾을 수 없습니다: ${def.id}`);
    const publishStatus = await set.getPublishStatusAsync();
    const variants: Record<string, string> = {};
    for (const child of set.children) {
      if (child.type === "COMPONENT") variants[child.name] = child.key;
    }
    components[def.id] = {
      componentSetKey: set.key,
      componentName: set.name,
      publishStatus,
      lifecycleStatus: def.lifecycleStatus ?? "ACTIVE",
      replacementLogicalType: def.replacementLogicalType ?? null,
      aliases: def.aliases ?? [],
      variants,
      properties: propertyMappings(def),
    };
  }

  const variables: ComponentRegistryExport["variables"] = {};
  for (const collection of await figma.variables.getLocalVariableCollectionsAsync()) {
    const collectionStatus = await collection.getPublishStatusAsync();
    for (const variableId of collection.variableIds) {
      const variable = await figma.variables.getVariableByIdAsync(variableId);
      if (!variable) continue;
      // 운영 Library에서 이미 생성·Publish된 Foundation 변수를 태그가
      // 없다는 이유로 누락하지 않는다. 태그가 있으면 논리 ID를 우선하고,
      // 없으면 이름을 계약 ID로 사용해 Collection Key를 보존한다.
      const logicalId = variable.getPluginData(PLUGIN_DATA_LOGICAL_ID) || variable.name;
      if (!logicalId) continue;
      const variableStatus = await variable.getPublishStatusAsync();
      variables[logicalId] = {
        variableKey: variable.key,
        variableName: variable.name,
        collectionKey: collection.key,
        collectionName: collection.name,
        resolvedType: variable.resolvedType,
        publishStatus: variableStatus === "CURRENT" && collectionStatus === "CURRENT"
          ? "CURRENT"
          : variableStatus === "CHANGED" || collectionStatus === "CHANGED" ? "CHANGED" : "UNPUBLISHED",
      };
    }
  }

  return {
    profileId: spec.id,
    profileVersion: spec.version,
    registryVersion: options.registryVersion.trim(),
    library: { fileKey: options.fileKey.trim(), name: options.libraryName.trim() || spec.name },
    components,
    variables,
  };
}

// ── R3-025: 승인 기록을 로컬 JSON으로 내보낸다(R6 API가 아직 없어 서버 직접 기록은 하지 않음) ──

type ReviewExport = {
  id: string; targetType: "DESIGN_SYSTEM_PROFILE"; targetId: string; targetVersion: string;
  eventType: ReviewEvent; status: ReviewStatus; actor: string; comment: string | null; occurredAt: string;
};

type StoredReviewState = {
  designSystemId: string;
  version: string;
  status: ReviewStatus;
  actor?: string;
  comment?: string | null;
  occurredAt: string;
};

function readReviewState(spec: DesignSystemSpec): StoredReviewState {
  const raw = figma.root.getPluginData(PLUGIN_DATA_REVIEW);
  if (raw) {
    try {
      const stored = JSON.parse(raw) as StoredReviewState;
      if (stored.designSystemId === spec.id && stored.version === spec.version) return stored;
    } catch {
      // 손상된 이전 상태는 새 DRAFT로 복구한다.
    }
  }
  return {
    designSystemId: spec.id,
    version: spec.version,
    status: "DRAFT",
    occurredAt: new Date().toISOString(),
  };
}

function writeReviewState(state: StoredReviewState): void {
  figma.root.setPluginData(PLUGIN_DATA_REVIEW, JSON.stringify(state));
}

function buildReviewExport(
  spec: DesignSystemSpec,
  eventType: ReviewExport["eventType"],
  status: ReviewStatus,
  actor: string,
  comment: string,
): ReviewExport {
  return {
    id: `${spec.id}-${Date.now()}`, targetType: "DESIGN_SYSTEM_PROFILE", targetId: spec.id, targetVersion: spec.version,
    eventType, status, actor, comment: comment || null, occurredAt: new Date().toISOString(),
  };
}

// ── main thread ⇄ UI 메시지 처리 ──

type IncomingMessage =
  | { type: "LOAD_SPEC"; spec: unknown }
  | { type: "APPLY" }
  | { type: "EXPORT_REVIEW"; eventType: ReviewExport["eventType"]; actor: string; comment: string }
  | { type: "EXPORT_REGISTRY"; options: RegistryExportOptions }
  | { type: "REPAIR_ACCESSIBILITY_STATES"; registry: RegistryV2 }
  | { type: "REPAIR_SELECTED_CHECKBOX" }
  | { type: "FOCUS_ERROR"; index: number };

let currentSpec: DesignSystemSpec | undefined;
let validationIssues: ValidationIssue[] = [];
let validationDesignSystemId: string | undefined;

figma.ui.onmessage = async (message: IncomingMessage) => {
  try {
    if (message.type === "LOAD_SPEC") {
      const { errors, parsed } = validateSpec(message.spec);
      if (errors.length > 0 || !parsed) {
        currentSpec = undefined;
        validationIssues = errors;
        validationDesignSystemId = typeof message.spec === "object"
          && message.spec !== null
          && typeof (message.spec as Record<string, unknown>).id === "string"
          ? (message.spec as Record<string, unknown>).id as string
          : undefined;
        figma.ui.postMessage({ type: "VALIDATION_ERROR", errors });
        return;
      }
      // documentAccess: dynamic-page 환경에서는 전체 문서 탐색 전에 명시적으로 로드해야 한다.
      await figma.loadAllPagesAsync();
      currentSpec = parsed;
      validationIssues = [];
      validationDesignSystemId = parsed.id;
      const reviewState = readReviewState(parsed);
      writeReviewState(reviewState);
      const diff = await computeDiff(parsed);
      figma.ui.postMessage({
        type: "DIFF_READY",
        diff,
        reviewStatus: reviewState.status,
        summary: `${parsed.name} v${parsed.version} — 컴포넌트 ${parsed.components.length}개, 패턴 ${parsed.patterns.length}개, 토큰 ${parsed.tokens.length}개`,
      });
      return;
    }

    if (message.type === "APPLY") {
      if (!currentSpec) throw new Error("먼저 DesignSystemSpec을 불러와 검증하세요.");
      const spec = currentSpec;
      await applyTokens(spec);
      await applyVariableCollections(spec);
      const existingSets = findTaggedComponentSets(spec.id);
      for (const def of spec.components) {
        await applyComponent(spec, def, existingSets);
      }
      const refreshedSets = findTaggedComponentSets(spec.id);
      for (const pattern of spec.patterns) {
        await applyPattern(spec, pattern, refreshedSets);
      }
      const diff = await computeDiff(spec);
      await buildPreviewPage(spec, diff);
      const reviewState: StoredReviewState = {
        designSystemId: spec.id,
        version: spec.version,
        status: "IN_REVIEW",
        occurredAt: new Date().toISOString(),
      };
      writeReviewState(reviewState);
      figma.ui.postMessage({
        type: "APPLY_RESULT",
        message: `적용 완료: 컴포넌트 ${spec.components.length}개, 패턴 ${spec.patterns.length}개, 토큰 ${spec.tokens.length}개. Preview 페이지에서 확인하세요.`,
        diff,
        reviewStatus: reviewState.status,
      });
      return;
    }

    if (message.type === "EXPORT_REVIEW") {
      if (!currentSpec) throw new Error("먼저 DesignSystemSpec을 불러와 검증하세요.");
      if (!message.actor.trim()) throw new Error("검토자 이름은 필수입니다.");
      const current = readReviewState(currentSpec);
      const nextStatus = transitionReviewStatus(current.status, message.eventType);
      const occurredAt = new Date().toISOString();
      const next: StoredReviewState = {
        designSystemId: currentSpec.id,
        version: currentSpec.version,
        status: nextStatus,
        actor: message.actor.trim(),
        comment: message.comment.trim() || null,
        occurredAt,
      };
      writeReviewState(next);
      const review = buildReviewExport(
        currentSpec, message.eventType, nextStatus, message.actor.trim(), message.comment);
      figma.ui.postMessage({ type: "REVIEW_EXPORTED", review, reviewStatus: nextStatus });
      return;
    }

    if (message.type === "EXPORT_REGISTRY") {
      if (!currentSpec) throw new Error("먼저 DesignSystemSpec을 불러와 검증하세요.");
      if (readReviewState(currentSpec).status !== "APPROVED") {
        throw new Error("사람 검토 상태가 APPROVED인 Design System만 Registry로 내보낼 수 있습니다.");
      }
      const registry = await buildRegistryExport(currentSpec, message.options);
      const statuses = [
        ...Object.values(registry.components).map(entry => entry.publishStatus),
        ...Object.values(registry.variables).map(entry => entry.publishStatus),
      ];
      figma.ui.postMessage({
        type: "REGISTRY_EXPORTED",
        registry,
        allCurrent: statuses.length > 0 && statuses.every(status => status === "CURRENT"),
      });
      return;
    }

    if (message.type === "REPAIR_ACCESSIBILITY_STATES") {
      const result = await repairAccessibilityStates(message.registry);
      figma.ui.postMessage({ type: "ACCESSIBILITY_STATES_REPAIRED", ...result });
      return;
    }

    if (message.type === "REPAIR_SELECTED_CHECKBOX") {
      const result = repairSelectedCheckboxComponentSet();
      figma.ui.postMessage({ type: "CHECKBOX_REPAIRED", message: `Checkbox Component Set을 ${result.created}개 계약 Variant로 재구성했습니다. 새 Component Set Key: ${result.key}` });
      return;
    }

    if (message.type === "FOCUS_ERROR") {
      const issue = validationIssues[message.index];
      if (!issue) throw new Error("선택한 검증 오류를 찾을 수 없습니다.");
      const target = validationDesignSystemId && issue.targetId
        ? findTaggedComponentSets(validationDesignSystemId).get(issue.targetId)
        : undefined;
      if (target) {
        const page = target.parent?.type === "PAGE" ? target.parent as PageNode : undefined;
        if (page) figma.currentPage = page;
        figma.currentPage.selection = [target];
        figma.viewport.scrollAndZoomIntoView([target]);
      }
      figma.ui.postMessage({
        type: "ERROR_FOCUSED",
        found: Boolean(target),
        path: issue.path,
        message: issue.message,
      });
    }
  } catch (error) {
    const detail = normalizePluginError(error);
    figma.ui.postMessage({ type: "ERROR", ...detail });
  }
};
