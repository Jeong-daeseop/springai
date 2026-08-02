package com.krdevops.springai.service.parity;

import com.krdevops.springai.model.contract.ArtifactRef;
import com.krdevops.springai.model.figma.contract.FigmaDesignOperation;
import com.krdevops.springai.model.figma.contract.FigmaDesignOperationStatus;
import com.krdevops.springai.model.parity.DesignParityRequest;
import com.krdevops.springai.model.parity.DesignParityResult;
import com.krdevops.springai.model.parity.DesignParityStatus;
import com.krdevops.springai.model.thymeleaf.ProjectOperationStatus;
import com.krdevops.springai.model.thymeleaf.ThymeleafProjectOperation;
import com.krdevops.springai.service.contract.OperationHashFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Set;

/**
 * ARCH-0207/0208/0209 (WP2 재구현): Figma Bundle Artifact와 Thymeleaf Render Artifact의
 * evidence 기반 parity 검증.
 *
 * <p><b>범위 밖:</b> Figma 디자인과 렌더된 HTML의 실제 시각적/구조적 일치(Component·Token·
 * Layout 비교)는 이 UseCase가 판단하지 않는다 — 서로 다른 표현 형식(디자인 그래프 vs HTML)을
 * 단순 hash 비교로 판정할 수 없기 때문이다. 그 판정은 계획서 §14(ARCH-WP8)의 실제 렌더링·
 * 스크린샷·axe 기반 Validation Gate 몫이며, 이 UseCase는 두 Artifact가 실제로 존재하고
 * 승인 가능한 상태이며 호출자가 명시적으로 선언한 hash와 일치하는지만 검증한다 — 즉 기존
 * {@code FigmaThymeleafBridgeTool.validateThymeleafDesignParity}가 항상 반환하던 근거 없는
 * {@code VERIFIED}를 제거하고, evidence가 없으면 VERIFIED로 전이할 수 없게 만드는 것이 목적이다.
 */
@Service
public class DesignParityValidationUseCase {

    private static final Set<FigmaDesignOperationStatus> FIGMA_EVIDENCE_READY = EnumSet.of(
            FigmaDesignOperationStatus.PREVIEW_READY,
            FigmaDesignOperationStatus.APPLY_REQUIRED,
            FigmaDesignOperationStatus.APPLIED);

    private static final Set<ProjectOperationStatus> THYMELEAF_EVIDENCE_READY = EnumSet.of(
            ProjectOperationStatus.APPROVED,
            ProjectOperationStatus.APPLIED,
            ProjectOperationStatus.VALIDATED);

    private final OperationHashFactory hashFactory;

    public DesignParityValidationUseCase(OperationHashFactory hashFactory) {
        this.hashFactory = hashFactory;
    }

    public DesignParityResult validate(DesignParityRequest request) {
        FigmaDesignOperation figmaOperation = request.figmaOperation();
        ThymeleafProjectOperation thymeleafOperation = request.thymeleafOperation();

        if (figmaOperation == null) {
            return DesignParityResult.of(DesignParityStatus.MISMATCH, "FIGMA_OPERATION_MISSING");
        }
        if (thymeleafOperation == null) {
            return DesignParityResult.of(DesignParityStatus.MISMATCH, "THYMELEAF_OPERATION_MISSING");
        }

        if (!FIGMA_EVIDENCE_READY.contains(figmaOperation.status())) {
            return DesignParityResult.of(DesignParityStatus.CONFLICT,
                    "FIGMA_OPERATION_NOT_EVIDENCE_READY: status=" + figmaOperation.status());
        }
        if (!THYMELEAF_EVIDENCE_READY.contains(thymeleafOperation.status())) {
            return DesignParityResult.of(DesignParityStatus.CONFLICT,
                    "THYMELEAF_OPERATION_NOT_EVIDENCE_READY: status=" + thymeleafOperation.status());
        }

        ArtifactRef figmaArtifact = findFigmaArtifact(figmaOperation, request.figmaArtifactId());
        if (figmaArtifact == null) {
            return DesignParityResult.of(DesignParityStatus.MISMATCH,
                    "FIGMA_ARTIFACT_NOT_FOUND: " + request.figmaArtifactId());
        }

        String thymeleafContent = thymeleafOperation.previewArtifacts().get(request.thymeleafRelativePath());
        if (thymeleafContent == null) {
            return DesignParityResult.of(DesignParityStatus.MISMATCH,
                    "THYMELEAF_ARTIFACT_NOT_FOUND: " + request.thymeleafRelativePath());
        }

        String expectedHash = request.expectedThymeleafContentHash();
        if (expectedHash == null || expectedHash.isBlank()) {
            return DesignParityResult.of(DesignParityStatus.UNSUPPORTED,
                    "EXPECTED_HASH_NOT_PROVIDED: 시각적 Component/Token/Layout 비교는 "
                    + "ARCH-WP8 Validation Gate(render/screenshot/axe) 대상이며, 이 UseCase는 "
                    + "호출자가 선언한 hash와의 evidence 기반 비교만 수행합니다.");
        }

        String actualHash = hashFactory.sha256Hex(thymeleafContent.getBytes(StandardCharsets.UTF_8));
        if (!actualHash.equals(expectedHash)) {
            return DesignParityResult.of(DesignParityStatus.MISMATCH,
                    "THYMELEAF_CONTENT_HASH_MISMATCH: expected=" + expectedHash + " actual=" + actualHash);
        }

        return DesignParityResult.verified(figmaArtifact.artifactId());
    }

    private ArtifactRef findFigmaArtifact(FigmaDesignOperation operation, String artifactId) {
        if (artifactId == null || artifactId.isBlank() || operation.artifacts() == null) {
            return null;
        }
        return operation.artifacts().stream()
                .filter(ref -> artifactId.equals(ref.artifactId()))
                .findFirst()
                .orElse(null);
    }
}
