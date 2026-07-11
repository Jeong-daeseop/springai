package com.krdevops.springai.service;

import com.krdevops.springai.model.FilePlan;
import com.krdevops.springai.model.GenerationReport;
import com.krdevops.springai.model.ProjectSpec;
import com.krdevops.springai.model.VersionCapability;
import com.krdevops.springai.service.initializr.FilePlanExecutor;
import com.krdevops.springai.service.initializr.FilePlanFactory;
import com.krdevops.springai.service.initializr.GenerationHistoryRecorder;
import com.krdevops.springai.service.initializr.ProjectValidator;
import com.krdevops.springai.service.initializr.ResultBuilder;
import com.krdevops.springai.service.initializr.VersionCapabilityResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * eGovFrame 프로젝트 골격 생성 서비스 (Phase 3 — 얇은 조율자)
 *
 * projectType : war  — XML 기반 전통 eGovFrame (web.xml + context-*.xml + Tomcat WAR 배포)
 *               boot — Spring Boot 기반 eGovFrame (application.yml + 내장 서버)
 *
 * egovVersion : 4.3     — eGovFrame 4.3.0 / Spring 5.3.x / Java 11 / javax.servlet 4.0
 *               latest  — eGovFrame 5.0.0 / Spring Boot 3.5.x / Java 17 / Jakarta EE 10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectInitializrService {

    private final VersionCapabilityResolver resolver;
    private final FilePlanFactory factory;
    private final FilePlanExecutor executor;
    private final ProjectValidator validator;
    private final ResultBuilder resultBuilder;
    private final GenerationHistoryRecorder recorder;

    // ── 공개 API ─────────────────────────────────────────────────────────────

    /**
     * eGovFrame 설정 파일 템플릿 단독 반환
     *
     * configType : contextCommon / contextDatasource / contextTransaction /
     *              dispatcherServlet / webXml / logback / log4j2 / applicationYml
     * packageName: Java 패키지명 (contextCommon, dispatcherServlet, applicationYml 에 사용)
     *              생략 시 "egovframework.let.sample" 적용
     */
    public String getConfigTemplate(String configType, String packageName) {
        String pkg  = (packageName == null || packageName.isBlank())
                      ? "egovframework.let.sample" : packageName;
        String name = pkg.contains(".") ? pkg.substring(pkg.lastIndexOf('.') + 1) : pkg;

        return switch (configType.toLowerCase()) {
            case "contextcommon"      -> factory.contextCommon(pkg);
            case "contextdatasource"  -> factory.contextDatasource();
            case "contexttransaction" -> factory.contextTransaction();
            case "dispatcherservlet"  -> factory.dispatcherServlet(pkg, "4.3");
            case "webxml"             -> factory.webXml(name, "5.0");
            case "logback"            -> factory.logbackSpringXml(name);
            case "log4j2"             -> factory.log4j2Xml(name);
            case "applicationyml"     -> factory.bootApplicationYml(name, pkg);
            default -> "지원하지 않는 configType 입니다: " + configType + "\n"
                     + "지원 목록: contextCommon / contextDatasource / contextTransaction /\n"
                     + "          dispatcherServlet / webXml / logback / log4j2 / applicationYml";
        };
    }

    public String initializeProject(String projectName, String groupId, String artifactId,
                                    String packageName, String buildTool,
                                    String projectType, String egovVersion, String outputPath) {
        return initializeProject(projectName, groupId, artifactId, packageName, buildTool,
                projectType, egovVersion, outputPath, "jsp");
    }

    public String initializeProject(String projectName, String groupId, String artifactId,
                                    String packageName, String buildTool,
                                    String projectType, String egovVersion, String outputPath,
                                    String viewType) {

        // ① Capability 해석 + Spec 조립
        VersionCapability cap = resolver.resolve(egovVersion);
        ProjectSpec spec = ProjectSpec.of(projectName, groupId, artifactId,
                packageName, buildTool, projectType, outputPath, cap, viewType);

        // ② 디렉터리 생성 (FilePlan 외부 — 디렉터리는 파일이 아님)
        createDirectories(spec);

        // ③ FilePlan 목록 생성 (Supplier 지연 — 렌더링 안 함)
        List<FilePlan> plans = factory.plan(spec);

        // ④ 사전 검증 (중복 경로, null, 경로 탈출)
        validator.validatePlans(plans);

        // ⑤ FilePlan 루프 실행 (파일 단위 에러 격리)
        GenerationReport report = executor.execute(spec, plans);

        // ⑥ 사후 검증 (필수 파일 + namespace + Java 버전)
        validator.validateResult(spec, report);

        // ⑦ 이력 기록
        recorder.record(spec, report.totalFiles(), report.errors().size());

        // ⑧ 결과 빌드 (ProjectContext 블록 포함)
        return resultBuilder.build(spec, report);
    }

    // ── private 헬퍼 ─────────────────────────────────────────────────────────

    private void createDirectories(ProjectSpec spec) {
        try {
            for (String dir : factory.directoryPlans(spec)) {
                Files.createDirectories(spec.root().resolve(dir));
            }
        } catch (IOException e) {
            log.error("디렉터리 생성 실패", e);
        }
    }
}
