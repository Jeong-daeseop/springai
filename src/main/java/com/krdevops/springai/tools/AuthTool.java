package com.krdevops.springai.tools;

import com.krdevops.springai.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthTool {

    private final AuthService authService;

    @Tool(description = """
            COMTNPROGRMLIST 에서 프로그램 목록을 키워드로 검색합니다.
            한국어명 / URL / PROGRM_FILE_NM 기준 LIKE 검색을 수행합니다.
            generateMenuInsertSql() 호출 전 중복 등록 여부를 반드시 확인하세요.
            PROGRM_FILE_NM은 PK이므로 중복 시 INSERT 오류가 발생합니다.
            keyword: 검색어 (예: "employer", "직원", "EgovEmployer")
            """)
    public String getProgramList(String keyword) {
        return authService.getProgramList(keyword);
    }

    @Tool(description = """
            신규 도메인 URL 접근 제어에 필요한 SQL 3개를 반환합니다.
            SQL 1: COMTNROLEINFO INSERT (롤 등록 — ROLE_CODE 자동 계산, URL 패턴 자동 생성)
            SQL 2: COMTNAUTHORROLERELATE INSERT (ROLE_ADMIN 권한에 롤 연결)
            SQL 3: 추가 권한 그룹 연결 예시 (주석 처리, 필요 시 활성화)
            이 Tool은 SQL을 반환만 합니다. 직접 DB에 INSERT하지 않으므로 사용자가 검토 후 실행하세요.
            SQL 실행 후 Spring Security 재기동 또는 캐시 갱신이 필요합니다.
            urlPrefix : URL 경로 접두사 (예: "/emp/employer")
            programNm : 프로그램 한국어명 (예: "직원관리")
            domain    : 도메인 식별자 (예: "emp") — 롤 설명에 사용됨
            """)
    public String generateAuthInsertSql(String urlPrefix, String programNm, String domain) {
        return authService.generateAuthInsertSql(urlPrefix, programNm, domain);
    }
}
