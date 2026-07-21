package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.WebCaptureProperties;
import com.krdevops.springai.model.capture.DesignArtifactMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DesignArtifactRetentionTest {
    @TempDir Path root;

    @Test
    void removesOnlyExpiredUuidArtifactDirectories() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        WebCaptureProperties properties = new WebCaptureProperties();
        properties.setArtifactBasePath(root); properties.setRetentionHours(24);
        DesignArtifactService service = new DesignArtifactService(properties, mapper);
        String expired = UUID.randomUUID().toString();
        String current = UUID.randomUUID().toString();
        write(mapper, expired, LocalDateTime.now().minusHours(25));
        write(mapper, current, LocalDateTime.now());
        Files.createDirectory(root.resolve("operator-notes"));

        assertThat(service.cleanupExpired()).isEqualTo(1);
        assertThat(root.resolve(expired)).doesNotExist();
        assertThat(root.resolve(current)).exists();
        assertThat(root.resolve("operator-notes")).exists();
    }

    private void write(ObjectMapper mapper, String id, LocalDateTime createdAt) throws Exception {
        Path directory = Files.createDirectory(root.resolve(id));
        mapper.writeValue(directory.resolve("metadata.json").toFile(),
                new DesignArtifactMetadata(id, "document", "content", "v1", createdAt));
    }
}
