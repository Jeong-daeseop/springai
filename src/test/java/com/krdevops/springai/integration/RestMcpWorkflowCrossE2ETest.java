package com.krdevops.springai.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.controller.ThymeleafOperationsController;
import com.krdevops.springai.model.thymeleaf.ProjectOperationStatus;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.thymeleaf.ProjectOperationStateService;
import com.krdevops.springai.service.thymeleaf.ThymeleafProjectWorkflowService;
import com.krdevops.springai.service.thymeleaf.ThymeleafToolAuthorizationService;
import com.krdevops.springai.service.thymeleaf.ValidationGateExecutor;
import com.krdevops.springai.tools.ThymeleafProjectWorkflowTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** REST에서 만든 상태 원장을 MCP가 이어받아 승인·적용·검증하는 교차 계약 E2E. */
class RestMcpWorkflowCrossE2ETest {

    @TempDir Path projectRoot;

    @Test
    void restPreviewCanBeApprovedAppliedAndValidatedThroughMcp() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ThymeleafProjectWorkflowService workflow = new ThymeleafProjectWorkflowService(
                new ProjectOperationStateService(), new ValidationGateExecutor(),
                new OperationHashFactory(objectMapper));
        ThymeleafOperationsController rest = new ThymeleafOperationsController(workflow);
        ThymeleafProjectWorkflowTool mcp = new ThymeleafProjectWorkflowTool(
                new ThymeleafToolAuthorizationService("shared"), workflow, objectMapper);
        String relative = "src/main/resources/templates/users/list.html";

        var preview = rest.preview(new ThymeleafOperationsController.PreviewRequest(
                projectRoot.toString(), Map.of(relative, "<main><p th:text=\"${title}\"></p></main>")));
        String operationId = preview.operation().operationId();
        assertThat(projectRoot.resolve(relative)).doesNotExist();

        mcp.approveThymeleafProject("shared", operationId, preview.previewHash());
        mcp.applyThymeleafProject("shared", operationId);
        mcp.revalidateThymeleafProject("shared", operationId);

        assertThat(Files.readString(projectRoot.resolve(relative))).contains("th:text");
        assertThat(rest.report(operationId).getBody().operation().status())
                .isEqualTo(ProjectOperationStatus.VALIDATED);
    }
}
