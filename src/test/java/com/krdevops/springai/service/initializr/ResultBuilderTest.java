package com.krdevops.springai.service.initializr;

import com.krdevops.springai.model.GenerationReport;
import com.krdevops.springai.model.ProjectSpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResultBuilderTest {

    private final ResultBuilder sut = new ResultBuilder();
    private final VersionCapabilityResolver resolver = new VersionCapabilityResolver();

    // ── WAR Maven 5.0 ──────────────────────────────────────────────────────────

    @Test
    void build_warMaven50_includesProjectContextAndProjectSetupWorkflowHint() {
        ProjectSpec spec = warMaven50Spec();
        String result = sut.build(spec, emptyReport(spec));

        assertThat(result).contains("[PROJECT_CONTEXT]");
        assertThat(result).contains("suggestProjectSetupCrudWorkflow");
    }

    @Test
    void build_warMaven50_includesMavenPackageCommand() {
        ProjectSpec spec = warMaven50Spec();
        String result = sut.build(spec, emptyReport(spec));

        assertThat(result).contains("mvn clean package");
    }

    @Test
    void build_warMaven50_securityGuide_isOptionalWorkflow() {
        ProjectSpec spec = warMaven50Spec();
        String result = sut.build(spec, emptyReport(spec));

        // Security는 "선택" 항목이어야 하며, 필수 단계처럼 표현되지 않아야 한다
        assertThat(result).contains("선택");
        assertThat(result).contains("suggestSecurityMenuAuthWorkflow");
        // getSecurityTemplate 직접 호출 안내는 제거됐어야 한다
        assertThat(result).doesNotContain("getSecurityTemplate");
        // 존재하지 않는 projectType 파라미터를 Tool 호출 예시로 안내하면 안 된다
        // (PROJECT_CONTEXT 블록의 projectType=war 메타데이터와 구분: 함수 호출 형태만 금지)
        assertThat(result).doesNotContain("projectType=\"");
    }

    // ── Boot Gradle 5.0 ────────────────────────────────────────────────────────

    @Test
    void build_bootGradle50_includesBootRunCommand() {
        ProjectSpec spec = ProjectSpec.of(
                "sample", "com.example", "sample", "com.example.sample",
                "gradle", "boot", "/tmp/test", resolver.resolve("5.0"));
        String result = sut.build(spec, emptyReport(spec));

        assertThat(result).contains("./gradlew bootRun");
    }

    @Test
    void build_bootGradle50_includesApplicationYmlHint() {
        ProjectSpec spec = ProjectSpec.of(
                "sample", "com.example", "sample", "com.example.sample",
                "gradle", "boot", "/tmp/test", resolver.resolve("5.0"));
        String result = sut.build(spec, emptyReport(spec));

        assertThat(result).contains("application.yml");
        assertThat(result).doesNotContain("context-datasource.xml");
    }

    // ── WAR Maven 4.3 ──────────────────────────────────────────────────────────

    @Test
    void build_warMaven43_doesNotContainGetSecurityTemplate() {
        ProjectSpec spec = ProjectSpec.of(
                "sample", "com.example", "sample", "com.example.sample",
                "maven", "war", "/tmp/test", resolver.resolve("4.3"));
        String result = sut.build(spec, emptyReport(spec));

        assertThat(result).doesNotContain("getSecurityTemplate");
        assertThat(result).contains("suggestSecurityMenuAuthWorkflow");
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────────

    private ProjectSpec warMaven50Spec() {
        return ProjectSpec.of(
                "sample", "com.example", "sample", "com.example.sample",
                "maven", "war", "/tmp/test", resolver.resolve("5.0"));
    }

    private static GenerationReport emptyReport(ProjectSpec spec) {
        return new GenerationReport(spec.root().toString());
    }
}
