package com.krdevops.springai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.krdevops.springai.model.figma.hybrid.FigmaHybridCandidate;
import com.krdevops.springai.model.figma.hybrid.FigmaHybridCandidateRequest;
import com.krdevops.springai.service.figma.FigmaHybridExportService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FigmaHybridControllerTest {

    @Test
    void 후보생성API는Hybrid서비스결과를반환한다() {
        FigmaHybridExportService service = org.mockito.Mockito.mock(FigmaHybridExportService.class);
        FigmaHybridController controller = new FigmaHybridController(service);
        String artifactId = UUID.randomUUID().toString();
        FigmaHybridCandidateRequest request = new FigmaHybridCandidateRequest(
                artifactId, "ebt", "COMTNEMPLYRINFO", "사용자 목록", "crud",
                List.of(), List.of());
        FigmaHybridCandidate expected = org.mockito.Mockito.mock(FigmaHybridCandidate.class);
        when(service.createCandidate(request)).thenReturn(expected);

        assertThat(controller.createCandidate(request)).isSameAs(expected);
    }

    @Test
    void 존재하지않는후보는표준404예외코드로변환한다() {
        FigmaHybridExportService service = org.mockito.Mockito.mock(FigmaHybridExportService.class);
        FigmaHybridController controller = new FigmaHybridController(service);
        String artifactId = UUID.randomUUID().toString();
        when(service.getCandidate(artifactId))
                .thenThrow(new IllegalArgumentException("Hybrid 후보를 찾을 수 없습니다"));

        assertThatThrownBy(() -> controller.getCandidate(artifactId))
                .isInstanceOfSatisfying(FigmaResourceNotFoundException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("FIGMA_HYBRID_CANDIDATE_NOT_FOUND");
                    assertThat(exception.getMessage()).contains("Hybrid 후보");
                });
    }

    @Test
    void 승인요청오류는표준400예외코드로변환한다() {
        FigmaHybridExportService service = org.mockito.Mockito.mock(FigmaHybridExportService.class);
        FigmaHybridController controller = new FigmaHybridController(service);
        String artifactId = UUID.randomUUID().toString();
        var request = new com.krdevops.springai.model.figma.hybrid.FigmaHybridApprovalRequest(
                "screen", "list", "krds", "DESKTOP", null, null, true);
        when(service.approveAndExport(artifactId, request))
                .thenThrow(new IllegalArgumentException("후보 버전이 변경되었습니다"));

        assertThatThrownBy(() -> controller.approve(artifactId, request))
                .isInstanceOfSatisfying(FigmaRequestException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("FIGMA_HYBRID_APPROVAL_INVALID");
                    assertThat(exception.getMessage()).contains("버전");
                });
    }
}
