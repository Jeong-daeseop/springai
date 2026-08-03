package com.krdevops.springai.tools;

import com.krdevops.springai.config.mcp.McpToolRisk;
import com.krdevops.springai.config.mcp.McpToolRiskLevel;

import com.krdevops.springai.service.ProjectScannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectScannerTool {

    private final ProjectScannerService projectScannerService;

    @McpToolRisk(McpToolRiskLevel.READ)
    @Tool(description = """
            기존 eGovFrame 프로젝트의 구조를 스캔하여 소스 생성에 필요한 정보를 반환합니다.
            projectRootPath: 스캔할 eGovFrame 프로젝트의 루트 절대경로
            반환 정보:
              - 패키지 루트 (예: egovframework.let)
              - 도메인 목록 (예: emp, bbs, board ...)
              - URL 패턴 샘플 (기존 @RequestMapping 값)
              - 레이어별 디렉터리 경로 (web/service/impl/mapper/mapperXml/jsp)
              - 주요 설정 파일 목록 (context-*.xml, web.xml 등)
              - 소스 통계 (Controller/ServiceImpl/Mapper/VO/MapperXml/JSP 파일 수)
            eGovFrame CRUD 소스 생성 전에 호출하여 패키지명, URL 패턴, 출력 경로를 정확히 파악하세요.
            """)
    public String scanProjectStructure(String projectRootPath) {
        return projectScannerService.scanProjectStructure(projectRootPath);
    }
}
