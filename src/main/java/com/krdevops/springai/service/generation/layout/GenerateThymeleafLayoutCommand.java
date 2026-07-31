package com.krdevops.springai.service.generation.layout;

import java.nio.file.Path;

public record GenerateThymeleafLayoutCommand(
        Path outputPath,
        String layoutBasePath,
        boolean overwrite,
        String packageName,
        String menuTableName,
        String programTableName) {
}
