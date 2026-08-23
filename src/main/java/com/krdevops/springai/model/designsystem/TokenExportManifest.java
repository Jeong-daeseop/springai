package com.krdevops.springai.model.designsystem;

import com.krdevops.springai.model.artifact.ContentHashes;
import java.nio.charset.StandardCharsets;
import java.util.List;

public record TokenExportManifest(String manifestId, String contentHash, String outputPath, List<TokenEntry> tokens) {
    public TokenExportManifest {
        if (manifestId == null || manifestId.isBlank() || outputPath == null || outputPath.isBlank()) throw new IllegalArgumentException("Token Export 필수값이 누락되었습니다.");
        contentHash = ContentHashes.requireValid(contentHash); tokens = List.copyOf(tokens == null ? List.of() : tokens);
    }
    public boolean hasValidContentHash() { return contentHash.equals(ContentHashes.sha256Hex((manifestId+"|"+outputPath+"|"+tokens).getBytes(StandardCharsets.UTF_8))); }
    public record TokenEntry(String name, String value, String outputVariable, String inputHash, String outputHash) {
        public TokenEntry { if (name == null || name.isBlank() || value == null || outputVariable == null || outputVariable.isBlank()) throw new IllegalArgumentException("Token 값이 올바르지 않습니다."); }
    }
}
