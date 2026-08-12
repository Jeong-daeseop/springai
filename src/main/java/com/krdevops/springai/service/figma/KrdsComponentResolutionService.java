package com.krdevops.springai.service.figma;

import com.krdevops.springai.mapper.ScreenPatternRepository;
import com.krdevops.springai.mapper.VariantRuleSetRepository;
import com.krdevops.springai.model.design.LayoutDensity;
import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.role.ComponentState;
import com.krdevops.springai.model.design.role.FieldMode;
import com.krdevops.springai.model.design.role.Platform;
import com.krdevops.springai.model.design.role.ScreenPattern;
import com.krdevops.springai.model.design.role.SemanticRole;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import com.krdevops.springai.model.designsystem.ScreenPatternDefinition;
import com.krdevops.springai.model.designsystem.VariantRuleSet;
import com.krdevops.springai.model.figma.ComponentResolutionContext;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.model.figma.FigmaScreenType;
import com.krdevops.springai.model.figma.ResolvedComponentRef;
import com.krdevops.springai.service.designsystem.ComponentRoleResolver;
import com.krdevops.springai.service.designsystem.VariantRuleResolver;
import com.krdevops.springai.service.observability.OperationalTelemetry;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Semantic Node Tree를 결정형 KRDS Published Instance Tree로 변환한다. */
@Service
public class KrdsComponentResolutionService {
    private final VariantRuleSetRepository ruleSetRepository;
    private final ScreenPatternRepository patternRepository;
    private final ScreenSemanticNormalizer normalizer;
    private final ScreenPatternValidator patternValidator;
    private final ComponentRoleResolver roleResolver;
    private final VariantRuleResolver variantResolver;
    private final OperationalTelemetry telemetry;

    public KrdsComponentResolutionService(
            VariantRuleSetRepository ruleSetRepository,
            ScreenPatternRepository patternRepository,
            ScreenSemanticNormalizer normalizer,
            ScreenPatternValidator patternValidator,
            ComponentRoleResolver roleResolver,
            VariantRuleResolver variantResolver,
            OperationalTelemetry telemetry
    ) {
        this.ruleSetRepository = ruleSetRepository;
        this.patternRepository = patternRepository;
        this.normalizer = normalizer;
        this.patternValidator = patternValidator;
        this.roleResolver = roleResolver;
        this.variantResolver = variantResolver;
        this.telemetry = telemetry;
    }

    public ResolutionResult resolve(
            String profileId,
            ComponentRegistry registry,
            PageSpec page,
            FigmaScreenType screenType,
            LayoutDensity density,
            String viewport,
            FigmaNodeSpec semanticRoot
    ) {
        long start = System.nanoTime();
        try {
            ResolutionResult result = doResolve(
                    profileId, registry, page, screenType, density, viewport, semanticRoot);
            telemetry.figmaResolutionDuration("SUCCESS", System.nanoTime() - start);
            return result;
        } catch (IllegalStateException e) {
            telemetry.figmaResolutionDuration("FAILURE", System.nanoTime() - start);
            recordFailureMetric(e.getMessage());
            throw e;
        }
    }

    /** KRV-074: {@link #failure(String, String)}가 만든 "CODE: detail" 메시지에서 오류 코드를 추출해 분류한다. */
    private void recordFailureMetric(String message) {
        String code = message == null ? "" : message.split(":", 2)[0].trim();
        if (OperationalTelemetry.ROLE_RESOLUTION_ERROR_CODES.contains(code)) {
            telemetry.figmaRoleResolutionFailure(code);
        } else if (OperationalTelemetry.VARIANT_RESOLUTION_ERROR_CODES.contains(code)) {
            telemetry.figmaVariantResolutionFailure(code);
        } else if (OperationalTelemetry.COMPONENT_PROPERTY_DRIFT_CODES.contains(code)) {
            telemetry.figmaComponentPropertyDrift(code);
        }
    }

