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
        VersionCapability cap,
        String viewType,
        String serverPort
) {
    public static ProjectSpec of(String projectName, String groupId, String artifactId,
                                 String packageName, String buildTool, String projectType,
                                 String outputPath, VersionCapability cap) {
        return of(projectName, groupId, artifactId, packageName, buildTool, projectType,
                outputPath, cap, "jsp");
    }

    public static ProjectSpec of(String projectName, String groupId, String artifactId,
                                 String packageName, String buildTool, String projectType,
                                 String outputPath, VersionCapability cap, String viewType) {
        return of(projectName, groupId, artifactId, packageName, buildTool, projectType,
                outputPath, cap, viewType, null);
    }

    public static ProjectSpec of(String projectName, String groupId, String artifactId,
                                 String packageName, String buildTool, String projectType,
                                 String outputPath, VersionCapability cap, String viewType,
                                 String serverPort) {
        boolean boot = "boot".equalsIgnoreCase(projectType);
        return new ProjectSpec(
            projectName, groupId, artifactId, packageName, buildTool, boot,
            Paths.get(outputPath, projectName),
            packageName.replace(".", "/"), cap, normalizeViewType(viewType),
            normalizeServerPort(serverPort));
    }

    public boolean gradle()      { return "gradle".equalsIgnoreCase(buildTool); }
    public String egovVersion()  { return cap.egovVersion(); }  // "4.3" / "5.0" 축약
    public String egovLabel()    { return cap.label(); }        // 표시용
    public boolean thymeleaf()   { return "thymeleaf".equalsIgnoreCase(viewType); }

    private static String normalizeViewType(String viewType) {
        if (viewType == null || viewType.isBlank()) {
            return "jsp";
        }
        String normalized = viewType.trim().toLowerCase();
        if (!normalized.equals("jsp") && !normalized.equals("thymeleaf")) {
            throw new IllegalArgumentException("지원하지 않는 viewType 입니다: " + viewType
                    + " (지원값: jsp, thymeleaf)");
        }
        return normalized;
    }

    /** Boot의 server.port 기본값. 미입력 시 8080. 숫자·1~65535 범위만 허용한다. */
    private static String normalizeServerPort(String serverPort) {
        if (serverPort == null || serverPort.isBlank()) {
            return "8080";
        }
        String trimmed = serverPort.trim();
        int port;
        try {
            port = Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("serverPort는 숫자여야 합니다: " + serverPort);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("serverPort는 1~65535 범위여야 합니다: " + serverPort);
        }
        return trimmed;
    }

    /** artifactId → PascalCase 클래스명 (예: my-project → MyProject) */
    public String className() {
        StringBuilder sb = new StringBuilder();
        for (String part : artifactId.split("[-_]")) {
            if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
