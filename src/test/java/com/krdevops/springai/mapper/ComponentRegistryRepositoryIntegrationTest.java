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

    /**
     * SSOT-R1-T04(19번 문서 §5): 새 필드(lifecycleStatus/roles/variantAxes 등)가 추가되기 전
     * 가장 오래된 형태(componentSetKey/properties만 존재)로 저장됐던 실제 운영 Snapshot을
     * {@code repository.save()}가 아니라 원시 SQL로 직접 넣어 재현한다 — 이래야 현재
     * ComponentRegistryEntry 인스턴스를 직렬화한(=이미 모든 필드를 포함하는) 값이 아니라 진짜
     * legacy 스키마 형태를 검증한다. 손실 없이 조회된다는 것은 (1) 예외 없이 역직렬화되고
     * (2) 실제 있던 데이터(componentSetKey, properties)가 그대로 보존되며 (3) 당시 없던
     * 필드는 기존 legacy 호환 생성자와 동일한 기본값(UNPUBLISHED/ACTIVE/빈 컬렉션)으로
     * 해석된다는 뜻이다.
     */
    @Test
    void legacyShapedRegistryJsonIsReadWithoutDataLoss() {
        repository.createTableIfNotExists();
        String profileId = "legacy-" + UUID.randomUUID();
        String legacyJson = """
                {
                  "profileId": "%s",
                  "profileVersion": "1.0",
                  "registryVersion": "legacy-2024.01",
                  "library": null,
                  "components": {
                    "krds.button": {
                      "componentSetKey": "BUTTON_KEY_LEGACY",
                      "properties": {
                        "label": {"figmaProperty": "Label", "type": "TEXT", "values": {}}
                      }
                    }
                  }
                }
                """.formatted(profileId);
        try {
            jdbcTemplate.update("""
                INSERT INTO AI_COMPONENT_REGISTRY (PROFILE_ID, REGISTRY_VERSION, REGISTRY_JSON)
                VALUES (?, ?, ?)
                """, profileId, "legacy-2024.01", legacyJson);

            ComponentRegistry loaded = repository.findVersion(profileId, "legacy-2024.01").orElseThrow();

            assertThat(loaded.profileId()).isEqualTo(profileId);
            assertThat(loaded.components()).containsOnlyKeys("krds.button");
            ComponentRegistryEntry entry = loaded.components().get("krds.button");
            assertThat(entry.componentSetKey()).isEqualTo("BUTTON_KEY_LEGACY");
            assertThat(entry.properties()).containsOnlyKeys("label");
            assertThat(entry.properties().get("label").figmaProperty()).isEqualTo("Label");
            assertThat(entry.publishStatus()).isEqualTo(ComponentRegistryEntry.PublishStatus.UNPUBLISHED);
            assertThat(entry.lifecycleStatus()).isEqualTo(ComponentRegistryEntry.LifecycleStatus.ACTIVE);
            assertThat(entry.aliases()).isEmpty();
            assertThat(entry.variants()).isEmpty();
            assertThat(entry.roles()).isEmpty();
            assertThat(entry.contractVersion()).isEqualTo("1.0.0");
        } finally {
            jdbcTemplate.update("DELETE FROM AI_COMPONENT_REGISTRY WHERE PROFILE_ID = ?", profileId);
        }
    }

    private ComponentRegistry registry(String profileId, String registryVersion) {
        return new ComponentRegistry(profileId, "1.0", registryVersion, null,
                Map.of("krds.button", new ComponentRegistryEntry("BUTTON_KEY", Map.of())));
    }
}
