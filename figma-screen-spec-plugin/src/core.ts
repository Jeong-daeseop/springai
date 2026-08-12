import type {
  ComponentRegistry,
  BundleContractMode,
  ExistingLogicalNode,
  ExportIssue,
  FigmaExportBundle,
  FigmaNodeSpec,
  LegacyFrameNode,
  MigrationPreview,
  ReconciliationChange,
  RegistryEntry,
} from "./types";

export const FIGMA_SCREEN_SPEC_V1 = "figma-screen-spec-v1";
export const FIGMA_SCREEN_SPEC_V2 = "figma-screen-spec-v2";

export function flattenSpec(root: FigmaNodeSpec): Array<{
  node: FigmaNodeSpec;
  parentLogicalNodeId: string | null;
  order: number;
}> {
  const result: Array<{ node: FigmaNodeSpec; parentLogicalNodeId: string | null; order: number }> = [];
  const visit = (node: FigmaNodeSpec, parentLogicalNodeId: string | null, order: number) => {
    result.push({ node, parentLogicalNodeId, order });
    node.children.forEach((child, index) => visit(child, node.logicalNodeId, index));
  };
  visit(root, null, 0);
  return result;
}

export function validateBundle(bundle: unknown): {
  parsed?: FigmaExportBundle;
  issues: ExportIssue[];
  contractMode?: BundleContractMode;
} {
  const issues: ExportIssue[] = [];
  if (!bundle || typeof bundle !== "object") {
    return { issues: [fatal("BUNDLE_INVALID", "최상위 값이 object가 아닙니다.")] };
  }
  const candidate = bundle as Partial<FigmaExportBundle>;
  const screen = candidate.figmaScreenSpec;
  const profile = candidate.designSystemProfile?.profile;
  const registry = candidate.componentRegistry?.registry;
  const pattern = candidate.screenPattern?.pattern;
  const ruleSet = candidate.variantRuleSet?.ruleSet;
  const metadata = candidate.metadata;
  if (!screen?.screenId || !screen.content) issues.push(fatal("SCREEN_SPEC_MISSING", "FigmaScreenSpec이 없습니다."));
  if (!profile?.id || !profile.version) issues.push(fatal("PROFILE_MISSING", "DesignSystemProfile Snapshot이 없습니다."));
  if (!registry?.profileId || !registry.registryVersion) issues.push(fatal("REGISTRY_MISSING", "ComponentRegistry Snapshot이 없습니다."));
  if (!metadata?.figmaScreenSpecSchemaVersion) issues.push(fatal("METADATA_MISSING", "Export Metadata가 없습니다."));
  if (issues.length || !screen || !profile || !registry || !metadata) return { issues };

  const schemaVersion = metadata.figmaScreenSpecSchemaVersion;
  const contractMode: BundleContractMode | undefined = schemaVersion === FIGMA_SCREEN_SPEC_V2
    ? "V2_APPLY"
    : schemaVersion === FIGMA_SCREEN_SPEC_V1 ? "V1_MIGRATION_PREVIEW" : undefined;
  if (!contractMode) {
    issues.push(fatal("SCHEMA_VERSION_UNSUPPORTED", `지원하지 않는 Schema입니다: ${metadata.figmaScreenSpecSchemaVersion}`));
  }
  if (contractMode === "V1_MIGRATION_PREVIEW") {
    issues.push({
      code: "LEGACY_SCHEMA_MIGRATION_PREVIEW_ONLY",
      severity: "WARNING",
      message: "figma-screen-spec-v1은 Legacy Migration Preview만 지원합니다. 일반 Apply는 v2 Bundle이 필요합니다.",
    });
  }
  if (contractMode === "V2_APPLY" && (!screen.semanticPattern || !screen.screenPatternVersion
      || !screen.variantRuleSetVersion || !screen.componentContractVersion)) {
    issues.push(fatal("SCREEN_SPEC_V2_REQUIRED", "Role·Variant v2 실행 명세가 필요합니다."));
  }
  if (contractMode === "V2_APPLY" && !pattern) {
    issues.push(fatal("SCREEN_PATTERN_SNAPSHOT_MISSING", "Screen Pattern Snapshot이 없습니다."));
  }
  if (contractMode === "V2_APPLY" && !ruleSet) {
    issues.push(fatal("VARIANT_RULE_SET_SNAPSHOT_MISSING", "Variant Rule Set Snapshot이 없습니다."));
  }
  if (contractMode === "V2_APPLY" && pattern) {
    if (pattern.status !== "PUBLISHED") {
      issues.push(fatal("SCREEN_PATTERN_NOT_PUBLISHED", `Pattern 상태가 PUBLISHED가 아닙니다: ${pattern.status ?? "UNKNOWN"}`));
    }
    if (pattern.pattern !== screen.semanticPattern || pattern.version !== screen.screenPatternVersion) {
      issues.push(fatal("SCREEN_PATTERN_SNAPSHOT_MISMATCH", "Screen Spec과 Pattern Snapshot 버전이 다릅니다."));
    }
  }
  if (contractMode === "V2_APPLY" && ruleSet) {
    if (ruleSet.status !== "PUBLISHED") {
      issues.push(fatal("VARIANT_RULE_SET_NOT_PUBLISHED", `Rule Set 상태가 PUBLISHED가 아닙니다: ${ruleSet.status}`));
    }
    if (ruleSet.version !== screen.variantRuleSetVersion
        || ruleSet.profileId !== screen.designSystem.profileId
        || ruleSet.registryVersion !== screen.designSystem.registryVersion) {
      issues.push(fatal("VARIANT_RULE_SET_SNAPSHOT_MISMATCH", "Screen Spec과 Rule Set Snapshot 버전이 다릅니다."));
    }
  }
  if (profile.status !== "PUBLISHED") {
    issues.push(fatal("PROFILE_NOT_PUBLISHED", `Profile 상태가 PUBLISHED가 아닙니다: ${profile.status ?? "UNKNOWN"}`));
  }
  if (profile.libraryFileKey && registry.library?.fileKey
      && profile.libraryFileKey !== registry.library.fileKey) {
    issues.push(fatal("LIBRARY_FILE_KEY_MISMATCH", `${profile.libraryFileKey} != ${registry.library.fileKey}`));
  }
  const versions: Array<[unknown, unknown, string]> = [
    [screen.designSystem.profileId, profile.id, "SCREEN_PROFILE_ID_MISMATCH"],
    [screen.designSystem.profileId, registry.profileId, "REGISTRY_PROFILE_ID_MISMATCH"],
    [screen.designSystem.profileVersion, profile.version, "PROFILE_VERSION_MISMATCH"],
    [screen.designSystem.profileVersion, registry.profileVersion, "REGISTRY_PROFILE_VERSION_MISMATCH"],
    [screen.designSystem.registryVersion, registry.registryVersion, "REGISTRY_VERSION_MISMATCH"],
    [metadata.registryVersion, registry.registryVersion, "METADATA_REGISTRY_VERSION_MISMATCH"],
    [metadata.designSystemProfileVersion, profile.version, "METADATA_PROFILE_VERSION_MISMATCH"],
    [metadata.screenSpecificationVersion, screen.screenSpecificationVersion, "SCREEN_SPEC_VERSION_MISMATCH"],
    [metadata.screenPatternVersion, screen.screenPatternVersion, "SCREEN_PATTERN_VERSION_MISMATCH"],
    [metadata.variantRuleSetVersion, screen.variantRuleSetVersion, "VARIANT_RULE_SET_VERSION_MISMATCH"],
    [metadata.componentContractVersion, screen.componentContractVersion, "COMPONENT_CONTRACT_VERSION_MISMATCH"],
  ];
  for (const [left, right, code] of versions) {
    if (left !== right) issues.push(fatal(code, `${left} != ${right}`));
  }
  if (profile.registryVersion !== registry.registryVersion) {
    issues.push(fatal("PROFILE_REGISTRY_VERSION_MISMATCH", `${profile.registryVersion} != ${registry.registryVersion}`));
  }
  issues.push(...(screen.issues ?? []));

  const seen = new Set<string>();
  for (const { node } of flattenSpec(screen.content)) {
    if (!node.logicalNodeId) issues.push(fatal("LOGICAL_NODE_ID_MISSING", "logicalNodeId가 비어 있습니다."));
    else if (seen.has(node.logicalNodeId)) {
      issues.push(fatal("DUPLICATE_LOGICAL_NODE_ID", "logicalNodeId가 중복되었습니다.", node.logicalNodeId));
    }
    seen.add(node.logicalNodeId);
    if (contractMode === "V2_APPLY" && requiresPublishedComponent(node)) {
      const entry = registry.components[node.type];
      if (!entry) issues.push(fatal("REQUIRED_COMPONENT_MISSING", `필수 Component가 Registry에 없습니다: ${node.type}`, node.logicalNodeId));
      else if (!entry.componentSetKey || entry.publishStatus !== "CURRENT") {
        issues.push(fatal("REQUIRED_COMPONENT_NOT_CURRENT", `${node.type}의 Publish 상태가 CURRENT가 아닙니다.`, node.logicalNodeId));
      }
    }
    if (contractMode === "V2_APPLY"
        && node.nodeType === "COMPONENT" && typeof node.properties.semanticRole === "string") {
      const resolution = node.componentResolution;
      if (!resolution) {
        issues.push(fatal("ROLE_NOT_RESOLVED", "Semantic Role의 Component 해석 결과가 없습니다.", node.logicalNodeId));
      } else if (!resolution.variantKey) {
        issues.push(fatal("VARIANT_NOT_RESOLVED", "Published Variant Key가 없습니다.", node.logicalNodeId));
      } else if (resolution.logicalType !== node.type) {
        issues.push(fatal("RESOLVED_LOGICAL_TYPE_MISMATCH", `${resolution.logicalType} != ${node.type}`, node.logicalNodeId));
      } else {
        const entry = registry.components[node.type];
        if (!entry || entry.componentSetKey !== resolution.componentSetKey) {
          issues.push(fatal("RESOLVED_COMPONENT_SET_MISMATCH", "Registry와 해결된 Component Set이 다릅니다.", node.logicalNodeId));
        } else if (Object.keys(entry.variants ?? {}).length > 0
            && !Object.values(entry.variants ?? {}).includes(resolution.variantKey)) {
          issues.push(fatal("RESOLVED_VARIANT_NOT_IN_REGISTRY", "해결된 Variant Key가 Registry에 없습니다.", node.logicalNodeId));
        } else if (resolution.ruleSetVersion !== screen.variantRuleSetVersion
            || resolution.contractVersion !== screen.componentContractVersion) {
          issues.push(fatal("RESOLUTION_VERSION_MISMATCH", "노드 Resolution 버전이 화면 버전과 다릅니다.", node.logicalNodeId));
        }
      }
    }
  }
  return { parsed: candidate as FigmaExportBundle, issues, contractMode };
}

