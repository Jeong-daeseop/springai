package com.krdevops.springai.service.menu;

import com.krdevops.springai.model.MenuRegistrationSpec;
import com.krdevops.springai.model.SqlPlan;
import com.krdevops.springai.service.sql.SqlDialectRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MenuSqlBuilder {

    private final SqlDialectRenderer renderer;

    public SqlPlan build(MenuRegistrationSpec spec, BigDecimal nextMenuNo, BigDecimal nextMenuOrdr) {
        String url = spec.urlPrefix() + "/" + spec.progrmFileNm() + ".do";
        String stre = spec.urlPrefix() + "/";

        List<String> statements = new ArrayList<>();

        statements.add(buildProgramSql(spec, url, stre));
        statements.add(buildMenuSql(spec, nextMenuNo, nextMenuOrdr, url));

        List<String> warnings = new ArrayList<>();
        warnings.add("※ MENU_NO(" + nextMenuNo + "), MENU_ORDR(" + nextMenuOrdr + ")는 SQL 생성 시점 기준입니다. 실행 전 중복 여부를 재확인하세요.");
        warnings.add("※ URL(" + url + ")이 실제 Controller RequestMapping과 일치하는지 확인하세요.");

        List<String> nextSteps = List.of(
                "1. 위 SQL을 DB에서 실행하세요.",
                "2. AuthTool.generateAuthInsertSql() 을 호출하여 URL 권한 SQL을 생성하세요.",
                "3. 권한 SQL 실행 후 서버 재기동 또는 Security 캐시 갱신이 필요합니다.",
                "4. 메뉴 노출 및 URL 접근 테스트를 수행하세요."
        );

        return new SqlPlan("메뉴/프로그램 등록 SQL", statements, warnings, nextSteps);
    }

    private String buildProgramSql(MenuRegistrationSpec spec, String url, String stre) {
        // 스키마: COMTNPROGRMLIST (PROGRM_FILE_NM, PROGRM_STRE_PATH, PROGRM_KOREAN_NM, PROGRM_DC, URL)
        return "INSERT INTO COMTNPROGRMLIST (" +
                "PROGRM_FILE_NM, PROGRM_STRE_PATH, PROGRM_KOREAN_NM, PROGRM_DC, URL) VALUES (" +
                "'" + esc(spec.progrmFileNm()) + "', " +
                "'" + esc(stre) + "', " +
                "'" + esc(spec.menuNm()) + "', " +
                "'" + esc(spec.menuNm()) + " 프로그램', " +
                "'" + esc(url) + "');";
    }

    private String buildMenuSql(MenuRegistrationSpec spec, BigDecimal nextMenuNo,
                                 BigDecimal nextMenuOrdr, String url) {
        // 스키마: COMTNMENUINFO (MENU_NO, UPPER_MENU_NO, MENU_NM, PROGRM_FILE_NM, MENU_ORDR)
        // URL 컬럼 없음 — PROGRM_FILE_NM으로 프로그램 연결
        return "INSERT INTO COMTNMENUINFO (" +
                "MENU_NO, UPPER_MENU_NO, MENU_NM, PROGRM_FILE_NM, MENU_ORDR) VALUES (" +
                nextMenuNo + ", " +
                spec.upperMenuNo() + ", " +
                "'" + esc(spec.menuNm()) + "', " +
                "'" + esc(spec.progrmFileNm()) + "', " +
                nextMenuOrdr + ");";
    }

    private String esc(String value) {
        return value == null ? "" : value.replace("'", "''");
    }
}
