package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.mapper.ComponentRegistryRepository;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.krdevops.springai.config.StubEmbeddingModelTestConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CI에는 로컬 전용 ko-sroberta ONNX 모델 파일이 없어, 이 테스트가 필요로 하지 않는 실제 임베딩
 * 모델 auto-config를 끄고(TestPropertySource) StubEmbeddingModelTestConfig로 대체한다. 후자만으로는
 * 부족하다 — TransformersEmbeddingModelAutoConfiguration의 @ConditionalOnMissingBean이 구현
 * 클래스(TransformersEmbeddingModel) 기준이라 인터페이스 스텁 Bean만으로는 비활성화되지 않는다.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.ai.model.embedding=none")
@Import(StubEmbeddingModelTestConfig.class)
@DisplayName("I-4A: ComponentInventoryValidator 테스트")
class ComponentInventoryValidatorTest {

    @Autowired
    private ComponentInventoryValidator validator;

    @MockitoBean
    private ComponentRegistryRepository componentRegistryRepository;

    @Test
    @DisplayName("NULL profileId 검증")
    void testNullProfileId() {
        var result = validator.validateRegistry(null, "1.0.0");
        assertFalse(result.isValid());
        assertTrue(result.issues().stream().anyMatch(i -> i.contains("profileId")));
    }

    @Test
    @DisplayName("빈 profileId 검증")
    void testEmptyProfileId() {
        var result = validator.validateRegistry("", "1.0.0");
        assertFalse(result.isValid());
    }

    @Test
    @DisplayName("NULL Registry 처리")
    void testNullRegistryHandling() {
        var result = validator.resolveComponentSelection(null, null);
        assertTrue(result.selectedComponentKeys().isEmpty());
        assertFalse(result.fallbackComponentKeys().isEmpty());
    }

    // R6-T15: 근거(issues)·confidence·Registry lifecycle 상태가 선택 결과에 반영되는지 검증.

    @Test
    @DisplayName("CURRENT/ACTIVE 컴포넌트는 confidence 0.95로 확정 선택된다")
    void currentActiveComponentIsSelectedWithHighConfidence() {
        var registry = registryWith(Map.of("krds.button", entry("BUTTON_KEY",
                ComponentRegistryEntry.PublishStatus.CURRENT, ComponentRegistryEntry.LifecycleStatus.ACTIVE, null)));

        var result = validator.resolveComponentSelection(registry, List.of("krds.button"));

        assertEquals(List.of("krds.button"), result.selectedComponentKeys());
        assertEquals(0.95, result.componentConfidence().get("krds.button"));
        assertTrue(result.issues().isEmpty());
    }

    @Test
    @DisplayName("DEPRECATED 컴포넌트는 확정 선택되지 않고 CURRENT 대체 컴포넌트로 fallback된다")
    void deprecatedComponentFallsBackToReplacementWhenReplacementIsCurrent() {
        var registry = registryWith(Map.of(
                "krds.button.old", entry("OLD_BUTTON_KEY",
                        ComponentRegistryEntry.PublishStatus.CURRENT, ComponentRegistryEntry.LifecycleStatus.DEPRECATED,
                        "krds.button.new"),
                "krds.button.new", entry("NEW_BUTTON_KEY",
                        ComponentRegistryEntry.PublishStatus.CURRENT, ComponentRegistryEntry.LifecycleStatus.ACTIVE, null)));

        var result = validator.resolveComponentSelection(registry, List.of("krds.button.old"));

        assertTrue(result.selectedComponentKeys().isEmpty());
        assertTrue(result.fallbackComponentKeys().contains("krds.button.new"));
        assertEquals(0.6, result.componentConfidence().get("krds.button.old"));
        assertTrue(result.issues().stream().anyMatch(issue ->
                issue.contains("krds.button.old") && issue.contains("DEPRECATED") && issue.contains("krds.button.new")));
    }

    @Test
    @DisplayName("REMOVED 컴포넌트에 대체 컴포넌트가 없으면 낮은 confidence로 선택도 fallback도 하지 않는다")
    void removedComponentWithoutReplacementGetsLowConfidenceAndNoSelection() {
        var registry = registryWith(Map.of("krds.legacy-badge", entry("LEGACY_PANEL_KEY",
                ComponentRegistryEntry.PublishStatus.CURRENT, ComponentRegistryEntry.LifecycleStatus.REMOVED, null)));

        var result = validator.resolveComponentSelection(registry, List.of("krds.legacy-badge"));

        assertTrue(result.selectedComponentKeys().isEmpty());
        assertFalse(result.fallbackComponentKeys().contains("krds.legacy-badge"));
        assertEquals(0.3, result.componentConfidence().get("krds.legacy-badge"));
        assertTrue(result.issues().stream().anyMatch(issue ->
                issue.contains("krds.legacy-badge") && issue.contains("REMOVED")));
    }

    @Test
    @DisplayName("UNPUBLISHED 컴포넌트는 lifecycle이 ACTIVE여도 currentForGeneration이 아니라 확정 선택되지 않는다")
    void unpublishedComponentIsNotSelectedEvenWhenLifecycleIsActive() {
        var registry = registryWith(Map.of("krds.draft-card", entry("DRAFT_CARD_KEY",
                ComponentRegistryEntry.PublishStatus.UNPUBLISHED, ComponentRegistryEntry.LifecycleStatus.ACTIVE, null)));

        var result = validator.resolveComponentSelection(registry, List.of("krds.draft-card"));

        assertTrue(result.selectedComponentKeys().isEmpty());
        assertEquals(0.3, result.componentConfidence().get("krds.draft-card"));
    }

    @Test
    @DisplayName("Registry에 아예 없는 컴포넌트는 이름 유사도 fallback을 시도한다")
    void unknownComponentTriesNameSimilarityFallback() {
        var registry = registryWith(Map.of("krds.button", entry("BUTTON_KEY",
                ComponentRegistryEntry.PublishStatus.CURRENT, ComponentRegistryEntry.LifecycleStatus.ACTIVE, null)));

        var result = validator.resolveComponentSelection(registry, List.of("krds.but-typo"));

        assertTrue(result.selectedComponentKeys().isEmpty());
        assertEquals(0.6, result.componentConfidence().get("krds.but-typo"));
    }

    private ComponentRegistry registryWith(Map<String, ComponentRegistryEntry> components) {
        return new ComponentRegistry(
                "ftc-krds", "1.0.0", "registry-1",
                new ComponentRegistry.LibraryRef("fileKey", "Library"), components);
    }

    private ComponentRegistryEntry entry(
            String componentSetKey,
            ComponentRegistryEntry.PublishStatus publishStatus,
            ComponentRegistryEntry.LifecycleStatus lifecycleStatus,
            String replacementLogicalType) {
        return new ComponentRegistryEntry(
                componentSetKey, componentSetKey, publishStatus, lifecycleStatus,
                replacementLogicalType, List.of(), Map.of(), Map.of(), Set.of(), Set.of(),
                Map.of(), Set.of(), null, null, "1.0.0");
    }
}
