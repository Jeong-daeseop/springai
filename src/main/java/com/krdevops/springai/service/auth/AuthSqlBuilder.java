package com.krdevops.springai.service.auth;

import com.krdevops.springai.model.AuthRegistrationSpec;
import com.krdevops.springai.model.SqlPlan;
import com.krdevops.springai.service.sql.SqlDialectRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AuthSqlBuilder {

    private final SqlDialectRenderer renderer;

    public SqlPlan build(AuthRegistrationSpec spec, int nextRoleNum) {
        String roleCode = String.format("web-%06d", nextRoleNum);
        String roleNm = spec.domain() + " " + spec.programNm() + " 접근권한";
        String rolePttrn = buildRolePttrn(spec.urlPrefix());

        List<String> statements = new ArrayList<>();

        statements.add(buildRoleInfoSql(roleCode, roleNm, rolePttrn, spec));
        statements.add(buildAuthorRoleRelateSql(roleCode));
        statements.add("-- 일반 사용자 권한도 부여하려면 아래 SQL을 추가 실행하세요:");
        statements.add("-- INSERT INTO COMTNAUTHORROLERELATE (AUTHOR_CODE, ROLE_CODE, CREAT_DT) VALUES ('ROLE_USER', '" + esc(roleCode) + "', " + renderer.now() + ");");

        List<String> warnings = new ArrayList<>();
        warnings.add("※ ROLE_CODE(" + roleCode + ")는 SQL 생성 시점 기준입니다. 실행 직전 COMTNROLEINFO 중복 여부를 재확인하세요.");
        warnings.add("※ 생성된 ROLE_PTTRN: " + rolePttrn);
        warnings.add("※ 적용 대상 URL 예: " + spec.urlPrefix() + "/Example.do");
        warnings.add("※ 이 SQL은 securityMapper가 COMTNROLEINFO / COMTNAUTHORROLERELATE를 조회할 때만 Security에 반영됩니다.");

        List<String> nextSteps = List.of(
                "1. 위 SQL을 DB에서 실행하세요.",
                "2. SecurityTemplateTool에서 securityMapper 포함 조합을 먼저 생성했는지 확인하세요.",
                "3. 서버 재기동 또는 Security 캐시 갱신이 필요합니다.",
                "4. " + spec.urlPrefix() + "/*.do URL 접근 권한 테스트를 수행하세요."
        );

        return new SqlPlan("URL 권한 등록 SQL", statements, warnings, nextSteps);
    }

    /**
     * ROLE_PTTRN 생성.
     * Security Filter에서 regex로 해석됨: \A{prefix}/.*\.do.*\Z
     */
    public String buildRolePttrn(String urlPrefix) {
        return "\\A" + urlPrefix + "/.*\\.do.*\\Z";
    }

    private String buildRoleInfoSql(String roleCode, String roleNm, String rolePttrn,
                                     AuthRegistrationSpec spec) {
        return "INSERT INTO COMTNROLEINFO (" +
                "ROLE_CODE, ROLE_NM, ROLE_PTTRN, ROLE_DC, ROLE_TY, ROLE_SORT, " +
                "CREAT_DT, MDFCN_DT) VALUES (" +
                "'" + esc(roleCode) + "', " +
                "'" + esc(roleNm) + "', " +
                "'" + esc(rolePttrn) + "', " +
                "'" + esc(spec.domain()) + " " + esc(spec.programNm()) + " 접근권한 설명', " +
                "'url', " +
                "1, " +
                renderer.now() + ", " + renderer.now() + ");";
    }

    private String buildAuthorRoleRelateSql(String roleCode) {
        return "INSERT INTO COMTNAUTHORROLERELATE (AUTHOR_CODE, ROLE_CODE, CREAT_DT) VALUES ('ROLE_ADMIN', '" + esc(roleCode) + "', " + renderer.now() + ");";
    }

    private String esc(String value) {
        return value == null ? "" : value.replace("'", "''");
    }
}
