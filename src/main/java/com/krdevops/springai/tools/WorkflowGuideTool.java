package com.krdevops.springai.tools;

import com.krdevops.springai.service.WorkflowGuideService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkflowGuideTool {

    private final WorkflowGuideService workflowGuideService;

    @Tool(description = """
            eGovFrame CRUD 소스 생성 워크플로우 안내 도구.
            currentContext에 지금까지 완료한 작업 내용을 입력하면 다음 단계를 안내합니다.
            빈 문자열을 입력하면 전체 14단계 워크플로우를 처음부터 안내합니다.

            14단계: DB스키마조회 → VO → Mapper → MapperXML → Service → ServiceImpl
                   → Controller → 목록JSP → 상세JSP → 등록JSP → 수정JSP
                   → 입력값검증 → 이력저장 → 상태확인
            """)
    public String suggestNextStep(String currentContext) {
        return workflowGuideService.suggestNextStep(currentContext);
    }

    @Tool(description = """
            ProjectInitializrTool.initializeProject() 실행 후 다음 작업을 안내하는 workflow 도구.
            initializeProject 결과의 PROJECT_CONTEXT 블록과 현재 완료한 작업 내용을 currentContext에 넣으면
            DB 설정, DB 스키마 조회, CRUD 프롬프트 생성, 코드 저장, 빌드 검증 순서로 다음 단계를 안내합니다.
            빈 문자열을 입력하면 전체 9단계 workflow를 처음부터 안내합니다.
            """)
    public String suggestProjectSetupCrudWorkflow(String currentContext) {
        return workflowGuideService.suggestProjectSetupCrudWorkflow(currentContext);
    }

    @Tool(description = """
            eGovFrame Security / 메뉴 등록 / URL 권한 등록 워크플로우 안내 도구.
            SecurityTemplateTool → MenuTool → AuthTool 의 올바른 적용 순서를 안내합니다.
            currentContext에 지금까지 완료한 작업 내용을 입력하면 다음 단계를 안내합니다.
            빈 문자열을 입력하면 전체 9단계 워크플로우를 처음부터 안내합니다.

            9단계: SecurityTemplateTool 생성 → securityMapper 확인 → 상위 메뉴 조회
                  → 프로그램 중복 확인 → 메뉴 SQL 생성 → 권한 SQL 생성
                  → SQL 실행 → 서버 재기동 → 접근 테스트

            ※ 현재는 기존 호출자 호환성을 위해 방식 B(전용 메서드)를 유지한다.
               workflowType 기반 방식 A 전환은 별도 리팩터링으로 진행한다.
            """)
    public String suggestSecurityMenuAuthWorkflow(String currentContext) {
        return workflowGuideService.suggestSecurityMenuAuthWorkflow(currentContext);
    }
}