export function requiresPublishedComponent(node: FigmaNodeSpec): boolean {
  if (node.nodeType !== "COMPONENT") return false;
  return new Set([
    "krds.button", "krds.textField", "krds.textarea", "krds.select", "krds.checkbox",
    "krds.pagination", "krds.pageHeader", "krds.searchPanel", "krds.tableCell",
    "egov.pageHeader", "egov.searchPanel", "egov.dataTable", "egov.formSection", "egov.actionArea",
    "egov.listPage", "egov.formPage",
  ]).has(node.type);
}

export type LayoutAnnotation = {
  /** wrapper.name에 덧붙일 사람이 읽는 주석(예: " [sticky][bp:mobile,tablet]"). 없으면 빈 문자열. */
  nameSuffix: string;
  /** wrapper에 그대로 저장할 pluginData 키/값. Figma에 네이티브 대응 개념이 없어 메타데이터로만 보존한다. */
  pluginData: Record<string, string>;
};

const SIGNIFICANT_OVERFLOW = new Set(["hidden", "scroll", "auto", "clip"]);
const STICKY_POSITIONS = new Set(["sticky", "fixed"]);

/**
 * R5-032/033: Figma에는 responsive breakpoint·overflow·position:sticky/fixed에 대응하는
 * 네이티브 개념이 없다. 렌더 캡처(RenderedNode.styles)나 Spec properties에 이 값들이 있으면
 * 화면을 실제로 그렇게 만들 수는 없어도, 사람이 layer 이름과 pluginData로 그 의미를 알 수
 * 있게 "주석"으로 보존한다.
 */
