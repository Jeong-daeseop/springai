package com.krdevops.springai.service;

import com.krdevops.springai.config.EgovProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratedProjectBuildValidatorTest {

    @Test
    void buildExecutionIsDisabledByDefault(@TempDir Path root) {
        EgovProperties properties = new EgovProperties();
        properties.getOutput().setBasePath(root.toString());
        GeneratedProjectBuildValidator validator = new GeneratedProjectBuildValidator(properties);

        var report = validator.validate(root.toString());

        assertThat(report.executed()).isFalse();
        assertThat(report.output()).contains("비활성화");
    }

    @Test
    void rejectsProjectOutsideAllowedRoots(@TempDir Path allowed, @TempDir Path outside) {
        EgovProperties properties = new EgovProperties();
        properties.getValidation().setAllowBuildExecution(true);
        properties.getOutput().setBasePath(allowed.resolve("generated").toString());
        properties.getOutput().setAllowedPaths(List.of(allowed.toString()));
        GeneratedProjectBuildValidator validator = new GeneratedProjectBuildValidator(properties);

        var report = validator.validate(outside.toString());

        assertThat(report.executed()).isFalse();
        assertThat(report.output()).contains("허용 경로 밖");
    }

    @Test
    void reportsUnsupportedBuildWithoutExecutingProcess(@TempDir Path root) throws Exception {
        Path project = Files.createDirectories(root.resolve("project"));
        EgovProperties properties = new EgovProperties();
        properties.getValidation().setAllowBuildExecution(true);
        properties.getOutput().setBasePath(root.toString());
        GeneratedProjectBuildValidator validator = new GeneratedProjectBuildValidator(properties);

        var report = validator.validate(project.toString());

        assertThat(report.executed()).isFalse();
        assertThat(report.output()).contains("빌드 파일이 없습니다");
    }
}
