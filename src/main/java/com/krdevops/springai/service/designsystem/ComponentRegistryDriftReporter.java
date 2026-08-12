package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * KRV-004: 기존 Registry Contract와 실제 Figma Library 사이의 Property·Variant·Lifecycle 차이를
 * 논리 타입(logicalType)별로 집계한 Drift 보고서를 생성한다.
 */
@Component
public class ComponentRegistryDriftReporter {

    private final FigmaPropertyDriftValidator driftValidator;

    public ComponentRegistryDriftReporter(FigmaPropertyDriftValidator driftValidator) {
        this.driftValidator = driftValidator;
    }

    /**
     * @param registry        검사 대상 Registry
     * @param actualSnapshots logicalType → 실제 Figma Library에서 수집한 Snapshot. 항목이 없으면
     *                        {@code COMPONENT_SNAPSHOT_MISSING} WARNING만 보고하고 Property/Variant Drift는 건너뛴다.
     */
    public DriftReport report(
            ComponentRegistry registry,
            Map<String, FigmaPropertyDriftValidator.LibraryComponentSnapshot> actualSnapshots
    ) {
        List<LogicalTypeDrift> perType = new ArrayList<>();
        if (registry == null) {
            return new DriftReport(List.of());
        }
        Map<String, FigmaPropertyDriftValidator.LibraryComponentSnapshot> snapshots =
                actualSnapshots == null ? Map.of() : actualSnapshots;
        registry.components().forEach((logicalType, entry) -> {
            List<DesignSystemIssue> issues = new ArrayList<>();
            if (!entry.currentForGeneration()) {
                issues.add(new DesignSystemIssue("COMPONENT_LIFECYCLE_NOT_CURRENT",
                        DesignSystemIssue.Severity.WARNING,
                        "publishStatus/lifecycleStatus가 CURRENT 조합이 아닙니다 (publishStatus="
                                + entry.publishStatus() + ", lifecycleStatus=" + entry.lifecycleStatus() + ").",
                        logicalType));
            }
            FigmaPropertyDriftValidator.LibraryComponentSnapshot actual = snapshots.get(logicalType);
            if (actual == null) {
                issues.add(new DesignSystemIssue("COMPONENT_SNAPSHOT_MISSING",
                        DesignSystemIssue.Severity.WARNING,
                        "실제 Figma Library Snapshot이 제공되지 않아 Property/Variant Drift를 검사하지 못했습니다.",
                        logicalType));
            } else {
                issues.addAll(driftValidator.validate(logicalType, entry, actual));
            }
            if (!issues.isEmpty()) {
                perType.add(new LogicalTypeDrift(logicalType, List.copyOf(issues)));
            }
        });
        perType.sort(Comparator.comparing(LogicalTypeDrift::logicalType));
        return new DriftReport(List.copyOf(perType));
    }

    public record LogicalTypeDrift(String logicalType, List<DesignSystemIssue> issues) {}

    public record DriftReport(List<LogicalTypeDrift> perType) {
        public boolean hasDrift() {
            return !perType.isEmpty();
        }

        public int totalIssueCount() {
            return perType.stream().mapToInt(entry -> entry.issues().size()).sum();
        }
    }
}
