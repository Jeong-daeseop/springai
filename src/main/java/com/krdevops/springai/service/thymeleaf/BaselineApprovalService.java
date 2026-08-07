package com.krdevops.springai.service.thymeleaf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.observability.ObservabilityContextHolder;
import com.krdevops.springai.model.thymeleaf.BaselineApprovalRequest;
import com.krdevops.springai.model.thymeleaf.BaselineApprovalResult;
import com.krdevops.springai.model.thymeleaf.BrowserGateStatus;
import com.krdevops.springai.model.thymeleaf.BrowserValidationReport;
import com.krdevops.springai.model.thymeleaf.BrowserValidationRequest;
import com.krdevops.springai.model.thymeleaf.BrowserViewportResult;
import com.krdevops.springai.model.thymeleaf.ProjectOperationStatus;
import com.krdevops.springai.model.thymeleaf.ThymeleafOperationSnapshot;
import com.krdevops.springai.service.artifact.ArtifactService;
import com.krdevops.springai.service.contract.OperationHashFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WP8 ARCH-0810: "지금 렌더된 화면을 시각 비교 기준으로 삼는다"는 명시적 승인 진입점.
 *
 * <p>이 서비스 없이는 baseline 파일이 생길 길이 전혀 없어 {@code VISUAL_PARITY} Gate가 언제나
 * {@code BASELINE_MISSING}으로 BLOCK된다. 자동 승격은 하지 않는다 — 승인은 반드시 사람이 이
 * 진입점을 호출해야 일어난다.
 *
 * <p>렌더는 {@link BrowserValidationGateExecutor#validate}를 그대로 재사용한다. 승인 전용 렌더
 * 경로를 따로 두면 재검증과 승인이 서로 다른 이미지를 만들어 diff가 영구히 어긋난다.
 */
@Service
public class BaselineApprovalService {

    private static final String APPROVAL_SCHEMA_VERSION = "thymeleaf-baseline-approval-v1";

    private final ThymeleafOperationStore store;
    private final BrowserValidationGateExecutor browserValidationGate;
    private final BrowserGateDirectoryResolver directories;
    private final OperationHashFactory hashFactory;
    private final ObjectMapper objectMapper;
    private final ArtifactService artifactService;

    public BaselineApprovalService(
            ThymeleafOperationStore store,
            BrowserValidationGateExecutor browserValidationGate,
            BrowserGateDirectoryResolver directories,
            OperationHashFactory hashFactory,
            ObjectMapper objectMapper,
            ArtifactService artifactService) {
        this.store = store;
        this.browserValidationGate = browserValidationGate;
        this.directories = directories;
        this.hashFactory = hashFactory;
        this.objectMapper = objectMapper;
        this.artifactService = artifactService;
    }

    public BaselineApprovalResult approve(BaselineApprovalRequest request) {
        ThymeleafOperationSnapshot snapshot = store.findLatest(request.operationId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "THYMELEAF_OPERATION_NOT_FOUND: " + request.operationId()));
        ProjectOperationStatus status = snapshot.operation().status();
        if (status != ProjectOperationStatus.APPLIED && status != ProjectOperationStatus.VALIDATED) {
            throw new IllegalStateException("THYMELEAF_BASELINE_APPROVAL_REQUIRES_APPLIED: " + status);
        }
        if (request.relativeFile() != null && !request.relativeFile().isBlank()
                && !snapshot.operation().previewArtifacts().containsKey(request.relativeFile())) {
            throw new IllegalArgumentException(
                    "THYMELEAF_BASELINE_FILE_NOT_IN_OPERATION: " + request.relativeFile());
        }

        Path projectRoot = Path.of(snapshot.projectRoot());
        Path artifactDirectory = directories.artifactDirectory(projectRoot, request.operationId());
        Path baselineDirectory = directories.baselineDirectory(projectRoot);
        BrowserValidationReport report = browserValidationGate.validate(new BrowserValidationRequest(
                request.screenId(), request.url(), request.renderedHtml(),
                artifactDirectory, baselineDirectory, request.maskSelectors(), request.readySelector(),
                BrowserValidationRequest.DEFAULT_MAX_DIFFERENCE_RATIO,
                BrowserValidationRequest.DEFAULT_TIMEOUT_MILLIS));

        List<Candidate> candidates = collectCandidates(request, report, artifactDirectory);
        List<BaselineApprovalResult.ViewportApproval> approvals = new ArrayList<>();
        for (Candidate candidate : candidates) {
            approvals.add(promote(request, snapshot, baselineDirectory, candidate));
        }
        return new BaselineApprovalResult(request.operationId(), request.screenId(), approvals);
    }

    /**
     * 파일을 하나라도 쓰기 전에 요청한 모든 viewport의 렌더 성공과 이미지 읽기를 끝낸다 —
     * 부분 승인은 baseline 집합을 서로 다른 렌더 시점이 섞인 상태로 만들어 이후 diff를 신뢰할 수
     * 없게 하므로 all-or-nothing이어야 한다.
     */
    private List<Candidate> collectCandidates(
            BaselineApprovalRequest request, BrowserValidationReport report, Path artifactDirectory) {
        Map<String, BrowserViewportResult> byName = new LinkedHashMap<>();
        report.viewports().forEach(viewport -> byName.put(viewport.name(), viewport));
        List<String> requested = request.viewports().isEmpty()
                ? List.copyOf(byName.keySet()) : request.viewports();
        if (requested.isEmpty()) {
            throw new IllegalStateException("THYMELEAF_BASELINE_CANDIDATE_RENDER_FAILED: 렌더 결과가 없습니다.");
        }

        List<Candidate> candidates = new ArrayList<>();
        for (String name : requested) {
            BrowserViewportResult viewport = byName.get(name);
            if (viewport == null || viewport.renderStatus() != BrowserGateStatus.PASSED
                    || viewport.screenshotPath() == null) {
                throw new IllegalStateException(
                        "THYMELEAF_BASELINE_CANDIDATE_RENDER_FAILED: " + name);
            }
            candidates.add(new Candidate(name, readScreenshot(viewport, artifactDirectory)));
        }
        return candidates;
    }

    /** runner가 돌려준 경로라도 승인 대상은 이번 렌더의 artifact 디렉터리 안이어야만 한다. */
    private byte[] readScreenshot(BrowserViewportResult viewport, Path artifactDirectory) {
        Path screenshot = Path.of(viewport.screenshotPath()).toAbsolutePath().normalize();
        if (!screenshot.startsWith(artifactDirectory.toAbsolutePath().normalize())) {
            throw new SecurityException("THYMELEAF_BASELINE_SCREENSHOT_PATH_ESCAPE: " + viewport.screenshotPath());
        }
        try {
            return Files.readAllBytes(screenshot);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "THYMELEAF_BASELINE_CANDIDATE_RENDER_FAILED: " + viewport.name(), exception);
        }
    }

    private BaselineApprovalResult.ViewportApproval promote(
            BaselineApprovalRequest request, ThymeleafOperationSnapshot snapshot,
            Path baselineDirectory, Candidate candidate) {
        Path target = baselineDirectory.resolve(request.screenId() + "-" + candidate.viewport() + ".png");
        String previousContentHash = existingHash(target);
        String newContentHash = hashFactory.sha256Hex(candidate.png());
        writeAtomically(baselineDirectory, target, candidate.png());

        String baselineArtifactId = null;
        String approvalArtifactId = null;
        if (artifactService != null) {
            baselineArtifactId = artifactService.ingestAndLink(candidate.png(), "image/png",
                    "THYMELEAF_VISUAL_BASELINE", snapshot.designRevision(),
                    request.operationId(), "THYMELEAF_PROJECT").artifactId();
            byte[] metadata = approvalMetadata(request, snapshot, candidate.viewport(),
                    baselineArtifactId, newContentHash, previousContentHash);
            approvalArtifactId = artifactService.ingestAndLink(metadata, "application/json",
                    "THYMELEAF_BASELINE_APPROVAL", snapshot.designRevision(),
                    request.operationId(), "THYMELEAF_PROJECT").artifactId();
        }
        return new BaselineApprovalResult.ViewportApproval(candidate.viewport(), newContentHash,
                previousContentHash, baselineArtifactId, approvalArtifactId);
    }

    private String existingHash(Path target) {
        if (!Files.isRegularFile(target)) return null;
        try {
            return hashFactory.sha256Hex(Files.readAllBytes(target));
        } catch (IOException exception) {
            throw new UncheckedIOException("기존 baseline을 읽지 못했습니다: " + target, exception);
        }
    }

    /** 재검증이 반쯤 쓰인 PNG를 읽는 일이 없도록 같은 디렉터리 임시 파일 → atomic move로 교체한다. */
    private void writeAtomically(Path baselineDirectory, Path target, byte[] png) {
        Path staged = null;
        try {
            Files.createDirectories(baselineDirectory);
            staged = Files.createTempFile(baselineDirectory, "baseline-", ".png.tmp");
            Files.write(staged, png);
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            staged = null;
        } catch (IOException exception) {
            throw new UncheckedIOException("baseline 저장에 실패했습니다: " + target, exception);
        } finally {
            if (staged != null) {
                try {
                    Files.deleteIfExists(staged);
                } catch (IOException ignored) {
                    // 임시 파일 정리 실패는 원래 예외를 가리지 않는다.
                }
            }
        }
    }

    private byte[] approvalMetadata(
            BaselineApprovalRequest request, ThymeleafOperationSnapshot snapshot, String viewport,
            String baselineArtifactId, String newContentHash, String previousContentHash) {
        // ARCH-0707과 동일한 이유로 actor는 실제 principal이 없으면 "system"으로 고정한다.
        String actor = ObservabilityContextHolder.current().actorId();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("schemaVersion", APPROVAL_SCHEMA_VERSION);
        metadata.put("operationId", request.operationId());
        metadata.put("screenId", request.screenId());
        metadata.put("relativeFile", request.relativeFile());
        metadata.put("viewport", viewport);
        metadata.put("projectRootKey", directories.projectKey(Path.of(snapshot.projectRoot())));
        metadata.put("designRevision", snapshot.designRevision());
        metadata.put("baselineArtifactId", baselineArtifactId);
        metadata.put("newContentHash", newContentHash);
        metadata.put("previousContentHash", previousContentHash);
        metadata.put("approvedAt", Instant.now().toString());
        metadata.put("actor", actor == null ? "system" : actor);
        try {
            return objectMapper.writeValueAsBytes(metadata);
        } catch (IOException exception) {
            throw new IllegalStateException("baseline 승인 메타데이터 직렬화에 실패했습니다.", exception);
        }
    }

    private record Candidate(String viewport, byte[] png) { }
}
