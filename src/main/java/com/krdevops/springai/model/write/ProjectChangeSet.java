package com.krdevops.springai.model.write;

import java.util.List;

/**
 * WP7/ARCH-0701: 일반 Generation과 Thymeleaf Apply가 공유하는 파일 변경 의미. {@code beforeHash}는
 * 승인 시점 기준 원본 hash — {@link ProjectWritePolicy#ATOMIC_APPROVED}에서 이 값이 현재 파일과
 * 다르면(drift) 아무 것도 쓰지 않는다. {@code afterHash}는 content로부터 호출자가 미리 계산해
 * 넣는다(이 record 자체는 해시 알고리즘에 의존하지 않는다).
 */
public record ProjectChangeSet(
        String projectRootRef,
        String baseRevision,
        List<FileChange> generatedFiles,
        List<FileDeletion> deletions,
        ProjectWritePolicy policy
) {
    public ProjectChangeSet {
        if (projectRootRef == null || projectRootRef.isBlank()) {
            throw new IllegalArgumentException("projectRootRef는 필수입니다.");
        }
        generatedFiles = generatedFiles == null ? List.of() : List.copyOf(generatedFiles);
        deletions = deletions == null ? List.of() : List.copyOf(deletions);
        policy = policy == null ? ProjectWritePolicy.ATOMIC_APPROVED : policy;
        if (generatedFiles.isEmpty() && deletions.isEmpty()) {
            throw new IllegalArgumentException("generatedFiles 또는 deletions 중 최소 1개는 있어야 합니다.");
        }
    }

    /** {@code path}는 projectRootRef 기준 상대경로. {@code beforeHash}가 없으면(신규 파일) "MISSING". */
    public record FileChange(String path, String beforeHash, String content, String afterHash) {
        public FileChange {
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("path는 필수입니다.");
            }
            content = content == null ? "" : content;
            beforeHash = beforeHash == null || beforeHash.isBlank() ? "MISSING" : beforeHash;
        }
    }

    /** 삭제 대상 파일. {@code beforeHash}가 현재 파일과 다르면(drift) 삭제하지 않는다. */
    public record FileDeletion(String path, String beforeHash) {
        public FileDeletion {
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("path는 필수입니다.");
            }
            beforeHash = beforeHash == null || beforeHash.isBlank() ? "MISSING" : beforeHash;
        }
    }
}
