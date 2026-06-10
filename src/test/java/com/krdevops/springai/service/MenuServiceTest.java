package com.krdevops.springai.service;

import com.krdevops.springai.service.menu.MenuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

    @Mock
    private MenuRepository menuRepository;

    private MenuService menuService;

    @BeforeEach
    void setUp() {
        menuService = new MenuService(menuRepository);
    }

    @Test
    void generateMenuInsertSql_정상입력_COMTNPROGRMLIST_SQL_포함() {
        when(menuRepository.existsUpperMenu(100)).thenReturn(true);
        when(menuRepository.existsProgrmFileNm("EgovEmpList")).thenReturn(false);
        when(menuRepository.existsUrl("/emp/employer/EgovEmpList.do")).thenReturn(false);
        when(menuRepository.findMaxMenuNo(100)).thenReturn(BigDecimal.valueOf(1000000));
        when(menuRepository.findMaxMenuOrdr(100)).thenReturn(BigDecimal.valueOf(5));

        String result = menuService.generateMenuInsertSql("100", "/emp/employer", "직원관리", "EgovEmpList");

        assertThat(result).contains("INSERT INTO COMTNPROGRMLIST");
        assertThat(result).contains("EgovEmpList");
        assertThat(result).contains("/emp/employer/EgovEmpList.do");
    }

    @Test
    void generateMenuInsertSql_정상입력_COMTNMENUINFO_SQL_포함() {
        when(menuRepository.existsUpperMenu(100)).thenReturn(true);
        when(menuRepository.existsProgrmFileNm("EgovEmpList")).thenReturn(false);
        when(menuRepository.existsUrl(anyString())).thenReturn(false);
        when(menuRepository.findMaxMenuNo(100)).thenReturn(BigDecimal.valueOf(1000000));
        when(menuRepository.findMaxMenuOrdr(100)).thenReturn(BigDecimal.valueOf(5));

        String result = menuService.generateMenuInsertSql("100", "/emp/employer", "직원관리", "EgovEmpList");

        assertThat(result).contains("INSERT INTO COMTNMENUINFO");
        assertThat(result).contains("직원관리");
        assertThat(result).contains("1010000");
        // COMTNMENUINFO는 URL 컬럼 없음 — PROGRM_FILE_NM으로 프로그램 연결
        assertThat(result).contains("PROGRM_FILE_NM");
    }

    @Test
    void generateMenuInsertSql_upperMenuNo_null_오류반환() {
        String result = menuService.generateMenuInsertSql(null, "/emp/employer", "직원관리", "EgovEmpList");
        assertThat(result).startsWith("오류:");
        assertThat(result).contains("upperMenuNo");
    }

    @Test
    void generateMenuInsertSql_upperMenuNo_비숫자_오류반환() {
        String result = menuService.generateMenuInsertSql("abc", "/emp/employer", "직원관리", "EgovEmpList");
        assertThat(result).startsWith("오류:");
        assertThat(result).contains("숫자");
    }

    @Test
    void generateMenuInsertSql_upperMenuNo_존재하지않으면_오류반환() {
        when(menuRepository.existsUpperMenu(999)).thenReturn(false);

        String result = menuService.generateMenuInsertSql("999", "/emp/employer", "직원관리", "EgovEmpList");
        assertThat(result).startsWith("오류:");
        assertThat(result).contains("999");
    }

    @Test
    void generateMenuInsertSql_progrmFileNm_중복이면_오류반환() {
        when(menuRepository.existsUpperMenu(100)).thenReturn(true);
        when(menuRepository.existsProgrmFileNm("EgovEmpList")).thenReturn(true);

        String result = menuService.generateMenuInsertSql("100", "/emp/employer", "직원관리", "EgovEmpList");
        assertThat(result).startsWith("오류:");
        assertThat(result).contains("EgovEmpList");
    }

    @Test
    void generateMenuInsertSql_URL_중복이면_경고반환() {
        when(menuRepository.existsUpperMenu(100)).thenReturn(true);
        when(menuRepository.existsProgrmFileNm("EgovEmpList")).thenReturn(false);
        when(menuRepository.existsUrl("/emp/employer/EgovEmpList.do")).thenReturn(true);

        String result = menuService.generateMenuInsertSql("100", "/emp/employer", "직원관리", "EgovEmpList");
        assertThat(result).contains("경고");
        assertThat(result).contains("/emp/employer/EgovEmpList.do");
    }

    @Test
    void generateMenuInsertSql_menuNm_singleQuote_escape() {
        when(menuRepository.existsUpperMenu(100)).thenReturn(true);
        when(menuRepository.existsProgrmFileNm(anyString())).thenReturn(false);
        when(menuRepository.existsUrl(anyString())).thenReturn(false);
        when(menuRepository.findMaxMenuNo(100)).thenReturn(BigDecimal.ZERO);
        when(menuRepository.findMaxMenuOrdr(100)).thenReturn(BigDecimal.ZERO);

        String result = menuService.generateMenuInsertSql("100", "/emp", "직원's 관리", "EgovEmpList");

        assertThat(result).contains("직원''s 관리");
        assertThat(result).doesNotContain("직원's 관리");
    }

    @Test
    void generateMenuInsertSql_올바른_스키마_컬럼명_사용() {
        when(menuRepository.existsUpperMenu(100)).thenReturn(true);
        when(menuRepository.existsProgrmFileNm(anyString())).thenReturn(false);
        when(menuRepository.existsUrl(anyString())).thenReturn(false);
        when(menuRepository.findMaxMenuNo(100)).thenReturn(BigDecimal.ZERO);
        when(menuRepository.findMaxMenuOrdr(100)).thenReturn(BigDecimal.ZERO);

        String result = menuService.generateMenuInsertSql("100", "/emp/employer", "직원관리", "EgovEmpList");

        // COMTNPROGRMLIST: PROGRM_STRE_PATH (STRE_PATH 단독 사용 금지)
        assertThat(result).contains("PROGRM_STRE_PATH");
        assertThat(result).contains("/emp/employer/");
        // COMTNMENUINFO: URL 컬럼 없음, PROGRM_FILE_NM으로 연결
        assertThat(result).contains("PROGRM_FILE_NM");
    }

    @Test
    void validateMenuNo_blank_오류반환() {
        String result = menuService.getMenuStructure("  ");
        assertThat(result).startsWith("오류:");
    }

    @Test
    void validateMenuNo_비숫자_오류반환() {
        String result = menuService.getMenuStructure("abc");
        assertThat(result).startsWith("오류:");
        assertThat(result).contains("숫자");
    }
}
