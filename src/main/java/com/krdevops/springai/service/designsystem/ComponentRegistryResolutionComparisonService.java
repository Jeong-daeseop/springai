package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ResolvedComponentRegistry;
import com.krdevops.springai.mapper.ComponentRegistryResolutionComparisonReportRepository;
import com.krdevops.springai.model.designsystem.ComponentRegistryResolutionComparisonReport;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Legacy Resolver와 Catalog+Registry v3 Resolved Resolver의 관찰 모드 비교기. */
@Service
public class ComponentRegistryResolutionComparisonService {

    private final ComponentRegistryResolutionComparisonReportRepository reportRepository;

    @Autowired
    public ComponentRegistryResolutionComparisonService(ComponentRegistryResolutionComparisonReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    public ComponentRegistryResolutionComparisonService() {
        this.reportRepository = null;
    }

    public ComponentRegistryResolutionComparisonReport compareAndSave(
            String profileId, ComponentRegistry legacy, ResolvedComponentRegistry resolved) {
        if (reportRepository == null) throw new IllegalStateException("Report 저장소가 연결되지 않았습니다.");
        Comparison comparison = compare(legacy, resolved);
        ComponentRegistryResolutionComparisonReport report = new ComponentRegistryResolutionComparisonReport(
                "registry-compare-" + java.util.UUID.randomUUID(), profileId,
                legacy == null ? "unknown" : legacy.registryVersion(),
                resolved == null ? "unknown" : resolved.registryVersion(),
                java.time.Instant.now(), comparison.identical(), comparison.differences());
        reportRepository.save(report);
        return report;
    }

    public Comparison compare(ComponentRegistry legacy, ResolvedComponentRegistry resolved) {
        List<Difference> differences = new ArrayList<>();
        if (legacy == null || resolved == null) {
            differences.add(new Difference("REGISTRY_RESULT_MISSING", null, "Legacy 또는 Resolved 결과가 없습니다."));
            return new Comparison(false, List.copyOf(differences));
        }
        legacy.components().forEach((logicalType, before) -> {
            ResolvedComponentRegistry.ResolvedEntry after = resolved.entries().get(logicalType);
            if (after == null || after.atomicBindings().isEmpty()) {
                differences.add(new Difference("LOGICAL_TYPE_MISSING", logicalType, "Resolved Binding이 없습니다."));
                return;
            }
            ResolvedComponentRegistry.AtomicBinding binding = after.atomicBindings().get(0);
            if (!Objects.equals(before.componentSetKey(), binding.binding().componentSetKey())) {
                differences.add(new Difference("COMPONENT_KEY_CHANGED", logicalType,
                        before.componentSetKey() + " -> " + binding.binding().componentSetKey()));
            }
            before.variants().forEach((variant, key) -> {
                String resolvedKey = binding.binding().variants().get(variant);
                if (resolvedKey != null && !Objects.equals(key, resolvedKey)) {
                    differences.add(new Difference("VARIANT_KEY_CHANGED", logicalType + "/" + variant,
                            key + " -> " + resolvedKey));
                }
            });
        });
        return new Comparison(differences.isEmpty(), List.copyOf(differences));
    }

    public record Comparison(boolean identical, List<Difference> differences) {
        public Comparison { differences = List.copyOf(differences); }
    }
    public record Difference(String code, String target, String detail) {}
}
