package com.krdevops.springai.model.contract;

import com.krdevops.springai.service.e2e.PipelineReleaseReadiness;
import java.util.List;
import java.util.Map;

/** Release Gate 평가 결과를 API/Handoff 계약으로 직렬화하는 응답 모델. */
public record PipelineReleaseGateResponse(boolean ready, Map<String, Boolean> gates,
                                          List<String> failedGateNames) {
    public PipelineReleaseGateResponse {
        gates = Map.copyOf(gates == null ? Map.of() : gates);
        failedGateNames = List.copyOf(failedGateNames == null ? List.of() : failedGateNames);
    }

    public static PipelineReleaseGateResponse from(PipelineReleaseReadiness.Readiness readiness) {
        if (readiness == null) throw new IllegalArgumentException("readiness는 필수입니다.");
        return new PipelineReleaseGateResponse(readiness.ready(), readiness.gates(), readiness.failedGateNames());
    }
}
