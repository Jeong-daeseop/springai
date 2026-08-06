package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.GateSeverity;
import com.krdevops.springai.model.thymeleaf.ValidationGateResult;
import com.krdevops.springai.model.thymeleaf.ValidationGateType;
import com.krdevops.springai.model.thymeleaf.ValidationReport;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
     * WP8/ARCH-0801: Gate 유형별 BLOCK/WARN 정책. 실결함(구조·렌더·바인딩·라우트)은 BLOCK, 아직
     * 휴리스틱 수준인 반응형 폭 검사는 WARN이다(실제 판정은 WP8 브라우저 Gate 몫).
     */
    private static final Map<ValidationGateType, GateSeverity> GATE_SEVERITY_POLICY = new EnumMap<>(Map.of(
            ValidationGateType.THYMELEAF_PARSE, GateSeverity.BLOCK,
            ValidationGateType.TEMPLATE_ENGINE_RENDER, GateSeverity.BLOCK,
            ValidationGateType.BINDING_VALIDATION, GateSeverity.BLOCK,
            ValidationGateType.ROUTE_PARITY, GateSeverity.BLOCK,
            ValidationGateType.OVERFLOW_CHECK, GateSeverity.WARN,
            ValidationGateType.BUILD_VALIDATION, GateSeverity.BLOCK,
            ValidationGateType.BROWSER_RENDER, GateSeverity.BLOCK,
            ValidationGateType.ACCESSIBILITY, GateSeverity.BLOCK,
            ValidationGateType.VISUAL_PARITY, GateSeverity.BLOCK));

    private final SpringTemplateEngine templateEngine;

    public ValidationGateExecutor() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        this.templateEngine = new SpringTemplateEngine();
        this.templateEngine.setTemplateResolver(resolver);
    }

    /** @return {@code type}이 실패했을 때 Preview/Apply를 막아야 하는지(BLOCK) 검토만 필요한지(WARN). */
    public GateSeverity severityOf(ValidationGateType type) {
        return GATE_SEVERITY_POLICY.getOrDefault(type, GateSeverity.BLOCK);
    }

    /**
     * WP8/ARCH-0805/0811: 화면 하나에 적용 가능한 문자열 기반 Gate(parse/binding/route parity/
     * overflow)를 묶어 실행하고 {@link ValidationReport}로 집계한다. {@link #validateTemplateEngineRender}는
     * 의도적으로 제외한다 — {@code @{...}}/{@code th:field}처럼 실제 WP6 산출물이 흔히 쓰는 표현식이
     * web context 없이는 항상 실패하므로(클래스 Javadoc 참고), 여기 포함시키면 정상적인 화면까지
     * 매번 BLOCK되어 이 묶음 실행 자체가 무의미해진다. 순수 {@code ${...}} 콘텐츠만 검증하고 싶으면
     * {@link #validateTemplateEngineRender}를 별도로 호출하라.
     */
    public ValidationReport runThymeleafGates(
            String screenId, String route, String composedHtml, Set<String> expectedBindings) {
        List<ValidationGateResult> results = new ArrayList<>();
        results.add(validateThymeleafParse(composedHtml));
        results.add(validateBindingContract(composedHtml, expectedBindings));
        results.add(validateRouteParity(route, composedHtml));
        results.add(validateNoOverflow(composedHtml));

        boolean blocked = results.stream()
                .anyMatch(result -> !result.passed() && severityOf(result.gateType()) == GateSeverity.BLOCK);

        return new ValidationReport(screenId, results, blocked, sha256Hex(composedHtml), Instant.now());
    }

    private String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }

    /**
     * WP8/ARCH-0803/0804: {@code validateThymeleafParse}(정규식 기반 휴리스틱)와 달리 실제 Spring
     * {@link SpringTemplateEngine}으로 처리해 진짜 렌더 실패(존재하지 않는 표현식, 잘못된 th:*
     * 문법 등)를 잡는다 — WP6의 {@code BindingComposerTemplateEngineParityTest}가 개별 fixture마다
     * 검증해온 것과 같은 엔진 설정을 재사용 가능한 프로덕션 Gate로 승격한 것이다.
     *
     * <p>{@code @{...}}(링크 URL) / {@code th:field}(Spring 폼 바인딩)처럼 서블릿 요청 컨텍스트가
     * 필요한 표현식은 검증 범위 밖이다 — {@code spring-test}의 Mock 서블릿 객체는 테스트
     * 클래스패스에만 있고 프로덕션 코드(이 클래스)에는 없다. {@code contextVariables}로 템플릿이
     * 참조하는 변수의 대표값을 직접 공급해야 한다.
     */
    public ValidationGateResult validateTemplateEngineRender(String contentFragment, Map<String, Object> contextVariables) {
        long start = System.currentTimeMillis();
        List<String> issues = new ArrayList<>();

        try {
            Context context = new Context(Locale.KOREA);
            if (contextVariables != null) {
                contextVariables.forEach(context::setVariable);
            }
            templateEngine.process(contentFragment, context);
        } catch (TemplateProcessingException exception) {
            issues.add("실제 렌더 실패: " + exception.getMessage());
        } catch (Exception exception) {
            issues.add("렌더 중 예상치 못한 오류: " + exception.getMessage());
        }

        long duration = System.currentTimeMillis() - start;
        return new ValidationGateResult(
                ValidationGateType.TEMPLATE_ENGINE_RENDER, issues.isEmpty(), issues, duration, Instant.now());
    }

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
            // form action이 원본 route와 일치하는지 확인. th:action="@{route}"(Thymeleaf 링크
            // 표현식 — WP6 BindingComposer가 실제로 만드는 형태)와 래핑 없는 th:action="route"/
            // action="route" 둘 다 인정한다.
            if (!thymeleafTemplate.contains("th:action=\"" + originalRoute + "\"") &&
                !thymeleafTemplate.contains("th:action=\"@{" + originalRoute + "}\"") &&
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
