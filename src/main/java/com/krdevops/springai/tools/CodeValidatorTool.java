package com.krdevops.springai.tools;

import com.krdevops.springai.config.mcp.McpToolRisk;
import com.krdevops.springai.config.mcp.McpToolRiskLevel;

import com.krdevops.springai.service.CodeValidatorService;
import com.krdevops.springai.service.GeneratedCodeContractAuditor;
import com.krdevops.springai.service.GeneratedProjectBuildValidator;
import com.krdevops.springai.service.ThymeleafRenderValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CodeValidatorTool {

    private final CodeValidatorService codeValidatorService;
    private final GeneratedCodeContractAuditor generatedCodeContractAuditor;
    private final ThymeleafRenderValidator thymeleafRenderValidator;
    private final GeneratedProjectBuildValidator generatedProjectBuildValidator;

    @McpToolRisk(McpToolRiskLevel.READ)
    @Tool(description = """
            생성된 eGovFrame 5.x 소스 파일 단건을 검증합니다.
            filePath: 검증할 파일의 절대경로
            파일 종류에 따라 아래 규칙을 자동으로 적용합니다:
              *Controller.java  → @Controller, @RequestMapping, EgovPropertyService, PaginationInfo 등
              *ServiceImpl.java → EgovAbstractServiceImpl 상속, @Transactional, @Override 등
              *Service.java     → interface 선언, 목록/건수 메서드, throws Exception 등
              *Mapper.java      → @Mapper, EgovAbstractMapper 상속 등
              *VO.java          → @Getter/@Setter, PaginationInfo, 검색 필드 등
              *Mapper.xml       → namespace, resultMap, 페이징 LIMIT, searchCondition 등
              *List.jsp         → UTF-8, JSTL, <c:url>, pageIndex 등
              *Detail/Regist/Updt.jsp → UTF-8, JSTL, <c:url> 등 (pageIndex 검사 없음)
            미준수 항목은 ❌, 통과 항목은 ✅ 로 표시합니다.
            """)
    public String validateGeneratedCode(String filePath) {
        return codeValidatorService.validateFile(filePath);
    }

    @McpToolRisk(McpToolRiskLevel.READ)
    @Tool(description = """
            디렉터리 내 eGovFrame 5.x 소스 파일 전체를 일괄 검증합니다.
            directoryPath: 검증할 디렉터리 절대경로 (하위 디렉터리 포함)
            .java / .xml / .jsp 파일을 자동으로 탐색하여 레이어별 표준 준수 여부를 검사합니다.
            미준수 항목이 있는 파일만 상세 내용을 출력합니다.
            """)
    public String validateGeneratedCodeDirectory(String directoryPath) {
        return codeValidatorService.validateDirectory(directoryPath);
    }

    @McpToolRisk(McpToolRiskLevel.READ)
    @Tool(description = """
            생성 결과 디렉터리의 HTML을 실제 Thymeleaf 엔진과 기본 fixture 모델로 렌더링합니다.
            템플릿 식 구문 오류와 렌더링 시점 오류를 파일별로 반환합니다.
            실제 업무 모델이 필요한 오류는 fixture를 보완해 다시 검사해야 합니다.
            """)
    public ThymeleafRenderValidator.RenderReport validateThymeleafRendering(String directoryPath) {
        return thymeleafRenderValidator.validateDirectory(directoryPath);
    }

    @McpToolRisk(McpToolRiskLevel.READ)
    @Tool(description = """
            생성 결과의 공통 계약과 기본 접근성 계약을 함께 검사합니다.
            FreeMarker/Claude Design 잔존 태그, Mapper ${} 치환, 외부 URL과
            html lang, img alt, 이름 없는 button을 진단합니다.
            """)
    public QualityAuditReport auditGeneratedQuality(String directoryPath) {
        return new QualityAuditReport(
                generatedCodeContractAuditor.audit(directoryPath),
                generatedCodeContractAuditor.auditAccessibility(directoryPath));
    }

    @McpToolRisk(McpToolRiskLevel.READ)
    @Tool(description = """
            생성된 eGovFrame 프로젝트를 별도 Maven/Gradle 프로세스로 컴파일 검증합니다.
            보안을 위해 기본 비활성이며, 운영자가 EGOV_ALLOW_BUILD_EXECUTION=true를 설정하고
            egov.output 허용 경로 안의 프로젝트를 지정한 경우에만 실행됩니다.
            Maven은 offline compile, Gradle은 offline compileJava를 직접 인자로 실행합니다.
            """)
    public GeneratedProjectBuildValidator.BuildValidationReport validateGeneratedProjectBuild(
            String projectRootPath) {
        return generatedProjectBuildValidator.validate(projectRootPath);
    }

    public record QualityAuditReport(
            java.util.List<String> contractFailures,
            java.util.List<String> accessibilityFailures) {
        public boolean passed() {
            return contractFailures.isEmpty() && accessibilityFailures.isEmpty();
        }
    }
}
