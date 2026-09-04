package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.artifact.Artifact;
import com.krdevops.springai.model.artifact.ArtifactStatus;
import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.artifact.StagedArtifact;
import com.krdevops.springai.model.contract.VersionedArtifactReference;
import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.model.design.UiDesignSpecV2;
import com.krdevops.springai.service.artifact.ArtifactCatalogPort;
import com.krdevops.springai.service.artifact.ArtifactStorePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UiDesignSpecV2ArtifactWriterTest {

    private final UiDesignSpecV1ToV2Adapter adapter = new UiDesignSpecV1ToV2Adapter();
    private InMemoryStore store;
    private InMemoryCatalog catalog;
    private UiDesignSpecV2ArtifactWriter writer;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        catalog = new InMemoryCatalog();
        writer = new UiDesignSpecV2ArtifactWriter(store, catalog, new ObjectMapper());
    }

    private UiDesignSpecV2 v2(String specId) {
        return adapter.adapt(specId, UiDesignSpec.empty("CRUD_LIST"),
                new UiDesignSpecV2.Source(UiDesignSpecV2.SourceType.IMAGE, null, null, "rev-1"));
    }

    @Test
    void specId를_artifactId로_저장하고_참조는_content_addressed_hash를_쓴다() {
        VersionedArtifactReference ref = writer.write(v2("analysis-1"));

        Artifact stored = catalog.findById("analysis-1").orElseThrow();
        assertThat(stored.artifactType()).isEqualTo("UI_DESIGN_SPEC_V2");
        assertThat(stored.mediaType()).isEqualTo("application/json");
        assertThat(stored.status()).isEqualTo(ArtifactStatus.ACTIVE);
        assertThat(stored.contentHash())
                .isEqualTo(ContentHashes.sha256Hex(store.committedBytes(stored.contentHash())));

        assertThat(ref.artifactId()).isEqualTo("analysis-1");
        assertThat(ref.artifactType()).isEqualTo("UI_DESIGN_SPEC_V2");
        assertThat(ref.schemaVersion()).isEqualTo("2.0");
        assertThat(ref.contentHash()).isEqualTo(stored.contentHash());
        assertThat(ref.sourceRevision()).isEqualTo("rev-1");
    }

    @Test
    void 동일_스펙_재저장은_멱등이다() {
        VersionedArtifactReference first = writer.write(v2("analysis-1"));
        VersionedArtifactReference second = writer.write(v2("analysis-1"));

        assertThat(second).isEqualTo(first);
        assertThat(catalog.size()).isEqualTo(1);
    }

    // ─── in-memory fakes ────────────────────────────────────────────────────────

    static final class InMemoryStore implements ArtifactStorePort {
        private final Map<String, byte[]> committed = new HashMap<>();
        private final Map<Path, byte[]> staged = new HashMap<>();

        @Override public StagedArtifact stage(byte[] content, String mediaType) {
            String hash = ContentHashes.sha256Hex(content);
            Path path = Path.of("/staging/" + hash + ".tmp");
            staged.put(path, content);
            return new StagedArtifact(path, hash, content.length, mediaType);
        }

        @Override public String commit(StagedArtifact s) {
            committed.putIfAbsent(s.contentHash(), staged.remove(s.stagingPath()));
            return "store/" + s.contentHash().substring(0, 2) + "/" + s.contentHash();
        }

        @Override public Optional<byte[]> read(String contentHash) {
            return Optional.ofNullable(committed.get(contentHash));
        }

        @Override public boolean exists(String contentHash) {
            return committed.containsKey(contentHash);
        }

        @Override public void quarantine(String contentHash) { }

        @Override public void discardStaged(StagedArtifact staged) {
            this.staged.remove(staged.stagingPath());
        }

        byte[] committedBytes(String hash) { return committed.get(hash); }
    }

    static final class InMemoryCatalog implements ArtifactCatalogPort {
        private final Map<String, Artifact> byHash = new HashMap<>();
        private final Map<String, Artifact> byId = new HashMap<>();

        @Override public Artifact save(Artifact artifact) {
            Artifact existing = byHash.get(artifact.contentHash());
            if (existing != null) return existing;
            byHash.put(artifact.contentHash(), artifact);
            byId.put(artifact.artifactId(), artifact);
            return artifact;
        }

        @Override public Optional<Artifact> findByContentHash(String contentHash) {
            return Optional.ofNullable(byHash.get(contentHash));
        }

        @Override public Optional<Artifact> findById(String artifactId) {
            return Optional.ofNullable(byId.get(artifactId));
        }

        @Override public List<Artifact> findAll() { return new ArrayList<>(byHash.values()); }

        @Override public void linkToOperation(String o, String t, String a) { }

        @Override public List<Artifact> findByOperation(String operationId) { return List.of(); }

        @Override public void updateStatus(String artifactId, ArtifactStatus status) { }

        int size() { return byHash.size(); }
    }
}
