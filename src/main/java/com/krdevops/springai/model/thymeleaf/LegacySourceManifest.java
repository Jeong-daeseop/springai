package com.krdevops.springai.model.thymeleaf;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * WP6 legacy 생성이 근거로 사용한 JSP·Controller·VO 파일의 불변 manifest.
 * 경로는 Operation의 project root 기준 상대경로만 저장하며, Apply 직전에 같은 안전 읽기 경계로
 * 다시 조회해 SHA-256 drift를 판정한다.
 */
public record LegacySourceManifest(
        List<SourceFile> files,
        String fingerprint
) {
    public LegacySourceManifest {
        files = files == null ? List.of() : List.copyOf(files);
        Set<String> paths = new HashSet<>();
        for (SourceFile file : files) {
            if (!paths.add(file.relativePath())) {
                throw new IllegalArgumentException("legacy source 경로가 중복됩니다: " + file.relativePath());
            }
        }
        if (!files.isEmpty() && (fingerprint == null || !fingerprint.matches("[a-f0-9]{64}"))) {
            throw new IllegalArgumentException("legacy source fingerprint는 SHA-256이어야 합니다.");
        }
        if (files.isEmpty()) {
            fingerprint = null;
        }
    }

    public static LegacySourceManifest empty() {
        return new LegacySourceManifest(List.of(), null);
    }

    public boolean tracked() {
        return !files.isEmpty();
    }

    public record SourceFile(String relativePath, String sha256Hex) {
        public SourceFile {
            if (relativePath == null || relativePath.isBlank()) {
                throw new IllegalArgumentException("legacy source relativePath는 필수입니다.");
            }
            if (sha256Hex == null || !sha256Hex.matches("[a-f0-9]{64}")) {
                throw new IllegalArgumentException("legacy source sha256Hex는 SHA-256이어야 합니다: "
                        + relativePath);
            }
        }
    }
}
