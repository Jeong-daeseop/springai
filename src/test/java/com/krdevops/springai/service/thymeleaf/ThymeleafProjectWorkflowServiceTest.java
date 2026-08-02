package com.krdevops.springai.service.thymeleaf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.thymeleaf.ProjectOperationStatus;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.contract.GenerationIssueFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThymeleafProjectWorkflowServiceTest {

    @TempDir Path projectRoot;
    private ThymeleafProjectWorkflowService workflow;

    @BeforeEach
    void setUp() {
        workflow = new ThymeleafProjectWorkflowService(
                new ProjectOperationStateService(), new ValidationGateExecutor(),
                new OperationHashFactory(new ObjectMapper().findAndRegisterModules()));
    }

    @Test
    void previewApproveApplyValidateWithoutPreApprovalWrite() throws Exception {
        String relative = "src/main/resources/templates/users/list.html";
        String html = "<div><form th:action=\"/users\"></form></div>";

        var preview = workflow.preview(projectRoot, Map.of(relative, html));
        Path target = projectRoot.resolve(relative);
        assertThat(preview.operation().status()).isEqualTo(ProjectOperationStatus.PREVIEW_READY);
        assertThat(target).doesNotExist();
        assertThatThrownBy(() -> workflow.approve(preview.operation().operationId(), "bad-hash"))
                .hasMessageContaining("PREVIEW_HASH_MISMATCH");

        var approved = workflow.approve(preview.operation().operationId(), preview.previewHash());
        assertThat(approved.operation().status()).isEqualTo(ProjectOperationStatus.APPROVED);
        assertThat(target).doesNotExist();

        var applied = workflow.apply(preview.operation().operationId());
        assertThat(applied.operation().status()).isEqualTo(ProjectOperationStatus.APPLIED);
        assertThat(target).hasContent(html);

        var validated = workflow.revalidate(preview.operation().operationId());
        assertThat(validated.operation().status()).isEqualTo(ProjectOperationStatus.VALIDATED);
    }

    @Test
    void sourceRevisionDriftProducesConflictWithoutOverwrite() throws Exception {
        String relative = "src/main/resources/templates/users/list.html";
        Path target = projectRoot.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.writeString(target, "<div>original</div>");

        var preview = workflow.preview(projectRoot, Map.of(relative, "<div>generated</div>"));
        workflow.approve(preview.operation().operationId(), preview.previewHash());
        Files.writeString(target, "<div>user-change</div>");

        var result = workflow.apply(preview.operation().operationId());

        assertThat(result.operation().status()).isEqualTo(ProjectOperationStatus.CONFLICT);
        assertThat(result.operation().conflictingFiles()).containsExactly(relative);
        assertThat(target).hasContent("<div>user-change</div>");
    }

    @Test
    void pathTraversalIsRejectedBeforePreview() {
        assertThatThrownBy(() -> workflow.preview(projectRoot, Map.of("../escape.html", "<div/>")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("PATH_ESCAPE");
    }

    @Test
    void symbolicLinkParentIsRejectedBeforePreview() throws Exception {
        Path outside = Files.createTempDirectory("thymeleaf-outside-");
        Files.createSymbolicLink(projectRoot.resolve("linked"), outside);

        assertThatThrownBy(() -> workflow.preview(
                projectRoot, Map.of("linked/escape.html", "<div/>")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("PATH_ESCAPE");
        assertThat(outside.resolve("escape.html")).doesNotExist();
    }

    @Test
    void forbiddenBusinessRuleInDesignMdStopsBeforePreview() throws Exception {
        Files.writeString(projectRoot.resolve("DESIGN.md"), """
                ---
                schemaVersion: "1.0"
                components:
                  form:
                    route: /admin
                ---
                """);

        assertThatThrownBy(() -> designAwareWorkflow().preview(
                projectRoot, Map.of("templates/list.html", "<div/>")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DESIGN_RULE_GATE_FAILED");
        assertThat(projectRoot.resolve("templates/list.html")).doesNotExist();
    }

    @Test
    void designMdDriftAfterApprovalProducesConflictWithoutWrite() throws Exception {
        Files.writeString(projectRoot.resolve("DESIGN.md"), validDesign("8px"));
        ThymeleafProjectWorkflowService designAware = designAwareWorkflow();
        String relative = "templates/list.html";
        var preview = designAware.preview(projectRoot, Map.of(relative, "<div></div>"));
        designAware.approve(preview.operation().operationId(), preview.previewHash());
        Files.writeString(projectRoot.resolve("DESIGN.md"), validDesign("12px"));

        var result = designAware.apply(preview.operation().operationId());

        assertThat(result.operation().status()).isEqualTo(ProjectOperationStatus.CONFLICT);
        assertThat(result.operation().conflictingFiles()).contains("DESIGN.md");
        assertThat(projectRoot.resolve(relative)).doesNotExist();
    }

    @Test
    void validationFailureCannotBeApproved() {
        var preview = workflow.preview(projectRoot, Map.of(
                "templates/broken.html", "<div style=\"width: 1600px\">"));

        assertThat(preview.operation().validationErrors()).isNotEmpty();
        assertThatThrownBy(() -> workflow.approve(
                preview.operation().operationId(), preview.previewHash()))
                .hasMessageContaining("PREVIEW_VALIDATION_FAILED");
    }

    @Test
    void secondFileFailureRollsBackFirstFile() throws Exception {
        Path first = projectRoot.resolve("a.html");
        Files.writeString(first, "<div>original</div>");
        var preview = workflow.preview(projectRoot, Map.of(
                "a.html", "<div>generated</div>",
                "blocked/b.html", "<div>second</div>"));
        workflow.approve(preview.operation().operationId(), preview.previewHash());
        Files.writeString(projectRoot.resolve("blocked"), "parent-is-a-file");

        assertThatThrownBy(() -> workflow.apply(preview.operation().operationId()))
                .hasMessageContaining("APPLY_ROLLED_BACK");
        assertThat(first).hasContent("<div>original</div>");
        assertThat(workflow.find(preview.operation().operationId()).orElseThrow()
                .operation().status()).isEqualTo(ProjectOperationStatus.FAILED);
    }

    private ThymeleafProjectWorkflowService designAwareWorkflow() {
        OperationHashFactory hashFactory = new OperationHashFactory(
                new ObjectMapper().findAndRegisterModules());
        return new ThymeleafProjectWorkflowService(
                new ProjectOperationStateService(), new ValidationGateExecutor(), hashFactory,
                new DesignMdRuleLoader(new LegacySourceInventoryService(hashFactory),
                        new GenerationIssueFactory()));
    }

    private String validDesign(String spacing) {
        return """
                ---
                schemaVersion: "1.0"
                spacing:
                  medium: "%s"
                ---
                """.formatted(spacing);
    }
}
