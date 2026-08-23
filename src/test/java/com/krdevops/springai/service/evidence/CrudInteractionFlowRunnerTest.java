package com.krdevops.springai.service.evidence;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CrudInteractionFlowRunnerTest {
    @Test void 목록_검색_상세_기본_flow를_생성한다() {
        var flow = new CrudInteractionFlowRunner().basicListSearchDetail("crud", "/emp", "/emp/1");
        assertThat(flow.steps()).extracting("action").containsExactly("목록 진입", "검색 실행", "상세 진입");
    }
}
