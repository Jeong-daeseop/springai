import {
  describeLayoutAnnotations,
  flattenSpec,
  mappedProperties,
  planFallback,
  previewLegacyMigration,
  reconcile,
  registryFor,
  selectVariantName,
  validateBundle,
} from "./core";
import type {
  ComponentRegistry,
  ExistingLogicalNode,
  ExportIssue,
  FigmaExportBundle,
  FigmaNodeSpec,
  GenerationReport,
  LegacyFrameNode,
  MigrationPreview,
  ReconciliationChange,
  RegistryEntry,
  SyncMode,
} from "./types";

const DATA_SCREEN_ID = "figmaScreenSpec.screenId";
const DATA_SCREEN_VERSION = "figmaScreenSpec.screenVersion";
const DATA_LOGICAL_ID = "figmaScreenSpec.logicalNodeId";
const DATA_LOGICAL_TYPE = "figmaScreenSpec.logicalType";
const DATA_COMPONENT_SET_KEY = "figmaScreenSpec.componentSetKey";
const DATA_MANAGED_PROPERTIES = "figmaScreenSpec.managedProperties";
const DATA_ARCHIVED = "figmaScreenSpec.archived";
const DATA_FALLBACK = "figmaScreenSpec.fallback";
const DATA_MIGRATION_BACKUP = "figmaScreenSpec.migrationBackup";

figma.showUI(__html__, { width: 440, height: 720 });

type Pending = { bundle: FigmaExportBundle; issues: ExportIssue[] };
type Message =
  | { type: "LOAD_BUNDLE"; bundle: unknown }
  | { type: "FETCH_BUNDLE"; baseUrl: string; screenId: string; version?: number; apiKey?: string; token?: string }
  | { type: "APPLY"; mode: Exclude<SyncMode, "PREVIEW"> }
  | { type: "PREVIEW_MIGRATION" }
  | { type: "APPLY_MIGRATION" }
  | { type: "CLOSE" };

let pending: Pending | undefined;
let pendingMigration: { rootId: string; preview: MigrationPreview } | undefined;

figma.ui.onmessage = async (message: Message) => {
  try {
    if (message.type === "CLOSE") {
      figma.closePlugin();
      return;
    }
    if (message.type === "LOAD_BUNDLE") {
      await loadBundleAndPreview(message.bundle);
      return;
    }
    if (message.type === "FETCH_BUNDLE") {
      const bundle = await fetchBundleWithRetry(message);
      await loadBundleAndPreview(bundle);
      return;
    }
    if (message.type === "PREVIEW_MIGRATION") {
      if (!pending) throw new Error("먼저 FigmaExportBundle을 불러오세요.");
      const root = selectedLegacyRoot();
      const preview = previewLegacyMigration(pending.bundle, legacyFrames(root));
      pendingMigration = { rootId: root.id, preview };
      figma.ui.postMessage({ type: "MIGRATION_PREVIEW_READY", preview });
      return;
    }
    if (message.type === "APPLY_MIGRATION") {
      if (!pending || !pendingMigration) throw new Error("먼저 Migration Preview를 실행하세요.");
      if (!pendingMigration.preview.canApply) {
        throw new Error("사람 확인이 필요한 매핑이 있어 Migration을 적용할 수 없습니다.");
      }
      const report = await applyLegacyMigration(
        pending.bundle, pendingMigration.rootId, pendingMigration.preview);
      figma.ui.postMessage({ type: "MIGRATION_RESULT", report });
      return;
    }
    if (message.type === "APPLY") {
      if (!pending) throw new Error("먼저 FigmaExportBundle을 불러오세요.");
      if (pending.issues.some(issue => issue.severity === "FATAL" || issue.severity === "ERROR")) {
        throw new Error("검증 오류가 있는 Bundle은 적용할 수 없습니다.");
      }
      if (pending.bundle.figmaScreenSpec.status !== "APPROVED") {
        throw new Error("APPROVED ScreenSpecification만 MERGE/REPLACE할 수 있습니다.");
      }
      const report = await applyBundle(pending.bundle, message.mode, pending.issues);
      figma.ui.postMessage({ type: "APPLY_RESULT", report });
    }
  } catch (error) {
    figma.ui.postMessage({
      type: "ERROR",
      message: error instanceof Error ? error.message : "알 수 없는 오류",
    });
  }
};

