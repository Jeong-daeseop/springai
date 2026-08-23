package com.krdevops.springai.model.renderer;

import com.krdevops.springai.model.artifact.ContentHashes;

import java.util.List;

/** 정렬된 Template Manifest와 파일별 Hash로 재현 가능한 Template Set 지문. */
public record TemplateSetFingerprint(
        String templateSetVersion,
        String templateSetHash,
        List<TemplateEntry> templates
) {
    public TemplateSetFingerprint {
        if (templateSetVersion == null || templateSetVersion.isBlank()) {
            throw new IllegalArgumentException("templateSetVersion은 필수입니다.");
        }
        templateSetHash = ContentHashes.requireValid(templateSetHash);
        templates = templates == null ? List.of() : List.copyOf(templates);
        if (templates.isEmpty()) throw new IllegalArgumentException("templates는 하나 이상이어야 합니다.");
    }

    public record TemplateEntry(String relativePath, long sizeBytes, String contentHash) {
        public TemplateEntry {
            if (relativePath == null || relativePath.isBlank() || relativePath.startsWith("/")
                    || relativePath.contains("..")) {
                throw new IllegalArgumentException("Template 상대 경로가 올바르지 않습니다.");
            }
            if (sizeBytes < 0) throw new IllegalArgumentException("sizeBytes는 0 이상이어야 합니다.");
            contentHash = ContentHashes.requireValid(contentHash);
        }
    }
}
