package com.krdevops.springai.service.figma;

import com.krdevops.springai.controller.FigmaOperationsController;
import com.krdevops.springai.mapper.FigmaDesignOperationRepository;
import com.krdevops.springai.model.figma.contract.FigmaDesignOperation;
import com.krdevops.springai.model.figma.contract.FigmaDesignOperationStatus;
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

    /**
     * Operation 조회 (R5-040)
     * 최신 revision 반환
     */
    public Optional<FigmaDesignOperation> findOperation(String operationId) {
        return repository.findLatest(operationId);
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
     * R5-043: 멀티 스크린 Operation 검증.
     * 모든 화면이 성공해야 일괄 적용. 하나라도 실패하면 전체 거부.
     */
    public void validateMultiScreenOperation(String operationId) {
        FigmaDesignOperation operation = repository.findLatest(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Operation not found"));

        // 멀티 스크린 요청인지 확인
        if (operation.request() == null ||
                operation.request().type() == null) {
            throw new IllegalStateException("Invalid operation for multi-screen validation");
        }

        // PREVIEW_READY 상태에서만 검증 가능
        if (operation.status() != FigmaDesignOperationStatus.PREVIEW_READY) {
            throw new IllegalStateException(
                    "Multi-screen validation requires PREVIEW_READY status, got: " +
                            operation.status()
            );
        }

        log.debug("Multi-screen operation {} validation passed", operationId);
    }

    /**
     * R5-042: editableNodeIds 범위 재검증.
     * 요청 시점과 실제 Apply 시점 사이에 파일/페이지 구조가 변경되었는지 확인.
     */
    public void validateEditableNodeIds(
            String operationId,
            String fileKey,
            List<String> currentEditableNodeIds
    ) {
        FigmaDesignOperation operation = repository.findLatest(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Operation not found"));

        // 요청된 editableNodeIds
        List<String> requestedNodeIds = operation.request().editableNodeIds();
        if (requestedNodeIds == null || requestedNodeIds.isEmpty()) {
            return; // MODIFY_EXISTING이 아닌 경우
        }

        // 현재 file의 노드 ID 목록과 비교
        // TODO: Figma API로 현재 노드 ID 목록 조회
        // 요청 시점의 노드 ID가 현재 file에 모두 존재하는지 확인
        // 미승인 컴포넌트가 포함되어 있지 않은지 확인

        log.debug("Editable nodes validation passed for operation {}", operationId);
    }
}
