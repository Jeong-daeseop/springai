package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.figma.FigmaExportIssue;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * R1-033/R1-034/R2-009/R2-T07: JSON Schema 구조 오류와 Java 의미 오류를
 * 하나의 FigmaExportIssue 목록으로 통합한다.
 */
@Component
public class FigmaScreenSpecValidator {

    private final FigmaContractSchemaValidator schemaValidator;

    public FigmaScreenSpecValidator() {
        this(new FigmaContractSchemaValidator(
                new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public FigmaScreenSpecValidator(FigmaContractSchemaValidator schemaValidator) {
        this.schemaValidator = schemaValidator;
    }

    public List<FigmaExportIssue> validate(FigmaScreenSpec spec) {
        List<FigmaExportIssue> issues = new ArrayList<>();
        if (spec == null) {
            issues.add(fatal("SPEC_NULL", "FigmaScreenSpec이 null입니다.", null, ""));
            return issues;
        }

        schemaValidator.validate(spec).stream()
                .map(issue -> new FigmaExportIssue(
                        issue.code(), FigmaExportIssue.Severity.FATAL,
                        issue.message(), issue.logicalNodeId(), issue.jsonPointer(), null))
                .forEach(issues::add);

        if (spec.content() == null) {
            issues.add(fatal("CONTENT_MISSING", "content(루트 노드)가 없습니다.", null, "/content"));
            return issues;
        }

        Set<String> seenIds = new HashSet<>();
        checkNode(spec.content(), seenIds, issues, "/content");
        if (spec.semanticPattern() != null) {
            issues.addAll(new ComponentResolutionValidator().validate(spec.content()));
        }
        return issues;
    }

    private void checkNode(
            FigmaNodeSpec node,
            Set<String> seenIds,
            List<FigmaExportIssue> issues,
            String pointer) {
        if (!seenIds.add(node.logicalNodeId())) {
            issues.add(error("DUPLICATE_LOGICAL_NODE_ID",
                    "logicalNodeId가 중복되었습니다: " + node.logicalNodeId(),
                    node.logicalNodeId(), pointer + "/logicalNodeId"));
        }
        if (Boolean.TRUE.equals(node.properties().get("unsupportedControl"))) {
            issues.add(warning("UNSUPPORTED_CONTROL",
                    "지원하지 않는 Control이라 기본 krds.textField로 대체되었습니다: "
                            + node.logicalNodeId(),
                    node.logicalNodeId(), pointer + "/properties/unsupportedControl"));
            issues.add(fatal("UNAPPROVED_COMPONENT_FALLBACK",
                    "지원하지 않는 Control의 자동 Fallback은 허용되지 않습니다.",
                    node.logicalNodeId(), pointer + "/properties/unsupportedControl"));
        }
        for (int index = 0; index < node.children().size(); index++) {
            checkNode(node.children().get(index), seenIds, issues,
                    pointer + "/children/" + index);
        }
    }

    private FigmaExportIssue fatal(
            String code, String message, String logicalNodeId, String jsonPointer) {
        return new FigmaExportIssue(
                code, FigmaExportIssue.Severity.FATAL, message, logicalNodeId, jsonPointer, null);
    }

    private FigmaExportIssue error(
            String code, String message, String logicalNodeId, String jsonPointer) {
        return new FigmaExportIssue(
                code, FigmaExportIssue.Severity.ERROR, message, logicalNodeId, jsonPointer, null);
    }

    private FigmaExportIssue warning(
            String code, String message, String logicalNodeId, String jsonPointer) {
        return new FigmaExportIssue(
                code, FigmaExportIssue.Severity.WARNING, message, logicalNodeId, jsonPointer, null);
    }
}