export function describeLayoutAnnotations(properties: Record<string, unknown>): LayoutAnnotation {
  const pluginData: Record<string, string> = {};
  const tags: string[] = [];

  const position = typeof properties.position === "string" ? properties.position.toLowerCase() : null;
  if (position && STICKY_POSITIONS.has(position)) {
    pluginData.position = position;
    tags.push(position);
  }

  const overflow = typeof properties.overflow === "string" ? properties.overflow.toLowerCase() : null;
  if (overflow && SIGNIFICANT_OVERFLOW.has(overflow)) {
    pluginData.overflow = overflow;
    tags.push(`overflow:${overflow}`);
  }

  const breakpoints = properties.responsiveBreakpoints;
  if (breakpoints && typeof breakpoints === "object" && !Array.isArray(breakpoints)) {
    const names = Object.keys(breakpoints as Record<string, unknown>);
    if (names.length > 0) {
      pluginData.responsiveBreakpoints = JSON.stringify(breakpoints);
      tags.push(`bp:${names.join(",")}`);
    }
  }

  return {
    nameSuffix: tags.length ? ` [${tags.join("][")}]` : "",
    pluginData,
  };
}

export type FallbackPlan = {
  label: string;
  issue: ExportIssue;
};

/**
 * R5-016: 필수 Component(requiresPublishedComponent)는 Registry에 없으면 validateBundle에서
 * FATAL로 막혀 여기까지 오지 않는다. 선택 Component(nodeType=COMPONENT인데 1차 필수 카탈로그
 * 밖이라 Registry에 없을 수 있는 유형)만 시각적 fallback 대상으로 판단한다.
 */
