package com.krdevops.springai.service;

import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
public class ThymeleafLayoutValidator {

    public static final String DEFAULT_LAYOUT_VIEW = "layout/default";
    public static final String DEFAULT_BREADCRUMB_VIEW = "layout/breadcrumb";
    public static final String DEFAULT_LAYOUT_BASE_PATH = "layout";

    private static final List<String> REQUIRED_FILES = List.of(
            "default.html",
            "gnb.html",
            "lnb.html",
            "breadcrumb.html",
            "footer.html"
    );

    public LayoutReference resolve(String layoutView, String breadcrumbView) {
        String resolvedLayoutView = normalizeView(defaultIfBlank(layoutView, DEFAULT_LAYOUT_VIEW), "layoutView");
        String resolvedBreadcrumbView = normalizeView(defaultIfBlank(breadcrumbView, DEFAULT_BREADCRUMB_VIEW), "breadcrumbView");

        int slash = resolvedLayoutView.lastIndexOf('/');
        String basePath = slash >= 0 ? resolvedLayoutView.substring(0, slash) : DEFAULT_LAYOUT_BASE_PATH;
        String expectedBreadcrumb = basePath + "/breadcrumb";
        if (!expectedBreadcrumb.equals(resolvedBreadcrumbView)) {
            throw new IllegalArgumentException(
                    "breadcrumbView는 layoutView와 같은 base를 사용해야 합니다. 기대값: " + expectedBreadcrumb);
        }
        return new LayoutReference(resolvedLayoutView, resolvedBreadcrumbView, basePath);
    }

    public LayoutValidationResult validateExisting(String outputPath, String layoutView, String breadcrumbView) {
        LayoutReference reference = resolve(layoutView, breadcrumbView);
        Path baseDir = Paths.get(outputPath, "src/main/resources/templates", reference.layoutBasePath())
                .normalize();
        List<String> missing = new ArrayList<>();
        for (String fileName : REQUIRED_FILES) {
            Path file = baseDir.resolve(fileName).normalize();
            if (!Files.isRegularFile(file)) {
                missing.add(file.toString());
            }
        }
        return new LayoutValidationResult(reference, missing);
    }

    public String normalizeLayoutBasePath(String layoutBasePath) {
        return normalizeView(defaultIfBlank(layoutBasePath, DEFAULT_LAYOUT_BASE_PATH), "layoutBasePath");
    }

    public List<String> requiredFiles() {
        return REQUIRED_FILES;
    }

    public String missingLayoutMessage(String outputPath, LayoutValidationResult result) {
        return "Thymeleaf layout 파일이 없습니다. 먼저 generateThymeleafLayout(outputPath=\""
                + outputPath + "\", layoutBasePath=\"" + result.reference().layoutBasePath()
                + "\")를 실행하세요. 누락 파일: " + String.join(", ", result.missingFiles());
    }

    private String normalizeView(String raw, String name) {
        String value = raw.trim().replace('\\', '/');
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        if (value.endsWith(".html")) {
            value = value.substring(0, value.length() - ".html".length());
        }
        if (value.isBlank() || value.contains("..") || value.contains("//")) {
            throw new IllegalArgumentException(name + " 값이 올바르지 않습니다: " + raw);
        }
        return value;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public record LayoutReference(String layoutView, String breadcrumbView, String layoutBasePath) {
    }

    public record LayoutValidationResult(LayoutReference reference, List<String> missingFiles) {
        public boolean valid() {
            return missingFiles.isEmpty();
        }
    }
}
