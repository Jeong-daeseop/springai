package com.krdevops.springai.service.menu;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 메뉴/프로그램 조회는 호출 시점에 실제 존재하는 LETTN* 테이블을 사용한다.
 * 테이블명은 {@link com.krdevops.springai.service.ProgramMetadataQueryService}가 화이트리스트
 * 후보군에서 탐지한 값만 전달받으므로 문자열 결합으로 SQL에 포함해도 injection 위험이 없다.
 */
@Repository
@RequiredArgsConstructor
public class MenuRepository {

    private final JdbcTemplate jdbcTemplate;

    public boolean existsUpperMenu(int upperMenuNo, String menuTable) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + menuTable + " WHERE MENU_NO = ?",
                Integer.class, upperMenuNo);
        return count != null && count > 0;
    }

    public boolean existsProgrmFileNm(String progrmFileNm, String programTable) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + programTable + " WHERE PROGRM_FILE_NM = ?",
                Integer.class, progrmFileNm);
        return count != null && count > 0;
    }

    public boolean existsUrl(String url, String programTable) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + programTable + " WHERE URL = ?",
                Integer.class, url);
        return count != null && count > 0;
    }

    public BigDecimal findMaxMenuNo(int upperMenuNo, String menuTable) {
        BigDecimal max = jdbcTemplate.queryForObject(
                "SELECT MAX(MENU_NO) FROM " + menuTable + " WHERE UPPER_MENU_NO = ?",
                BigDecimal.class, upperMenuNo);
        return max != null ? max : BigDecimal.ZERO;
    }

    public BigDecimal findMaxMenuOrdr(int upperMenuNo, String menuTable) {
        BigDecimal max = jdbcTemplate.queryForObject(
                "SELECT MAX(MENU_ORDR) FROM " + menuTable + " WHERE UPPER_MENU_NO = ?",
                BigDecimal.class, upperMenuNo);
        return max != null ? max : BigDecimal.ZERO;
    }

    public List<Map<String, Object>> findMenuByNo(int menuNo, String menuTable) {
        return jdbcTemplate.queryForList(
                "SELECT MENU_NO, MENU_NM, UPPER_MENU_NO, MENU_ORDR FROM " + menuTable + " WHERE MENU_NO = ?",
                menuNo);
    }

    public List<Map<String, Object>> findRootMenus(String menuTable) {
        return jdbcTemplate.queryForList(
                "SELECT MENU_NO, MENU_NM, UPPER_MENU_NO, MENU_ORDR FROM " + menuTable
                        + " WHERE UPPER_MENU_NO = 0 AND MENU_NO != 0 ORDER BY MENU_ORDR",
                (Object[]) null);
    }

    public List<Map<String, Object>> findChildMenus(String upperMenuNo, String menuTable) {
        return jdbcTemplate.queryForList(
                "SELECT MENU_NO, MENU_NM, UPPER_MENU_NO, MENU_ORDR FROM " + menuTable
                        + " WHERE UPPER_MENU_NO = ? ORDER BY MENU_ORDR",
                upperMenuNo);
    }
}