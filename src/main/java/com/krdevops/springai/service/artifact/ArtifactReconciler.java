package com.krdevops.springai.service.artifact;

import com.krdevops.springai.config.ArtifactStoreProperties;
import com.krdevops.springai.model.artifact.Artifact;
import com.krdevops.springai.model.artifact.ArtifactReconciliationReport;
import com.krdevops.springai.model.artifact.ArtifactStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * ARCH-0509/0511: catalog(DB)와 filesystem 실제 상태를 비교해
 * orphan(파일은 있는데 catalog row 없음) / missing(catalog row는 있는데 파일 없음)을 탐지한다.
 * dry-run(기본)은 보고만 하고, execute(dryRun=false)는 orphan 파일을 quarantine으로 옮기고
 * missing catalog row는 QUARANTINED 상태로 표시한다 — 어느 경로도 즉시 삭제하지 않는다.
 */
@Component
public class ArtifactReconciler {

    private static final Pattern HASH_FILENAME = Pattern.compile("^[a-f0-9]{64}$");

    private final ArtifactStoreProperties properties;
    private final ArtifactCatalogPort catalog;
    private final ArtifactStorePort store;

    public ArtifactReconciler(ArtifactStoreProperties properties, ArtifactCatalogPort catalog, ArtifactStorePort store) {
        this.properties = properties;
        this.catalog = catalog;
        this.store = store;
    }

    public ArtifactReconciliationReport reconcile(boolean dryRun) {
        Set<String> filesOnDisk = listContentHashesOnDisk();
        List<Artifact> catalogEntries = catalog.findAll();

        Set<String> hashesInCatalog = new HashSet<>();
        List<Artifact> missing = new ArrayList<>();
        for (Artifact artifact : catalogEntries) {
            hashesInCatalog.add(artifact.contentHash());
            if (!filesOnDisk.contains(artifact.contentHash())) {
                missing.add(artifact);
            }
        }

        List<String> orphans = filesOnDisk.stream()
                .filter(hash -> !hashesInCatalog.contains(hash))
                .sorted()
                .toList();

        List<String> quarantined = new ArrayList<>();
        if (!dryRun) {
            for (String orphanHash : orphans) {
                try {
                    store.quarantine(orphanHash);
                    quarantined.add(orphanHash);
                } catch (IOException e) {
                    throw new UncheckedIOException("orphan artifact quarantine 중 오류: " + orphanHash, e);
                }
            }
            for (Artifact artifact : missing) {
                catalog.updateStatus(artifact.artifactId(), ArtifactStatus.QUARANTINED);
            }
        }

        return new ArtifactReconciliationReport(dryRun, orphans, missing, quarantined);
    }

    private Set<String> listContentHashesOnDisk() {
        Path root = properties.getRootPath().toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return Set.of();
        }
        Path stagingDir = root.resolve(".staging");
        Path quarantineDir = root.resolve(".quarantine");

        Set<String> hashes = new HashSet<>();
        try (Stream<Path> walk = Files.walk(root, 3)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> !path.startsWith(stagingDir))
                    .filter(path -> !path.startsWith(quarantineDir))
                    .map(path -> path.getFileName().toString())
                    .filter(name -> HASH_FILENAME.matcher(name).matches())
                    .forEach(hashes::add);
        } catch (IOException e) {
            throw new UncheckedIOException("artifact store 디렉토리 탐색 중 오류가 발생했습니다.", e);
        }
        return hashes;
    }
}