export function planFallback(node: FigmaNodeSpec, registry: ComponentRegistry): FallbackPlan | null {
  if (node.nodeType !== "COMPONENT") return null;
  if (registry.components[node.type]) return null;
  if (requiresPublishedComponent(node)) return null;
  return {
    label: `⚠ ${node.type} (Registry 없음)`,
    issue: {
      code: "OPTIONAL_COMPONENT_NOT_IN_REGISTRY",
      severity: "WARNING",
      message: `선택 Component가 Registry에 없어 시각적 fallback으로 대체했습니다: ${node.type}`,
      logicalNodeId: node.logicalNodeId,
    },
  };
}

/** 실제 생성 결과의 fallback을 SUCCESS로 숨기지 않기 위한 보고 상태 정책. */
export function generationStatus(
  fatal: boolean,
  fallbackCount: number,
): "SUCCESS" | "PARTIAL" | "FAILED" {
  if (fatal) return "FAILED";
  return fallbackCount > 0 ? "FAILED" : "SUCCESS";
}

/** 승인 기준선이 없으면 최초 기준선을 만들고, 이후에는 결정형 이미지 Hash를 엄격 비교한다. */
export function visualRegressionStatus(
  evidenceHash: string,
  baselineHash?: string | null,
  baselineRequired = false,
): "PASSED" | "FAILED" | "BASELINE_CREATED" {
  if (!baselineHash) return baselineRequired ? "FAILED" : "BASELINE_CREATED";
  return evidenceHash === baselineHash ? "PASSED" : "FAILED";
}

export type SectionEvidence = { sectionId: string; hash: string };

export type SectionVisualComparison = {
  status: "PASSED" | "FAILED" | "BASELINE_CREATED";
  diffRatio: number;
  threshold: number;
  changedSections: string[];
};

