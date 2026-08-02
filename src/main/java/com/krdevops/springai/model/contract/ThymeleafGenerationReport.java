package com.krdevops.springai.model.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * I-5: Thymeleaf 생성 보고서.
 * JSP → Thymeleaf 전환의 단계별 결과, 기능 일치도, 적용 파일을 기록합니다.
 */
public record ThymeleafGenerationReport(
        @JsonProperty("conversionId")
        String conversionId,

        @JsonProperty("operationId")
        String operationId,  // nullable: Figma ScreenSpecification 결합 시만 설정

        @JsonProperty("stage")
        String stage,  // "ANALYZED" | "CONTRACT_READY" | "PREVIEW_READY" | "APPROVED" | "APPLIED" | "VALIDATED"

        @JsonProperty("results")
        List<StageResult> results,

        @JsonProperty("parity")
        ParityCheckResult parity,

        @JsonProperty("appliedFiles")
        List<AppliedFile> appliedFiles,  // APPLIED 이후 채워짐

        @JsonProperty("generatedAt")
        String generatedAt,  // ISO 8601 instant

        @JsonProperty("duration")
        long duration  // milliseconds
) {
    /**
     * 단축 생성자: 필수값 검증
     */
    public ThymeleafGenerationReport {
        if (conversionId == null || !conversionId.matches("^thymeleaf-conv-[a-f0-9\\-]+$")) {
            throw new IllegalArgumentException("conversionId 형식이 잘못되었습니다");
        }
        if (!stage.matches("ANALYZED|CONTRACT_READY|PREVIEW_READY|APPROVED|APPLIED|VALIDATED")) {
            throw new IllegalArgumentException("stage가 유효하지 않습니다");
        }
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("results는 최소 1개 이상 필수입니다");
        }
        if (parity == null) {
            throw new IllegalArgumentException("parity는 필수입니다");
        }
        if (generatedAt == null || generatedAt.isBlank()) {
            throw new IllegalArgumentException("generatedAt은 필수입니다 (ISO 8601)");
        }
        if (duration < 0 || duration > 3_600_000L) {
            throw new IllegalArgumentException("duration은 0~3600000ms 범위여야 합니다");
        }
    }

    /**
     * Figma ScreenSpecification 결합 여부
     */
    public boolean hasFigmaReference() {
        return operationId != null && !operationId.isBlank();
    }

    /**
     * 모든 parity 검증 통과 여부
     */
    public boolean isParityComplete() {
        return parity.routeMatched() && parity.fieldBindingMatched() && parity.validationMatched() &&
               parity.csrfMatched() && parity.authorityMatched();
    }

    /**
     * 모든 stage 성공 여부
     */
    public boolean isAllStagesSuccess() {
        return results.stream().allMatch(r -> "SUCCESS".equals(r.status()));
    }

    /**
     * Stage별 결과
     */
    public record StageResult(
            @JsonProperty("stageName")
            String stageName,  // "ANALYZE" | "RENDER" | "BUILD" | "VALIDATE"

            @JsonProperty("status")
            String status,  // "SUCCESS" | "FAILED" | "SKIPPED" | "PARTIAL"

            @JsonProperty("inputHash")
            String inputHash,  // nullable, "sha256:..."

            @JsonProperty("outputHash")
            String outputHash,  // nullable, "sha256:..."

            @JsonProperty("duration")
            long duration,  // milliseconds

            @JsonProperty("issues")
            List<GenerationIssue> issues,  // empty if success

            @JsonProperty("details")
            Map<String, Object> details  // stage-specific info
    ) {
        /**
         * Stage 결과 검증
         */
        public StageResult {
            if (stageName == null || !stageName.matches("ANALYZE|RENDER|BUILD|VALIDATE")) {
                throw new IllegalArgumentException("stageName이 유효하지 않습니다");
            }
            if (status == null || !status.matches("SUCCESS|FAILED|SKIPPED|PARTIAL")) {
                throw new IllegalArgumentException("status가 유효하지 않습니다");
            }
            if (duration < 0) {
                throw new IllegalArgumentException("duration은 음수가 될 수 없습니다");
            }
            if (issues == null) {
                throw new IllegalArgumentException("issues는 필수입니다 (빈 리스트 가능)");
            }
            if (details == null) {
                throw new IllegalArgumentException("details는 필수입니다 (빈 맵 가능)");
            }
        }

        /**
         * 성공 여부
         */
        public boolean isSuccess() {
            return "SUCCESS".equals(status);
        }

        /**
         * 해시 완정성: 입출력 모두 있어야 함
         */
        public boolean hasCompleteHash() {
            return inputHash != null && outputHash != null &&
                   inputHash.matches("^sha256:[a-f0-9]{64}$") &&
                   outputHash.matches("^sha256:[a-f0-9]{64}$");
        }
    }

    /**
     * JSP vs Thymeleaf 기능 일치도 검증
     */
    public record ParityCheckResult(
            @JsonProperty("routeMatched")
            boolean routeMatched,

            @JsonProperty("fieldBindingMatched")
            boolean fieldBindingMatched,

            @JsonProperty("validationMatched")
            boolean validationMatched,

            @JsonProperty("csrfMatched")
            boolean csrfMatched,

            @JsonProperty("authorityMatched")
            boolean authorityMatched,

            @JsonProperty("issues")
            List<GenerationIssue> issues  // parity 불일치 상세
    ) {
        /**
         * Parity 검증 생성자
         */
        public ParityCheckResult {
            if (issues == null) {
                throw new IllegalArgumentException("issues는 필수입니다 (빈 리스트 가능)");
            }
        }

        /**
         * 모든 parity 검증 통과 여부
         */
        public boolean isComplete() {
            return routeMatched && fieldBindingMatched && validationMatched &&
                   csrfMatched && authorityMatched;
        }

        /**
         * 불일치 개수
         */
        public int getMismatchCount() {
            int count = 0;
            if (!routeMatched) count++;
            if (!fieldBindingMatched) count++;
            if (!validationMatched) count++;
            if (!csrfMatched) count++;
            if (!authorityMatched) count++;
            return count;
        }
    }

    /**
     * 프로젝트에 적용된 파일
     */
    public record AppliedFile(
            @JsonProperty("path")
            String path,  // relative path (src/main/webapp/...)

            @JsonProperty("action")
            String action,  // "CREATED" | "REPLACED" | "BACKUP" | "ROLLBACK"

            @JsonProperty("hash")
            String hash,  // "sha256:...", 적용 후 파일의 해시

            @JsonProperty("previousHash")
            String previousHash,  // nullable, "sha256:...", 교체 전 원본

            @JsonProperty("size")
            long size  // bytes
    ) {
        /**
         * 적용 파일 검증
         */
        public AppliedFile {
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("path는 필수입니다");
            }
            if (!action.matches("CREATED|REPLACED|BACKUP|ROLLBACK")) {
                throw new IllegalArgumentException("action이 유효하지 않습니다");
            }
            if (!hash.matches("^sha256:[a-f0-9]{64}$")) {
                throw new IllegalArgumentException("hash 형식이 잘못되었습니다");
            }
            if (size < 0) {
                throw new IllegalArgumentException("size는 음수가 될 수 없습니다");
            }
            if ("REPLACED".equals(action) && (previousHash == null || previousHash.isBlank())) {
                throw new IllegalArgumentException("REPLACED 액션은 previousHash가 필수입니다");
            }
        }

        /**
         * 생성 여부
         */
        public boolean isCreated() {
            return "CREATED".equals(action);
        }

        /**
         * 교체 여부
         */
        public boolean isReplaced() {
            return "REPLACED".equals(action);
        }

        /**
         * 교체 추적 가능 여부 (previousHash 있음)
         */
        public boolean isTrackable() {
            return previousHash != null && !previousHash.isBlank();
        }
    }
}
