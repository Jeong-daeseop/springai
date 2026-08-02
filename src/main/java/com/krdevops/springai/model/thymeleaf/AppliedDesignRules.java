package com.krdevops.springai.model.thymeleaf;

import com.krdevops.springai.model.contract.GenerationIssue;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * R6-055: DESIGN.md 파일에서 파싱한 설계 규칙을 표현하는 도메인 모델.
 *
 * <p>프로젝트 루트의 DESIGN.md 파일에서 YAML frontmatter를 추출하고 파싱하여
 * 구조화된 설계 규칙을 생성한다. 파이프라인 후속 단계(R6-056 Token 매핑, R6-057 Skeleton 생성)에서
 * 이 규칙들을 실제 Thymeleaf 생성에 적용하기 위한 기초 데이터로 사용된다.
 */
public record AppliedDesignRules(
        @Nullable String designMdPath,
        @Nullable String contentHash,
        @Nullable String schemaVersion,
        List<AppliedRule> appliedRules,
        List<IgnoredRule> ignoredRules,
        List<GenerationIssue> violations
) {

    /**
     * 설계 규칙의 단일 항목. YAML frontmatter의 범주(category) 아래 key-value 쌍으로 표현된다.
     */
    public record AppliedRule(
            String category,
            String key,
            String value,
            String sourceLocation
    ) {}

    /**
     * 무시되는 규칙. 지원하지 않는 최상위 YAML 키 또는 구문 오류로 인해 파싱되지 않은 항목.
     */
    public record IgnoredRule(
            String rawKey,
            String reason,
            String sourceLocation
    ) {}

    /**
     * 이 AppliedDesignRules 인스턴스에 FATAL 수준의 위반이 있는지 확인.
     * FATAL은 파이프라인 진행 불가 상황(버전 미지원, 업무계약 침범, YAML 구문 오류 등)을 나타낸다.
     */
    public boolean hasFatalViolation() {
        return violations.stream()
                .anyMatch(issue -> issue.severity() == GenerationIssue.Severity.FATAL);
    }
}
