package com.krdevops.springai.model.thymeleaf;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * I-6: 여러 JSP 화면을 일괄 변환하기 위한 배치 요청.
 * 프로젝트 루트에서 JSP를 자동 발견하고 Thymeleaf로 변환한다.
 */
public record BatchConversionRequest(
        String batchId,
        Path projectRoot,
        String jspPattern,
        String outputBaseDirectory,
        boolean parallelExecution,
        int maxConcurrency,
        List<String> excludePatterns,
        Instant createdAt
) {
    public BatchConversionRequest {
        if (projectRoot == null || projectRoot.toString().isBlank()) {
            throw new IllegalArgumentException("projectRoot는 필수입니다");
        }
        if (jspPattern == null || jspPattern.isBlank()) {
            throw new IllegalArgumentException("jspPattern은 필수입니다");
        }
        if (outputBaseDirectory == null || outputBaseDirectory.isBlank()) {
            throw new IllegalArgumentException("outputBaseDirectory는 필수입니다");
        }
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException("maxConcurrency는 최소 1 이상이어야 합니다");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String batchId;
        private Path projectRoot;
        private String jspPattern = "**/*.jsp";
        private String outputBaseDirectory;
        private boolean parallelExecution = false;
        private int maxConcurrency = 4;
        private List<String> excludePatterns = List.of();
        private Instant createdAt = Instant.now();

        public Builder batchId(String batchId) {
            this.batchId = batchId;
            return this;
        }

        public Builder projectRoot(Path projectRoot) {
            this.projectRoot = projectRoot;
            return this;
        }

        public Builder jspPattern(String jspPattern) {
            this.jspPattern = jspPattern;
            return this;
        }

        public Builder outputBaseDirectory(String outputBaseDirectory) {
            this.outputBaseDirectory = outputBaseDirectory;
            return this;
        }

        public Builder parallelExecution(boolean parallelExecution) {
            this.parallelExecution = parallelExecution;
            return this;
        }

        public Builder maxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
            return this;
        }

        public Builder excludePatterns(List<String> excludePatterns) {
            this.excludePatterns = excludePatterns;
            return this;
        }

        public BatchConversionRequest build() {
            if (batchId == null) {
                batchId = "batch-" + System.currentTimeMillis();
            }
            return new BatchConversionRequest(
                    batchId, projectRoot, jspPattern, outputBaseDirectory,
                    parallelExecution, maxConcurrency, excludePatterns, createdAt);
        }
    }
}
