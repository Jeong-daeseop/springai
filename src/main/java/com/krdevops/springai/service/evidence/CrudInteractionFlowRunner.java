package com.krdevops.springai.service.evidence;

import com.krdevops.springai.model.evidence.InteractionFlowEvidence;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CrudInteractionFlowRunner {
    public InteractionFlowEvidence basicListSearchDetail(String flowId, String listRoute, String detailRoute) {
        return new InteractionFlowEvidence(flowId, List.of(
                new InteractionFlowEvidence.Step(1, "목록 진입", listRoute, "list", null),
                new InteractionFlowEvidence.Step(2, "검색 실행", listRoute + "?search=true", "list-search-result", null),
                new InteractionFlowEvidence.Step(3, "상세 진입", detailRoute, "detail", null)), InteractionFlowEvidence.FlowStatus.INCOMPLETE);
    }
}
