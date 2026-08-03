package com.krdevops.springai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.LegacyRepositoryDdlProperties;
import com.krdevops.springai.model.design.DesignAnalysisResult;
import com.krdevops.springai.model.design.DesignAnalysisSaveOutcome;
import com.krdevops.springai.model.design.DesignSourceType;
import com.krdevops.springai.model.design.UiDesignSpec;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DesignAnalysisRepositoryIntegrationTest {

    @Test
    void concurrentSaveOrGetReturnsWinnerWhosePrimaryKeyMatchesJsonId() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:mysql://localhost:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
                System.getenv().getOrDefault("DB_USERNAME", "ebt"),
                System.getenv().getOrDefault("DB_PASSWORD", "ebt01"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        DesignAnalysisRepository repository =
                new DesignAnalysisRepository(jdbc, objectMapper, new LegacyRepositoryDdlProperties());
        repository.createTableIfNotExists();
        String sourceHash = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        var first = result(UUID.randomUUID().toString(), sourceHash);
        var second = result(UUID.randomUUID().toString(), sourceHash);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var firstFuture = executor.submit(() -> saveTogether(repository, first, ready, start));
            var secondFuture = executor.submit(() -> saveTogether(repository, second, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            DesignAnalysisSaveOutcome firstOutcome = firstFuture.get(10, TimeUnit.SECONDS);
            DesignAnalysisSaveOutcome secondOutcome = secondFuture.get(10, TimeUnit.SECONDS);
            DesignAnalysisResult firstStored = firstOutcome.result();
            DesignAnalysisResult secondStored = secondOutcome.result();
            assertThat(firstStored.analysisId()).isEqualTo(secondStored.analysisId());
            assertThat(List.of(firstOutcome.insertedByCaller(), secondOutcome.insertedByCaller()))
                    .containsExactlyInAnyOrder(true, false);

            var row = jdbc.queryForMap("""
                    SELECT ANALYSIS_ID, RESULT_JSON FROM AI_DESIGN_ANALYSIS
                     WHERE SOURCE_HASH = ? AND PROVIDER_ID = 'figma'
                       AND MODEL_ID = 'deterministic-mapper' AND PROMPT_VERSION = 'figma-mapper-v1'
                    """, sourceHash);
            String storedId = (String) row.get("ANALYSIS_ID");
            String jsonId = objectMapper.readTree((String) row.get("RESULT_JSON"))
                    .path("analysisId").asText();
            assertThat(storedId).isEqualTo(jsonId).isEqualTo(firstStored.analysisId());
        } finally {
            executor.shutdownNow();
            jdbc.update("DELETE FROM AI_DESIGN_ANALYSIS WHERE SOURCE_HASH = ?", sourceHash);
        }
    }

    private DesignAnalysisSaveOutcome saveTogether(
            DesignAnalysisRepository repository, DesignAnalysisResult result,
            CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 저장 시작 신호를 받지 못했습니다.");
        }
        return repository.saveOrGet(result);
    }

    private DesignAnalysisResult result(String analysisId, String sourceHash) {
        return new DesignAnalysisResult(analysisId, sourceHash, "figma://test", null,
                DesignSourceType.FIGMA, null, "figma-mapper-v1",
                UiDesignSpec.SCHEMA_VERSION, "crud",
                "figma", "deterministic-mapper", "figma-mapper-v1", List.of(),
                UiDesignSpec.empty("CRUD_LIST"), List.of(), LocalDateTime.now());
    }
}
