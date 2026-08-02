package com.krdevops.springai.model.thymeleaf;

import java.time.Instant;
import java.util.List;

/**
 * I-6: 배치 변환 결과 집계.
 * 전체 성공/실패/스킵 통계 및 개별 항목별 상세 정보.
 */
public record BatchConversionResult(
        String batchId,
        int totalScanned,
        int successfulConversions,
        int failedConversions,
        int skippedConversions,
        List<ConversionItemResult> itemResults,
        Instant startedAt,
        Instant completedAt
) {
    public int successRate() {
        int processedCount = successfulConversions + failedConversions;
        if (processedCount == 0) return 0;
        return (successfulConversions * 100) / processedCount;
    }

    public boolean allSuccessful() {
        return failedConversions == 0 && totalScanned == successfulConversions;
    }

    public record ConversionItemResult(
            String jspRelativePath,
            String status,
            String targetRelativePath,
            String errorMessage,
            long durationMillis
    ) {
        public enum Status {
            SUCCESS, FAILED, SKIPPED
        }

        public boolean isSuccess() {
            return Status.SUCCESS.name().equals(status);
        }
    }
}
