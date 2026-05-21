package com.krdevops.springai.tools;

import com.krdevops.springai.service.OutputPathResolverService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutputPathResolverTool {

    private final OutputPathResolverService outputPathResolverService;

    @Tool(description = """
            CRUD 소스를 저장할 기본 경로를 반환합니다.
            사용자가 저장 경로를 모르거나 별도 프로젝트가 없을 때 사용하세요.
            기본 경로: ~/Desktop/egov-generated/{domainLc}
            환경변수 EGOV_OUTPUT_PATH로 변경 가능합니다.

            domain: 도메인명 대문자 시작 (예: Employee)

            반환값을 buildFullCrudPrompt()의 outputPath 파라미터로 그대로 사용하세요.
            사용자가 경로를 지정하지 않았을 때 이 Tool을 먼저 호출하여 경로를 확정한 후 CRUD 생성을 시작하세요.
            """)
    public String getDefaultOutputPath(String domain) {
        return outputPathResolverService.resolveDefault(domain);
    }

    @Tool(description = """
            기존 eGovFrame 프로젝트를 스캔하여 CRUD 소스를 저장할 실제 경로를 분석합니다.
            사용자가 기존 프로젝트에 직접 소스를 추가하려 할 때 사용하세요.

            projectRootPath: 기존 eGovFrame 프로젝트 루트 절대경로 (예: /Users/user/workspace/my-egov-project)
            packageName    : 패키지명 (예: egovframework.let.emp)
            domain         : 도메인명 대문자 시작 (예: Employee)

            반환값:
              - 레이어별 실제 저장 경로 (VO / Mapper / Service / ServiceImpl / Controller / MapperXml / JSP)
              - buildFullCrudPrompt()의 outputPath 권장값

            사용자가 "기존 프로젝트에 넣어줘" 또는 프로젝트 경로를 제공한 경우 이 Tool을 먼저 호출하세요.
            """)
    public String resolveProjectOutputPath(String projectRootPath, String packageName, String domain) {
        return outputPathResolverService.resolveFromProject(projectRootPath, packageName, domain);
    }
}
