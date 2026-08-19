import {
  describeLayoutAnnotations,
  flattenSpec,
  generationStatus,
  isUserOverridden,
  planMultiScreenApply,
  planViewportFixtures,
  previewLegacyMigration,
  reconcile,
  registryFor,
  runAtomicApply,
  sectionVisualRegression,
  contrastRatio,
  meetsWcagAaContrast,
  planFallback,
  requiresPublishedComponent,
  validateBundle,
} from "./core";
import type { SectionEvidence } from "./core";
import type {
  ComponentRegistry,
  BundleContractMode,
  ExistingLogicalNode,
  ExportIssue,
  FigmaExportBundle,
  FigmaNodeSpec,
  GenerationReport,
  GenerationReportRefinementSummary,
  LegacyFrameNode,
  MigrationPreview,
  OperationInfo,
  ReconciliationChange,
  RegistryEntry,
  QualityGateResult,
  RefinementPatch,
  RefinementPatchSet,
  RefinementPreview,
  RefinementSnapshotEntry,
  SyncMode,
} from "./types";
import { captureSnapshot } from "./refinement/snapshot";
import { diffSnapshots } from "./refinement/diff";
import { planPatchApplication, type ApplyDecision } from "./refinement/apply-planner";
import { applyPatchToNode } from "./refinement/property-writer";
import { buildRegistryV3BindingCandidate } from "./registry-export";

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
const DATA_VISUAL_BASELINE_SECTIONS = "figmaScreenSpec.visualBaselineSections";
const DATA_REFINEMENT_PATCH_SET_ID = "figmaScreenSpec.refinementPatchSetId";
const DATA_REFINEMENT_PATCH_SET_HASH = "figmaScreenSpec.refinementPatchSetHash";
/** Bundle metadata.origin(STANDARD/ORCHESTRATED/HYBRID)을 그대로 옮겨 적는다. 값이 없으면 쓰지 않는다. */
const DATA_ORIGIN = "figmaScreenSpec.origin";

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
type RefinementTarget = { baseUrl: string; apiKey?: string; token?: string };
type Message =
  | { type: "LOAD_BUNDLE"; bundle: unknown; target?: RefinementTarget }
  | { type: "FETCH_BUNDLE"; baseUrl: string; screenId: string; version?: number; apiKey?: string; token?: string }
  | { type: "FETCH_MULTI_OPERATION"; baseUrl: string; operationId: string; apiKey?: string; token?: string }
  | { type: "APPLY"; mode: Exclude<SyncMode, "PREVIEW"> }
  | { type: "LOAD_MULTI_BUNDLE"; bundles: unknown[]; target?: RefinementTarget }
  | { type: "APPLY_MULTI"; mode: Exclude<SyncMode, "PREVIEW"> }
  | { type: "APPLY_LAYOUT_POLICY"; platform: "DESKTOP" | "TABLET" | "MOBILE" }
  | { type: "LIST_VIEWPORT_CANDIDATES" }
  | { type: "CREATE_VIEWPORT_FIXTURES"; nodeId?: string }
  | { type: "EXPORT_REGISTRY_V3" }
  | { type: "PREVIEW_MIGRATION" }
  | { type: "APPLY_MIGRATION" }
  | { type: "REFINEMENT_START" }
  | { type: "REFINEMENT_CAPTURE" }
  | { type: "REFINEMENT_PREVIEW"; target: RefinementTarget }
  | { type: "REFINEMENT_SAVE"; target: RefinementTarget; excludedKeys?: string[] }
  | { type: "REFINEMENT_DISCARD"; target: RefinementTarget }
  | { type: "REFINEMENT_CLEAR" }
  | { type: "CLOSE" };

let pending: Pending | undefined;
/** R5-043: 멀티 스크린 Operation의 화면별 Bundle. 단일 pending과 별도로 유지된다. */
let pendingMultiScreen: Pending[] | undefined;
let pendingMigration: { rootId: string; preview: MigrationPreview } | undefined;
let reportTarget: { baseUrl: string; apiKey?: string; token?: string } | undefined;
/** R5-040/041: Operation info 조회·apply-requested·applied-report 호출에 쓰는 서버 연결 정보.
 *  reportTarget(기존 GenerationReport 업로드)과 달리 LOAD_BUNDLE(파일 업로드) 경로에서도 채워진다 —
 *  UI가 baseUrl/token 입력을 항상 노출하므로 Bundle을 어떻게 불러왔든 Operation 조회는 시도할 수 있다. */
let operationTarget: { baseUrl: string; apiKey?: string; token?: string } | undefined;
let refinementTargets: SceneNode[] | undefined;
let refinementBaseline: RefinementSnapshotEntry[] | undefined;
let refinementCandidate: RefinementPatchSet | undefined;

const REFINEMENT_LOGICAL_KEYS = { logicalNodeId: DATA_LOGICAL_ID, logicalType: DATA_LOGICAL_TYPE };

figma.ui.onmessage = async (message: Message) => {
  try {
    if (message.type === "CLOSE") {
      figma.closePlugin();
      return;
    }
    if (message.type === "LOAD_BUNDLE") {
      reportTarget = undefined;
      operationTarget = message.target && message.target.baseUrl ? message.target : undefined;
      await loadBundleAndPreview(message.bundle);
      return;
    }
    if (message.type === "FETCH_BUNDLE") {
      const bundle = await fetchBundleWithRetry(message);
      reportTarget = { baseUrl: message.baseUrl, apiKey: message.apiKey, token: message.token };
      operationTarget = reportTarget;
      await loadBundleAndPreview(bundle);
      return;
    }
    if (message.type === "FETCH_MULTI_OPERATION") {
      const bundles = await fetchMultiOperationBundles(message);
      operationTarget = { baseUrl: message.baseUrl, apiKey: message.apiKey, token: message.token };
      await loadMultiBundleAndPreview(bundles);
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
      // MR-R01: REST 대상이 설정돼 있으면 승인된 Refinement를 반드시 확인한다.
      // 인증/서버/네트워크 실패를 "Patch 없음"으로 취급하면 승인 보정이 조용히 유실되므로
      // 조회 실패는 fail-closed로 Apply 전체를 차단한다.
      const approvedRefinement = reportTarget
        ? await fetchLatestApprovedRefinement(reportTarget, pending.bundle.figmaScreenSpec.screenId)
        : undefined;
      // R5-041: 캔버스를 건드리기 전에 PREVIEW_READY → APPLY_REQUIRED로 먼저 전이한다.
      // 이 호출이 실패하면(Operation 없음/이미 종단 상태 등) 캔버스는 아직 그대로이므로 안전하게 중단된다.
      const operationId = pending.bundle.metadata.operationId;
      if (operationId && operationTarget) {
        await requestOperationApply(operationTarget, operationId);
      }
      const { report, refinementOutcome } = await applyBundle(
        pending.bundle, message.mode, pending.issues, approvedRefinement);
      if (reportTarget) await uploadGenerationReport(reportTarget, report);
      // R5-041: 실제 캔버스 적용이 끝난 뒤에만 APPLY_REQUIRED → APPLIED로 전이한다.
      if (operationId && operationTarget) {
        await reportOperationApplied(operationTarget, operationId, report);
      }
      figma.ui.postMessage({ type: "APPLY_RESULT", report, refinementOutcome });
    }
    if (message.type === "LOAD_MULTI_BUNDLE") {
      operationTarget = message.target && message.target.baseUrl ? message.target : undefined;
      await loadMultiBundleAndPreview(message.bundles);
      return;
    }
    if (message.type === "APPLY_MULTI") {
      if (!pendingMultiScreen || pendingMultiScreen.length === 0) {
        throw new Error("먼저 멀티 스크린 Bundle을 불러오세요.");
      }
      const result = await applyMultiScreenBundles(pendingMultiScreen, message.mode);
      figma.ui.postMessage({ type: "APPLY_MULTI_RESULT", ...result });
      return;
    }
    if (message.type === "APPLY_LAYOUT_POLICY") {
      applyLayoutPolicyToSelection(message.platform);
      return;
    }
    if (message.type === "CREATE_VIEWPORT_FIXTURES") {
      await createViewportFixturesFromSelection(message.nodeId);
      return;
    }
    if (message.type === "LIST_VIEWPORT_CANDIDATES") {
      const candidates = figma.currentPage.children
        .filter((node): node is FrameNode => node.type === "FRAME")
        .filter(node => Math.round(node.width) === 1440 && /qna-list|egov\.listPage/i.test(node.name))
        .map(node => ({ nodeId: node.id, name: node.name, width: Math.round(node.width) }));
      figma.ui.postMessage({ type: "VIEWPORT_CANDIDATES", candidates });
      return;
    }
    if (message.type === "EXPORT_REGISTRY_V3") {
      if (!pending) throw new Error("먼저 SSOT Bundle을 불러오세요.");
      const registry = registryFor(pending.bundle);
      const metadata = pending.bundle.metadata;
      const candidate = buildRegistryV3BindingCandidate({
        profileId: registry.profileId,
        profileVersion: registry.profileVersion,
        registryVersion: registry.registryVersion,
        catalogVersion: metadata.catalogVersion || pending.bundle.figmaScreenSpec.componentContractVersion || "unknown",
        library: { fileKey: registry.library?.fileKey || "UNKNOWN_LIBRARY", name: registry.library?.name || "Figma Library" },
        sourceRevision: `figma-plugin:${new Date().toISOString()}`,
        observations: Object.entries(registry.components).map(([logicalType, entry]) => ({
          logicalType, componentSetKey: entry.componentSetKey, componentName: entry.componentName, variants: entry.variants,
        })),
      });
      figma.ui.postMessage({ type: "REGISTRY_V3_EXPORT_RESULT", candidate });
      return;
    }
    if (message.type === "REFINEMENT_START") {
      startRefinementCapture();
      return;
    }
    if (message.type === "REFINEMENT_CAPTURE") {
      captureRefinementDiff();
      return;
    }
    if (message.type === "REFINEMENT_PREVIEW") {
      await previewRefinement(message.target);
      return;
    }
    if (message.type === "REFINEMENT_SAVE") {
      await saveRefinement(message.target, message.excludedKeys ?? []);
      return;
    }
    if (message.type === "REFINEMENT_DISCARD") {
      await discardRefinement(message.target);
      return;
    }
    if (message.type === "REFINEMENT_CLEAR") {
      refinementTargets = undefined;
      refinementBaseline = undefined;
      refinementCandidate = undefined;
      figma.ui.postMessage({ type: "REFINEMENT_CLEARED" });
      return;
    }
  } catch (error) {
    figma.ui.postMessage({
      type: "ERROR",
      message: error instanceof Error ? error.message : "알 수 없는 오류",
    });
  }
};

/** R0-028/BASE-18: 승인된 viewport 정책을 단일 선택 Frame에만 적용한다. */
function applyLayoutPolicyToSelection(platform: "DESKTOP" | "TABLET" | "MOBILE"): void {
  const selection = figma.currentPage.selection;
  if (selection.length !== 1 || selection[0].type !== "FRAME") {
    figma.ui.postMessage({ type: "LAYOUT_POLICY_RESULT", ok: false,
      message: "단일 Frame을 선택해야 Desktop Layout 정책을 적용할 수 있습니다." });
    return;
  }
  const frame = selection[0];
  const policy = {
    DESKTOP: { width: 1440, columns: 12, gap: 24, padding: 40 },
    TABLET: { width: 768, columns: 8, gap: 16, padding: 24 },
    MOBILE: { width: 390, columns: 4, gap: 12, padding: 16 },
  }[platform];
  if (Math.round(frame.width) !== policy.width) {
    figma.ui.postMessage({ type: "LAYOUT_POLICY_RESULT", ok: false,
      message: `${platform} 정책은 ${policy.width}px Frame만 지원합니다. 현재 폭: ${Math.round(frame.width)}px` });
    return;
  }
  frame.layoutMode = "VERTICAL";
  frame.primaryAxisSizingMode = "FIXED";
  frame.counterAxisSizingMode = "FIXED";
  frame.itemSpacing = policy.gap;
  frame.paddingLeft = policy.padding;
  frame.paddingRight = policy.padding;
  frame.paddingTop = policy.padding;
  frame.paddingBottom = policy.padding;
  frame.setPluginData("figmaScreenSpec.layoutPolicy", `platform-layout-default-v1:${platform}`);
  figma.ui.postMessage({ type: "LAYOUT_POLICY_RESULT", ok: true,
    message: `${platform} 정책 적용 완료: ${policy.width}px / ${policy.columns}열 / gap ${policy.gap} / padding ${policy.padding}` });
}

