package com.krdevops.springai.service.renderer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.renderer.RendererCapabilityRequirement;
import com.krdevops.springai.model.renderer.RendererFallback;
import com.krdevops.springai.model.renderer.RendererFeature;
import com.krdevops.springai.model.renderer.RendererProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RendererCapabilityMatrixServiceTest {

    private final RendererCapabilityMatrixService service =
            new RendererCapabilityMatrixService();

    @Test
    void 배포Profile을전체FeatureFallbackBooleanMatrix로변환한다() {
        RendererProfile profile = loadProfile();

        var matrix = service.inspect(profile);

        assertThat(matrix.valid()).isTrue();
        assertThat(matrix.supportedFeatures()).hasSize(RendererFeature.values().length)
                .containsEntry(RendererFeature.CRUD_LIST, true)
                .containsEntry(RendererFeature.COMPOSITE_PRIMARY_KEY, true);
        assertThat(matrix.forbiddenFallbacks()).hasSize(RendererFallback.values().length)
                .containsEntry(RendererFallback.UNSUPPORTED_VARIANT, true)
                .containsEntry(RendererFallback.LEGACY_LAYOUT_REUSE, false);
    }

    @Test
    void 지원Feature와허용Fallback만요청하면통과한다() {
        var requirement = new RendererCapabilityRequirement(
                Set.of(RendererFeature.CRUD_LIST, RendererFeature.CRUD_SEARCH),
                Set.of(RendererFallback.LEGACY_LAYOUT_REUSE));

        var result = service.requireSupported(loadProfile(), requirement);

        assertThat(result.supported()).isTrue();
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void 미지원Feature와금지Fallback을개별Evidence로모아차단한다() {
        RendererProfile limited = profile(
                List.of("CRUD_LIST"), List.of("UNSUPPORTED_VARIANT"));
        var requirement = new RendererCapabilityRequirement(
                Set.of(RendererFeature.CRUD_UPDATE),
                Set.of(RendererFallback.UNSUPPORTED_VARIANT));

        assertThatThrownBy(() -> service.requireSupported(limited, requirement))
                .isInstanceOfSatisfying(
                        RendererCapabilityMatrixService.RendererCapabilityException.class,
                        exception -> assertThat(exception.assessment().issues())
                                .extracting(RendererCapabilityMatrixService.CapabilityIssue::code)
                                .containsExactly(
                                        "RENDERER_FEATURE_UNSUPPORTED",
                                        "RENDERER_FALLBACK_FORBIDDEN"));
    }

    @Test
    void 알수없는ProfileCapability문자열은구조검증에서차단한다() {
        RendererProfile unknown = profile(
                List.of("CRUD_LIST", "MAGIC_RENDER"), List.of("UNKNOWN_FALLBACK"));
        RendererProfileValidator validator = new RendererProfileValidator(
                null, service);

        var result = validator.validate(unknown, RendererProfileValidator.Purpose.PREVIEW);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(RendererProfileValidator.ValidationIssue::code)
                .containsExactly("RENDERER_FEATURE_UNKNOWN", "RENDERER_FALLBACK_UNKNOWN");
    }

    private RendererProfile loadProfile() {
        return new RendererProfileLoader(
                new ObjectMapper().findAndRegisterModules(), new RendererProfileValidator())
                .load("thymeleaf-krds", "1.0");
    }

    private RendererProfile profile(List<String> features, List<String> fallbacks) {
        return new RendererProfile(
                "test", "1.0", "a".repeat(64), RendererProfile.Status.APPROVED,
                RendererProfile.RendererType.THYMELEAF,
                RendererProfile.TemplateEngine.FREEMARKER,
                "crud-thymeleaf-1.0", "b".repeat(64), "1.0", features, fallbacks,
                "1.0", "validator@1.0#" + "c".repeat(64), List.of("THYMELEAF"));
    }
}
