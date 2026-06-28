package com.krdevops.springai.service.initializr;

import com.krdevops.springai.model.GenerationReport;
import com.krdevops.springai.model.ProjectContext;
import com.krdevops.springai.model.ProjectSpec;
import org.springframework.stereotype.Component;

@Component
public class ResultBuilder {

    public String build(ProjectSpec s, GenerationReport report) {
        ProjectContext ctx = ProjectContext.from(s);
        String typeLabel = s.boot() ? "Spring Boot (내장 서버)" : "WAR (Tomcat 외부 배포)";
        String buildCmd  = s.gradle()
            ? (s.boot() ? "./gradlew bootRun" : "./gradlew build")
            : (s.boot() ? "mvn spring-boot:run" : "mvn clean package");

        String egovLabel = s.egovLabel();

        StringBuilder sb = new StringBuilder();
        sb.append("=== eGovFrame 프로젝트 초기화 완료 ===\n\n");
        sb.append("📌 경로   : ").append(report.rootPath()).append("\n");
        sb.append("📌 타입   : ").append(typeLabel).append("\n");
        sb.append("📌 버전   : eGovFrame ").append(egovLabel).append("\n");
        sb.append("📌 빌드   : ").append(s.buildTool()).append("\n\n");

        sb.append("✅ 생성 완료 (").append(report.totalFiles()).append("개)\n");
        report.created().forEach(f -> sb.append("  📄 ").append(f).append("\n"));

        if (report.hasErrors()) {
            sb.append("\n⚠️  오류 (").append(report.errors().size()).append("개)\n");
            report.errors().forEach((f, m) ->
                sb.append("  ❌ ").append(f).append(" → ").append(m).append("\n"));
        }

        if (!report.warnings().isEmpty()) {
            sb.append("\n⚠️  경고\n");
            report.warnings().forEach(w -> sb.append("  ⚠ ").append(w).append("\n"));
        }

        String dbConfig = s.boot() ? "application.yml" : "context-datasource.xml";

        sb.append("\n📋 다음 단계\n");
        sb.append("  1. ").append(dbConfig).append(" DB 정보 설정\n");
        sb.append("  2. buildFullCrudPrompt(..., egovVersion=\"").append(egovLabel)
          .append("\", viewType=\"jsp\") 로 CRUD 소스 생성\n");
        sb.append("     - viewType: \"jsp\" 또는 \"thymeleaf\" 선택 가능\n");
        sb.append("     - buildFullCrudPrompt는 내부에서 getTableSchema와 공통코드 조회를 함께 처리합니다.\n");
        sb.append("  3. saveGeneratedCode 또는 auto orchestration 결과에 따라 파일 저장 확인\n");
        sb.append("  4. ").append(buildCmd).append(" 로 빌드 검증\n");
        sb.append("\n후속 workflow를 단계별로 확인하려면\n");
        sb.append("  → suggestProjectSetupCrudWorkflow(PROJECT_CONTEXT 블록 + \"프로젝트 초기화 완료\")\n");
        sb.append("\n선택: Security/Menu/Auth 적용이 필요하면\n");
        sb.append("  → suggestSecurityMenuAuthWorkflow(PROJECT_CONTEXT 블록)\n");

        sb.append("\n").append(ctx.toBlock()).append("\n");
        return sb.toString();
    }
}
