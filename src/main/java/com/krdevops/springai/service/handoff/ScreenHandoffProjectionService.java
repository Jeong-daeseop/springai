package com.krdevops.springai.service.handoff;
import com.krdevops.springai.model.handoff.ScreenHandoffBundle;
import org.springframework.stereotype.Service;
import java.util.List;
import com.krdevops.springai.service.pipeline.PipelineOperationGate;
import com.krdevops.springai.service.pipeline.PipelineActionAuthorization;
import com.krdevops.springai.model.contract.PipelineReleaseGateResponse;

@Service public class ScreenHandoffProjectionService {
    public Projection project(ScreenHandoffBundle bundle, Audience audience) {
        if (bundle == null || !bundle.hasValidContentHash()) throw new IllegalArgumentException("Handoff Bundle이 유효하지 않습니다.");
        List<String> actions = switch (audience) {
            case DESIGNER -> List.of("REVIEW_VISUAL", "REQUEST_CHANGE");
            case BUSINESS -> List.of("REVIEW_BINDING", "APPROVE");
            case DEVELOPER, QA -> List.of("RUN_VALIDATION", "REQUEST_CHANGE");
            case APPROVER -> List.of("APPROVE", "REJECT");
            case AGENT -> List.of("RUN_VALIDATION", "PREVIEW_CHANGE");
        };
        return new Projection(audience, bundle.bundleId(), bundle.contentHash(), bundle.issues(), actions,
                false, List.of(), bundle.auditSnapshotHash());
    }
    public Projection projectWithReleaseGate(ScreenHandoffBundle bundle, Audience audience,
                                             PipelineReleaseGateResponse releaseGate) {
        if (releaseGate == null) throw new IllegalArgumentException("releaseGate는 필수입니다.");
        Projection base = project(bundle, audience);
        return new Projection(base.audience(), base.bundleId(), base.bundleHash(), base.issues(),
                base.nextAllowedActions(), releaseGate.ready(), releaseGate.failedGateNames(), bundle.auditSnapshotHash());
    }
    public Projection projectAuthorized(ScreenHandoffBundle bundle, Audience audience,
                                       PipelineOperationGate gate,
                                       PipelineActionAuthorization.AuthorizationContext context) {
        if (gate == null) throw new IllegalArgumentException("operation gate는 필수입니다.");
        gate.authorize("getScreenHandoff", context);
        return project(bundle, audience);
    }
    public enum Audience { DESIGNER, BUSINESS, DEVELOPER, QA, APPROVER, AGENT }
    public record Projection(Audience audience, String bundleId, String bundleHash, List<String> issues, List<String> nextAllowedActions,
                             boolean releaseReady, List<String> failedGateNames, String auditSnapshotHash) {
        public Projection(Audience audience, String bundleId, String bundleHash, List<String> issues, List<String> nextAllowedActions) {
            this(audience, bundleId, bundleHash, issues, nextAllowedActions, false, List.of(), "0".repeat(64));
        }
        public Projection { issues=List.copyOf(issues==null?List.of():issues); nextAllowedActions=List.copyOf(nextAllowedActions==null?List.of():nextAllowedActions); failedGateNames=List.copyOf(failedGateNames==null?List.of():failedGateNames); }
    }
}
