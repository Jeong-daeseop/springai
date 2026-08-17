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

    // ===== R6-T19: 실제 프로세스 실행(stub mvn) 성공/실패/timeout =====

    @Test
    void realMavenProcessSuccessIsReportedAsPassed(@TempDir Path root) throws Exception {
        Path project = Files.createDirectories(root.resolve("project"));
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        Path stubMvn = stubScript(root, "mvn-success", "exit 0\n");
        EgovProperties properties = new EgovProperties();
        properties.getValidation().setAllowBuildExecution(true);
        properties.getValidation().setMavenCommand(stubMvn.toString());
        properties.getOutput().setBasePath(root.toString());
        GeneratedProjectBuildValidator validator = new GeneratedProjectBuildValidator(properties);

        var report = validator.validate(project.toString());

        assertThat(report.executed()).isTrue();
        assertThat(report.passed()).isTrue();
        assertThat(report.timedOut()).isFalse();
        assertThat(report.exitCode()).isZero();
        assertThat(report.buildTool()).isEqualTo("maven");
    }

    @Test
    void realMavenProcessFailureIsReportedWithNonZeroExitCode(@TempDir Path root) throws Exception {
        Path project = Files.createDirectories(root.resolve("project"));
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        Path stubMvn = stubScript(root, "mvn-failure", "echo 'COMPILATION ERROR' >&2\nexit 1\n");
        EgovProperties properties = new EgovProperties();
        properties.getValidation().setAllowBuildExecution(true);
        properties.getValidation().setMavenCommand(stubMvn.toString());
        properties.getOutput().setBasePath(root.toString());
        GeneratedProjectBuildValidator validator = new GeneratedProjectBuildValidator(properties);

        var report = validator.validate(project.toString());

        assertThat(report.executed()).isTrue();
        assertThat(report.passed()).isFalse();
        assertThat(report.timedOut()).isFalse();
        assertThat(report.exitCode()).isEqualTo(1);
        assertThat(report.output()).contains("COMPILATION ERROR");
    }

    @Test
    void realMavenProcessExceedingTimeoutIsForciblyStoppedAndReportedAsTimedOut(@TempDir Path root) throws Exception {
        Path project = Files.createDirectories(root.resolve("project"));
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        Path stubMvn = stubScript(root, "mvn-hang", "sleep 30\nexit 0\n");
        EgovProperties properties = new EgovProperties();
        properties.getValidation().setAllowBuildExecution(true);
        properties.getValidation().setMavenCommand(stubMvn.toString());
        properties.getValidation().setBuildTimeoutSeconds(1);
        properties.getOutput().setBasePath(root.toString());
        GeneratedProjectBuildValidator validator = new GeneratedProjectBuildValidator(properties);

        var report = validator.validate(project.toString());

        assertThat(report.executed()).isTrue();
        assertThat(report.passed()).isFalse();
        assertThat(report.timedOut()).isTrue();
        assertThat(report.output()).contains("제한 시간을 초과");
    }

    private Path stubScript(Path root, String name, String body) throws Exception {
        Path script = root.resolve(name + ".sh");
        Files.writeString(script, "#!/bin/sh\n" + body);
        script.toFile().setExecutable(true);
        return script;
    }
}
