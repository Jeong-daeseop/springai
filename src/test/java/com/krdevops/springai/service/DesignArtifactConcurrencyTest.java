package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.WebCaptureProperties;
import com.krdevops.springai.model.capture.RenderedDesignDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DesignArtifactConcurrencyTest {
    @TempDir Path root;

    @Test
    void concurrentSaveCreatesExactlyOneCompleteArtifact() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        WebCaptureProperties properties = new WebCaptureProperties();
        properties.setArtifactBasePath(root);
        DesignArtifactService service = new DesignArtifactService(properties, mapper);
        String captureId = "11111111-1111-4111-8111-111111111111";
        RenderedDesignDocument document = new RenderedDesignDocument(
                RenderedDesignDocument.SCHEMA_VERSION, captureId, "a".repeat(64), "b".repeat(64),
                new RenderedDesignDocument.Source("RENDERED_WEB_PAGE", "JSP", "http://localhost/",
                        "http://localhost/", "c".repeat(64), "2026-01-01T00:00:00Z"),
                new RenderedDesignDocument.Environment("desktop", 1440, 1200, 1, "ko-KR",
                        "Asia/Seoul", "light", true, "chromium"),
                new RenderedDesignDocument.Page("동시 저장", "root", 1440, 1200, 0, 0, "white"),
                List.of(), List.of(), Map.of(), List.of(), List.of(), List.of(), Map.of());
        byte[] documentBytes = mapper.writeValueAsBytes(document);
        var pack = new RenderedDesignPackageValidator.ValidatedPackage(new byte[]{1}, null, document,
                Map.of("document.json", documentBytes));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var futures = List.of(executor.submit(() -> save(service, pack, ready, start)),
                    executor.submit(() -> save(service, pack, ready, start)));
            ready.await(); start.countDown();
            long successes = 0;
            for (var future : futures) if (future.get()) successes++;

            assertThat(successes).isEqualTo(1);
            assertThat(service.get(captureId).artifactId()).isEqualTo(captureId);
            try (var children = Files.list(root)) {
                assertThat(children.map(path -> path.getFileName().toString()).toList())
                        .containsExactly(captureId);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsArtifactPathTraversalBeforeFilesystemAccess() {
        WebCaptureProperties properties = new WebCaptureProperties();
        properties.setArtifactBasePath(root);
        DesignArtifactService service = new DesignArtifactService(properties,
                new ObjectMapper().findAndRegisterModules());

        assertThatThrownBy(() -> service.get("../metadata.json"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.prepareFigmaImport("../../escape"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Figma import artifact 준비 실패");
        assertThat(root).isEmptyDirectory();
    }

    private static boolean save(DesignArtifactService service,
                                RenderedDesignPackageValidator.ValidatedPackage pack,
                                CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            service.save(pack);
            return true;
        } catch (Exception expected) {
            return false;
        }
    }
}
