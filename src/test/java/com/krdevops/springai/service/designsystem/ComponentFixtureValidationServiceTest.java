package com.krdevops.springai.service.designsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import com.krdevops.springai.service.thymeleaf.ValidationGateExecutor;
import com.krdevops.springai.service.write.SafePathResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComponentFixtureValidationServiceTest {

    private final SafePathResolver pathResolver = new SafePathResolver();
    private final ComponentFixtureValidationService service = new ComponentFixtureValidationService(
            new ComponentFixtureModelAdapter(), new ComponentPropertyParameterResolver(),
            new ComponentVariantValueResolver(), new ComponentSlotRegionResolver(),
            new ThymeleafFragmentContractValidator(pathResolver), new ValidationGateExecutor(),
            pathResolver, new ObjectMapper());

    @Test
    void canonicalFixture를모든Resolver와실제ThymeleafEngine으로검증한다(@TempDir Path root)
            throws Exception {
        writeTemplate(root);
        DesignCodeComponentMapping mapping = mapping("2.0",
                DesignCodeComponentMapping.Status.REVIEW_REQUIRED, canonicalFixture("Primary"));

        ComponentFixtureValidationService.ValidationResult result =
                service.requireValid(root, mapping);

        assertThat(result.valid()).isTrue();
        assertThat(result.legacyAdapted()).isFalse();
        assertThat(result.fixtureHash()).hasSize(64);
        assertThat(result.renderHash()).hasSize(64);
        assertThat(result.renderContext()).containsEntry("label", "저장")
                .containsEntry("variant", "primary")
                .containsEntry("disabled", false)
                .containsEntry("content", "버튼 내용")
                .containsEntry("ariaLabel", "저장 버튼");
    }

    @Test
    void 기존평면Fixture를Figma입력으로역변환해호환렌더한다(@TempDir Path root)
            throws Exception {
        writeTemplate(root);
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("label", "저장");
        legacy.put("variant", "primary");
        legacy.put("disabled", false);
        legacy.put("content", "버튼 내용");
        legacy.put("ariaLabel", "저장 버튼");

        ComponentFixtureValidationService.ValidationResult result = service.requireValid(
                root, mapping("1.0", DesignCodeComponentMapping.Status.APPROVED, legacy));

        assertThat(result.valid()).isTrue();
        assertThat(result.legacyAdapted()).isTrue();
        assertThat(result.fixture().figmaProperties()).containsEntry("Style", "Primary");
        assertThat(result.issues()).extracting(ComponentFixtureValidationService.FixtureIssue::code)
                .contains("LEGACY_FIXTURE_ADAPTED");
    }

    @Test
    void 필수Property누락과미지원Variant는렌더전에차단한다(@TempDir Path root)
            throws Exception {
        writeTemplate(root);
        Map<String, Object> missing = Map.of(
                "schemaVersion", "1.0",
                "figmaProperties", Map.of("Style", "Danger"),
                "figmaSlots", Map.of("Content", "본문"));

        ComponentFixtureValidationService.ValidationResult result = service.validate(
                root, mapping("2.0", DesignCodeComponentMapping.Status.REVIEW_REQUIRED, missing));

        assertThat(result.valid()).isFalse();
        assertThat(result.renderHash()).isNull();
        assertThat(result.issues()).extracting(ComponentFixtureValidationService.FixtureIssue::code)
                .contains("REQUIRED_PROPERTY_MISSING", "VARIANT_VALUE_UNSUPPORTED");
        assertThatThrownBy(() -> service.requireValid(
                root, mapping("2.0", DesignCodeComponentMapping.Status.REVIEW_REQUIRED, missing)))
                .isInstanceOf(ComponentFixtureValidationService.FixtureValidationException.class);
    }

    @Test
    void context가MappingParameter를덮어쓰려하면차단한다(@TempDir Path root) throws Exception {
        writeTemplate(root);
        Map<String, Object> fixture = canonicalFixture("Primary");
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) fixture.get("contextVariables");
        LinkedHashMap<String, Object> collisionContext = new LinkedHashMap<>(context);
        collisionContext.put("label", "오염된 값");
        LinkedHashMap<String, Object> collision = new LinkedHashMap<>(fixture);
        collision.put("contextVariables", collisionContext);

        ComponentFixtureValidationService.ValidationResult result = service.validate(
                root, mapping("2.0", DesignCodeComponentMapping.Status.REVIEW_REQUIRED, collision));

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ComponentFixtureValidationService.FixtureIssue::code)
                .contains("FIXTURE_CONTEXT_COLLISION");
    }

    @Test
    void rollback으로복원한동일Fixture는같은Fixture와RenderHash를재현한다(@TempDir Path root)
            throws Exception {
        writeTemplate(root);
        Map<String, Object> fixture = canonicalFixture("Secondary");
        DesignCodeComponentMapping historical = mapping(
                "1.0", DesignCodeComponentMapping.Status.APPROVED, fixture);
        DesignCodeComponentMapping rollback = mapping(
                "3.0", DesignCodeComponentMapping.Status.APPROVED, fixture);

        ComponentFixtureValidationService.ValidationResult before =
                service.requireValid(root, historical);
        ComponentFixtureValidationService.ValidationResult after =
                service.requireValid(root, rollback);

        assertThat(after.fixtureHash()).isEqualTo(before.fixtureHash());
        assertThat(after.renderHash()).isEqualTo(before.renderHash());
        assertThat(after.renderContext()).isEqualTo(before.renderContext());
    }

    private Map<String, Object> canonicalFixture(String style) {
        LinkedHashMap<String, Object> fixture = new LinkedHashMap<>();
        fixture.put("schemaVersion", "1.0");
        fixture.put("figmaProperties", Map.of("Label", "저장", "Style", style));
        fixture.put("figmaSlots", Map.of("Content", "버튼 내용"));
        fixture.put("contextVariables", Map.of("ariaLabel", "저장 버튼"));
        return fixture;
    }

    private DesignCodeComponentMapping mapping(
            String version, DesignCodeComponentMapping.Status status, Map<String, Object> fixture) {
        String actor = status == DesignCodeComponentMapping.Status.APPROVED ? "reviewer" : null;
        Instant approvedAt = status == DesignCodeComponentMapping.Status.APPROVED
                ? Instant.parse("2026-08-23T01:00:00Z") : null;
        return new DesignCodeComponentMapping(
                "map-button", version, status, "a".repeat(64), "krds.button", "BUTTON_SET",
                "fragments/button :: button",
                List.of(
                        new DesignCodeComponentMapping.PropertyMapping(
                                "Label", "label", Map.of(), true, null, null),
                        new DesignCodeComponentMapping.PropertyMapping(
                                "Style", "variant",
                                Map.of("Primary", "primary", "Secondary", "secondary"),
                                true, null, null),
                        new DesignCodeComponentMapping.PropertyMapping(
                                "Disabled", "disabled", Map.of("true", true, "false", false),
                                false, false, null)),
                List.of(new DesignCodeComponentMapping.SlotMapping("Content", "content")),
                fixture, List.of("thymeleaf-krds"), "revision-1", actor, approvedAt);
    }

    private void writeTemplate(Path root) throws Exception {
        Path template = root.resolve("src/main/resources/templates/fragments/button.html");
        Files.createDirectories(template.getParent());
        Files.writeString(template, """
                <!doctype html>
                <html xmlns:th="http://www.thymeleaf.org">
                <body>
                <button th:fragment="button(label, variant, disabled, content)"
                        th:class="${variant}" th:disabled="${disabled}"
                        th:attr="aria-label=${ariaLabel}">
                  <span th:text="${label}">버튼</span><span th:text="${content}"></span>
                </button>
                </body>
                </html>
                """);
    }
}