    private ResolutionResult doResolve(
            String profileId,
            ComponentRegistry registry,
            PageSpec page,
            FigmaScreenType screenType,
            LayoutDensity density,
            String viewport,
            FigmaNodeSpec semanticRoot
    ) {
        if (registry == null) throw failure("REGISTRY_NOT_FOUND", "Component Registry가 없습니다.");
        ScreenPattern pattern = normalizer.pattern(page);
        ScreenPatternDefinition patternDefinition = patternRepository.findLatest(pattern)
                .orElseThrow(() -> failure("SCREEN_PATTERN_NOT_RESOLVED", pattern.code()));
        VariantRuleSet ruleSet = ruleSetRepository.findPublished(profileId, registry.registryVersion())
                .orElseThrow(() -> failure("VARIANT_RULE_SET_NOT_PUBLISHED", profileId + "/" + registry.registryVersion()));

        List<DesignSystemIssue> patternIssues = patternValidator.validate(patternDefinition, semanticRoot);
        if (!patternIssues.isEmpty()) {
            throw failure(patternIssues.get(0).code(), patternIssues.get(0).message());
        }
        Platform platform = parsePlatform(viewport);
        FigmaNodeSpec resolved = resolveNode(semanticRoot, registry, ruleSet, pattern, screenType, density, platform);
        String componentContractVersion = resolveComponentContractVersion(resolved);
        return new ResolutionResult(resolved, pattern, patternDefinition.version(), ruleSet.version(),
                componentContractVersion);
    }

    /** Registry 자체 버전과 독립적으로, 실제 화면에서 사용한 Entry 계약 버전을 확정한다. */
    private String resolveComponentContractVersion(FigmaNodeSpec root) {
        Set<String> versions = new TreeSet<>();
        collectContractVersions(root, versions);
        if (versions.isEmpty()) {
            throw failure("COMPONENT_CONTRACT_VERSION_MISSING", "해석된 COMPONENT 계약 버전이 없습니다.");
        }
        if (versions.size() > 1) {
            throw failure("COMPONENT_CONTRACT_VERSION_MISMATCH", String.join(",", versions));
        }
        return versions.iterator().next();
    }

    private void collectContractVersions(FigmaNodeSpec node, Set<String> versions) {
        if (node.nodeType() == FigmaNodeSpec.NodeType.COMPONENT) {
            if (node.componentResolution() == null
                    || node.componentResolution().contractVersion() == null
                    || node.componentResolution().contractVersion().isBlank()) {
                throw failure("COMPONENT_CONTRACT_VERSION_MISSING", node.logicalNodeId());
            }
            versions.add(node.componentResolution().contractVersion());
        }
        node.children().forEach(child -> collectContractVersions(child, versions));
    }

