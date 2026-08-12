import {
  describeLayoutAnnotations,
  flattenSpec,
  generationStatus,
  previewLegacyMigration,
  reconcile,
  registryFor,
  runAtomicApply,
  validateBundle,
  visualRegressionStatus,
} from "./core";
import type {
  ComponentRegistry,
  BundleContractMode,
  ExistingLogicalNode,
  ExportIssue,
  FigmaExportBundle,
  FigmaNodeSpec,
  GenerationReport,
  LegacyFrameNode,
  MigrationPreview,
  ReconciliationChange,
  RegistryEntry,
  QualityGateResult,
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
const DATA_APPLY_STAGING = "figmaScreenSpec.applyStaging";
const DATA_APPLY_BACKUP = "figmaScreenSpec.applyBackup";
const DATA_COMPONENT_VARIANT_KEY = "figmaScreenSpec.componentVariantKey";
const DATA_VISUAL_BASELINE_HASH = "figmaScreenSpec.visualBaselineHash";

figma.showUI(__html__, { width: 440, height: 720 });

type Pending = { bundle: FigmaExportBundle; issues: ExportIssue[]; contractMode: BundleContractMode };
type ImportedComponent = ComponentSetNode | ComponentNode;
type ApplyCounts = { reused: number; created: number; archived: number; fallback: number };
type ApplyBackup = {
  existingRoot?: FrameNode;
  backupClone?: FrameNode;
  parent?: PageNode | FrameNode;
  index: number;
  x: number;
  y: number;
  visible: boolean;
  opacity: number;
  archived: string;
};
type ApplyStaging = { container: FrameNode; root?: FrameNode };
type Message =
  | { type: "LOAD_BUNDLE"; bundle: unknown }
  | { type: "FETCH_BUNDLE"; baseUrl: string; screenId: string; version?: number; apiKey?: string; token?: string }
  | { type: "APPLY"; mode: Exclude<SyncMode, "PREVIEW"> }
  | { type: "PREVIEW_MIGRATION" }
  | { type: "APPLY_MIGRATION" }
  | { type: "CLOSE" };

let pending: Pending | undefined;
let pendingMigration: { rootId: string; preview: MigrationPreview } | undefined;
let reportTarget: { baseUrl: string; apiKey?: string; token?: string } | undefined;

figma.ui.onmessage = async (message: Message) => {
  try {
    if (message.type === "CLOSE") {
      figma.closePlugin();
      return;
    }
    if (message.type === "LOAD_BUNDLE") {
      reportTarget = undefined;
      await loadBundleAndPreview(message.bundle);
      return;
    }
    if (message.type === "FETCH_BUNDLE") {
      const bundle = await fetchBundleWithRetry(message);
      reportTarget = { baseUrl: message.baseUrl, apiKey: message.apiKey, token: message.token };
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
      if (pending.contractMode !== "V2_APPLY") {
        throw new Error("figma-screen-spec-v1은 Migration Preview만 지원하며 적용할 수 없습니다.");
      }
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
      if (pending.contractMode !== "V2_APPLY") {
        throw new Error("일반 Apply는 figma-screen-spec-v2 Bundle만 지원합니다.");
      }
      if (pending.issues.some(issue => issue.severity === "FATAL" || issue.severity === "ERROR")) {
        throw new Error("검증 오류가 있는 Bundle은 적용할 수 없습니다.");
      }
      if (pending.bundle.figmaScreenSpec.status !== "APPROVED") {
        throw new Error("APPROVED ScreenSpecification만 MERGE/REPLACE할 수 있습니다.");
      }
      const report = await applyBundle(pending.bundle, message.mode, pending.issues);
      if (reportTarget) await uploadGenerationReport(reportTarget, report);
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
  if (!validated.contractMode) {
    pending = undefined;
    figma.ui.postMessage({ type: "VALIDATION_ERROR", issues: validated.issues });
    return;
  }
  pending = {
    bundle: validated.parsed,
    issues: validated.issues,
    contractMode: validated.contractMode,
  };
  pendingMigration = undefined;
  const screen = validated.parsed.figmaScreenSpec;
  const existing = findExistingNodes(screen.screenId);
  const changes = reconcile(screen.content, existing);
  figma.ui.postMessage({
    type: "PREVIEW_READY",
    summary: `${screen.name} v${screen.screenVersion} · ${screen.screenType} · 논리 노드 ${changes.length}개`
      + (validated.contractMode === "V1_MIGRATION_PREVIEW" ? " · v1 Migration Preview 전용" : " · v2 Apply 가능"),
    changes,
    issues: validated.issues,
    contractMode: validated.contractMode,
    canApply: validated.contractMode === "V2_APPLY"
      && screen.status === "APPROVED"
      && !validated.issues.some(issue => issue.severity === "FATAL" || issue.severity === "ERROR"),
  });
}

async function uploadGenerationReport(
  target: { baseUrl: string; apiKey?: string; token?: string },
  report: GenerationReport,
): Promise<void> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (target.token) headers.Authorization = `Bearer ${target.token}`;
  else if (target.apiKey) headers["X-API-Key"] = target.apiKey;
  const response = await fetch(`${target.baseUrl.replace(/\/+$/, "")}/api/figma/operations/reports`, {
    method: "POST", headers, body: JSON.stringify(report),
  });
  if (!response.ok) throw new Error(`Generation Report 업로드 실패(${response.status})`);
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
        lastMessage = await responseErrorMessage(response);
        continue;
      }
      if (!response.ok) {
        throw new Error(await responseErrorMessage(response));
      }
      return await response.json();
    } catch (error) {
      const isAuthFailure = error instanceof Error && error.message.includes("인증");
      if (isAuthFailure) throw error;
      lastMessage = readableError(error);
    }
  }
  throw new Error(
    `서버에서 Bundle을 가져오지 못했습니다(오프라인이거나 서버 응답 없음: ${lastMessage}). `
    + `.figma-export-bundle.json 파일을 직접 선택해 진행할 수 있습니다.`,
  );
}

