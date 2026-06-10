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
            eGovFrame Security / 메뉴 등록 / URL 권한 등록 워크플로우 안내 도구.
            SecurityTemplateTool → MenuTool → AuthTool 의 올바른 적용 순서를 안내합니다.
            currentContext에 지금까지 완료한 작업 내용을 입력하면 다음 단계를 안내합니다.
            빈 문자열을 입력하면 전체 9단계 워크플로우를 처음부터 안내합니다.

            9단계: SecurityTemplateTool 생성 → securityMapper 확인 → 상위 메뉴 조회
                  → 프로그램 중복 확인 → 메뉴 SQL 생성 → 권한 SQL 생성
                  → SQL 실행 → 서버 재기동 → 접근 테스트

            ※ 방식 B(전용 메서드)로 운영 중. workflow 종류 3개 이상 또는
               suggest*Workflow() 메서드 2개 이상 추가 시 방식 A(workflowType 파라미터)로 전환 예정.
            """)
    public String suggestSecurityMenuAuthWorkflow(String currentContext) {
        return workflowGuideService.suggestSecurityMenuAuthWorkflow(currentContext);
    }
}
