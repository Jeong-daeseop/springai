package com.krdevops.springai.service.parity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.contract.ArtifactRef;
import com.krdevops.springai.model.contract.SourceRevisionRef;
import com.krdevops.springai.model.figma.contract.FigmaDesignOperation;
import com.krdevops.springai.model.figma.contract.FigmaDesignOperationStatus;
import com.krdevops.springai.model.figma.contract.FigmaDesignRequest;
import com.krdevops.springai.model.parity.DesignParityRequest;
import com.krdevops.springai.model.parity.DesignParityResult;
import com.krdevops.springai.model.parity.DesignParityStatus;
import com.krdevops.springai.model.thymeleaf.ProjectOperationStatus;
import com.krdevops.springai.model.thymeleaf.ThymeleafProjectOperation;
import com.krdevops.springai.service.contract.OperationHashFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ARCH-0208/0209: 기존 {@code FigmaThymeleafBridgeTool.validateThymeleafDesignParity}가
 * 항상 반환하던 근거 없는 VERIFIED가 재현되지 않음을 증명한다 — evidence(존재하는 Operation·
 * Artifact·일치하는 hash) 없이는 어떤 경로로도 VERIFIED에 도달할 수 없어야 한다.
 */
class DesignParityValidationUseCaseTest {

    private final DesignParityValidationUseCase useCase =
            new DesignParityValidationUseCase(new OperationHashFactory(new ObjectMapper()));

    private static final String THYMELEAF_CONTENT = "<html><body>Employee List</body></html>";

    private FigmaDesignOperation figmaOperation(FigmaDesignOperationStatus status, List<ArtifactRef> artifacts) {
        FigmaDesignRequest request = FigmaDesignRequest.textDescription("직원 목록 화면", "file-key-123");
        return new FigmaDesignOperation(
                "figma-op-1", 1, request, "req-hash", status,
                new SourceRevisionRef("file-key-123", "rev-1", Instant.now()),
                List.of(), artifacts, Instant.now(), Instant.now());
    }

    private ArtifactRef artifactRef(String id, String hash) {
        return new ArtifactRef(id, "FIGMA_EXPORT_BUNDLE", "s3://bundle/" + id, hash, Instant.now());
    }

    private ThymeleafProjectOperation thymeleafOperation(ProjectOperationStatus status, Map<String, String> previews) {
        return new ThymeleafProjectOperation(
                "thymeleaf-op-1", "/project/root", status, previews,
                List.copyOf(previews.keySet()), null, List.of(), List.of(), true,
                LocalDateTime.now(), null);
    }