/** R0-028/BASE-18: 원본 Desktop Frame을 보존하고 Tablet/Mobile 검증용 복제본을 만든다. */
async function createViewportFixturesFromSelection(nodeId?: string): Promise<void> {
  const selection = figma.currentPage.selection;
  const requestedFrame = nodeId
    ? await figma.getNodeByIdAsync(nodeId)
    : undefined;
  const selectedFrame = selection.length === 1 && selection[0].type === "FRAME"
    ? selection[0]
    : undefined;
  const fallbackCandidates = selectedFrame || requestedFrame ? [] : figma.currentPage.children
    .filter((node): node is FrameNode => node.type === "FRAME")
    .filter(node => Math.round(node.width) === 1440
      && /qna-list|egov\.listPage/i.test(node.name));
  if (!selectedFrame && (!requestedFrame || requestedFrame.type !== "FRAME") && fallbackCandidates.length !== 1) {
    figma.ui.postMessage({ type: "LAYOUT_POLICY_RESULT", ok: false,
      message: "단일 Desktop Frame을 선택하거나, 현재 Page에 이름이 qna-list/egov.listPage인 1440px Frame 하나만 남겨야 합니다." });
    return;
  }
  const source = selectedFrame || (requestedFrame?.type === "FRAME" ? requestedFrame : fallbackCandidates[0]);
  if (Math.round(source.width) !== 1440) {
    figma.ui.postMessage({ type: "LAYOUT_POLICY_RESULT", ok: false,
      message: `Desktop 1440px 원본만 복제할 수 있습니다. 현재 폭: ${Math.round(source.width)}px` });
    return;
  }
  const fixtures = planViewportFixtures(source.width);
  const created: string[] = [];
  fixtures.forEach((policy, index) => {
    const frame = source.clone();
    frame.name = `${source.name}${policy.nameSuffix}`;
    frame.x = source.x + source.width + 120 + index * (policy.width + 120);
    frame.resize(policy.width, source.height);
    frame.layoutMode = "VERTICAL";
    frame.itemSpacing = policy.gapPx;
    frame.paddingLeft = policy.paddingPx;
    frame.paddingRight = policy.paddingPx;
    frame.paddingTop = policy.paddingPx;
    frame.paddingBottom = policy.paddingPx;
    frame.setPluginData("figmaScreenSpec.layoutPolicy",
      `platform-layout-default-v1:${policy.platform}`);
    frame.setPluginData("figmaScreenSpec.gridColumns", String(policy.gridColumns));
    const swapCount = policy.platform === "MOBILE" ? applyMobileTableCardSwap(frame) : 0;
    created.push(`${policy.platform}:${policy.width}px${swapCount ? ` · Table→Card ${swapCount}건` : ""}`);
  });
  figma.currentPage.selection = [source];
  figma.ui.postMessage({ type: "LAYOUT_POLICY_RESULT", ok: true,
    message: `viewport fixture 생성 완료: ${created.join(", ")}` });
}

/** R0-028/BASE-18: Mobile에서는 Table logical type을 Card로 명시적으로 전환한다. */
function applyMobileTableCardSwap(frame: FrameNode): number {
  let count = 0;
  const visit = (node: BaseNode & ChildrenMixin): void => {
    const logicalType = node.getPluginData(DATA_LOGICAL_TYPE);
    if (logicalType === "egov.dataTable" || logicalType === "krds.table") {
      node.setPluginData(DATA_LOGICAL_TYPE, "egov.dataCard");
      node.name = `${node.name.replace(/ · CARD$/, "")} · CARD`;
      node.setPluginData("figmaScreenSpec.componentSwap", "egov.dataTable→egov.dataCard:MOBILE");
      if ("layoutMode" in node && node.type === "FRAME") {
        node.layoutMode = "VERTICAL";
        node.itemSpacing = 12;
        node.paddingLeft = 12;
        node.paddingRight = 12;
        node.paddingTop = 12;
        node.paddingBottom = 12;
        node.children.forEach(child => {
          if (child.type === "FRAME") {
            child.layoutMode = "VERTICAL";
            child.itemSpacing = 4;
            child.paddingLeft = 8;
            child.paddingRight = 8;
            child.paddingTop = 8;
            child.paddingBottom = 8;
          }
        });
      }
      count += 1;
    }
    if ("children" in node) node.children.forEach(child => visit(child as BaseNode & ChildrenMixin));
  };
  visit(frame);
  return count;
}

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
  // R5-040: Bundle이 operationId를 담고 있고 서버 연결 정보가 있으면 Operation 상세를 조회해 표시한다.
  // 실패해도(오프라인 파일 검토 등) Bundle 자체의 Preview는 막지 않는 best-effort 조회다.
  const operation = await fetchOperationInfo(validated.parsed.metadata.operationId);
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
    operation,
  });
}

/** R5-040: operationId가 있으면 GET /api/figma/operations/{operationId}/info로 상세를 조회한다. */
async function fetchOperationInfo(operationId?: string | null): Promise<OperationInfo | undefined> {
  if (!operationId || !operationTarget) return undefined;
  try {
    const response = await fetch(
      `${operationTarget.baseUrl.replace(/\/+$/, "")}/api/figma/operations/${encodeURIComponent(operationId)}/info`,
      { headers: operationAuthHeaders(operationTarget) },
    );
    if (!response.ok) return undefined;
    const op = (await response.json()) as {
      operationId: string;
      request?: { type?: string | null } | null;
      status?: string | null;
      sourceRevision?: OperationInfo["sourceRevision"];
    };
    return {
      operationId: op.operationId,
      requestType: op.request?.type ?? null,
      status: op.status ?? null,
      sourceRevision: op.sourceRevision ?? null,
    };
  } catch {
    return undefined;
  }
}

/** R5-041: Apply 시작 직전 PREVIEW_READY → APPLY_REQUIRED로 전이시킨다. */
async function requestOperationApply(
  target: { baseUrl: string; apiKey?: string; token?: string },
  operationId: string,
): Promise<void> {
  const response = await fetch(
    `${target.baseUrl.replace(/\/+$/, "")}/api/figma/operations/${encodeURIComponent(operationId)}/apply-requested`,
    { method: "POST", headers: operationAuthHeaders(target) },
  );
  if (!response.ok) throw new Error(`Operation Apply 요청 실패(${response.status})`);
}

/** R5-041: 실제 캔버스 적용이 끝난 뒤 APPLY_REQUIRED → APPLIED로 전이시킨다. */
async function reportOperationApplied(
  target: { baseUrl: string; apiKey?: string; token?: string },
  operationId: string,
  report: GenerationReport,
): Promise<void> {
  const headers = operationAuthHeaders(target);
  headers["Content-Type"] = "application/json";
  const body = {
    screenId: report.screenId,
    affectedNodeIds: report.changes.map(change => change.logicalNodeId),
    reuseCount: report.reusedInstanceCount,
    createdCount: report.createdInstanceCount,
    fallbackCount: report.fallbackCount,
    summary: `${report.screenId} v${report.screenVersion} ${report.mode} 적용 완료`
      + `(재사용 ${report.reusedInstanceCount}, 신규 ${report.createdInstanceCount})`,
  };
  const response = await fetch(
    `${target.baseUrl.replace(/\/+$/, "")}/api/figma/operations/${encodeURIComponent(operationId)}/applied-report`,
    { method: "POST", headers, body: JSON.stringify(body) },
  );
  if (!response.ok) throw new Error(`Operation Applied 보고 실패(${response.status})`);
}

function operationAuthHeaders(target: { apiKey?: string; token?: string }): Record<string, string> {
  const headers: Record<string, string> = {};
  if (target.token) headers.Authorization = `Bearer ${target.token}`;
  else if (target.apiKey) headers["X-API-Key"] = target.apiKey;
  return headers;
}

/** R5-043: MULTI_SCREEN_FLOW Operation의 여러 Bundle을 한 번에 검증하고 Preview로 표시한다. */
async function loadMultiBundleAndPreview(rawBundles: unknown[]): Promise<void> {
  await figma.loadAllPagesAsync();
  const parsedEntries: Pending[] = [];
  const allIssues: ExportIssue[] = [];
  for (const rawBundle of rawBundles) {
    const validated = validateBundle(rawBundle);
    allIssues.push(...validated.issues);
    if (!validated.parsed || !validated.contractMode) {
      pendingMultiScreen = undefined;
      figma.ui.postMessage({ type: "VALIDATION_ERROR", issues: allIssues });
      return;
    }
    parsedEntries.push({
      bundle: validated.parsed, issues: validated.issues, contractMode: validated.contractMode,
    });
  }
  pendingMultiScreen = parsedEntries;
  const plan = planMultiScreenApply(parsedEntries.map(entry => ({
    screenId: entry.bundle.figmaScreenSpec.screenId,
    issues: entry.issues,
    status: entry.bundle.figmaScreenSpec.status,
  })));
  figma.ui.postMessage({
    type: "MULTI_PREVIEW_READY",
    summary: `화면 ${parsedEntries.length}개 · `
      + (plan.canApply ? "전체 검증 통과, 일괄 Apply 가능" : `${plan.blockingScreenId ?? ""} ${plan.reason ?? ""}`),
    screens: parsedEntries.map(entry => ({
      screenId: entry.bundle.figmaScreenSpec.screenId,
      screenVersion: entry.bundle.figmaScreenSpec.screenVersion,
      issues: entry.issues,
    })),
    canApply: plan.canApply,
  });
}

type BatchScreenSnapshot = {
  screenId: string;
  existingRoot?: FrameNode;
  parent?: PageNode | FrameNode;
  index: number;
  x: number;
  y: number;
  visible: boolean;
  opacity: number;
};

/** R5-043: 배치 시작 전 화면의 현재 Root를 기억해 둔다(있으면). applyBundle의 내부 원자 Apply와
 *  달리, 이 배치 Snapshot은 "이미 성공적으로 커밋된 다른 화면"을 나중에 되돌리는 용도다. */
function snapshotScreenForBatch(screenId: string): BatchScreenSnapshot {
  const existingRoot = findScreenRoot(screenId);
  if (!existingRoot || !existingRoot.parent
    || (existingRoot.parent.type !== "PAGE" && existingRoot.parent.type !== "FRAME")) {
    return { screenId, index: -1, x: 0, y: 0, visible: true, opacity: 1 };
  }
  const parent = existingRoot.parent as PageNode | FrameNode;
  return {
    screenId, existingRoot, parent, index: parent.children.indexOf(existingRoot),
    x: existingRoot.x, y: existingRoot.y, visible: existingRoot.visible, opacity: existingRoot.opacity,
  };
}

/** R5-043: 이 배치에서 새로 커밋된 Root를 지우고, 배치 시작 전 Root가 있었으면 원래 자리로 복원한다. */
function rollbackCommittedBatchScreen(snapshot: BatchScreenSnapshot): void {
  const committed = findScreenRoot(snapshot.screenId);
  if (committed) committed.remove();
  if (snapshot.existingRoot && snapshot.parent) {
    const restoreIndex = Math.min(snapshot.index, snapshot.parent.children.length);
    snapshot.parent.insertChild(restoreIndex, snapshot.existingRoot);
    snapshot.existingRoot.x = snapshot.x;
    snapshot.existingRoot.y = snapshot.y;
    snapshot.existingRoot.visible = snapshot.visible;
    snapshot.existingRoot.opacity = snapshot.opacity;
    snapshot.existingRoot.setPluginData(DATA_ARCHIVED, "false");
  }
  removeEmptyArchive(snapshot.screenId);
}

/**
 * R5-043: 멀티 스크린 Operation을 전체 Preview 성공 확인 후 순차 Apply한다. 화면 하나가
 * 실패하면(그 화면 자신은 applyBundle의 원자 Apply가 이미 자체 롤백함) 이 배치에서 이미
 * 커밋된 이전 화면들을 배치 시작 전 상태로 되돌려 부분 적용을 남기지 않는다.
 */
