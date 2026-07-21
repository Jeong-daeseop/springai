package com.krdevops.springai.service;

import com.krdevops.springai.model.design.FieldSourceType;
import com.krdevops.springai.model.design.ScreenFieldBinding;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.SpecIssue;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ScreenSpecValidator {

    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");
    private static final Pattern SAFE_JOIN = Pattern.compile(
            "[A-Za-z][A-Za-z0-9_]*\\.[A-Za-z][A-Za-z0-9_]*\\s*=\\s*"
                    + "[A-Za-z][A-Za-z0-9_]*\\.[A-Za-z][A-Za-z0-9_]*");

    public ScreenSpecification validate(ScreenSpecification specification) {
        List<SpecIssue> issues = new ArrayList<>(specification.issues());
        if (blank(specification.database()) || blank(specification.primaryTable())) {
            issues.add(error("PRIMARY_DATA_SOURCE_REQUIRED", "주 데이터베이스와 테이블이 필요합니다.", null));
        }
        if (specification.pages().isEmpty()) {
            issues.add(error("PAGE_REQUIRED", "목록·상세·폼 중 하나 이상의 페이지가 필요합니다.", null));
        }

        Set<String> seenAliases = new HashSet<>();
        specification.dataSources().forEach(source -> {
            if (!identifier(source.schema()) || !identifier(source.table()) || !identifier(source.alias())) {
                issues.add(error("INVALID_DATA_SOURCE_IDENTIFIER",
                        "데이터 소스 schema/table/alias는 안전한 SQL 식별자여야 합니다: " + source, null));
            } else if (!seenAliases.add(source.alias())) {
                issues.add(error("DUPLICATE_DATA_SOURCE_ALIAS",
                        "데이터 소스 alias가 중복됩니다: " + source.alias(), null));
            }
            if (!source.primary() && (blank(source.joinType())
                    || !("LEFT".equalsIgnoreCase(source.joinType())
                    || "INNER".equalsIgnoreCase(source.joinType())))) {
                issues.add(error("INVALID_JOIN_TYPE", "JOIN 유형은 LEFT 또는 INNER만 허용합니다.", null));
            }
            if (!source.primary() && (blank(source.joinExpression())
                    || !SAFE_JOIN.matcher(source.joinExpression()).matches())) {
                issues.add(error("UNSAFE_JOIN_EXPRESSION",
                        "JOIN 식은 alias.column = alias.column 형식만 허용합니다.", null));
            }
        });

        specification.pages().stream()
                .flatMap(page -> page.fields().stream())
                .filter(this::isUnmapped)
                .forEach(field -> issues.add(error(
                        "FIELD_UNMAPPED", "화면 필드의 데이터 출처가 확정되지 않았습니다: " + field.label(), field.id())));

        specification.pages().stream()
                .flatMap(page -> page.fields().stream())
                .filter(field -> !identifier(field.id()))
                .forEach(field -> issues.add(error(
                        "INVALID_FIELD_IDENTIFIER", "필드 ID는 안전한 Java/SQL 식별자여야 합니다: " + field.id(), field.id())));

        specification.pages().stream()
                .flatMap(page -> page.fields().stream())
                .filter(field -> field.source() != null)
                .filter(field -> (field.source().tableAlias() != null && !identifier(field.source().tableAlias()))
                        || (field.source().column() != null && !identifier(field.source().column())))
                .forEach(field -> issues.add(error(
                        "INVALID_FIELD_SOURCE_IDENTIFIER",
                        "필드 출처 alias/column은 안전한 SQL 식별자여야 합니다: " + field.source(), field.id())));

        specification.pages().stream()
                .flatMap(page -> page.fields().stream())
                .filter(field -> field.source() != null)
                .filter(field -> field.source().type() == FieldSourceType.DERIVED)
                .filter(field -> blank(field.source().expression())
                        || !"PAGE_ROW_NUMBER".equals(field.source().expression()))
                .forEach(field -> issues.add(error(
                        "UNSUPPORTED_DERIVED_EXPRESSION",
                        "현재 허용된 파생 식은 PAGE_ROW_NUMBER뿐입니다: " + field.label(), field.id())));

        specification.pages().stream()
                .flatMap(page -> page.fields().stream())
                .filter(field -> field.source() != null)
                .filter(field -> field.source().type() == FieldSourceType.COMMON_CODE)
                .filter(field -> blank(field.source().codeGroup()) || !identifier(field.source().codeGroup()))
                .forEach(field -> issues.add(error(
                        "COMMON_CODE_GROUP_REQUIRED", "공통코드 CODE_ID가 필요합니다: " + field.label(), field.id())));

        List<String> aliases = specification.dataSources().stream()
                .map(source -> source.alias() == null ? "" : source.alias())
                .toList();
        specification.pages().stream()
                .flatMap(page -> page.fields().stream())
                .filter(field -> field.source() != null)
                .filter(field -> field.source().type() == FieldSourceType.JOIN_COLUMN)
                .filter(field -> blank(field.source().tableAlias())
                        || !aliases.contains(field.source().tableAlias()))
                .forEach(field -> issues.add(error(
                        "JOIN_DATA_SOURCE_REQUIRED", "JOIN 필드의 데이터 소스가 없습니다: " + field.label(), field.id())));

        boolean hasBlockingIssue = issues.stream()
                .anyMatch(issue -> issue.severity() == SpecIssue.Severity.ERROR
                        || issue.severity() == SpecIssue.Severity.WARNING);
        ScreenSpecStatus status = hasBlockingIssue
                ? ScreenSpecStatus.REVIEW_REQUIRED : ScreenSpecStatus.APPROVED;
        return specification.withValidation(status, distinct(issues));
    }

    public ScreenSpecification approve(ScreenSpecification specification) {
        ScreenSpecification validated = validate(specification);
        if (validated.status() == ScreenSpecStatus.REVIEW_REQUIRED) {
            throw new IllegalStateException("미해결 화면명세 이슈가 있어 승인할 수 없습니다: " + validated.issues());
        }
        return validated.withStatus(ScreenSpecStatus.APPROVED);
    }

    private boolean isUnmapped(ScreenFieldBinding field) {
        return field.source() == null || field.source().type() == FieldSourceType.UNMAPPED;
    }

    private List<SpecIssue> distinct(List<SpecIssue> issues) {
        return issues.stream().distinct().toList();
    }

    private SpecIssue error(String code, String message, String fieldId) {
        return new SpecIssue(code, SpecIssue.Severity.ERROR, message, fieldId);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private boolean identifier(String value) {
        return value != null && SQL_IDENTIFIER.matcher(value).matches();
    }
}
