package com.krdevops.springai.model.capture;

import java.util.List;

public record ComponentCandidate(String type, List<String> nodeIds, double confidence, List<String> evidence) {
    public ComponentCandidate {
        nodeIds = nodeIds == null ? List.of() : List.copyOf(nodeIds);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