/**
 * KRV-066: 화면 전체를 단일 Hash로 묶어 0%/100%로만 비교하는 대신, 화면을 구성하는 주요
 * Section(예: header/action/table 같은 최상위 Wrapper Frame) 단위로 Hash를 비교해 실제
 * 변경 비율(diffRatio = 변경된 Section 수 / 전체 Section 수)을 계산한다. 새 Section이
 * 추가되거나 기존 Section이 사라진 경우도 변경으로 집계한다.
 */
export function sectionVisualRegression(
  evidence: SectionEvidence[],
  baseline: SectionEvidence[] | null | undefined,
  threshold = 0,
  baselineRequired = false,
): SectionVisualComparison {
  if (!baseline || baseline.length === 0) {
    return {
      status: baselineRequired ? "FAILED" : "BASELINE_CREATED",
      diffRatio: 0,
      threshold,
      changedSections: [],
    };
  }
  const baselineBySection = new Map(baseline.map(entry => [entry.sectionId, entry.hash]));
  const evidenceIds = new Set(evidence.map(entry => entry.sectionId));
  const changed = new Set<string>();
  for (const entry of evidence) {
    const baselineHash = baselineBySection.get(entry.sectionId);
    if (baselineHash === undefined || baselineHash !== entry.hash) changed.add(entry.sectionId);
  }
  for (const entry of baseline) {
    if (!evidenceIds.has(entry.sectionId)) changed.add(entry.sectionId);
  }
  const totalSections = new Set([...baselineBySection.keys(), ...evidenceIds]).size;
  const diffRatio = totalSections === 0 ? 0 : changed.size / totalSections;
  return {
    status: diffRatio <= threshold ? "PASSED" : "FAILED",
    diffRatio,
    threshold,
    changedSections: Array.from(changed).sort(),
  };
}

export type AtomicApplyHooks<Backup, Staging, Result> = {
  createBackup: () => Promise<Backup>;
  createStaging: (backup: Backup) => Promise<Staging>;
  populateStaging: (staging: Staging, backup: Backup) => Promise<void>;
  validateStaging: (staging: Staging, backup: Backup) => Promise<void>;
  commit: (staging: Staging, backup: Backup) => Promise<Result>;
  rollback: (staging: Staging | undefined, backup: Backup | undefined, cause: unknown) => Promise<void>;
};

/**
 * Apply의 부수 효과 순서를 고정한다. Staging 생성 이후 어느 단계에서 실패하더라도
 * rollback이 실행되며, commit 전에는 기존 Root를 변경하지 않는 것이 Port의 계약이다.
 */
export async function runAtomicApply<Backup, Staging, Result>(
  hooks: AtomicApplyHooks<Backup, Staging, Result>,
): Promise<Result> {
  let backup: Backup | undefined;
  let staging: Staging | undefined;
  try {
    backup = await hooks.createBackup();
    staging = await hooks.createStaging(backup);
    await hooks.populateStaging(staging, backup);
    await hooks.validateStaging(staging, backup);
    return await hooks.commit(staging, backup);
  } catch (error) {
    try {
      await hooks.rollback(staging, backup, error);
    } catch (rollbackError) {
      throw new Error(
        `Apply 실패 후 Rollback에도 실패했습니다: ${rollbackError instanceof Error ? rollbackError.message : String(rollbackError)}`,
        { cause: error },
      );
    }
    throw error;
  }
}

