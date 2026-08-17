package com.krdevops.springai.service.designsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.designsystem.ComponentCatalog;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Classpath에 패키징된 Catalog를 버전과 SHA-256으로 고정해 읽는다. */
@Service
public class ComponentCatalogLoader {

    private static final String CURRENT_PATH = "figma/contracts/component-catalog-v2.json";
    private final ObjectMapper objectMapper;
    private final Map<String, LoadedCatalog> cache = new ConcurrentHashMap<>();

    public ComponentCatalogLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    public LoadedCatalog load(String contractVersion) {
        if (contractVersion == null || contractVersion.isBlank()) {
            throw new ComponentCatalogException("CATALOG_VERSION_REQUIRED", "Catalog 버전은 필수입니다.");
        }
        return cache.computeIfAbsent(contractVersion, this::loadCurrent);
    }

    private LoadedCatalog loadCurrent(String requestedVersion) {
        try {
            byte[] bytes = new ClassPathResource(CURRENT_PATH).getInputStream().readAllBytes();
            ComponentCatalog catalog = objectMapper.readValue(bytes, ComponentCatalog.class);
            if (!ComponentCatalog.SCHEMA_VERSION.equals(catalog.schemaVersion())) {
                throw new ComponentCatalogException("CATALOG_SCHEMA_UNSUPPORTED",
                        "지원하지 않는 Catalog Schema입니다: " + catalog.schemaVersion());
            }
            if (!requestedVersion.equals(catalog.contractVersion())) {
                throw new ComponentCatalogException("CATALOG_VERSION_NOT_FOUND",
                        "Catalog 버전을 찾을 수 없습니다: " + requestedVersion);
            }
            return new LoadedCatalog(catalog, sha256(bytes));
        } catch (ComponentCatalogException e) {
            throw e;
        } catch (IOException e) {
            throw new ComponentCatalogException("CATALOG_LOAD_FAILED", "Catalog를 읽을 수 없습니다.", e);
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 계산 실패", e);
        }
    }

    public record LoadedCatalog(ComponentCatalog catalog, String contentHash) {}

    public static class ComponentCatalogException extends IllegalArgumentException {
        private final String code;

        public ComponentCatalogException(String code, String message) {
            super(message);
            this.code = code;
        }

        public ComponentCatalogException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
