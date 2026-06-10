package com.krdevops.springai.tools;

import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.CodeValidatorService;
import com.krdevops.springai.service.CrudPromptBuilderService;
import com.krdevops.springai.service.CrudPromptBuilderService.PlaceholderValues;
import com.krdevops.springai.service.GenerationHistoryService;
import com.krdevops.springai.service.MasterDetailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CrudPromptBuilderTool {

    private final CrudPromptBuilderService crudPromptBuilderService;
    private final MasterDetailService masterDetailService;
    private final CodeService codeService;
    private final CodeValidatorService codeValidatorService;
    private final GenerationHistoryService generationHistoryService;

    /** 레이어별 [layerKey, 파일명 접두사, 파일명 접미사, 하위 경로] 정의 */
    private static final String[][] LAYERS = {
        // layerKey, 파일명, 하위경로 (outputPath 기준)
        {"vo",               "VO.java",               "egovframework/let/{PKG}/{DOMAIN_LC}/service/"},
        {"mapper",           "Mapper.java",            "egovframework/let/{PKG}/{DOMAIN_LC}/service/impl/"},
        {"mapperXml",        "Mapper.xml",             "egovframework/let/{PKG}/{DOMAIN_LC}/service/impl/"},
        {"service",          "Service.java",           "egovframework/let/{PKG}/{DOMAIN_LC}/service/"},
        {"serviceImpl",      "ServiceImpl.java",       "egovframework/let/{PKG}/{DOMAIN_LC}/service/impl/"},
        {"controller",       "Controller.java",        "egovframework/let/{PKG}/{DOMAIN_LC}/web/"},
        {"controlleradvice", "ValidationHandler.java", "egovframework/let/{PKG}/{DOMAIN_LC}/web/"},
        {"jspList",          "List.jsp",               "jsp/{DOMAIN_LC}/"},
        {"jspDetail",        "Detail.jsp",             "jsp/{DOMAIN_LC}/"},
        {"jspRegist",        "Regist.jsp",             "jsp/{DOMAIN_LC}/"},
        {"jspUpdt",          "Updt.jsp",               "jsp/{DOMAIN_LC}/"},
    };

    /** 레이어별 파일명 결정 — vo/mapper/mapperXml/service는 {Domain}Xxx, 나머지는 Egov{Domain}Xxx */
    private static String resolveFileName(String layerKey, String domain, String suffix) {
        return switch (layerKey) {
            case "vo", "mapper", "mapperXml", "service" -> domain + suffix;
            default                                      -> "Egov" + domain + suffix;
        };
    }

    @Tool(description = """
            eGovFrame 5.x CRUD 전체 소스 생성에 필요한 통합 프롬프트를 반환합니다.
            이 Tool 하나로 getTableSchema + 공통코드 조회 + 플레이스홀더 매핑을 한 번에 처리합니다.
            반환된 프롬프트의 지시에 따라 11개 레이어 소스를 순서대로 생성하고 저장하세요.
            database   : 데이터베이스명 (예: com)
            tableName  : 테이블명 (예: COMTNEMPLYRINFO)
            domain     : 도메인명 대문자 시작 (예: Employer)
            packageName: 패키지명 (예: egovframework.let.emp)
            outputPath : 소스 저장 절대경로 (예: /Users/user/Desktop/egov-gen/emp)
            llmProvider: 소스 생성 주체 선택 (생략 시 "claude" 기본값)
              - "claude": 기존 방식 — 프롬프트를 반환하고 Claude가 Tool을 순서대로 호출하여 생성
              - "auto"  : 내부 오케스트레이션 — Tool 내부에서 11개 파일을 직접 생성·저장 (Claude 토큰 97% 절감)
            egovVersion: eGovFrame 버전 (선택, 기본값 "5.0")
              - "5.0" 또는 "latest" : jakarta.validation.* import 사용
              - "4.3"               : javax.validation.* import 사용
              initializeProject() 완료 후 PROJECT_CONTEXT 블록의 egovVersion 값을 그대로 전달하세요.

            [중요] outputPath 결정 규칙 — 반드시 아래 순서를 따르세요:
            1. 사용자가 저장 경로를 명시한 경우 → 그 경로를 그대로 사용
            2. 사용자가 기존 프로젝트 경로를 알려준 경우 → resolveProjectOutputPath() 먼저 호출하여 경로 확정
            3. 경로를 모르거나 언급이 없는 경우 → getDefaultOutputPath(domain) 호출하여 기본 경로 사용
               (기본 경로: ~/Desktop/egov-generated/{domain})
            절대로 경로를 임의로 결정하거나 추측하지 마세요.
            outputPath를 확정한 후 사용자에게 "이 경로에 생성합니다: {path}" 라고 먼저 알리고 진행하세요.
            """)
    public String buildFullCrudPrompt(String database, String tableName,
                                      String domain, String packageName,
                                      String outputPath, String llmProvider,
                                      @Nullable String egovVersion) {
        // egovVersion 기본값 처리 (미입력 시 5.0)
        String resolved = (egovVersion == null || egovVersion.isBlank()) ? "5.0" : egovVersion;

        // llmProvider 기본값 처리
        String provider = (llmProvider == null || llmProvider.isBlank()) ? "claude" : llmProvider.trim().toLowerCase();

        if ("auto".equals(provider)) {
            return orchestrateAuto(database, tableName, domain, packageName, outputPath, resolved);
        }
        // "claude" 또는 그 외 — 기존 프롬프트 반환 방식
        return crudPromptBuilderService.buildFullCrudPrompt(
            database, tableName, domain, packageName, outputPath, resolved);
    }

    /**
     * auto 모드: Tool 내부에서 11개 파일을 직접 생성·저장합니다.
     * LLM 개입 없이 결정적으로 소스를 생성하므로 Claude 토큰을 대폭 절감합니다.
     */
    private String orchestrateAuto(String database, String tableName,
                                   String domain, String packageName, String outputPath,
                                   String egovVersion) {
        log.info("[auto] CRUD 오케스트레이션 시작: table={}, domain={}, outputPath={}, egovVersion={}", tableName, domain, outputPath, egovVersion);

        // 1. 플레이스홀더 값 계산
        PlaceholderValues pv = crudPromptBuilderService.buildPlaceholderValues(
            database, tableName, domain, packageName, outputPath, egovVersion);
        if (pv == null) {
            return "테이블을 찾을 수 없습니다: " + database + "." + tableName;
        }
        Map<String, String> values = pv.toMap();

        // 패키지 서브 경로 (egovframework.let.emp → emp)
        String pkgSub = packageName.replace("egovframework.let.", "").replace(".", "/");

        // 2. 11개 레이어 순서대로 생성·저장
        StringBuilder result = new StringBuilder();
        result.append("=== [auto] eGovFrame 5.x CRUD 소스 생성 완료 ===\n\n");
        result.append("DB: ").append(database).append(" | 테이블: ").append(tableName)
              .append(" | 도메인: ").append(domain).append("\n");
        result.append("출력 경로: ").append(outputPath).append("\n\n");
        result.append("[생성 파일 목록]\n");

        int successCount = 0;
        int failCount = 0;

        for (String[] layer : LAYERS) {
            String layerKey  = layer[0];
            String suffix    = layer[1];
            String subPath   = layer[2]
                .replace("{PKG}",       pkgSub)
                .replace("{DOMAIN_LC}", pv.domainLc());

            String fileName = resolveFileName(layerKey, domain, suffix);
            String filePath = outputPath + "/" + subPath + fileName;

            try {
                // 템플릿 치환 → 소스 생성
                String code = codeService.generateSource(layerKey, values);

                if (code.startsWith("지원하지 않는")) {
                    result.append("  ⚠ [SKIP] ").append(fileName).append(" — ").append(code).append("\n");
                    failCount++;
                    continue;
                }

                // 파일 저장
                String saveResult = codeService.saveGeneratedCode(filePath, code);
                result.append("  ✅ ").append(fileName).append("\n");
                log.info("[auto] 저장 완료: {}", filePath);
                successCount++;

            } catch (Exception e) {
                result.append("  ❌ ").append(fileName).append(" — 오류: ").append(e.getMessage()).append("\n");
                log.error("[auto] 파일 생성 실패: layer={}, file={}, error={}", layerKey, filePath, e.getMessage());
                failCount++;
            }
        }

        result.append("\n총 ").append(successCount).append("개 성공");
        if (failCount > 0) result.append(", ").append(failCount).append("개 실패");
        result.append("\n");

        // 3. 생성된 코드 일괄 검증
        result.append("\n[코드 검증 결과]\n");
        try {
            String validation = codeValidatorService.validateDirectory(outputPath);
            result.append(validation).append("\n");
        } catch (Exception e) {
            result.append("검증 실패: ").append(e.getMessage()).append("\n");
            log.warn("[auto] 코드 검증 실패: {}", e.getMessage());
        }

        // 4. 생성 이력 저장
        try {
            String historyResult = generationHistoryService.saveHistory(
                tableName, domain, packageName, outputPath, successCount + "개 파일");
            result.append("\n[생성 이력]\n").append(historyResult).append("\n");
        } catch (Exception e) {
            result.append("\n생성 이력 저장 실패: ").append(e.getMessage()).append("\n");
            log.warn("[auto] 생성 이력 저장 실패: {}", e.getMessage());
        }

        log.info("[auto] CRUD 오케스트레이션 완료: successCount={}, failCount={}", successCount, failCount);
        return result.toString();
    }

    @Tool(description = """
            1:N 마스터-디테일 구조의 eGovFrame CRUD 소스 생성 지시를 반환합니다.
            마스터 테이블 상세화면에 디테일 테이블 목록 그리드 탭이 포함됩니다.
            getTableRelations()에서 자식 테이블이 탐지된 경우 이 Tool을 사용하세요.
            database    : 데이터베이스명 (예: com)
            masterTable : 마스터(부모) 테이블명 (예: COMTNEMPLYRINFO)
            detailTable : 디테일(자식) 테이블명 (예: COMTNEMPLYRATTRBINFO)
            domain      : 마스터 도메인명 대문자 시작 (예: Employer)
            packageName : 패키지명 (예: egovframework.let.emp)
            outputPath  : 소스 저장 절대경로 (예: /Users/user/Desktop/egov-gen/emp)
            생성 파일: 마스터 VO+Mapper+Service+ServiceImpl+Controller + 디테일 VO+Mapper + JSP 5개 (총 12개)

            [중요] outputPath 결정 규칙 — 반드시 아래 순서를 따르세요:
            1. 사용자가 저장 경로를 명시한 경우 → 그 경로를 그대로 사용
            2. 사용자가 기존 프로젝트 경로를 알려준 경우 → resolveProjectOutputPath() 먼저 호출하여 경로 확정
            3. 경로를 모르거나 언급이 없는 경우 → getDefaultOutputPath(domain) 호출하여 기본 경로 사용
               (기본 경로: ~/Desktop/egov-generated/{domain})
            절대로 경로를 임의로 결정하거나 추측하지 마세요.
            outputPath를 확정한 후 사용자에게 "이 경로에 생성합니다: {path}" 라고 먼저 알리고 진행하세요.
            """)
    public String buildMasterDetailPrompt(String database, String masterTable, String detailTable,
                                          String domain, String packageName, String outputPath) {
        return masterDetailService.buildMasterDetailPrompt(
            database, masterTable, detailTable, domain, packageName, outputPath);
    }

    @Tool(description = """
            단일 테이블에 JOIN이 필요한 경우 SELECT 쿼리·resultMap·VO 추가 필드를 자동 생성합니다.
            getTableRelations()에서 공통코드·부서 등 JOIN 후보 컬럼이 탐지된 경우 사용하세요.
            기존 buildFullCrudPrompt()로 생성된 소스에 JOIN을 추가할 때 활용합니다.
            database  : 데이터베이스명 (예: com)
            tableName : JOIN을 추가할 테이블명 (예: COMTNEMPLYRINFO)
            반환값: JOIN SELECT 쿼리 초안 + resultMap 추가 항목 + VO 추가 필드 목록
            """)
    public String buildJoinSelectPrompt(String database, String tableName) {
        return masterDetailService.buildJoinSelectPrompt(database, tableName);
    }
}
