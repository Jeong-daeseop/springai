export type TokenCategory = "COLOR" | "SPACING" | "TYPOGRAPHY" | "RADIUS" | "ELEVATION";
export type Token = { category: TokenCategory; name: string; value: string };
export type VariableCollectionSpec = {
  name: string;
  modes: string[];
  valuesByMode: Record<string, Record<string, string>>;
};
export type PropertyType = "TEXT" | "BOOLEAN" | "VARIANT" | "INSTANCE_SWAP";
export type ComponentProperty = { name: string; type: PropertyType; defaultValue?: string | null };
export type ComponentLayout = {
  mode?: string;
  paddingX?: string;
  paddingY?: string;
  gap?: string;
  alignment?: string;
  minWidth?: string;
  maxWidth?: string;
  minHeight?: string;
  maxHeight?: string;
} | null;
export type DeveloperMetadata = {
  codeComponent?: string | null;
  documentationUrl?: string | null;
  packageName?: string | null;
};
export type ComponentDefinition = {
  id: string;
  name: string;
  description?: string | null;
  developer?: DeveloperMetadata | null;
  lifecycleStatus?: "ACTIVE" | "DEPRECATED";
  replacementLogicalType?: string | null;
  aliases?: string[];
  layout?: ComponentLayout | null;
  properties: ComponentProperty[];
  variants: Record<string, string[]>;
};
export type PatternDefinition = { id: string; name: string; composedOf: string[] };
export type Issue = {
  code: string;
  severity: "FATAL" | "ERROR" | "WARNING";
  message: string;
  targetId?: string | null;
};
export type DesignSystemSpec = {
  id: string;
  name: string;
  version: string;
  tokens: Token[];
  variableCollections: VariableCollectionSpec[];
  components: ComponentDefinition[];
  patterns: PatternDefinition[];
  issues: Issue[];
};
export type ValidationIssue = {
  code: string;
  path: string;
  message: string;
  targetId?: string;
};
export type ReviewStatus = "DRAFT" | "IN_REVIEW" | "APPROVED" | "REJECTED";
export type ReviewEvent = "REVIEW" | "APPROVAL" | "REJECTION";
export type Comparison = {
  field: string;
  before: string;
  after: string;
  change: "ADD" | "UPDATE" | "REMOVE";
};
export type DiffKind = "ADD" | "UPDATE" | "NO_CHANGE" | "BREAKING" | "DEPRECATE";

