package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.artifact.ContentHashes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;

/** CRUD 생성 화면 하나(같은 outputPath·tableName·viewType 조합)를 식별하는 결정적 operationId. */
public final class CrudGenerationOperationIdFactory {

    private CrudGenerationOperationIdFactory() {
    }

    public static String forScreen(String outputPath, String tableName, String viewType) {
        if (outputPath == null || tableName == null || viewType == null) {
            throw new IllegalArgumentException("outputPath·tableName·viewType은 모두 필수입니다.");
        }
        String canonicalOutputPath = Path.of(outputPath).toAbsolutePath().normalize().toString();
        String canonical = canonicalOutputPath + "|" + tableName.trim().toUpperCase(Locale.ROOT)
                + "|" + viewType.trim().toLowerCase(Locale.ROOT);
        return ContentHashes.sha256Hex(canonical.getBytes(StandardCharsets.UTF_8));
    }
}
