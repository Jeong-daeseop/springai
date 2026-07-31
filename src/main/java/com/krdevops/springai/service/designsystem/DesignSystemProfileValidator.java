package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.ComponentBinding;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import com.krdevops.springai.model.designsystem.DesignSystemProfile;
import com.krdevops.springai.model.designsystem.VariableBinding;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** R1-031: DesignSystemProfile의 Java 의미 검증. Binding 상태와 실제 key 존재 여부의 정합성을 확인한다. */
@Service
public class DesignSystemProfileValidator {

    public List<DesignSystemIssue> validate(DesignSystemProfile profile) {
        List<DesignSystemIssue> issues = new ArrayList<>();
        if (profile == null) {
            issues.add(fatal("PROFILE_NULL", "DesignSystemProfile이 null입니다.", null));
            return issues;
        }

        profile.components().forEach((logicalType, binding) ->
                validateComponentBinding(logicalType, binding, issues));
        profile.variables().forEach((logicalToken, binding) ->
                validateVariableBinding(logicalToken, binding, issues));

        if (profile.status() == DesignSystemProfile.Status.PUBLISHED) {
            if (profile.libraryFileKey() == null || profile.libraryFileKey().isBlank()) {
                issues.add(error("PUBLISHED_WITHOUT_FILE_KEY",
                        "PUBLISHED 상태인데 libraryFileKey가 없습니다.", profile.id()));
            }
            boolean hasUnbound = profile.components().values().stream()
                    .anyMatch(b -> b.status() != ComponentBinding.BindingStatus.BOUND)
                    || profile.variables().values().stream()
                    .anyMatch(b -> b.status() != ComponentBinding.BindingStatus.BOUND);
            if (hasUnbound) {
                issues.add(warning("PUBLISHED_WITH_UNBOUND_ENTRY",
                        "PUBLISHED 상태인데 BOUND가 아닌 component/variable binding이 남아 있습니다.", profile.id()));
            }
        }

        return issues;
    }

    private void validateComponentBinding(String logicalType, ComponentBinding binding, List<DesignSystemIssue> issues) {
        if (binding.status() == ComponentBinding.BindingStatus.BOUND
                && (binding.componentSetKey() == null || binding.componentSetKey().isBlank())) {
            issues.add(error("BOUND_COMPONENT_WITHOUT_KEY",
                    "논리 타입 " + logicalType + "이(가) BOUND 상태인데 componentSetKey가 없습니다.", logicalType));
        }
    }

    private void validateVariableBinding(String logicalToken, VariableBinding binding, List<DesignSystemIssue> issues) {
        if (binding.status() == ComponentBinding.BindingStatus.BOUND
                && (binding.variableId() == null || binding.variableId().isBlank())) {
            issues.add(error("BOUND_VARIABLE_WITHOUT_ID",
                    "논리 토큰 " + logicalToken + "이(가) BOUND 상태인데 variableId가 없습니다.", logicalToken));
        }
    }

    private DesignSystemIssue fatal(String code, String message, String targetId) {
        return new DesignSystemIssue(code, DesignSystemIssue.Severity.FATAL, message, targetId);
    }

    private DesignSystemIssue error(String code, String message, String targetId) {
        return new DesignSystemIssue(code, DesignSystemIssue.Severity.ERROR, message, targetId);
    }

    private DesignSystemIssue warning(String code, String message, String targetId) {
        return new DesignSystemIssue(code, DesignSystemIssue.Severity.WARNING, message, targetId);
    }
}
