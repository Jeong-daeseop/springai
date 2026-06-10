package com.krdevops.springai.model;

/**
 * initializeProject 결과를 이후 Tool 호출에서 재사용하기 위한 컨텍스트.
 * egovVersion은 축약형("4.3"/"5.0")으로 통일.
 */
public record ProjectContext(
        String projectName,
        String rootPath,
        String packageName,
        String projectType,     // "war" / "boot"
        String buildTool,       // "maven" / "gradle"
        String egovVersion      // "4.3" / "5.0" (축약형 통일)
) {
    public static ProjectContext from(ProjectSpec s) {
        return new ProjectContext(
            s.projectName(), s.root().toString(), s.packageName(),
            s.boot() ? "boot" : "war", s.buildTool(), s.egovVersion());
    }

    /** buildResult()에 포함할 구조화 블록 */
    public String toBlock() {
        return """
            [PROJECT_CONTEXT]
            projectName=%s
            rootPath=%s
            packageName=%s
            projectType=%s
            buildTool=%s
            egovVersion=%s
            [/PROJECT_CONTEXT]""".formatted(
                projectName, rootPath, packageName, projectType, buildTool, egovVersion);
    }
}
