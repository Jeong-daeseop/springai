package com.krdevops.springai.service.generation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrudGenerationOperationIdFactoryTest {

    @Test
    void 같은_입력이면_항상_같은_operationId를_반환한다() {
        String first = CrudGenerationOperationIdFactory.forScreen("/tmp/proj", "LETTNEMPLYRINFO", "thymeleaf");
        String second = CrudGenerationOperationIdFactory.forScreen("/tmp/proj", "LETTNEMPLYRINFO", "thymeleaf");

        assertThat(first).isEqualTo(second);
        assertThat(first).matches("[a-f0-9]{64}");
    }

    @Test
    void tableName_대소문자는_같은_operationId를_만든다() {
        String lower = CrudGenerationOperationIdFactory.forScreen("/tmp/proj", "lettnemplyrinfo", "thymeleaf");
        String upper = CrudGenerationOperationIdFactory.forScreen("/tmp/proj", "LETTNEMPLYRINFO", "thymeleaf");

        assertThat(lower).isEqualTo(upper);
    }

    @Test
    void viewType이_다르면_다른_operationId다() {
        String jsp = CrudGenerationOperationIdFactory.forScreen("/tmp/proj", "EMP", "jsp");
        String thymeleaf = CrudGenerationOperationIdFactory.forScreen("/tmp/proj", "EMP", "thymeleaf");

        assertThat(jsp).isNotEqualTo(thymeleaf);
    }

    @Test
    void 필수값이_없으면_거부한다() {
        assertThatThrownBy(() -> CrudGenerationOperationIdFactory.forScreen(null, "EMP", "jsp"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 상대경로와_절대경로는_같은_operationId를_만든다() {
        String relative = "some-output-dir";
        String absolute = java.nio.file.Path.of(relative).toAbsolutePath().normalize().toString();

        String fromRelative = CrudGenerationOperationIdFactory.forScreen(relative, "EMP", "jsp");
        String fromAbsolute = CrudGenerationOperationIdFactory.forScreen(absolute, "EMP", "jsp");

        assertThat(fromRelative).isEqualTo(fromAbsolute);
    }
}
