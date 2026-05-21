package com.krdevops.springai.tools;

import com.krdevops.springai.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MenuTool {

    private final MenuService menuService;

    @Tool(description = """
            eGovFrame COMTNMENUINFO 테이블 기준 메뉴 트리 구조를 조회합니다.
            menuNo = "0" 이면 전체 트리를 반환합니다.
            특정 menuNo를 지정하면 해당 메뉴의 하위 트리를 반환합니다.
            반환값에 신규 등록 시 권장 MENU_NO / MENU_ORDR 이 자동 계산되어 포함됩니다.
            menuNo: 조회할 메뉴 번호 (예: "0"=전체, "6000000"=시스템관리 하위)
            새 메뉴 등록 위치 파악 후 generateMenuInsertSql() 을 호출하세요.
            """)
    public String getMenuStructure(String menuNo) {
        return menuService.getMenuStructure(menuNo);
    }

    @Tool(description = """
            신규 메뉴 등록에 필요한 SQL 2개를 반환합니다.
            SQL 1: COMTNPROGRMLIST INSERT (프로그램 등록)
            SQL 2: COMTNMENUINFO INSERT (메뉴 등록)
            MENU_NO와 MENU_ORDR은 기존 최대값 기준으로 자동 계산됩니다.
            이 Tool은 SQL을 반환만 합니다. 직접 DB에 INSERT하지 않으므로 사용자가 검토 후 실행하세요.
            upperMenuNo : 상위 메뉴 번호 (예: "6000000")
            urlPrefix   : URL 경로 접두사 (예: "/emp/employer")
            menuNm      : 메뉴명 (예: "직원관리")
            progrmFileNm: 프로그램 파일명 (예: "EgovEmployerList") — getProgramList()로 중복 확인 필수
            """)
    public String generateMenuInsertSql(String upperMenuNo, String urlPrefix,
                                         String menuNm, String progrmFileNm) {
        return menuService.generateMenuInsertSql(upperMenuNo, urlPrefix, menuNm, progrmFileNm);
    }
}
