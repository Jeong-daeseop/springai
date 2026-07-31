package com.krdevops.springai.service.generation.pipeline.processor;

import com.krdevops.springai.service.GenerationHistoryService;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.pipeline.GenerationHistoryRecorder;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import com.krdevops.springai.service.generation.pipeline.HistoryRecordResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 기존 {@link GenerationHistoryService#saveHistory} 위임 — 이력 저장 실패는 비치명이라
 * 예외를 삼키고 요약 문자열로만 보고한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultGenerationHistoryRecorder implements GenerationHistoryRecorder {

    private final GenerationHistoryService generationHistoryService;

    @Override
    public HistoryRecordResult record(GenerationProcessingContext processingContext) {
        GenerationContext context = processingContext.context();
        int successCount = processingContext.execution().succeededFiles().size();
        try {
            return new HistoryRecordResult(generationHistoryService.saveHistory(
                    context.tableName(), context.domain(), context.packageName(),
                    context.outputPath(), successCount + "개 파일"));
        } catch (Exception e) {
            log.warn("[pipeline] 생성 이력 저장 실패: {}", e.getMessage());
            return new HistoryRecordResult("생성 이력 저장 실패: " + e.getMessage());
        }
    }
}