export function validateSpec(spec: unknown): { errors: ValidationIssue[]; parsed?: DesignSystemSpec } {
  const errors: ValidationIssue[] = [];
  if (typeof spec !== "object" || spec === null) {
    return {
      errors: [{ code: "SPEC_TYPE", path: "", message: "최상위 값이 object가 아닙니다." }],
    };
  }
  const value = spec as Record<string, unknown>;
  for (const field of ["id", "name", "version"]) {
    if (typeof value[field] !== "string" || (value[field] as string).length === 0) {
      errors.push({
        code: "REQUIRED",
        path: `/${field}`,
        message: `${field}은(는) 비어 있지 않은 문자열이어야 합니다.`,
      });
    }
  }

  const components = Array.isArray(value.components)
    ? value.components as ComponentDefinition[] : [];
  const patterns = Array.isArray(value.patterns)
    ? value.patterns as PatternDefinition[] : [];
  const tokens = Array.isArray(value.tokens) ? value.tokens as Token[] : [];
  const variableCollections = Array.isArray(value.variableCollections)
    ? value.variableCollections as VariableCollectionSpec[] : [];

  const seenComponentIds = new Set<string>();
  components.forEach((component, componentIndex) => {
    const componentPath = `/components/${componentIndex}`;
    if (!component.id) {
      errors.push({
        code: "COMPONENT_ID_REQUIRED",
        path: `${componentPath}/id`,
        message: "컴포넌트에 id가 없습니다.",
      });
      return;
    }
    if (seenComponentIds.has(component.id)) {
      errors.push({
        code: "DUPLICATE_COMPONENT_ID",
        path: `${componentPath}/id`,
        targetId: component.id,
        message: `컴포넌트 id가 중복되었습니다: ${component.id}`,
      });
    }
    seenComponentIds.add(component.id);
    (component.properties ?? []).forEach((property, propertyIndex) => {
      if (property.type === "VARIANT" && !(component.variants ?? {})[property.name]) {
        errors.push({
          code: "VARIANT_OPTIONS_REQUIRED",
          path: `${componentPath}/properties/${propertyIndex}`,
          targetId: component.id,
          message: `컴포넌트 ${component.id}의 속성 ${property.name}이(가) VARIANT 타입인데 variants에 옵션이 없습니다.`,
        });
      }
    });
    const layout = component.layout;
    if (layout) {
      validateSizeRange(layout.minWidth, layout.maxWidth, "width", componentPath, component.id, errors);
      validateSizeRange(layout.minHeight, layout.maxHeight, "height", componentPath, component.id, errors);
    }
    if (component.developer?.documentationUrl) {
      try {
        new URL(component.developer.documentationUrl);
      } catch {
        errors.push({
          code: "INVALID_DOCUMENTATION_URL",
          path: `${componentPath}/developer/documentationUrl`,
          targetId: component.id,
          message: `컴포넌트 ${component.id}의 documentationUrl이 올바르지 않습니다.`,
        });
      }
    }
  });

  const aliasOwners = new Map<string, string>();
  components.forEach((component, componentIndex) => {
    for (const alias of component.aliases ?? []) {
      const owner = aliasOwners.get(alias);
      if (seenComponentIds.has(alias) || owner) {
        errors.push({
          code: "COMPONENT_ALIAS_CONFLICT",
          path: `/components/${componentIndex}/aliases`,
          targetId: component.id,
          message: `alias ${alias}이(가) 다른 논리 컴포넌트와 충돌합니다.`,
        });
      } else {
        aliasOwners.set(alias, component.id);
      }
    }
    if (component.lifecycleStatus === "DEPRECATED") {
      const replacement = component.replacementLogicalType;
      if (!replacement || replacement === component.id || !seenComponentIds.has(replacement)) {
        errors.push({
          code: "INVALID_COMPONENT_REPLACEMENT",
          path: `/components/${componentIndex}/replacementLogicalType`,
          targetId: component.id,
          message: `폐기 컴포넌트 ${component.id}에는 존재하는 다른 대체 논리 타입이 필요합니다.`,
        });
      }
    }
  });

  const seenPatternIds = new Set<string>();
  patterns.forEach((pattern, patternIndex) => {
    const patternPath = `/patterns/${patternIndex}`;
    if (!pattern.id) {
      errors.push({
        code: "PATTERN_ID_REQUIRED",
        path: `${patternPath}/id`,
        message: "패턴에 id가 없습니다.",
      });
      return;
    }
    if (seenPatternIds.has(pattern.id)) {
      errors.push({
        code: "DUPLICATE_PATTERN_ID",
        path: `${patternPath}/id`,
        targetId: pattern.id,
        message: `패턴 id가 중복되었습니다: ${pattern.id}`,
      });
    }
    if (seenComponentIds.has(pattern.id)) {
      errors.push({
        code: "LOGICAL_ID_COLLISION",
        path: `${patternPath}/id`,
        targetId: pattern.id,
        message: `컴포넌트와 패턴의 논리 id가 충돌합니다: ${pattern.id}`,
      });
    }
    seenPatternIds.add(pattern.id);
    (pattern.composedOf ?? []).forEach((componentId, dependencyIndex) => {
      if (!seenComponentIds.has(componentId)) {
        errors.push({
          code: "UNKNOWN_PATTERN_COMPONENT",
          path: `${patternPath}/composedOf/${dependencyIndex}`,
          targetId: pattern.id,
          message: `패턴 ${pattern.id}이(가) 존재하지 않는 컴포넌트를 참조합니다: ${componentId}`,
        });
      }
    });
  });

  if (errors.length > 0) return { errors };
  return {
    errors: [],
    parsed: {
      id: value.id as string,
      name: value.name as string,
      version: value.version as string,
      tokens,
      variableCollections,
      components,
      patterns,
      issues: Array.isArray(value.issues) ? value.issues as Issue[] : [],
    },
  };
}

function validateSizeRange(
  minimum: string | undefined,
  maximum: string | undefined,
  dimension: string,
  componentPath: string,
  componentId: string,
  errors: ValidationIssue[],
): void {
  const min = parsePositiveSize(minimum);
  const max = parsePositiveSize(maximum);
  if ((minimum && min === undefined) || (maximum && max === undefined)) {
    errors.push({
      code: "INVALID_LAYOUT_SIZE",
      path: `${componentPath}/layout`,
      targetId: componentId,
      message: `${dimension} min/max 값은 0보다 큰 숫자여야 합니다.`,
    });
    return;
  }
  if (min !== undefined && max !== undefined && min > max) {
    errors.push({
      code: "INVALID_LAYOUT_RANGE",
      path: `${componentPath}/layout`,
      targetId: componentId,
      message: `min ${dimension}가 max ${dimension}보다 클 수 없습니다.`,
    });
  }
}

