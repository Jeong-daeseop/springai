package com.krdevops.springai.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class GeneratedCodeContractAuditor {

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "java", "xml", "html", "jsp", "css", "js", "properties", "yml", "yaml");
    private static final Pattern MUSTACHE_PLACEHOLDER = Pattern.compile("\\{\\{[A-Z0-9_]+}}", Pattern.MULTILINE);
    private static final Pattern KRDS_SIZED_ELEMENT = Pattern.compile(
            "<(?:input|select|textarea|button|a)\\b[^>]*\\bclass\\s*=\\s*[\"']([^\"']*\\bkrds-(?:input|form-select|btn)\\b[^\"']*)[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern POST_FORM = Pattern.compile(
            "<form\\b(?=[^>]*\\bmethod\\s*=\\s*[\"']post[\"'])[^>]*>(.*?)</form>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final List<String> CLAUDE_DESIGN_MARKERS = List.of("x-dc", "sc-for");

    public List<String> audit(String outputPath) {
        Path root = Path.of(outputPath);
        if (!Files.isDirectory(root)) return List.of("생성 결과 디렉터리가 없습니다: " + root);
        List<String> failures = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(this::isTextFile)
                    .forEach(path -> auditFile(root, path, failures));
        } catch (Exception e) {
            failures.add("생성 결과 감사 중 파일 탐색 실패: " + e.getMessage());
        }
        return List.copyOf(failures);
    }

    /** HTML/JSP의 기본 접근성 계약을 별도 진단한다. 기존 생성 저장 흐름에는 강제 적용하지 않는다. */
    public List<String> auditAccessibility(String outputPath) {
        Path root = Path.of(outputPath);
        if (!Files.isDirectory(root)) return List.of("생성 결과 디렉터리가 없습니다: " + root);
        List<String> failures = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase();
                        return name.endsWith(".html") || name.endsWith(".jsp");
                    })
                    .forEach(path -> auditAccessibilityFile(root, path, failures));
        } catch (Exception e) {
            failures.add("접근성 감사 중 파일 탐색 실패: " + e.getMessage());
        }
        return List.copyOf(failures);
    }

    private void auditFile(Path root, Path path, List<String> failures) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            String relative = root.relativize(path).toString();
            if (source.contains("<#") || source.contains("</#")) {
                failures.add(relative + " — FreeMarker 지시문 잔존");
            }
            if (MUSTACHE_PLACEHOLDER.matcher(source).find()) {
                failures.add(relative + " — 미치환 {{...}} 플레이스홀더 잔존");
            }
            for (String marker : CLAUDE_DESIGN_MARKERS) {
                if (source.contains(marker)) failures.add(relative + " — Claude Design 전용 태그 잔존: " + marker);
            }
            if (relative.contains("mapper") && relative.endsWith(".xml") && source.contains("${")) {
                failures.add(relative + " — Mapper XML 문자열 치환 ${} 사용");
            }
            if ((relative.endsWith(".html") || relative.endsWith(".jsp"))
                    && (source.contains("src=\"https://") || source.contains("href=\"https://"))) {
                failures.add(relative + " — 외부 CDN/URL 직접 참조");
            }
            if (relative.endsWith(".html")) {
                auditHtmlUiContract(relative, source, failures);
            }
        } catch (Exception e) {
            failures.add(root.relativize(path) + " — 감사 파일 읽기 실패: " + e.getMessage());
        }
    }

    private void auditHtmlUiContract(String relative, String source, List<String> failures) {
        boolean scopedCrudScreen = source.contains("egov-crud-page");
        var sizedElements = KRDS_SIZED_ELEMENT.matcher(source);
        while (sizedElements.find()) {
            String classes = sizedElements.group(1);
            if (!hasSizeModifier(classes)) {
                failures.add(relative + " — KRDS 크기 modifier 누락: " + primaryKrdsClass(classes));
            }
            if (scopedCrudScreen && hasClass(classes, "krds-btn") && !hasClass(classes, "egov-btn")) {
                failures.add(relative + " — CRUD 공통 버튼 클래스 누락: egov-btn");
            }
        }
        var postForms = POST_FORM.matcher(source);
        while (postForms.find()) {
            if (!postForms.group(1).contains("_csrf")) {
                failures.add(relative + " — POST form CSRF 토큰 누락");
            }
        }
    }

    private boolean hasSizeModifier(String classes) {
        return List.of(classes.split("\\s+")).stream()
                .anyMatch(value -> value.equals("small") || value.equals("medium") || value.equals("large"));
    }

    private boolean hasClass(String classes, String expected) {
        return List.of(classes.split("\\s+")).stream().anyMatch(expected::equals);
    }

    private String primaryKrdsClass(String classes) {
        return List.of(classes.split("\\s+")).stream()
                .filter(value -> value.startsWith("krds-"))
                .findFirst()
                .orElse("krds component");
    }

    private boolean isTextFile(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && TEXT_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase());
    }

    private void auditAccessibilityFile(Path root, Path path, List<String> failures) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            String relative = root.relativize(path).toString();
            if (Pattern.compile("<html\\b", Pattern.CASE_INSENSITIVE).matcher(source).find()
                    && !Pattern.compile("<html\\b[^>]*\\blang\\s*=", Pattern.CASE_INSENSITIVE)
                    .matcher(source).find()) {
                failures.add(relative + " — html lang 속성 없음");
            }
            var images = Pattern.compile("<img\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(source);
            while (images.find()) {
                if (!Pattern.compile("\\balt\\s*=", Pattern.CASE_INSENSITIVE)
                        .matcher(images.group()).find()) {
                    failures.add(relative + " — img alt 속성 없음");
                    break;
                }
            }
            var buttons = Pattern.compile("<button\\b([^>]*)>(.*?)</button>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(source);
            while (buttons.find()) {
                String attributes = buttons.group(1);
                String text = buttons.group(2).replaceAll("<[^>]+>", "").trim();
                if (text.isEmpty() && !Pattern.compile("aria-label\\s*=|title\\s*=", Pattern.CASE_INSENSITIVE)
                        .matcher(attributes).find()) {
                    failures.add(relative + " — 이름 없는 button 요소");
                    break;
                }
            }
        } catch (Exception e) {
            failures.add(root.relativize(path) + " — 접근성 감사 파일 읽기 실패: " + e.getMessage());
        }
    }
}
