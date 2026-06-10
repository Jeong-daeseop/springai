package com.krdevops.springai.model;

import java.nio.file.Path;

/**
 * Security 템플릿 생성 요청의 정규화된 스펙.
 * 입력 정규화, 버전 정보, 루트 경로, 패키지 헬퍼를 포함한다.
 */
public record SecuritySpec(
        String securityType,
        String packageName,
        String projectType,
        Path root,
        VersionCapability capability
) {

    /**
     * 팩토리 메서드 — 입력값 정규화 포함.
     */
    public static SecuritySpec of(
            String securityType,
            String packageName,
            String projectType,
            String outputPath,
            VersionCapability capability) {

        String pkg  = (packageName == null || packageName.isBlank())
                ? "egovframework.let.sample" : packageName;
        String type = (projectType  == null || projectType.isBlank())
                ? "war" : projectType;
        Path   root = (outputPath   == null || outputPath.isBlank())
                ? null : Path.of(outputPath);

        return new SecuritySpec(securityType, pkg, type, root, capability);
    }

    /** packageName → 경로 형태. 예) egovframework.let.sample → egovframework/let/sample */
    public String packagePath() {
        return packageName.replace('.', '/');
    }

    /** packageName의 마지막 세그먼트. 예) egovframework.let.sample → sample */
    public String basePackage() {
        String[] parts = packageName.split("\\.");
        return parts[parts.length - 1];
    }

    /** eGovFrame 버전에 따른 서블릿 패키지명. 5.0 이상 → jakarta, 4.3 → javax */
    public String javaxOrJakarta() {
        return capability.jakarta() ? "jakarta" : "javax";
    }

    public boolean hasOutputPath() {
        return root != null;
    }
}
