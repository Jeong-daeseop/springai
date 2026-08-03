package com.krdevops.springai.mapper;

import com.krdevops.springai.config.LegacyRepositoryDdlProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** R1-T04/T05: ComponentRegistryRepository 버전별 저장·조회와 스키마 초기화 반복 실행 안전성. */
class ComponentRegistryRepositoryIntegrationTest {

    private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:mysql://localhost:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
            System.getenv().getOrDefault("DB_USERNAME", "ebt"),
            System.getenv().getOrDefault("DB_PASSWORD", "ebt01"));
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    private final ComponentRegistryRepository repository = new ComponentRegistryRepository(
            jdbcTemplate, new ObjectMapper().findAndRegisterModules(), new LegacyRepositoryDdlProperties());

    @Test
    void createTableIfNotExistsIsIdempotent() {
        assertThatCode(repository::createTableIfNotExists).doesNotThrowAnyException();
        assertThatCode(repository::createTableIfNotExists).doesNotThrowAnyException();
    }

    @Test
    void findLatestReturnsMostRecentlySavedRegistryVersion() {
        repository.createTableIfNotExists();
        String profileId = "test-" + UUID.randomUUID();
        try {
            repository.save(registry(profileId, "2026.06"));
            repository.save(registry(profileId, "2026.07"));

            assertThat(repository.findVersion(profileId, "2026.06")).isPresent()
                    .get().extracting(ComponentRegistry::registryVersion).isEqualTo("2026.06");
            assertThat(repository.findLatest(profileId)).isPresent()
                    .get().extracting(ComponentRegistry::registryVersion).isEqualTo("2026.07");
        } finally {
            jdbcTemplate.update("DELETE FROM AI_COMPONENT_REGISTRY WHERE PROFILE_ID = ?", profileId);
        }
    }

    private ComponentRegistry registry(String profileId, String registryVersion) {
        return new ComponentRegistry(profileId, "1.0", registryVersion, null,
                Map.of("krds.button", new ComponentRegistryEntry("BUTTON_KEY", Map.of())));
    }
}
