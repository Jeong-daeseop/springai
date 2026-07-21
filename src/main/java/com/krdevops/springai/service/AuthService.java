package com.krdevops.springai.service;

import com.krdevops.springai.model.AuthRegistrationSpec;
import com.krdevops.springai.model.SqlPlan;
import com.krdevops.springai.service.auth.AuthInputValidator;
import com.krdevops.springai.service.auth.AuthRepository;
import com.krdevops.springai.service.auth.AuthResultBuilder;
import com.krdevops.springai.service.auth.AuthSqlBuilder;
import com.krdevops.springai.service.sql.SqlDialectRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthRepository authRepository;
    private final SqlDialectRenderer renderer;
    private final AuthInputValidator validator;
    private final AuthSqlBuilder sqlBuilder;
    private final AuthResultBuilder resultBuilder;
    private final ProgramMetadataQueryService programMetadataQueryService;

    public String getProgramList(String keyword) {
        String programTable = programMetadataQueryService.firstExistingProgramTable();
        List<Map<String, Object>> rows = authRepository.searchPrograms(keyword, renderer, programTable);

        if (rows.isEmpty()) {
            return "검색 결과가 없습니다." + (keyword != null && !keyword.isBlank() ? " (검색어: " + keyword + ")" : "");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-30s %-30s %s%n", "PROGRM_FILE_NM", "PROGRM_KOREAN_NM", "URL"));
        sb.append("-".repeat(100)).append("\n");
        for (Map<String, Object> row : rows) {
            sb.append(String.format("%-30s %-30s %s%n",
                    row.get("PROGRM_FILE_NM"),
                    row.get("PROGRM_KOREAN_NM"),
                    row.get("URL")));
        }
        sb.append("\n총 ").append(rows.size()).append("건\n");
        return sb.toString();
    }

    public String generateAuthInsertSql(String urlPrefix, String programNm, String domain) {
        AuthRegistrationSpec spec;
        try {
            spec = validator.validateAndBuild(urlPrefix, programNm, domain);
        } catch (IllegalArgumentException e) {
            return "오류: " + e.getMessage();
        }

        int nextRoleNum = authRepository.findNextRoleNum(renderer);
        SqlPlan plan = sqlBuilder.build(spec, nextRoleNum);
        return resultBuilder.render(plan);
    }
}