async function loadBundleAndPreview(rawBundle: unknown): Promise<void> {
  await figma.loadAllPagesAsync();
  const validated = validateBundle(rawBundle);
  if (!validated.parsed) {
    pending = undefined;
    figma.ui.postMessage({ type: "VALIDATION_ERROR", issues: validated.issues });
    return;
  }
  pending = { bundle: validated.parsed, issues: validated.issues };
  pendingMigration = undefined;
  const screen = validated.parsed.figmaScreenSpec;
  const existing = findExistingNodes(screen.screenId);
  const changes = reconcile(screen.content, existing);
  figma.ui.postMessage({
    type: "PREVIEW_READY",
    summary: `${screen.name} v${screen.screenVersion} · ${screen.screenType} · 논리 노드 ${changes.length}개`,
    changes,
    issues: validated.issues,
    canApply: screen.status === "APPROVED"
      && !validated.issues.some(issue => issue.severity === "FATAL" || issue.severity === "ERROR"),
  });
}

/**
 * R5-002/R6-012: DEC-10=REST 경로. `X-API-Key`(장기) 또는 `Authorization: Bearer`(단기 토큰,
 * `POST /api/figma/tokens`로 발급) 중 하나로 Spring `GET /api/figma/screens/{id}/download`를
 * 호출한다. 5xx·네트워크 오류만 짧은 backoff로 재시도하고, 인증 오류(401)는 즉시 포기해
 * 잘못된 값으로 계속 재시도하지 않는다. 모든 시도가 실패하면 파일 업로드로 되돌아가라는
 * 안내를 포함한 오류를 던진다(오프라인 fallback).
 */
async function fetchBundleWithRetry(
  request: { baseUrl: string; screenId: string; version?: number; apiKey?: string; token?: string },
): Promise<unknown> {
  const trimmedBase = request.baseUrl.replace(/\/+$/, "");
  const url = `${trimmedBase}/api/figma/screens/${encodeURIComponent(request.screenId)}/download`
    + (request.version ? `?version=${request.version}` : "");
  const headers: Record<string, string> = {};
  if (request.token) headers.Authorization = `Bearer ${request.token}`;
  else if (request.apiKey) headers["X-API-Key"] = request.apiKey;
  else throw new Error("API Key 또는 단기 토큰 중 하나를 입력하세요.");

  const retryDelaysMs = [0, 300, 900];
  let lastMessage = "알 수 없는 오류";
  for (let attempt = 0; attempt < retryDelaysMs.length; attempt++) {
    if (retryDelaysMs[attempt] > 0) await sleep(retryDelaysMs[attempt]);
    try {
      const response = await fetch(url, { headers });
      if (response.status === 401 || response.status === 403) {
        throw new Error("인증에 실패했습니다. API Key 또는 단기 토큰을 확인하세요.");
      }
      if (response.status >= 500) {
        lastMessage = `서버 오류(${response.status})`;
        continue;
      }
      if (!response.ok) {
        throw new Error(`서버가 요청을 거부했습니다(${response.status}).`);
      }
      return await response.json();
    } catch (error) {
      const isAuthFailure = error instanceof Error && error.message.includes("인증");
      if (isAuthFailure) throw error;
      lastMessage = error instanceof Error ? error.message : String(error);
    }
  }
  throw new Error(
    `서버에서 Bundle을 가져오지 못했습니다(오프라인이거나 서버 응답 없음: ${lastMessage}). `
    + `.figma-export-bundle.json 파일을 직접 선택해 진행할 수 있습니다.`,
  );
}