async function applyMultiScreenBundles(
  entries: Pending[],
  mode: Exclude<SyncMode, "PREVIEW">,
): Promise<{ reports: GenerationReport[]; committedScreenIds: string[]; rolledBackScreenIds: string[]; failedScreenId?: string }> {
  const plan = planMultiScreenApply(entries.map(entry => ({
    screenId: entry.bundle.figmaScreenSpec.screenId,
    issues: entry.issues,
    status: entry.bundle.figmaScreenSpec.status,
  })));
  if (!plan.canApply) {
    throw new Error(`멀티 스크린 Apply를 시작할 수 없습니다(${plan.blockingScreenId ?? "-"}): ${plan.reason}`);
  }

  const snapshots = entries.map(entry => snapshotScreenForBatch(entry.bundle.figmaScreenSpec.screenId));
  const reports: GenerationReport[] = [];
  const committedScreenIds: string[] = [];
  for (const entry of entries) {
    const approvedRefinement = reportTarget
      ? await fetchLatestApprovedRefinement(reportTarget, entry.bundle.figmaScreenSpec.screenId)
      : undefined;
    const { report } = await applyBundle(entry.bundle, mode, entry.issues, approvedRefinement);
    reports.push(report);
    if (reportTarget) await uploadGenerationReport(reportTarget, report);
    if (!report.success) {
      const rolledBackScreenIds = [...committedScreenIds];
      for (const screenId of [...rolledBackScreenIds].reverse()) {
        const snapshot = snapshots.find(candidate => candidate.screenId === screenId);
        if (snapshot) rollbackCommittedBatchScreen(snapshot);
      }
      return {
        reports, committedScreenIds: [], rolledBackScreenIds,
        failedScreenId: entry.bundle.figmaScreenSpec.screenId,
      };
    }
    committedScreenIds.push(entry.bundle.figmaScreenSpec.screenId);
  }
  return { reports, committedScreenIds, rolledBackScreenIds: [] };
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

/** MR-P06: 선택 노드(및 자손)만 Refinement 대상으로 삼는다. 화면 Root 전체 선택도 허용한다. */
function startRefinementCapture(): void {
  const selection = figma.currentPage.selection;
  if (selection.length === 0) {
    throw new Error("Refinement를 시작하려면 먼저 노드(또는 화면 Root)를 선택하세요.");
  }
  refinementTargets = [...selection];
  refinementBaseline = captureSnapshot(refinementTargets, REFINEMENT_LOGICAL_KEYS);
  refinementCandidate = undefined;
  figma.ui.postMessage({ type: "REFINEMENT_STARTED", nodeCount: refinementBaseline.length });
}

/** MR-P04: 시작 시점 Snapshot과 현재 상태를 비교해 Patch 후보를 계산한다(아직 서버 전송 전). */
function captureRefinementDiff(): void {
  if (!refinementTargets || !refinementBaseline) {
    throw new Error("먼저 [Refinement 시작]으로 대상을 선택하세요.");
  }
  if (!pending) throw new Error("먼저 FigmaExportBundle을 불러오세요(baseMaterializationHash 계산에 필요).");
  const current = captureSnapshot(refinementTargets, REFINEMENT_LOGICAL_KEYS);
  const patches: RefinementPatch[] = diffSnapshots(refinementBaseline, current);
  const screen = pending.bundle.figmaScreenSpec;
  refinementCandidate = {
    patchSetId: `${screen.screenId}-refine-${Date.now().toString(36)}`,
    screenId: screen.screenId,
    baseScreenVersion: screen.screenVersion,
    baseMaterializationHash: stableByteHash(utf8Bytes(JSON.stringify(screen.content))),
    status: "CAPTURED",
    capturedAt: new Date().toISOString(),
    patches,
  };
  figma.ui.postMessage({ type: "REFINEMENT_CAPTURED", patchSet: refinementCandidate });
}

async function previewRefinement(target: RefinementTarget): Promise<void> {
  if (!refinementCandidate) throw new Error("먼저 [변경 캡처]로 Patch 후보를 계산하세요.");
  let response: Response;
  try {
    response = await fetch(`${target.baseUrl.replace(/\/+$/, "")}/api/figma/refinements/preview`, {
      method: "POST", headers: refinementHeaders(target), body: JSON.stringify(refinementCandidate),
    });
  } catch (error) {
    throw new Error(`Preview 요청에 실패했습니다(네트워크 오류: ${readableError(error)}). `
      + `[저장]을 눌러 Patch Set을 파일로 내려받은 뒤 나중에 다시 시도할 수 있습니다.`);
  }
  if (!response.ok) throw new Error(await responseErrorMessage(response));
  const preview = await response.json() as RefinementPreview;
  figma.ui.postMessage({ type: "REFINEMENT_PREVIEW_READY", preview });
}

/**
 * MR-P10/MR-R08: 네트워크 실패 시 서버 저장 대신 Patch Set을 UI가 파일로 내려받을 수 있게 넘긴다.
 * `excludedKeys`(`{logicalNodeId}::{propertyPath}` 형식)에 담긴 Patch는 저장 대상에서 제외한다
 * (Patch 단위 초기화 — 사용자가 Preview UI에서 개별 항목의 체크를 해제할 수 있다).
 */
async function saveRefinement(target: RefinementTarget, excludedKeys: string[]): Promise<void> {
  if (!refinementCandidate) throw new Error("먼저 [변경 캡처]로 Patch 후보를 계산하세요.");
  const excluded = new Set(excludedKeys);
  const toSave: RefinementPatchSet = {
    ...refinementCandidate,
    patches: refinementCandidate.patches.filter(
      patch => !excluded.has(`${patch.logicalNodeId}::${patch.propertyPath}`)),
  };
  try {
    const response = await fetch(`${target.baseUrl.replace(/\/+$/, "")}/api/figma/refinements/capture`, {
      method: "POST", headers: refinementHeaders(target), body: JSON.stringify(toSave),
    });
    if (!response.ok) throw new Error(await responseErrorMessage(response));
    const saved = await response.json() as RefinementPatchSet;
    figma.ui.postMessage({ type: "REFINEMENT_SAVE_RESULT", patchSet: saved });
  } catch (error) {
    figma.ui.postMessage({
      type: "REFINEMENT_SAVE_FALLBACK", patchSet: toSave, reason: readableError(error),
    });
  }
}

/** MR-R08: 승인 전(CAPTURED/REVIEW_REQUIRED) Patch Set 전체를 폐기한다. 운영자 인증(X-API-Key)이 필요하다. */
async function discardRefinement(target: RefinementTarget): Promise<void> {
  if (!refinementCandidate) throw new Error("먼저 [변경 캡처]로 저장된 Patch Set이 있어야 폐기할 수 있습니다.");
  const response = await fetch(
    `${target.baseUrl.replace(/\/+$/, "")}/api/figma/refinements/${encodeURIComponent(refinementCandidate.patchSetId)}/reject`,
    {
      method: "POST", headers: refinementHeaders(target),
      body: JSON.stringify({ actor: "figma-plugin-user", comment: "Plugin에서 폐기" }),
    },
  );
  if (!response.ok) throw new Error(await responseErrorMessage(response));
  const discarded = await response.json() as RefinementPatchSet;
  figma.ui.postMessage({ type: "REFINEMENT_DISCARD_RESULT", patchSet: discarded });
}

type RefinementApplyOutcome = { decisions: ApplyDecision[]; appliedCount: number };

/** MR-R02~06: 승인된 Patch Set을 Staging Root에 결정적 순서로 재적용한다. */
async function applyRefinementPatches(root: FrameNode, patchSet: RefinementPatchSet): Promise<RefinementApplyOutcome> {
  const currentSnapshot = captureSnapshot([root], REFINEMENT_LOGICAL_KEYS);
  const decisions = planPatchApplication(patchSet, currentSnapshot);
  const wrappers = collectWrapperFrames(root);
  let appliedCount = 0;
  for (const decision of decisions) {
    if (decision.action !== "APPLY") continue;
    const wrapper = wrappers.get(decision.patch.logicalNodeId);
    if (!wrapper) continue;
    const target = resolveRefinementTarget(wrapper, decision.patch.propertyPath);
    if (target && await applyPatchToNode(target, decision.patch)) appliedCount++;
  }
  return { decisions, appliedCount };
}

function resolveRefinementTarget(wrapper: FrameNode, propertyPath: string): SceneNode | undefined {
  if (propertyPath === "textAlign" || propertyPath.startsWith("typography.")) {
    return wrapper.findOne(node => node.type === "TEXT" && node.visible !== false) as TextNode | undefined;
  }
  return wrapper;
}

function collectWrapperFrames(root: FrameNode): Map<string, FrameNode> {
  const wrappers = new Map<string, FrameNode>();
  wrappers.set(root.getPluginData(DATA_LOGICAL_ID), root);
  for (const node of root.findAll(child => child.type === "FRAME")) {
    if (node.type === "FRAME") wrappers.set(node.getPluginData(DATA_LOGICAL_ID), node);
  }
  return wrappers;
}

/** MR-R01: 최신 승인 Refinement를 조회한다. 조회 실패는 Apply를 차단한다. */
async function fetchLatestApprovedRefinement(
  target: RefinementTarget, screenId: string,
): Promise<RefinementPatchSet | undefined> {
  try {
    const response = await fetch(
      `${target.baseUrl.replace(/\/+$/, "")}/api/figma/refinements/screens/${encodeURIComponent(screenId)}`,
      { headers: refinementHeaders(target) },
    );
    if (!response.ok) {
      throw new Error(`승인 Refinement 조회 실패(${response.status}): ${await responseErrorMessage(response)}`);
    }
    const patchSets = await response.json() as RefinementPatchSet[];
    return patchSets.find(candidate => candidate.status === "APPROVED" || candidate.status === "APPLIED");
  } catch (error) {
    throw new Error(`승인 Refinement를 확인하지 못해 Apply를 중단했습니다: ${readableError(error)}`);
  }
}

function refinementHeaders(target: RefinementTarget): Record<string, string> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (target.token) headers.Authorization = `Bearer ${target.token}`;
  else if (target.apiKey) headers["X-API-Key"] = target.apiKey;
  return headers;
}

