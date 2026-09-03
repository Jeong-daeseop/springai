package com.krdevops.springai.service;

import java.nio.file.Files;
import java.nio.file.Path;

/** KRDS 원본 CSS/JavaScript 자산이 WAR 또는 Spring Boot 배치에 완비됐는지 판정한다. */
public final class KrdsAssetVerifier {

    private static final String[] WAR_PATHS = {
            "src/main/webapp/resources/css/_ds_bundle.css",
            "src/main/webapp/resources/js/krds.min.js"
    };
    private static final String[] BOOT_PATHS = {
            "src/main/resources/static/resources/css/_ds_bundle.css",
            "src/main/resources/static/resources/js/krds.min.js"
    };

    private KrdsAssetVerifier() {
    }

    public static boolean hasCompleteAssets(String outputPath) {
        Path root = Path.of(outputPath);
        return allExist(root, WAR_PATHS) || allExist(root, BOOT_PATHS);
    }

    private static boolean allExist(Path root, String[] relativePaths) {
        for (String relativePath : relativePaths) {
            if (!Files.exists(root.resolve(relativePath))) {
                return false;
            }
        }
        return true;
    }
}
