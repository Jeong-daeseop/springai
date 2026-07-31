import { sha256 } from "@noble/hashes/sha256";
import { bytesToHex } from "@noble/hashes/utils";
import {
  compareSnapshots,
  componentSnapshot,
  normalizePluginError,
  planComponentChange,
  transitionReviewStatus,
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

const PLUGIN_DATA_NS_ID = "designSystemId";
const PLUGIN_DATA_LOGICAL_ID = "logicalId";
const PLUGIN_DATA_CONTENT_HASH = "contentHash";
const PLUGIN_DATA_DEFINITION = "definitionSnapshot";
const PLUGIN_DATA_REVIEW = "reviewState";

figma.showUI(__html__, { width: 420, height: 680 });

// ── 결정론적 contentHash: 업데이트 필요 여부 판정에 사용 ──

async function contentHash(value: unknown): Promise<string> {
  const json = JSON.stringify(value, Object.keys(value as object).sort());
  return bytesToHex(sha256(new TextEncoder().encode(json)));
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

function findTaggedVariables(designSystemId: string): Map<string, Variable> {
  const result = new Map<string, Variable>();
  for (const collection of figma.variables.getLocalVariableCollections()) {
    for (const variableId of collection.variableIds) {
      const variable = figma.variables.getVariableById(variableId);
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
  const existingVars = findTaggedVariables(spec.id);
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

function findOrCreateCollection(name: string, modes: string[]): VariableCollection {
  const existing = figma.variables.getLocalVariableCollections().find(c => c.name === name);
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

function findOrCreateVariable(collection: VariableCollection, name: string, type: VariableResolvedDataType): Variable {
  for (const variableId of collection.variableIds) {
    const variable = figma.variables.getVariableById(variableId);
    if (variable && variable.name === name) return variable;
  }
  return figma.variables.createVariable(name, collection, type);
}

async function applyTokens(spec: DesignSystemSpec): Promise<void> {
  if (spec.tokens.length === 0) return;
  const collection = findOrCreateCollection("Foundation", ["Default"]);
  const defaultModeId = collection.modes[0].modeId;
  for (const token of spec.tokens) {
    const type = resolvedTypeFor(token.category);
    const variable = findOrCreateVariable(collection, token.name, type);
    variable.setValueForMode(defaultModeId, parseVariableValue(token.category, token.value));
    variable.setPluginData(PLUGIN_DATA_NS_ID, spec.id);
    variable.setPluginData(PLUGIN_DATA_LOGICAL_ID, token.name);
    variable.setPluginData(PLUGIN_DATA_CONTENT_HASH, await contentHash(token));
  }
}

async function applyVariableCollections(spec: DesignSystemSpec): Promise<void> {
  for (const vc of spec.variableCollections) {
    const collection = findOrCreateCollection(vc.name, vc.modes);
    const modeIdByName = new Map(collection.modes.map(m => [m.name, m.modeId]));
    for (const [variableName, valuesByMode] of Object.entries(vc.valuesByMode)) {
      const variable = findOrCreateVariable(collection, variableName, "STRING");
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

function buildVariantComponent(
  defName: string,
  combo: Record<string, string>,
  layout?: ComponentLayout | null,
): ComponentNode {
  const component = figma.createComponent();
  component.name = variantName(combo);
  component.resize(120, 40);
  const label = figma.createText();
  label.characters = `${defName} · ${variantName(combo)}`;
  component.appendChild(label);
  applyLayout(component, layout
    ?? { mode: "HORIZONTAL", paddingX: "16", paddingY: "12", gap: "8", alignment: "CENTER" });
  return component;
}

function applyNonVariantProperties(target: ComponentSetNode, properties: ComponentProperty[]): void {
  const existingDefs = target.componentPropertyDefinitions ?? {};
  for (const property of properties) {
    if (property.type === "VARIANT") continue;
    const alreadyDefined = Object.keys(existingDefs).some(key => key.split("#")[0] === property.name);
    if (alreadyDefined) continue;
    if (property.type === "BOOLEAN") {
      target.addComponentProperty(property.name, "BOOLEAN", property.defaultValue === "true");
    } else if (property.type === "TEXT") {
      target.addComponentProperty(property.name, "TEXT", property.defaultValue ?? "");
    } else if (property.type === "INSTANCE_SWAP") {
      target.addComponentProperty(property.name, "INSTANCE_SWAP", "");
    }
  }
}

async function applyComponent(spec: DesignSystemSpec, def: ComponentDefinition, existingSets: Map<string, ComponentSetNode>): Promise<void> {
  const existing = existingSets.get(def.id);
  if (!existing) {
    const combos = variantCombinations(def.variants);
    const variantComponents = combos.map(combo =>
      buildVariantComponent(def.name, combo, def.layout));
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
    const created = buildVariantComponent(def.name, combo, def.layout);
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

function propertyMappings(def: ComponentDefinition): ComponentRegistryExport["components"][string]["properties"] {
  const mappings: ComponentRegistryExport["components"][string]["properties"] = {};
  for (const property of def.properties) {
    const values = property.type === "VARIANT"
      ? Object.fromEntries((def.variants[property.name] ?? []).map(value => [value, value]))
      : {};
    mappings[property.name] = {
      figmaProperty: property.name,
      type: property.type,
      values,
    };
  }
  for (const [variantName, options] of Object.entries(def.variants)) {
    if (mappings[variantName]) continue;
    mappings[variantName] = {
      figmaProperty: variantName,
      type: "VARIANT",
      values: Object.fromEntries(options.map(value => [value, value])),
    };
  }
  return mappings;
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
  for (const collection of figma.variables.getLocalVariableCollections()) {
    const collectionStatus = await collection.getPublishStatusAsync();
    for (const variableId of collection.variableIds) {
      const variable = figma.variables.getVariableById(variableId);
      if (!variable || variable.getPluginData(PLUGIN_DATA_NS_ID) !== spec.id) continue;
      const logicalId = variable.getPluginData(PLUGIN_DATA_LOGICAL_ID);
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
