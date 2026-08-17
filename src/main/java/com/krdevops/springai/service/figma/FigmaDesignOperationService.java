package com.krdevops.springai.service.figma;

import com.krdevops.springai.controller.FigmaOperationsController;
import com.krdevops.springai.mapper.FigmaDesignOperationRepository;
import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.figma.contract.FigmaDesignOperation;
import com.krdevops.springai.model.figma.contract.FigmaDesignOperationStatus;
import com.krdevops.springai.service.FigmaApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * R5-041: Plugin으로부터 Apply 완료 보고서를 수신하고 Operation 상태 전이.
 * MCP 분석(PREVIEW_READY) 및 Plugin Apply 완료(APPLIED) 상태를 엄격하게 분리.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FigmaDesignOperationService {

    private final FigmaDesignOperationRepository repository;
    private final FigmaDesignOperationStateService stateService;
    private final FigmaApiClient figmaApiClient;

    /**
     * Operation 조회 (R5-040)
     * 최신 revision 반환
     */
    public Optional<FigmaDesignOperation> findOperation(String operationId) {
        return repository.findLatest(operationId);
    }

    /**
     * R5-041/R6-047: PREVIEW_READY → APPLY_REQUIRED 전이.
     * Plugin이 Apply를 시작하기 직전에 호출한다. 이 호출 없이는 {@link #reportApplied}가
     * (상태가 여전히 PREVIEW_READY라서) 항상 실패한다 — MCP 분석 완료와 Plugin Apply
     * 완료(APPLIED)를 분리하는 3단계 상태(PREVIEW_READY → APPLY_REQUIRED → APPLIED)의 중간 단계.
     */
    public FigmaDesignOperation requestApply(String operationId) {
        FigmaDesignOperation current = repository.findLatest(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Operation not found: " + operationId));
        return repository.appendTransition(
                operationId, FigmaDesignOperationStatus.APPLY_REQUIRED,
                current.sourceRevision(), current.issues(), current.artifacts());
    }

    /**
     * Plugin 적용 완료 보고서 수신 및 상태 전이 (R5-041)
     *
     * 엄격한 상태 검증:
     * 1. APPLY_REQUIRED 상태에서만 APPLIED로 전이 가능
     * 2. pluginReportReceived=true를 반드시 전달 (상태 규칙 강제)
     * 3. 이전 상태 + revision 기반 낙관적 잠금으로 멱등성 보장
     */
    public FigmaDesignOperation reportApplied(
            String operationId,
            FigmaOperationsController.PluginApplyReport report
    ) {
        FigmaDesignOperation current = repository.findLatest(operationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Operation not found: " + operationId
                ));

        // 상태 전이 검증 (Plugin 보고서 필수)
        stateService.assertTransitionToAppliedAllowed(current.status(), true);

        // 요청 시점 editableNodeIds가 Apply 시점에도 유효한지 재검증 (R5-042)
        validateEditableNodeIds(operationId);

        // R5-042: Plugin이 실제로 건드렸다고 보고한 노드가 승인된 editableNodeIds 범위 안인지 확인.
        // 위 검증은 "요청한 노드가 아직 존재하는지"만 보므로, "적용 범위가 요청을 벗어나지 않았는지"는
        // 별도로 막아야 한다. 이 확인이 없으면 Plugin이 승인되지 않은 노드를 수정해도 그대로 APPLIED된다.
        validateAffectedNodesWithinScope(current, report);

        log.info("Transitioning operation {} to APPLIED (screenId={})",
                 operationId, report.screenId());

        // Repository의 transitionToApplied 메서드 사용
        // (이미 revision 관리와 낙관적 잠금이 구현됨)
        return repository.transitionToApplied(
                operationId,
                true,  // pluginReportReceived=true
                current.artifacts()  // artifacts 유지
        );
    }

    /**
     * R5-042: Plugin의 Apply 보고서(affectedNodeIds)가 요청 시점 승인된 editableNodeIds 범위 안에서만
     * 이뤄졌는지 확인한다. MODIFY_EXISTING이 아닌 요청(editableNodeIds 없음)은 범위 제한이 없다.
     */
    private void validateAffectedNodesWithinScope(
            FigmaDesignOperation operation, FigmaOperationsController.PluginApplyReport report) {
        List<String> allowedNodeIds = operation.request().editableNodeIds();
        if (allowedNodeIds == null || allowedNodeIds.isEmpty()) {
            return;
        }
        List<String> affectedNodeIds = report.affectedNodeIds();
        if (affectedNodeIds == null || affectedNodeIds.isEmpty()) {
            return;
        }
        List<String> outOfScope = affectedNodeIds.stream()
                .filter(id -> !allowedNodeIds.contains(id))
                .toList();
        if (outOfScope.isEmpty()) {
            return;
        }

        List<GenerationIssue> issues = outOfScope.stream()
                .map(id -> new GenerationIssue(
                        "FIGMA_EDITABLE_NODE_OUT_OF_SCOPE",
                        GenerationIssue.Severity.ERROR,
                        "EDITABLE_SCOPE_VALIDATION",
                        id,
                        "Plugin이 승인된 editableNodeIds 범위 밖 노드를 적용했습니다: " + id,
                        "editableNodeIds를 갱신하거나 Plugin 적용 범위를 재확인하세요."))
                .toList();
        repository.appendTransition(operation.operationId(), FigmaDesignOperationStatus.CONFLICT,
                operation.sourceRevision(), issues, operation.artifacts());
        throw new IllegalStateException("FIGMA_OPERATION_EDITABLE_SCOPE_CONFLICT: " + outOfScope);
    }

    /**
     * R5-043: MULTI_SCREEN_FLOW Operation이 일괄 Apply를 준비할 수 있는 최소 형태 검증(모든 화면
     * 이름이 채워졌는지, PREVIEW_READY까지 왔는지). 실제 "화면별 Bundle 일괄 Preview·부분 실패
     * rollback"의 대부분은 Plugin(figma-screen-spec-plugin/src/core.ts의
     * {@code planMultiScreenApply}/{@code applyMultiScreenBundles})이 화면별 Bundle 단위로 수행한다
     * — 이 메서드는 아직 아무 곳에서도 호출하지 않는다. MULTI_SCREEN_FLOW 요청이
     * {@link FigmaDesignOrchestrationService#processExplicitRequest}에서 화면별 Bundle을 생성해
     * PREVIEW_READY까지 도달하는 경로(R6-036 잔여 범위) 자체가 아직 없어서, 이 검증을 지금
     * requestApply()에 걸면 존재하지 않는 상태를 항상 거부하는 죽은 Gate가 되기 때문이다.
     * 그 생성 경로가 만들어지면 이 메서드를 requestApply()에서 호출해 화면 수만큼의
     * FIGMA_EXPORT_BUNDLE artifact가 모두 있는지까지 검증을 넓혀야 한다.
     */
    public void validateMultiScreenOperation(String operationId) {
        FigmaDesignOperation operation = repository.findLatest(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Operation not found"));

        if (operation.request() == null
                || operation.request().type() != com.krdevops.springai.model.figma.contract.FigmaDesignRequestType.MULTI_SCREEN_FLOW
                || operation.request().screens() == null
                || operation.request().screens().isEmpty()) {
            throw new IllegalStateException(
                    "Invalid operation for multi-screen validation: MULTI_SCREEN_FLOW with non-empty screens required");
        }

        // PREVIEW_READY 상태에서만 검증 가능
        if (operation.status() != FigmaDesignOperationStatus.PREVIEW_READY) {
            throw new IllegalStateException(
                    "Multi-screen validation requires PREVIEW_READY status, got: " +
                            operation.status()
            );
        }

        log.debug("Multi-screen operation {} validation passed ({} screens)",
                operationId, operation.request().screens().size());
    }

    /**
     * R5-042: editableNodeIds 범위 재검증.
     * 요청 시점과 실제 Apply 시점 사이에 파일/페이지 구조가 변경되었는지 확인한다.
     * 누락 노드가 있으면 Operation을 CONFLICT로 전이한 뒤 예외를 던진다.
     */
    public void validateEditableNodeIds(String operationId) {
        FigmaDesignOperation operation = repository.findLatest(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Operation not found: " + operationId));

        List<String> requestedNodeIds = operation.request().editableNodeIds();
        if (requestedNodeIds == null || requestedNodeIds.isEmpty()) {
            return; // MODIFY_EXISTING이 아닌 경우
        }

        // Figma 연동이 꺼져 있으면 검증할 방법 자체가 없다. 여기서 실패시키면 MODIFY_EXISTING
        // Operation이 APPLY_REQUIRED에 갇혀 APPLIED로 영영 못 가므로(생성 경로는 Figma 접근 없이도
        // APPLY_REQUIRED까지 올라간다), 검증을 건너뛰고 경고만 남긴다.
        if (!figmaApiClient.isFigmaEnabled()) {
            log.warn("Figma 연동이 비활성이라 operation {}의 editable scope 재검증을 건너뜁니다 "
                    + "(요청 노드 {}개).", operationId, requestedNodeIds.size());
            return;
        }

        // 노드 수만큼 왕복하지 않도록 한 번의 GET으로 존재 여부를 확인한다.
        List<String> missing = figmaApiClient.findMissingNodeIds(
                operation.request().fileKey(), requestedNodeIds);
        if (missing.isEmpty()) {
            log.debug("Editable nodes validation passed for operation {}", operationId);
            return;
        }

        List<GenerationIssue> issues = missing.stream()
                .map(id -> new GenerationIssue(
                        "FIGMA_EDITABLE_NODE_MISSING",
                        GenerationIssue.Severity.ERROR,
                        "EDITABLE_SCOPE_VALIDATION",
                        id,
                        "editableNodeIds에 포함된 노드가 현재 Figma 파일에 존재하지 않습니다: " + id,
                        "요청을 다시 생성하거나 editableNodeIds를 갱신하세요."))
                .toList();
        repository.appendTransition(operationId, FigmaDesignOperationStatus.CONFLICT,
                operation.sourceRevision(), issues, operation.artifacts());
        throw new IllegalStateException("FIGMA_OPERATION_EDITABLE_SCOPE_CONFLICT: " + missing);
    }
}
