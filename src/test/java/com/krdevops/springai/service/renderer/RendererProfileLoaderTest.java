package com.krdevops.springai.service.renderer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.renderer.RendererProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RendererProfileLoaderTest {

    private final RendererProfileValidator validator = new RendererProfileValidator();
    private final RendererProfileLoader loader = new RendererProfileLoader(
            new ObjectMapper().findAndRegisterModules(), validator);

    @Test
    void 기본ThymeleafProfile을ID와Version으로읽고캐시한다() {
        RendererProfile first = loader.load("thymeleaf-krds", "1.0");
        RendererProfile second = loader.load("thymeleaf-krds", "1.0");

        assertThat(first).isSameAs(second);
        assertThat(first.status()).isEqualTo(RendererProfile.Status.APPROVED);
        assertThat(first.rendererType()).isEqualTo(RendererProfile.RendererType.THYMELEAF);
        assertThat(first.templateEngine()).isEqualTo(RendererProfile.TemplateEngine.FREEMARKER);
        assertThat(first.supportedViewTypes()).containsExactly("THYMELEAF");
        assertThat(loader.loadApproved("thymeleaf-krds", "1.0")).isSameAs(first);
    }

    @Test
    void 존재하지않는ID나Version은최신값으로Fallback하지않는다() {
        assertThatThrownBy(() -> loader.load("thymeleaf-krds", "2.0"))
                .isInstanceOfSatisfying(RendererProfileLoader.RendererProfileLoadException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("RENDERER_PROFILE_NOT_FOUND"));
    }

    @Test
    void DraftProfile은Preview에서읽을수있지만Apply에는사용할수없다() {
        RendererProfile draft = new RendererProfile(
                "draft", "1.0", "a".repeat(64), RendererProfile.Status.DRAFT,
                RendererProfile.RendererType.THYMELEAF,
                RendererProfile.TemplateEngine.FREEMARKER,
                "templates-1.0", "b".repeat(64), "mapping-1.0", List.of("CRUD"),
                List.of(), "output-1.0", "validator@1.0#" + "c".repeat(64),
                List.of("THYMELEAF"));

        assertThat(validator.validate(draft, RendererProfileValidator.Purpose.PREVIEW).valid()).isTrue();
        assertThatThrownBy(() -> validator.requireValid(
                draft, RendererProfileValidator.Purpose.APPLY))
                .isInstanceOfSatisfying(
                        RendererProfileValidator.RendererProfileValidationException.class,
                        exception -> assertThat(exception.result().issues())
                                .extracting(RendererProfileValidator.ValidationIssue::code)
                                .containsExactly("RENDERER_PROFILE_NOT_APPROVED"));
    }
}
