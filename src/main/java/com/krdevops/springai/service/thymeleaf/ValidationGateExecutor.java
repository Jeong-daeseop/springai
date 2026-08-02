package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.ValidationGateResult;
import com.krdevops.springai.model.thymeleaf.ValidationGateType;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.exceptions.TemplateProcessingException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * I-5C: 검증 Gate 실행자.
 * Thymeleaf 생성 결과 검증: parse, render, parity, overflow, build.
 */
@Service
public class ValidationGateExecutor {

    private static final Pattern BINDING_PATTERN = Pattern.compile("\\$\\{[^}]+\\}|th:field=\"\\*\\{[^}]+\\}\"");
    private static final int MAX_WIDTH_DESKTOP = 1440;
    private static final int MAX_WIDTH_TABLET = 768;
    private static final int MAX_WIDTH_MOBILE = 390;

    /**
     * Thymeleaf 파싱 검증.
     */
    public ValidationGateResult validateThymeleafParse(String htmlContent) {
        long start = System.currentTimeMillis();
        List<String> issues = new ArrayList<>();

        try {
            // 기본 XML/HTML 구조 검증
            if (!isWellFormedHtml(htmlContent)) {
                issues.add("HTML 구조가 올바르지 않음 (unclosed tags)");
            }
            // Thymeleaf 속성 검증
            if (!isValidThymeleafAttributes(htmlContent)) {
                issues.add("Thymeleaf 속성 형식이 올바르지 않음");
            }
        } catch (Exception e) {
            issues.add("파싱 오류: " + e.getMessage());
        }

        long duration = System.currentTimeMillis() - start;
        return new ValidationGateResult(
            ValidationGateType.THYMELEAF_PARSE,
            issues.isEmpty(),
            issues,
            duration,
            Instant.now()
        );
    }

    /**
     * 바인딩 계약 검증.
     */
    public ValidationGateResult validateBindingContract(String htmlContent, Set<String> expectedBindings) {
        long start = System.currentTimeMillis();
        List<String> issues = new ArrayList<>();

        try {
            Set<String> foundBindings = extractBindings(htmlContent);

            // 예상 바인딩이 모두 포함되어 있는지 확인
            for (String expected : expectedBindings) {
                if (!foundBindings.contains(expected)) {
                    issues.add("바인딩 누락: " + expected);
                }
            }
        } catch (Exception e) {
            issues.add("바인딩 검증 오류: " + e.getMessage());
        }

        long duration = System.currentTimeMillis() - start;
        return new ValidationGateResult(
            ValidationGateType.BINDING_VALIDATION,
            issues.isEmpty(),
            issues,
            duration,
            Instant.now()
        );
    }

    /**
     * 라우트/필드/액션 일치성 검증.
     */
    public ValidationGateResult validateRouteParity(String originalRoute, String thymeleafTemplate) {
        long start = System.currentTimeMillis();
        List<String> issues = new ArrayList<>();

        try {
            // form action이 원본 route와 일치하는지 확인
            if (!thymeleafTemplate.contains("th:action=\"" + originalRoute + "\"") &&
                !thymeleafTemplate.contains("action=\"" + originalRoute + "\"")) {
                issues.add("폼 액션이 원본 route와 일치하지 않음: " + originalRoute);
            }
        } catch (Exception e) {
            issues.add("라우트 검증 오류: " + e.getMessage());
        }

        long duration = System.currentTimeMillis() - start;
        return new ValidationGateResult(
            ValidationGateType.ROUTE_PARITY,
            issues.isEmpty(),
            issues,
            duration,
            Instant.now()
        );
    }

    /**
     * 오버플로우 검증 (viewport 폭 초과 확인).
     */
    public ValidationGateResult validateNoOverflow(String htmlContent) {
        long start = System.currentTimeMillis();
        List<String> issues = new ArrayList<>();

        try {
            // inline style에서 width: 값이 viewport를 초과하는지 확인
            Pattern widthPattern = Pattern.compile("width:\\s*(\\d+)px");
            Matcher matcher = widthPattern.matcher(htmlContent);

            while (matcher.find()) {
                int width = Integer.parseInt(matcher.group(1));
                if (width > MAX_WIDTH_DESKTOP) {
                    issues.add(String.format("Desktop 오버플로우: %dpx > %dpx", width, MAX_WIDTH_DESKTOP));
                }
            }
        } catch (Exception e) {
            issues.add("오버플로우 검증 오류: " + e.getMessage());
        }

        long duration = System.currentTimeMillis() - start;
        return new ValidationGateResult(
            ValidationGateType.OVERFLOW_CHECK,
            issues.isEmpty(),
            issues,
            duration,
            Instant.now()
        );
    }

    /**
     * 빌드 검증 (파일 저장 가능 여부 확인).
     */
    public ValidationGateResult validateBuild(Path targetPath, String htmlContent) {
        long start = System.currentTimeMillis();
        List<String> issues = new ArrayList<>();

        try {
            // 경로 생성 가능 여부 확인
            if (targetPath.getParent() != null && !Files.exists(targetPath.getParent())) {
                Files.createDirectories(targetPath.getParent());
            }
            // 파일 크기 제한 (1MB)
            if (htmlContent.length() > 1_000_000) {
                issues.add(String.format("파일 크기 초과: %d bytes > 1MB", htmlContent.length()));
            }
        } catch (Exception e) {
            issues.add("빌드 검증 오류: " + e.getMessage());
        }

        long duration = System.currentTimeMillis() - start;
        return new ValidationGateResult(
            ValidationGateType.BUILD_VALIDATION,
            issues.isEmpty(),
            issues,
            duration,
            Instant.now()
        );
    }

    // ===== 헬퍼 메서드 =====

    private boolean isWellFormedHtml(String html) {
        // 기본 태그 닫힘 확인
        int openDiv = countOccurrences(html, "<div");
        int closeDiv = countOccurrences(html, "</div>");
        if (openDiv != closeDiv) {
            return false;
        }

        int openForm = countOccurrences(html, "<form");
        int closeForm = countOccurrences(html, "</form>");
        return openForm == closeForm;
    }

    private boolean isValidThymeleafAttributes(String html) {
        // th: 속성들이 올바르게 사용되었는지 기본 확인
        Pattern thAttrPattern = Pattern.compile("th:[a-z-]+\\s*=\\s*['\"][^'\"]*['\"]");
        return thAttrPattern.matcher(html).find() || !html.contains("th:");
    }

    private Set<String> extractBindings(String html) {
        Set<String> bindings = new HashSet<>();
        Matcher matcher = BINDING_PATTERN.matcher(html);
        while (matcher.find()) {
            bindings.add(matcher.group());
        }
        return bindings;
    }

    private int countOccurrences(String text, String pattern) {
        return text.split(Pattern.quote(pattern), -1).length - 1;
    }
}
