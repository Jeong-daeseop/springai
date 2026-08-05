package com.krdevops.springai.service;

import com.krdevops.springai.model.write.ProjectChangeSet;
import com.krdevops.springai.model.write.ProjectWritePolicy;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.write.ApplyOutcome;
import com.krdevops.springai.service.write.ApprovedProjectWritePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 생성 프로젝트의 MyBatis mapperLocations와 MapperScannerConfigurer를 멱등하게 보강한다.
 *
 * <p>WP7 5차 pass: 저장은 {@code Files.writeString} 원시 호출 대신 공용
 * {@link ApprovedProjectWritePort}({@link ProjectWritePolicy#ATOMIC_APPROVED})로 위임한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MyBatisRuntimeConfigurer {

    static final String CONTEXT_COMMON_XML =
            "src/main/resources/egovframework/spring/context-common.xml";
    static final String MAPPER_LOCATIONS = "classpath*:egovframework/mapper/**/*.xml";

    private static final Pattern SQL_SESSION_FACTORY_BEAN_PATTERN = Pattern.compile(
            "(<bean\\s+id=\"sqlSessionFactory\"\\s+class=\"org\\.mybatis\\.spring\\.SqlSessionFactoryBean\"[^>]*>.*?)"
                    + "(</bean>)",
            Pattern.DOTALL);
    private static final Pattern MAPPER_SCANNER_BASE_PACKAGE_PATTERN = Pattern.compile(
            "(<bean\\b[^>]*class=\"org\\.mybatis\\.spring\\.mapper\\.MapperScannerConfigurer\"[^>]*>.*?"
                    + "<property\\s+name=\"basePackage\"\\s+value=\")([^\"]*)(\"[^>]*/>.*?</bean>)",
            Pattern.DOTALL);

    private final CodeService codeService;
    private final ApprovedProjectWritePort writePort;
    private final OperationHashFactory hashFactory;

    public ConfigurationResult ensureConfigured(String outputPath, String mapperPackage) {
        Path contextCommon = Path.of(outputPath, CONTEXT_COMMON_XML).normalize();
        if (!Files.exists(contextCommon)) {
            return ConfigurationResult.skipped(contextCommon,
                    "context-common.xml이 없어 MyBatis Mapper 설정 보강을 생략했습니다.");
        }

        try {
            String original = Files.readString(contextCommon, StandardCharsets.UTF_8);
            String patched = ensureMapperLocations(original, contextCommon);
            patched = ensureMapperScanner(patched, mapperPackage, contextCommon);
            boolean changed = !patched.equals(original);
            if (changed) {
                writeChange(outputPath, original, patched);
            }

            ValidationResult validation = validateContent(patched, mapperPackage);
            if (!validation.valid()) {
                return ConfigurationResult.failed(contextCommon, validation.message());
            }
            String action = changed ? "보강 완료" : "기존 설정 보존";
            return ConfigurationResult.success(contextCommon, changed,
                    action + " — mapperPackage=" + mapperPackage
                            + ", basePackage=" + requiredBasePackage(mapperPackage));
        } catch (IOException | IllegalStateException e) {
            log.warn("MyBatis Mapper 런타임 설정 보강 실패: {}", e.getMessage());
            return ConfigurationResult.failed(contextCommon, e.getMessage());
        }
    }

    private void writeChange(String outputPath, String before, String after) throws IOException {
        codeService.validateOutputRoot(outputPath);
        ProjectChangeSet changeSet = new ProjectChangeSet(
                outputPath, null,
                List.of(new ProjectChangeSet.FileChange(
                        CONTEXT_COMMON_XML, hashFactory.sha256Hex(before.getBytes(StandardCharsets.UTF_8)),
                        after, null)),
                List.of(), ProjectWritePolicy.ATOMIC_APPROVED);
        ApplyOutcome outcome = writePort.apply(changeSet);
        if (outcome.status() != ApplyOutcome.Status.APPLIED) {
            String detail = switch (outcome.status()) {
                case CONFLICT -> "적용 직전 파일이 변경됨: " + outcome.conflictingPaths();
                case ROLLED_BACK -> outcome.failureDetail();
                default -> "알 수 없는 결과: " + outcome.status();
            };
            throw new IOException(detail);
        }
    }

    public ValidationResult validate(String outputPath, String mapperPackage) {
        Path contextCommon = Path.of(outputPath, CONTEXT_COMMON_XML).normalize();
        if (!Files.exists(contextCommon)) {
            return new ValidationResult(false, "context-common.xml 파일이 없습니다: " + contextCommon);
        }
        try {
            return validateContent(Files.readString(contextCommon, StandardCharsets.UTF_8), mapperPackage);
        } catch (Exception e) {
            return new ValidationResult(false, "context-common.xml 읽기 실패: " + e.getMessage());
        }
    }

    private String ensureMapperLocations(String content, Path contextCommon) {
        if (content.contains("name=\"mapperLocations\"") && content.contains(MAPPER_LOCATIONS)) {
            return content;
        }
        if (content.contains("name=\"mapperLocations\"")) {
            throw new IllegalStateException(
                    "기존 mapperLocations가 생성 Mapper XML 경로를 포함하지 않습니다: " + contextCommon);
        }
        Matcher matcher = SQL_SESSION_FACTORY_BEAN_PATTERN.matcher(content);
        if (!matcher.find()) {
            throw new IllegalStateException("SqlSessionFactoryBean을 찾을 수 없습니다: " + contextCommon);
        }
        String property = "\n        <property name=\"mapperLocations\" value=\""
                + MAPPER_LOCATIONS + "\"/>\n    ";
        return content.substring(0, matcher.end(1)) + property + content.substring(matcher.start(2));
    }

    private String ensureMapperScanner(String content, String mapperPackage, Path contextCommon) {
        String required = requiredBasePackage(mapperPackage);
        if (!content.contains("org.mybatis.spring.mapper.MapperScannerConfigurer")) {
            if (countOccurrences(content, "</beans>") != 1) {
                throw new IllegalStateException(
                        "MapperScannerConfigurer 삽입 위치를 확정할 수 없습니다: " + contextCommon);
            }
            String block = """

                    <bean class="org.mybatis.spring.mapper.MapperScannerConfigurer">
                        <property name="basePackage" value="%s"/>
                        <property name="sqlSessionFactoryBeanName" value="sqlSessionFactory"/>
                        <property name="annotationClass" value="org.apache.ibatis.annotations.Mapper"/>
                    </bean>
                    """.formatted(required);
            return content.replace("</beans>", block + "\n</beans>");
        }

        Matcher matcher = MAPPER_SCANNER_BASE_PACKAGE_PATTERN.matcher(content);
        if (!matcher.find()) {
            throw new IllegalStateException(
                    "MapperScannerConfigurer basePackage 속성을 찾을 수 없습니다: " + contextCommon);
        }
        String existing = matcher.group(2);
        if (packagesCover(existing, required) && packagesCover(existing, mapperPackage)) {
            return content;
        }
        String merged = mergeBasePackages(existing, required);
        return content.substring(0, matcher.start(2)) + merged + content.substring(matcher.end(2));
    }

    private ValidationResult validateContent(String content, String mapperPackage) {
        if (!content.contains("name=\"mapperLocations\"") || !content.contains(MAPPER_LOCATIONS)) {
            return new ValidationResult(false, "mapperLocations가 생성 Mapper XML 경로를 포함하지 않습니다.");
        }
        Matcher matcher = MAPPER_SCANNER_BASE_PACKAGE_PATTERN.matcher(content);
        if (!matcher.find()) {
            return new ValidationResult(false, "MapperScannerConfigurer 또는 basePackage가 없습니다.");
        }
        if (!packagesCover(matcher.group(2), mapperPackage)) {
            return new ValidationResult(false,
                    "MapperScannerConfigurer 범위가 Mapper 패키지를 포함하지 않습니다: "
                            + mapperPackage + " (현재: " + matcher.group(2) + ")");
        }
        return new ValidationResult(true, "MyBatis Mapper 런타임 설정 검증 통과");
    }

    public String requiredBasePackage(String mapperPackage) {
        if (mapperPackage == null || mapperPackage.isBlank()) {
            throw new IllegalArgumentException("mapperPackage는 필수입니다.");
        }
        if (mapperPackage.equals("egovframework.let") || mapperPackage.startsWith("egovframework.let.")) {
            return "egovframework.let";
        }
        return mapperPackage;
    }

    public String mergeBasePackages(String existing, String required) {
        Set<String> candidates = new LinkedHashSet<>(splitBasePackages(existing));
        candidates.add(required);
        List<String> all = new ArrayList<>(candidates);
        return all.stream()
                .filter(candidate -> all.stream().noneMatch(other ->
                        !candidate.equals(other) && packageCovers(other, candidate)))
                .reduce((left, right) -> left + ", " + right)
                .orElse(required);
    }

    public boolean packagesCover(String basePackages, String targetPackage) {
        return splitBasePackages(basePackages).stream()
                .anyMatch(basePackage -> packageCovers(basePackage, targetPackage));
    }

    private boolean packageCovers(String basePackage, String targetPackage) {
        return targetPackage.equals(basePackage) || targetPackage.startsWith(basePackage + ".");
    }

    private List<String> splitBasePackages(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<String> packages = new ArrayList<>();
        for (String token : value.split("[,;\\s]+")) {
            if (!token.isBlank()) packages.add(token.trim());
        }
        return packages;
    }

    private int countOccurrences(String source, String token) {
        int count = 0;
        int from = 0;
        while ((from = source.indexOf(token, from)) >= 0) {
            count++;
            from += token.length();
        }
        return count;
    }

    public record ConfigurationResult(
            boolean success, boolean changed, boolean skipped, Path path, String message) {
        static ConfigurationResult success(Path path, boolean changed, String message) {
            return new ConfigurationResult(true, changed, false, path, message);
        }

        static ConfigurationResult skipped(Path path, String message) {
            return new ConfigurationResult(true, false, true, path, message);
        }

        static ConfigurationResult failed(Path path, String message) {
            return new ConfigurationResult(false, false, false, path, message);
        }
    }

    public record ValidationResult(boolean valid, String message) {
    }
}
