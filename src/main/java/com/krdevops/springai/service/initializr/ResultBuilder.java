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

        sb.append("\n📋 다음 단계\n");
        sb.append("  1. ").append(s.boot() ? "application.yml" : "context-datasource.xml")
          .append(" DB 정보 설정\n");
        sb.append("  2. Spring Security 설정 추가 (선택)\n");
        if (!s.boot() && !s.cap().spring6()) {
            sb.append("     → getSecurityTemplate(\"webxmlfilter\",    \"<packageName>\", \"").append(egovLabel).append("\")\n");
            sb.append("     → getSecurityTemplate(\"contextsecurity\", \"<packageName>\", \"").append(egovLabel).append("\")\n");
        } else {
            sb.append("     → getSecurityTemplate(\"javaconfig\", \"<packageName>\", \"").append(egovLabel).append("\")\n");
        }
        sb.append("  3. buildFullCrudPrompt(..., egovVersion=\"").append(egovLabel).append("\") 로 CRUD 소스 생성\n");
        sb.append("  4. ").append(buildCmd).append(" 로 빌드/실행\n");

        sb.append("\n").append(ctx.toBlock()).append("\n");
        return sb.toString();
    }
}
