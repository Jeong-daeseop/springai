package com.krdevops.springai.service.menu;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class MenuRepository {

    private final JdbcTemplate jdbcTemplate;

    public boolean existsUpperMenu(int upperMenuNo) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM COMTNMENUINFO WHERE MENU_NO = ?",
                Integer.class, upperMenuNo);
        return count != null && count > 0;
    }

    public boolean existsProgrmFileNm(String progrmFileNm) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM COMTNPROGRMLIST WHERE PROGRM_FILE_NM = ?",
                Integer.class, progrmFileNm);
        return count != null && count > 0;
    }

    public boolean existsUrl(String url) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM COMTNPROGRMLIST WHERE URL = ?",
                Integer.class, url);
        return count != null && count > 0;
    }

    public BigDecimal findMaxMenuNo(int upperMenuNo) {
        BigDecimal max = jdbcTemplate.queryForObject(
                "SELECT MAX(MENU_NO) FROM COMTNMENUINFO WHERE UPPER_MENU_NO = ?",
                BigDecimal.class, upperMenuNo);
        return max != null ? max : BigDecimal.ZERO;
    }

    public BigDecimal findMaxMenuOrdr(int upperMenuNo) {
        BigDecimal max = jdbcTemplate.queryForObject(
                "SELECT MAX(MENU_ORDR) FROM COMTNMENUINFO WHERE UPPER_MENU_NO = ?",
                BigDecimal.class, upperMenuNo);
        return max != null ? max : BigDecimal.ZERO;
    }

    public List<Map<String, Object>> findMenuByNo(int menuNo) {
        return jdbcTemplate.queryForList(
                "SELECT MENU_NO, MENU_NM, UPPER_MENU_NO, MENU_ORDR FROM COMTNMENUINFO WHERE MENU_NO = ?",
                menuNo);
    }

    public List<Map<String, Object>> findRootMenus() {
        return jdbcTemplate.queryForList(
                "SELECT MENU_NO, MENU_NM, UPPER_MENU_NO, MENU_ORDR FROM COMTNMENUINFO WHERE UPPER_MENU_NO = 0 ORDER BY MENU_ORDR",
                (Object[]) null);
    }

    public List<Map<String, Object>> findChildMenus(String upperMenuNo) {
        return jdbcTemplate.queryForList(
                "SELECT MENU_NO, MENU_NM, UPPER_MENU_NO, MENU_ORDR FROM COMTNMENUINFO WHERE UPPER_MENU_NO = ? ORDER BY MENU_ORDR",
                upperMenuNo);
    }
}
