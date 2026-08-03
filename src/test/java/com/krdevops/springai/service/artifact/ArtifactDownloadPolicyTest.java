package com.krdevops.springai.service.artifact;

import com.krdevops.springai.model.artifact.Artifact;
import com.krdevops.springai.model.artifact.ArtifactStatus;
import com.krdevops.springai.model.artifact.ContentHashes;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ARCH-0507: QUARANTINED artifact 다운로드 거부(M2-G9), Content-Disposition 파일명 생성. */
class ArtifactDownloadPolicyTest {

    private final ArtifactDownloadPolicy policy = new ArtifactDownloadPolicy();

    @Test
    void activeArtifact_isDownloadableWithAttachmentDisposition() {
        Artifact artifact = artifact(ArtifactStatus.ACTIVE, "application/json");

        assertThat(policy.isDownloadable(artifact)).isTrue();
        assertThat(policy.contentDisposition(artifact))
                .isEqualTo("attachment; filename=\"" + artifact.artifactId() + ".json\"");
    }

    @Test
    void quarantinedArtifact_isNotDownloadable() {
        Artifact artifact = artifact(ArtifactStatus.QUARANTINED, "text/html");

        assertThat(policy.isDownloadable(artifact)).isFalse();
        assertThatThrownBy(() -> policy.contentDisposition(artifact))
                .isInstanceOf(IllegalStateException.class);
    }

    private Artifact artifact(ArtifactStatus status, String mediaType) {
        String contentHash = ContentHashes.sha256Hex(("policy-" + status).getBytes());
        return new Artifact("art-id", "THYMELEAF_PREVIEW", mediaType, 10L, contentHash,
                "rev-1", "ab/cd/" + contentHash, status, Instant.now());
    }
}
