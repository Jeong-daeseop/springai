package com.krdevops.springai.service.e2e;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class PipelineCompletionGateReportTest {
    @Test void report_exposesFailedGateNames() {
        var report = new PipelineCompletionGateReport().evaluate(Map.of("binding", true, "a11y", false));
        assertThat(report.passed()).isFalse();
        assertThat(report.failedGateNames()).containsExactly("a11y");
    }

    @Test void report_rejectsBlankGateName() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new PipelineCompletionGateReport().evaluate(Map.of("", true)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void report_treatsNullGateAsFailure() {
        var gates = new java.util.HashMap<String, Boolean>();
        gates.put("render", null);
        var report = new PipelineCompletionGateReport().evaluate(gates);
        assertThat(report.passed()).isFalse();
        assertThat(report.failedGateNames()).containsExactly("render");
    }
}
