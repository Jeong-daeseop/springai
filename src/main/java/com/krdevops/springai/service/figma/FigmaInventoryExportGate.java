package com.krdevops.springai.service.figma;

import com.krdevops.springai.mapper.FigmaLibraryInventoryRepository;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import com.krdevops.springai.model.figma.FigmaExportIssue;
import com.krdevops.springai.model.figma.FigmaExportMode;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.service.designsystem.FigmaPropertyDriftValidator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Export가 참조한 Registry와 같은 버전의 실제 Figma Inventory를 사전 Gate로 검증한다. */
@Component
public class FigmaInventoryExportGate {
    private final FigmaLibraryInventoryRepository repository;
    private final FigmaPropertyDriftValidator validator;

    public FigmaInventoryExportGate(FigmaLibraryInventoryRepository repository,
            FigmaPropertyDriftValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    public List<FigmaExportIssue> validate(ComponentRegistry registry, FigmaNodeSpec content, FigmaExportMode mode) {
        var snapshot = repository.findLatest(registry.profileId(), registry.registryVersion()).orElse(null);
        if (snapshot == null) {
            var severity = mode == FigmaExportMode.FINAL
                    ? FigmaExportIssue.Severity.FATAL : FigmaExportIssue.Severity.WARNING;
            return List.of(new FigmaExportIssue("FIGMA_INVENTORY_SNAPSHOT_MISSING", severity,
                    "Registry 버전과 일치하는 실제 Figma Library Inventory가 없습니다.",
                    registry.registryVersion(), null, null));
        }
        Set<String> used = new LinkedHashSet<>();
        collect(content, used);
        List<FigmaExportIssue> result = new ArrayList<>();
        for (String logicalType : used) {
            var contract = registry.components().get(logicalType);
            var actual = snapshot.components().get(logicalType);
            if (contract == null) continue; // Resolution Gate가 별도 오류를 낸다.
            for (DesignSystemIssue issue : validator.validate(logicalType, contract, actual)) {
                result.add(new FigmaExportIssue(issue.code(), FigmaExportIssue.Severity.FATAL,
                        issue.message(), logicalType, null, null));
            }
        }
        return List.copyOf(result);
    }

    private void collect(FigmaNodeSpec node, Set<String> used) {
        if (node.nodeType() == FigmaNodeSpec.NodeType.COMPONENT) used.add(node.type());
        node.children().forEach(child -> collect(child, used));
    }
}
