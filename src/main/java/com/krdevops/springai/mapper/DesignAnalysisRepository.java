package com.krdevops.springai.mapper;

import com.krdevops.springai.config.LegacyRepositoryDdlProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.design.DesignAnalysisResult;
import com.krdevops.springai.model.design.DesignAnalysisSaveOutcome;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
public class DesignAnalysisRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final LegacyRepositoryDdlProperties ddlProperties;

    public DesignAnalysisRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
            LegacyRepositoryDdlProperties ddlProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
        this.ddlProperties = ddlProperties;
    }

    @PostConstruct
    public void createTableIfNotExists() {
        if (!ddlProperties.isLegacyRepositoryDdlEnabled()) {
            return;
        }
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS AI_DESIGN_ANALYSIS (
                ANALYSIS_ID   VARCHAR(64) PRIMARY KEY,
                SOURCE_HASH   VARCHAR(64) NOT NULL,
                PROVIDER_ID   VARCHAR(32) NOT NULL,
                MODEL_ID      VARCHAR(100) NOT NULL,
                PROMPT_VERSION VARCHAR(32) NOT NULL,
                RESULT_JSON   LONGTEXT NOT NULL,
                CREATED_AT    DATETIME DEFAULT CURRENT_TIMESTAMP,
                UNIQUE KEY UK_DESIGN_ANALYSIS_CACHE (SOURCE_HASH, PROVIDER_ID, MODEL_ID, PROMPT_VERSION)
            )
            """);
        log.info("AI_DESIGN_ANALYSIS 테이블 초기화 완료");
    }

    @Transactional
    public DesignAnalysisSaveOutcome saveOrGet(DesignAnalysisResult result) {
        jdbcTemplate.update("""
            INSERT INTO AI_DESIGN_ANALYSIS
                (ANALYSIS_ID, SOURCE_HASH, PROVIDER_ID, MODEL_ID, PROMPT_VERSION, RESULT_JSON)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE ANALYSIS_ID = ANALYSIS_ID
            """, result.analysisId(), result.sourceHash(), result.provider(), result.model(),
                result.promptVersion(), toJson(result));
        DesignAnalysisResult stored = findExact(
                result.sourceHash(), result.provider(), result.model(), result.promptVersion())
                .orElseThrow(() -> new IllegalStateException("디자인 분석 저장 직후 재조회에 실패했습니다."));
        return new DesignAnalysisSaveOutcome(stored, stored.analysisId().equals(result.analysisId()));
    }

    public Optional<DesignAnalysisResult> findExact(
            String sourceHash, String provider, String model, String promptVersion) {
        List<DesignAnalysisResult> results = jdbcTemplate.query("""
            SELECT ANALYSIS_ID, RESULT_JSON FROM AI_DESIGN_ANALYSIS
             WHERE SOURCE_HASH = ? AND PROVIDER_ID = ? AND MODEL_ID = ? AND PROMPT_VERSION = ?
             LIMIT 1
            """, (rs, rowNum) -> fromRow(rs.getString("ANALYSIS_ID"), rs.getString("RESULT_JSON")),
                sourceHash, provider, model, promptVersion);
        return results.stream().findFirst();
    }

    public Optional<DesignAnalysisResult> findById(String analysisId) {
        List<DesignAnalysisResult> results = jdbcTemplate.query(
                "SELECT ANALYSIS_ID, RESULT_JSON FROM AI_DESIGN_ANALYSIS WHERE ANALYSIS_ID = ?",
                (rs, rowNum) -> fromRow(rs.getString("ANALYSIS_ID"), rs.getString("RESULT_JSON")),
                analysisId);
        return results.stream().findFirst();
    }

    private String toJson(DesignAnalysisResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new IllegalStateException("디자인 분석 JSON 직렬화 실패", e);
        }
    }

    private DesignAnalysisResult fromJson(String json) {
        try {
            return objectMapper.readValue(json, DesignAnalysisResult.class);
        } catch (Exception e) {
            throw new IllegalStateException("디자인 분석 JSON 역직렬화 실패", e);
        }
    }

    private DesignAnalysisResult fromRow(String storedId, String json) {
        DesignAnalysisResult result = fromJson(json);
        if (!storedId.equals(result.analysisId())) {
            throw new IllegalStateException("디자인 분석 DB 키와 JSON analysisId가 일치하지 않습니다.");
        }
        return result;
    }
}
