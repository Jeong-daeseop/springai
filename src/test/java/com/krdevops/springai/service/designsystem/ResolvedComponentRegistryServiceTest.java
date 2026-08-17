package com.krdevops.springai.service.designsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.designsystem.ComponentCatalog;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.ComponentRegistrySnapshotV3;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import com.krdevops.springai.model.designsystem.ResolvedComponentRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R6-044: {@link ComponentRegistryResolver}가 v3 입력을 위임하는 실제 해석기가 승인된 Catalog
 * 계약과 Registry Binding을 결합해 요청 allowlist·alias·합성(Composition)·필수/선택 정책을
 * 정확히 적용하는지 검증한다.
 */
class ResolvedComponentRegistryServiceTest {

    private static final String CATALOG_VERSION = "2.0.0";

    private final ComponentCatalogLoader loader = new ComponentCatalogLoader(new ObjectMapper());
    private final ComponentRegistryBindingValidator bindingValidator =
            new ComponentRegistryBindingValidator(new ComponentCatalogValidator());
    private final ComponentRegistryOptionalFallbackPolicy fallbackPolicy = new ComponentRegistryOptionalFallbackPolicy();
    private final ResolvedComponentRegistryService service =
            new ResolvedComponentRegistryService(loader, bindingValidator, fallbackPolicy);

    @Test
    void resolveCombinesCatalogContractWithRegistryBindingForRequestedAllowlist() {
        ResolvedComponentRegistry resolved = service.resolve(approvedRegistry(), Set.of("krds.button"));

        var entry = resolved.entries().get("krds.button");
        assertThat(entry.canonicalLogicalType()).isEqualTo("krds.button");
        assertThat(entry.atomicBindings()).singleElement().satisfies(binding ->
                assertThat(binding.binding().componentSetKey()).isEqualTo("krds.button_SET"));
    }

    /** 요청 allowlist는 Catalog 전체가 아니라 지정한 논리 타입만 결과에 포함해야 한다. */
    @Test
    void resolveOnlyReturnsRequestedAllowlistEvenWhenRegistryHasMoreBindings() {
        ResolvedComponentRegistry resolved = service.resolve(approvedRegistry(), Set.of("krds.button"));

        assertThat(resolved.entries()).hasSize(1).containsOnlyKeys("krds.button");
    }

    @Test
    void resolveFollowsCatalogAliasToCanonicalLogicalType() {
        ResolvedComponentRegistry resolved = service.resolve(approvedRegistry(), Set.of("button"));

        var entry = resolved.entries().get("button");
        assertThat(entry.requestedLogicalType()).isEqualTo("button");
        assertThat(entry.canonicalLogicalType()).isEqualTo("krds.button");
    }

    @Test
    void resolveRejectsLogicalTypeUnknownToCatalog() {
        ComponentRegistrySnapshotV3 registry = approvedRegistry();

        assertThatThrownBy(() -> service.resolve(registry, Set.of("krds.doesNotExist")))
                .isInstanceOfSatisfying(ResolvedComponentRegistryService.ResolutionException.class,
                        error -> assertThat(error.issues()).extracting(DesignSystemIssue::code)
                                .contains("UNKNOWN_LOGICAL_TYPE"));
    }

    /** 승인되지 않은 Registry는 UNAPPROVED_REGISTRY로 차단한다(resolve()는 항상 승인을 요구). */
    @Test
    void resolveRejectsUnapprovedRegistry() {
        ComponentRegistrySnapshotV3 registry = draftRegistryWithAllRequiredBindings();

        assertThatThrownBy(() -> service.resolve(registry, Set.of("krds.button")))
                .isInstanceOfSatisfying(ResolvedComponentRegistryService.ResolutionException.class,
                        error -> assertThat(error.issues()).extracting(DesignSystemIssue::code)
                                .contains("UNAPPROVED_REGISTRY"));
    }

    /** 합성(Pattern) 논리 타입은 원자 컴포넌트 목록으로 재귀 전개돼야 한다. */
    @Test
    void resolveExpandsCompositePatternIntoAtomicBindings() {
        ResolvedComponentRegistry resolved = service.resolve(approvedRegistry(), Set.of("egov.actionArea"));

        var entry = resolved.entries().get("egov.actionArea");
        assertThat(entry.atomicBindings()).extracting(ResolvedComponentRegistry.AtomicBinding::logicalType)
                .containsExactly("krds.button");
    }

    /** 다단 합성(Pattern of Pattern)도 최종적으로는 원자 컴포넌트 목록으로만 남는다. */
    @Test
    void resolveExpandsNestedCompositionAllTheWayToAtomicBindings() {
        ResolvedComponentRegistry resolved = service.resolve(approvedRegistry(), Set.of("egov.pattern.list"));

        var entry = resolved.entries().get("egov.pattern.list");
        assertThat(entry.atomicBindings()).extracting(ResolvedComponentRegistry.AtomicBinding::logicalType)
                .containsExactly(
                        "krds.pageHeader", "krds.searchPanel", "krds.tableHeader", "krds.tableCell", "krds.pagination");
    }

