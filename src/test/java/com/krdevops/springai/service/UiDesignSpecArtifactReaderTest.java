package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.artifact.Artifact;
import com.krdevops.springai.model.artifact.ArtifactStatus;
import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.design.DesignAnalysisResult;
import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.model.design.UiDesignSpecV2;
import com.krdevops.springai.service.artifact.ArtifactCatalogPort;
import com.krdevops.springai.service.artifact.ArtifactStorePort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UiDesignSpecArtifactReaderTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final UiDesignSpecV1ToV2Adapter adapter = new UiDesignSpecV1ToV2Adapter();

    @Test
    void v2Artifact는변환없이그대로읽는다() throws Exception {
        UiDesignSpecV2 original = adapter.adapt("ui-v2", UiDesignSpec.empty("CRUD_LIST"),
                new UiDesignSpecV2.Source(UiDesignSpecV2.SourceType.IMAGE, null, null, "image-r1"));
        byte[] bytes = mapper.writeValueAsBytes(original);
        Fixture fixture = fixture("ui-v2", "UI_DESIGN_SPEC_V2", bytes, ArtifactStatus.ACTIVE);

        UiDesignSpecArtifactReader.ReadResult result = fixture.reader().read("ui-v2");

        assertThat(result.legacyConverted()).isFalse();
        assertThat(result.spec()).isEqualTo(original);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void designAnalysis의v1을손실표시가있는v2View로읽고원본은보존한다() throws Exception {
        DesignAnalysisResult analysis = new DesignAnalysisResult(
                "analysis-1", "a".repeat(64), "/tmp/form.png", null,
                "vision", "model", "prompt-v1", List.of(), UiDesignSpec.empty("CRUD_FORM"),
                List.of("기존 경고"), LocalDateTime.of(2026, 8, 23, 10, 0));
        byte[] bytes = mapper.writeValueAsBytes(analysis);
        byte[] before = bytes.clone();
        Fixture fixture = fixture("analysis-artifact", "DESIGN_ANALYSIS", bytes, ArtifactStatus.ACTIVE);

        UiDesignSpecArtifactReader.ReadResult result = fixture.reader().read("analysis-artifact");

        assertThat(result.legacyConverted()).isTrue();
        assertThat(result.spec().specId()).isEqualTo("analysis-artifact");
        assertThat(result.spec().nodes())
                .allSatisfy(node -> assertThat(node.evidence().legacyUnknown()).isTrue());
        assertThat(result.spec().issues()).extracting(UiDesignSpecV2.DesignIssue::code)
                .contains("LEGACY_EVIDENCE_UNAVAILABLE");
        assertThat(result.warnings()).contains("기존 경고");
        assertThat(bytes).containsExactly(before);
        verify(fixture.store()).read(fixture.artifact().contentHash());
    }

    @Test
    void rawV1도출처손실을명시한v2View로읽는다() throws Exception {
        byte[] bytes = mapper.writeValueAsBytes(UiDesignSpec.empty("BOARD_LIST"));
        Fixture fixture = fixture("legacy-raw", "UI_DESIGN_SPEC_V1", bytes, ArtifactStatus.ACTIVE);

        UiDesignSpecArtifactReader.ReadResult result = fixture.reader().read("legacy-raw");

        assertThat(result.legacyConverted()).isTrue();
        assertThat(result.spec().source().sourceType()).isEqualTo(UiDesignSpecV2.SourceType.IMAGE);
        assertThat(result.warnings()).anyMatch(value -> value.contains("출처 Envelope가 없는"));
    }

    @Test
    void 격리Artifact와지원하지않는계약을거부한다() throws Exception {
        byte[] bytes = mapper.writeValueAsBytes(UiDesignSpec.empty("CRUD_LIST"));
        Fixture quarantined = fixture("legacy", "UI_DESIGN_SPEC_V1", bytes, ArtifactStatus.QUARANTINED);

        assertThatThrownBy(() -> quarantined.reader().read("legacy"))
                .isInstanceOf(UiDesignSpecArtifactReader.DesignIrReadException.class)
                .extracting(exception -> ((UiDesignSpecArtifactReader.DesignIrReadException) exception).errorCode())
                .isEqualTo("DESIGN_IR_NOT_ACTIVE");

        byte[] unknown = mapper.writeValueAsBytes(java.util.Map.of("schemaVersion", "3.0"));
        Fixture unsupported = fixture("future", "UI_DESIGN_SPEC_V2", unknown, ArtifactStatus.ACTIVE);
        assertThatThrownBy(() -> unsupported.reader().read("future"))
                .isInstanceOf(UiDesignSpecArtifactReader.DesignIrReadException.class)
                .extracting(exception -> ((UiDesignSpecArtifactReader.DesignIrReadException) exception).errorCode())
                .isEqualTo("DESIGN_IR_SCHEMA_UNSUPPORTED");
    }

    @Test
    void 저장바이트Hash가Catalog와다르면거부한다() throws Exception {
        byte[] catalogBytes = mapper.writeValueAsBytes(UiDesignSpec.empty("CRUD_LIST"));
        byte[] corrupted = mapper.writeValueAsBytes(UiDesignSpec.empty("BOARD_LIST"));
        ArtifactCatalogPort catalog = mock(ArtifactCatalogPort.class);
        ArtifactStorePort store = mock(ArtifactStorePort.class);
        Artifact artifact = artifact("legacy", "UI_DESIGN_SPEC_V1", catalogBytes, ArtifactStatus.ACTIVE);
        when(catalog.findById("legacy")).thenReturn(Optional.of(artifact));
        when(store.read(artifact.contentHash())).thenReturn(Optional.of(corrupted));
        UiDesignSpecArtifactReader reader = new UiDesignSpecArtifactReader(catalog, store, mapper, adapter);

        assertThatThrownBy(() -> reader.read("legacy"))
                .isInstanceOf(UiDesignSpecArtifactReader.DesignIrReadException.class)
                .extracting(exception -> ((UiDesignSpecArtifactReader.DesignIrReadException) exception).errorCode())
                .isEqualTo("DESIGN_IR_HASH_MISMATCH");
    }

    private Fixture fixture(String id, String type, byte[] bytes, ArtifactStatus status) throws Exception {
        ArtifactCatalogPort catalog = mock(ArtifactCatalogPort.class);
        ArtifactStorePort store = mock(ArtifactStorePort.class);
        Artifact artifact = artifact(id, type, bytes, status);
        when(catalog.findById(id)).thenReturn(Optional.of(artifact));
        when(store.read(artifact.contentHash())).thenReturn(Optional.of(bytes));
        return new Fixture(new UiDesignSpecArtifactReader(catalog, store, mapper, adapter), store, artifact);
    }

    private Artifact artifact(String id, String type, byte[] bytes, ArtifactStatus status) {
        return new Artifact(id, type, "application/json", bytes.length, ContentHashes.sha256Hex(bytes),
                "source-r1", "artifacts/" + id + ".json", status, Instant.parse("2026-08-23T01:00:00Z"));
    }

    private record Fixture(UiDesignSpecArtifactReader reader, ArtifactStorePort store, Artifact artifact) {
    }
}
