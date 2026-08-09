// src/core.ts
function validateSpec(spec) {
  const errors = [];
  if (typeof spec !== "object" || spec === null) {
    return {
      errors: [{ code: "SPEC_TYPE", path: "", message: "\uCD5C\uC0C1\uC704 \uAC12\uC774 object\uAC00 \uC544\uB2D9\uB2C8\uB2E4." }]
    };
  }
  const value = spec;
  for (const field of ["id", "name", "version"]) {
    if (typeof value[field] !== "string" || value[field].length === 0) {
      errors.push({
        code: "REQUIRED",
        path: `/${field}`,
        message: `${field}\uC740(\uB294) \uBE44\uC5B4 \uC788\uC9C0 \uC54A\uC740 \uBB38\uC790\uC5F4\uC774\uC5B4\uC57C \uD569\uB2C8\uB2E4.`
      });
    }
  }
  const components = Array.isArray(value.components) ? value.components : [];
  const patterns = Array.isArray(value.patterns) ? value.patterns : [];
  const tokens = Array.isArray(value.tokens) ? value.tokens : [];
  const variableCollections = Array.isArray(value.variableCollections) ? value.variableCollections : [];
  const seenComponentIds = /* @__PURE__ */ new Set();
  components.forEach((component, componentIndex) => {
    const componentPath = `/components/${componentIndex}`;
    if (!component.id) {
      errors.push({
        code: "COMPONENT_ID_REQUIRED",
        path: `${componentPath}/id`,
        message: "\uCEF4\uD3EC\uB10C\uD2B8\uC5D0 id\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4."
      });
      return;
    }
    if (seenComponentIds.has(component.id)) {
      errors.push({
        code: "DUPLICATE_COMPONENT_ID",
        path: `${componentPath}/id`,
        targetId: component.id,
        message: `\uCEF4\uD3EC\uB10C\uD2B8 id\uAC00 \uC911\uBCF5\uB418\uC5C8\uC2B5\uB2C8\uB2E4: ${component.id}`
      });
    }
    seenComponentIds.add(component.id);
    (component.properties ?? []).forEach((property, propertyIndex) => {
      if (property.type === "VARIANT" && !(component.variants ?? {})[property.name]) {
        errors.push({
          code: "VARIANT_OPTIONS_REQUIRED",
          path: `${componentPath}/properties/${propertyIndex}`,
          targetId: component.id,
          message: `\uCEF4\uD3EC\uB10C\uD2B8 ${component.id}\uC758 \uC18D\uC131 ${property.name}\uC774(\uAC00) VARIANT \uD0C0\uC785\uC778\uB370 variants\uC5D0 \uC635\uC158\uC774 \uC5C6\uC2B5\uB2C8\uB2E4.`
        });
      }
    });
    const layout = component.layout;
    if (layout) {
      validateSizeRange(layout.minWidth, layout.maxWidth, "width", componentPath, component.id, errors);
      validateSizeRange(layout.minHeight, layout.maxHeight, "height", componentPath, component.id, errors);
    }
    if (component.developer?.documentationUrl && !isAbsoluteHttpUrl(component.developer.documentationUrl)) {
      errors.push({
        code: "INVALID_DOCUMENTATION_URL",
        path: `${componentPath}/developer/documentationUrl`,
        targetId: component.id,
        message: `\uCEF4\uD3EC\uB10C\uD2B8 ${component.id}\uC758 documentationUrl\uC774 \uC62C\uBC14\uB974\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4.`
      });
    }
  });
  const aliasOwners = /* @__PURE__ */ new Map();
  components.forEach((component, componentIndex) => {
    for (const alias of component.aliases ?? []) {
      const owner = aliasOwners.get(alias);
      if (seenComponentIds.has(alias) || owner) {
        errors.push({
          code: "COMPONENT_ALIAS_CONFLICT",
          path: `/components/${componentIndex}/aliases`,
          targetId: component.id,
          message: `alias ${alias}\uC774(\uAC00) \uB2E4\uB978 \uB17C\uB9AC \uCEF4\uD3EC\uB10C\uD2B8\uC640 \uCDA9\uB3CC\uD569\uB2C8\uB2E4.`
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
          message: `\uD3D0\uAE30 \uCEF4\uD3EC\uB10C\uD2B8 ${component.id}\uC5D0\uB294 \uC874\uC7AC\uD558\uB294 \uB2E4\uB978 \uB300\uCCB4 \uB17C\uB9AC \uD0C0\uC785\uC774 \uD544\uC694\uD569\uB2C8\uB2E4.`
        });
      }
    }
  });
  const seenPatternIds = /* @__PURE__ */ new Set();
  patterns.forEach((pattern, patternIndex) => {
    const patternPath = `/patterns/${patternIndex}`;
    if (!pattern.id) {
      errors.push({
        code: "PATTERN_ID_REQUIRED",
        path: `${patternPath}/id`,
        message: "\uD328\uD134\uC5D0 id\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4."
      });
      return;
    }
    if (seenPatternIds.has(pattern.id)) {
      errors.push({
        code: "DUPLICATE_PATTERN_ID",
        path: `${patternPath}/id`,
        targetId: pattern.id,
        message: `\uD328\uD134 id\uAC00 \uC911\uBCF5\uB418\uC5C8\uC2B5\uB2C8\uB2E4: ${pattern.id}`
      });
    }
    if (seenComponentIds.has(pattern.id)) {
      errors.push({
        code: "LOGICAL_ID_COLLISION",
        path: `${patternPath}/id`,
        targetId: pattern.id,
        message: `\uCEF4\uD3EC\uB10C\uD2B8\uC640 \uD328\uD134\uC758 \uB17C\uB9AC id\uAC00 \uCDA9\uB3CC\uD569\uB2C8\uB2E4: ${pattern.id}`
      });
    }
    seenPatternIds.add(pattern.id);
    (pattern.composedOf ?? []).forEach((componentId, dependencyIndex) => {
      if (!seenComponentIds.has(componentId)) {
        errors.push({
          code: "UNKNOWN_PATTERN_COMPONENT",
          path: `${patternPath}/composedOf/${dependencyIndex}`,
          targetId: pattern.id,
          message: `\uD328\uD134 ${pattern.id}\uC774(\uAC00) \uC874\uC7AC\uD558\uC9C0 \uC54A\uB294 \uCEF4\uD3EC\uB10C\uD2B8\uB97C \uCC38\uC870\uD569\uB2C8\uB2E4: ${componentId}`
        });
      }
    });
  });
  if (errors.length > 0) return { errors };
  return {
    errors: [],
    parsed: {
      id: value.id,
      name: value.name,
      version: value.version,
      tokens,
      variableCollections,
      components,
      patterns,
      issues: Array.isArray(value.issues) ? value.issues : []
    }
  };
}
function isAbsoluteHttpUrl(value) {
  return /^https?:\/\/[^\s/?#]+(?:[/?#][^\s]*)?$/i.test(value);
}
function utf8Bytes(value) {
  const encoded = encodeURIComponent(value);
  const bytes = [];
  for (let index = 0; index < encoded.length; index++) {
    if (encoded[index] === "%") {
      bytes.push(Number.parseInt(encoded.slice(index + 1, index + 3), 16));
      index += 2;
    } else {
      bytes.push(encoded.charCodeAt(index));
    }
  }
  return new Uint8Array(bytes);
}
function figmaVariableName(logicalId) {
  const parts = logicalId.trim().split(/[./]+/u).map((part) => part.trim()).filter(Boolean).map((part) => part.replace(/[^\p{L}\p{N}_-]+/gu, "-").replace(/-{2,}/g, "-").replace(/^-+|-+$/g, "")).filter(Boolean);
  return parts.length > 0 ? parts.join("/") : "token/unnamed";
}
function validateSizeRange(minimum, maximum, dimension, componentPath, componentId, errors) {
  const min = parsePositiveSize(minimum);
  const max = parsePositiveSize(maximum);
  if (minimum && min === void 0 || maximum && max === void 0) {
    errors.push({
      code: "INVALID_LAYOUT_SIZE",
      path: `${componentPath}/layout`,
      targetId: componentId,
      message: `${dimension} min/max \uAC12\uC740 0\uBCF4\uB2E4 \uD070 \uC22B\uC790\uC5EC\uC57C \uD569\uB2C8\uB2E4.`
    });
    return;
  }
  if (min !== void 0 && max !== void 0 && min > max) {
    errors.push({
      code: "INVALID_LAYOUT_RANGE",
      path: `${componentPath}/layout`,
      targetId: componentId,
      message: `min ${dimension}\uAC00 max ${dimension}\uBCF4\uB2E4 \uD074 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4.`
    });
  }
}
function parsePositiveSize(raw) {
  if (!raw) return void 0;
  const value = Number.parseFloat(raw.replace(/[^0-9.-]/g, ""));
  return Number.isFinite(value) && value > 0 ? value : void 0;
}
function componentSnapshot(definition) {
  return {
    name: definition.name,
    description: definition.description ?? "",
    developer: definition.developer ?? {},
    lifecycleStatus: definition.lifecycleStatus ?? "ACTIVE",
    replacementLogicalType: definition.replacementLogicalType ?? null,
    aliases: definition.aliases ?? [],
    layout: definition.layout ?? {},
    properties: definition.properties ?? [],
    variants: definition.variants ?? {}
  };
}
function compareSnapshots(before, after) {
  const comparisons = [];
  const fields = /* @__PURE__ */ new Set([...Object.keys(before), ...Object.keys(after)]);
  for (const field of [...fields].sort()) {
    const oldValue = JSON.stringify(before[field] ?? null);
    const newValue = JSON.stringify(after[field] ?? null);
    if (oldValue === newValue) continue;
    comparisons.push({
      field,
      before: oldValue,
      after: newValue,
      change: !(field in before) ? "ADD" : !(field in after) ? "REMOVE" : "UPDATE"
    });
  }
  return comparisons;
}
function planComponentChange(before, incoming) {
  if (!before) {
    return { kind: "ADD", comparisons: [] };
  }
  const after = componentSnapshot(incoming);
  const comparisons = compareSnapshots(before, after);
  if (comparisons.length === 0) return { kind: "NO_CHANGE", comparisons };
  const breaking = comparisons.some((comparison) => comparison.change === "REMOVE" || comparison.field === "properties" && removesArrayEntry(comparison.before, comparison.after) || comparison.field === "variants" && removesObjectOption(comparison.before, comparison.after));
  return { kind: breaking ? "BREAKING" : "UPDATE", comparisons };
}
function removesArrayEntry(before, after) {
  const oldValues = JSON.parse(before);
  const newValues = JSON.parse(after);
  const incomingNames = new Set(newValues.map((value) => value.name));
  return oldValues.some((value) => !incomingNames.has(value.name));
}
function removesObjectOption(before, after) {
  const oldValues = JSON.parse(before);
  const newValues = JSON.parse(after);
  return Object.entries(oldValues).some(([name, options]) => {
    const incoming = new Set(newValues[name] ?? []);
    return options.some((option) => !incoming.has(option));
  });
}
function transitionReviewStatus(current, event) {
  if (event === "REVIEW") return "IN_REVIEW";
  if (event === "APPROVAL") {
    if (current !== "IN_REVIEW") {
      throw new Error("\uC2B9\uC778\uC740 IN_REVIEW \uC0C1\uD0DC\uC5D0\uC11C\uB9CC \uAC00\uB2A5\uD569\uB2C8\uB2E4.");
    }
    return "APPROVED";
  }
  if (current !== "IN_REVIEW") {
    throw new Error("\uBC18\uB824\uB294 IN_REVIEW \uC0C1\uD0DC\uC5D0\uC11C\uB9CC \uAC00\uB2A5\uD569\uB2C8\uB2E4.");
  }
  return "REJECTED";
}
function normalizePluginError(error) {
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
export {
  compareSnapshots,
  componentSnapshot,
  figmaVariableName,
  isAbsoluteHttpUrl,
  normalizePluginError,
  planComponentChange,
  transitionReviewStatus,
  utf8Bytes,
  validateSpec
};
