package com.krdevops.springai.model;

import java.nio.file.Path;
import java.nio.file.Paths;

public record ProjectSpec(
        String projectName,
        String groupId,
        String artifactId,
        String packageName,
        String buildTool,
        boolean boot,
        Path root,
        String packagePath,
        VersionCapability cap
) {
    public static ProjectSpec of(String projectName, String groupId, String artifactId,
                                 String packageName, String buildTool, String projectType,
                                 String outputPath, VersionCapability cap) {
        boolean boot = "boot".equalsIgnoreCase(projectType);
        return new ProjectSpec(
            projectName, groupId, artifactId, packageName, buildTool, boot,
            Paths.get(outputPath, projectName),
            packageName.replace(".", "/"), cap);
    }

    public boolean gradle()      { return "gradle".equalsIgnoreCase(buildTool); }
    public String egovVersion()  { return cap.egovVersion(); }  // "4.3" / "5.0" 축약
    public String egovLabel()    { return cap.label(); }        // 표시용

    /** artifactId → PascalCase 클래스명 (예: my-project → MyProject) */
    public String className() {
        StringBuilder sb = new StringBuilder();
        for (String part : artifactId.split("[-_]")) {
            if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
