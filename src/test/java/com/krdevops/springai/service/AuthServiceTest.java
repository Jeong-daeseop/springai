package com.krdevops.springai.service;

import com.krdevops.springai.service.auth.AuthRepository;
import com.krdevops.springai.service.auth.AuthSqlBuilder;
import com.krdevops.springai.service.sql.DbDialect;
import com.krdevops.springai.service.sql.SqlDialectRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthRepository authRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(authRepository);
    }

    @Test
    void generateAuthInsertSql_정상입력_COMTNROLEINFO_SQL_포함() {
        when(authRepository.findNextRoleNum(any())).thenReturn(42);

        String result = authService.generateAuthInsertSql("/emp/employer", "직원관리", "emp");

        assertThat(result).contains("INSERT INTO COMTNROLEINFO");
        assertThat(result).contains("web-000042");
    }

    @Test
    void generateAuthInsertSql_정상입력_COMTNAUTHORROLERELATE_SQL_포함() {
        when(authRepository.findNextRoleNum(any())).thenReturn(1);

        String result = authService.generateAuthInsertSql("/emp/employer", "직원관리", "emp");

        assertThat(result).contains("INSERT INTO COMTNAUTHORROLERELATE");
        assertThat(result).contains("ROLE_ADMIN");
    }

    @Test
    void generateAuthInsertSql_ROLE_PTTRN_올바른_패턴_포함() {
        when(authRepository.findNextRoleNum(any())).thenReturn(1);

        String result = authService.generateAuthInsertSql("/emp/employer", "직원관리", "emp");

        assertThat(result).contains("\\A/emp/employer/.*\\.do.*\\Z");
    }

    @Test
    void generateAuthInsertSql_ROLE_SORT_숫자_출력() {
        when(authRepository.findNextRoleNum(any())).thenReturn(1);

        String result = authService.generateAuthInsertSql("/emp/employer", "직원관리", "emp");

        assertThat(result).contains("1, ");
        assertThat(result).doesNotContain("'1'");
    }

    @Test
    void generateAuthInsertSql_securityMapper_안내_포함() {
        when(authRepository.findNextRoleNum(any())).thenReturn(1);

        String result = authService.generateAuthInsertSql("/emp/employer", "직원관리", "emp");

        assertThat(result).contains("securityMapper");
    }

    @Test
    void generateAuthInsertSql_ROLE_CODE_재확인_안내_포함() {
        when(authRepository.findNextRoleNum(any())).thenReturn(1);

        String result = authService.generateAuthInsertSql("/emp/employer", "직원관리", "emp");

        assertThat(result).contains("중복 여부를 재확인");
    }

    @Test
    void generateAuthInsertSql_urlPrefix_null_오류반환() {
        String result = authService.generateAuthInsertSql(null, "직원관리", "emp");
        assertThat(result).startsWith("오류:");
        assertThat(result).contains("urlPrefix");
    }

    @Test
    void generateAuthInsertSql_programNm_blank_오류반환() {
        String result = authService.generateAuthInsertSql("/emp/employer", "  ", "emp");
        assertThat(result).startsWith("오류:");
        assertThat(result).contains("programNm");
    }

    @Test
    void generateAuthInsertSql_programNm_singleQuote_escape() {
        when(authRepository.findNextRoleNum(any())).thenReturn(1);

        String result = authService.generateAuthInsertSql("/emp", "직원's 관리", "emp");

        assertThat(result).contains("직원''s 관리");
        assertThat(result).doesNotContain("직원's 관리");
    }

    @Test
    void getProgramList_keyword_null_오류없이반환() {
        when(authRepository.searchPrograms(isNull(), any())).thenReturn(List.of());

        String result = authService.getProgramList(null);

        assertThat(result).isNotNull();
        assertThat(result).doesNotStartWith("오류:");
    }

    @Test
    void buildRolePttrn_positive_url_매칭() {
        AuthSqlBuilder builder = new AuthSqlBuilder(new SqlDialectRenderer(DbDialect.MYSQL_MARIADB));
        String pattern = builder.buildRolePttrn("/emp/employer");

        assertThat(pattern).isEqualTo("\\A/emp/employer/.*\\.do.*\\Z");
        assertThat("/emp/employer/EgovEmpList.do").matches(pattern);
    }

    @Test
    void buildRolePttrn_negative_url_비매칭() {
        AuthSqlBuilder builder = new AuthSqlBuilder(new SqlDialectRenderer(DbDialect.MYSQL_MARIADB));
        String pattern = builder.buildRolePttrn("/emp/employer");

        assertThat("/emp/other/EgovOtherList.do").doesNotMatch(pattern);
        assertThat("/admin/employer/EgovEmpList.do").doesNotMatch(pattern);
    }
}
