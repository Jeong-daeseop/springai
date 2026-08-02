package com.krdevops.springai.tools;

import com.krdevops.springai.service.thymeleaf.mcp.ThymeleafConversionMcpFacade;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * R6-064: JSP→Thymeleaf 변환 파이프라인 MCP Tool.
 *
 * <p>기존 eGovFrame JSP 화면과 관련 Controller·VO를 분석하여 Thymeleaf 화면으로 변환한다.
 * 승인 전에는 대상 프로젝트의 파일을 건드리지 않으며, Apply 직전에 소스 변경을 감지한다.
 */
@Component
@RequiredArgsConstructor
public class ThymeleafConversionTool {

    private final ThymeleafConversionMcpFacade facade;

    /**
     * 프로젝트 내 JSP 파일 발견.
     */
    @Tool(description = """
            eGovFrame 프로젝트에서 JSP 화면 파일을 자동 발견합니다.
            glob 패턴으로 필터링하고 제외 목록을 적용하여 변환 대상 화면을 찾습니다.

            입력:
            - projectRootPath: 프로젝트 루트 (절대 경로)
            - globPattern: JSP 파일 패턴 (예: "**/WEB-INF/jsp/**/*.jsp", 생략 시 기본값)
            - excludePatterns: 제외 패턴 목록 (예: ["**/build/**", "**/target/**"], 생략 가능)

            출력:
            - 발견된 JSP 파일 목록 (파일명, 상대 경로, 예상 Controller/VO 경로 포함)

            주의:
            - 경로 탈출 시도(../), 제외 디렉터리(.git, build, target, node_modules 등), 민감 파일은 차단됨
            - 읽기 전용 (파일 수정 없음)
            """)
    public String scanLegacyJspFiles(
            String projectRootPath,
            @Nullable String globPattern,
            @Nullable List<String> excludePatterns) {
        return facade.scanLegacyJspFiles(projectRootPath, globPattern, excludePatterns);
    }

    /**
     * JSP·Controller·VO 분석 + 렌더링 + Preview.
     *
     * <p>입력:
     * - projectRootPath: 프로젝트 루트 (절대 경로)
     * - screenId: 화면 식별자
     * - screenRole: 화면 역할 (LIST / FORM / DETAIL)
     * - jspRelativePath: JSP 파일 상대 경로
     * - controllerRelativePath: Controller 파일 상대 경로
     * - voRelativePath: VO 파일 상대 경로
     * - pageTitle: 페이지 제목 (예: "직원 목록")
     * - targetRelativePath: 생성할 Thymeleaf 파일 상대 경로
     *
     * <p>출력:
     * - ThymeleafConversionOperation (operationId, status=PREVIEW_READY/FAILED, issues)
     *
     * <p>상태:
     * - PREVIEW_READY: 미리보기 준비 완료 (사람 승인 대기)
     * - FAILED: 분석/렌더링 실패 (issues 확인)
     * - REVIEW_REQUIRED: 매핑 충돌 (사람 검토 필요)
     *
     * <p>주의:
     * - 이 단계에서는 대상 프로젝트의 파일을 건드리지 않음
     * - operationId를 기록했다가 approve/apply 시 사용
     */
    @Tool(description = """
            JSP 화면과 관련 Controller·VO를 분석하여 Thymeleaf 화면을 생성합니다.
            분석 → 렌더링 → Preview까지 진행하되, 대상 프로젝트의 파일은 건드리지 않습니다.
            사람이 Preview를 확인한 후 approveThymeleafConversion → applyThymeleafConversion을 순서대로 호출하세요.

            입력:
            - projectRootPath: 프로젝트 루트 (절대 경로)
            - screenId: 화면 식별자 (예: "emp-list-001")
            - screenRole: LIST / FORM / DETAIL
            - jspRelativePath: JSP 파일 (예: "WEB-INF/jsp/egovframework/let/emp/EgovEmployerList.jsp")
            - controllerRelativePath: Controller 자바 파일 (예: "src/main/java/.../EgovEmployerController.java")
            - voRelativePath: VO 자바 파일 (예: "src/main/java/.../EmployerVO.java")
            - pageTitle: 페이지 제목 (예: "직원 목록")
            - targetRelativePath: 생성할 Thymeleaf 경로 (예: "emp/EgovEmployerList.html")

            출력:
            - operationId: 작업 식별자 (approve/apply 시 필요)
            - status: PREVIEW_READY (성공) / FAILED (실패) / REVIEW_REQUIRED (충돌)
            - renderedHtml: 미리보기 HTML (status=PREVIEW_READY인 경우)
            - issues: 문제 목록 (Binding 충돌, 필드 미매핑 등)

            주의:
            - 이 단계에서는 대상 파일을 생성하지 않음
            - status=PREVIEW_READY인지 확인 후 다음 단계 진행
            """)
    public String analyzeAndPreviewLegacyScreen(
            String projectRootPath,
            String screenId,
            String screenRole,
            String jspRelativePath,
            String controllerRelativePath,
            String voRelativePath,
            String pageTitle,
            String targetRelativePath) {
        return facade.analyzeAndPreviewLegacyScreen(
                projectRootPath, screenId, screenRole,
                jspRelativePath, controllerRelativePath, voRelativePath,
                pageTitle, targetRelativePath);
    }

