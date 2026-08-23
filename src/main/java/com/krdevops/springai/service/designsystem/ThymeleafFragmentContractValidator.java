package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import com.krdevops.springai.service.write.SafePathResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 실제 프로젝트의 Thymeleaf Fragment 파일·이름·Parameter 선언을 Mapping 계약과 대조한다. */
@Service
public class ThymeleafFragmentContractValidator {

    private static final List<String> TEMPLATE_ROOTS = List.of(
            "src/main/resources/templates",
            "src/main/webapp/WEB-INF/templates");
    private static final long MAX_TEMPLATE_BYTES = 1024 * 1024;
    private static final Pattern REFERENCE = Pattern.compile(
            "^([A-Za-z0-9_./-]+?)(?:\\.html)?\\s*::\\s*([A-Za-z_][A-Za-z0-9_-]*)$");
    private static final Pattern FRAGMENT_ATTRIBUTE = Pattern.compile(
            "th:fragment\\s*=\\s*([\"'])(.*?)\\1", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DECLARATION = Pattern.compile(
            "^([A-Za-z_][A-Za-z0-9_-]*)(?:\\s*\\((.*)\\))?$");
    private static final Pattern PARAMETER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final SafePathResolver pathResolver;

    public ThymeleafFragmentContractValidator(SafePathResolver pathResolver) {
        this.pathResolver = pathResolver;
    }

    public ValidationResult validate(Path projectRoot, DesignCodeComponentMapping mapping) {
        if (mapping == null) throw new IllegalArgumentException("mapping은 필수입니다.");
        Path realProjectRoot = pathResolver.realDirectory(projectRoot);
        List<ValidationIssue> issues = new ArrayList<>();
        FragmentReference reference = parseReference(mapping.thymeleafFragment(), issues);
        if (reference == null) return invalid(mapping, issues);

        List<Path> matches = locateTemplates(realProjectRoot, reference.templatePath(), issues);
        if (matches.size() != 1) return invalid(mapping, issues);
        Path template = matches.get(0);
        String content = readTemplate(template, issues);
        if (content == null) return invalid(mapping, issues);

        List<FragmentDeclaration> declarations = declarations(content, reference.fragmentName(), issues);
        if (declarations.size() != 1) return invalid(mapping, issues);
        FragmentDeclaration declaration = declarations.get(0);

        LinkedHashSet<String> expected = mapping.propertyMappings().stream()
                .map(DesignCodeComponentMapping.PropertyMapping::fragmentParameter)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        mapping.slotMappings().stream().map(DesignCodeComponentMapping.SlotMapping::fragmentSlot)
                .forEach(expected::add);
        LinkedHashSet<String> missing = new LinkedHashSet<>(expected);
        missing.removeAll(declaration.parameters());
        LinkedHashSet<String> additional = new LinkedHashSet<>(declaration.parameters());
        additional.removeAll(expected);
        for (String parameter : missing) {
            issues.add(new ValidationIssue("FRAGMENT_PARAMETER_MISSING", Severity.ERROR,
                    "Mapping의 Fragment Parameter가 실제 th:fragment 선언에 없습니다.", parameter));
        }
        for (String parameter : additional) {
            issues.add(new ValidationIssue("FRAGMENT_PARAMETER_UNMAPPED", Severity.WARNING,
                    "실제 th:fragment Parameter가 Property Mapping에 연결되지 않았습니다.", parameter));
        }
        return new ValidationResult(mapping.mappingId(), mapping.version(),
                realProjectRoot.relativize(template).toString(), reference.fragmentName(),
                declaration.parameters(), missing, additional, issues);
    }

    public ValidationResult requireValid(Path projectRoot, DesignCodeComponentMapping mapping) {
        ValidationResult result = validate(projectRoot, mapping);
        if (!result.valid()) throw new FragmentContractValidationException(result);
        return result;
    }

    private FragmentReference parseReference(String value, List<ValidationIssue> issues) {
        if (value.contains("${") || value.contains("*{") || value.contains("#{")
                || value.contains("@{") || value.contains("~{")) {
            issues.add(new ValidationIssue("FRAGMENT_REFERENCE_DYNAMIC", Severity.ERROR,
                    "동적 Thymeleaf Fragment 참조는 정적 승인 Mapping에 사용할 수 없습니다.", value));
            return null;
        }
        Matcher matcher = REFERENCE.matcher(value.trim());
        if (!matcher.matches() || matcher.group(1).contains("..")) {
            issues.add(new ValidationIssue("FRAGMENT_REFERENCE_INVALID", Severity.ERROR,
                    "thymeleafFragment는 'templates 상대경로 :: fragmentName' 형식이어야 합니다.", value));
            return null;
        }
        return new FragmentReference(matcher.group(1) + ".html", matcher.group(2));
    }

    private List<Path> locateTemplates(
            Path projectRoot, String relative, List<ValidationIssue> issues) {
        List<Path> matches = new ArrayList<>();
        for (String rootName : TEMPLATE_ROOTS) {
            Path templateRoot = pathResolver.resolveTarget(projectRoot, rootName);
            Path candidate;
            try {
                candidate = pathResolver.resolveTarget(templateRoot, relative);
            } catch (SecurityException exception) {
                issues.add(new ValidationIssue("FRAGMENT_PATH_ESCAPE", Severity.ERROR,
                        "Fragment 경로가 Template Root를 벗어났습니다.", relative));
                return List.of();
            }
            if (Files.isRegularFile(candidate)) matches.add(candidate);
        }
        if (matches.isEmpty()) {
            issues.add(new ValidationIssue("FRAGMENT_FILE_NOT_FOUND", Severity.ERROR,
                    "Mapping이 참조하는 Fragment 파일을 찾을 수 없습니다.", relative));
        } else if (matches.size() > 1) {
            issues.add(new ValidationIssue("FRAGMENT_FILE_AMBIGUOUS", Severity.ERROR,
                    "Boot와 WAR Template Root에 같은 Fragment 파일이 모두 존재합니다.", relative));
        }
        return matches;
    }

    private String readTemplate(Path template, List<ValidationIssue> issues) {
        try {
            if (Files.size(template) > MAX_TEMPLATE_BYTES) {
                issues.add(new ValidationIssue("FRAGMENT_FILE_TOO_LARGE", Severity.ERROR,
                        "정적 검사 한도를 초과한 Fragment 파일입니다.", template.toString()));
                return null;
            }
            return Files.readString(template, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            issues.add(new ValidationIssue("FRAGMENT_FILE_READ_FAILED", Severity.ERROR,
                    "Fragment 파일을 읽을 수 없습니다.", template.toString()));
            return null;
        }
    }

    private List<FragmentDeclaration> declarations(
            String content, String expectedName, List<ValidationIssue> issues) {
        List<FragmentDeclaration> found = new ArrayList<>();
        Matcher attributes = FRAGMENT_ATTRIBUTE.matcher(content);
        while (attributes.find()) {
            String raw = attributes.group(2).trim();
            if (raw.contains("${") || raw.contains("*{") || raw.contains("#{") || raw.contains("@{")) {
                continue;
            }
            Matcher declaration = DECLARATION.matcher(raw);
            if (!declaration.matches() || !expectedName.equals(declaration.group(1))) continue;
            LinkedHashSet<String> parameters = parseParameters(declaration.group(2), issues);
            if (parameters != null) found.add(new FragmentDeclaration(expectedName, parameters));
        }
        if (found.isEmpty()) {
            issues.add(new ValidationIssue("FRAGMENT_DECLARATION_NOT_FOUND", Severity.ERROR,
                    "파일에 일치하는 정적 th:fragment 선언이 없습니다.", expectedName));
        } else if (found.size() > 1) {
            issues.add(new ValidationIssue("FRAGMENT_DECLARATION_AMBIGUOUS", Severity.ERROR,
                    "같은 이름의 th:fragment 선언이 두 개 이상입니다.", expectedName));
        }
        return found;
    }

    private LinkedHashSet<String> parseParameters(String raw, List<ValidationIssue> issues) {
        LinkedHashSet<String> parameters = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) return parameters;
        for (String part : raw.split(",", -1)) {
            String parameter = part.trim();
            int defaultSeparator = parameter.indexOf('=');
            if (defaultSeparator >= 0) parameter = parameter.substring(0, defaultSeparator).trim();
            if (!PARAMETER.matcher(parameter).matches() || !parameters.add(parameter)) {
                issues.add(new ValidationIssue("FRAGMENT_PARAMETER_INVALID", Severity.ERROR,
                        "Fragment Parameter 이름이 유효하지 않거나 중복됐습니다.", part.trim()));
                return null;
            }
        }
        return parameters;
    }

    private ValidationResult invalid(
            DesignCodeComponentMapping mapping, List<ValidationIssue> issues) {
        return new ValidationResult(mapping.mappingId(), mapping.version(), null, null,
                Set.of(), Set.of(), Set.of(), issues);
    }

    private record FragmentReference(String templatePath, String fragmentName) {}

    private record FragmentDeclaration(String name, Set<String> parameters) {}

    public record ValidationResult(
            String mappingId,
            String mappingVersion,
            String templatePath,
            String fragmentName,
            Set<String> declaredParameters,
            Set<String> missingParameters,
            Set<String> unmappedParameters,
            List<ValidationIssue> issues
    ) {
        public ValidationResult {
            declaredParameters = immutableSet(declaredParameters);
            missingParameters = immutableSet(missingParameters);
            unmappedParameters = immutableSet(unmappedParameters);
            issues = List.copyOf(issues);
        }

        public boolean valid() {
            return issues.stream().noneMatch(issue -> issue.severity() == Severity.ERROR);
        }

        private static <T> Set<T> immutableSet(Set<T> source) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(source));
        }
    }

    public record ValidationIssue(String code, Severity severity, String message, String target) {}

    public enum Severity { WARNING, ERROR }

    public static final class FragmentContractValidationException extends IllegalStateException {
        private final ValidationResult result;

        public FragmentContractValidationException(ValidationResult result) {
            super("Thymeleaf Fragment 계약 정적 검사에 실패했습니다: "
                    + result.mappingId() + "@" + result.mappingVersion());
            this.result = result;
        }

        public ValidationResult result() {
            return result;
        }
    }
}
