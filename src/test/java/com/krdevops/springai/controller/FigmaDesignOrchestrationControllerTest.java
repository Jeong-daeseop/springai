package com.krdevops.springai.controller;

import com.krdevops.springai.model.figma.contract.FigmaDesignOperation;
import com.krdevops.springai.model.figma.contract.FigmaDesignOperationStatus;
import com.krdevops.springai.model.figma.contract.FigmaDesignRequest;
import com.krdevops.springai.service.figma.FigmaDesignOrchestrationService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 22/23번 문서 A-02: bindFigmaDesignRequestTable MCP Tool과 동일한 동작을 제공하는 REST 진입점 검증. */
class FigmaDesignOrchestrationControllerTest {

    @Test
    void bindTableDelegatesToOrchestrationService() {
        FigmaDesignOrchestrationService service = mock(FigmaDesignOrchestrationService.class);
        FigmaDesignOrchestrationController controller = new FigmaDesignOrchestrationController(service);
        FigmaDesignRequest request = FigmaDesignRequest.referenceStyle(
                "기존 목록처럼", "allowed-file", List.of("1:2"), "ebt", "emp_list", null, null);
        Instant now = Instant.now();
        FigmaDesignOperation expected = new FigmaDesignOperation(
                "figop-test", 2, request, "b".repeat(64), FigmaDesignOperationStatus.PREVIEW_READY,
                null, List.of(), List.of(), now, now);
        when(service.bindTable("figop-test", "ebt", "emp_list")).thenReturn(expected);

        var result = controller.bindTable(
                new FigmaDesignOrchestrationController.BindTableRequest("figop-test", "ebt", "emp_list"));

        assertThat(result.status()).isEqualTo(FigmaDesignOperationStatus.PREVIEW_READY);
    }

    /** 서비스가 상태 위반(IllegalStateException)을 던지면 400 표준 오류(FigmaRequestException)로 변환한다. */
    @Test
    void bindTableWrapsServiceStateViolationAsFigmaRequestException() {
        FigmaDesignOrchestrationService service = mock(FigmaDesignOrchestrationService.class);
        FigmaDesignOrchestrationController controller = new FigmaDesignOrchestrationController(service);
        when(service.bindTable("figop-test", "ebt", "emp_list")).thenThrow(new IllegalStateException(
                "AWAITING_TABLE_BINDING 상태의 Operation만 테이블을 바인딩할 수 있습니다: ANALYZED"));

        assertThatThrownBy(() -> controller.bindTable(
                new FigmaDesignOrchestrationController.BindTableRequest("figop-test", "ebt", "emp_list")))
                .isInstanceOf(FigmaRequestException.class)
                .hasMessageContaining("AWAITING_TABLE_BINDING");
    }
}
