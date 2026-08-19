package com.krdevops.springai.tools;

import com.krdevops.springai.config.mcp.McpToolRisk;
import com.krdevops.springai.config.mcp.McpToolRiskLevel;

import com.krdevops.springai.model.figma.contract.FigmaDesignOperation;
import com.krdevops.springai.model.figma.contract.FigmaDesignRequest;
import com.krdevops.springai.model.figma.contract.FigmaScreenRequest;
import com.krdevops.springai.service.figma.FigmaDesignOrchestrationService;
import com.krdevops.springai.service.figma.FigmaPlatformConversionService;
import com.krdevops.springai.service.figma.FigmaToolAuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * R6-032~038: Figma 디자인 7가지 요청 MCP Callback.
 * 각 요청 타입별 전용 메서드로 FigmaDesignOrchestrationService를 호출.
 * 결과는 FigmaDesignOperation으로 반환 (operationId, status, issues 포함).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FigmaDesignOrchestrationTool {

    private final FigmaDesignOrchestrationService orchestrationService;
    private final FigmaToolAuthorizationService authorization;
    private final ObjectMapper objectMapper;
    private final FigmaPlatformConversionService platformConversionService;

    /**
     * R6-032: 텍스트 설명으로 새 화면 생성.
     * 자연어 프롬프트 → DB Schema 기반 ScreenSpecification → FigmaScreenSpec → Bundle/Operation.
     *
     * 입력: 자유 텍스트 프롬프트 (예: "사용자 목록을 표시하는 목록 화면 만들어줘")
     * 출력: FigmaDesignOperation (operationId, status=PREVIEW_READY, issues)
     */
    @McpToolRisk(McpToolRiskLevel.APPLY)
    @Tool(description = """
            텍스트 설명으로 새 Figma 화면을 생성합니다.
            자연어 프롬프트를 분석하여 자동으로 요청 유형을 감지한 후 처리합니다.

            입력:
            - prompt: 화면 설명 (예: "사용자 목록, 검색, 페이지네이션 포함")
            - fileKey: Figma 파일 Key (예: "mVy5h1UbORVqQoBm8Wr1bT")

            출력:
            - operationId: 작업 식별자
            - status: ANALYZED (화면명세 승인 대기) 또는 REJECTED
            - issues: 문제 목록 (confidence 미달 시 분류 오류)

            주의: 승인된 ScreenSpecification을 별도 Bundle 생성 Tool에 전달해야 PREVIEW_READY로 전이합니다.
            """)
    public String createDesignFromText(String figmaMcpSecret, String prompt, String fileKey) {
        authorization.authorize(figmaMcpSecret);
        log.info("Processing TEXT_DESCRIPTION: prompt length={}, fileKey={}", prompt.length(), fileKey);
        FigmaDesignOperation operation = orchestrationService.processTextRequest(prompt, fileKey);
        return serializeOperation(operation);
    }

    /**
     * R6-033: 기존 Figma 화면 참조하여 새 화면 생성.
     * 참조 화면(Node) → 스타일/레이아웃 분석 → 새 ScreenSpecification → FigmaScreenSpec.
     */
    @McpToolRisk(McpToolRiskLevel.APPLY)
    @Tool(description = """
            기존 Figma 화면을 참조하여 새 화면을 생성합니다.
            참조 화면의 스타일, 레이아웃, 컴포넌트를 분석하여 유사한 새 화면을 생성합니다.

            입력:
            - prompt: 새 화면 설명 (예: "기존 사용자 목록처럼 만들되 더 간단하게")
            - fileKey: Figma 파일 Key
            - referenceNodeIds: 참조할 노드 ID 목록 (예: ["node-123", "node-456"])
            - database, tableName: 실제 Bundle까지 생성하려면 필요(DB 스키마 기반 CRUD 생성 아키텍처와 통일).
              생략해도 generateFigmaBundleForOperation 호출 시 분석은 그대로 실행되고 AWAITING_TABLE_BINDING
              으로 전이합니다(REFERENCE_STYLE·IMAGE_REFERENCE 2종에만 해당하는 예외 경로 — 다른 5종
              요청 유형은 database/tableName 없이 generateFigmaBundleForOperation을 호출하면 즉시
              REJECTED됩니다). issues의 FIELD_CANDIDATE 항목으로 필드 후보를 확인한 뒤
              bindFigmaDesignRequestTable로 테이블을 지정해 이어갈 수 있습니다.
            - screenName, featureType: 선택. 생략 시 테이블명·표준 CRUD로 추론됩니다.

            출력: FigmaDesignOperation (ANALYZED — generateFigmaBundleForOperation으로 다음 단계 진행)

            주의: referenceNodeIds가 필수입니다.
            """)
    public String createDesignFromReference(
            String figmaMcpSecret, String prompt, String fileKey, List<String> referenceNodeIds,
            @Nullable String database, @Nullable String tableName,
            @Nullable String screenName, @Nullable String featureType) {
        authorization.authorize(figmaMcpSecret);
        log.info("Processing REFERENCE_STYLE: referenceNodeIds count={}", referenceNodeIds.size());
        FigmaDesignRequest request = (database == null || database.isBlank())
                ? FigmaDesignRequest.referenceStyle(prompt, fileKey, referenceNodeIds)
                : FigmaDesignRequest.referenceStyle(
                        prompt, fileKey, referenceNodeIds, database, tableName, screenName, featureType);
        FigmaDesignOperation operation = orchestrationService.processExplicitRequest(request);
        return serializeOperation(operation);
    }

    /**
     * R6-034: 기존 화면 수정.
     * 기존 화면 Node → 자연어 수정 요청 분석 → ScreenSpecification 수정 → FigmaScreenSpec 갱신.
     */
    @McpToolRisk(McpToolRiskLevel.APPLY)
    @Tool(description = """
            기존 Figma 화면을 수정합니다.
            지정한 노드 ID 범위 내에서만 수정을 허용하며, 승인되지 않은 컴포넌트는 차단합니다.

            입력:
            - prompt: 수정 요청 (예: "버튼 색상을 파란색으로, 텍스트는 더 크게") — 참고용 텍스트일 뿐,
              자유 텍스트를 필드 단위 변경으로 파싱하는 기능은 없습니다.
            - fileKey: Figma 파일 Key
            - editableNodeIds: 수정 가능 노드 ID 목록 (예: ["1:2", "3:4"])
            - screenSpecificationId: 재생성할 기존 화면명세 ID(필수 — nodeId→화면명세 자동 매핑이 없어
              직접 지정해야 합니다). 생략하면 ANALYZED에서 멈춥니다.

            출력: FigmaDesignOperation

            주의:
            - editableNodeIds가 필수입니다.
            - nodeId는 Figma 표기(`1:2`, URL의 `1-2`도 허용)만 받습니다. 다른 형식은 요청 시점에 거부됩니다.
            - Apply 전에 editableNodeIds 범위가 현재 file/page와 일치하는지 재검증합니다.
            - generateFigmaBundleForOperation은 현재 DB 스키마 기준으로 화면명세를 재검증·재생성할 뿐,
              prompt의 자연어 지시(색상 변경 등)는 반영하지 않습니다.
            """)
    public String modifyExistingDesign(
            String figmaMcpSecret, String prompt, String fileKey, List<String> editableNodeIds,
            @Nullable String screenSpecificationId) {
        authorization.authorize(figmaMcpSecret);
        log.info("Processing MODIFY_EXISTING: editableNodeIds count={}", editableNodeIds.size());
        FigmaDesignRequest request = (screenSpecificationId == null || screenSpecificationId.isBlank())
                ? FigmaDesignRequest.modifyExisting(prompt, fileKey, editableNodeIds)
                : FigmaDesignRequest.modifyExisting(prompt, fileKey, editableNodeIds, screenSpecificationId);
        FigmaDesignOperation operation = orchestrationService.processExplicitRequest(request);
        return serializeOperation(operation);
    }

    /**
     * R6-035: 이미지/스크린샷 참조하여 화면 생성.
     * 이미지 Node (export) → Vision 분석 → ScreenSpecification → FigmaScreenSpec.
     */
    @McpToolRisk(McpToolRiskLevel.APPLY)
    @Tool(description = """
            이미지 또는 스크린샷을 참조하여 Figma 화면을 생성합니다.
            이미지의 UI/UX 패턴을 분석하여 FigmaScreenSpec으로 변환합니다.

            입력:
            - prompt: 추가 지시사항 (예: "이미지처럼 정확하게, 하지만 색상은 KRDS 기준으로")
            - fileKey: Figma 파일 Key
            - imageNodeIds: Figma 이미지 노드 ID 목록 (export 경로)
            - database, tableName: 실제 Bundle까지 생성하려면 필요(기존 CRUD 생성 아키텍처와 통일).
              생략해도 generateFigmaBundleForOperation 호출 시 이미지 export·분석은 그대로 실행되고
              AWAITING_TABLE_BINDING으로 전이합니다(REFERENCE_STYLE·IMAGE_REFERENCE 2종에만 해당하는
              예외 경로). issues의 FIELD_CANDIDATE 항목으로 필드 후보를 확인한 뒤
              bindFigmaDesignRequestTable로 테이블을 지정해 이어갈 수 있습니다.

            출력: FigmaDesignOperation

            주의:
            - imageNodeIds가 필수입니다.
            - Figma 이미지 export 분석은 Vision capability 지원 필수.
            - generateFigmaBundleForOperation 단계에서 첫 번째 imageNodeId만 실제로 export·분석합니다.
            """)
    public String createDesignFromImage(
            String figmaMcpSecret, String prompt, String fileKey, List<String> imageNodeIds,
            @Nullable String database, @Nullable String tableName,
            @Nullable String screenName, @Nullable String featureType) {
        authorization.authorize(figmaMcpSecret);
        log.info("Processing IMAGE_REFERENCE: imageNodeIds count={}", imageNodeIds.size());
        FigmaDesignRequest request = (database == null || database.isBlank())
                ? FigmaDesignRequest.imageReference(prompt, fileKey, imageNodeIds)
                : FigmaDesignRequest.imageReference(
                        prompt, fileKey, imageNodeIds, database, tableName, screenName, featureType);
        FigmaDesignOperation operation = orchestrationService.processExplicitRequest(request);
        return serializeOperation(operation);
    }

    /**
     * R6-036: 다중 화면 플로우 생성.
     * 화면 목록 → 각각 ScreenSpecification 생성 → 화면 간 flow 관계 설정 → FigmaScreenSpec 번들.
     */
    @McpToolRisk(McpToolRiskLevel.APPLY)
    @Tool(description = """
            여러 화면을 한번에 생성하고 플로우(Navigation)를 설정합니다.
            각 화면이 성공해야 일괄 적용됩니다. 하나라도 실패하면 전체 거부.

            입력:
            - prompt: 플로우 설명 (예: "로그인 → 홈 → 프로필 → 로그아웃 흐름")
            - fileKey: Figma 파일 Key
            - screens: 화면 목록. 각 항목은 name/description/database/tableName을 가지며,
              generateFigmaBundleForOperation으로 실제 Bundle까지 만들려면 화면마다 database·
              tableName이 필요합니다(기존 CRUD 생성 아키텍처와 통일).

            출력: FigmaDesignOperation (여러 화면 포함)

            주의:
            - 모든 화면이 성공해야 PREVIEW_READY로 진행하며, 하나라도 실패하면 전체 REJECTED입니다.
            """)
    public String createMultiScreenFlow(
            String figmaMcpSecret, String prompt, String fileKey, List<FigmaScreenRequest> screens) {
        authorization.authorize(figmaMcpSecret);
        if (screens == null || screens.isEmpty()) {
            throw new IllegalArgumentException("screens는 최소 1개 이상이어야 합니다");
        }
        log.info("Processing MULTI_SCREEN_FLOW: screens count={}", screens.size());
        FigmaDesignRequest request = FigmaDesignRequest.multiScreenFlow(prompt, fileKey, screens);
        FigmaDesignOperation operation = orchestrationService.processExplicitRequest(request);
        return serializeOperation(operation);
    }

    /**
     * R6-037: 특정 컴포넌트 지정하여 화면 생성.
     * 컴포넌트 Logical Type 목록 → Registry 존재 검증 → 컴포넌트 조합 기반 ScreenSpecification.
     */
    @McpToolRisk(McpToolRiskLevel.APPLY)
    @Tool(description = """
            특정 컴포넌트(button, form, table 등)를 지정하여 화면을 생성합니다.
            요청 컴포넌트는 승인된 ComponentRegistry에만 존재해야 합니다.

            입력:
            - prompt: 화면 설명 (예: "버튼, 입력필드, 테이블을 포함한 관리 화면")
            - fileKey: Figma 파일 Key
            - components: 요청 컴포넌트 Logical Type (예: ["krds.button", "krds.textField", "krds.table"])
            - database, tableName: 실제 Bundle까지 생성하려면 필수(기존 CRUD 생성 아키텍처와 통일).

            출력: FigmaDesignOperation

            주의:
            - 요청 컴포넌트는 Registry에서 CURRENT 상태여야 합니다(generateFigmaBundleForOperation
              단계에서 하나라도 해석 실패하면 전체 REJECTED).
            - 미승인 또는 폐기된 컴포넌트는 차단됩니다.
            """)
    public String createDesignWithComponents(
            String figmaMcpSecret, String prompt, String fileKey, List<String> components,
            @Nullable String database, @Nullable String tableName,
            @Nullable String screenName, @Nullable String featureType) {
        authorization.authorize(figmaMcpSecret);
        log.info("Processing COMPONENT_SPECIFIED: components count={}", components.size());
        FigmaDesignRequest request = (database == null || database.isBlank())
                ? FigmaDesignRequest.componentSpecified(prompt, fileKey, components)
                : FigmaDesignRequest.componentSpecified(
                        prompt, fileKey, components, database, tableName, screenName, featureType);
        FigmaDesignOperation operation = orchestrationService.processExplicitRequest(request);
        return serializeOperation(operation);
    }

    /**
     * R6-038: 플랫폼/반응형 변환.
     * 기존 화면명세를 다른 플랫폼 viewport로 export하고 Component Swap을 적용한다.
     */
    @McpToolRisk(McpToolRiskLevel.APPLY)
    @Tool(description = """
            기존 화면을 다른 플랫폼으로 변환합니다 (Desktop ↔ Tablet ↔ Mobile).
            Component 선택(Swap)을 자동 적용합니다.

            입력:
            - prompt: 변환 지시사항 (참고용 텍스트일 뿐, 실제 변환 결과에는 영향 없음)
            - fileKey: Figma 파일 Key
            - sourceNodeIds: 원본 화면을 가리키는 참고용 노드 ID(실제 변환에는 사용하지 않음 — 계약
              호환을 위해 유지)
            - targetPlatform: 목표 플랫폼 (DESKTOP / TABLET / MOBILE)
            - screenSpecificationId: 변환할 기존 화면명세 ID(필수 — nodeId→화면명세 자동 매핑이 없어
              직접 지정해야 합니다). 생략하면 ANALYZED에서 멈춥니다.

            출력: FigmaDesignOperation

            주의:
            - targetPlatform 필수 (대소문자 구분)
            - generateFigmaBundleForOperation 단계에서 screenSpecificationId가 APPROVED 상태여야
              변환됩니다.
            - Grid 폭·열 수 재계산은 지원하지 않습니다(FigmaScreenSpec에 그런 필드가 없음) —
              Component Swap과 viewport 표시만 실제로 반영되며, 결과에 경고 Issue가 남습니다.
            """)
    public String convertPlatform(
            String figmaMcpSecret, String prompt, String fileKey,
            List<String> sourceNodeIds, String targetPlatform, @Nullable String screenSpecificationId) {
        authorization.authorize(figmaMcpSecret);
        log.info("Processing PLATFORM_CONVERT: sourceNodeIds count={}, targetPlatform={}",
                sourceNodeIds.size(), targetPlatform);
        FigmaDesignRequest request = (screenSpecificationId == null || screenSpecificationId.isBlank())
                ? FigmaDesignRequest.platformConvert(prompt, fileKey, sourceNodeIds, targetPlatform)
                : FigmaDesignRequest.platformConvert(
                        prompt, fileKey, sourceNodeIds, targetPlatform, screenSpecificationId);
        FigmaDesignOperation operation = orchestrationService.processExplicitRequest(request);
        return serializeOperation(operation);
    }

    /**
     * R6-032~038(2026-08-18): createDesignFromReference/createDesignFromImage/
     * createDesignWithComponents/modifyExistingDesign/createMultiScreenFlow/convertPlatform이
     * ANALYZED로 저장한 요청을 실제 ScreenSpecification(또는 기존 명세)→FigmaExportBundle까지
     * 이어간다. TEXT_DESCRIPTION은 이미 별도 진입점(createFigmaBundleFromApprovedSpecification)이
     * 있어 대상이 아니다.
     */
    @McpToolRisk(McpToolRiskLevel.APPLY)
    @Tool(description = """
            ANALYZED 상태의 Figma 디자인 Operation에 대해 실제 ScreenSpecification과
            FigmaExportBundle을 생성합니다(REFERENCE_STYLE/IMAGE_REFERENCE/COMPONENT_SPECIFIED/
            MODIFY_EXISTING/MULTI_SCREEN_FLOW/PLATFORM_CONVERT 지원). screenSpecificationId가
            없거나 생성/대상 화면명세가 REVIEW_REQUIRED·미승인이면 REJECTED로 전이하고 issues에
            다음 단계(reviseScreenSpecification 등)를 안내합니다.

            database/tableName 미지정 시 동작은 요청 유형별로 다릅니다:
            - REFERENCE_STYLE·IMAGE_REFERENCE: 분석은 그대로 실행되고 AWAITING_TABLE_BINDING으로
              전이합니다(issues에 필드 후보 포함). bindFigmaDesignRequestTable로 이어가세요.
            - 그 외 4종(COMPONENT_SPECIFIED/MODIFY_EXISTING/MULTI_SCREEN_FLOW/PLATFORM_CONVERT):
              이 예외 경로를 타지 않고 기존과 동일하게 즉시 REJECTED됩니다.

            입력: operationId(createDesignFromReference 등이 반환한 값)
            출력: FigmaDesignOperation (PREVIEW_READY, AWAITING_TABLE_BINDING 또는 REJECTED)
            """)
    public String generateFigmaBundleForOperation(String figmaMcpSecret, String operationId) {
        authorization.authorize(figmaMcpSecret);
        log.info("Generating Figma bundle for operation: {}", operationId);
        try {
            return serializeOperation(orchestrationService.generateBundle(operationId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new IllegalStateException("Bundle 생성 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 22/23번 문서 A-01e: createDesignFromReference/createDesignFromImage가 database/tableName
     * 없이 호출돼 AWAITING_TABLE_BINDING으로 멈춘 Operation에 실제 테이블을 채워 분석을
     * 재실행한다.
     */
    @McpToolRisk(McpToolRiskLevel.APPLY)
    @Tool(description = """
            AWAITING_TABLE_BINDING 상태의 Figma 디자인 Operation에 database/tableName을 채워
            분석을 다시 실행합니다. createDesignFromReference/createDesignFromImage를 database/
            tableName 없이 호출했을 때 발생하는 중간 상태를 이어서 완료하는 용도입니다.

            입력:
            - operationId: createDesignFromReference/createDesignFromImage가 반환한 값
              (status가 AWAITING_TABLE_BINDING이어야 합니다. issues의 FIELD_CANDIDATE 항목으로
              추천 필드 후보를 미리 확인할 수 있습니다)
            - database, tableName: 바인딩할 실제 DB 테이블

            출력: FigmaDesignOperation (PREVIEW_READY 또는 REJECTED — 매칭이 애매하면
            REJECTED와 함께 reviseScreenSpecification 등 수동 경로를 안내합니다)

            주의: AWAITING_TABLE_BINDING 상태가 아닌 Operation에 호출하면 오류로 거부됩니다.
            """)
    public String bindFigmaDesignRequestTable(
            String figmaMcpSecret, String operationId, String database, String tableName) {
        authorization.authorize(figmaMcpSecret);
        log.info("Binding table to operation: operationId={}, database={}, tableName={}",
                operationId, database, tableName);
        try {
            return serializeOperation(orchestrationService.bindTable(operationId, database, tableName));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new IllegalStateException("테이블 바인딩 실패: " + e.getMessage(), e);
        }
    }

    /**
     * R6-046/R5-044: convertPlatform이 실제로 적용할 Desktop/Tablet/Mobile Grid·Navigation·
     * Component Swap 정책을 캔버스에 쓰기 전에 미리 계산해 반환합니다(조회 전용, 부작용 없음).
     * Plugin이 이 결과를 받아 화면에 실제 반영합니다.
     */
    @McpToolRisk(McpToolRiskLevel.READ)
    @Tool(description = """
            targetPlatform(DESKTOP/TABLET/MOBILE)으로 변환할 때 적용될 Grid 폭·열 수·Navigation 방식과,
            logicalTypes로 넘긴 논리 컴포넌트 타입별 Component Swap 결정을 미리 계산해 반환합니다.
            캔버스에 실제로 쓰지 않는 조회 전용 Tool입니다 — Plugin이 결과를 받아 화면에 반영합니다.

            입력:
            - targetPlatform: DESKTOP, TABLET, MOBILE 중 하나
            - logicalTypes: 변환 대상 화면에 있는 논리 컴포넌트 타입 목록 (예: ["krds.table", "krds.button"])

            출력: platform, viewport(width/gridColumns/navigationStyle), componentSwaps(요청 타입별 대체 여부/사유), policyVersion
            """)
    public String previewPlatformConversion(
            String figmaMcpSecret, String targetPlatform, List<String> logicalTypes) {
        authorization.authorize(figmaMcpSecret);
        log.info("Previewing platform conversion: targetPlatform={}, logicalTypes count={}",
                targetPlatform, logicalTypes == null ? 0 : logicalTypes.size());
        FigmaPlatformConversionService.PlatformConversionResult result =
                platformConversionService.convert(targetPlatform, logicalTypes);
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Failed to serialize PlatformConversionResult", e);
            return "{}";
        }
    }

    // ===== Private Methods =====

    private String serializeOperation(FigmaDesignOperation operation) {
        try {
            return objectMapper.writeValueAsString(operation);
        } catch (Exception e) {
            log.error("Failed to serialize FigmaDesignOperation", e);
            return "{}";
        }
    }
}
