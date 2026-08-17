package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.mapper.ComponentRegistryRepository;
import com.krdevops.springai.mapper.ComponentRegistrySnapshotV3Repository;
import com.krdevops.springai.model.designsystem.ComponentRegistryResolutionComparisonReport;
import org.springframework.stereotype.Service;

import java.util.Set;

/** Legacy/v3 Resolver를 같은 요청에서 실행하고 관찰 비교 Report를 저장한다. */
@Service
public class ComponentRegistryObservationService {
    private final ComponentRegistryRepository legacyRepository;
    private final ComponentRegistrySnapshotV3Repository v3Repository;
    private final ResolvedComponentRegistryService resolvedService;
    private final ComponentRegistryResolutionComparisonService comparisonService;

    public ComponentRegistryObservationService(ComponentRegistryRepository legacyRepository,
            ComponentRegistrySnapshotV3Repository v3Repository,
            ResolvedComponentRegistryService resolvedService,
            ComponentRegistryResolutionComparisonService comparisonService) {
        this.legacyRepository = legacyRepository;
        this.v3Repository = v3Repository;
        this.resolvedService = resolvedService;
        this.comparisonService = comparisonService;
    }

    public ComponentRegistryResolutionComparisonReport compare(String profileId,
            String legacyVersion, String resolvedVersion, Set<String> logicalTypes) {
        var legacy = legacyRepository.findVersion(profileId, legacyVersion)
                .orElseThrow(() -> new IllegalArgumentException("Legacy Registry를 찾을 수 없습니다: " + legacyVersion));
        var v3 = v3Repository.findVersion(profileId, resolvedVersion)
                .orElseThrow(() -> new IllegalArgumentException("Registry v3 Snapshot을 찾을 수 없습니다: " + resolvedVersion));
        var resolved = resolvedService.resolve(v3, logicalTypes);
        return comparisonService.compareAndSave(profileId, legacy, resolved);
    }
}
