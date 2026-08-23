package com.krdevops.springai.service.evidence;

import com.krdevops.springai.model.evidence.InteractionFlowEvidence;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CrudWriteInteractionFlowRunner {
    public InteractionFlowEvidence createUpdateCancel(String flowId, String listRoute, String createRoute, String updateRoute) {
        return new InteractionFlowEvidence(flowId, List.of(
                new InteractionFlowEvidence.Step(1, "등록 화면 진입", createRoute, "create", null),
                new InteractionFlowEvidence.Step(2, "수정 화면 진입", updateRoute, "update", null),
                new InteractionFlowEvidence.Step(3, "취소 후 목록 복귀", listRoute, "list", null)), InteractionFlowEvidence.FlowStatus.INCOMPLETE);
    }
}