    private String hashOf(String content) {
        return new OperationHashFactory(new ObjectMapper()).sha256Hex(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void verifiedWhenBothArtifactsExistAndHashMatches() {
        String hash = hashOf(THYMELEAF_CONTENT);
        var request = new DesignParityRequest(
                figmaOperation(FigmaDesignOperationStatus.APPLIED, List.of(artifactRef("bundle-1", "a".repeat(64)))),
                "bundle-1",
                thymeleafOperation(ProjectOperationStatus.APPLIED, Map.of("list.html", THYMELEAF_CONTENT)),
                "list.html",
                hash);

        DesignParityResult result = useCase.validate(request);

        assertThat(result.status()).isEqualTo(DesignParityStatus.VERIFIED);
        assertThat(result.evidenceArtifactId()).contains("bundle-1");
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void mismatchWhenFigmaOperationMissing() {
        var request = new DesignParityRequest(
                null, "bundle-1",
                thymeleafOperation(ProjectOperationStatus.APPLIED, Map.of("list.html", THYMELEAF_CONTENT)),
                "list.html", hashOf(THYMELEAF_CONTENT));

        assertThat(useCase.validate(request).status()).isEqualTo(DesignParityStatus.MISMATCH);
    }

    @Test
    void mismatchWhenThymeleafOperationMissing() {
        var request = new DesignParityRequest(
                figmaOperation(FigmaDesignOperationStatus.APPLIED, List.of(artifactRef("bundle-1", "a".repeat(64)))),
                "bundle-1", null, "list.html", hashOf(THYMELEAF_CONTENT));

        assertThat(useCase.validate(request).status()).isEqualTo(DesignParityStatus.MISMATCH);
    }

    @ParameterizedTest
    @EnumSource(value = FigmaDesignOperationStatus.class, names = {"ANALYZED", "FAILED", "CONFLICT", "REJECTED"})
    void conflictWhenFigmaOperationNotEvidenceReady(FigmaDesignOperationStatus status) {
        var request = new DesignParityRequest(
                figmaOperation(status, List.of(artifactRef("bundle-1", "a".repeat(64)))),
                "bundle-1",
                thymeleafOperation(ProjectOperationStatus.APPLIED, Map.of("list.html", THYMELEAF_CONTENT)),
                "list.html", hashOf(THYMELEAF_CONTENT));

        assertThat(useCase.validate(request).status()).isEqualTo(DesignParityStatus.CONFLICT);
    }

    @ParameterizedTest
    @EnumSource(value = ProjectOperationStatus.class,
            names = {"ANALYZED", "CONTRACT_READY", "PREVIEW_READY", "FAILED", "CONFLICT", "REJECTED"})
    void conflictWhenThymeleafOperationNotEvidenceReady(ProjectOperationStatus status) {
        var request = new DesignParityRequest(
                figmaOperation(FigmaDesignOperationStatus.APPLIED, List.of(artifactRef("bundle-1", "a".repeat(64)))),
                "bundle-1",
                thymeleafOperation(status, Map.of("list.html", THYMELEAF_CONTENT)),
                "list.html", hashOf(THYMELEAF_CONTENT));

        assertThat(useCase.validate(request).status()).isEqualTo(DesignParityStatus.CONFLICT);
    }

    @Test
    void mismatchWhenFigmaArtifactIdNotFound() {
        var request = new DesignParityRequest(
                figmaOperation(FigmaDesignOperationStatus.APPLIED, List.of(artifactRef("bundle-1", "a".repeat(64)))),
                "nonexistent-bundle",
                thymeleafOperation(ProjectOperationStatus.APPLIED, Map.of("list.html", THYMELEAF_CONTENT)),
                "list.html", hashOf(THYMELEAF_CONTENT));

        assertThat(useCase.validate(request).status()).isEqualTo(DesignParityStatus.MISMATCH);
    }

    @Test
    void mismatchWhenThymeleafPathNotFound_evenWithTraversalStylePath() {
        // ARCH-0210: previewArtifacts는 이미 메모리에 올라온 Map이라 파일시스템 접근이 없다 —
        // traversal 스타일 키를 넣어도 단순 Map 조회 실패로 끝나야 하며 어떤 경로도 escape하지 않는다.
        var request = new DesignParityRequest(
                figmaOperation(FigmaDesignOperationStatus.APPLIED, List.of(artifactRef("bundle-1", "a".repeat(64)))),
                "bundle-1",
                thymeleafOperation(ProjectOperationStatus.APPLIED, Map.of("list.html", THYMELEAF_CONTENT)),
                "../../../../etc/passwd", hashOf(THYMELEAF_CONTENT));

        DesignParityResult result = useCase.validate(request);
        assertThat(result.status()).isEqualTo(DesignParityStatus.MISMATCH);
        assertThat(result.issues().get(0)).contains("THYMELEAF_ARTIFACT_NOT_FOUND");
    }

    @Test
    void unsupportedWhenExpectedHashNotProvided() {
        var request = new DesignParityRequest(
                figmaOperation(FigmaDesignOperationStatus.APPLIED, List.of(artifactRef("bundle-1", "a".repeat(64)))),
                "bundle-1",
                thymeleafOperation(ProjectOperationStatus.APPLIED, Map.of("list.html", THYMELEAF_CONTENT)),
                "list.html", null);

        DesignParityResult result = useCase.validate(request);
        assertThat(result.status()).isEqualTo(DesignParityStatus.UNSUPPORTED);
        assertThat(result.evidenceArtifactId()).isEmpty();
    }

    @Test
    void unsupportedWhenExpectedHashIsBlank() {
        var request = new DesignParityRequest(
                figmaOperation(FigmaDesignOperationStatus.APPLIED, List.of(artifactRef("bundle-1", "a".repeat(64)))),
                "bundle-1",
                thymeleafOperation(ProjectOperationStatus.APPLIED, Map.of("list.html", THYMELEAF_CONTENT)),
                "list.html", "   ");

        assertThat(useCase.validate(request).status()).isEqualTo(DesignParityStatus.UNSUPPORTED);
    }

    @Test
    void mismatchWhenExpectedHashDoesNotMatchActualContent() {
        var request = new DesignParityRequest(
                figmaOperation(FigmaDesignOperationStatus.APPLIED, List.of(artifactRef("bundle-1", "a".repeat(64)))),
                "bundle-1",
                thymeleafOperation(ProjectOperationStatus.APPLIED, Map.of("list.html", THYMELEAF_CONTENT)),
                "list.html", "0".repeat(64));

        DesignParityResult result = useCase.validate(request);
        assertThat(result.status()).isEqualTo(DesignParityStatus.MISMATCH);
        assertThat(result.issues().get(0)).contains("THYMELEAF_CONTENT_HASH_MISMATCH");
    }

    @Test
    void neverReturnsVerified_whenNoEvidenceProvidedAtAll() {
        // 옛 Bridge Tool의 회귀 방지: figmaOperation/artifactId/thymeleafOperation/path/hash 중
        // 어느 하나라도 비어 있으면 VERIFIED가 나올 수 없다.
        var allMissing = new DesignParityRequest(null, null, null, null, null);
        assertThat(useCase.validate(allMissing).status()).isNotEqualTo(DesignParityStatus.VERIFIED);
    }
}