function sleep(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function selectedLegacyRoot(): FrameNode {
  if (figma.currentPage.selection.length !== 1
      || figma.currentPage.selection[0].type !== "FRAME") {
    throw new Error("Migration할 기존 Root Frame 하나를 선택하세요.");
  }
  return figma.currentPage.selection[0] as FrameNode;
}

function legacyFrames(root: FrameNode): LegacyFrameNode[] {
  return [root, ...root.findAll(node => node.type === "FRAME")]
    .filter((node): node is FrameNode => node.type === "FRAME")
    .map(node => ({
      nodeId: node.id,
      name: node.name,
      nodeType: node.type,
      logicalNodeId: node.getPluginData(DATA_LOGICAL_ID) || null,
      hasLocalInstance: node.children.some(child =>
        child.type === "INSTANCE" && child.mainComponent?.remote === false),
    }));
}

async function applyLegacyMigration(
  bundle: FigmaExportBundle,
  rootId: string,
  preview: MigrationPreview,
): Promise<{
  screenId: string;
  screenVersion: number;
  success: boolean;
  backupNodeId: string;
  appliedCount: number;
  replacedInstanceCount: number;
  failedCount: number;
  issues: ExportIssue[];
  completedAt: string;
}> {
  const rootNode = await figma.getNodeByIdAsync(rootId);
  if (!rootNode || rootNode.type !== "FRAME") throw new Error("선택했던 Legacy Root Frame을 찾을 수 없습니다.");
  const root = rootNode as FrameNode;
  const backup = root.clone();
  backup.name = `Migration Backup · ${root.name} · ${new Date().toISOString()}`;
  backup.visible = false;
  backup.setPluginData(DATA_MIGRATION_BACKUP, "true");
  backup.setPluginData(DATA_SCREEN_ID, preview.screenId);

  const registry = registryFor(bundle);
  const issues: ExportIssue[] = [];
  const imported = await preloadComponents(bundle.figmaScreenSpec.content, registry, issues);
  const specs = new Map(flattenSpec(bundle.figmaScreenSpec.content)
    .map(({node}) => [node.logicalNodeId, node]));
  let appliedCount = 0;
  let replacedInstanceCount = 0;
  let failedCount = 0;
  for (const operation of preview.operations) {
    if (!operation.nodeId || operation.action === "MANUAL_REVIEW") continue;
    const existing = await figma.getNodeByIdAsync(operation.nodeId);
    const spec = specs.get(operation.logicalNodeId);
    if (!existing || existing.type !== "FRAME" || !spec) {
      failedCount++;
      issues.push({
        code: "MIGRATION_NODE_NOT_FOUND",
        severity: "ERROR",
        message: `Migration 대상 Frame 또는 Spec을 찾을 수 없습니다: ${operation.logicalNodeId}`,
        logicalNodeId: operation.logicalNodeId,
      });
      continue;
    }
    const frame = existing as FrameNode;
    frame.setPluginData(DATA_SCREEN_ID, preview.screenId);
    frame.setPluginData(DATA_SCREEN_VERSION, String(preview.screenVersion));
    frame.setPluginData(DATA_LOGICAL_ID, operation.logicalNodeId);
    frame.setPluginData(DATA_LOGICAL_TYPE, operation.logicalType);
    frame.setPluginData(DATA_ARCHIVED, "false");
    if (operation.action === "ASSIGN_AND_REPLACE") {
      const entry = registry.components[operation.logicalType];
      if (!entry) {
        failedCount++;
        issues.push({
          code: "MIGRATION_REGISTRY_ENTRY_MISSING",
          severity: "ERROR",
          message: `Registry 항목을 찾을 수 없습니다: ${operation.logicalType}`,
          logicalNodeId: operation.logicalNodeId,
        });
        continue;
      }
      await ensurePublishedInstance(frame, spec, entry, imported, issues);
      replacedInstanceCount++;
    }
    appliedCount++;
  }
  const success = failedCount === 0
    && !issues.some(issue => issue.severity === "FATAL" || issue.severity === "ERROR");
  return {
    screenId: preview.screenId,
    screenVersion: preview.screenVersion,
    success,
    backupNodeId: backup.id,
    appliedCount,
    replacedInstanceCount,
    failedCount,
    issues,
    completedAt: new Date().toISOString(),
  };
}

function findScreenRoot(screenId: string): FrameNode | undefined {
  return figma.root.findAll(node =>
    node.type === "FRAME"
    && node.getPluginData(DATA_SCREEN_ID) === screenId
    && node.getPluginData(DATA_ARCHIVED) !== "true")[0] as FrameNode | undefined;
}

function logicalChildren(parent: FrameNode): FrameNode[] {
  return parent.children.filter(node =>
    node.type === "FRAME" && node.getPluginData(DATA_LOGICAL_ID)) as FrameNode[];
}

function findExistingNodes(screenId: string): ExistingLogicalNode[] {
  const root = findScreenRoot(screenId);
  if (!root) return [];
  const result: ExistingLogicalNode[] = [];
  const visit = (node: FrameNode, parentLogicalNodeId: string | null, order: number) => {
    const instance = node.children.find(child => child.type === "INSTANCE") as InstanceNode | undefined;
    result.push({
      logicalNodeId: node.getPluginData(DATA_LOGICAL_ID),
      logicalType: node.getPluginData(DATA_LOGICAL_TYPE),
      parentLogicalNodeId,
      order,
      detached: instance ? !instance.getPluginData(DATA_COMPONENT_SET_KEY) : false,
    });
    logicalChildren(node).forEach((child, index) =>
      visit(child, node.getPluginData(DATA_LOGICAL_ID), index));
  };
  visit(root, null, 0);
  return result;
}

function indexExisting(screenId: string): Map<string, FrameNode> {
  const index = new Map<string, FrameNode>();
  const root = findScreenRoot(screenId);
  if (!root) return index;
  for (const node of [root, ...root.findAll(child => child.type === "FRAME")]) {
    if (node.type !== "FRAME") continue;
    const logicalId = node.getPluginData(DATA_LOGICAL_ID);
    if (logicalId && node.getPluginData(DATA_ARCHIVED) !== "true") index.set(logicalId, node);
  }
  return index;
}

async function applyBundle(
  bundle: FigmaExportBundle,
  mode: Exclude<SyncMode, "PREVIEW">,
  validationIssues: ExportIssue[],
): Promise<GenerationReport> {
  const startedAt = new Date().toISOString();
  const screen = bundle.figmaScreenSpec;
  const registry = registryFor(bundle);
  const changes: ReconciliationChange[] = [];
  const issues = [...validationIssues];
  const reportCounts = { reused: 0, created: 0, archived: 0, fallback: 0 };
  const importedComponents = await preloadComponents(screen.content, registry, issues);
  let existing = indexExisting(screen.screenId);
  const existingRoot = findScreenRoot(screen.screenId);
  const origin = existingRoot ? { x: existingRoot.x, y: existingRoot.y } : { x: 0, y: 0 };

  if (mode === "REPLACE" && existingRoot) {
    archiveNode(existingRoot, screen.screenId);
    reportCounts.archived++;
    existing = new Map();
  }

  const root = await syncNode(
    screen.content,
    figma.currentPage,
    existing,
    registry,
    importedComponents,
    screen.screenId,
    screen.screenVersion,
    changes,
    issues,
    reportCounts,
  );
  root.x = origin.x;
  root.y = origin.y;

  for (const stale of existing.values()) {
    if (stale === root || stale.getPluginData(DATA_ARCHIVED) === "true") continue;
    const parentFrame = stale.parent?.type === "FRAME" ? stale.parent as FrameNode : undefined;
    const parentLogicalId = parentFrame?.getPluginData(DATA_LOGICAL_ID);
    if (parentLogicalId && existing.has(parentLogicalId)) continue;
    archiveNode(stale, screen.screenId);
    reportCounts.archived++;
    changes.push({
      logicalNodeId: stale.getPluginData(DATA_LOGICAL_ID),
      logicalType: stale.getPluginData(DATA_LOGICAL_TYPE),
      changeType: "ARCHIVE",
      detail: "새 Spec에서 제거되어 Archive로 이동",
    });
  }

  figma.currentPage.selection = [root];
  figma.viewport.scrollAndZoomIntoView([root]);
  const fatal = issues.some(issue => issue.severity === "FATAL" || issue.severity === "ERROR");
  return {
    reportId: `figma-${screen.screenId}-v${screen.screenVersion}-${Date.now()}`,
    status: fatal ? "FAILED" : "SUCCESS",
    figmaScreenSpec: screen,
    generatedAt: new Date().toISOString(),
    screenId: screen.screenId,
    screenVersion: screen.screenVersion,
    mode,
    startedAt,
    completedAt: new Date().toISOString(),
    success: !fatal,
    reusedInstanceCount: reportCounts.reused,
    createdInstanceCount: reportCounts.created,
    archivedNodeCount: reportCounts.archived,
    fallbackCount: reportCounts.fallback,
    changes,
    issues,
  };
}

async function syncNode(
  spec: FigmaNodeSpec,
  parent: PageNode | FrameNode,
  existing: Map<string, FrameNode>,
  registry: ComponentRegistry,
  importedComponents: Map<string, ComponentSetNode>,
  screenId: string,
  screenVersion: number,
  changes: ReconciliationChange[],
  issues: ExportIssue[],
  counts: { reused: number; created: number; archived: number; fallback: number },
): Promise<FrameNode> {
  let wrapper = existing.get(spec.logicalNodeId);
  const typeChanged = wrapper && wrapper.getPluginData(DATA_LOGICAL_TYPE) !== spec.type;
  if (wrapper && typeChanged) {
    archiveNode(wrapper, screenId);
    counts.archived++;
    existing.delete(spec.logicalNodeId);
    wrapper = undefined;
  }

  if (!wrapper) {
    wrapper = figma.createFrame();
    counts.created++;
    changes.push(change(spec, "ADD", typeChanged ? "타입 변경으로 신규 생성" : "신규 생성"));
  } else {
    counts.reused++;
    existing.delete(spec.logicalNodeId);
    changes.push(change(spec, "REUSE", "기존 Wrapper와 Instance 재사용"));
  }
  configureWrapper(wrapper, spec, screenId, screenVersion);
  parent.appendChild(wrapper);

  const entry = registry.components[spec.type];
  if (entry) {
    removeFallbackPlaceholder(wrapper);
    await ensurePublishedInstance(wrapper, spec, entry, importedComponents, issues);
  } else {
    const plan = planFallback(spec, registry);
    if (plan) {
      await ensureFallbackPlaceholder(wrapper, plan);
      issues.push(plan.issue);
      counts.fallback++;
    }
  }
  for (const child of spec.children) {
    await syncNode(child, wrapper, existing, registry, importedComponents, screenId, screenVersion, changes, issues, counts);
  }
  return wrapper;
}

function configureWrapper(
  wrapper: FrameNode,
  spec: FigmaNodeSpec,
  screenId: string,
  screenVersion: number,
): void {
  const annotation = describeLayoutAnnotations(spec.properties);
  wrapper.name = `${spec.logicalNodeId} · ${spec.type}${annotation.nameSuffix}`;
  wrapper.setPluginData(DATA_SCREEN_ID, screenId);
  wrapper.setPluginData(DATA_SCREEN_VERSION, String(screenVersion));
  wrapper.setPluginData(DATA_LOGICAL_ID, spec.logicalNodeId);
  wrapper.setPluginData(DATA_LOGICAL_TYPE, spec.type);
  wrapper.setPluginData(DATA_ARCHIVED, "false");
  for (const [key, value] of Object.entries(annotation.pluginData)) {
    wrapper.setPluginData(`figmaScreenSpec.layout.${key}`, value);
  }
  wrapper.layoutMode = spec.type === "egov.actionArea" ? "HORIZONTAL" : "VERTICAL";
  wrapper.primaryAxisSizingMode = "AUTO";
  wrapper.counterAxisSizingMode = "AUTO";
  wrapper.itemSpacing = spec.nodeType === "PAGE" ? 24 : 12;
  wrapper.paddingTop = wrapper.paddingBottom = spec.nodeType === "PAGE" ? 32 : 12;
  wrapper.paddingLeft = wrapper.paddingRight = spec.nodeType === "PAGE" ? 32 : 12;
  wrapper.clipsContent = false;
}

async function ensurePublishedInstance(
  wrapper: FrameNode,
  spec: FigmaNodeSpec,
  entry: RegistryEntry,
  importedComponents: Map<string, ComponentSetNode>,
  issues: ExportIssue[],
): Promise<void> {
  try {
    const componentSet = importedComponents.get(entry.componentSetKey);
    if (!componentSet) throw new Error("사전 import된 Component Set을 찾을 수 없습니다.");
    const properties = mappedProperties(spec.properties, entry);
    await resolveInstanceSwapProperties(entry, properties, spec, issues);
    const variantName = selectVariantName(properties, entry);
    const component = componentSet.children.find(child =>
      child.type === "COMPONENT" && (!variantName || child.name === variantName)) as ComponentNode | undefined
      ?? componentSet.children.find(child => child.type === "COMPONENT") as ComponentNode | undefined;
    if (!component) throw new Error("Component Set에 사용할 Variant가 없습니다.");

    let instance = wrapper.children.find(child =>
      child.type === "INSTANCE"
      && child.getPluginData(DATA_COMPONENT_SET_KEY) === entry.componentSetKey) as InstanceNode | undefined;
    if (!instance) {
      for (const old of wrapper.children.filter(child => child.type === "INSTANCE")) old.remove();
      instance = component.createInstance();
      wrapper.insertChild(0, instance);
      instance.setPluginData(DATA_COMPONENT_SET_KEY, entry.componentSetKey);
    }
    instance.name = `${spec.type} · Published Instance`;
    applyOwnedProperties(instance, properties);
  } catch (error) {
    await ensureFallbackPlaceholder(wrapper, {
      label: `⚠ ${spec.type} (Published Instance import 실패)`,
    });
    issues.push({
      code: "COMPONENT_IMPORT_FALLBACK",
      severity: "WARNING",
      message: `${spec.type} import 실패: ${error instanceof Error ? error.message : "알 수 없는 오류"}`,
      logicalNodeId: spec.logicalNodeId,
    });
  }
}

/**
 * R5-014: mappedProperties()는 INSTANCE_SWAP 속성도 VARIANT처럼 entry.properties의
 * values 표를 통해 "논리값 → componentKey"까지만 해석한다. Figma의 setProperties()는
 * INSTANCE_SWAP에 componentKey가 아니라 실제 import된 노드 id를 요구하므로, 여기서
 * 각 INSTANCE_SWAP componentKey를 import해 id로 치환한다. import 실패는 인스턴스 생성
 * 자체를 막지 않고 WARNING으로만 보고한다(기본 슬롯 없이 진행).
 */
async function resolveInstanceSwapProperties(
  entry: RegistryEntry,
  properties: Record<string, string | boolean>,
  spec: FigmaNodeSpec,
  issues: ExportIssue[],
): Promise<void> {
  for (const mapping of Object.values(entry.properties ?? {})) {
    if (mapping.type !== "INSTANCE_SWAP") continue;
    const componentKey = properties[mapping.figmaProperty];
    if (typeof componentKey !== "string" || !componentKey) continue;
    try {
      const swapComponent = await figma.importComponentByKeyAsync(componentKey);
      properties[mapping.figmaProperty] = swapComponent.id;
    } catch (error) {
      delete properties[mapping.figmaProperty];
      issues.push({
        code: "INSTANCE_SWAP_IMPORT_FAILED",
        severity: "WARNING",
        message: `Instance Swap 대상 Component를 import하지 못했습니다(${mapping.figmaProperty}=${componentKey}): `
          + `${error instanceof Error ? error.message : "알 수 없는 오류"}`,
        logicalNodeId: spec.logicalNodeId,
      });
    }
  }
}

const FALLBACK_STROKE_COLOR: RGB = { r: 0.88, g: 0.35, b: 0.13 };
const FALLBACK_FILL_COLOR: RGB = { r: 1, g: 0.95, b: 0.89 };
const FALLBACK_TEXT_COLOR: RGB = { r: 0.55, g: 0.22, b: 0.06 };

/**
 * R5-016: Registry에 없는 선택 Component는 Published Instance 대신 대시 테두리 +
 * 경고색 Placeholder를 만들어 정식 Instance와 한눈에 구분되게 한다. 재실행 시 기존
 * Placeholder를 재사용해 중복 생성하지 않는다.
 */
async function ensureFallbackPlaceholder(wrapper: FrameNode, plan: { label: string }): Promise<void> {
  for (const child of wrapper.children) {
    if (child.type === "INSTANCE") child.remove();
  }
  let placeholder = wrapper.children.find(child =>
    child.type === "FRAME" && child.getPluginData(DATA_FALLBACK) === "true") as FrameNode | undefined;
  if (!placeholder) {
    placeholder = figma.createFrame();
    placeholder.setPluginData(DATA_FALLBACK, "true");
    placeholder.layoutMode = "HORIZONTAL";
    placeholder.primaryAxisSizingMode = "AUTO";
    placeholder.counterAxisSizingMode = "AUTO";
    placeholder.paddingTop = placeholder.paddingBottom = 6;
    placeholder.paddingLeft = placeholder.paddingRight = 10;
    placeholder.cornerRadius = 4;
    placeholder.fills = [{ type: "SOLID", color: FALLBACK_FILL_COLOR }];
    placeholder.strokes = [{ type: "SOLID", color: FALLBACK_STROKE_COLOR }];
    placeholder.strokeWeight = 1;
    placeholder.dashPattern = [4, 3];
    wrapper.insertChild(0, placeholder);
  }
  placeholder.name = `FALLBACK · ${plan.label}`;

  let label = placeholder.children.find(child => child.type === "TEXT") as TextNode | undefined;
  if (!label) {
    label = figma.createText();
    placeholder.appendChild(label);
  }
  await figma.loadFontAsync(label.fontName as FontName);
  label.characters = plan.label;
  label.fontSize = 11;
  label.fills = [{ type: "SOLID", color: FALLBACK_TEXT_COLOR }];
}

function removeFallbackPlaceholder(wrapper: FrameNode): void {
  for (const child of wrapper.children) {
    if (child.type === "FRAME" && child.getPluginData(DATA_FALLBACK) === "true") child.remove();
  }
}

async function preloadComponents(
  root: FigmaNodeSpec,
  registry: ComponentRegistry,
  issues: ExportIssue[],
): Promise<Map<string, ComponentSetNode>> {
  const keys = new Set<string>();
  const visit = (node: FigmaNodeSpec) => {
    const key = registry.components[node.type]?.componentSetKey;
    if (key) keys.add(key);
    node.children.forEach(visit);
  };
  visit(root);
  const imported = new Map<string, ComponentSetNode>();
  for (const key of keys) {
    const logicalType = Object.entries(registry.components).find(([, entry]) => entry.componentSetKey === key)?.[0];
    try {
      imported.set(key, await figma.importComponentSetByKeyAsync(key));
    } catch {
      const localSet = logicalType ? findLocalComponentSet(registry.components[logicalType]) : null;
      if (localSet) {
        imported.set(key, localSet);
        issues.push({
          code: "COMPONENT_SET_KEY_PLACEHOLDER_FALLBACK",
          severity: "WARNING",
          message: `${logicalType}의 componentSetKey(${key})를 import할 수 없어 현재 Figma 파일의 동일 이름 Component Set으로 대체했습니다.`,
          logicalNodeId: null,
        });
      }
    }
  }
  return imported;
}

function findLocalComponentSet(entry: RegistryEntry): ComponentSetNode | null {
  const targetName = normalizeLookupName(entry.componentName ?? "");
  if (!targetName) return null;
  const candidates = figma.root.findAll(node =>
    node.type === "COMPONENT_SET" && normalizeLookupName(node.name) === targetName) as ComponentSetNode[];
  return candidates[0] ?? null;
}

function normalizeLookupName(value: string): string {
  return value.trim().toLowerCase().replace(/[\s_-]+/g, "");
}

function applyOwnedProperties(
  instance: InstanceNode,
  mapped: Record<string, string | boolean>,
): void {
  const previous = parseManagedProperties(instance.getPluginData(DATA_MANAGED_PROPERTIES));
  const next = { ...previous };
  const updates: Record<string, string | boolean> = {};
  for (const [baseName, value] of Object.entries(mapped)) {
    const actualKey = Object.keys(instance.componentProperties)
      .find(key => key.split("#")[0].toLowerCase() === baseName.toLowerCase()) ?? baseName;
    const current = instance.componentProperties[actualKey]?.value;
    const userOverrode = previous[actualKey] !== undefined && current !== previous[actualKey];
    if (userOverrode) continue;
    updates[actualKey] = value;
    next[actualKey] = value;
  }
  if (Object.keys(updates).length > 0) instance.setProperties(updates);
  instance.setPluginData(DATA_MANAGED_PROPERTIES, JSON.stringify(next));
}

function parseManagedProperties(raw: string): Record<string, string | boolean> {
  if (!raw) return {};
  try {
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch {
    return {};
  }
}

function archiveNode(node: FrameNode, screenId: string): void {
  const archive = ensureArchiveFrame(screenId);
  node.setPluginData(DATA_ARCHIVED, "true");
  node.opacity = 0.45;
  archive.appendChild(node);
}

function ensureArchiveFrame(screenId: string): FrameNode {
  const existing = figma.currentPage.children.find(node =>
    node.type === "FRAME" && node.name === `🗄 Removed — ${screenId}`) as FrameNode | undefined;
  if (existing) return existing;
  const archive = figma.createFrame();
  archive.name = `🗄 Removed — ${screenId}`;
  archive.layoutMode = "VERTICAL";
  archive.primaryAxisSizingMode = "AUTO";
  archive.counterAxisSizingMode = "AUTO";
  archive.itemSpacing = 16;
  archive.paddingTop = archive.paddingBottom = 24;
  archive.paddingLeft = archive.paddingRight = 24;
  archive.x = 1800;
  archive.y = 0;
  return archive;
}

function change(
  spec: FigmaNodeSpec,
  changeType: ReconciliationChange["changeType"],
  detail: string,
): ReconciliationChange {
  return { logicalNodeId: spec.logicalNodeId, logicalType: spec.type, changeType, detail };
}
