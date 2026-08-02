package com.krdevops.springai.tools;

import com.krdevops.springai.model.figma.contract.FigmaDesignOperation;
import com.krdevops.springai.model.figma.contract.FigmaDesignRequest;
import com.krdevops.springai.model.figma.contract.FigmaScreenRequest;
import com.krdevops.springai.service.figma.FigmaDesignOrchestrationService;
import com.krdevops.springai.service.figma.FigmaToolAuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * R6-032: 텍스트 설명으로 새 화면 생성.
     * 자연어 프롬프트 → DB Schema 기반 ScreenSpecification → FigmaScreenSpec → Bundle/Operation.
     *
     * 입력: 자유 텍스트 프롬프트 (예: "사용자 목록을 표시하는 목록 화면 만들어줘")
     * 출력: FigmaDesignOperation (operationId, status=PREVIEW_READY, issues)
     */
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
    @Tool(description = """
            기존 Figma 화면을 참조하여 새 화면을 생성합니다.
            참조 화면의 스타일, 레이아웃, 컴포넌트를 분석하여 유사한 새 화면을 생성합니다.

            입력:
            - prompt: 새 화면 설명 (예: "기존 사용자 목록처럼 만들되 더 간단하게")
            - fileKey: Figma 파일 Key
            - referenceNodeIds: 참조할 노드 ID 목록 (예: ["node-123", "node-456"])

            출력: FigmaDesignOperation (ANALYZED 승인 후보)

            주의: referenceNodeIds가 필수입니다.
            """)
    public String createDesignFromReference(
            String figmaMcpSecret, String prompt, String fileKey, List<String> referenceNodeIds) {
        authorization.authorize(figmaMcpSecret);
        log.info("Processing REFERENCE_STYLE: referenceNodeIds count={}", referenceNodeIds.size());
        FigmaDesignRequest request = FigmaDesignRequest.referenceStyle(prompt, fileKey, referenceNodeIds);
        FigmaDesignOperation operation = orchestrationService.processExplicitRequest(request);
        return serializeOperation(operation);
    }

    /**
     * R6-034: 기존 화면 수정.
     * 기존 화면 Node → 자연어 수정 요청 분석 → ScreenSpecification 수정 → FigmaScreenSpec 갱신.
     */
    @Tool(description = """
            기존 Figma 화면을 수정합니다.
            지정한 노드 ID 범위 내에서만 수정을 허용하며, 승인되지 않은 컴포넌트는 차단합니다.

            입력:
            - prompt: 수정 요청 (예: "버튼 색상을 파란색으로, 텍스트는 더 크게")
            - fileKey: Figma 파일 Key
            - editableNodeIds: 수정 가능 노드 ID 목록 (예: ["node-789", "node-101"])

            출력: FigmaDesignOperation

            주의:
            - editableNodeIds가 필수입니다.
            - Apply 전에 editableNodeIds 범위가 현재 file/page와 일치하는지 재검증합니다.
            """)
    public String modifyExistingDesign(
            String figmaMcpSecret, String prompt, String fileKey, List<String> editableNodeIds) {
        authorization.authorize(figmaMcpSecret);
        log.info("Processing MODIFY_EXISTING: editableNodeIds count={}", editableNodeIds.size());
        FigmaDesignRequest request = FigmaDesignRequest.modifyExisting(prompt, fileKey, editableNodeIds);
        FigmaDesignOperation operation = orchestrationService.processExplicitRequest(request);
        return serializeOperation(operation);
    }

    /**
     * R6-035: 이미지/스크린샷 참조하여 화면 생성.
     * 이미지 Node (export) → Vision 분석 → ScreenSpecification → FigmaScreenSpec.
     */
    @Tool(description = """
            이미지 또는 스크린샷을 참조하여 Figma 화면을 생성합니다.
            이미지의 UI/UX 패턴을 분석하여 FigmaScreenSpec으로 변환합니다.

            입력:
            - prompt: 추가 지시사항 (예: "이미지처럼 정확하게, 하지만 색상은 KRDS 기준으로")
            - fileKey: Figma 파일 Key
            - imageNodeIds: Figma 이미지 노드 ID 목록 (export 경로)

            출력: FigmaDesignOperation

            주의:
            - imageNodeIds가 필수입니다.
            - Figma 이미지 export 분석은 Vision capability 지원 필수.
            """)
    public String createDesignFromImage(
            String figmaMcpSecret, String prompt, String fileKey, List<String> imageNodeIds) {
        authorization.authorize(figmaMcpSecret);
        log.info("Processing IMAGE_REFERENCE: imageNodeIds count={}", imageNodeIds.size());
        FigmaDesignRequest request = FigmaDesignRequest.imageReference(prompt, fileKey, imageNodeIds);
        FigmaDesignOperation operation = orchestrationService.processExplicitRequest(request);
        return serializeOperation(operation);
    }

    /**
     * R6-036: 다중 화면 플로우 생성.
     * 화면 목록 → 각각 ScreenSpecification 생성 → 화면 간 flow 관계 설정 → FigmaScreenSpec 번들.
     */
    @Tool(description = """
            여러 화면을 한번에 생성하고 플로우(Navigation)를 설정합니다.
            각 화면이 성공해야 일괄 적용됩니다. 하나라도 실패하면 전체 거부.

            입력:
            - prompt: 플로우 설명 (예: "로그인 → 홈 → 프로필 → 로그아웃 흐름")
            - fileKey: Figma 파일 Key
            - screens: 화면 목록 (각 화면의 이름, 역할, 순서)

            출력: FigmaDesignOperation (여러 화면 포함)

            주의:
            - 모든 화면이 성공할 때만 APPLY_REQUIRED로 진행.
            - Apply 전에 멀티 스크린 검증(모두 성공 또는 모두 실패).
            """)
    public String createMultiScreenFlow(
            String figmaMcpSecret, String prompt, String fileKey, List<String> screenNames) {
        authorization.authorize(figmaMcpSecret);
        log.info("Processing MULTI_SCREEN_FLOW: screens count={}", screenNames.size());
        if (screenNames == null || screenNames.isEmpty()) {
            throw new IllegalArgumentException("screenNames는 최소 1개 이상이어야 합니다");
        }
        List<FigmaScreenRequest> screens = screenNames.stream()
                .map(name -> new FigmaScreenRequest(name, prompt + " - " + name))
                .toList();
        FigmaDesignRequest request = FigmaDesignRequest.multiScreenFlow(prompt, fileKey, screens);
        FigmaDesignOperation operation = orchestrationService.processExplicitRequest(request);
        return serializeOperation(operation);
    }

    /**
     * R6-037: 특정 컴포넌트 지정하여 화면 생성.
     * 컴포넌트 Logical Type 목록 → Registry 존재 검증 → 컴포넌트 조합 기반 ScreenSpecification.
     */
    @Tool(description = """
            특정 컴포넌트(button, form, table 등)를 지정하여 화면을 생성합니다.
            요청 컴포넌트는 승인된 ComponentRegistry에만 존재해야 합니다.

            입력:
            - prompt: 화면 설명 (예: "버튼, 입력필드, 테이블을 포함한 관리 화면")
            - fileKey: Figma 파일 Key
            - components: 요청 컴포넌트 Logical Type (예: ["krds.button", "krds.textField", "krds.table"])

            출력: FigmaDesignOperation

            주의:
            - 요청 컴포넌트는 Registry에서 CURRENT 상태여야 합니다.
            - 미승인 또는 폐기된 컴포넌트는 차단됩니다.
            - 선택 컴포넌트는 Placeholder로 폴백.
            """)
    public String createDesignWithComponents(
            String figmaMcpSecret, String prompt, String fileKey, List<String> components) {
        authorization.authorize(figmaMcpSecret);
        log.info("Processing COMPONENT_SPECIFIED: components count={}", components.size());
        FigmaDesignRequest request = FigmaDesignRequest.componentSpecified(prompt, fileKey, components);
        FigmaDesignOperation operation = orchestrationService.processExplicitRequest(request);
        return serializeOperation(operation);
    }

    /**
     * R6-038: 플랫폼/반응형 변환.
     * Desktop 화면 → Tablet/Mobile 레이아웃 변환 (Grid, Navigation, Component Swap).
     */
    @Tool(description = """
            Figma 화면을 다른 플랫폼으로 변환합니다 (Desktop ↔ Tablet ↔ Mobile).
            레이아웃, Grid, Navigation, Component 선택(Swap)을 자동 적용합니다.

            입력:
            - prompt: 변환 지시사항 (예: "모바일 우선 디자인, Navigation은 하단 탭으로")
            - fileKey: Figma 파일 Key
            - sourceNodeIds: 원본 화면 노드 ID (예: Desktop 화면)
            - targetPlatform: 목표 플랫폼 (DESKTOP / TABLET / MOBILE)

            출력: FigmaDesignOperation

            주의:
            - targetPlatform 필수 (대소문자 구분)
            - 지원 정책: Desktop 1440px/12열, Tablet 768px/8열, Mobile 390px/4열
            - Component Swap은 Profile에서 지정된 정책만 적용됨.
            """)
    public String convertPlatform(
            String figmaMcpSecret, String prompt, String fileKey,
            List<String> sourceNodeIds, String targetPlatform) {
        authorization.authorize(figmaMcpSecret);
        log.info("Processing PLATFORM_CONVERT: sourceNodeIds count={}, targetPlatform={}",
                sourceNodeIds.size(), targetPlatform);
        FigmaDesignRequest request = FigmaDesignRequest.platformConvert(prompt, fileKey, sourceNodeIds, targetPlatform);
        FigmaDesignOperation operation = orchestrationService.processExplicitRequest(request);
        return serializeOperation(operation);
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
