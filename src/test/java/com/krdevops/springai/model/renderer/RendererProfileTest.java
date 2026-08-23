package com.krdevops.springai.model.renderer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RendererProfileTest {

    @Test
    void Profile컬렉션은중복과빈항목을허용하지않는다() {
        assertThatThrownBy(() -> profile(List.of("CRUD", "CRUD"), List.of("THYMELEAF")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("중복");
        assertThatThrownBy(() -> profile(List.of("CRUD"), List.of(" ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("supportedViewTypes 항목");
    }

    @Test
    void 지원ViewType은하나이상필요하다() {
        assertThatThrownBy(() -> profile(List.of("CRUD"), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("하나 이상");
    }

    private RendererProfile profile(List<String> features, List<String> viewTypes) {
        return new RendererProfile(
                "thymeleaf-test", "1.0", "a".repeat(64), RendererProfile.Status.APPROVED,
                RendererProfile.RendererType.THYMELEAF,
                RendererProfile.TemplateEngine.FREEMARKER,
                "templates-1.0", "b".repeat(64), "mapping-1.0", features,
                List.of("UNMAPPED_COMPONENT"), "output-1.0",
                "validator@1.0#" + "c".repeat(64), viewTypes);
    }
}
