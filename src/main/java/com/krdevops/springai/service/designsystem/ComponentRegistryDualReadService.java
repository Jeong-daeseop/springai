package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.mapper.ComponentRegistryRepository;
import com.krdevops.springai.mapper.ComponentRegistrySnapshotV3Repository;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryResolutionComparisonReport;
import org.springframework.stereotype.Service;

import java.util.Set;

/** Legacy 결과를 선택값으로 유지하면서 v3 Resolver를 병렬 실행하는 이중 읽기 모드. */
@Service
public class ComponentRegistryDualReadService {
    private final ComponentRegistryRepository legacyRepository;
    private final ComponentRegistrySnapshotV3Repository v3Repository;
    private final ResolvedComponentRegistryService resolvedService;
    private final ComponentRegistryResolutionComparisonService comparisonService;

    public ComponentRegistryDualReadService(ComponentRegistryRepository legacyRepository,
            ComponentRegistrySnapshotV3Repository v3Repository,
            ResolvedComponentRegistryService resolvedService,
            ComponentRegistryResolutionComparisonService comparisonService) {
        this.legacyRepository = legacyRepository;
        this.v3Repository = v3Repository;
        this.resolvedService = resolvedService;
        this.comparisonService = comparisonService;
    }

    public DualReadResult read(String profileId, String legacyVersion, String resolvedVersion,
                               Set<String> logicalTypes) {
        ComponentRegistry legacy = legacyRepository.findVersion(profileId, legacyVersion)
                .orElseThrow(() -> new IllegalArgumentException("Legacy Registry를 찾을 수 없습니다: " + legacyVersion));
        var v3 = v3Repository.findVersion(profileId, resolvedVersion)
                .orElseThrow(() -> new IllegalArgumentException("Registry v3 Snapshot을 찾을 수 없습니다: " + resolvedVersion));
        var resolved = resolvedService.resolve(v3, logicalTypes);
        var comparison = comparisonService.compareAndSave(profileId, legacy, resolved);
        // 호환 전환 기간에는 반드시 Legacy 결과를 선택값으로 반환한다.
        return new DualReadResult(legacy, comparison, "LEGACY");
    }

    public record DualReadResult(ComponentRegistry selectedLegacyRegistry,
                                 ComponentRegistryResolutionComparisonReport comparisonReport,
                                 String selectedSource) {}
}
