export type ScreenStatus = "DRAFT" | "REVIEW_REQUIRED" | "APPROVED" | "SUPERSEDED";
export type NodeType = "PAGE" | "SECTION" | "COMPONENT" | "TEXT" | "SLOT" | "REPEAT";
export type SyncMode = "PREVIEW" | "MERGE" | "REPLACE";
export type BundleContractMode = "V1_MIGRATION_PREVIEW" | "V2_APPLY";
export type PublishStatus = "UNPUBLISHED" | "CURRENT" | "CHANGED";
export type PropertyType = "TEXT" | "BOOLEAN" | "VARIANT" | "INSTANCE_SWAP";

export type FigmaNodeSpec = {
  logicalNodeId: string;
  nodeType: NodeType;
  type: string;
  properties: Record<string, unknown>;
  componentResolution?: ComponentResolution | null;
  children: FigmaNodeSpec[];
};

export type ComponentResolution = {
  role: string;
  logicalType: string;
  componentSetKey: string;
  variantKey: string;
  variantProperties: Record<string, string>;
  componentProperties: Record<string, string | boolean>;
  contractVersion: string;
  ruleSetVersion: string;
  ruleId?: string | null;
  contextHash: string;
};

export type FigmaScreenSpec = {
  screenId: string;
  screenVersion: number;
  screenSpecificationId: string;
  screenSpecificationVersion: number;
  screenType: "LIST" | "FORM" | "DETAIL";
  layoutPattern: "STANDARD" | "MASTER_DETAIL" | "DASHBOARD";
  name: string;
  route?: string | null;
  viewport: string;
  status: ScreenStatus;
  designSystem: { profileId: string; profileVersion: string; registryVersion: string };
  content: FigmaNodeSpec;
  issues: ExportIssue[];
  semanticPattern?: "crud.list" | "crud.detail" | "crud.create" | "crud.edit";
  screenPatternVersion?: string;
  variantRuleSetVersion?: string;
  componentContractVersion?: string;
};

export type RegistryEntry = {
  componentSetKey: string;
  componentName?: string | null;
  publishStatus?: PublishStatus;
  variants?: Record<string, string>;
  properties: Record<string, {
    figmaProperty: string;
    type: PropertyType;
    values?: Record<string, string> | null;
  }>;
};

export type ComponentRegistry = {
  profileId: string;
  profileVersion: string;
  registryVersion: string;
  library?: { fileKey?: string | null; name?: string | null } | null;
  components: Record<string, RegistryEntry>;
  variables?: Record<string, unknown>;
};

export type DesignSystemProfile = {
  id: string;
  version: string;
  registryVersion: string;
  status?: string;
  libraryFileKey?: string | null;
};

export type ScreenPatternDefinition = {
  pattern: "crud.list" | "crud.detail" | "crud.create" | "crud.edit";
  version: string;
  status?: "DRAFT" | "APPROVED" | "PUBLISHED" | "SUPERSEDED";
  slots: Array<Record<string, unknown>>;
};

export type VariantRuleSet = {
  id: string;
  version: string;
  profileId: string;
  registryVersion: string;
  status: "DRAFT" | "APPROVED" | "PUBLISHED" | "SUPERSEDED";
  rules: Array<Record<string, unknown>>;
};

export type FigmaExportBundle = {
  figmaScreenSpec: FigmaScreenSpec;
  designSystemProfile: { profile: DesignSystemProfile; snapshotAt: string };
  componentRegistry: { registry: ComponentRegistry; snapshotAt: string };
  resolvedComponentRegistry?: {
    profileId: string;
    profileVersion: string;
    registryVersion: string;
    catalogVersion: string;
    catalogHash: string;
    registryHash: string;
    components: Record<string, RegistryEntry>;
  } | null;
  screenPattern: { pattern: ScreenPatternDefinition; snapshotAt: string };
  variantRuleSet: { ruleSet: VariantRuleSet; snapshotAt: string };
  metadata: {
    exportedAt: string;
    figmaScreenSpecSchemaVersion: string;
    screenSpecificationVersion: number;
    designSystemProfileVersion: string;
    registryVersion: string;
    screenPatternVersion?: string | null;
    variantRuleSetVersion?: string | null;
    componentContractVersion?: string | null;
    catalogVersion?: string | null;
    catalogHash?: string | null;
    registryHash?: string | null;
    /** R5-040: 이 Bundle을 생성한 FigmaDesignOperation. 7가지 요청 오케스트레이션 경로가 아니면 없음. */
    operationId?: string | null;
    /** 이 Bundle이 어느 생성 경로에서 나왔는지(STANDARD/ORCHESTRATED/HYBRID). */
    origin?: string | null;
  };
};

/** R5-040: GET /api/figma/operations/{operationId}/info 응답 중 Preview 표시에 필요한 부분. */
export type OperationInfo = {
  operationId: string;
  requestType?: string | null;
  status?: string | null;
  sourceRevision?: { sourceId: string; revisionToken: string; capturedAt: string } | null;
};

export type ExportIssue = {
  code: string;
  severity: "FATAL" | "ERROR" | "WARNING";
  message: string;
  logicalNodeId?: string | null;
};

export type ExistingLogicalNode = {
  logicalNodeId: string;
  logicalType: string;
  parentLogicalNodeId: string | null;
  order: number;
  detached?: boolean;
};

export type ReconciliationChange = {
  logicalNodeId: string;
  logicalType: string;
  changeType: "ADD" | "REUSE" | "MOVE" | "UPDATE" | "ARCHIVE" | "CONFLICT";
  detail: string;
  /** 실제 import/재사용된 Published Variant Component Key. 비 Component 변경은 생략한다. */
  componentKey?: string | null;
};

