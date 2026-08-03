package com.krdevops.springai.service.artifact;

import com.krdevops.springai.config.ArtifactStoreProperties;
import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.artifact.StagedArtifact;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

/**
 * ARCH-0504/0505/0512: stage(임시 파일 작성) → commit(atomic move, content-addressed 경로)
 * 순서로 저장한다. 최종 경로는 {@code {root}/{hash[0:2]}/{hash[2:4]}/{hash}}이며 동일 hash는
 * 항상 동일 경로로 귀결되므로 저장 자체가 멱등이다(WORM). contentHash는 64자 소문자 hex로만
 * 구성되어 경로에 {@code .}/{@code /}가 섞일 수 없으므로 traversal이 구조적으로 불가능하며,
 * 그 위에 root 경계 검사와 조상 경로 symlink 검사를 추가한다.
 */
@Component
public class FilesystemArtifactStore implements ArtifactStorePort {

    private final ArtifactStoreProperties properties;

    public FilesystemArtifactStore(ArtifactStoreProperties properties) {
        this.properties = properties;
    }

    @Override
    public StagedArtifact stage(byte[] content, String mediaType) throws IOException {
        if (content == null) {
            throw new IllegalArgumentException("content는 필수입니다.");
        }
        if (mediaType == null || mediaType.isBlank()) {
            throw new IllegalArgumentException("mediaType은 필수입니다.");
        }
        if (!properties.getAllowedMediaTypes().contains(mediaType)) {
            throw new IllegalArgumentException("허용되지 않은 media type입니다: " + mediaType);
        }
        if (content.length > properties.getMaxArtifactSizeBytes()) {
            throw new IllegalArgumentException(
                    "artifact 크기(%d bytes)가 허용 한도(%d bytes)를 초과합니다."
                            .formatted(content.length, properties.getMaxArtifactSizeBytes()));
        }

        Path stagingDir = stagingDir();
        Files.createDirectories(stagingDir);
        Path stagingFile = stagingDir.resolve(UUID.randomUUID() + ".tmp");
        Files.write(stagingFile, content);

        return new StagedArtifact(stagingFile, ContentHashes.sha256Hex(content), content.length, mediaType);
    }

    @Override
    public String commit(StagedArtifact staged) throws IOException {
        Path finalPath = resolveContentPath(staged.contentHash());

        if (Files.exists(finalPath)) {
            Files.deleteIfExists(staged.stagingPath());
            return relativeUri(finalPath);
        }

        Files.createDirectories(finalPath.getParent());
        try {
            Files.move(staged.stagingPath(), finalPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException raceLoser) {
            Files.deleteIfExists(staged.stagingPath());
        }
        return relativeUri(finalPath);
    }

    @Override
    public Optional<byte[]> read(String contentHash) throws IOException {
        Path finalPath = resolveContentPath(contentHash);
        if (!Files.exists(finalPath) || Files.isSymbolicLink(finalPath)) {
            return Optional.empty();
        }
        return Optional.of(Files.readAllBytes(finalPath));
    }

    @Override
    public boolean exists(String contentHash) {
        Path finalPath = resolveContentPath(contentHash);
        return Files.exists(finalPath) && !Files.isSymbolicLink(finalPath);
    }

    @Override
    public void quarantine(String contentHash) throws IOException {
        Path finalPath = resolveContentPath(contentHash);
        if (!Files.exists(finalPath)) {
            return;
        }
        Path quarantineDir = properties.getRootPath().toAbsolutePath().normalize().resolve(".quarantine");
        Files.createDirectories(quarantineDir);
        Files.move(finalPath, quarantineDir.resolve(contentHash), StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void discardStaged(StagedArtifact staged) throws IOException {
        Files.deleteIfExists(staged.stagingPath());
    }

    private Path stagingDir() {
        return properties.getRootPath().toAbsolutePath().normalize().resolve(".staging");
    }

    private Path resolveContentPath(String contentHash) {
        ContentHashes.requireValid(contentHash);
        Path root = properties.getRootPath().toAbsolutePath().normalize();
        Path resolved = root
                .resolve(contentHash.substring(0, 2))
                .resolve(contentHash.substring(2, 4))
                .resolve(contentHash)
                .normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("경로가 artifact store 루트를 벗어납니다.");
        }
        requireNoSymlinkAncestors(resolved, root);
        return resolved;
    }

    private void requireNoSymlinkAncestors(Path target, Path root) {
        Path current = root;
        Path relative = root.relativize(target);
        for (Path segment : relative) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("artifact store 경로에 symlink가 포함될 수 없습니다: " + current);
            }
        }
    }

    private String relativeUri(Path finalPath) {
        return properties.getRootPath().toAbsolutePath().normalize()
                .relativize(finalPath)
                .toString();
    }
}
