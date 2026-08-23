package com.krdevops.springai.service.handoff;
import com.krdevops.springai.model.handoff.ScreenHandoffBundle;
import org.springframework.stereotype.Service;
import java.util.Set;

@Service public class HandoffActionGuard {
    private static final Set<String> ACTIONS=Set.of("REVIEW_VISUAL","REQUEST_CHANGE","REVIEW_BINDING","APPROVE","RUN_VALIDATION","PREVIEW_CHANGE","REJECT");
    public void requireAllowed(ScreenHandoffProjectionService.Projection projection, String action) { if (projection==null||action==null||!ACTIONS.contains(action)||!projection.nextAllowedActions().contains(action)) throw new IllegalStateException("Handoff Action이 허용되지 않습니다: "+action); }
    public void requireApplyPermission(ScreenHandoffProjectionService.Projection projection, boolean applyPermission) { if(!applyPermission) throw new IllegalStateException("Handoff Apply 권한이 없습니다."); if(projection==null||projection.audience()!= ScreenHandoffProjectionService.Audience.AGENT) throw new IllegalStateException("Agent Apply 경계가 아닙니다."); }
}
