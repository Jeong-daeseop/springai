package com.krdevops.springai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.figma.FigmaExportIssue;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import com.krdevops.springai.model.figma.FigmaScreenType;
import com.krdevops.springai.model.figma.LayoutPattern;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** R1-T04/T05: FigmaScreenSpecRepository 버전별 저장·조회와 스키마 초기화 반복 실행 안전성. */
class FigmaScreenSpecRepositoryIntegrationTest {

    private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:mysql://localhost:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
            System.getenv().getOrDefault("DB_USERNAME", "ebt"),
            System.getenv().getOrDefault("DB_PASSWORD", "ebt01"));
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    private final FigmaScreenSpecRepository repository = new FigmaScreenSpecRepository(
            jdbcTemplate, new ObjectMapper().findAndRegisterModules());

    @Test
    void createTableIfNotExistsIsIdempotent() {
        assertThatCode(repository::createTableIfNotExists).doesNotThrowAnyException();
        assertThatCode(repository::createTableIfNotExists).doesNotThrowAnyException();
    }

    @Test
    void savingMultipleVersionsPreservesEachVersionAndLatestReturnsNewest() {
        repository.createTableIfNotExists();
        String screenId = "test-" + UUID.randomUUID();
        try {
            repository.save(screenSpec(screenId, 1));
            repository.save(screenSpec(screenId, 2));

            assertThat(repository.findVersion(screenId, 1)).isPresent()
                    .get().extracting(FigmaScreenSpec::screenVersion).isEqualTo(1);
            assertThat(repository.findVersion(screenId, 2)).isPresent()
                    .get().extracting(FigmaScreenSpec::screenVersion).isEqualTo(2);
            assertThat(repository.findLatest(screenId)).isPresent()
                    .get().extracting(FigmaScreenSpec::screenVersion).isEqualTo(2);
        } finally {
            jdbcTemplate.update("DELETE FROM AI_FIGMA_SCREEN_SPEC WHERE SCREEN_ID = ?", screenId);
        }
    }

    @Test
    void sameVersionIsIdempotentButDifferentContentIsRejected() {
        repository.createTableIfNotExists();
        String screenId = "test-" + UUID.randomUUID();
        try {
            FigmaScreenSpec original = screenSpec(screenId, 1);
            repository.save(original);
            repository.save(original);

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM AI_FIGMA_SCREEN_SPEC WHERE SCREEN_ID = ?", Integer.class, screenId);
            assertThat(count).isEqualTo(1);

            assertThatThrownBy(() -> repository.save(screenSpec(screenId, 1, "변경된 화면")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FIGMA_SCREEN_VERSION_CONFLICT");
            assertThat(repository.findVersion(screenId, 1).orElseThrow().name())
                    .isEqualTo("테스트 화면");
        } finally {
            jdbcTemplate.update("DELETE FROM AI_FIGMA_SCREEN_SPEC WHERE SCREEN_ID = ?", screenId);
        }
    }

    private FigmaScreenSpec screenSpec(String screenId, int version) {
        return screenSpec(screenId, version, "테스트 화면");
    }

    private FigmaScreenSpec screenSpec(String screenId, int version, String name) {
        FigmaNodeSpec root = new FigmaNodeSpec(
                screenId + "-root", FigmaNodeSpec.NodeType.PAGE, "egov.listPage", Map.of(), List.of());
        return new FigmaScreenSpec(
                screenId, version, "spec-test", 1,
                FigmaScreenType.LIST, LayoutPattern.STANDARD,
                name, null, "DESKTOP", "APPROVED",
                new FigmaScreenSpec.DesignSystemRef("krds", "1.0", "2026.07"),
                root, List.of(new FigmaExportIssue("X", FigmaExportIssue.Severity.WARNING, "msg", null, null, null)));
    }
}
