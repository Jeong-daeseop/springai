package com.krdevops.springai.service.generation.crud;

import com.krdevops.springai.model.renderer.RendererProfile;
import com.krdevops.springai.model.renderer.RendererProfileReference;
import com.krdevops.springai.service.generation.model.DesignContextReference;
import com.krdevops.springai.service.generation.model.LayoutOptions;
import com.krdevops.springai.service.generation.model.ProgramMetadataOverrides;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrudGenerationCommandRendererProfileTest {

    @Test
    void 기존Command호출은기본승인RendererProfile참조를고정한다() {
        CrudGenerationCommand command = legacyCommand();

        assertThat(command.rendererProfileReference())
                .isEqualTo(RendererProfileReference.defaultThymeleafKrds());
    }

    @Test
    void 명시적RendererProfile참조는IDVersionHash를모두보존한다() {
        RendererProfileReference reference = new RendererProfileReference(
                "thymeleaf-custom", "2.1", "d".repeat(64));
        CrudGenerationCommand command = new CrudGenerationCommand(
                "com", "EMP", "Employer", "egovframework.let.emp", Path.of("/tmp/out"),
                "auto", "5.0", "thymeleaf", LayoutOptions.empty(),
                ProgramMetadataOverrides.empty(), DesignContextReference.empty(), reference);

        assertThat(command.rendererProfileReference()).isSameAs(reference);
        assertThat(reference.identifies(new RendererProfile(
                "thymeleaf-custom", "2.1", "d".repeat(64), RendererProfile.Status.APPROVED,
                RendererProfile.RendererType.THYMELEAF,
                RendererProfile.TemplateEngine.FREEMARKER,
                "set-1", "e".repeat(64), "mapping-1", java.util.List.of("CRUD_LIST"),
                java.util.List.of(), "output-1", "validator@1.0#" + "f".repeat(64),
                java.util.List.of("THYMELEAF")))).isTrue();
    }

    @Test
    void Hash가없거나잘못된참조는Command생성전에거부한다() {
        assertThatThrownBy(() -> new RendererProfileReference(
                "thymeleaf-custom", "2.1", "missing"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private CrudGenerationCommand legacyCommand() {
        return new CrudGenerationCommand(
                "com", "EMP", "Employer", "egovframework.let.emp", Path.of("/tmp/out"),
                "auto", "5.0", "thymeleaf", LayoutOptions.empty(),
                ProgramMetadataOverrides.empty(), DesignContextReference.empty());
    }
}
