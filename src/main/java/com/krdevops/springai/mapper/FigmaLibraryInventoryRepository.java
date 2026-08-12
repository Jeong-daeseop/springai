package com.krdevops.springai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.LegacyRepositoryDdlProperties;
import com.krdevops.springai.model.designsystem.FigmaLibraryInventorySnapshot;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 실제 Figma Library Inventory를 Registry 버전별 불변 Snapshot으로 저장한다. */
@Repository
public class FigmaLibraryInventoryRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final LegacyRepositoryDdlProperties ddlProperties;

    public FigmaLibraryInventoryRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
            LegacyRepositoryDdlProperties ddlProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
        this.ddlProperties = ddlProperties;
    }

    @PostConstruct
    public void createTableIfNotExists() {
        if (!ddlProperties.isLegacyRepositoryDdlEnabled()) return;
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS AI_FIGMA_LIBRARY_INVENTORY (
                PROFILE_ID VARCHAR(64) NOT NULL,
                REGISTRY_VERSION VARCHAR(64) NOT NULL,
                INVENTORY_VERSION VARCHAR(128) NOT NULL,
                SNAPSHOT_JSON LONGTEXT NOT NULL,
                CAPTURED_AT DATETIME(6) NOT NULL,
                PRIMARY KEY (PROFILE_ID, REGISTRY_VERSION, INVENTORY_VERSION)
            )
            """);
    }

    public void saveImmutable(FigmaLibraryInventorySnapshot snapshot) {
        Optional<FigmaLibraryInventorySnapshot> existing = findVersion(
                snapshot.profileId(), snapshot.registryVersion(), snapshot.inventoryVersion());
        if (existing.isPresent()) {
            if (existing.get().equals(snapshot)) return;
            throw new IllegalStateException("FIGMA_INVENTORY_VERSION_CONFLICT: "
                    + snapshot.profileId() + "/" + snapshot.registryVersion()
                    + "/" + snapshot.inventoryVersion());
        }
        jdbcTemplate.update("""
            INSERT INTO AI_FIGMA_LIBRARY_INVENTORY
              (PROFILE_ID, REGISTRY_VERSION, INVENTORY_VERSION, SNAPSHOT_JSON, CAPTURED_AT)
            VALUES (?, ?, ?, ?, ?)
            """, snapshot.profileId(), snapshot.registryVersion(), snapshot.inventoryVersion(),
                toJson(snapshot), java.sql.Timestamp.from(snapshot.capturedAt()));
    }

    public Optional<FigmaLibraryInventorySnapshot> findVersion(
            String profileId, String registryVersion, String inventoryVersion) {
        List<String> rows = jdbcTemplate.queryForList("""
            SELECT SNAPSHOT_JSON FROM AI_FIGMA_LIBRARY_INVENTORY
             WHERE PROFILE_ID = ? AND REGISTRY_VERSION = ? AND INVENTORY_VERSION = ?
            """, String.class, profileId, registryVersion, inventoryVersion);
        return rows.isEmpty() ? Optional.empty() : Optional.of(fromJson(rows.get(0)));
    }

    public Optional<FigmaLibraryInventorySnapshot> findLatest(String profileId, String registryVersion) {
        List<String> rows = jdbcTemplate.queryForList("""
            SELECT SNAPSHOT_JSON FROM AI_FIGMA_LIBRARY_INVENTORY
             WHERE PROFILE_ID = ? AND REGISTRY_VERSION = ?
             ORDER BY CAPTURED_AT DESC, INVENTORY_VERSION DESC LIMIT 1
            """, String.class, profileId, registryVersion);
        return rows.isEmpty() ? Optional.empty() : Optional.of(fromJson(rows.get(0)));
    }

    private String toJson(FigmaLibraryInventorySnapshot value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Figma Inventory 직렬화 실패", e); }
    }

    private FigmaLibraryInventorySnapshot fromJson(String value) {
        try { return objectMapper.readValue(value, FigmaLibraryInventorySnapshot.class); }
        catch (Exception e) { throw new IllegalStateException("Figma Inventory 역직렬화 실패", e); }
    }
}
