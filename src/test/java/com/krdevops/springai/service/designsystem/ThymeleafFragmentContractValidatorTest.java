package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import com.krdevops.springai.service.write.SafePathResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThymeleafFragmentContractValidatorTest {

    private final ThymeleafFragmentContractValidator validator =
            new ThymeleafFragmentContractValidator(new SafePathResolver());

    @Test
    void 실제Fragment파일과Parameter계약을검증한다(@TempDir Path root) throws Exception {
        writeTemplate(root, "fragments/button.html", """
                <button th:fragment="button(label, variant, disabled, ariaLabel)">확인</button>
                """);

        ThymeleafFragmentContractValidator.ValidationResult result =
                validator.requireValid(root, mapping("fragments/button :: button"));

        assertThat(result.valid()).isTrue();
        assertThat(result.templatePath()).isEqualTo(
                "src/main/resources/templates/fragments/button.html");
        assertThat(result.declaredParameters()).containsExactlyInAnyOrder(
                "label", "variant", "disabled", "ariaLabel");
        assertThat(result.missingParameters()).isEmpty();
        assertThat(result.unmappedParameters()).containsExactly("ariaLabel");
        assertThat(result.issues()).extracting(ThymeleafFragmentContractValidator.ValidationIssue::code)
                .containsExactly("FRAGMENT_PARAMETER_UNMAPPED");
    }

    @Test
    void MappingParameter가실제선언에없으면승인을차단한다(@TempDir Path root) throws Exception {
        writeTemplate(root, "fragments/button.html",
                "<button th:fragment='button(label, variant)'>확인</button>");

        ThymeleafFragmentContractValidator.ValidationResult result =
                validator.validate(root, mapping("fragments/button :: button"));

        assertThat(result.valid()).isFalse();
        assertThat(result.missingParameters()).containsExactly("disabled");
        assertThatThrownBy(() -> validator.requireValid(
                root, mapping("fragments/button :: button")))
                .isInstanceOf(ThymeleafFragmentContractValidator.FragmentContractValidationException.class);
    }

    @Test
    void 파일과Fragment선언누락을구분한다(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("src/main/resources/templates"));
        ThymeleafFragmentContractValidator.ValidationResult missingFile =
                validator.validate(root, mapping("fragments/button :: button"));
        assertThat(missingFile.issues())
                .extracting(ThymeleafFragmentContractValidator.ValidationIssue::code)
                .containsExactly("FRAGMENT_FILE_NOT_FOUND");

        writeTemplate(root, "fragments/button.html", "<button>fragment 아님</button>");
        ThymeleafFragmentContractValidator.ValidationResult missingDeclaration =
                validator.validate(root, mapping("fragments/button :: button"));
        assertThat(missingDeclaration.issues())
                .extracting(ThymeleafFragmentContractValidator.ValidationIssue::code)
                .containsExactly("FRAGMENT_DECLARATION_NOT_FOUND");
    }

    @Test
    void 동적참조와경로이탈참조를파일조회전에거부한다(@TempDir Path root) {
        assertThat(validator.validate(root, mapping("${template} :: button")).issues())
                .extracting(ThymeleafFragmentContractValidator.ValidationIssue::code)
                .containsExactly("FRAGMENT_REFERENCE_DYNAMIC");
        assertThat(validator.validate(root, mapping("../secret :: button")).issues())
                .extracting(ThymeleafFragmentContractValidator.ValidationIssue::code)
                .containsExactly("FRAGMENT_REFERENCE_INVALID");
    }

    @Test
    void 같은이름의Fragment선언이중복되면모호성오류다(@TempDir Path root) throws Exception {
        writeTemplate(root, "fragments/button.html", """
                <button th:fragment="button(label, variant, disabled)">첫째</button>
                <button th:fragment="button(label, variant, disabled)">둘째</button>
                """);

        ThymeleafFragmentContractValidator.ValidationResult result =
                validator.validate(root, mapping("fragments/button :: button"));

        assertThat(result.valid()).isFalse();
        assertThat(result.issues())
                .extracting(ThymeleafFragmentContractValidator.ValidationIssue::code)
                .containsExactly("FRAGMENT_DECLARATION_AMBIGUOUS");
    }

    @Test
    void Boot와War에같은경로가있으면대상을임의선택하지않는다(@TempDir Path root) throws Exception {
        writeTemplate(root, "fragments/button.html",
                "<button th:fragment='button(label, variant, disabled)'>Boot</button>");
        Path war = root.resolve("src/main/webapp/WEB-INF/templates/fragments/button.html");
        Files.createDirectories(war.getParent());
        Files.writeString(war,
                "<button th:fragment='button(label, variant, disabled)'>WAR</button>");

        ThymeleafFragmentContractValidator.ValidationResult result =
                validator.validate(root, mapping("fragments/button :: button"));

        assertThat(result.valid()).isFalse();
        assertThat(result.issues())
                .extracting(ThymeleafFragmentContractValidator.ValidationIssue::code)
                .containsExactly("FRAGMENT_FILE_AMBIGUOUS");
    }

    private void writeTemplate(Path root, String relative, String content) throws Exception {
        Path file = root.resolve("src/main/resources/templates").resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private DesignCodeComponentMapping mapping(String fragment) {
        return new DesignCodeComponentMapping(
                "map-button", "1.0", DesignCodeComponentMapping.Status.APPROVED, "a".repeat(64),
                "button", "FIGMA_BUTTON", fragment,
                List.of(
                        new DesignCodeComponentMapping.PropertyMapping(
                                "Label", "label", Map.of(), true, null, null),
                        new DesignCodeComponentMapping.PropertyMapping(
                                "Style", "variant", Map.of("Primary", "primary"), true, null, null),
                        new DesignCodeComponentMapping.PropertyMapping(
                                "Disabled", "disabled", Map.of("true", true), false, false, null)),
                List.of(), null, List.of("thymeleaf-krds"), "figma-r1", "reviewer",
                Instant.parse("2026-08-23T01:00:00Z"));
    }
}