    @Test
    void previewReturnsNullResolutionWhenRegistryIsNotApproved() {
        var preview = service.preview(draftRegistryWithAllRequiredBindings(), Set.of("krds.button"));

        assertThat(preview.resolved()).isNull();
    }

    /** 필수 컴포넌트의 Binding이 없으면 Preview에서도 fallback 없이 차단(ERROR)한다. */
    @Test
    void previewBlocksWhenRequiredComponentBindingIsMissing() {
        var preview = service.preview(registryMissingRequiredButtonBinding(), Set.of("krds.button"));

        assertThat(preview.resolved()).isNull();
        assertThat(preview.issues()).extracting(DesignSystemIssue::code)
                .contains("REQUIRED_BINDING_MISSING");
    }

    /** Optional 컴포넌트의 Binding 누락은 Preview에서 WARNING fallback으로만 표시되고 결과 생성을 막지 않는다. */
    @Test
    void previewFallsBackToWarningWhenOptionalComponentBindingIsMissing() {
        // approvedRegistry()는 REQUIRED 컴포넌트만 채우므로 OPTIONAL인 krds.radio는 바인딩이 없다.
        var preview = service.preview(approvedRegistry(), Set.of("krds.radio"));

        assertThat(preview.issues()).extracting(DesignSystemIssue::code)
                .contains("OPTIONAL_BINDING_MISSING_PREVIEW_FALLBACK");
        assertThat(preview.resolved()).isNotNull();
    }

    @Test
    void previewReturnsRegistryNullIssueForNullRegistry() {
        var preview = service.preview(null, Set.of("krds.button"));

        assertThat(preview.resolved()).isNull();
        assertThat(preview.issues()).extracting(DesignSystemIssue::code).contains("REGISTRY_NULL");
    }

    private ComponentRegistrySnapshotV3 approvedRegistry() {
        return withApproval(draftRegistryWithAllRequiredBindings());
    }

    private ComponentRegistrySnapshotV3 registryMissingRequiredButtonBinding() {
        var loaded = loader.load(CATALOG_VERSION);
        Map<String, ComponentRegistrySnapshotV3.Binding> bindings = new LinkedHashMap<>();
        loaded.catalog().components().forEach((logicalType, entry) -> {
            if (entry.atomicComponent() && entry.requirement() == ComponentCatalog.Requirement.REQUIRED
                    && !"krds.button".equals(logicalType)) {
                bindings.put(logicalType, binding(logicalType));
            }
        });
        return withApproval(new ComponentRegistrySnapshotV3(
                ComponentRegistrySnapshotV3.SCHEMA_VERSION, "krds", CATALOG_VERSION, "3.0.0",
                loaded.catalog().contractVersion(), loaded.contentHash(),
                new ComponentRegistry.LibraryRef("LIBRARY", "KRDS"), bindings, Map.of(),
                "revision-1", null, null, null));
    }

    private ComponentRegistrySnapshotV3 draftRegistryWithAllRequiredBindings() {
        var loaded = loader.load(CATALOG_VERSION);
        Map<String, ComponentRegistrySnapshotV3.Binding> bindings = new LinkedHashMap<>();
        loaded.catalog().components().forEach((logicalType, entry) -> {
            if (entry.atomicComponent() && entry.requirement() == ComponentCatalog.Requirement.REQUIRED) {
                bindings.put(logicalType, binding(logicalType));
            }
        });
        return new ComponentRegistrySnapshotV3(
                ComponentRegistrySnapshotV3.SCHEMA_VERSION, "krds", CATALOG_VERSION, "3.0.0",
                loaded.catalog().contractVersion(), loaded.contentHash(),
                new ComponentRegistry.LibraryRef("LIBRARY", "KRDS"), bindings, Map.of(),
                "revision-1", null, null, null);
    }

    private ComponentRegistrySnapshotV3.Binding binding(String logicalType) {
        return new ComponentRegistrySnapshotV3.Binding(
                logicalType + "_SET", logicalType,
                ComponentRegistryEntry.PublishStatus.CURRENT,
                ComponentRegistryEntry.LifecycleStatus.CURRENT, Map.of());
    }

    private ComponentRegistrySnapshotV3 withApproval(ComponentRegistrySnapshotV3 draft) {
        return new ComponentRegistrySnapshotV3(
                draft.schemaVersion(), draft.profileId(), draft.profileVersion(), draft.registryVersion(),
                draft.catalogVersion(), draft.catalogHash(), draft.library(), draft.bindings(), draft.variables(),
                draft.sourceRevision(), "design-system-owner", Instant.parse("2026-08-18T00:00:00Z"),
                draft.contentHash());
    }
}
