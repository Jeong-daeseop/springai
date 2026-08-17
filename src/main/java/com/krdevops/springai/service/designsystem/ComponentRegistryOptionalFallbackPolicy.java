package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.ComponentCatalog;
import com.krdevops.springai.model.designsystem.ComponentRegistrySnapshotV3;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import org.springframework.stereotype.Service;

/** Preview와 Apply의 Optional Binding fallback 정책을 서버에서 공통 판단한다. */
@Service
public class ComponentRegistryOptionalFallbackPolicy {
    public Decision decide(ComponentCatalog.Entry contract, ComponentRegistrySnapshotV3.Binding binding,
                           boolean previewOnly) {
        if (binding != null) return new Decision(false, false, null);
        if (contract == null || contract.requirement() == ComponentCatalog.Requirement.REQUIRED) {
            return new Decision(false, true, new DesignSystemIssue("REQUIRED_BINDING_MISSING",
                    DesignSystemIssue.Severity.ERROR, "필수 Binding이 없어 fallback할 수 없습니다.", null));
        }
        if (!previewOnly) {
            return new Decision(false, true, new DesignSystemIssue("OPTIONAL_BINDING_APPLY_BLOCKED",
                    DesignSystemIssue.Severity.ERROR, "Optional Binding fallback은 Preview에서만 허용됩니다.", null));
        }
        return new Decision(true, false, new DesignSystemIssue("OPTIONAL_BINDING_MISSING_PREVIEW_FALLBACK",
                DesignSystemIssue.Severity.WARNING, "Optional Binding 누락을 Preview fallback으로 표시합니다.", null));
    }

    public record Decision(boolean fallback, boolean blocked, DesignSystemIssue issue) {}
}
