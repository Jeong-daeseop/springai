package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.figma.contract.FigmaDesignOperation;
import com.krdevops.springai.model.figma.contract.FigmaDesignOperationStatus;
import com.krdevops.springai.model.figma.contract.FigmaDesignRequest;
import com.krdevops.springai.model.figma.contract.FigmaDesignRequestType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Figma 디자인 요청 오케스트레이션 (2-A5).
 * 분석 → 검증 → Spec 생성 → Bundle 조립 → Operation 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FigmaDesignOrchestrationService {

    private final FigmaDesignRequestRouter router;
    private final FigmaContextAnalyzer contextAnalyzer;
    private final FigmaStyleExtractor styleExtractor;
    private final FigmaFileAllowlistValidator allowlistValidator;

    /**
     * 텍스트 요청 처리 (CREATE_FROM_TEXT).
     */
    public FigmaDesignOperation processTextRequest(String prompt, String fileKey) {
        try {
            if (prompt == null || prompt.isBlank()) {
                throw new IllegalArgumentException("prompt는 필수입니다");
            }
            if (fileKey == null || fileKey.isBlank()) {
                throw new IllegalArgumentException("fileKey는 필수입니다");
            }

            // Allowlist 검증
            allowlistValidator.validateFileKey(fileKey);

            // LLM 분석
            var analysis = contextAnalyzer.analyze(prompt, null);

            // 요청 생성
            FigmaDesignRequest request = new FigmaDesignRequest(
                    FigmaDesignRequestType.TEXT_DESCRIPTION,
                    prompt,
                    fileKey,
                    null, null, null, null, null, null
            );

            // 상태 결정
            var status = analysis.requiresReview() ?
                    FigmaDesignOperationStatus.REJECTED :
                    FigmaDesignOperationStatus.PREVIEW_READY;

            // Issues 생성
            List<GenerationIssue> issues = new ArrayList<>();
            if (analysis.requiresReview()) {
                issues.add(new GenerationIssue(
                        "LOW_CONFIDENCE",
                        GenerationIssue.Severity.WARNING,
                        "CONTEXT_ANALYSIS",
                        null,
                        "LLM 분석 신뢰도 낮음: " + String.format("%.1f%%", analysis.uncertainty() * 100),
                        null
                ));
            }

            // Operation 생성
            return new FigmaDesignOperation(
                    generateOperationId(),
                    0,  // revision
                    request,
                    "sha256:" + UUID.randomUUID().toString().replace("-", "").substring(0, 64),
                    status,
                    null,  // sourceRevision
                    issues,
                    null,  // artifacts
                    Instant.now(),
                    Instant.now()
            );

        } catch (Exception e) {
            log.error("TEXT 요청 처리 실패", e);
            throw new RuntimeException("요청 처리 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 명시적 요청 처리 (모든 타입).
     */
    public FigmaDesignOperation processExplicitRequest(FigmaDesignRequest request) {
        try {
            if (request == null || request.prompt() == null || request.prompt().isBlank()) {
                throw new IllegalArgumentException("prompt는 필수입니다");
            }

            // Allowlist 검증
            allowlistValidator.validateFileKey(request.fileKey());

            // LLM 분석
            var analysis = contextAnalyzer.analyze(request.prompt(), null);

            // 상태 결정
            var status = analysis.requiresReview() ?
                    FigmaDesignOperationStatus.REJECTED :
                    FigmaDesignOperationStatus.PREVIEW_READY;

            // Issues 생성
            List<GenerationIssue> issues = new ArrayList<>();
            if (analysis.requiresReview()) {
                issues.add(new GenerationIssue(
                        "LOW_CONFIDENCE",
                        GenerationIssue.Severity.WARNING,
                        "CONTEXT_ANALYSIS",
                        null,
                        "LLM 분석 신뢰도 낮음: " + String.format("%.1f%%", analysis.uncertainty() * 100),
                        null
                ));
            }

            // Operation 생성
            return new FigmaDesignOperation(
                    generateOperationId(),
                    0,  // revision
                    request,
                    "sha256:" + UUID.randomUUID().toString().replace("-", "").substring(0, 64),
                    status,
                    null,  // sourceRevision
                    issues,
                    null,  // artifacts
                    Instant.now(),
                    Instant.now()
            );

        } catch (Exception e) {
            log.error("EXPLICIT 요청 처리 실패", e);
            throw new RuntimeException("요청 처리 실패: " + e.getMessage(), e);
        }
    }

    private String generateOperationId() {
        return "figma-op-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
