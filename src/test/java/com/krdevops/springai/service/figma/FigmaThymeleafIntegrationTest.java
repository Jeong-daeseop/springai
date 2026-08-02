package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.figma.FigmaThymeleafMapping;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FigmaThymeleafIntegrationTest {

    private ScreenToFigmaComponentMapper componentMapper;
    private FigmaUpdateApplier updateApplier;
    private ScreenPreviewGenerator previewGenerator;
    private ApprovalWorkflow approvalWorkflow;

    @BeforeEach
    void setUp() {
        componentMapper = new ScreenToFigmaComponentMapper();
        updateApplier = new FigmaUpdateApplier();
        previewGenerator = new ScreenPreviewGenerator();
        approvalWorkflow = new ApprovalWorkflow();
    }

    @Test
    void testComponentMapping() {
        String html = "<div th:object=\"*{employee}\"><input th:field=\"*{name}\" />" +
                     "<input th:field=\"*{email}\" /></div>";

        Map<String, Object> mapping = componentMapper.mapHtmlToFigmaComponents(html, "Employee");

        assertNotNull(mapping);
        assertEquals("Employee", mapping.get("screenName"));
        assertTrue(mapping.containsKey("components"));
        assertTrue(mapping.containsKey("fields"));
    }

    @Test
    void testComponentExtraction() {
        String html = "<div th:object=\"*{user}\"></div>";
        List<String> components = componentMapper.extractComponents(html);

        assertFalse(components.isEmpty());
        assertTrue(components.contains("user"));
    }

    @Test
    void testFormFieldExtraction() {
        String html = "<input th:field=\"*{username}\" /><input th:field=\"*{email}\" />";
        List<String> fields = componentMapper.extractFormFields(html);

        assertEquals(2, fields.size());
        assertTrue(fields.contains("username"));
        assertTrue(fields.contains("email"));
    }

    @Test
    void testFigmaNodeStructureConversion() {
        List<String> components = List.of("employee", "department");
        List<String> fields = List.of("name", "email");

        Map<String, Object> structure = componentMapper.convertToFigmaNodeStructure(components, fields);

        assertEquals("FRAME", structure.get("type"));
        assertTrue(structure.containsKey("children"));
    }

    @Test
    void testCssGenerationFromDesignTokens() {
        Map<String, String> tokens = new HashMap<>();
        tokens.put("color-primary", "#007bff");
        tokens.put("color-secondary", "#6c757d");

        String css = updateApplier.generateCssFromDesignTokens(tokens);

        assertTrue(css.contains("--color-primary"));
        assertTrue(css.contains("#007bff"));
    }

    @Test
    void testCssPatchingWithDesignTokens() {
        String originalCss = "color: #007bff;";
        Map<String, String> tokens = new HashMap<>();
        tokens.put("color-primary", "#007bff");

        String patchedCss = updateApplier.patchCssWithDesignTokens(originalCss, tokens);

        assertTrue(patchedCss.contains("var(--color-primary)"));
    }

    @Test
    void testPreviewMetadataGeneration() {
        Map<String, Object> metadata = previewGenerator.generatePreviewMetadata(
            "figma-file-123",
            "node-456",
            "Employee Form",
            "/preview/abc123"
        );

        assertEquals("figma-file-123", metadata.get("figmaFileId"));
        assertEquals("Employee Form", metadata.get("screenName"));
        assertEquals("READY_FOR_EMBED", metadata.get("status"));
    }

    @Test
    void testViewportPreviewOptions() {
        Map<String, Map<String, Object>> options = previewGenerator.generateViewportPreviewOptions();

        assertTrue(options.containsKey("DESKTOP"));
        assertTrue(options.containsKey("TABLET"));
        assertTrue(options.containsKey("MOBILE"));

        assertEquals(1440, options.get("DESKTOP").get("width"));
        assertEquals(768, options.get("TABLET").get("width"));
        assertEquals(390, options.get("MOBILE").get("width"));
    }

    @Test
    void testApprovalBoardCreation() {
        Map<String, Object> board = approvalWorkflow.createApprovalBoard(
            "figma-file-123",
            "Employee Form",
            "/templates/employee.html"
        );

        assertEquals("figma-file-123", board.get("figmaFileId"));
        assertEquals("DRAFT", board.get("status"));
        assertTrue(board.containsKey("comments"));
        assertTrue(board.containsKey("approvals"));
    }

    @Test
    void testReviewCommentAddition() {
        Map<String, Object> comment = approvalWorkflow.addReviewComment(
            "board-123",
            "designer@example.com",
            "Layout needs adjustment",
            "MINOR"
        );

        assertEquals("designer@example.com", comment.get("reviewer"));
        assertEquals("MINOR", comment.get("severity"));
        assertEquals("PENDING", comment.get("status"));
    }

    @Test
    void testApprovalDecisionSubmission() {
        Map<String, Object> approval = approvalWorkflow.submitApprovalDecision(
            "board-123",
            "qa@example.com",
            "APPROVED",
            "All tests pass"
        );

        assertEquals("qa@example.com", approval.get("reviewer"));
        assertEquals("APPROVED", approval.get("decision"));
    }

    @Test
    void testRevisionIterationCreation() {
        String revisedHtml = "<div>Revised content</div>";
        List<String> changes = List.of("Fixed layout", "Updated colors");

        Map<String, Object> revision = approvalWorkflow.createRevisionIteration(
            "board-123",
            "developer@example.com",
            revisedHtml,
            changes
        );

        assertEquals("developer@example.com", revision.get("developer"));
        assertEquals("RESUBMITTED_FOR_REVIEW", revision.get("status"));
        assertTrue(revision.containsKey("htmlFingerprint"));
    }

    @Test
    void testApprovalWorkflowCompletion() {
        Map<String, Object> completion = approvalWorkflow.completeApprovalWorkflow(
            "board-123",
            "PRODUCTION"
        );

        assertEquals("APPROVED_FOR_DEPLOYMENT", completion.get("status"));
        assertEquals("PRODUCTION", completion.get("deployTarget"));
    }

    @Test
    void testFigmaCommentThreadCreation() {
        Map<String, Object> thread = approvalWorkflow.createFigmaCommentThread(
            "node-456",
            "designer@example.com",
            "Can you adjust the padding?"
        );

        assertEquals("node-456", thread.get("figmaNodeId"));
        assertFalse((boolean) thread.get("resolved"));
        assertTrue(thread.containsKey("replies"));
    }
}
