package com.krdevops.springai.model.contract;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PipelineErrorResponseTest {
    @Test void factory_allowsOptionalOperationId() {
        var response = PipelineErrorResponse.forOperation("GATE_BLOCKED", "검증 실패", null);
        assertThat(response.hasOperationId()).isFalse();
        assertThat(response.safeSummary()).isEqualTo("GATE_BLOCKED");
        assertThat(response.withOperationId("op-42").safeSummary()).isEqualTo("GATE_BLOCKED[op-42]");
        assertThat(response.withOperationId("op-42").safeLogFields())
                .containsEntry("code", "GATE_BLOCKED").containsEntry("operationId", "op-42");
    }
    @Test void response_rejectsUnsafeCode() {
        assertThatThrownBy(() -> new PipelineErrorResponse("bad code", "실패", "op-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void response_rejectsUnsafeOperationId() {
        assertThatThrownBy(() -> new PipelineErrorResponse("GATE_BLOCKED", "실패", "bad id"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void response_rejectsOversizedMessage() {
        assertThatThrownBy(() -> new PipelineErrorResponse("GATE_BLOCKED", "x".repeat(4097), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