    /**
     * 미리보기 승인.
     *
     * <p>입력:
     * - operationId: analyzeAndPreviewLegacyScreen의 결과에서 받은 operationId
     *
     * <p>출력:
     * - 업데이트된 ThymeleafConversionOperation (status=APPROVED)
     *
     * <p>주의:
     * - 이 단계에서도 파일은 건드리지 않음
     * - 다음 단계 applyThymeleafConversion 호출 직전 한 번만 호출
     */
    @Tool(description = """
            Preview를 확인한 후 변환 작업을 승인합니다.
            이 단계에서도 파일을 건드리지 않습니다.

            입력:
            - operationId: analyzeAndPreviewLegacyScreen의 결과에서 받은 operationId

            출력:
            - 업데이트된 Operation (status=APPROVED)

            주의:
            - 이 단계 후에는 applyThymeleafConversion을 반드시 호출하여 실제 파일을 생성해야 함
            - 승인 없이 apply를 호출하면 거부됨
            """)
    public String approveThymeleafConversion(String operationId) {
        return facade.approveThymeleafConversion(operationId);
    }

    /**
     * 승인된 작업을 실제 프로젝트에 적용.
     *
     * <p>입력:
     * - operationId: approve의 결과 operationId
     * - projectRootPath, screenId, screenRole, jspRelativePath, controllerRelativePath, voRelativePath:
     *   analyzeAndPreview와 동일 (Apply 직전 소스 변경 감지용)
     *
     * <p>출력:
     * - ThymeleafConversionOperation (status=VALIDATED, artifacts에 생성 파일 기록)
     *
     * <p>상태:
     * - VALIDATED: 적용 완료 + 검증 통과
     * - CONFLICT: 소스 파일이 분석 이후 변경됨 (재분석 필요)
     * - FAILED: 파일 쓰기 실패
     *
     * <p>주의:
     * - 이 단계에서 targetRelativePath로 지정한 경로에 파일이 생성됨
     * - 기존 파일이 있으면 .bak 백업이 자동 생성됨
     * - 소스(JSP/Controller/VO)가 변경되면 CONFLICT 상태로 전이되며 다시 분석해야 함
     */
    @Tool(description = """
            승인된 변환을 실제 프로젝트에 적용합니다.
            이 단계에서 대상 파일이 생성됩니다.

            입력:
            - operationId: approveThymeleafConversion의 결과 operationId
            - projectRootPath, screenId, screenRole, jspRelativePath, controllerRelativePath, voRelativePath:
              (analyzeAndPreview와 동일 - Apply 직전 충돌 검증에 사용)

            출력:
            - 업데이트된 Operation (status=VALIDATED/CONFLICT/FAILED)
            - artifacts: 생성된 파일, 백업 파일 정보

            상태:
            - VALIDATED: 적용 완료 + 최종 검증 통과 ✅
            - CONFLICT: 소스 파일이 분석 이후 변경됨 (SOURCE_REVISION_CHANGED) → 재분석 필요
            - FAILED: 파일 쓰기 또는 렌더링 실패

            주의:
            - 기존 파일이 있으면 .bak-{timestamp} 백업이 생성됨
            - 소스(JSP/Controller/VO)가 변경되었다고 감지되면 CONFLICT로 중단
            - 적용 후 Thymeleaf 렌더링 재검증을 자동으로 수행
            """)
    public String applyThymeleafConversion(
            String operationId,
            String projectRootPath,
            String screenId,
            String screenRole,
            String jspRelativePath,
            String controllerRelativePath,
            String voRelativePath) {
        return facade.applyThymeleafConversion(
                operationId, projectRootPath, screenId, screenRole,
                jspRelativePath, controllerRelativePath, voRelativePath);
    }
}
