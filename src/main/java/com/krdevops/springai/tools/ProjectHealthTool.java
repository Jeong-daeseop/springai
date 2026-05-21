package com.krdevops.springai.tools;

import com.krdevops.springai.service.ProjectHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectHealthTool {

    private final ProjectHealthService projectHealthService;

    @Tool(description = """
            eGovFrame 프로젝트의 특정 도메인 소스 생성 완성도를 점검합니다.
            projectRootPath: 프로젝트 루트 절대경로 (예: /Users/user/project) 또는 소스 출력 경로
            domain: 도메인명 소문자 (예: employer, bbs, board)
            점검 항목:
              - 레이어별 파일 존재 여부 (VO/Mapper/MapperXML/Service/ServiceImpl/Controller/JSP 4종)
              - eGovFrame 표준 준수 여부 (validateGeneratedCode 연동)
              - COMTNMENUINFO 메뉴 등록 여부
              - COMTNPROGRMLIST 권한 등록 여부
              - 테스트 코드 존재 여부
            반환값: 완성도 %(점수), 항목별 ✅/❌, 다음 권장 작업 목록
            소스 생성 완료 후 최종 점검 또는 작업 재개 전 현황 파악 시 사용합니다.
            """)
    public String checkProjectHealth(String projectRootPath, String domain) {
        return projectHealthService.checkProjectHealth(projectRootPath, domain);
    }
}
