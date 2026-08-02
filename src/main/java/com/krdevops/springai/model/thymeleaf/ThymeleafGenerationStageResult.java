package com.krdevops.springai.model.thymeleaf;

import com.krdevops.springai.model.contract.GenerationIssue;

import java.util.List;

/**
 * I-2~I-5 Pipeline의 각 단계(Source Reader, Binding Contract, Skeleton, Responsive, Apply)가
 * 공통으로 반환하는 결과 봉투. {@code successful=false}면 {@code value}는 항상 {@code null}이며
 * 호출자는 {@code issues}의 FATAL 여부로 후속 단계 실행 가능 여부를 판단한다.
 */
public record ThymeleafGenerationStageResult<T>(
        T value,
        List<GenerationIssue> issues,
        boolean successful
) {
    public ThymeleafGenerationStageResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
        if (successful && value == null) {
            throw new IllegalArgumentException("successful=true면 value는 null일 수 없습니다.");
        }
        if (!successful && value != null) {
            throw new IllegalArgumentException("successful=false면 value는 null이어야 합니다.");
        }
    }

    public static <T> ThymeleafGenerationStageResult<T> success(T value, List<GenerationIssue> issues) {
        return new ThymeleafGenerationStageResult<>(value, issues, true);
    }

    public static <T> ThymeleafGenerationStageResult<T> failure(List<GenerationIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            throw new IllegalArgumentException("failure는 최소 1개 이상의 issue가 필요합니다.");
        }
        return new ThymeleafGenerationStageResult<>(null, issues, false);
    }

    public boolean hasFatalIssue() {
        return issues.stream().anyMatch(issue -> issue.severity() == GenerationIssue.Severity.FATAL);
    }
}
