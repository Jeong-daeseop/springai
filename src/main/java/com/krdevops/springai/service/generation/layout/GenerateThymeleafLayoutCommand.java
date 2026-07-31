package com.krdevops.springai.service.generation.layout;

import java.nio.file.Path;

/**
 * {@code packageNameMissing}은 원본 {@code packageName} 인자가 null/blank였는지를 별도로 보존한다 —
 * {@code packageName}은 이미 기본값이 적용된 상태이므로, 경고 문구 재현에는 원본 판정 결과가 필요하다.
 */
public record GenerateThymeleafLayoutCommand(
        Path outputPath,
        String layoutBasePath,
        boolean overwrite,
        String packageName,
        boolean packageNameMissing,
        String menuTableName,
        String programTableName) {
}
