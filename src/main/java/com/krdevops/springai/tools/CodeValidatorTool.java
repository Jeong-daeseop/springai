package com.krdevops.springai.tools;

import com.krdevops.springai.service.CodeValidatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CodeValidatorTool {

    private final CodeValidatorService codeValidatorService;

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
              *.jsp             → UTF-8, JSTL, <c:url>, pageIndex 등
            미준수 항목은 ❌, 통과 항목은 ✅ 로 표시합니다.
            """)
    public String validateGeneratedCode(String filePath) {
        return codeValidatorService.validateFile(filePath);
    }

    @Tool(description = """
            디렉터리 내 eGovFrame 5.x 소스 파일 전체를 일괄 검증합니다.
            directoryPath: 검증할 디렉터리 절대경로 (하위 디렉터리 포함)
            .java / .xml / .jsp 파일을 자동으로 탐색하여 레이어별 표준 준수 여부를 검사합니다.
            미준수 항목이 있는 파일만 상세 내용을 출력합니다.
            """)
    public String validateGeneratedCodeDirectory(String directoryPath) {
        return codeValidatorService.validateDirectory(directoryPath);
    }
}