export function reconcile(
  root: FigmaNodeSpec,
  existing: ExistingLogicalNode[],
): ReconciliationChange[] {
  const desired = flattenSpec(root);
  const existingById = new Map(existing.map(node => [node.logicalNodeId, node]));
  const changes: ReconciliationChange[] = [];
  for (const desiredNode of desired) {
    const current = existingById.get(desiredNode.node.logicalNodeId);
    if (!current) {
      changes.push(change(desiredNode.node, "ADD", "신규 논리 노드"));
      continue;
    }
    existingById.delete(desiredNode.node.logicalNodeId);
    if (current.detached) {
      changes.push(change(desiredNode.node, "CONFLICT", "Library 연결이 해제된 Instance가 있습니다."));
    } else if (current.logicalType !== desiredNode.node.type) {
      changes.push(change(desiredNode.node, "UPDATE", `${current.logicalType} → ${desiredNode.node.type}`));
    } else if (current.parentLogicalNodeId !== desiredNode.parentLogicalNodeId || current.order !== desiredNode.order) {
      changes.push(change(desiredNode.node, "MOVE", "부모 또는 순서 변경"));
    } else {
      changes.push(change(desiredNode.node, "REUSE", "기존 논리 노드 재사용"));
    }
  }
  for (const remaining of existingById.values()) {
    changes.push({
      logicalNodeId: remaining.logicalNodeId,
      logicalType: remaining.logicalType,
      changeType: "ARCHIVE",
      detail: "새 Spec에서 제거되어 Archive 대상",
    });
  }
  return changes;
}

export function mappedProperties(
  logicalProperties: Record<string, unknown>,
  entry: RegistryEntry,
): Record<string, string | boolean> {
  const result: Record<string, string | boolean> = {};
  const source = new Map(Object.entries(logicalProperties).map(([key, value]) => [key.toLowerCase(), value]));
  for (const [logicalName, mapping] of Object.entries(entry.properties ?? {})) {
    const propertyName = mapping.figmaProperty.toLowerCase();
    let raw = source.get(logicalName.toLowerCase()) ?? source.get(propertyName);
    if (raw === undefined && ["style", "type", "variant"].includes(propertyName)) {
      raw = source.get("variant") ?? source.get("control");
    }
    if (raw === undefined && propertyName === "label") {
      const actionType = source.get("actiontype");
      raw = actionType ? actionLabel(String(actionType)) : source.get("title");
    }
    if (raw === undefined || raw === null) continue;
    if (mapping.type === "BOOLEAN") result[mapping.figmaProperty] = raw === true || raw === "true";
    else {
      const value = String(raw);
      const mappedValue = Object.entries(mapping.values ?? {})
        .find(([candidate]) => candidate.toLowerCase() === value.toLowerCase())?.[1];
      result[mapping.figmaProperty] = mappedValue ?? value;
    }
  }
  return result;
}

function actionLabel(actionType: string): string {
  return ({
    SEARCH: "검색", CREATE: "등록", SAVE: "저장", UPDATE: "수정",
    DELETE: "삭제", CANCEL: "취소", VIEW_DETAIL: "상세",
  } as Record<string, string>)[actionType] ?? actionType;
}

export function selectVariantName(
  properties: Record<string, string | boolean>,
  entry: RegistryEntry,
): string | null {
  const variantParts = Object.entries(entry.properties ?? {})
    .filter(([, mapping]) => mapping.type === "VARIANT")
    .map(([, mapping]) => {
      const value = properties[mapping.figmaProperty];
      return value === undefined ? null : `${mapping.figmaProperty}=${String(value)}`;
    })
    .filter((value): value is string => value !== null);
  if (variantParts.length === 0) return null;
  const matches = Object.keys(entry.variants ?? {}).filter(name =>
    variantParts.length === name.split(",").length
    && variantParts.every(part => name.split(",").map(value => value.trim()).includes(part)));
  return matches.length === 1 ? matches[0] : null;
}

function fatal(code: string, message: string, logicalNodeId?: string): ExportIssue {
  return { code, severity: "FATAL", message, logicalNodeId };
}

function change(
  node: FigmaNodeSpec,
  changeType: ReconciliationChange["changeType"],
  detail: string,
): ReconciliationChange {
  return { logicalNodeId: node.logicalNodeId, logicalType: node.type, changeType, detail };
}

export function registryFor(bundle: FigmaExportBundle): ComponentRegistry {
  return bundle.componentRegistry.registry;
}

/**
 * 기존 Frame의 이름과 이미 부여된 logicalNodeId를 승인된 Spec에 결정론적으로 매핑한다.
 * 모호한 후보는 자동 확정하지 않고 MANUAL_REVIEW로 남긴다.
 */