async function responseErrorMessage(response: Response): Promise<string> {
  const redirect = response.headers.get("Location");
  let detail = "";
  try {
    const body = await response.clone().json() as Record<string, unknown>;
    detail = String(body.message ?? body.error ?? body.code ?? JSON.stringify(body));
  } catch {
    try { detail = (await response.clone().text()).slice(0, 300); } catch { detail = ""; }
  }
  return `HTTP ${response.status}${redirect ? ` → ${redirect}` : ""}${detail ? `: ${detail}` : ""}`;
}

function readableError(error: unknown): string {
  if (error instanceof Error) return error.message;
  if (typeof error === "string") return error;
  try { return JSON.stringify(error); } catch { return String(error); }
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
    && node.getPluginData(DATA_APPLY_STAGING) !== "true"
    && node.getPluginData(DATA_ARCHIVED) !== "true"
    && !hasScreenAncestor(node, screenId))[0] as FrameNode | undefined;
}

function hasScreenAncestor(node: BaseNode, screenId: string): boolean {
  let parent = node.parent;
  while (parent) {
    if (parent.type === "FRAME" && parent.getPluginData(DATA_SCREEN_ID) === screenId) return true;
    parent = parent.parent;
  }
  return false;
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

function indexRoot(root: FrameNode): Map<string, FrameNode> {
  const index = new Map<string, FrameNode>();
  for (const node of [root, ...root.findAll(child => child.type === "FRAME")]) {
    if (node.type !== "FRAME") continue;
    const logicalId = node.getPluginData(DATA_LOGICAL_ID);
    if (logicalId && node.getPluginData(DATA_ARCHIVED) !== "true") index.set(logicalId, node);
  }
  return index;
}

function createApplyBackup(existingRoot: FrameNode | undefined): ApplyBackup {
  if (!existingRoot) {
    return { index: -1, x: 0, y: 0, visible: true, opacity: 1, archived: "" };
  }
  const parent = existingRoot.parent;
  if (!parent || (parent.type !== "PAGE" && parent.type !== "FRAME")) {
    throw new Error("기존 Screen Root의 부모가 Page 또는 Frame이 아닙니다.");
  }
  const index = parent.children.indexOf(existingRoot);
  const backupClone = existingRoot.clone();
  backupClone.name = `Apply Backup · ${existingRoot.name} · ${new Date().toISOString()}`;
  backupClone.visible = false;
  markStagingTree(backupClone, "true");
  backupClone.setPluginData(DATA_APPLY_BACKUP, "true");
  return {
    existingRoot,
    backupClone,
    parent,
    index,
    x: existingRoot.x,
    y: existingRoot.y,
    visible: existingRoot.visible,
    opacity: existingRoot.opacity,
    archived: existingRoot.getPluginData(DATA_ARCHIVED),
  };
}

function createApplyStaging(screenId: string): ApplyStaging {
  const container = figma.createFrame();
  container.name = `⏳ Staging — ${screenId}`;
  container.visible = false;
  container.layoutMode = "VERTICAL";
  container.primaryAxisSizingMode = "AUTO";
  container.counterAxisSizingMode = "AUTO";
  container.setPluginData(DATA_APPLY_STAGING, "true");
  return { container };
}

function validateStagedRoot(
  root: FrameNode,
  specRoot: FigmaNodeSpec,
  screenId: string,
  screenVersion: number,
  issues: ExportIssue[],
): void {
  if (issues.some(issue => issue.severity === "FATAL" || issue.severity === "ERROR")) {
    throw new Error("STAGING_POST_VALIDATION_ISSUES");
  }
  const wrappers = [root, ...root.findAll(node => node.type === "FRAME")]
    .filter((node): node is FrameNode => node.type === "FRAME" && Boolean(node.getPluginData(DATA_LOGICAL_ID)));
  const actual = new Map<string, FrameNode>();
  for (const wrapper of wrappers) {
    const logicalId = wrapper.getPluginData(DATA_LOGICAL_ID);
    if (actual.has(logicalId)) throw new Error(`STAGING_DUPLICATE_LOGICAL_ID: ${logicalId}`);
    actual.set(logicalId, wrapper);
  }
  const expected = flattenSpec(specRoot);
  if (actual.size !== expected.length) {
    throw new Error(`STAGING_NODE_COUNT_MISMATCH: expected=${expected.length}, actual=${actual.size}`);
  }
  for (const { node } of expected) {
    const wrapper = actual.get(node.logicalNodeId);
    if (!wrapper) throw new Error(`STAGING_LOGICAL_NODE_MISSING: ${node.logicalNodeId}`);
    if (wrapper.getPluginData(DATA_SCREEN_ID) !== screenId
        || wrapper.getPluginData(DATA_SCREEN_VERSION) !== String(screenVersion)
        || wrapper.getPluginData(DATA_LOGICAL_TYPE) !== node.type) {
      throw new Error(`STAGING_NODE_METADATA_MISMATCH: ${node.logicalNodeId}`);
    }
    const expectedChildren = node.children.map(child => child.logicalNodeId);
    const actualChildren = logicalChildren(wrapper).map(child => child.getPluginData(DATA_LOGICAL_ID));
    if (JSON.stringify(actualChildren) !== JSON.stringify(expectedChildren)) {
      throw new Error(`STAGING_CHILD_ORDER_MISMATCH: ${node.logicalNodeId}`);
    }
    if (node.componentResolution) {
      const instance = wrapper.children.find(child => child.type === "INSTANCE"
        && child.getPluginData(DATA_COMPONENT_SET_KEY) === node.componentResolution?.componentSetKey) as InstanceNode | undefined;
      if (!instance) throw new Error(`STAGING_INSTANCE_MISSING: ${node.logicalNodeId}`);
      if (instance.getPluginData(DATA_COMPONENT_VARIANT_KEY) !== node.componentResolution.variantKey) {
        throw new Error(`STAGING_VARIANT_MISMATCH: ${node.logicalNodeId}`);
      }
    }
  }
}

function clearStagingMarker(root: FrameNode): void {
  markStagingTree(root, "false");
  root.setPluginData(DATA_APPLY_BACKUP, "false");
  for (const node of root.findAll(child => child.type === "FRAME")) {
    if (node.type !== "FRAME") continue;
    node.setPluginData(DATA_APPLY_BACKUP, "false");
  }
}

function markStagingTree(root: FrameNode, value: "true" | "false"): void {
  root.setPluginData(DATA_APPLY_STAGING, value);
  for (const node of root.findAll(child => child.type === "FRAME")) {
    if (node.type === "FRAME") node.setPluginData(DATA_APPLY_STAGING, value);
  }
}

function removeEmptyArchive(screenId: string): void {
  const archive = figma.currentPage.children.find(node =>
    node.type === "FRAME" && node.name === `🗄 Removed — ${screenId}`) as FrameNode | undefined;
  if (archive && archive.children.length === 0) archive.remove();
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
  const reportCounts: ApplyCounts = { reused: 0, created: 0, archived: 0, fallback: 0 };
  let qualityGates: QualityGateResult[] = [];
  const importedComponents = await preloadComponents(screen.content, registry, issues);
  if (issues.some(issue => issue.severity === "FATAL" || issue.severity === "ERROR")) {
    throw new Error("Published Component 사전 import에 실패하여 Apply를 중단했습니다.");
  }
  const existingRoot = findScreenRoot(screen.screenId);
  const origin = existingRoot ? { x: existingRoot.x, y: existingRoot.y } : { x: 0, y: 0 };
  try {
    await runAtomicApply<ApplyBackup, ApplyStaging, FrameNode>({
    createBackup: async () => createApplyBackup(existingRoot),
    createStaging: async () => createApplyStaging(screen.screenId),
    populateStaging: async staging => {
      let existing = new Map<string, FrameNode>();
      if (mode === "MERGE" && existingRoot) {
        const candidate = existingRoot.clone();
        markStagingTree(candidate, "true");
        staging.container.appendChild(candidate);
        existing = indexRoot(candidate);
      }
      staging.root = await syncNode(
        screen.content, staging.container, existing, registry, importedComponents,
        screen.screenId, screen.screenVersion, changes, issues, reportCounts,
      );
      staging.root.x = 0;
      staging.root.y = 0;
      const staleRoots = [...existing.values()].filter(stale => {
        const parentFrame = stale.parent?.type === "FRAME" ? stale.parent as FrameNode : undefined;
        const parentLogicalId = parentFrame?.getPluginData(DATA_LOGICAL_ID);
        return !parentLogicalId || !existing.has(parentLogicalId);
      });
      for (const stale of staleRoots) {
        reportCounts.archived++;
        changes.push({
          logicalNodeId: stale.getPluginData(DATA_LOGICAL_ID),
          logicalType: stale.getPluginData(DATA_LOGICAL_TYPE),
          changeType: "ARCHIVE",
          detail: "새 Spec에서 제거되어 기존 Root 백업에만 보존",
        });
        stale.remove();
      }
    },
    validateStaging: async staging => {
      if (!staging.root) throw new Error("STAGING_ROOT_NOT_CREATED");
      validateStagedRoot(staging.root, screen.content, screen.screenId, screen.screenVersion, issues);
      qualityGates = await validateQualityGates(staging.root, screen.content, existingRoot);
      for (const gate of qualityGates.filter(candidate => candidate.status === "FAILED")) {
        issues.push({
          code: `${gate.gate}_GATE_FAILED`, severity: "FATAL",
          message: `${gate.gate} Gate 실패: ${gate.issueCodes.join(", ")}`,
          logicalNodeId: screen.content.logicalNodeId,
        });
      }
      if (qualityGates.some(gate => gate.status === "FAILED")) {
        throw new Error("STAGING_QUALITY_GATE_FAILED");
      }
    },
    commit: async (staging, backup) => {
      if (!staging.root) throw new Error("STAGING_ROOT_NOT_CREATED");
      const targetParent = backup.parent ?? figma.currentPage;
      const targetIndex = backup.parent
        ? Math.min(backup.index, targetParent.children.length)
        : Math.max(0, targetParent.children.indexOf(staging.container));
      if (backup.existingRoot) {
        archiveNode(backup.existingRoot, screen.screenId);
        if (mode === "REPLACE") reportCounts.archived++;
      }
      targetParent.insertChild(targetIndex, staging.root);
      staging.root.x = origin.x;
      staging.root.y = origin.y;
      staging.root.visible = backup.existingRoot ? backup.visible : true;
      staging.root.opacity = backup.existingRoot ? backup.opacity : 1;
      clearStagingMarker(staging.root);
      const visual = qualityGates.find(gate => gate.gate === "VISUAL_REGRESSION");
      if (visual?.evidenceHash) staging.root.setPluginData(DATA_VISUAL_BASELINE_HASH, visual.evidenceHash);
      staging.container.remove();
      backup.backupClone?.remove();
      if (findScreenRoot(screen.screenId) !== staging.root) {
        throw new Error("POST_COMMIT_ROOT_MISMATCH");
      }
      figma.currentPage.selection = [staging.root];
      figma.viewport.scrollAndZoomIntoView([staging.root]);
      return staging.root;
    },
    rollback: async (staging, backup) => {
      if (staging?.root?.parent) staging.root.remove();
      if (staging?.container.parent) staging.container.remove();
      if (backup?.existingRoot && backup.parent) {
        const restoreIndex = Math.min(backup.index, backup.parent.children.length);
        backup.parent.insertChild(restoreIndex, backup.existingRoot);
        backup.existingRoot.x = backup.x;
        backup.existingRoot.y = backup.y;
        backup.existingRoot.visible = backup.visible;
        backup.existingRoot.opacity = backup.opacity;
        backup.existingRoot.setPluginData(DATA_ARCHIVED, backup.archived);
      }
      if (backup?.backupClone?.parent) backup.backupClone.remove();
      removeEmptyArchive(screen.screenId);
    },
    });
  } catch (error) {
    issues.push({
      code: "ATOMIC_APPLY_ROLLED_BACK", severity: "FATAL",
      message: error instanceof Error ? error.message : "원자 적용 실패",
      logicalNodeId: screen.content.logicalNodeId,
    });
  }
  const fatal = issues.some(issue => issue.severity === "FATAL" || issue.severity === "ERROR");
  const status = generationStatus(fatal, reportCounts.fallback);
  return {
    reportId: `figma-${screen.screenId}-v${screen.screenVersion}-${Date.now()}`,
    status,
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
    qualityGates,
  };
}

async function validateQualityGates(
  root: FrameNode,
  spec: FigmaNodeSpec,
  existingRoot?: FrameNode,
): Promise<QualityGateResult[]> {
  const layoutIssues: string[] = [];
  const accessibilityIssues: string[] = [];
  const wrappers = new Map<string, FrameNode>();
  wrappers.set(root.getPluginData(DATA_LOGICAL_ID), root);
  for (const node of root.findAll(child => child.type === "FRAME")) {
    if (node.type === "FRAME") wrappers.set(node.getPluginData(DATA_LOGICAL_ID), node);
  }
  const visit = (node: FigmaNodeSpec) => {
    const wrapper = wrappers.get(node.logicalNodeId);
    if (!wrapper) {
      layoutIssues.push(`NODE_MISSING:${node.logicalNodeId}`);
    } else {
      if (wrapper.layoutMode === "NONE") layoutIssues.push(`AUTO_LAYOUT_MISSING:${node.logicalNodeId}`);
      if (wrapper.width <= 0 || wrapper.height <= 0) layoutIssues.push(`EMPTY_BOUNDS:${node.logicalNodeId}`);
      if (node.componentResolution) {
        const role = node.componentResolution.role;
        const target = wrapper.children.find(child => child.type === "INSTANCE") as InstanceNode | undefined;
        if (!target) accessibilityIssues.push(`INSTANCE_MISSING:${node.logicalNodeId}`);
        if (target && (role.startsWith("action.") || role.startsWith("field."))
            && (target.width < 44 || target.height < 44)) {
          accessibilityIssues.push(`TARGET_SIZE:${node.logicalNodeId}`);
        }
        const state = Object.entries(node.componentResolution.variantProperties)
          .find(([key]) => key.toLowerCase() === "state")?.[1];
        if ((node.properties.disabled === true || node.properties.mode === "disabled")
            && state?.toLowerCase() !== "disabled") {
          accessibilityIssues.push(`DISABLED_STATE:${node.logicalNodeId}`);
        }
        if ((node.properties.mode === "view" || node.properties.mode === "readonly")
            && !["view", "readonly", "read-only"].includes((state ?? "").toLowerCase())) {
          accessibilityIssues.push(`READ_ONLY_STATE:${node.logicalNodeId}`);
        }
      }
    }
    node.children.forEach(visit);
  };
  visit(spec);

  const image = await root.exportAsync({ format: "PNG", constraint: { type: "SCALE", value: 1 } });
  const evidenceHash = stableByteHash(image);
  const sameScreenVersion = existingRoot?.getPluginData(DATA_SCREEN_VERSION) ===
    root.getPluginData(DATA_SCREEN_VERSION);
  const baselineHash = sameScreenVersion
    ? existingRoot?.getPluginData(DATA_VISUAL_BASELINE_HASH) || null : null;
  const visualStatus = visualRegressionStatus(evidenceHash, baselineHash, sameScreenVersion);
  return [
    { gate: "LAYOUT", status: layoutIssues.length ? "FAILED" : "PASSED", issueCodes: layoutIssues },
    { gate: "ACCESSIBILITY", status: accessibilityIssues.length ? "FAILED" : "PASSED", issueCodes: accessibilityIssues },
    {
      gate: "VISUAL_REGRESSION", status: visualStatus,
      issueCodes: visualStatus === "FAILED"
        ? [baselineHash == null ? "VISUAL_BASELINE_MISSING" : "PIXEL_HASH_MISMATCH"] : [],
      evidenceHash, baselineHash, diffRatio: visualStatus === "FAILED" ? 1 : 0, threshold: 0,
    },
  ];
}

function stableByteHash(bytes: Uint8Array): string {
  let hash = 0x811c9dc5;
  for (const byte of bytes) {
    hash ^= byte;
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return `fnv1a32:${hash.toString(16).padStart(8, "0")}:${bytes.length}`;
}

async function syncNode(
  spec: FigmaNodeSpec,
  parent: PageNode | FrameNode,
  existing: Map<string, FrameNode>,
  registry: ComponentRegistry,
  importedComponents: Map<string, ImportedComponent>,
  screenId: string,
  screenVersion: number,
  changes: ReconciliationChange[],
  issues: ExportIssue[],
  counts: { reused: number; created: number; archived: number; fallback: number },
): Promise<FrameNode> {
  let wrapper = existing.get(spec.logicalNodeId);
  const typeChanged = wrapper && wrapper.getPluginData(DATA_LOGICAL_TYPE) !== spec.type;
  if (wrapper && typeChanged) {
    counts.archived++;
    existing.delete(spec.logicalNodeId);
    wrapper.remove();
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
  if (parent.type === "FRAME") {
    // 원자적 Apply의 Staging Frame은 AUTO 폭이다. PAGE Root까지 STRETCH하면
    // 명세의 1440px 폭이 Staging 폭으로 축소되므로 하위 노드에만 적용한다.
    if (parent.layoutMode === "VERTICAL" && spec.nodeType !== "PAGE") {
      wrapper.layoutAlign = "STRETCH";
      // Figma의 HUG(AUTO) 축은 layoutAlign=STRETCH만으로 부모 폭을 채우지 않는다.
      // 자식의 폭 축을 FIXED로 전환해야 Table Row와 Action Area가 실제 콘텐츠 폭을 사용한다.
      if (wrapper.layoutMode === "HORIZONTAL") wrapper.primaryAxisSizingMode = "FIXED";
      else wrapper.counterAxisSizingMode = "FIXED";
    }
    if (parent.layoutMode === "HORIZONTAL") {
      const parentType = parent.getPluginData(DATA_LOGICAL_TYPE);
      if (parentType === "egov.actionArea") {
        wrapper.layoutGrow = 0;
        wrapper.minWidth = null;
      } else {
        const rawPercent = spec.properties.columnWidthPercent;
        if (typeof rawPercent === "number" && Number.isFinite(rawPercent)
            && rawPercent > 0 && rawPercent <= 100) {
          // Figma layoutGrow는 0/1만 허용하므로 컬럼 비율은 Row 폭 기준 고정 폭으로 계산한다.
          wrapper.layoutGrow = 0;
          wrapper.minWidth = null;
          wrapper.counterAxisSizingMode = "FIXED";
          wrapper.resizeWithoutConstraints(
            Math.max(1, parent.width * rawPercent / 100),
            Math.max(1, wrapper.height),
          );
        } else {
          // 비율 계약이 없는 일반 Horizontal Layout은 균등 배분한다.
          wrapper.layoutGrow = 1;
          wrapper.minWidth = 96;
        }
      }
    }
  }

  const entry = registry.components[spec.type];
  if (spec.componentResolution && entry) {
    removeFallbackPlaceholder(wrapper);
    const published = await ensurePublishedInstance(wrapper, spec, entry, importedComponents, issues);
    if (!published) {
      counts.fallback++;
      wrapper.remove();
      throw new Error(`Published Component 적용 실패: ${spec.logicalNodeId}`);
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
  wrapper.setPluginData(DATA_APPLY_STAGING, "true");
  for (const [key, value] of Object.entries(annotation.pluginData)) {
    wrapper.setPluginData(`figmaScreenSpec.layout.${key}`, value);
  }
  wrapper.layoutMode = spec.type === "egov.actionArea" ? "HORIZONTAL" : "VERTICAL";
  if (spec.type === "krds.dataTable.header" || spec.type === "krds.dataTable.row") {
    wrapper.layoutMode = "HORIZONTAL";
  }
  wrapper.primaryAxisSizingMode = "AUTO";
  wrapper.counterAxisSizingMode = spec.nodeType === "PAGE" ? "FIXED" : "AUTO";
  wrapper.itemSpacing = spec.nodeType === "PAGE" ? 40 : 16;
  wrapper.paddingTop = wrapper.paddingBottom = spec.nodeType === "PAGE" ? 48 : 0;
  wrapper.paddingLeft = wrapper.paddingRight = spec.nodeType === "PAGE" ? 80 : 0;
  if (spec.nodeType === "PAGE") wrapper.resizeWithoutConstraints(1440, Math.max(1, wrapper.height));
  if (spec.type === "krds.dataTable") wrapper.itemSpacing = 0;
  if (spec.type === "krds.dataTable.header" || spec.type === "krds.dataTable.row") {
    wrapper.itemSpacing = 0;
    wrapper.minHeight = spec.type.endsWith("header") ? 56 : 52;
  }
  if (spec.type === "egov.actionArea") {
    wrapper.primaryAxisAlignItems = "MAX";
    wrapper.counterAxisAlignItems = "CENTER";
    wrapper.itemSpacing = 12;
  }
  wrapper.clipsContent = false;
}

async function ensurePublishedInstance(
  wrapper: FrameNode,
  spec: FigmaNodeSpec,
  entry: RegistryEntry,
  importedComponents: Map<string, ImportedComponent>,
  issues: ExportIssue[],
): Promise<boolean> {
  try {
    const resolution = spec.componentResolution;
    if (!resolution) throw new Error("서버 Component Resolution이 없습니다.");
    const imported = importedComponents.get(resolution.variantKey);
    if (!imported) throw new Error("사전 import된 Published Variant를 찾을 수 없습니다.");
    const properties = {
      ...resolution.variantProperties,
      ...resolution.componentProperties,
    };
    await resolveInstanceSwapProperties(entry, properties, spec, issues);
    if (imported.type !== "COMPONENT") throw new Error("variantKey가 단일 Published Component를 가리키지 않습니다.");
    const component = imported;

    let instance = wrapper.children.find(child =>
      child.type === "INSTANCE"
      && child.getPluginData(DATA_COMPONENT_SET_KEY) === resolution.componentSetKey) as InstanceNode | undefined;
    if (!instance) {
      for (const old of wrapper.children.filter(child => child.type === "INSTANCE")) old.remove();
      instance = component.createInstance();
      wrapper.insertChild(0, instance);
      instance.setPluginData(DATA_COMPONENT_SET_KEY, resolution.componentSetKey);
    }
    instance.setPluginData(DATA_COMPONENT_VARIANT_KEY, resolution.variantKey);
    instance.name = `${spec.type} · Published Instance`;
    applyOwnedProperties(instance, properties);
    const rawMaxWidth = spec.properties.componentMaxWidth;
    if (typeof rawMaxWidth === "number" && Number.isFinite(rawMaxWidth) && rawMaxWidth > 0) {
      instance.layoutAlign = "INHERIT";
      instance.resizeWithoutConstraints(rawMaxWidth, Math.max(1, instance.height));
    } else {
      instance.layoutAlign = "STRETCH";
    }
    return true;
  } catch (error) {
    issues.push({
      code: "PUBLISHED_COMPONENT_IMPORT_FAILED",
      severity: "FATAL",
      message: `${spec.type} import 실패: ${error instanceof Error ? error.message : "알 수 없는 오류"}`,
      logicalNodeId: spec.logicalNodeId,
    });
    return false;
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

function removeFallbackPlaceholder(wrapper: FrameNode): void {
  for (const child of wrapper.children) {
    if (child.type === "FRAME" && child.getPluginData(DATA_FALLBACK) === "true") child.remove();
  }
}

async function preloadComponents(
  root: FigmaNodeSpec,
  registry: ComponentRegistry,
  issues: ExportIssue[],
): Promise<Map<string, ImportedComponent>> {
  const keys = new Set<string>();
  const visit = (node: FigmaNodeSpec) => {
    const key = node.componentResolution?.variantKey;
    if (key) keys.add(key);
    node.children.forEach(visit);
  };
  visit(root);
  const imported = new Map<string, ImportedComponent>();
  for (const key of keys) {
    try {
      imported.set(key, await figma.importComponentByKeyAsync(key));
    } catch {
      issues.push({
        code: "PUBLISHED_COMPONENT_IMPORT_FAILED",
        severity: "FATAL",
        message: "서버가 지정한 Published Variant를 import할 수 없습니다.",
        logicalNodeId: null,
      });
    }
  }
  return imported;
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
