package com.krdevops.springai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.util.Map;

/**
 * CRUD 소스 저장 경로를 결정하는 서비스.
 *
 * [3순위] resolveDefault()  — application.yaml의 egov.output.base-path 기반 기본 경로 반환
 * [4순위] resolveFromProject() — 기존 eGovFrame 프로젝트를 스캔하여 실제 레이어별 경로 반환
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutputPathResolverService {

    @Value("${egov.output.base-path:${user.home}/Desktop/egov-generated}")
    private String basePath;

    private final ProjectScannerService projectScannerService;

    // ── 3순위: 기본 경로 ──────────────────────────────────────────────────────

    /**
     * 기본 출력 경로를 반환합니다.
     * 환경변수 EGOV_OUTPUT_PATH 또는 ~/Desktop/egov-generated/{domainLc}
     *
     * @param domain 도메인명 (예: Employee)
     */
    public String resolveDefault(String domain) {
        String domainLc = toLowerFirst(domain);
        String path = basePath + "/" + domainLc;
        log.info("기본 출력 경로: {}", path);
        return path;
    }

    // ── 4순위: 프로젝트 스캔 기반 경로 ──────────────────────────────────────

    /**
     * 기존 eGovFrame 프로젝트를 스캔하여 레이어별 저장 경로를 반환합니다.
     *
     * @param projectRootPath 스캔할 프로젝트 루트 절대경로
     * @param packageName     패키지명 (예: egovframework.let.emp)
     * @param domain          도메인명 (예: Employee)
     */
    public String resolveFromProject(String projectRootPath, String packageName, String domain) {
        Map<String, String> layers = projectScannerService.getLayerDirectories(projectRootPath);

        if (layers.isEmpty()) {
            String fallback = resolveDefault(domain);
            return "프로젝트 경로를 스캔할 수 없습니다: " + projectRootPath +
                   "\n기본 경로를 사용합니다: " + fallback;
        }

        String domainLc = toLowerFirst(domain);
        StringBuilder sb = new StringBuilder();
        sb.append("=== eGovFrame 소스 저장 경로 분석 ===\n");
        sb.append("프로젝트: ").append(projectRootPath).append("\n\n");

        // Java 소스 기본 경로: web 디렉터리의 부모 = 도메인 패키지 디렉터리
        String javaBase = null;
        if (layers.containsKey("web")) {
            javaBase = Paths.get(layers.get("web")).getParent().toString();
        } else if (layers.containsKey("service")) {
            javaBase = Paths.get(layers.get("service")).getParent().toString();
        } else if (layers.containsKey("impl")) {
            javaBase = Paths.get(layers.get("impl")).getParent().getParent().toString();
        }

        if (javaBase != null) {
            sb.append("[Java 소스 기본 경로]\n");
            sb.append("  ").append(javaBase).append("\n\n");
            sb.append("[레이어별 저장 위치]\n");
            sb.append("  VO          : ").append(javaBase).append("/vo/").append(domain).append("VO.java\n");
            sb.append("  Mapper      : ").append(javaBase).append("/service/impl/").append(domain).append("Mapper.java\n");
            sb.append("  Service     : ").append(javaBase).append("/service/").append(domain).append("Service.java\n");
            sb.append("  ServiceImpl : ").append(javaBase).append("/service/impl/Egov").append(domain).append("ServiceImpl.java\n");
            sb.append("  Controller  : ").append(javaBase).append("/web/Egov").append(domain).append("Controller.java\n");
        }

        if (layers.containsKey("mapperXml")) {
            sb.append("  MapperXml   : ").append(layers.get("mapperXml")).append("/").append(domain).append("Mapper.xml\n");
        }

        if (layers.containsKey("jsp")) {
            String jspBase = Paths.get(layers.get("jsp")).getParent().toString();
            sb.append("  JSP (List)  : ").append(jspBase).append("/").append(domainLc).append("/Egov").append(domain).append("List.jsp\n");
            sb.append("  JSP (Detail): ").append(jspBase).append("/").append(domainLc).append("/Egov").append(domain).append("Detail.jsp\n");
            sb.append("  JSP (Regist): ").append(jspBase).append("/").append(domainLc).append("/Egov").append(domain).append("Regist.jsp\n");
            sb.append("  JSP (Updt)  : ").append(jspBase).append("/").append(domainLc).append("/Egov").append(domain).append("Updt.jsp\n");
        }

        sb.append("\n[buildFullCrudPrompt outputPath 권장값]\n");
        String recommended = javaBase != null ? javaBase : resolveDefault(domain);
        sb.append("  ").append(recommended).append("\n");
        sb.append("\n이 경로를 buildFullCrudPrompt()의 outputPath 파라미터로 사용하세요.\n");
        sb.append("각 파일은 위 [레이어별 저장 위치]에 명시된 경로에 saveGeneratedCode()로 저장하세요.\n");

        log.info("프로젝트 기반 경로 분석 완료: project={}, javaBase={}", projectRootPath, recommended);
        return sb.toString();
    }

    private String toLowerFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
