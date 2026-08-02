package com.krdevops.springai.model.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * I-2: 로컬 소스 파일 메타정보 (JSP 프로젝트의 요청 시점 스냅샷).
 *
 * 프로젝트 경로, 소스 해시, 보안 정책 검증 이유, 소스 revision을 기록합니다.
 * Apply 직전 원본 backup 해시로 복구 추적을 보장합니다.
 */
public record LocalSourceDesignDocument(
        @JsonProperty("screenId")
        String screenId,

        @JsonProperty("sourceType")
        String sourceType,  // "LOCAL_SOURCE" or "HYBRID"

        @JsonProperty("projectPath")
        String projectPath,

        @JsonProperty("filePath")
        String filePath,  // nullable

        @JsonProperty("fileSize")
        long fileSize,

        @JsonProperty("sourceHash")
        String sourceHash,  // "sha256:..."

        @JsonProperty("projectFingerprint")
        String projectFingerprint,  // nullable

        @JsonProperty("allowedPaths")
        java.util.List<String> allowedPaths,

        @JsonProperty("excludedReasons")
        java.util.List<String> excludedReasons,

        @JsonProperty("readAt")
        String readAt,  // ISO 8601 instant

        @JsonProperty("sourceRevision")
        SourceRevisionRef sourceRevision,  // nullable

        @JsonProperty("backupPath")
        String backupPath,  // nullable

        @JsonProperty("backupHash")
        String backupHash  // nullable, "sha256:..."
) {
    /**
     * 단축 생성자: 필수값 검증
     */
    public LocalSourceDesignDocument {
        if (screenId == null || screenId.isBlank()) {
            throw new IllegalArgumentException("screenId는 필수입니다");
        }
        if (!sourceType.matches("LOCAL_SOURCE|HYBRID")) {
            throw new IllegalArgumentException("sourceType은 LOCAL_SOURCE 또는 HYBRID여야 합니다");
        }
        if (projectPath == null || projectPath.isBlank()) {
            throw new IllegalArgumentException("projectPath는 필수입니다");
        }
        if (fileSize < 0 || fileSize > 52_428_800L) {
            throw new IllegalArgumentException("fileSize는 0~50MB 범위여야 합니다");
        }
        if (!sourceHash.matches("^sha256:[a-f0-9]{64}$")) {
            throw new IllegalArgumentException("sourceHash 형식이 잘못되었습니다 (sha256:...)");
        }
        if (allowedPaths == null || allowedPaths.isEmpty()) {
            throw new IllegalArgumentException("allowedPaths는 최소 1개 이상 필수입니다");
        }
        if (readAt == null || readAt.isBlank()) {
            throw new IllegalArgumentException("readAt은 필수입니다 (ISO 8601)");
        }
        if (backupHash != null && !backupHash.matches("^sha256:[a-f0-9]{64}$")) {
            throw new IllegalArgumentException("backupHash 형식이 잘못되었습니다");
        }
    }

    /**
     * 프로젝트 지문 유효성: 존재 여부
     */
    public boolean hasProjectFingerprint() {
        return projectFingerprint != null && projectFingerprint.matches("^sha256:[a-f0-9]{64}$");
    }

    /**
     * Backup 정보 완정성: 경로와 해시 모두 있어야 함
     */
    public boolean hasCompleteBackup() {
        return backupPath != null && !backupPath.isBlank() &&
               backupHash != null && !backupHash.isBlank();
    }
}
