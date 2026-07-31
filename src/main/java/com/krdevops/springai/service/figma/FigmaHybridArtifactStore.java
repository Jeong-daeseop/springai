package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.WebCaptureProperties;
import com.krdevops.springai.model.figma.hybrid.FigmaHybridCandidate;
import com.krdevops.springai.model.figma.hybrid.FigmaHybridExportResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Hybrid 메타데이터를 기존 capture artifact 디렉터리에 함께 보관한다. */
@Service
public class FigmaHybridArtifactStore {

    private static final String CANDIDATE_FILE = "hybrid-candidate.json";
    private static final String RESULT_FILE = "hybrid-result.json";

    private final WebCaptureProperties properties;
    private final ObjectMapper objectMapper;

    public FigmaHybridArtifactStore(WebCaptureProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    public void saveCandidate(FigmaHybridCandidate candidate) {
        write(candidate.artifactId(), CANDIDATE_FILE, candidate);
    }

    public FigmaHybridCandidate readCandidate(String artifactId) {
        return read(artifactId, CANDIDATE_FILE, FigmaHybridCandidate.class)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Hybrid 후보를 찾을 수 없습니다: " + artifactId));
    }

    public void saveResult(FigmaHybridExportResult result) {
        write(result.artifactId(), RESULT_FILE, result);
    }

    public Optional<FigmaHybridExportResult> findResult(String artifactId) {
        return read(artifactId, RESULT_FILE, FigmaHybridExportResult.class);
    }

    private void write(String artifactId, String fileName, Object value) {
        Path directory = artifactDirectory(artifactId);
        Path target = directory.resolve(fileName);
        Path temporary = directory.resolve("." + fileName + ".tmp-" + UUID.randomUUID());
        try {
            objectMapper.writeValue(temporary.toFile(), value);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            try {
                Files.deleteIfExists(temporary);
            } catch (Exception ignored) {
                // 원래 저장 예외를 유지한다.
            }
            throw new IllegalStateException("Hybrid artifact 저장 실패: " + fileName, exception);
        }
    }

    private <T> Optional<T> read(String artifactId, String fileName, Class<T> type) {
        Path target = artifactDirectory(artifactId).resolve(fileName);
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(target.toFile(), type));
        } catch (Exception exception) {
            throw new IllegalStateException("Hybrid artifact 읽기 실패: " + fileName, exception);
        }
    }

    private Path artifactDirectory(String artifactId) {
        String safeId = UUID.fromString(artifactId).toString();
        Path root = properties.getArtifactBasePath().toAbsolutePath().normalize();
        Path directory = root.resolve(safeId).normalize();
        if (!directory.getParent().equals(root) || !Files.isDirectory(directory)
                || Files.isSymbolicLink(directory)) {
            throw new IllegalArgumentException("유효한 capture artifact가 아닙니다: " + artifactId);
        }
        return directory;
    }
}
