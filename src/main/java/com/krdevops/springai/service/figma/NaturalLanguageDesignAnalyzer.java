package com.krdevops.springai.service.figma;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * R6-032: 자연어 화면 요청의 결정론적 1차 구조화 분석기.
 *
 * <p>LLM이 임의의 DB를 선택하지 않도록 명시적인 {@code database.table},
 * {@code database=...}, {@code tableName=...} 표기만 데이터 소스로 승격한다.
 * 표기가 없으면 후보 화면 유형·플랫폼만 반환하고 TABLE_BINDING_REQUIRED를 남긴다.
 */
@Service
public class NaturalLanguageDesignAnalyzer {
    private static final Pattern QUALIFIED_TABLE = Pattern.compile("(?i)([a-z][a-z0-9_$-]{0,63})\\.([a-z][a-z0-9_$-]{0,63})");
    private static final Pattern DATABASE = Pattern.compile("(?i)(?:database|schema|데이터베이스|스키마)\\s*[:=]\\s*([a-z][a-z0-9_$-]{0,63})");
    private static final Pattern TABLE = Pattern.compile("(?i)(?:tableName|table|테이블)\\s*[:=]\\s*([a-z][a-z0-9_$-]{0,63})");

    public Analysis analyze(String prompt, String platform) {
        if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("prompt는 필수입니다.");
        String text = prompt.trim();
        String database = null;
        String table = null;
        Matcher qualified = QUALIFIED_TABLE.matcher(text);
        if (qualified.find()) {
            database = qualified.group(1);
            table = qualified.group(2);
        } else {
            Matcher db = DATABASE.matcher(text);
            Matcher tb = TABLE.matcher(text);
            if (db.find()) database = db.group(1);
            if (tb.find()) table = tb.group(1);
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String screenType = containsAny(lower, "상세", "detail", "조회 화면") ? "DETAIL"
                : containsAny(lower, "등록", "수정", "form", "입력") ? "FORM" : "LIST";
        String resolvedPlatform = platform == null || platform.isBlank() ? "DESKTOP" : platform.trim().toUpperCase(Locale.ROOT);
        String issue = database == null || table == null ? "TABLE_BINDING_REQUIRED" : null;
        double confidence = issue == null ? 0.95 : (screenType.equals("LIST") ? 0.70 : 0.65);
        return new Analysis(database, table, screenType, resolvedPlatform, confidence, issue);
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) if (value.contains(candidate)) return true;
        return false;
    }

    public record Analysis(String database, String tableName, String screenType,
                           String platform, double confidence, String issueCode) {
        public boolean hasTableBinding() { return database != null && tableName != null; }
    }
}
