package com.krdevops.springai.controller;

import com.krdevops.springai.mapper.FigmaScreenSpecRepository;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import com.krdevops.springai.model.figma.ops.FigmaGenerationReport;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementPatchSet;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementPreview;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementStatus;
import com.krdevops.springai.service.figma.FigmaOperationsService;
import com.krdevops.springai.service.figma.FigmaRefinementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * MR-A: Manual Refinement Patch Set REST API. `Capture → Preview → Approve/Reject`
 * 흐름의 서버 진입점이다(MR-DEC-03). 승인·반려는 이 Controller가 아니라
 * {@code SecurityConfig.requiredScopeFor}가 Plugin 단기 Token으로는 통과시키지 않는다(MR-DEC-05).
 */
@RestController
@RequestMapping("/api/figma/refinements")
@RequiredArgsConstructor
public class FigmaRefinementController {

    private final FigmaRefinementService refinementService;
    private final FigmaScreenSpecRepository screenSpecRepository;
    private final FigmaOperationsService operationsService;

    @PostMapping("/preview")
    public FigmaRefinementPreview preview(@Valid @RequestBody FigmaRefinementPatchSet candidate) {
        MaterializedScreen screen = latestScreen(candidate.screenId());
        boolean baseHashMatches = matchesBaseline(candidate, screen);
        return refinementService.preview(candidate, screen.content(), baseHashMatches);
    }

    /**
     * Plugin이 계산한 Diff를 CAPTURED로 저장한다. 저장 직후 즉시 Preview를 계산해 차단·충돌이
     * 하나도 없으면 REVIEW_REQUIRED로 자동 전환한다(MR-DEC-01). 차단·충돌이 있으면 CAPTURED에
     * 머물며, Plugin이 사유를 보고 다시 Capture하거나 사람이 직접 확인해야 한다.
     */
    @PostMapping("/capture")
    public FigmaRefinementPatchSet capture(@Valid @RequestBody FigmaRefinementPatchSet candidate) {
        try {
            FigmaRefinementPatchSet saved = refinementService.capture(candidate);
            MaterializedScreen screen = latestScreen(saved.screenId());
            boolean baseHashMatches = matchesBaseline(saved, screen);
            FigmaRefinementPreview preview = refinementService.preview(saved, screen.content(), baseHashMatches);
            if (preview.blocked().isEmpty() && preview.conflicts().isEmpty()) {
                return refinementService.markReviewRequired(saved.patchSetId());
            }
            return saved;
        } catch (IllegalStateException exception) {
            throw new FigmaRefinementConflictException("REFINEMENT_CONFLICT", exception.getMessage());
        }
    }

    @GetMapping("/screens/{screenId}")
    public List<FigmaRefinementPatchSet> byScreen(@PathVariable String screenId) {
        return refinementService.findByScreen(screenId);
    }

    /**
     * MR-Q07: 화면의 승인된(가장 최근) Patch Set과 마지막 Generation Report를 한 번의 호출로
     * 조회한다. Refinement 승인 여부와 실제 적용 결과를 함께 검토할 때 사용한다.
     */
    @GetMapping("/screens/{screenId}/summary")
    public ScreenRefinementSummary summary(@PathVariable String screenId) {
        Optional<FigmaRefinementPatchSet> approved = refinementService.findLatestApprovedByScreen(screenId);
        // reports()는 CREATED_AT DESC로 정렬돼 있으므로 success=true인 첫 항목이 마지막 성공 보고서다.
        FigmaGenerationReport lastSuccessfulReport = operationsService.reports(screenId).stream()
                .filter(FigmaGenerationReport::success)
                .findFirst()
                .orElse(null);
        return new ScreenRefinementSummary(screenId, approved.orElse(null), lastSuccessfulReport);
    }

    @GetMapping("/{patchSetId}")
    public FigmaRefinementPatchSet byId(@PathVariable String patchSetId) {
        return refinementService.findById(patchSetId)
                .orElseThrow(() -> new FigmaResourceNotFoundException(
                        "REFINEMENT_PATCH_SET_NOT_FOUND", "Refinement Patch Set을 찾을 수 없습니다: " + patchSetId));
    }

    /** MR-A05/MR-DEC-05: 운영자(X-API-Key)만 호출 가능 — Plugin 단기 Token으로는 이 경로를 통과할 수 없다. */
    @PostMapping("/{patchSetId}/approve")
    public FigmaRefinementPatchSet approve(
            @PathVariable String patchSetId, @Valid @RequestBody ApprovalRequest request) {
        try {
            return refinementService.approve(patchSetId, request.actor(), request.comment());
        } catch (IllegalStateException exception) {
            throw new FigmaRefinementConflictException(conflictCode(exception, FigmaRefinementStatus.APPROVED),
                    exception.getMessage());
        }
    }

    @PostMapping("/{patchSetId}/reject")
    public FigmaRefinementPatchSet reject(
            @PathVariable String patchSetId, @Valid @RequestBody ApprovalRequest request) {
        try {
            return refinementService.reject(patchSetId, request.actor(), request.comment());
        } catch (IllegalStateException exception) {
            throw new FigmaRefinementConflictException(conflictCode(exception, FigmaRefinementStatus.REJECTED),
                    exception.getMessage());
        }
    }

    private String conflictCode(IllegalStateException exception, FigmaRefinementStatus target) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (message.startsWith("REFINEMENT_CONCURRENT_TRANSITION")) return "REFINEMENT_CONFLICT";
        return "REFINEMENT_NOT_APPROVED";
    }

    private MaterializedScreen latestScreen(String screenId) {
        FigmaScreenSpec spec = screenSpecRepository.findLatest(screenId)
                .orElseThrow(() -> new FigmaResourceNotFoundException(
                        "FIGMA_SCREEN_SPEC_NOT_FOUND", "화면을 찾을 수 없습니다: " + screenId));
        return new MaterializedScreen(spec.content(), refinementService.computeMaterializationHash(spec.content()));
    }

    private boolean matchesBaseline(FigmaRefinementPatchSet candidate, MaterializedScreen screen) {
        return Objects.equals(candidate.baseMaterializationHash(), screen.hash());
    }

    private record MaterializedScreen(FigmaNodeSpec content, String hash) {}

    public record ApprovalRequest(String actor, String comment) {}

    /** MR-Q07: 화면 하나의 승인 Patch Set과 마지막 성공 Generation Report 조합 조회 결과. */
    public record ScreenRefinementSummary(
            String screenId,
            FigmaRefinementPatchSet approvedPatchSet,
            FigmaGenerationReport lastSuccessfulReport
    ) {}
}
