package com.krdevops.springai.service.renderer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.renderer.RendererProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateSetFingerprintServiceTest {

    private final TemplateSetFingerprintService service = new TemplateSetFingerprintService();

    @Test
    void 배포ThymeleafTemplate28개의결정적Fingerprint를계산한다() {
        var first = service.calculate();
        var second = service.calculate();

        assertThat(first).isEqualTo(second);
        assertThat(first.templateSetVersion()).isEqualTo("crud-thymeleaf-1.0");
        assertThat(first.templateSetHash())
                .isEqualTo("ddffe4624efb4248d3751804043696b4e5ef7ddc495ae67bef2d7e3a4c96e4f8");
        assertThat(first.templates()).hasSize(28);
        assertThat(first.templates()).extracting(value -> value.relativePath())
                .isSorted()
                .contains("thymeleaf-list.html.ftl", "thymeleaf-list-body.html.ftl",
                        "thymeleaf-list-standalone.html.ftl", "layout/default.html.ftl");
        assertThat(first.templates()).allSatisfy(template -> {
            assertThat(template.sizeBytes()).isPositive();
            assertThat(template.contentHash()).matches("[0-9a-f]{64}");
        });
    }

    @Test
    void Profile의TemplateSetHash가실측값과다르면검증을차단한다() {
        RendererProfile profile = new RendererProfile(
                "thymeleaf-krds", "1.0", "a".repeat(64), RendererProfile.Status.APPROVED,
                RendererProfile.RendererType.THYMELEAF,
                RendererProfile.TemplateEngine.FREEMARKER,
                "crud-thymeleaf-1.0", "b".repeat(64), "1.0", List.of("CRUD"),
                List.of(), "1.0", "validator@1.0#" + "c".repeat(64), List.of("THYMELEAF"));
        RendererProfileValidator validator = new RendererProfileValidator(service);

        var result = validator.validate(profile, RendererProfileValidator.Purpose.APPLY);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(RendererProfileValidator.ValidationIssue::code)
                .containsExactly("TEMPLATE_SET_HASH_MISMATCH");
    }

    @Test
    void 배포Profile은실제TemplateSetFingerprint와일치한다() {
        RendererProfileValidator validator = new RendererProfileValidator(service);
        RendererProfileLoader loader = new RendererProfileLoader(
                new ObjectMapper().findAndRegisterModules(), validator);

        RendererProfile profile = loader.loadApproved("thymeleaf-krds", "1.0");

        assertThat(profile.templateSetVersion()).isEqualTo(service.calculate().templateSetVersion());
        assertThat(profile.templateSetHash()).isEqualTo(service.calculate().templateSetHash());
    }
}
