package com.krdevops.springai.service.e2e;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class PipelineDoneCriteriaEvaluatorTest {
    @Test void result_exposesFailedCriteria() {
        var result = new PipelineDoneCriteriaEvaluator().evaluate(Map.of("tests", true, "evidence", false));
        assertThat(result.complete()).isFalse();
        assertThat(result.failedCriteria()).containsExactly("evidence");
    }
}
