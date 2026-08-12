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
  };
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
