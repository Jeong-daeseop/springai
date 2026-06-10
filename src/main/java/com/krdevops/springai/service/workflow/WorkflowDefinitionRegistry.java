package com.krdevops.springai.service.workflow;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class WorkflowDefinitionRegistry {

    private final Map<String, WorkflowDefinition> registry;

    public WorkflowDefinitionRegistry() {
        List<WorkflowDefinition> definitions = List.of(
                buildCrudWorkflow(),
                buildSecurityMenuAuthWorkflow()
        );
        registry = definitions.stream()
                .collect(Collectors.toMap(WorkflowDefinition::type, Function.identity()));
    }

    public Optional<WorkflowDefinition> find(String type) {
        return Optional.ofNullable(registry.get(type));
    }

    public WorkflowDefinition getOrDefault(String type) {
        return registry.getOrDefault(type != null ? type : "crud", buildCrudWorkflow());
    }

    private WorkflowDefinition buildCrudWorkflow() {
        return new WorkflowDefinition("crud", "eGovFrame CRUD 소스 생성 워크플로우", List.of(
                new WorkflowStep(1, "DB 스키마 조회", "getTableSchema", "테이블 구조 파악", new String[]{"스키마", "테이블", "schema"}),
                new WorkflowStep(2, "VO 생성", "saveGeneratedCode", "VO 클래스 생성", new String[]{"vo", "valueobject"}),
                new WorkflowStep(3, "Mapper 인터페이스 생성", "saveGeneratedCode", "Mapper 인터페이스 생성", new String[]{"mapper", "인터페이스"}),
                new WorkflowStep(4, "Mapper XML 생성", "saveGeneratedCode", "MyBatis XML 생성", new String[]{"mapper xml", "mapperxml", "xml"}),
                new WorkflowStep(5, "Service 인터페이스 생성", "saveGeneratedCode", "Service 인터페이스 생성", new String[]{"service 인터페이스"}),
                new WorkflowStep(6, "ServiceImpl 생성", "saveGeneratedCode", "Service 구현체 생성", new String[]{"serviceimpl", "impl"}),
                new WorkflowStep(7, "Controller 생성", "saveGeneratedCode", "Controller 생성", new String[]{"controller"}),
                new WorkflowStep(8, "목록 JSP 생성", "saveGeneratedCode", "목록 페이지 JSP 생성", new String[]{"목록", "list"}),
                new WorkflowStep(9, "상세 JSP 생성", "saveGeneratedCode", "상세 페이지 JSP 생성", new String[]{"상세", "detail"}),
                new WorkflowStep(10, "등록 JSP 생성", "saveGeneratedCode", "등록 페이지 JSP 생성", new String[]{"등록", "regist"}),
                new WorkflowStep(11, "수정 JSP 생성", "saveGeneratedCode", "수정 페이지 JSP 생성", new String[]{"수정", "update"}),
                new WorkflowStep(12, "입력값 검증", "검토", "유효성 검증 확인", new String[]{"검증", "validation"}),
                new WorkflowStep(13, "생성 이력 저장", "saveGenerationHistory", "이력 기록", new String[]{"이력", "history"}),
                new WorkflowStep(14, "프로젝트 상태 확인", "checkProjectHealth", "최종 확인", new String[]{"확인", "health"})
        ));
    }

    private WorkflowDefinition buildSecurityMenuAuthWorkflow() {
        return new WorkflowDefinition("security-menu-auth", "Security / 메뉴 / 권한 등록 워크플로우", List.of(
                new WorkflowStep(1, "Security 기반 생성", "SecurityTemplateTool", "eGovFrame Security 파일 생성", new String[]{"security", "보안", "securitytemplate"}),
                new WorkflowStep(2, "securityMapper 확인", "확인", "securityMapper 포함 여부 확인", new String[]{"securitymapper", "security mapper"}),
                new WorkflowStep(3, "상위 메뉴 조회", "getMenuStructure", "상위 메뉴 번호 확인", new String[]{"메뉴구조", "getmenustructure", "상위메뉴"}),
                new WorkflowStep(4, "프로그램 중복 확인", "getProgramList", "기존 프로그램 목록 확인", new String[]{"프로그램목록", "getprogramlist", "중복"}),
                new WorkflowStep(5, "메뉴 SQL 생성", "generateMenuInsertSql", "프로그램/메뉴 INSERT SQL 생성", new String[]{"메뉴sql", "generatemenusinsertsql", "메뉴등록"}),
                new WorkflowStep(6, "권한 SQL 생성", "generateAuthInsertSql", "ROLE/권한 INSERT SQL 생성", new String[]{"권한sql", "generateauthInsertsql", "권한등록"}),
                new WorkflowStep(7, "SQL 실행", "실행", "생성된 SQL DB 실행", new String[]{"sql실행", "실행", "execute"}),
                new WorkflowStep(8, "서버 재기동", "재기동", "Security 캐시 갱신 또는 재기동", new String[]{"재기동", "restart", "캐시"}),
                new WorkflowStep(9, "접근 테스트", "테스트", "메뉴 노출 및 URL 접근 테스트", new String[]{"테스트", "test", "접근확인"})
        ));
    }
}
