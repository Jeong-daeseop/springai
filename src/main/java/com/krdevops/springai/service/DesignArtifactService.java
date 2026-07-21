package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.WebCaptureProperties;
import com.krdevops.springai.model.capture.CaptureArtifactSummary;
import com.krdevops.springai.model.capture.CaptureStatus;
import com.krdevops.springai.model.capture.DesignArtifactMetadata;
import com.krdevops.springai.model.capture.FigmaImportArtifact;
import com.krdevops.springai.model.capture.RenderedDesignDocument;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;

@Service
public class DesignArtifactService {
    private final WebCaptureProperties properties;
    private final ObjectMapper objectMapper;

    public DesignArtifactService(WebCaptureProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    public CaptureArtifactSummary save(RenderedDesignPackageValidator.ValidatedPackage pack) {
        String artifactId = requireUuid(pack.document().captureId());
        Path root = root();
        Path target = resolveArtifact(artifactId);
        Path temporary = root.resolve("." + artifactId + ".tmp-" + UUID.randomUUID()).normalize();
        try {
            Files.createDirectories(root);
            if (Files.isSymbolicLink(root)) throw new IllegalStateException("artifact root는 symbolic link일 수 없습니다.");
            cleanupExpired();
            if (Files.exists(target)) throw new IllegalStateException("동일 artifact가 이미 존재합니다.");
            Files.createDirectory(temporary);
            Files.write(temporary.resolve("source.figpack"), pack.packageBytes());
            Files.write(temporary.resolve("document.json"), pack.entries().get("document.json"));
            if (pack.entries().containsKey("preview.png")) {
                Files.write(temporary.resolve("preview.png"), pack.entries().get("preview.png"));
            }
            for (var entry : pack.entries().entrySet()) {
                if (!entry.getKey().startsWith("assets/")) continue;
                Path destination = temporary.resolve(entry.getKey()).normalize();
                if (!destination.startsWith(temporary)) throw new IllegalStateException("artifact 경로 이탈입니다.");
                Files.createDirectories(destination.getParent());
                Files.write(destination, entry.getValue());
            }
            DesignArtifactMetadata metadata = new DesignArtifactMetadata(artifactId,
                    pack.document().documentKey(), pack.document().contentHash(),
                    pack.document().schemaVersion(), LocalDateTime.now());
            objectMapper.writeValue(temporary.resolve("metadata.json").toFile(), metadata);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            return summary(pack.document(), metadata.createdAt());
        } catch (Exception e) {
            deleteQuietly(temporary);
            if (e instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("design artifact 저장 실패", e);
        }
    }

    public CaptureArtifactSummary get(String artifactId) {
        try {
            Path directory = resolveArtifact(requireUuid(artifactId));
            DesignArtifactMetadata metadata = objectMapper.readValue(directory.resolve("metadata.json").toFile(),
                    DesignArtifactMetadata.class);
            RenderedDesignDocument document = objectMapper.readValue(directory.resolve("document.json").toFile(),
                    RenderedDesignDocument.class);
            if (!metadata.artifactId().equals(document.captureId())
                    || !metadata.contentHash().equals(document.contentHash())) {
                throw new IllegalStateException("artifact metadata와 document가 일치하지 않습니다.");
            }
            return summary(document, metadata.createdAt());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("design artifact를 찾거나 읽을 수 없습니다.", e);
        }
    }

    public RenderedDesignDocument readDocument(String artifactId) {
        try {
            return objectMapper.readValue(resolveArtifact(requireUuid(artifactId)).resolve("document.json").toFile(),
                    RenderedDesignDocument.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("design document를 읽을 수 없습니다.", e);
        }
    }

    public FigmaImportArtifact prepareFigmaImport(String artifactId) {
        try {
            String safeId = requireUuid(artifactId);
            Path source = resolveArtifact(safeId).resolve("source.figpack");
            Path export = resolveArtifact(safeId).resolve("export");
            Files.createDirectories(export);
            Path target = export.resolve(safeId + ".figpack");
            if (!Files.exists(target)) Files.copy(source, target);
            return new FigmaImportArtifact(safeId, target.getFileName().toString(), target.toString(), Files.size(target));
        } catch (Exception e) {
            throw new IllegalArgumentException("Figma import artifact 준비 실패", e);
        }
    }

    public int cleanupExpired() {
        Path root = root();
        if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) return 0;
        LocalDateTime cutoff = LocalDateTime.now().minus(Duration.ofHours(properties.getRetentionHours()));
        int removed = 0;
        try (var children = Files.list(root)) {
            for (Path child : children.toList()) {
                if (!Files.isDirectory(child, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(child) || child.getFileName().toString().startsWith(".")) continue;
                try {
                    UUID.fromString(child.getFileName().toString());
                    Path metadataPath = child.resolve("metadata.json");
                    if (!Files.isRegularFile(metadataPath, java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue;
                    DesignArtifactMetadata metadata = objectMapper.readValue(metadataPath.toFile(), DesignArtifactMetadata.class);
                    if (metadata.createdAt() != null && metadata.createdAt().isBefore(cutoff)) {
                        deleteQuietly(child);
                        removed++;
                    }
                } catch (IllegalArgumentException ignored) {
                    // UUID 형식이 아닌 운영자 파일은 건드리지 않는다.
                }
            }
            return removed;
        } catch (Exception e) {
            throw new IllegalStateException("만료 design artifact 정리 실패", e);
        }
    }

    private CaptureArtifactSummary summary(RenderedDesignDocument document, LocalDateTime createdAt) {
        return new CaptureArtifactSummary(document.captureId(), CaptureStatus.SUCCEEDED,
                document.documentKey(), document.contentHash(), document.nodes().size(),
                document.assets().size(), document.warnings(), createdAt);
    }

    private Path root() {
        return properties.getArtifactBasePath().toAbsolutePath().normalize();
    }

    private Path resolveArtifact(String artifactId) {
        Path resolved = root().resolve(artifactId).normalize();
        if (!resolved.getParent().equals(root())) throw new IllegalArgumentException("artifact 경로가 올바르지 않습니다.");
        return resolved;
    }

    private static String requireUuid(String value) {
        return UUID.fromString(value).toString();
    }

    private static void deleteQuietly(Path path) {
        if (path == null || !Files.exists(path)) return;
        try (var paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(item -> {
                try { Files.deleteIfExists(item); } catch (Exception ignored) { }
            });
        } catch (Exception ignored) { }
    }
}