export type GenerationReport = {
  reportId: string;
  status: "SUCCESS" | "PARTIAL" | "FAILED";
  figmaScreenSpec: FigmaScreenSpec;
  generatedAt: string;
  screenId: string;
  screenVersion: number;
  mode: SyncMode;
  startedAt: string;
  completedAt: string;
  success: boolean;
  reusedInstanceCount: number;
  createdInstanceCount: number;
  archivedNodeCount: number;
  fallbackCount: number;
  changes: ReconciliationChange[];
  issues: ExportIssue[];
  qualityGates: QualityGateResult[];
  /** MR-Q05: 이번 적용에 사용된 Manual Refinement Patch Set 증적. 미적용 시 null/undefined. */
  refinement?: GenerationReportRefinementSummary | null;
  ssotEvidence?: GenerationReportSsotEvidence | null;
};

export type GenerationReportSsotEvidence = {
  catalogVersion: string;
  catalogHash: string;
  registryVersion: string;
  registryHash: string;
};

export type GenerationReportRefinementSummary = {
  patchSetId: string;
  patchSetVersion: number;
  appliedCount: number;
  excludedCount: number;
  conflictCount: number;
  blockedCount: number;
};

export type QualityGateResult = {
  gate: "LAYOUT" | "ACCESSIBILITY" | "VISUAL_REGRESSION";
  status: "PASSED" | "FAILED" | "BASELINE_CREATED";
  issueCodes: string[];
  evidenceHash?: string | null;
  baselineHash?: string | null;
  diffRatio?: number | null;
  threshold?: number | null;
  /** KRV-066: Section 단위 baseline으로 저장할 JSON(SectionEvidence[]). VISUAL_REGRESSION Gate에서만 채워진다. */
  sectionEvidenceJson?: string | null;
  /** KRV-066: 기준선 대비 변경된 Section의 logicalNodeId 목록. */
  changedSections?: string[] | null;
};

export type LegacyFrameNode = {
  nodeId: string;
  name: string;
  nodeType: string;
  logicalNodeId?: string | null;
  hasLocalInstance: boolean;
};

export type MigrationOperation = {
  nodeId: string | null;
  nodeName: string | null;
  logicalNodeId: string;
  logicalType: string;
  action: "REUSE" | "ASSIGN" | "ASSIGN_AND_REPLACE" | "MANUAL_REVIEW";
  componentSetKey?: string | null;
  reason: string;
};

export type MigrationPreview = {
  screenId: string;
  screenVersion: number;
  backupRequired: true;
  canApply: boolean;
  operations: MigrationOperation[];
  issues: ExportIssue[];
};

// --- Manual Refinement (docs/figma/17_..._Manual_Refinement_Implementation_List.md) ---

export type RefinementStatus =
  "DRAFT" | "CAPTURED" | "REVIEW_REQUIRED" | "APPROVED" | "REJECTED" | "APPLIED" | "SUPERSEDED";

export type RefinementOwner = "SCREEN_SPEC" | "DESIGN_SYSTEM" | "MANUAL_REFINEMENT" | "SYSTEM_LAYOUT" | "RUNTIME_DATA";

export type RefinementScope = "ALLOWED" | "CONDITIONAL" | "BLOCKED";

export type RefinementPropertyType = "COLOR" | "NUMBER" | "STRING" | "BOOLEAN" | "ENUM";

export type RefinementConflictStatus =
  "NONE" | "UPSTREAM_CHANGED" | "TARGET_REMOVED" | "TYPE_CHANGED" | "POLICY_BLOCKED" | "BASE_STALE";

/** MR-DEC-04: MVP 허용 속성 경로. `refinement/policy.ts`의 단일 기준. */
export type RefinementPropertyPath =
  | "fill" | "stroke" | "opacity" | "cornerRadius"
  | "typography.fontFamily" | "typography.fontStyle" | "typography.fontSize"
  | "typography.letterSpacing" | "typography.lineHeight"
  | "padding.top" | "padding.right" | "padding.bottom" | "padding.left"
  | "itemSpacing" | "textAlign"
  | "width" | "height" | "minWidth" | "minHeight" | "layoutGrow" | "layoutAlign";

export type RefinementPatch = {
  logicalNodeId: string;
  baselineLogicalType: string;
  propertyPath: string;
  propertyType: RefinementPropertyType;
  before: unknown;
  after: unknown;
  owner: RefinementOwner;
  scope: RefinementScope;
  conflictStatus: RefinementConflictStatus;
};

export type RefinementPatchSet = {
  patchSetId: string;
  screenId: string;
  baseScreenVersion: number;
  baseMaterializationHash: string;
  status: RefinementStatus;
  capturedAt?: string | null;
  approvedAt?: string | null;
  approvedBy?: string | null;
  approvalComment?: string | null;
  patches: RefinementPatch[];
};

export type RefinementPreviewEntry = {
  logicalNodeId: string;
  propertyPath: string;
  reason: string;
  conflictStatus?: RefinementConflictStatus | null;
};

export type RefinementPreview = {
  patchSetId: string;
  screenId: string;
  generatedAt: string;
  applied: RefinementPreviewEntry[];
  excluded: RefinementPreviewEntry[];
  blocked: RefinementPreviewEntry[];
  conflicts: RefinementPreviewEntry[];
};

/** MR-P03: Capture 시점 기준 Snapshot 한 노드. `snapshot.ts`가 만들고 `diff.ts`가 비교한다. */
export type RefinementSnapshotEntry = {
  logicalNodeId: string;
  logicalType: string;
  properties: Record<string, unknown>;
};
