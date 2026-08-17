package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.mapper.ComponentRegistryRepository;
import com.krdevops.springai.mapper.ComponentRegistrySnapshotV3Repository;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryResolutionComparisonReport;
import com.krdevops.springai.model.designsystem.ResolvedComponentRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ComponentRegistryDualReadServiceTest {
    @Test
    void keepsLegacyAsSelectedSourceWhileRunningResolvedComparison() {
        ComponentRegistryRepository legacyRepository = mock(ComponentRegistryRepository.class);
        ComponentRegistrySnapshotV3Repository v3Repository = mock(ComponentRegistrySnapshotV3Repository.class);
        ResolvedComponentRegistryService resolvedService = mock(ResolvedComponentRegistryService.class);
        ComponentRegistryResolutionComparisonService comparisonService = mock(ComponentRegistryResolutionComparisonService.class);
        ComponentRegistry legacy = mock(ComponentRegistry.class);
        var v3 = mock(com.krdevops.springai.model.designsystem.ComponentRegistrySnapshotV3.class);
        var resolved = mock(ResolvedComponentRegistry.class);
        var report = new ComponentRegistryResolutionComparisonReport("report", "krds", "2", "3",
                Instant.parse("2026-08-17T00:00:00Z"), true, java.util.List.of());
        when(legacyRepository.findVersion("krds", "2")).thenReturn(Optional.of(legacy));
        when(v3Repository.findVersion("krds", "3")).thenReturn(Optional.of(v3));
        when(resolvedService.resolve(eq(v3), anySet())).thenReturn(resolved);
        when(comparisonService.compareAndSave("krds", legacy, resolved)).thenReturn(report);

        var result = new ComponentRegistryDualReadService(legacyRepository, v3Repository,
                resolvedService, comparisonService).read("krds", "2", "3", java.util.Set.of("button"));

        assertThat(result.selectedSource()).isEqualTo("LEGACY");
        assertThat(result.selectedLegacyRegistry()).isSameAs(legacy);
        verify(resolvedService).resolve(eq(v3), anySet());
        verify(comparisonService).compareAndSave("krds", legacy, resolved);
    }
}