export function previewLegacyMigration(
  bundle: FigmaExportBundle,
  legacyNodes: LegacyFrameNode[],
): MigrationPreview {
  const validated = validateBundle(bundle);
  if (!validated.parsed) {
    return {
      screenId: "",
      screenVersion: 0,
      backupRequired: true,
      canApply: false,
      operations: [],
      issues: validated.issues,
    };
  }
  const screen = validated.parsed.figmaScreenSpec;
  const registry = registryFor(validated.parsed);
  const remaining = new Map(legacyNodes.map(node => [node.nodeId, node]));
  const operations = flattenSpec(screen.content).map(({node}) => {
    const exact = legacyNodes.find(candidate =>
      candidate.logicalNodeId === node.logicalNodeId && remaining.has(candidate.nodeId));
    const candidates = exact ? [exact] : legacyNodes
      .filter(candidate => remaining.has(candidate.nodeId))
      .filter(candidate => migrationScore(candidate, node) >= 2);
    if (candidates.length !== 1) {
      return {
        nodeId: null,
        nodeName: null,
        logicalNodeId: node.logicalNodeId,
        logicalType: node.type,
        action: "MANUAL_REVIEW" as const,
        componentSetKey: registry.components[node.type]?.componentSetKey ?? null,
        reason: candidates.length === 0
          ? "일치하는 기존 Frame을 찾지 못했습니다."
          : `동일 점수 후보가 ${candidates.length}개라 자동 확정할 수 없습니다.`,
      };
    }
    const selected = candidates[0];
    remaining.delete(selected.nodeId);
    const entry = registry.components[node.type];
    const replace = selected.hasLocalInstance && Boolean(entry?.componentSetKey);
    return {
      nodeId: selected.nodeId,
      nodeName: selected.name,
      logicalNodeId: node.logicalNodeId,
      logicalType: node.type,
      action: exact ? "REUSE" as const
        : replace ? "ASSIGN_AND_REPLACE" as const : "ASSIGN" as const,
      componentSetKey: entry?.componentSetKey ?? null,
      reason: exact
        ? "기존 logicalNodeId가 승인된 Spec과 일치합니다."
        : replace
          ? "이름/타입 매핑 후 로컬 Instance를 Published Instance로 교체합니다."
          : "이름/타입 매핑으로 logicalNodeId를 부여합니다.",
    };
  });
  const manual = operations.filter(operation => operation.action === "MANUAL_REVIEW");
  const issues = [...validated.issues];
  if (manual.length > 0) {
    issues.push({
      code: "MIGRATION_MAPPING_INCOMPLETE",
      severity: "WARNING",
      message: `사람 확인이 필요한 논리 노드가 ${manual.length}개 있습니다.`,
    });
  }
  return {
    screenId: screen.screenId,
    screenVersion: screen.screenVersion,
    backupRequired: true,
    canApply: validated.contractMode === "V2_APPLY"
      && manual.length === 0
      && !issues.some(issue => issue.severity === "FATAL" || issue.severity === "ERROR"),
    operations,
    issues,
  };
}

function migrationScore(legacy: LegacyFrameNode, spec: FigmaNodeSpec): number {
  const name = normalizeMigrationValue(legacy.name);
  const logicalId = normalizeMigrationValue(spec.logicalNodeId.split("/").at(-1) ?? spec.logicalNodeId);
  const label = normalizeMigrationValue(
    String(spec.properties.label ?? spec.properties.title ?? spec.properties.actionType ?? ""),
  );
  const logicalType = normalizeMigrationValue(spec.type.split(".").at(-1) ?? spec.type);
  let score = 0;
  if (logicalId && name.includes(logicalId)) score += 2;
  if (label && name.includes(label)) score += 2;
  if (logicalType && name.includes(logicalType)) score += 1;
  if (legacy.nodeType === "FRAME") score += 1;
  return score;
}

function normalizeMigrationValue(value: string): string {
  return value.toLocaleLowerCase().replace(/[^a-z0-9가-힣]/g, "");
}