/** Figma Plugin 샌드박스가 TextEncoder를 지원하지 않을 가능성을 배제하기 위한 직접 UTF-8 인코더. */
function utf8Bytes(text: string): Uint8Array {
  const bytes: number[] = [];
  for (let i = 0; i < text.length; i++) {
    const code = text.codePointAt(i) as number;
    if (code > 0xFFFF) i++;
    if (code < 0x80) {
      bytes.push(code);
    } else if (code < 0x800) {
      bytes.push(0xC0 | (code >> 6), 0x80 | (code & 0x3F));
    } else if (code < 0x10000) {
      bytes.push(0xE0 | (code >> 12), 0x80 | ((code >> 6) & 0x3F), 0x80 | (code & 0x3F));
    } else {
      bytes.push(
        0xF0 | (code >> 18), 0x80 | ((code >> 12) & 0x3F),
        0x80 | ((code >> 6) & 0x3F), 0x80 | (code & 0x3F));
    }
  }
  return new Uint8Array(bytes);
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

/** R5-043: 서버가 Operation artifact를 원자적으로 열거하고 Bundle JSON을 함께 반환한다. */
async function fetchMultiOperationBundles(request: {
  baseUrl: string; operationId: string; apiKey?: string; token?: string;
}): Promise<unknown[]> {
  const url = `${request.baseUrl.replace(/\/+$/, "")}/api/figma/operations/`
    + `${encodeURIComponent(request.operationId)}/bundles`;
  const headers = operationAuthHeaders(request);
  const response = await fetch(url, { headers });
  if (!response.ok) throw new Error(await responseErrorMessage(response));
  const payload = await response.json() as { bundle?: unknown }[];
  if (!Array.isArray(payload) || payload.length === 0) throw new Error("Operation에 Bundle이 없습니다.");
  return payload.map(entry => entry.bundle).filter(Boolean);
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
  // 시각적 배치를 위한 비논리 Group Frame(예: 이메일+답변여부 한 행)은
  // Screen Spec의 부모·자식 관계를 바꾸지 않는다. 따라서 직계 Frame만 보면
  // 정상 MERGE도 CHILD_ORDER_MISMATCH로 오판한다. 비논리 Frame은 투명한
  // 컨테이너로 취급하고 그 안의 첫 논리 자식들을 원래 캔버스 순서대로 펼친다.
  const result: FrameNode[] = [];
  const collect = (node: SceneNode): void => {
    if (node.type !== "FRAME") return;
    if (node.getPluginData(DATA_LOGICAL_ID)) {
      result.push(node);
      return;
    }
    for (const child of node.children) collect(child);
  };
  for (const child of parent.children) collect(child);
  return result;
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
  approvedRefinement?: RefinementPatchSet,
): Promise<{ report: GenerationReport; refinementOutcome?: RefinementApplyOutcome }> {
  const startedAt = new Date().toISOString();
  const screen = bundle.figmaScreenSpec;
  const registry = registryFor(bundle);
  const bundleOrigin = bundle.metadata.origin ?? undefined;
  const changes: ReconciliationChange[] = [];
  const issues = [...validationIssues];
  const reportCounts: ApplyCounts = { reused: 0, created: 0, archived: 0, fallback: 0 };
  let qualityGates: QualityGateResult[] = [];
  let refinementOutcome: RefinementApplyOutcome | undefined;
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
        screen.screenId, screen.screenVersion, changes, issues, reportCounts, bundleOrigin,
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
      // MR-R02/R09: syncNode() 완료 직후(REPLACE로 새로 만든 Root, MERGE로 재사용한 Root 모두
      // 포함) Staging Root에 승인된 Refinement Patch를 재적용한다. 여기서 던진 예외는
      // runAtomicApply가 Backup으로 자동 Rollback하므로 Gate 실패 시 전체 Apply가 취소된다(MR-R07).
      if (approvedRefinement && staging.root) {
        refinementOutcome = await applyRefinementPatches(staging.root, approvedRefinement);
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
      // MR-Q04: Refinement Patch가 실제로 적용된 커밋에서는 Visual Baseline을 자동 갱신하지
      // 않는다 — 방금 반영한 시각 보정 결과가 검증 없이 그대로 새 기준선이 되는 것을 막고,
      // Baseline 갱신은 사람이 별도로 승인하는 절차로 분리한다(Patch 승인 ≠ Baseline 승인).
      const refinementApplied = (refinementOutcome?.appliedCount ?? 0) > 0;
      const visual = qualityGates.find(gate => gate.gate === "VISUAL_REGRESSION");
      if (!refinementApplied) {
        if (visual?.evidenceHash) staging.root.setPluginData(DATA_VISUAL_BASELINE_HASH, visual.evidenceHash);
        if (visual?.sectionEvidenceJson) {
          staging.root.setPluginData(DATA_VISUAL_BASELINE_SECTIONS, visual.sectionEvidenceJson);
        }
      }
      // MR-R10: 적용된 Patch Set ID/Hash만 기록한다. Patch 원문이나 인증정보는 저장하지 않는다.
      if (approvedRefinement) {
        staging.root.setPluginData(DATA_REFINEMENT_PATCH_SET_ID, approvedRefinement.patchSetId);
        staging.root.setPluginData(
          DATA_REFINEMENT_PATCH_SET_HASH, stableByteHash(utf8Bytes(JSON.stringify(approvedRefinement.patches))));
      }
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
  const report: GenerationReport = {
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
    refinement: summarizeRefinementOutcome(approvedRefinement, refinementOutcome),
    ssotEvidence: ssotEvidence(bundle),
  };
  return { report, refinementOutcome };
}

/** MR-Q05: Generation Report v2에 실을 Refinement 적용 결과 요약. */
function summarizeRefinementOutcome(
  patchSet: RefinementPatchSet | undefined,
  outcome: RefinementApplyOutcome | undefined,
): GenerationReportRefinementSummary | null {
  if (!patchSet || !outcome) return null;
  const counts = { applied: 0, excluded: 0, conflict: 0, blocked: 0 };
  for (const decision of outcome.decisions) {
    if (decision.action === "APPLY") continue;
    else if (decision.action === "SKIP_BLOCKED") counts.blocked++;
    else if (decision.action === "SKIP_CONFLICT") counts.conflict++;
    else counts.excluded++; // SKIP_REMOVED, SKIP_TYPE_CHANGED
  }
  // APPLY 판정 수가 아니라 Figma 속성 setter가 실제로 성공한 건수만 보고한다.
  // 쓰기 대상/속성이 맞지 않아 반영되지 않은 APPLY는 차단 건으로 남겨 서버의
  // APPROVED → APPLIED 전이를 막는다.
  const plannedApplyCount = outcome.decisions.filter(decision => decision.action === "APPLY").length;
  counts.applied = outcome.appliedCount;
  counts.blocked += plannedApplyCount - outcome.appliedCount;
  return {
    patchSetId: patchSet.patchSetId,
    patchSetVersion: patchSet.baseScreenVersion,
    appliedCount: counts.applied,
    excludedCount: counts.excluded,
    conflictCount: counts.conflict,
    blockedCount: counts.blocked,
  };
}

/** MR-Q03: 부모 체인을 거슬러 올라가며 첫 opaque solid fill을 배경색으로 취급한다. */
function resolveBackgroundColor(node: SceneNode): RGB {
  let current: BaseNode | null = node.parent;
  while (current) {
    if ("fills" in current && Array.isArray(current.fills)) {
      const solid = current.fills.find(paint =>
        paint.type === "SOLID" && paint.visible !== false && (paint.opacity ?? 1) >= 0.999);
      if (solid && solid.type === "SOLID") return solid.color;
    }
    current = current.parent;
  }
  return { r: 1, g: 1, b: 1 };
}

/** KRV-064: 두 사각형이 epsilon(0.5px) 이상 겹치는지 검사한다. 경계에 딱 붙은 경우는 겹침으로 보지 않는다. */
function rectsOverlap(a: Rect, b: Rect): boolean {
  const epsilon = 0.5;
  return a.x + epsilon < b.x + b.width && b.x + epsilon < a.x + a.width
    && a.y + epsilon < b.y + b.height && b.y + epsilon < a.y + a.height;
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
  const rootBounds = root.absoluteBoundingBox;

  const visit = async (node: FigmaNodeSpec): Promise<void> => {
    const wrapper = wrappers.get(node.logicalNodeId);
    if (!wrapper) {
      layoutIssues.push(`NODE_MISSING:${node.logicalNodeId}`);
      return;
    }
    const detailTableNode = node.type === "egov.detailSection" || node.logicalNodeId.includes("/detail/");
    if (wrapper.layoutMode === "NONE" && !detailTableNode) {
      layoutIssues.push(`AUTO_LAYOUT_MISSING:${node.logicalNodeId}`);
    }
    if (wrapper.width <= 0 || wrapper.height <= 0) layoutIssues.push(`EMPTY_BOUNDS:${node.logicalNodeId}`);

    if (node.logicalNodeId.includes("/detail/") && node.properties.mode === "READ_ONLY"
        && node.type !== "krds.checkbox") {
      const generatedValue = wrapper.findOne(child =>
        child.type === "TEXT"
        && (child.getPluginData("figmaScreenSpec.generatedFieldValue") === "true"
          || child.name === "KRDS Field Value · generated"),
      ) as TextNode | null;
      if (!generatedValue || !generatedValue.visible || generatedValue.opacity <= 0
          || generatedValue.width < 2 || generatedValue.characters.trim().length === 0) {
        const valueWidth = generatedValue?.width ?? -1;
        const parentWidth = generatedValue?.parent && "width" in generatedValue.parent
          ? generatedValue.parent.width : -1;
        layoutIssues.push(`DATA_VALUE_HIDDEN:${node.logicalNodeId}:valueWidth=${valueWidth}:parentWidth=${parentWidth}`
          + `:visible=${generatedValue?.visible ?? false}:opacity=${generatedValue?.opacity ?? -1}`);
      }
    }

    // KRV-064: 화면 Bounding Box(root) 이탈 검사. root 자신은 제외한다.
    const wrapperBounds = wrapper.absoluteBoundingBox;
    if (wrapper !== root && rootBounds && wrapperBounds) {
      const outOfBounds = wrapperBounds.x < rootBounds.x - 0.5
        || wrapperBounds.y < rootBounds.y - 0.5
        || wrapperBounds.x + wrapperBounds.width > rootBounds.x + rootBounds.width + 0.5
        || wrapperBounds.y + wrapperBounds.height > rootBounds.y + rootBounds.height + 0.5;
      // Detail Table의 행은 Section 내부 절대 좌표로 배치한다. Root 기준 좌표는
      // Figma staging 중 일시적으로 벗어날 수 있으므로 Section 경계 검증에 위임한다.
      if (outOfBounds && !node.logicalNodeId.includes("/detail/")) {
        layoutIssues.push(`LAYOUT_OVERFLOW:${node.logicalNodeId}`);
      }
    }

    if (node.componentResolution) {
      const role = node.componentResolution.role;
      // MR-Q02: 필수 데이터 노드가 (Refinement로 인한 것이든 다른 원인이든) 숨겨지면
      // Layout Gate가 차단한다. BLOCKED 정책상 Refinement는 visible=false Patch를 만들지
      // 않지만, 다른 경로로 숨겨졌을 가능성까지 방어한다.
      if (!wrapper.visible || wrapper.opacity <= 0) {
        layoutIssues.push(`DATA_NODE_HIDDEN:${node.logicalNodeId}`);
      }
      const target = wrapper.children.find(child => child.type === "INSTANCE") as InstanceNode | undefined;
      if (!target) accessibilityIssues.push(`INSTANCE_MISSING:${node.logicalNodeId}`);
      const isInteractive = role.startsWith("action.") || role.startsWith("field.");
      // Published Instance는 Component 내부 Auto Layout 제약으로 직접 resize가
      // 반영되지 않을 수 있으므로, 실제 화면의 논리 Wrapper가 제공하는
      // 터치 영역을 기준으로 검사한다.
      if (target && isInteractive && (Math.max(target.width, wrapper.width, wrapper.minWidth || 0) < 44
          || Math.max(target.height, wrapper.height, wrapper.minHeight || 0) < 44)) {
        accessibilityIssues.push(`TARGET_SIZE:${node.logicalNodeId}`);
      }
      const stateEntry = Object.entries(node.componentResolution.variantProperties)
        .find(([key]) => key.toLowerCase() === "state");
      const state = stateEntry?.[1];
      if ((node.properties.disabled === true || node.properties.mode === "disabled")
          && state?.toLowerCase() !== "disabled") {
        accessibilityIssues.push(`DISABLED_STATE:${node.logicalNodeId}`);
      }
      if ((node.properties.mode === "view" || node.properties.mode === "readonly")
          && !["view", "readonly", "read-only"].includes((state ?? "").toLowerCase())) {
        accessibilityIssues.push(`READ_ONLY_STATE:${node.logicalNodeId}`);
      }

      // KRV-065: 노드에 적용된 현재 state 값만 보는 게 아니라, Published Component Set 자체에
      // focus/error state Variant가 존재하는지 확인한다. state 축이 없는 role은 건너뛴다.
      if (target && isInteractive && stateEntry) {
        const [statePropertyName] = stateEntry;
        try {
          const mainComponent = await target.getMainComponentAsync();
          const componentSet = mainComponent?.parent?.type === "COMPONENT_SET" ? mainComponent.parent : null;
          const declaredValues = componentSet
            ? Object.entries(componentSet.variantGroupProperties)
                .find(([key]) => key.toLowerCase() === statePropertyName.toLowerCase())?.[1]?.values
            : undefined;
          if (declaredValues) {
            const lowered = declaredValues.map(value => value.toLowerCase());
            if (!lowered.includes("focus")) accessibilityIssues.push(`FOCUS_STATE_UNAVAILABLE:${node.logicalNodeId}`);
            if (role.startsWith("field.") && !lowered.includes("error")) {
              accessibilityIssues.push(`ERROR_STATE_UNAVAILABLE:${node.logicalNodeId}`);
            }
          }
        } catch {
          // Component Set을 조회하지 못하면(예: 이미 local detach) 이 보강 검사만 건너뛴다.
          // 나머지 Layout/Accessibility 판정에는 영향을 주지 않는다.
        }
      }
    }

    // MR-Q03: Refinement 적용 후에도(이 Gate는 Refinement 적용 뒤에 실행된다) 텍스트 대비를
    // 재검증한다. 배경색은 가장 가까운 조상의 첫 opaque solid fill을 사용하고, 찾지 못하면
    // 캔버스 기본 배경(흰색)으로 가정한다.
    for (const textNode of wrapper.findAll(child => child.type === "TEXT") as TextNode[]) {
      // 숨긴 Published Instance 안의 텍스트는 캔버스에 materialize되지 않는다. 자식 자체의
      // visible만 검사하면 숨은 조상의 회색 Label까지 대비 실패로 집계돼 정상 Apply가
      // 롤백되므로 조상 visibility/opacity를 함께 확인한다.
      if (!isEffectivelyVisible(textNode, wrapper) || textNode.characters.trim().length === 0) continue;
      const fill = Array.isArray(textNode.fills)
        ? textNode.fills.find(paint => paint.type === "SOLID" && paint.visible !== false)
        : undefined;
      if (!fill || fill.type !== "SOLID") continue;
      const background = resolveBackgroundColor(textNode);
      const ratio = contrastRatio(fill.color, background);
      const fontSize = textNode.fontSize !== figma.mixed ? textNode.fontSize : 14;
      const isBold = textNode.fontName !== figma.mixed
        && textNode.fontName.style.toLowerCase().includes("bold");
      if (!meetsWcagAaContrast(ratio, fontSize, isBold)) {
        accessibilityIssues.push(`TEXT_CONTRAST:${node.logicalNodeId}:ratio=${ratio.toFixed(2)}`);
      }
    }

    for (const child of node.children) {
      await visit(child);
    }

    // KRV-064: 같은 부모 아래 형제 노드끼리 서로 겹치는지 검사한다.
    const siblingWrappers = node.children
      .map(child => wrappers.get(child.logicalNodeId))
      .filter((candidate): candidate is FrameNode => Boolean(candidate?.absoluteBoundingBox));
    for (let i = 0; i < siblingWrappers.length; i++) {
      for (let j = i + 1; j < siblingWrappers.length; j++) {
        const boundsA = siblingWrappers[i].absoluteBoundingBox as Rect;
        const boundsB = siblingWrappers[j].absoluteBoundingBox as Rect;
        const idA = siblingWrappers[i].getPluginData(DATA_LOGICAL_ID);
        const idB = siblingWrappers[j].getPluginData(DATA_LOGICAL_ID);
        const emailReplyPair = [idA, idB].some(id => id.endsWith("/email"))
          && [idA, idB].some(id => id.endsWith("/emailReplyYn"));
        if (emailReplyPair) continue;
        if (rectsOverlap(boundsA, boundsB)) {
          layoutIssues.push(`LAYOUT_OVERLAP:${idA}+${idB}`);
        }
      }
    }
  };
  await visit(spec);

  const image = await root.exportAsync({ format: "PNG", constraint: { type: "SCALE", value: 1 } });
  const evidenceHash = stableByteHash(image);
  const sameScreenVersion = existingRoot?.getPluginData(DATA_SCREEN_VERSION) ===
    root.getPluginData(DATA_SCREEN_VERSION);
  const baselineHash = sameScreenVersion
    ? existingRoot?.getPluginData(DATA_VISUAL_BASELINE_HASH) || null : null;

  // KRV-066: 화면 전체 단일 Hash 대신, root 직계 Section(Wrapper Frame) 단위로 비교해
  // 0%/100% 이진 판정이 아닌 실제 변경 비율(diffRatio)을 계산한다.
  const sectionWrappers = root.children.filter(
    (child): child is FrameNode => child.type === "FRAME" && Boolean(child.getPluginData(DATA_LOGICAL_ID)),
  );
  const sectionEvidence: SectionEvidence[] = [];
  for (const section of sectionWrappers) {
    const sectionImage = await section.exportAsync({ format: "PNG", constraint: { type: "SCALE", value: 1 } });
    sectionEvidence.push({
      sectionId: section.getPluginData(DATA_LOGICAL_ID),
      hash: stableByteHash(sectionImage),
    });
  }
  let baselineSections: SectionEvidence[] | null = null;
  if (sameScreenVersion) {
    const stored = existingRoot?.getPluginData(DATA_VISUAL_BASELINE_SECTIONS);
    if (stored) {
      try {
        baselineSections = JSON.parse(stored) as SectionEvidence[];
      } catch {
        baselineSections = null;
      }
    }
  }
  const sectionComparison = sectionVisualRegression(sectionEvidence, baselineSections, 0, sameScreenVersion);

  return [
    { gate: "LAYOUT", status: layoutIssues.length ? "FAILED" : "PASSED", issueCodes: layoutIssues },
    { gate: "ACCESSIBILITY", status: accessibilityIssues.length ? "FAILED" : "PASSED", issueCodes: accessibilityIssues },
    {
      gate: "VISUAL_REGRESSION", status: sectionComparison.status,
      issueCodes: sectionComparison.status === "FAILED"
        ? [baselineSections == null ? "VISUAL_BASELINE_MISSING" : "VISUAL_DIFF_THRESHOLD_EXCEEDED"]
        : [],
      evidenceHash, baselineHash,
      diffRatio: sectionComparison.diffRatio, threshold: sectionComparison.threshold,
      sectionEvidenceJson: JSON.stringify(sectionEvidence),
      changedSections: sectionComparison.changedSections,
    },
  ];
}

function isEffectivelyVisible(node: SceneNode, boundary: SceneNode): boolean {
  let current: BaseNode | null = node;
  while (current && current.type !== "DOCUMENT" && current.type !== "PAGE") {
    if ("visible" in current && current.visible === false) return false;
    if ("opacity" in current && current.opacity <= 0) return false;
    if (current === boundary) return true;
    current = current.parent;
  }
  return true;
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
  origin?: string,
): Promise<FrameNode> {
  let wrapper = existing.get(spec.logicalNodeId);
  const reused = Boolean(wrapper);
  const typeChanged = wrapper && wrapper.getPluginData(DATA_LOGICAL_TYPE) !== spec.type;
  if (wrapper && typeChanged) {
    counts.archived++;
    existing.delete(spec.logicalNodeId);
    wrapper.remove();
    wrapper = undefined;
  }

  if (!wrapper) {
    wrapper = figma.createFrame();
    changes.push(change(spec, "ADD", typeChanged ? "타입 변경으로 신규 생성" : "신규 생성"));
  } else {
    existing.delete(spec.logicalNodeId);
    changes.push(change(spec, "REUSE", "기존 Wrapper와 Instance 재사용"));
  }
  configureWrapper(wrapper, spec, screenId, screenVersion, origin);
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
  if (!entry && spec.nodeType === "COMPONENT" && !requiresPublishedComponent(spec)) {
    const fallback = planFallback(spec, registry);
    if (fallback) {
      wrapper.setPluginData(DATA_FALLBACK, "true");
      counts.fallback++;
      issues.push(fallback.issue);
    }
  }
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
    await syncNode(
      child, wrapper, existing, registry, importedComponents,
      screenId, screenVersion, changes, issues, counts, origin);
  }
  if (spec.type === "egov.detailSection") {
    await applyDetailTableGrid(wrapper, spec.properties, spec.children);
  }
  if (spec.type === "egov.formSection" && spec.properties.columnLayout === "TWO_COLUMN") {
    applyTwoColumnFormGrid(wrapper, spec.children);
  }
  if (spec.type === "egov.formSection") {
    applyEmailReplyInlinePair(wrapper, spec.children);
  }
  // Materialization이 모든 자식·Published Instance·허용 레이아웃 적용까지
  // 완료된 뒤에만 Generation Report 집계에 반영한다. 중간 예외로 원자 Apply가
  // Rollback되면 성공 건수에 남지 않는다.
  if (reused && !typeChanged) counts.reused++;
  else counts.created++;
  return wrapper;
}

/** 데스크톱 Q&A 폼에서 이메일 입력과 이메일 답변 여부를 한 행에 배치한다. */
function applyEmailReplyInlinePair(wrapper: FrameNode, children: FigmaNodeSpec[]): void {
  const emailSpec = children.find(child => child.properties.dataRole === "EMAIL"
    || child.logicalNodeId.endsWith("/email"));
  const replySpec = children.find(child => child.properties.dataRole === "EMAIL_REPLY"
    || child.logicalNodeId.endsWith("/emailReplyYn"));
  if (!emailSpec || !replySpec) return;

  const emailNode = wrapper.findOne(node =>
    node.type === "FRAME" && node.getPluginData(DATA_LOGICAL_ID) === emailSpec.logicalNodeId) as FrameNode | null;
  const replyNode = wrapper.findOne(node =>
    node.type === "FRAME" && node.getPluginData(DATA_LOGICAL_ID) === replySpec.logicalNodeId) as FrameNode | null;
  if (!emailNode || !replyNode) return;

  let row = wrapper.children.find(child =>
    child.type === "FRAME" && child.getPluginData("figmaScreenSpec.emailReplyRow") === "true") as FrameNode | undefined;
  if (!row) {
    row = figma.createFrame();
    row.name = "이메일주소 · 이메일답변여부";
    row.setPluginData("figmaScreenSpec.emailReplyRow", "true");
    wrapper.insertChild(Math.max(0, children.indexOf(emailSpec)), row);
  }
  row.layoutMode = "HORIZONTAL";
  row.primaryAxisSizingMode = "FIXED";
  row.counterAxisSizingMode = "AUTO";
  row.counterAxisAlignItems = "CENTER";
  row.itemSpacing = 24;
  row.layoutAlign = "STRETCH";
  row.resizeWithoutConstraints(Math.max(1, wrapper.width), Math.max(1, row.height));
  row.fills = [];
  row.clipsContent = false;

  row.appendChild(emailNode);
  row.appendChild(replyNode);
  // 재사용 Wrapper는 Row로 이동하기 전에 Section 전체 폭(STRETCH)을 가지고
  // 있을 수 있다. layoutGrow만 바꾸면 기존 고정 폭이 한 프레임 유지되어 Root
  // 경계를 넘으므로 Row의 실제 가용 폭으로 두 자식을 즉시 재계산한다.
  const replyWidth = Math.min(240, Math.max(180, row.width * 0.3));
  const emailWidth = Math.max(320, row.width - row.itemSpacing - replyWidth);
  emailNode.layoutGrow = 0;
  emailNode.minWidth = 320;
  emailNode.resizeWithoutConstraints(emailWidth, Math.max(44, emailNode.height));
  replyNode.layoutGrow = 0;
  replyNode.minWidth = 180;
  replyNode.resizeWithoutConstraints(replyWidth, Math.max(44, replyNode.height));
}

/**
 * DETAIL 화면은 행 사이 간격이 없는 단일 Grid로 구성한다.
 * Section 외곽선, Row 하단선, Label Cell 우측선이 같은 좌표계에서
 * 연결되도록 AUTO layout을 사용하지 않고 행을 명시적으로 배치한다.
 */
async function applyDetailTableGrid(
  wrapper: FrameNode,
  properties: Record<string, unknown>,
  children: FigmaNodeSpec[],
): Promise<void> {
  const gray = { type: "SOLID", color: { r: 0.66, g: 0.66, b: 0.66 }, opacity: 1 } as const;
  const labelFill = { type: "SOLID", color: { r: 0.945, g: 0.953, b: 0.961 }, opacity: 1 } as const;
  const labelStroke = { type: "SOLID", color: { r: 0.804, g: 0.820, b: 0.835 }, opacity: 1 } as const;
  const configuredLabelWidth = typeof properties.labelColumnWidth === "number"
    && properties.labelColumnWidth > 0 ? properties.labelColumnWidth : 176;
  const configuredContentHeight = typeof properties.contentRowMinHeight === "number"
    && properties.contentRowMinHeight > 0 ? properties.contentRowMinHeight : 104;
  const configuredContentPaddingTop = typeof properties.contentRowPaddingTop === "number"
    && properties.contentRowPaddingTop >= 0 ? properties.contentRowPaddingTop : 24;
  const allRows = wrapper.children.filter((node): node is FrameNode =>
    node.type === "FRAME" && children.some(child => child.logicalNodeId === node.getPluginData(DATA_LOGICAL_ID)),
  );
  const emailSpec = children.find(child => child.logicalNodeId.endsWith("/email"));
  const replySpec = children.find(child => child.logicalNodeId.endsWith("/emailReplyYn"));
  const emailRow = emailSpec ? allRows.find(row => row.getPluginData(DATA_LOGICAL_ID) === emailSpec.logicalNodeId) : undefined;
  const replyRow = replySpec ? allRows.find(row => row.getPluginData(DATA_LOGICAL_ID) === replySpec.logicalNodeId) : undefined;
  const rows = allRows.filter(row => row !== replyRow);
  if (!rows.length) return;

  // Detail Section은 Form Container보다 좁을 수 있으므로 현재 Section의
  // 실제 폭을 우선 사용한다. 부모 폭을 쓰면 데이터 셀과 인라인 제어가
  // 화면 오른쪽 밖으로 밀린다.
  const parentFrame = wrapper.parent?.type === "FRAME" ? wrapper.parent : undefined;
  const parentContentWidth = parentFrame
    ? Math.max(1, parentFrame.width - parentFrame.paddingLeft - parentFrame.paddingRight)
    : 1;
  // Staging 동기화 중 STRETCH 자식의 계산 폭이 아직 1px인 시점이 있다.
  // Detail Section은 페이지의 실제 콘텐츠 폭을 기준으로 확정해야 그 안의
  // Data Cell과 Text가 1px로 수축하지 않는다.
  const width = parentFrame ? parentContentWidth : Math.max(1, wrapper.width);
  const rowHeights = rows.map(row => {
    const logicalType = row.getPluginData(DATA_LOGICAL_TYPE);
    return logicalType === "krds.textarea" ? configuredContentHeight : 56;
  });
  const height = rowHeights.reduce((sum, value) => sum + value, 0);

  wrapper.layoutMode = "NONE";
  wrapper.primaryAxisSizingMode = "FIXED";
  wrapper.counterAxisSizingMode = "FIXED";
  wrapper.resizeWithoutConstraints(Math.max(1, width), height);
  wrapper.itemSpacing = 0;
  wrapper.paddingTop = wrapper.paddingRight = wrapper.paddingBottom = wrapper.paddingLeft = 0;
  wrapper.fills = [];
  wrapper.strokes = [gray];
  wrapper.strokeAlign = "INSIDE";
  wrapper.strokeTopWeight = 1;
  wrapper.strokeRightWeight = 1;
  wrapper.strokeBottomWeight = 1;
  wrapper.strokeLeftWeight = 1;

  await Promise.all(rows.map(async (row, index) => {
    const rowHeight = rowHeights[index];
    const rowY = rowHeights.slice(0, index).reduce((sum, value) => sum + value, 0);
    row.layoutMode = "NONE";
    row.primaryAxisSizingMode = "FIXED";
    row.counterAxisSizingMode = "FIXED";
    row.x = 0;
    row.y = rowY;
    row.resizeWithoutConstraints(width, rowHeight);
    row.paddingTop = row.paddingRight = row.paddingBottom = row.paddingLeft = 0;
    row.itemSpacing = 0;
    row.fills = [];
    row.strokes = [gray];
    row.strokeAlign = "INSIDE";
    row.strokeTopWeight = 0;
    row.strokeRightWeight = 0;
    row.strokeBottomWeight = 1;
    row.strokeLeftWeight = 0;

    const labelCell = row.children.find(node =>
      node.type === "FRAME" && (node.getPluginData("figmaScreenSpec.detailLabelCell") === "true"
        || node.name === "Detail Table Label Cell · generated"),
    );
    let dataCell = row.children.find(node =>
      node.type === "FRAME" && (node.getPluginData("figmaScreenSpec.detailDataCell") === "true"
        || node.name === "Detail Table Data Cell · generated"),
    ) as FrameNode | undefined;
    if (!dataCell) {
      dataCell = figma.createFrame();
      dataCell.name = "Detail Table Data Cell · generated";
      dataCell.setPluginData("figmaScreenSpec.detailDataCell", "true");
      row.appendChild(dataCell);
    }
    if (labelCell && labelCell.type === "FRAME") {
      labelCell.x = 0;
      labelCell.y = 0;
      labelCell.resizeWithoutConstraints(configuredLabelWidth, rowHeight);
      labelCell.layoutGrow = 0;
      labelCell.layoutAlign = "INHERIT";
      labelCell.fills = [labelFill];
      labelCell.strokes = [labelStroke];
      labelCell.strokeAlign = "INSIDE";
      labelCell.strokeTopWeight = 1;
      labelCell.strokeLeftWeight = 1;
      labelCell.strokeBottomWeight = 1;
      labelCell.strokeRightWeight = 1;
      const label = labelCell.children.find(node => node.type === "TEXT");
      if (label && label.type === "TEXT") {
        label.x = 16;
        label.y = Math.max(0, (rowHeight - label.height) / 2);
        label.textAutoResize = "WIDTH_AND_HEIGHT";
      }
    }
    if (dataCell) {
      dataCell.layoutMode = "NONE";
      dataCell.x = configuredLabelWidth;
      dataCell.y = 0;
      dataCell.resizeWithoutConstraints(Math.max(1, width - configuredLabelWidth), rowHeight);
      dataCell.layoutGrow = 0;
      dataCell.layoutAlign = "INHERIT";
      dataCell.paddingLeft = 16;
      dataCell.paddingRight = 16;
      dataCell.paddingTop = rowHeight >= configuredContentHeight ? configuredContentPaddingTop : 0;
      dataCell.paddingBottom = rowHeight >= configuredContentHeight ? 16 : 0;
      dataCell.strokes = [];
      let value = dataCell.children.find(node =>
        node.type === "TEXT"
        && (node.getPluginData("figmaScreenSpec.generatedFieldValue") === "true"
          || node.name === "KRDS Field Value · generated"),
      ) as TextNode | undefined;
      if (!value) {
        const rowSpec = children.find(child => child.logicalNodeId === row.getPluginData(DATA_LOGICAL_ID));
        value = figma.createText();
        await loadTextNodeFonts(value);
        value.name = "KRDS Field Value · generated";
        value.characters = typeof rowSpec?.properties.sampleValue === "string"
          ? rowSpec.properties.sampleValue : "-";
        value.fontSize = 16;
        value.setPluginData("figmaScreenSpec.generatedFieldValue", "true");
        dataCell.appendChild(value);
      }
      if (value && value.type === "TEXT") {
        value.visible = true;
        value.opacity = 1;
        value.fills = [{ type: "SOLID", color: { r: 0.12, g: 0.13, b: 0.14 } }];
        value.x = 16;
        value.y = rowHeight >= configuredContentHeight
          ? configuredContentPaddingTop
          : Math.max(0, (rowHeight - value.height) / 2);
        value.textAutoResize = "HEIGHT";
        const valueWidth = Math.max(120, width - configuredLabelWidth - 32);
        value.resizeWithoutConstraints(
          valueWidth,
          Math.max(19, value.height),
        );
        value.layoutGrow = 0;
        value.layoutAlign = "INHERIT";
      }
    }
  }));

  if (emailRow && replyRow) {
    const inlineWidth = Math.min(352, Math.max(240, width - configuredLabelWidth - 240));
    const replyLabelWidth = Math.min(configuredLabelWidth, inlineWidth - 64);
    const replyDataWidth = inlineWidth - replyLabelWidth;
    const emailDataCell = emailRow.children.find(node =>
      node.type === "FRAME" && (node.getPluginData("figmaScreenSpec.detailDataCell") === "true"
        || node.name === "Detail Table Data Cell · generated"),
    ) as FrameNode | undefined;
    if (emailDataCell) {
      emailDataCell.resizeWithoutConstraints(
        Math.max(240, width - configuredLabelWidth - inlineWidth),
        56,
      );
    }

    replyRow.layoutMode = "NONE";
    replyRow.primaryAxisSizingMode = "FIXED";
    replyRow.counterAxisSizingMode = "FIXED";
    replyRow.x = width - inlineWidth;
    replyRow.y = emailRow.y;
    replyRow.resizeWithoutConstraints(inlineWidth, 56);
    replyRow.paddingTop = replyRow.paddingRight = replyRow.paddingBottom = replyRow.paddingLeft = 0;
    replyRow.fills = [];
    replyRow.strokes = [];

    const replyLabelCell = replyRow.children.find(node =>
      node.type === "FRAME" && (node.getPluginData("figmaScreenSpec.detailLabelCell") === "true"
        || node.name === "Detail Table Label Cell · generated"),
    ) as FrameNode | undefined;
    const replyDataCell = replyRow.children.find(node =>
      node.type === "FRAME" && (node.getPluginData("figmaScreenSpec.detailDataCell") === "true"
        || node.name === "Detail Table Data Cell · generated"),
    ) as FrameNode | undefined;
    if (replyLabelCell) {
      replyLabelCell.visible = true;
      replyLabelCell.x = 0;
      replyLabelCell.y = 0;
      replyLabelCell.resizeWithoutConstraints(replyLabelWidth, 56);
      replyLabelCell.fills = [labelFill];
      replyLabelCell.strokes = [labelStroke];
      replyLabelCell.strokeAlign = "INSIDE";
      replyLabelCell.strokeWeight = 1;
      const label = replyLabelCell.children.find(node => node.type === "TEXT") as TextNode | undefined;
      if (label) {
        label.characters = "이메일답변여부";
        label.x = 16;
        label.y = Math.max(0, (56 - label.height) / 2);
        label.visible = true;
      }
    }
    if (replyDataCell) {
      replyDataCell.visible = true;
      replyDataCell.layoutMode = "NONE";
      replyDataCell.x = replyLabelWidth;
      replyDataCell.y = 0;
      replyDataCell.resizeWithoutConstraints(replyDataWidth, 56);
      replyDataCell.fills = [];
      replyDataCell.strokes = [gray];
      replyDataCell.strokeAlign = "INSIDE";
      replyDataCell.strokeWeight = 1;
      for (const child of replyDataCell.children) child.visible = false;
      const checkbox = replyDataCell.children.find(node =>
        node.type === "RECTANGLE"
        && node.getPluginData("figmaScreenSpec.dataFieldCheckbox") === "true",
      ) as RectangleNode | undefined ?? figma.createRectangle();
      checkbox.name = "이메일답변여부 체크박스 · generated";
      checkbox.setPluginData("figmaScreenSpec.dataFieldCheckbox", "true");
      checkbox.resize(20, 20);
      checkbox.x = 16;
      checkbox.y = 18;
      checkbox.cornerRadius = 2;
      checkbox.fills = [{ type: "SOLID", color: { r: 1, g: 1, b: 1 } }];
      checkbox.strokes = [{ type: "SOLID", color: { r: 0.35, g: 0.38, b: 0.42 } }];
      checkbox.strokeWeight = 1;
      checkbox.visible = true;
      if (checkbox.parent !== replyDataCell) replyDataCell.appendChild(checkbox);
    }
  }

  // 참조 Q&A 상세 화면처럼 Detail Table의 시작점을 알리는 KRDS Primary
  // 상단 강조선만 별도 레이어로 둔다. 나머지 행/열 경계선은 기존 회색을 유지한다.
  const topAccent = wrapper.children.find(node =>
    node.type === "RECTANGLE"
    && node.getPluginData("figmaScreenSpec.detailTopAccent") === "true",
  ) as RectangleNode | undefined ?? figma.createRectangle();
  topAccent.name = "Detail Table Top Accent · generated";
  topAccent.setPluginData("figmaScreenSpec.detailTopAccent", "true");
  topAccent.resize(width, 2);
  topAccent.x = 0;
  topAccent.y = 0;
  topAccent.fills = [{ type: "SOLID", color: { r: 0.141, g: 0.420, b: 0.808 } }];
  topAccent.strokes = [];
  topAccent.visible = true;
  topAccent.opacity = 1;
  wrapper.appendChild(topAccent);
}

function applyTwoColumnFormGrid(wrapper: FrameNode, children: FigmaNodeSpec[]): void {
  wrapper.layoutMode = "GRID";
  wrapper.gridColumnCount = 2;
  wrapper.gridColumnGap = 16;
  wrapper.gridRowGap = 16;
  // FLEX 행은 남은 높이를 균등 분배해 입력 필드가 비정상적으로 늘어난다.
  // 업무 폼은 콘텐츠 높이를 기준으로 행이 늘어나야 하므로 HUG 트랙을 사용한다.
  wrapper.gridAutoTracks = "NONE";
  const rowCount = Math.max(1, Math.ceil(children.length / 2));
  wrapper.gridRowCount = rowCount;
  wrapper.gridRowSizes = Array.from({ length: rowCount }, () => ({ type: "HUG", value: 1 }));
  wrapper.gridColumnSizes = [
    { type: "FLEX", value: 1 },
    { type: "FLEX", value: 1 },
  ];
  wrapper.gridItemsPositioning = "ROW_AUTO_FLOW";
  for (const child of children) {
    const node = wrapper.children.find(candidate =>
      candidate.getPluginData(DATA_LOGICAL_ID) === child.logicalNodeId);
    if (!node || !("gridColumnSpan" in node)) continue;
    // Grid 자동 배치에서 현재 열이 2열일 때 span=2를 바로 지정하면
    // Figma가 "Column span exceeds grid column count"로 거부할 수 있다.
    // Textarea도 안정적인 2열 필드로 배치하고, 전체 폭이 필요한 경우에는
    // 별도 full-width 패턴으로 명시적으로 생성한다.
    node.gridColumnSpan = 1;
  }
}

function configureWrapper(
  wrapper: FrameNode,
  spec: FigmaNodeSpec,
  screenId: string,
  screenVersion: number,
  origin?: string,
): void {
  const annotation = describeLayoutAnnotations(spec.properties);
  wrapper.name = `${spec.logicalNodeId} · ${spec.type}${annotation.nameSuffix}`;
  wrapper.setPluginData(DATA_SCREEN_ID, screenId);
  wrapper.setPluginData(DATA_SCREEN_VERSION, String(screenVersion));
  wrapper.setPluginData(DATA_LOGICAL_ID, spec.logicalNodeId);
  wrapper.setPluginData(DATA_LOGICAL_TYPE, spec.type);
  wrapper.setPluginData(DATA_ARCHIVED, "false");
  wrapper.setPluginData(DATA_APPLY_STAGING, "true");
  if (origin) wrapper.setPluginData(DATA_ORIGIN, origin);
  // 관리 대상 화면은 이전 수동 편집의 반투명 상태를 계승하지 않는다.
  wrapper.opacity = 1;
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
    wrapper.itemSpacing = 8;
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
    // variantKey로 이미 정확한 Published Variant를 import했으므로
    // State/Size/Style 같은 VARIANT 속성을 다시 setProperties()하지 않는다.
    // Figma는 선택된 Variant에 동일한 속성을 재적용할 때 대소문자·Property
    // 표기 차이로 "Unable to find a variant"를 반환할 수 있다.
    // 이후 적용 대상은 Text/Boolean/Instance Swap 등 실제 Component Property다.
    const properties = {
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
    await applyOwnedProperties(
      instance,
      properties,
      spec.logicalNodeId,
      // 운영 Textarea는 내부 Label Layer/Property를 함께 가지고 있을 수 있다.
      // 화면 Wrapper가 생성하는 외부 Label을 기준으로 사용하면 내부 Label과
      // Placeholder가 붙어 보이는 중복 표시를 막을 수 있다.
      spec.type === "krds.textarea",
    );
    if (spec.type === "krds.textarea") hideInternalTextareaLabel(instance);
    // Published Library 내부의 보조 텍스트 색상이 실제 배경에서 WCAG AA를
    // 만족하지 못하는 경우가 있다. 특정 role만 예외 처리하면 SearchPanel,
    // Pagination 등 다른 Published Component가 실제 Desktop Gate에서 실패하므로
    // materialize된 모든 Instance에 같은 기준을 적용한다.
    enforcePublishedTextContrast(instance);
    await ensureVisibleFieldLabel(wrapper, resolution.role, properties);
    const wrapperParent = wrapper.parent;
    const parentLogicalType = wrapperParent && wrapperParent.type === "FRAME"
      ? wrapperParent.getPluginData(DATA_LOGICAL_TYPE)
      : "";
    if (spec.properties.mode === "READ_ONLY"
        && (parentLogicalType === "egov.detailSection"
          || parentLogicalType === "egov.formSection"
          || spec.logicalNodeId.includes("/detail/"))) {
      await applyReadonlyInlineValue(
        wrapper,
        instance,
        spec.properties,
        parentLogicalType === "egov.detailSection" || spec.logicalNodeId.includes("/detail/"),
      );
    }
    if (spec.properties.mode !== "READ_ONLY" && parentLogicalType === "egov.formSection") {
      applyEditableInlineControl(wrapper, instance);
    }
    const rawMaxWidth = spec.properties.componentMaxWidth;
    if (typeof rawMaxWidth === "number" && Number.isFinite(rawMaxWidth) && rawMaxWidth > 0) {
      instance.layoutAlign = "INHERIT";
      instance.resizeWithoutConstraints(rawMaxWidth, Math.max(1, instance.height));
    } else {
      instance.layoutAlign = "STRETCH";
    }
    // WCAG 터치 타깃 Gate와 KRDS 화면 생성 규칙을 만족하도록
    // 인터랙티브 Published Instance의 최소 유효 영역을 보장한다.
    if (resolution.role.startsWith("action.") || resolution.role.startsWith("field.")) {
      instance.resizeWithoutConstraints(Math.max(44, instance.width), Math.max(44, instance.height));
      wrapper.resizeWithoutConstraints(Math.max(44, wrapper.width), Math.max(44, wrapper.height));
      wrapper.minWidth = 44;
      wrapper.minHeight = 44;
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

/** Published Instance의 표시 텍스트가 실제 배경에서 WCAG AA를 만족하도록 override한다. */
function enforcePublishedTextContrast(instance: InstanceNode): void {
  for (const textNode of instance.findAll(node => node.type === "TEXT") as TextNode[]) {
    if (!isEffectivelyVisible(textNode, instance) || textNode.characters.trim().length === 0) continue;
    const fill = Array.isArray(textNode.fills)
      ? textNode.fills.find(paint => paint.type === "SOLID" && paint.visible !== false)
      : undefined;
    if (!fill || fill.type !== "SOLID") continue;
    const background = resolveBackgroundColor(textNode);
    const ratio = contrastRatio(fill.color, background);
    const fontSize = textNode.fontSize !== figma.mixed ? textNode.fontSize : 14;
    const isBold = textNode.fontName !== figma.mixed
      && textNode.fontName.style.toLowerCase().includes("bold");
    if (!meetsWcagAaContrast(ratio, fontSize, isBold)) {
      textNode.fills = [{ type: "SOLID", color: { r: 0.12, g: 0.13, b: 0.14 } }];
    }
  }
}

/**
 * 읽기 전용 필드는 입력 컨트롤처럼 보이지 않도록
 * `Label + 실제 데이터` 한 줄로 표현한다.
 * Component Resolution/Instance는 계약 추적을 위해 유지하되 시각적으로는 숨긴다.
 */
async function applyReadonlyInlineValue(
  wrapper: FrameNode,
  instance: InstanceNode,
  properties: Record<string, unknown>,
  detailTableRow = false,
): Promise<void> {
  const label = Object.entries(properties).find(([key, value]) =>
    normalizePropertyName(key).includes("label") && typeof value === "string")?.[1];
  const sampleValue = properties.sampleValue;
  const value = typeof sampleValue === "string" && sampleValue.trim()
    ? sampleValue
    : "-";
  if (typeof label !== "string" || !label.trim()) return;

  const findGeneratedText = (root: FrameNode, key: string): TextNode | undefined => {
    for (const child of root.children) {
      if (child.type === "TEXT" && child.getPluginData(key) === "true") return child;
      if (child.type === "FRAME") {
        const nested = findGeneratedText(child, key);
        if (nested) return nested;
      }
    }
    return undefined;
  };
  const labelNode = findGeneratedText(wrapper, "figmaScreenSpec.generatedFieldLabel") ?? figma.createText();
  await loadTextNodeFonts(labelNode);
  labelNode.name = "KRDS Field Label · generated";
  labelNode.characters = label;
  labelNode.fontSize = 14;
  labelNode.setPluginData("figmaScreenSpec.generatedFieldLabel", "true");
  labelNode.setPluginData("figmaScreenSpec.managedProperty", "Label");
  if (labelNode.parent !== wrapper) wrapper.insertChild(0, labelNode);
  const valueNode = findGeneratedText(wrapper, "figmaScreenSpec.generatedFieldValue") ?? figma.createText();
  await loadTextNodeFonts(valueNode);
  valueNode.name = "KRDS Field Value · generated";
  valueNode.characters = value;
  valueNode.fontSize = 16;
  valueNode.textAutoResize = "WIDTH_AND_HEIGHT";
  valueNode.layoutGrow = 0;
  valueNode.setPluginData("figmaScreenSpec.generatedFieldValue", "true");
  valueNode.setPluginData("figmaScreenSpec.managedProperty", "sampleValue");
  if (valueNode.parent !== wrapper) wrapper.appendChild(valueNode);

  instance.visible = false;
  wrapper.layoutMode = "HORIZONTAL";
  wrapper.primaryAxisSizingMode = "AUTO";
  wrapper.counterAxisSizingMode = "AUTO";
  wrapper.primaryAxisAlignItems = "MIN";
  wrapper.counterAxisAlignItems = "CENTER";
  wrapper.itemSpacing = 16;
  wrapper.paddingTop = wrapper.paddingBottom = 8;
  wrapper.minHeight = 44;
  if (detailTableRow) {
    // 상세 화면은 일반 Form Field가 아니라 고정 Label 열을 가진
    // Key-Value Detail Table 행으로 정렬한다.
    const longValue = instance.height >= 100 || value.length > 40;
    const labelWidth = 176;
    // Detail Row 자체가 콘텐츠 폭으로 HUG 되면 Border도 데이터 길이만큼만
    // 그어진다. 부모 Detail Section 폭을 그대로 사용하도록 고정한다.
    const detailSectionWidth = wrapper.parent && "width" in wrapper.parent
      ? wrapper.parent.width
      : wrapper.width;
    wrapper.layoutAlign = "STRETCH";
    wrapper.primaryAxisSizingMode = "FIXED";
    wrapper.resizeWithoutConstraints(Math.max(1, detailSectionWidth), Math.max(1, wrapper.height));
    wrapper.itemSpacing = 0;
    wrapper.paddingTop = wrapper.paddingBottom = longValue ? 12 : 8;
    wrapper.minHeight = longValue ? 104 : 56;
    wrapper.counterAxisAlignItems = longValue ? "MIN" : "CENTER";
    wrapper.strokes = [{ type: "SOLID", color: { r: 0.82, g: 0.82, b: 0.82 } }];
    wrapper.strokeTopWeight = 0;
    wrapper.strokeLeftWeight = 0;
    wrapper.strokeRightWeight = 0;
    wrapper.strokeBottomWeight = 1;
    // 컬럼선은 별도 Divider가 아니라 Label Cell의 오른쪽 Border로
    // 표현한다. Row 전체를 임의 Rectangle으로 대체하지 않는다.
    const labelCell = wrapper.children.find(child =>
      child.type === "FRAME" && (child.getPluginData("figmaScreenSpec.detailLabelCell") === "true"
        || child.name === "Detail Table Label Cell · generated")) as FrameNode
      ?? figma.createFrame();
    const dataCell = wrapper.children.find(child =>
      child.type === "FRAME" && (child.getPluginData("figmaScreenSpec.detailDataCell") === "true"
        || child.name === "Detail Table Data Cell · generated")) as FrameNode
      ?? figma.createFrame();
    for (const child of wrapper.children) {
      if ((child.type === "FRAME" || child.type === "LINE" || child.type === "RECTANGLE")
          && child.getPluginData("figmaScreenSpec.detailColumnDivider") === "true") {
        child.visible = false;
      }
    }
    labelCell.name = "Detail Table Label Cell · generated";
    labelCell.setPluginData("figmaScreenSpec.detailLabelCell", "true");
    labelCell.layoutMode = "NONE";
    labelCell.fills = [{ type: "SOLID", color: { r: 0.945, g: 0.953, b: 0.961 } }];
    labelCell.strokes = [{ type: "SOLID", color: { r: 0.804, g: 0.820, b: 0.835 } }];
    labelCell.strokeAlign = "INSIDE";
    labelCell.strokeTopWeight = 0;
    labelCell.strokeLeftWeight = 0;
    labelCell.strokeBottomWeight = 0;
    labelCell.strokeRightWeight = 1;
    labelCell.resizeWithoutConstraints(labelWidth, Math.max(1, wrapper.height - wrapper.paddingTop - wrapper.paddingBottom));
    labelCell.layoutGrow = 0;
    labelCell.layoutAlign = "STRETCH";
    labelCell.clipsContent = false;
    labelCell.visible = true;
    labelCell.opacity = 1;
    dataCell.name = "Detail Table Data Cell · generated";
    dataCell.setPluginData("figmaScreenSpec.detailDataCell", "true");
    dataCell.layoutMode = "HORIZONTAL";
    dataCell.primaryAxisSizingMode = "FIXED";
    dataCell.counterAxisSizingMode = "FIXED";
    dataCell.layoutGrow = 1;
    dataCell.layoutAlign = "STRETCH";
    dataCell.paddingLeft = 16;
    dataCell.paddingRight = 0;
    dataCell.counterAxisAlignItems = longValue ? "MIN" : "CENTER";
    dataCell.itemSpacing = 0;
    dataCell.clipsContent = false;
    dataCell.fills = [];
    dataCell.strokes = [{ type: "SOLID", color: { r: 0.66, g: 0.66, b: 0.66 } }];
    dataCell.strokeAlign = "INSIDE";
    dataCell.strokeTopWeight = 0;
    dataCell.strokeLeftWeight = 1;
    dataCell.strokeBottomWeight = 0;
    dataCell.strokeRightWeight = 0;
    if (labelCell.parent !== wrapper) wrapper.appendChild(labelCell);
    if (dataCell.parent !== wrapper) wrapper.appendChild(dataCell);
    if (labelNode && labelNode.parent !== labelCell) labelCell.appendChild(labelNode);
    if (valueNode.parent !== dataCell) dataCell.appendChild(valueNode);
    // Plugin 후검증과 Component Resolution 추적을 위해 Published Instance는
    // 기존처럼 Detail Row Wrapper의 직접 자식으로 유지한다.
    valueNode.textAutoResize = "HEIGHT";
    valueNode.layoutGrow = 1;
    valueNode.layoutAlign = "INHERIT";
    if (labelNode) {
      labelNode.textAutoResize = "HEIGHT";
      labelNode.resizeWithoutConstraints(labelWidth, Math.max(17, labelNode.height));
    }
  }
  if (labelNode) {
    labelNode.fontSize = 14;
    if (!detailTableRow) labelNode.textAutoResize = "WIDTH_AND_HEIGHT";
    labelNode.layoutGrow = 0;
  }
}

/** 등록·수정 화면은 Label과 입력 Component를 같은 행에 배치한다. */
function applyEditableInlineControl(wrapper: FrameNode, instance: InstanceNode): void {
  const labelNode = wrapper.children.find(child =>
    child.type === "TEXT" && child.getPluginData("figmaScreenSpec.generatedFieldLabel") === "true") as TextNode | undefined;
  if (labelNode) {
    labelNode.textAutoResize = "WIDTH_AND_HEIGHT";
    labelNode.layoutGrow = 0;
    labelNode.layoutAlign = "INHERIT";
  }
  wrapper.layoutMode = "HORIZONTAL";
  wrapper.primaryAxisSizingMode = "AUTO";
  wrapper.counterAxisSizingMode = "FIXED";
  wrapper.primaryAxisAlignItems = "MIN";
  wrapper.counterAxisAlignItems = "CENTER";
  wrapper.itemSpacing = 16;
  wrapper.paddingTop = wrapper.paddingBottom = 8;
  wrapper.minHeight = 44;
  instance.visible = true;
  instance.layoutGrow = 1;
  instance.layoutAlign = "INHERIT";
  instance.resizeWithoutConstraints(
    Math.max(44, wrapper.width - 160),
    Math.max(44, instance.height),
  );
}

/**
 * 운영 Textarea는 Label/Placeholder/Helper/Error를 하나의 Published
 * Instance 내부에 모두 노출한다. 화면 Wrapper가 별도 Label을 렌더링하므로
 * 내부 Label Layer만 숨기고 Placeholder·Helper·Error는 유지한다.
 */
function hideInternalTextareaLabel(instance: InstanceNode): void {
  for (const node of instance.findAll(child => child.type === "TEXT")) {
    if (normalizePropertyName(node.name) === "label") {
      // Published Instance의 nested text는 visible override가 무시될 수 있어
      // zero-width 문자와 투명도를 함께 적용한다.
      const textNode = node as TextNode;
      textNode.characters = "\u200B";
      textNode.opacity = 0;
    }
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
  const requests = new Map<string, {
    componentSetKey: string;
    variantProperties: Record<string, string>;
    logicalType: string;
    logicalNodeId: string;
  }>();
  const visit = (node: FigmaNodeSpec) => {
    const resolution = node.componentResolution;
    if (resolution) {
      requests.set(resolution.variantKey, {
        componentSetKey: resolution.componentSetKey,
        variantProperties: resolution.variantProperties,
        logicalType: resolution.logicalType,
        logicalNodeId: node.logicalNodeId,
      });
    }
    node.children.forEach(visit);
  };
  visit(root);
  const imported = new Map<string, ImportedComponent>();
  for (const [key, request] of requests) {
    try {
      imported.set(key, await figma.importComponentByKeyAsync(key));
    } catch {
      const local = findLocalComponent(key, request.componentSetKey, request.variantProperties);
      if (local) {
        imported.set(key, local);
        continue;
      }
      // 운영 Library가 Variant Published Key를 재발행하면 Variant Key만
      // 바뀌고 Component Set Key는 유지될 수 있다. 이 경우 Set을 import해
      // Variant Property로 실제 Component를 찾아 호환한다.
      try {
        const componentSet = await figma.importComponentSetByKeyAsync(request.componentSetKey);
        const component = componentSet.children
          .filter(child => child.type === "COMPONENT")
          .find(child => variantNameMatches(child.name, request.variantProperties));
        if (!component || component.type !== "COMPONENT") throw new Error("해당 Variant를 찾을 수 없습니다.");
        imported.set(key, component);
      } catch (setError) {
        issues.push({
          code: "PUBLISHED_COMPONENT_IMPORT_FAILED",
          severity: "FATAL",
          message: `${request.logicalType} Published Variant/Component Set import 실패 `
            + `(variantKey=${key}, componentSetKey=${request.componentSetKey}, `
            + `variant=${JSON.stringify(request.variantProperties)}): `
            + (setError instanceof Error ? setError.message : "알 수 없는 오류"),
          logicalNodeId: request.logicalNodeId,
        });
      }
    }
  }
  return imported;
}

function findLocalComponent(
  variantKey: string,
  componentSetKey: string,
  variantProperties: Record<string, string>,
): ComponentNode | null {
  const localVariant = figma.root.findAll(node =>
    node.type === "COMPONENT" && node.key === variantKey) as ComponentNode[];
  if (localVariant.length > 0) return localVariant[0];
  const localSets = figma.root.findAll(node =>
    node.type === "COMPONENT_SET" && node.key === componentSetKey) as ComponentSetNode[];
  for (const set of localSets) {
    const match = set.children
      .filter(child => child.type === "COMPONENT")
      .find(child => variantNameMatches(child.name, variantProperties));
    if (match && match.type === "COMPONENT") return match;
  }
  return null;
}

function variantNameMatches(name: string, expected: Record<string, string>): boolean {
  const actual = Object.fromEntries(name.split(",").map(part => {
    const [key, ...value] = part.trim().split("=");
    return [key, value.join("=")];
  }).filter(([key, value]) => key && value));
  return Object.entries(expected).every(([key, value]) =>
    actual[key]?.toLowerCase() === String(value).toLowerCase());
}

async function applyOwnedProperties(
  instance: InstanceNode,
  mapped: Record<string, string | boolean>,
  logicalNodeId: string,
  suppressInternalLabel = false,
): Promise<void> {
  const previous = parseManagedProperties(instance.getPluginData(DATA_MANAGED_PROPERTIES));
  const next = { ...previous };
  const updates: Record<string, string | boolean> = {};
  for (const [baseName, value] of Object.entries(mapped)) {
    if (suppressInternalLabel && normalizePropertyName(baseName).includes("label")) {
      const actualLabelKey = Object.keys(instance.componentProperties)
        .find(key => normalizePropertyName(key) === normalizePropertyName(baseName));
      if (actualLabelKey && instance.componentProperties[actualLabelKey]?.type === "TEXT") {
        updates[actualLabelKey] = "";
        next[actualLabelKey] = "";
      }
      continue;
    }
    const actualKey = Object.keys(instance.componentProperties)
      .find(key => normalizePropertyName(key) === normalizePropertyName(baseName));
    // Registry의 Property Key에는 Library 버전에 따라 `↪️`, 공백, 줄바꿈
    // 같은 표시용 문자가 붙을 수 있다. 이 키를 그대로 비교하면 Label과
    // Placeholder가 조용히 누락되어 모든 화면이 Component 기본 문구로 보인다.
    // 서버가 계약한 Property를 실제 Instance에서 찾지 못한 경우에는 Apply를
    // 성공 처리하지 않고 FATAL로 되돌려, 잘못된 화면이 남지 않게 한다.
    if (!actualKey || !instance.componentProperties[actualKey]) {
      const textNode = findTextPropertyFallback(instance, baseName);
      if (!textNode || typeof value !== "string") {
        throw new Error(`Component Property를 찾을 수 없습니다: ${baseName} (${logicalNodeId})`);
      }
      const managedKey = `text:${normalizePropertyName(baseName)}`;
      const userOverrode = isUserOverridden(previous[managedKey], textNode.characters);
      if (!userOverrode) {
        await loadTextNodeFonts(textNode);
        textNode.characters = value;
        next[managedKey] = value;
      }
      continue;
    }
    const current = instance.componentProperties[actualKey]?.value;
    const userOverrode = isUserOverridden(previous[actualKey], current);
    if (userOverrode) continue;
    updates[actualKey] = value;
    next[actualKey] = value;
  }
  if (Object.keys(updates).length > 0) instance.setProperties(updates);
  instance.setPluginData(DATA_MANAGED_PROPERTIES, JSON.stringify(next));
}

async function ensureVisibleFieldLabel(
  wrapper: FrameNode,
  role: string,
  properties: Record<string, string | boolean>,
): Promise<void> {
  if (!role.startsWith("field.")) return;
  const label = Object.entries(properties).find(([key, value]) =>
    normalizePropertyName(key).includes("label") && typeof value === "string");
  if (!label || typeof label[1] !== "string" || !label[1].trim()) return;

  const existing = wrapper.children.find(child =>
    child.type === "TEXT" && child.getPluginData("figmaScreenSpec.generatedFieldLabel") === "true") as TextNode | undefined;
  const textNode = existing ?? figma.createText();
  await loadTextNodeFonts(textNode);
  textNode.name = "KRDS Field Label · generated";
  textNode.characters = label[1];
  textNode.fontSize = 14;
  textNode.setPluginData("figmaScreenSpec.generatedFieldLabel", "true");
  textNode.setPluginData("figmaScreenSpec.managedProperty", "Label");
  if (!existing) wrapper.insertChild(0, textNode);
}

async function loadTextNodeFonts(textNode: TextNode): Promise<void> {
  const length = Math.max(1, textNode.characters.length);
  const fonts = textNode.fontName === figma.mixed
    ? textNode.getRangeAllFontNames(0, length)
    : [textNode.fontName];
  const uniqueFonts = new Map(fonts.map(font => [`${font.family}:${font.style}`, font]));
  for (const font of uniqueFonts.values()) await figma.loadFontAsync(font);
}

/**
 * 일부 운영 Published Component는 Label/Placeholder를 Component Property로
 * 노출하지 않고 내부 TEXT Layer로만 가지고 있다. 이 경우 서버의 논리
 * Property를 내부 Layer에 적용해 업무 라벨이 기본 문구로 남지 않게 한다.
 */
function findTextPropertyFallback(instance: InstanceNode, baseName: string): TextNode | null {
  const logicalName = normalizePropertyName(baseName);
  const textNodes = instance.findAll(node =>
    node.type === "TEXT" && node.visible !== false) as TextNode[];
  if (textNodes.length === 0) return null;

  const named = textNodes.find(node => {
    const name = normalizePropertyName(node.name);
    return name.includes(logicalName)
      || (logicalName === "label" && name.includes("lable"))
      || (logicalName === "placeholder" && name.includes("placeHolder".toLowerCase()));
  });
  if (named) return named;

  if (logicalName === "helper" || logicalName === "helpertext"
      || logicalName === "error" || logicalName === "errormessage") return null;
  if (logicalName.includes("placeholder")) {
    return textNodes.find(node => /입력|선택|검색|placeholder/i.test(node.characters))
      ?? textNodes[1]
      ?? textNodes[0];
  }
  if (logicalName.includes("label") || logicalName.includes("title")) return textNodes[0];
  return textNodes[0];
}

function normalizePropertyName(name: string): string {
  return name
    .split("#")[0]
    .normalize("NFKC")
    .toLowerCase()
    .replace("lable", "label")
    // Published Component의 표시용 접두사(예: `↪️`)와 공백·구분자를
    // 제거해 `↪️Label`, `Label`, `Label#...`을 동일한 논리 Property로 본다.
    .replace(/[^\p{L}\p{N}]+/gu, "");
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
  return {
    logicalNodeId: spec.logicalNodeId,
    logicalType: spec.type,
    changeType,
    detail,
    componentKey: spec.componentResolution?.variantKey ?? null,
  };
}

function ssotEvidence(bundle: FigmaExportBundle): GenerationReport["ssotEvidence"] {
  const metadata = bundle.metadata;
  if (!metadata.catalogVersion || !metadata.catalogHash || !metadata.registryHash) return null;
  return {
    catalogVersion: metadata.catalogVersion,
    catalogHash: metadata.catalogHash,
    registryVersion: metadata.registryVersion,
    registryHash: metadata.registryHash,
  };
}
