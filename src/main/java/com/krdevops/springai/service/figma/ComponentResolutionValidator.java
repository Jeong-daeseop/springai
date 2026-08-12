package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.figma.FigmaExportIssue;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** v2 Semantic Node가 모두 Published Component로 해결되었는지 확인한다. */
@Component
public class ComponentResolutionValidator {

    public List<FigmaExportIssue> validate(FigmaNodeSpec root) {
        List<FigmaExportIssue> issues = new ArrayList<>();
        visit(root, issues);
        return List.copyOf(issues);
    }

    private void visit(FigmaNodeSpec node, List<FigmaExportIssue> issues) {
        boolean semanticComponent = node.nodeType() == FigmaNodeSpec.NodeType.COMPONENT
                && node.properties().containsKey("semanticRole");
        if (semanticComponent && node.componentResolution() == null) {
            issues.add(new FigmaExportIssue("ROLE_NOT_RESOLVED", FigmaExportIssue.Severity.FATAL,
                    "Semantic Role이 Published Component로 해결되지 않았습니다.",
                    node.logicalNodeId(), null, null));
        }
        if (node.componentResolution() != null
                && (node.componentResolution().componentSetKey() == null
                || node.componentResolution().componentSetKey().isBlank())) {
            issues.add(new FigmaExportIssue("COMPONENT_KEY_NOT_RESOLVED", FigmaExportIssue.Severity.FATAL,
                    "해결된 Component Key가 없습니다.", node.logicalNodeId(), null, null));
        }
        node.children().forEach(child -> visit(child, issues));
    }
}
