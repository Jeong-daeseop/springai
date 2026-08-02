package com.krdevops.springai.model.thymeleaf;

import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.contract.SourceRevisionRef;

import java.time.Instant;
import java.util.List;

/**
 * I-2C 세 Reader(JSP·Controller·VO)의 결과를 화면 하나로 모은 중간 산출물.
 * {@link com.krdevops.springai.service.thymeleaf.LegacyBindingContractAssembler}가 이를 입력 삼아
 * {@link ThymeleafBindingContract}를 만든다.
 */
public record LegacyScreenAnalysis(
        String screenId,
        LegacyScreenRole screenRole,
        JspEvidence jsp,
        ControllerEvidence controller,
        VoEvidence vo,
        SourceRevisionRef sourceRevision,
        List<GenerationIssue> issues,
        Instant analyzedAt
) {
    public LegacyScreenAnalysis {
        if (screenId == null || screenId.isBlank()) {
            throw new IllegalArgumentException("screenId는 필수입니다.");
        }
        if (screenRole == null) {
            throw new IllegalArgumentException("screenRole은 필수입니다.");
        }
        if (jsp == null) {
            throw new IllegalArgumentException("jsp는 필수입니다.");
        }
        if (controller == null) {
            throw new IllegalArgumentException("controller는 필수입니다.");
        }
        if (vo == null) {
            throw new IllegalArgumentException("vo는 필수입니다.");
        }
        issues = issues == null ? List.of() : List.copyOf(issues);
        analyzedAt = analyzedAt == null ? Instant.now() : analyzedAt;
    }
}
