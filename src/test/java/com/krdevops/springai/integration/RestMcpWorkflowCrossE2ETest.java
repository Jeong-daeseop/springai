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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** REST에서 만든 상태 원장을 MCP가 이어받아 승인·적용·검증하는 교차 계약 E2E. */
class RestMcpWorkflowCrossE2ETest {

    @TempDir Path projectRoot;

    private ObjectMapper objectMapper;
    private ThymeleafProjectWorkflowService workflow;
    private ThymeleafOperationsController rest;
    private ThymeleafProjectWorkflowTool mcp;

    private void wire() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        workflow = new ThymeleafProjectWorkflowService(
                new ProjectOperationStateService(), new ValidationGateExecutor(),
                new OperationHashFactory(objectMapper));
        rest = new ThymeleafOperationsController(workflow);
        mcp = new ThymeleafProjectWorkflowTool(
                new ThymeleafToolAuthorizationService("shared"), workflow, objectMapper);
    }

    @Test
    void restPreviewCanBeApprovedAppliedAndValidatedThroughMcp() throws Exception {
        wire();
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

    /** ARCH-0813: 반대 방향 — MCP에서 만든 Preview를 REST가 승인하고 다시 MCP가 적용한다. */
    @Test
    void mcpPreviewCanBeApprovedThroughRestAndAppliedThroughMcp() throws Exception {
        wire();
        String relative = "src/main/resources/templates/users/detail.html";

        String previewJson = mcp.previewThymeleafProject(
                "shared", projectRoot.toString(), Map.of(relative, "<main><p th:text=\"${name}\"></p></main>"));
        var preview = objectMapper.readValue(previewJson, ThymeleafProjectWorkflowService.WorkflowResult.class);
        String operationId = preview.operation().operationId();

        var approved = rest.approve(operationId,
                new ThymeleafOperationsController.ApprovalRequest(preview.previewHash()));
        assertThat(approved.operation().status()).isEqualTo(ProjectOperationStatus.APPROVED);

        mcp.applyThymeleafProject("shared", operationId);

        assertThat(Files.readString(projectRoot.resolve(relative))).contains("th:text");
        assertThat(rest.report(operationId).getBody().operation().status())
                .isEqualTo(ProjectOperationStatus.APPLIED);
    }

    /** ARCH-0815: REST로 승인한 뒤 대상 파일이 바뀌면(drift) MCP Apply가 CONFLICT로 막고 아무 것도 쓰지 않는다. */
    @Test
    void sourceDriftAfterRestApprovalBlocksMcpApplyThroughReport() throws Exception {
        wire();
        String relative = "src/main/resources/templates/users/regist.html";
        Files.createDirectories(projectRoot.resolve(relative).getParent());
        Files.writeString(projectRoot.resolve(relative), "<main>original</main>");

        var preview = rest.preview(new ThymeleafOperationsController.PreviewRequest(
                projectRoot.toString(), Map.of(relative, "<main>generated</main>")));
        mcp.approveThymeleafProject("shared", preview.operation().operationId(), preview.previewHash());
        Files.writeString(projectRoot.resolve(relative), "<main>user-changed-after-approval</main>");

        mcp.applyThymeleafProject("shared", preview.operation().operationId());

        assertThat(rest.report(preview.operation().operationId()).getBody().operation().status())
                .isEqualTo(ProjectOperationStatus.CONFLICT);
        assertThat(Files.readString(projectRoot.resolve(relative))).isEqualTo("<main>user-changed-after-approval</main>");
    }

    /** ARCH-0815: MCP Apply 두 건이 동시에 들어와도 하나만 성공하고 나머지는 락으로 막힌다. */
    @Test
    void concurrentMcpApplyOnlyOneSucceeds() throws Exception {
        wire();
        String relative = "src/main/resources/templates/users/concurrent.html";

        var preview = rest.preview(new ThymeleafOperationsController.PreviewRequest(
                projectRoot.toString(), Map.of(relative, "<main>generated</main>")));
        mcp.approveThymeleafProject("shared", preview.operation().operationId(), preview.previewHash());

        int attempts = 4;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger lockedCount = new AtomicInteger();
        try {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    try {
                        go.await();
                        mcp.applyThymeleafProject("shared", preview.operation().operationId());
                        successCount.incrementAndGet();
                    } catch (IllegalStateException e) {
                        if (e.getMessage() != null && e.getMessage().contains("LOCKED")) {
                            lockedCount.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                        // 락 경합 외 사유(이미 APPLIED 등)로 실패해도 이 테스트의 관심사는 "성공은 1건뿐"이다.
                    }
                }));
            }
            ready.await(5, TimeUnit.SECONDS);
            go.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
        }

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(rest.report(preview.operation().operationId()).getBody().operation().status())
                .isEqualTo(ProjectOperationStatus.APPLIED);
    }

    /** ARCH-0816: MCP Apply 도중 실패하면 rollback되고 REST report에 FAILED로 남는다. */
    @Test
    void mcpApplyFailureRollsBackAndReportsFailedThroughRest() throws Exception {
        wire();
        Path first = projectRoot.resolve("a.html");
        Files.writeString(first, "original");
        // "blocked"를 파일로 만들어 blocked/b.html의 부모 디렉터리 생성이 실패하게 한다.
        Files.writeString(projectRoot.resolve("blocked"), "parent-is-a-file");

        var preview = rest.preview(new ThymeleafOperationsController.PreviewRequest(
                projectRoot.toString(), Map.of(
                        "a.html", "generated",
                        "blocked/b.html", "generated")));
        mcp.approveThymeleafProject("shared", preview.operation().operationId(), preview.previewHash());

        assertThatThrownBy(() -> mcp.applyThymeleafProject("shared", preview.operation().operationId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APPLY_ROLLED_BACK");

        assertThat(first).hasContent("original");
        assertThat(rest.report(preview.operation().operationId()).getBody().operation().status())
                .isEqualTo(ProjectOperationStatus.FAILED);
    }
}
