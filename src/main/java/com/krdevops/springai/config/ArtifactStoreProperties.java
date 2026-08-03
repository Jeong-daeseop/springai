package com.krdevops.springai.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** ARCH-0504: 공통 Artifact filesystem 저장소 설정. */
@ConfigurationProperties(prefix = "app.artifact-store")
public class ArtifactStoreProperties {

    private Path rootPath = Path.of(System.getProperty("java.io.tmpdir"), "springai-artifact-store");
    private long maxArtifactSizeBytes = 100L * 1024 * 1024;
    private List<String> allowedMediaTypes = new ArrayList<>(List.of(
            "application/json", "text/html", "text/plain", "application/zip", "image/png", "image/jpeg"));

    @PostConstruct
    void validate() {
        if (maxArtifactSizeBytes <= 0) {
            throw new IllegalStateException("app.artifact-store.max-artifact-size-bytes는 0보다 커야 합니다.");
        }
        if (allowedMediaTypes.isEmpty()) {
            throw new IllegalStateException("app.artifact-store.allowed-media-types는 최소 1개 이상이어야 합니다.");
        }
    }

    public Path getRootPath() {
        return rootPath;
    }

    public void setRootPath(Path value) {
        rootPath = value;
    }

    public long getMaxArtifactSizeBytes() {
        return maxArtifactSizeBytes;
    }

    public void setMaxArtifactSizeBytes(long value) {
        maxArtifactSizeBytes = value;
    }

    public List<String> getAllowedMediaTypes() {
        return allowedMediaTypes;
    }

    public void setAllowedMediaTypes(List<String> value) {
        allowedMediaTypes = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }
}