    private FigmaNodeSpec resolveNode(
            FigmaNodeSpec node,
            ComponentRegistry registry,
            VariantRuleSet ruleSet,
            ScreenPattern pattern,
            FigmaScreenType screenType,
            LayoutDensity density,
            Platform platform
    ) {
        List<FigmaNodeSpec> children = node.children().stream()
                .map(child -> resolveNode(child, registry, ruleSet, pattern, screenType, density, platform))
                .toList();
        Object rawRole = node.properties().get("semanticRole");
        if (!(rawRole instanceof String roleCode)) {
            return new FigmaNodeSpec(node.logicalNodeId(), node.nodeType(), node.type(), node.properties(), children);
        }
        // form.container, form.section, data.table 같은 구조 Role은
        // Published Component가 아니라 Auto Layout Recipe로 구현될 수 있다.
        if (node.nodeType() != FigmaNodeSpec.NodeType.COMPONENT) {
            return new FigmaNodeSpec(node.logicalNodeId(), node.nodeType(), node.type(), node.properties(), children);
        }
        SemanticRole role = SemanticRole.fromCode(roleCode);
        ComponentResolutionContext context = new ComponentResolutionContext(
                pattern, screenType, platform, density,
                enumValue(FieldMode.class, node.properties().get("mode")),
                enumValue(ComponentState.class, node.properties().get("state")),
                booleanValue(node.properties().get("required")),
                booleanValue(node.properties().get("disabled")),
                integerValue(node.properties().get("fieldCount")), role);
        ComponentRoleResolver.Resolution component = roleResolver.resolve(registry, context);
        if (!component.resolved()) throw failure(component.errorCode(), role.code());

        ComponentRegistryEntry contract = component.entry();
        String variantKey;
        Map<String, String> variantProperties;
        String ruleId = null;
        if (contract.variantAxes().isEmpty()) {
            if (contract.variants().size() > 1) throw failure("VARIANT_RULE_NOT_FOUND", role.code());
            variantKey = contract.variants().isEmpty()
                    ? contract.componentSetKey() : contract.variants().values().iterator().next();
            variantProperties = Map.of();
        } else {
            VariantRuleResolver.Resolution variant = variantResolver.resolve(contract, context, ruleSet);
            if (!variant.resolved()) throw failure(variant.errorCode(), role.code());
            variantKey = variant.variantKey();
            variantProperties = variant.variantProperties();
            ruleId = variant.ruleId();
        }
        Map<String, Object> componentProperties = mapComponentProperties(node.properties(), contract);
        ResolvedComponentRef ref = new ResolvedComponentRef(
                role, component.logicalType(), contract.componentSetKey(), variantKey,
                variantProperties, componentProperties, contract.contractVersion(), ruleSet.version(),
                ruleId, context.stableHash());
        return new FigmaNodeSpec(node.logicalNodeId(), node.nodeType(), component.logicalType(),
                node.properties(), ref, children);
    }

    private Map<String, Object> mapComponentProperties(
            Map<String, Object> semanticProperties,
            ComponentRegistryEntry contract
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        contract.properties().forEach((logicalName, mapping) -> {
            if (mapping.type() == ComponentRegistryEntry.PropertyType.VARIANT) return;
            Object raw = semanticProperties.get(logicalName);
            if (raw == null && "label".equalsIgnoreCase(logicalName)) raw = semanticProperties.get("title");
            if (raw == null) return;
            if (mapping.type() == ComponentRegistryEntry.PropertyType.BOOLEAN) {
                result.put(mapping.figmaProperty(), Boolean.parseBoolean(String.valueOf(raw)));
            } else {
                String value = String.valueOf(raw);
                result.put(mapping.figmaProperty(), mapping.values().getOrDefault(value, value));
            }
        });
        for (String required : contract.requiredProperties()) {
            ComponentRegistryEntry.PropertyMapping mapping = contract.properties().get(required);
            if (mapping != null && mapping.type() != ComponentRegistryEntry.PropertyType.VARIANT
                    && !result.containsKey(mapping.figmaProperty())) {
                throw failure("REQUIRED_COMPONENT_PROPERTY_MISSING", required);
            }
        }
        return Map.copyOf(result);
    }

    private Platform parsePlatform(String viewport) {
        try { return Platform.valueOf((viewport == null ? "DESKTOP" : viewport).toUpperCase(Locale.ROOT)); }
        catch (Exception exception) { throw failure("COMPONENT_PLATFORM_UNSUPPORTED", viewport); }
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, Object value) {
        if (value == null) return null;
        try { return Enum.valueOf(type, String.valueOf(value).toUpperCase(Locale.ROOT)); }
        catch (Exception exception) { throw failure("INVALID_RESOLUTION_CONTEXT", String.valueOf(value)); }
    }

    private Boolean booleanValue(Object value) {
        return value == null ? null : Boolean.valueOf(String.valueOf(value));
    }

    private Integer integerValue(Object value) {
        if (value == null) return null;
        return value instanceof Number number ? number.intValue() : Integer.valueOf(String.valueOf(value));
    }

    private IllegalStateException failure(String code, String detail) {
        return new IllegalStateException(code + ": " + detail);
    }

    public record ResolutionResult(
            FigmaNodeSpec content,
            ScreenPattern pattern,
            String screenPatternVersion,
            String variantRuleSetVersion,
            String componentContractVersion
    ) {}
}
