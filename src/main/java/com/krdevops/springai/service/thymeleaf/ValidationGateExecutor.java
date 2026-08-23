package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.GateSeverity;
import com.krdevops.springai.model.thymeleaf.LegacyScreenRole;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingContract;
import com.krdevops.springai.model.thymeleaf.ValidationGateResult;
import com.krdevops.springai.model.thymeleaf.ValidationGateType;
import com.krdevops.springai.model.thymeleaf.ValidationReport;
import org.springframework.stereotype.Service;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.support.DataBindingPropertyAccessor;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import com.krdevops.springai.service.observability.OperationalTelemetry;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private static final Pattern WEB_LINK_ATTRIBUTE = Pattern.compile(
            "th:(action|href)=\"@\\{([^\"}]*)}\"");
    private static final Pattern FIELD_ERROR_CONDITION = Pattern.compile(
            "th:if=\"\\$\\{#fields\\.hasErrors\\('[^']+'\\)}\"");
    private static final PropertyAccessor MAP_PROPERTY_ACCESSOR = new PropertyAccessor() {
        @Override
        public Class<?>[] getSpecificTargetClasses() {
            return new Class<?>[]{Map.class};
        }

        @Override
        public boolean canRead(EvaluationContext context, Object target, String name) {
            return target instanceof Map<?, ?> map && map.containsKey(name);
        }

        @Override
        public TypedValue read(EvaluationContext context, Object target, String name) throws AccessException {
            if (!(target instanceof Map<?, ?> map) || !map.containsKey(name)) {
                throw new AccessException("대표 렌더 문맥에 필드가 없습니다: " + name);
            }
            return new TypedValue(map.get(name));
        }

        @Override
        public boolean canWrite(EvaluationContext context, Object target, String name) {
            return false;
        }

        @Override
        public void write(EvaluationContext context, Object target, String name, Object newValue)
                throws AccessException {
            throw new AccessException("대표 렌더 문맥은 읽기 전용입니다.");
        }
    };
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
    private OperationalTelemetry telemetry;

    public ValidationGateExecutor() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        this.templateEngine = new SpringTemplateEngine();
        this.templateEngine.setTemplateResolver(resolver);
    }

    @Autowired
    void configureTelemetry(OperationalTelemetry telemetry) { this.telemetry = telemetry; }

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

    /**
     * WP6/WP8 Gate 자동 연결용 진입점. 영속된 Binding Contract에서 화면 역할별 예상 바인딩과
     * 실제 렌더 대표 문맥을 도출해 parse/render/binding/route/overflow Gate를 한 번에 실행한다.
     */
    public ValidationReport runThymeleafGates(
            ThymeleafBindingContract contract, String composedHtml) {
        if (contract == null) {
            throw new IllegalArgumentException("bindingContract는 필수입니다.");
        }
        List<ValidationGateResult> results = new ArrayList<>();
        results.add(validateThymeleafParse(composedHtml));
        results.add(validateTemplateEngineRender(
                renderProbeTemplate(composedHtml), renderContext(contract)));
        results.add(validateBindingContract(composedHtml, expectedBindings(contract)));
        results.add(validateRouteParity(contract.route().route(), composedHtml));
        results.add(validateNoOverflow(composedHtml));

        boolean blocked = results.stream()
                .anyMatch(result -> !result.passed() && severityOf(result.gateType()) == GateSeverity.BLOCK);
        return new ValidationReport(
                contract.screenId(), results, blocked, sha256Hex(composedHtml), Instant.now());
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
        TemplateRenderProbe probe = renderTemplateEngine(contentFragment, contextVariables);

        long duration = System.currentTimeMillis() - start;
        return observed(new ValidationGateResult(
                ValidationGateType.TEMPLATE_ENGINE_RENDER, probe.passed(), probe.issues(), duration, Instant.now()));
    }

    /** 실제 Spring Template Engine 출력과 오류를 Fixture 재현성 검증에 사용할 수 있게 반환한다. */
    public TemplateRenderProbe renderTemplateEngine(
            String contentFragment, Map<String, Object> contextVariables) {
        List<String> issues = new ArrayList<>();
        try {
            Context context = new Context(Locale.KOREA);
            if (contextVariables != null) contextVariables.forEach(context::setVariable);
            EvaluationContext evaluationContext = SimpleEvaluationContext
                    .forPropertyAccessors(
                            MAP_PROPERTY_ACCESSOR, DataBindingPropertyAccessor.forReadOnlyAccess())
                    .withInstanceMethods()
                    .build();
            context.setVariable(
                    ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME,
                    evaluationContext);
            return new TemplateRenderProbe(true, templateEngine.process(contentFragment, context), List.of());
        } catch (TemplateProcessingException exception) {
            issues.add("실제 렌더 실패: " + exception.getMessage());
        } catch (Exception exception) {
            issues.add("렌더 중 예상치 못한 오류: " + exception.getMessage());
        }
        return new TemplateRenderProbe(false, null, issues);
    }

    public record TemplateRenderProbe(boolean passed, String output, List<String> issues) {
        public TemplateRenderProbe {
            issues = List.copyOf(issues);
        }
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
        return observed(new ValidationGateResult(
            ValidationGateType.THYMELEAF_PARSE,
            issues.isEmpty(),
            issues,
            duration,
            Instant.now()
        ));
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
        return observed(new ValidationGateResult(
            ValidationGateType.BINDING_VALIDATION,
            issues.isEmpty(),
            issues,
            duration,
            Instant.now()
        ));
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
            if (!containsRoute(thymeleafTemplate, "action", originalRoute)
                    && !containsRoute(thymeleafTemplate, "href", originalRoute)) {
                issues.add("액션/링크가 원본 route와 일치하지 않음: " + originalRoute);
            }
        } catch (Exception e) {
            issues.add("라우트 검증 오류: " + e.getMessage());
        }

        long duration = System.currentTimeMillis() - start;
        return observed(new ValidationGateResult(
            ValidationGateType.ROUTE_PARITY,
            issues.isEmpty(),
            issues,
            duration,
            Instant.now()
        ));
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
        return observed(new ValidationGateResult(
            ValidationGateType.OVERFLOW_CHECK,
            issues.isEmpty(),
            issues,
            duration,
            Instant.now()
        ));
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
        return observed(new ValidationGateResult(
            ValidationGateType.BUILD_VALIDATION,
            issues.isEmpty(),
            issues,
            duration,
            Instant.now()
        ));
    }

    /**
     * R6-060: 생성 대상 프로젝트의 의존성을 새로 받지 않는 재현 가능한 offline build Gate.
     * Gradle Wrapper 또는 Maven Wrapper가 있을 때만 실행하며, 임의의 시스템 Gradle/Maven은 사용하지 않는다.
     */
    public ValidationGateResult validateOfflineBuild(Path projectRoot) {
        long start = System.currentTimeMillis();
        List<String> issues = new ArrayList<>();
        if (projectRoot == null || !Files.isDirectory(projectRoot)) {
            issues.add("OFFLINE_BUILD_PROJECT_NOT_FOUND");
            return observed(new ValidationGateResult(ValidationGateType.BUILD_VALIDATION, false, issues,
                    System.currentTimeMillis() - start, Instant.now()));
        }
        List<String> command;
        if (Files.isRegularFile(projectRoot.resolve("gradlew"))) {
            command = List.of(projectRoot.resolve("gradlew").toAbsolutePath().toString(), "--offline", "assemble");
        } else if (Files.isRegularFile(projectRoot.resolve("gradlew.bat"))) {
            command = List.of(projectRoot.resolve("gradlew.bat").toAbsolutePath().toString(), "--offline", "assemble");
        } else if (Files.isRegularFile(projectRoot.resolve("mvnw"))) {
            command = List.of(projectRoot.resolve("mvnw").toAbsolutePath().toString(), "-o", "-DskipTests", "package");
        } else if (Files.isRegularFile(projectRoot.resolve("mvnw.cmd"))) {
            command = List.of(projectRoot.resolve("mvnw.cmd").toAbsolutePath().toString(), "-o", "-DskipTests", "package");
        } else {
            issues.add("OFFLINE_BUILD_WRAPPER_NOT_FOUND");
            return observed(new ValidationGateResult(ValidationGateType.BUILD_VALIDATION, false, issues,
                    System.currentTimeMillis() - start, Instant.now()));
        }
        try {
            Process process = new ProcessBuilder(command).directory(projectRoot.toFile())
                    .redirectErrorStream(true).start();
            boolean finished = process.waitFor(Duration.ofMinutes(3).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                issues.add("OFFLINE_BUILD_TIMEOUT");
            } else if (process.exitValue() != 0) {
                issues.add("OFFLINE_BUILD_FAILED(exit=" + process.exitValue() + ")");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            issues.add("OFFLINE_BUILD_INTERRUPTED");
        } catch (Exception exception) {
            issues.add("OFFLINE_BUILD_INFRA_ERROR: " + exception.getMessage());
        }
        return observed(new ValidationGateResult(ValidationGateType.BUILD_VALIDATION, issues.isEmpty(), issues,
                System.currentTimeMillis() - start, Instant.now()));
    }

    // ===== 헬퍼 메서드 =====

    private ValidationGateResult observed(ValidationGateResult result) {
        if (telemetry != null) telemetry.gate(result.gateType(), severityOf(result.gateType()),
                result.passed(), java.time.Duration.ofMillis(result.durationMs()));
        return result;
    }

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

    private boolean containsRoute(String template, String attribute, String route) {
        return template.contains("th:" + attribute + "=\"" + route + "\"")
                || template.contains("th:" + attribute + "=\"@{" + route + "}\"")
                || template.contains(attribute + "=\"" + route + "\"");
    }

    private Set<String> expectedBindings(ThymeleafBindingContract contract) {
        Set<String> expected = new LinkedHashSet<>();
        if (contract.screenRole() == LegacyScreenRole.FORM) {
            contract.fields().forEach(field ->
                    expected.add("th:field=\"*{" + field.fieldName() + "}\""));
            return expected;
        }
        if (contract.primaryDisplayAttributeName() != null) {
            String item = contract.screenRole() == LegacyScreenRole.LIST ? "item"
                    : contract.primaryDisplayAttributeName();
            contract.displayFieldNames().forEach(field ->
                    expected.add("${" + item + "." + field + "}"));
        }
        contract.secondaryDisplayFieldNames().forEach(field ->
                expected.add("${item." + field + "}"));
        return expected;
    }

    private Map<String, Object> renderContext(ThymeleafBindingContract contract) {
        Map<String, Object> variables = new LinkedHashMap<>();
        Map<String, Object> primary = values(contract.displayFieldNames());
        Map<String, Object> allFields = values(contract.fields().stream()
                .map(field -> field.fieldName()).toList());

        if (contract.screenRole() == LegacyScreenRole.FORM) {
            String modelAttribute = contract.route().modelAttributeName();
            if (modelAttribute != null && !modelAttribute.isBlank()) {
                variables.put(modelAttribute, allFields);
            }
        } else if (contract.primaryDisplayAttributeName() != null) {
            Object value = contract.screenRole() == LegacyScreenRole.LIST
                    ? List.of(primary) : primary;
            variables.put(contract.primaryDisplayAttributeName(), value);
        }
        if (contract.secondaryDisplayAttributeName() != null) {
            variables.put(contract.secondaryDisplayAttributeName(),
                    List.of(values(contract.secondaryDisplayFieldNames())));
        }
        variables.put("message", null);
        variables.put("param", Map.of("searchKeyword", ""));
        variables.put("_csrf", null);
        return variables;
    }

    private Map<String, Object> values(List<String> fieldNames) {
        Map<String, Object> values = new LinkedHashMap<>();
        fieldNames.forEach(field -> values.put(field, "validation-value"));
        return values;
    }

    /**
     * 비-web Context에서 처리할 수 없는 URL/폼 전용 processor만 계약 기반 Gate가 별도로 검증하는
     * 동등한 표준 속성으로 바꾼다. 나머지 템플릿은 실제 SpringTemplateEngine이 그대로 처리한다.
     */
    private String renderProbeTemplate(String html) {
        return WEB_LINK_ATTRIBUTE.matcher(html).replaceAll("$1=\"$2\"")
                .replace("th:field=\"*{", "th:value=\"*{")
                .replaceAll("\\s+th:errors=\"\\*\\{[^}]+}\"", "")
                .replaceAll(FIELD_ERROR_CONDITION.pattern(),
                        Matcher.quoteReplacement("th:if=\"${false}\""))
                .replace("th:name=\"${_csrf.parameterName}\"", "name=\"_csrf\"")
                .replace("th:value=\"${_csrf.token}\"", "value=\"\"");
    }

    private int countOccurrences(String text, String pattern) {
        return text.split(Pattern.quote(pattern), -1).length - 1;
    }
}
