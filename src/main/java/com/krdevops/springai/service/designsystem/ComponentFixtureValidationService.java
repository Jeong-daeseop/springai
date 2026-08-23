package com.krdevops.springai.service.designsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.designsystem.ComponentFixtureModel;
import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import com.krdevops.springai.service.thymeleaf.ValidationGateExecutor;
import com.krdevops.springai.service.write.SafePathResolver;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Component Fixture를 Mapping Resolver와 실제 Thymeleaf Engine으로 검증하고 재현 Hash를 생성한다. */
@Service
public class ComponentFixtureValidationService {

    private final ComponentFixtureModelAdapter adapter;
    private final ComponentPropertyParameterResolver propertyResolver;
    private final ComponentVariantValueResolver variantResolver;
    private final ComponentSlotRegionResolver slotResolver;
    private final ThymeleafFragmentContractValidator fragmentValidator;
    private final ValidationGateExecutor gateExecutor;
    private final SafePathResolver pathResolver;
    private final ObjectMapper canonicalMapper;

    public ComponentFixtureValidationService(
            ComponentFixtureModelAdapter adapter,
            ComponentPropertyParameterResolver propertyResolver,
            ComponentVariantValueResolver variantResolver,
            ComponentSlotRegionResolver slotResolver,
            ThymeleafFragmentContractValidator fragmentValidator,
            ValidationGateExecutor gateExecutor,
            SafePathResolver pathResolver,
            ObjectMapper objectMapper) {
        this.adapter = adapter;
        this.propertyResolver = propertyResolver;
        this.variantResolver = variantResolver;
        this.slotResolver = slotResolver;
        this.fragmentValidator = fragmentValidator;
        this.gateExecutor = gateExecutor;
        this.pathResolver = pathResolver;
        this.canonicalMapper = objectMapper.copy().findAndRegisterModules()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public ValidationResult validate(Path projectRoot, DesignCodeComponentMapping mapping) {
        List<FixtureIssue> issues = new ArrayList<>();
        ComponentFixtureModelAdapter.Adaptation adaptation = adapter.adapt(mapping);
        adaptation.issues().forEach(issue -> issues.add(new FixtureIssue(
                "FIXTURE_ADAPTER", issue.code(), severity(issue.severity()), issue.message(), issue.target())));
        ThymeleafFragmentContractValidator.ValidationResult fragment =
                fragmentValidator.validate(projectRoot, mapping);
        fragment.issues().forEach(issue -> issues.add(new FixtureIssue(
                "FRAGMENT_CONTRACT", issue.code(),
                issue.severity() == ThymeleafFragmentContractValidator.Severity.ERROR
                        ? Severity.ERROR : Severity.WARNING,
                issue.message(), issue.target())));
        if (!adaptation.valid()) {
            return result(mapping, adaptation, fragment, null, null, Map.of(), issues);
        }

        ComponentFixtureModel fixture = adaptation.fixture();
        ComponentPropertyParameterResolver.Resolution properties =
                propertyResolver.resolveCandidate(mapping, fixture.figmaProperties());
        properties.issues().forEach(issue -> issues.add(new FixtureIssue(
                "PROPERTY_RESOLUTION", issue.code(), switch (issue.severity()) {
                    case INFO -> Severity.INFO;
                    case WARNING -> Severity.WARNING;
                    case ERROR -> Severity.ERROR;
                }, issue.message(), issue.target())));
        ComponentVariantValueResolver.Resolution variants = variantResolver.resolve(mapping, properties);
        variants.issues().forEach(issue -> issues.add(new FixtureIssue(
                "VARIANT_RESOLUTION", issue.code(),
                issue.severity() == ComponentVariantValueResolver.Severity.ERROR
                        ? Severity.ERROR : Severity.WARNING,
                issue.message(), issue.figmaProperty())));
        ComponentSlotRegionResolver.Resolution slots =
                slotResolver.resolveCandidate(mapping, fixture.figmaSlots());
        slots.issues().forEach(issue -> issues.add(new FixtureIssue(
                "SLOT_RESOLUTION", issue.code(), switch (issue.severity()) {
                    case INFO -> Severity.INFO;
                    case WARNING -> Severity.WARNING;
                    case ERROR -> Severity.ERROR;
                }, issue.message(), issue.target())));

        LinkedHashMap<String, Object> renderContext = new LinkedHashMap<>(variants.fragmentParameters());
        merge(renderContext, slots.fragmentRegions(), "SLOT_PARAMETER_COLLISION", issues);
        merge(renderContext, fixture.contextVariables(), "FIXTURE_CONTEXT_COLLISION", issues);
        String fixtureHash = fixtureHash(fixture);
        String renderHash = null;
        if (!blocking(issues) && fragment.valid()) {
            try {
                Path root = pathResolver.realDirectory(projectRoot);
                Path template = pathResolver.resolveTarget(root, fragment.templatePath());
                String html = Files.readString(template, StandardCharsets.UTF_8);
                ValidationGateExecutor.TemplateRenderProbe probe =
                        gateExecutor.renderTemplateEngine(html, renderContext);
                if (probe.passed()) {
                    renderHash = ContentHashes.sha256Hex(
                            probe.output().getBytes(StandardCharsets.UTF_8));
                } else {
                    probe.issues().forEach(message -> issues.add(new FixtureIssue(
                            "THYMELEAF_RENDER", "FIXTURE_RENDER_FAILED", Severity.ERROR,
                            message, fragment.templatePath())));
                }
            } catch (Exception exception) {
                issues.add(new FixtureIssue("THYMELEAF_RENDER", "FIXTURE_TEMPLATE_READ_FAILED",
                        Severity.ERROR, exception.getMessage(), fragment.templatePath()));
            }
        }
        return result(mapping, adaptation, fragment, fixtureHash, renderHash, renderContext, issues);
    }

    public ValidationResult requireValid(Path projectRoot, DesignCodeComponentMapping mapping) {
        ValidationResult result = validate(projectRoot, mapping);
        if (!result.valid()) throw new FixtureValidationException(result);
        return result;
    }

    private void merge(
            LinkedHashMap<String, Object> target,
            Map<String, Object> values,
            String collisionCode,
            List<FixtureIssue> issues) {
        values.forEach((key, value) -> {
            if (target.containsKey(key)) {
                issues.add(new FixtureIssue("FIXTURE_CONTEXT", collisionCode, Severity.ERROR,
                        "Fixture Render Context Key가 Mapping 결과와 충돌합니다.", key));
            } else target.put(key, value);
        });
    }

    private String fixtureHash(ComponentFixtureModel fixture) {
        try {
            return ContentHashes.sha256Hex(canonicalMapper.writeValueAsBytes(fixture));
        } catch (Exception exception) {
            throw new IllegalStateException("Component Fixture Hash 계산에 실패했습니다.", exception);
        }
    }

    private Severity severity(ComponentFixtureModelAdapter.Severity severity) {
        return severity == ComponentFixtureModelAdapter.Severity.ERROR ? Severity.ERROR : Severity.WARNING;
    }

    private boolean blocking(List<FixtureIssue> issues) {
        return issues.stream().anyMatch(issue -> issue.severity() == Severity.ERROR);
    }

    private ValidationResult result(
            DesignCodeComponentMapping mapping,
            ComponentFixtureModelAdapter.Adaptation adaptation,
            ThymeleafFragmentContractValidator.ValidationResult fragment,
            String fixtureHash,
            String renderHash,
            Map<String, Object> renderContext,
            List<FixtureIssue> issues) {
        return new ValidationResult(mapping.mappingId(), mapping.version(), adaptation.fixture(),
                adaptation.legacyAdapted(), fixtureHash, renderHash, renderContext, fragment, issues);
    }

    public record ValidationResult(
            String mappingId,
            String mappingVersion,
            ComponentFixtureModel fixture,
            boolean legacyAdapted,
            String fixtureHash,
            String renderHash,
            Map<String, Object> renderContext,
            ThymeleafFragmentContractValidator.ValidationResult fragmentValidation,
            List<FixtureIssue> issues
    ) {
        public ValidationResult {
            renderContext = Collections.unmodifiableMap(new LinkedHashMap<>(renderContext));
            issues = List.copyOf(issues);
        }

        public boolean valid() {
            return fixture != null && fixtureHash != null && renderHash != null
                    && issues.stream().noneMatch(issue -> issue.severity() == Severity.ERROR);
        }
    }

    public record FixtureIssue(
            String source, String code, Severity severity, String message, String target) {}

    public enum Severity { INFO, WARNING, ERROR }

    public static final class FixtureValidationException extends IllegalStateException {
        private final ValidationResult result;

        public FixtureValidationException(ValidationResult result) {
            super("Component Fixture 검증에 실패했습니다: "
                    + result.mappingId() + "@" + result.mappingVersion());
            this.result = result;
        }

        public ValidationResult result() {
            return result;
        }
    }
}