function parsePositiveSize(raw: string | undefined): number | undefined {
  if (!raw) return undefined;
  const value = Number.parseFloat(raw.replace(/[^0-9.-]/g, ""));
  return Number.isFinite(value) && value > 0 ? value : undefined;
}

export function componentSnapshot(definition: ComponentDefinition): Record<string, unknown> {
  return {
    name: definition.name,
    description: definition.description ?? "",
    developer: definition.developer ?? {},
    lifecycleStatus: definition.lifecycleStatus ?? "ACTIVE",
    replacementLogicalType: definition.replacementLogicalType ?? null,
    aliases: definition.aliases ?? [],
    layout: definition.layout ?? {},
    properties: definition.properties ?? [],
    variants: definition.variants ?? {},
  };
}

export function compareSnapshots(
  before: Record<string, unknown>,
  after: Record<string, unknown>,
): Comparison[] {
  const comparisons: Comparison[] = [];
  const fields = new Set([...Object.keys(before), ...Object.keys(after)]);
  for (const field of [...fields].sort()) {
    const oldValue = JSON.stringify(before[field] ?? null);
    const newValue = JSON.stringify(after[field] ?? null);
    if (oldValue === newValue) continue;
    comparisons.push({
      field,
      before: oldValue,
      after: newValue,
      change: !(field in before) ? "ADD" : !(field in after) ? "REMOVE" : "UPDATE",
    });
  }
  return comparisons;
}

export function planComponentChange(
  before: Record<string, unknown> | undefined,
  incoming: ComponentDefinition,
): { kind: DiffKind; comparisons: Comparison[] } {
  if (!before) {
    return { kind: "ADD", comparisons: [] };
  }
  const after = componentSnapshot(incoming);
  const comparisons = compareSnapshots(before, after);
  if (comparisons.length === 0) return { kind: "NO_CHANGE", comparisons };
  const breaking = comparisons.some(comparison =>
    comparison.change === "REMOVE"
    || (comparison.field === "properties" && removesArrayEntry(comparison.before, comparison.after))
    || (comparison.field === "variants" && removesObjectOption(comparison.before, comparison.after)));
  return { kind: breaking ? "BREAKING" : "UPDATE", comparisons };
}

function removesArrayEntry(before: string, after: string): boolean {
  const oldValues = JSON.parse(before) as Array<{ name?: string }>;
  const newValues = JSON.parse(after) as Array<{ name?: string }>;
  const incomingNames = new Set(newValues.map(value => value.name));
  return oldValues.some(value => !incomingNames.has(value.name));
}

function removesObjectOption(before: string, after: string): boolean {
  const oldValues = JSON.parse(before) as Record<string, string[]>;
  const newValues = JSON.parse(after) as Record<string, string[]>;
  return Object.entries(oldValues).some(([name, options]) => {
    const incoming = new Set(newValues[name] ?? []);
    return options.some(option => !incoming.has(option));
  });
}

export function transitionReviewStatus(
  current: ReviewStatus,
  event: ReviewEvent,
): ReviewStatus {
  if (event === "REVIEW") return "IN_REVIEW";
  if (event === "APPROVAL") {
    if (current !== "IN_REVIEW") {
      throw new Error("승인은 IN_REVIEW 상태에서만 가능합니다.");
    }
    return "APPROVED";
  }
  if (current !== "IN_REVIEW") {
    throw new Error("반려는 IN_REVIEW 상태에서만 가능합니다.");
  }
  return "REJECTED";
}

export function normalizePluginError(error: unknown): {
  code: "FIGMA_PERMISSION_DENIED" | "FIGMA_API_LIMIT" | "FIGMA_API_ERROR";
  message: string;
  retryable: boolean;
} {
  const message = error instanceof Error ? error.message : String(error);
  const normalized = message.toLowerCase();
  if (normalized.includes("permission") || normalized.includes("not allowed")) {
    return { code: "FIGMA_PERMISSION_DENIED", message, retryable: false };
  }
  if (normalized.includes("rate") || normalized.includes("limit") || normalized.includes("quota")) {
    return { code: "FIGMA_API_LIMIT", message, retryable: true };
  }
  return { code: "FIGMA_API_ERROR", message, retryable: false };
}
