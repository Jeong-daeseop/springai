package com.krdevops.springai.service;

import com.krdevops.springai.service.workflow.WorkflowDefinitionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class WorkflowGuideServiceTest {

    private WorkflowGuideService workflowGuideService;

    @BeforeEach
    void setUp() {
        workflowGuideService = new WorkflowGuideService(new WorkflowDefinitionRegistry());
    }

    @Test
    void suggestNextStep_빈context_전체CRUD워크플로우_반환() {
        String result = workflowGuideService.suggestNextStep("");
        assertThat(result).contains("CRUD");
        assertThat(result).contains("1.");
        assertThat(result).contains("14.");
    }

    @Test
    void suggestSecurityMenuAuthWorkflow_빈context_전체9단계_반환() {
        String result = workflowGuideService.suggestSecurityMenuAuthWorkflow("");
        assertThat(result).contains("Security");
        assertThat(result).contains("1.");
        assertThat(result).contains("9.");
    }

    @Test
    void suggestSecurityMenuAuthWorkflow_security완료context_다음단계_securityMapper_안내() {
        String result = workflowGuideService.suggestSecurityMenuAuthWorkflow(
                "SecurityTemplateTool로 security 파일 생성 완료");
        assertThat(result).contains("securityMapper");
    }

    @Test
    void suggestSecurityMenuAuthWorkflow_메뉴SQL완료_다음단계_권한SQL_안내() {
        String result = workflowGuideService.suggestSecurityMenuAuthWorkflow(
                "security, securitymapper, 메뉴구조, 프로그램목록, 메뉴등록 완료");
        assertThat(result).contains("권한");
    }

    @Test
    void suggestSecurityMenuAuthWorkflow_단독완료문맥_비연속감지_다음단계_안내() {
        // 사용자가 현재 완료 작업만 입력한 경우 (앞 단계 언급 없음)
        // "메뉴등록" 키워드 → 5단계 감지 → 다음 단계로 권한 SQL 안내
        String result = workflowGuideService.suggestSecurityMenuAuthWorkflow("메뉴등록 완료");
        assertThat(result).contains("권한");
    }
}
