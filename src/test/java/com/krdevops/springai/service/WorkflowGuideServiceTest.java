package com.krdevops.springai.service;

import com.krdevops.springai.model.GenerationReport;
import com.krdevops.springai.model.ProjectSpec;
import com.krdevops.springai.service.initializr.ResultBuilder;
import com.krdevops.springai.service.initializr.VersionCapabilityResolver;
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

    // ── project-setup-crud workflow ─────────────────────────────────────────────

    @Test
    void suggestProjectSetupCrudWorkflow_빈context_전체9단계_반환() {
        String result = workflowGuideService.suggestProjectSetupCrudWorkflow("");
        assertThat(result).contains("프로젝트 초기화 후 CRUD");
        assertThat(result).contains("▶ 1. 프로젝트 초기화 [initializeProject] ← 다음 단계");
        assertThat(result).contains("9.");
    }

    @Test
    void suggestProjectSetupCrudWorkflow_PROJECT_CONTEXT_초기화완료_다음단계_DB설정() {
        // §4.3 정책: [PROJECT_CONTEXT] 포함 → 1단계 자기완료 → 2단계 DB 설정 안내
        String result = workflowGuideService.suggestProjectSetupCrudWorkflow(
                "[PROJECT_CONTEXT]\negovVersion=5.0\n[/PROJECT_CONTEXT]");
        assertThat(result).contains("▶ 2. DB 설정 [수동 설정] ← 다음 단계");
    }

    @Test
    void suggestProjectSetupCrudWorkflow_DB설정완료_다음단계_DB스키마조회() {
        String result = workflowGuideService.suggestProjectSetupCrudWorkflow(
                "PROJECT_CONTEXT 포함. context-datasource.xml DB 정보 설정 완료");
        assertThat(result).contains("▶ 3. DB 스키마 조회 [getTableSchema] ← 다음 단계");
    }

    @Test
    void suggestProjectSetupCrudWorkflow_buildFullCrudPrompt완료_다음단계_CRUD코드저장() {
        String result = workflowGuideService.suggestProjectSetupCrudWorkflow(
                "PROJECT_CONTEXT 포함. datasource 설정 완료. getTableSchema 완료. buildFullCrudPrompt 완료");
        assertThat(result).contains("▶ 5. CRUD 코드 저장 [saveGeneratedCode] ← 다음 단계");
    }

    @Test
    void suggestProjectSetupCrudWorkflow_빌드완료_다음단계_상태확인() {
        String result = workflowGuideService.suggestProjectSetupCrudWorkflow(
                "PROJECT_CONTEXT 포함. datasource 설정 완료. getTableSchema 완료. buildFullCrudPrompt 완료. " +
                "saveGeneratedCode 완료. 이력 저장 완료. 빌드 완료");
        assertThat(result).contains("▶ 8. 프로젝트 상태 확인 [checkProjectHealth] ← 다음 단계");
    }

    @Test
    void suggestProjectSetupCrudWorkflow_initializeProject전체결과_step1만_감지_step2가_다음단계() {
        // initializeProject 전체 출력을 context로 넘길 때
        // ResultBuilder는 instruction 텍스트로 getTableSchema, buildFullCrudPrompt,
        // saveGeneratedCode, mvn clean package 등을 포함하지만 "완료 행위" 키워드가 없으므로
        // step 1(PROJECT_CONTEXT)만 감지 → step 2(DB 설정)가 다음 단계여야 한다
        ProjectSpec spec = ProjectSpec.of(
                "sample", "com.example", "sample", "com.example.sample",
                "maven", "war", "/tmp/test", new VersionCapabilityResolver().resolve("5.0"));
        String fullInitResult = new ResultBuilder().build(spec, new GenerationReport(spec.root().toString()));

        String result = workflowGuideService.suggestProjectSetupCrudWorkflow(fullInitResult);
        assertThat(result).contains("▶ 2. DB 설정 [수동 설정] ← 다음 단계");
    }
}
