package com.krdevops.springai.policy;

import com.krdevops.springai.model.crud.FieldModel;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 목록/상세 화면에 노출하면 안 되는 인증·식별 관련 필드 정책. */
public final class SensitiveFieldPolicy {

    private static final Set<String> SENSITIVE_TOKENS = Set.of(
            "PASSWORD", "PWD", "IHID", "ESNTL", "CERT", "LOCK", "UNIQ",
            "SECRET", "DN", "HASH", "KEY");

    /* 구분자가 없는 eGovFrame 레거시 컬럼명을 토큰 정책과 함께 보호한다. */
    private static final Set<String> SENSITIVE_EXACT_NAMES = Set.of(
            "IHIDNUM", "CERTDN", "USERDN", "UNIQID", "PASSWORD", "SECRET");

    private SensitiveFieldPolicy() {
    }

    public static boolean isSensitiveDisplayField(String javaName, String columnName) {
        Set<String> names = Stream.of(javaName, columnName)
                .map(SensitiveFieldPolicy::normalize)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
        if (names.stream().anyMatch(SENSITIVE_EXACT_NAMES::contains)) return true;
        return tokens(javaName, columnName).stream().anyMatch(SENSITIVE_TOKENS::contains);
    }

    public static boolean isSensitiveDisplayField(FieldModel field) {
        return field != null && isSensitiveDisplayField(field.javaName(), field.columnName());
    }

    public static List<FieldModel> filterDisplayFields(List<FieldModel> fields) {
        if (fields == null) return List.of();
        return fields.stream().filter(field -> !isSensitiveDisplayField(field)).toList();
    }

    private static Set<String> tokens(String... names) {
        return Arrays.stream(names)
                .filter(name -> name != null && !name.isBlank())
                .flatMap(name -> {
                    String snake = name.replaceAll("([a-z0-9])([A-Z])", "$1_$2");
                    return Arrays.stream(snake.toUpperCase(Locale.ROOT).split("[^A-Z0-9]+"));
                })
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }
}
