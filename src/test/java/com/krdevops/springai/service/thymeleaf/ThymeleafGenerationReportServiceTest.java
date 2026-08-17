package com.krdevops.springai.service.thymeleaf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.contract.SourceRevisionRef;
import com.krdevops.springai.model.contract.ThymeleafGenerationReport;
import com.krdevops.springai.model.thymeleaf.BindingContractStatus;
import com.krdevops.springai.model.thymeleaf.LegacyScreenRole;
import com.krdevops.springai.model.thymeleaf.LegacySourceManifest;
import com.krdevops.springai.model.thymeleaf.ProjectOperationStatus;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingContract;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageStatus;
import com.krdevops.springai.model.thymeleaf.ThymeleafOperationSnapshot;
import com.krdevops.springai.model.thymeleaf.ThymeleafProjectOperation;
import com.krdevops.springai.model.thymeleaf.ThymeleafRouteBinding;
import com.krdevops.springai.service.contract.OperationHashFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ThymeleafGenerationReportServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final OperationHashFactory hashFactory = new OperationHashFactory(objectMapper);
    private final ThymeleafGenerationReportService service = new ThymeleafGenerationReportService(hashFactory);

    @Test
    void previewCreatesNineCompletedStagesAndPendingValidation() {
        ThymeleafProjectOperation operation = operation(ProjectOperationStatus.PREVIEW_READY, List.of());
        String previewHash = hashFactory.canonicalHash(Map.of("preview", "qna-list"));

        ThymeleafGenerationReport report = service.previewReport(
                operation, previewHash, "/project", "design-v1", manifest(), bindingContract(),
                Map.of("templates/qna/list.html", "<html></html>"));

        assertThat(report.stages()).hasSize(10);
        assertThat(report.stages().subList(0, 9))
                .allSatisfy(stage -> {
                    assertThat(stage.status()).isEqualTo(ThymeleafGenerationStageStatus.SUCCEEDED);
                    assertThat(stage.inputHash()).matches("^[a-f0-9]{64}$");
                    assertThat(stage.outputHash()).matches("^[a-f0-9]{64}$");
                });
        assertThat(report.stages().get(9).status()).isEqualTo(ThymeleafGenerationStageStatus.PENDING);
        assertThat(report.finalStatus()).isEqualTo(ProjectOperationStatus.PREVIEW_READY);
        assertThat(report.generatedFiles()).singleElement()
                .satisfies(file -> assertThat(file.relativePath()).isEqualTo("templates/qna/list.html"));
    }

    @Test
    void operationTransitionsKeepReportAndValidationCompletesTenthStage() {
        ThymeleafProjectOperation preview = operation(ProjectOperationStatus.PREVIEW_READY, List.of());
        ThymeleafGenerationReport report = service.previewReport(
                preview, hashFactory.canonicalHash(Map.of("preview", 1)), "/project", "design-v1",
                manifest(), bindingContract(), Map.of("qna.html", "<div></div>"));

        ThymeleafProjectOperation approved = operation(ProjectOperationStatus.APPROVED, List.of());
        report = service.transition(report, ProjectOperationStatus.PREVIEW_READY, approved);
        ThymeleafProjectOperation applied = operation(ProjectOperationStatus.APPLIED, List.of());
        report = service.transition(report, ProjectOperationStatus.APPROVED, applied);
        ThymeleafProjectOperation validated = operation(ProjectOperationStatus.VALIDATED, List.of());
        report = service.transition(report, ProjectOperationStatus.APPLIED, validated);

        assertThat(report.finalStatus()).isEqualTo(ProjectOperationStatus.VALIDATED);
        assertThat(report.stages().get(9).status()).isEqualTo(ThymeleafGenerationStageStatus.SUCCEEDED);
        assertThat(report.stages().get(9).outputHash()).matches("^[a-f0-9]{64}$");
    }

    @Test
    void validationFailureIsPersistedAsFatalTenthStage() {
        ThymeleafProjectOperation applied = operation(ProjectOperationStatus.APPLIED, List.of());
        ThymeleafGenerationReport report = service.previewReport(
                operation(ProjectOperationStatus.PREVIEW_READY, List.of()),
                hashFactory.canonicalHash(Map.of("preview", 2)), "/project", "design-v1",
                manifest(), bindingContract(), Map.of("qna.html", "<div></div>"));
        report = service.transition(report, ProjectOperationStatus.PREVIEW_READY, applied);

        ThymeleafProjectOperation failed = operation(
                ProjectOperationStatus.FAILED, List.of("qna.html: Thymeleaf render 실패"));
        report = service.transition(report, ProjectOperationStatus.APPLIED, failed);

        assertThat(report.finalStatus()).isEqualTo(ProjectOperationStatus.FAILED);
        assertThat(report.stages().get(9).status()).isEqualTo(ThymeleafGenerationStageStatus.FAILED);
        assertThat(report.stages().get(9).issues()).singleElement()
                .satisfies(issue -> assertThat(issue.code()).isEqualTo("THYMELEAF_BUILD_RENDER_PARITY_FAILED"));
    }

    @Test
    void snapshotJsonRoundTripRecoversFullReport() throws Exception {
        ThymeleafProjectOperation operation = operation(ProjectOperationStatus.PREVIEW_READY, List.of());
        String previewHash = hashFactory.canonicalHash(Map.of("preview", 3));
        ThymeleafGenerationReport report = service.previewReport(
                operation, previewHash, "/project", "design-v1", manifest(), bindingContract(),
                Map.of("qna.html", "<div></div>"));
        ThymeleafOperationSnapshot snapshot = new ThymeleafOperationSnapshot(
                1, operation, "/project", Map.of("qna.html", "MISSING"), "design-v1", previewHash,
                manifest(), bindingContract(), report);

        ThymeleafOperationSnapshot restored = objectMapper.readValue(
                objectMapper.writeValueAsBytes(snapshot), ThymeleafOperationSnapshot.class);

        assertThat(restored.generationReport()).isEqualTo(report);
        assertThat(restored.generationReport().stages()).hasSize(10);
    }

    private ThymeleafProjectOperation operation(ProjectOperationStatus status, List<String> errors) {
        return new ThymeleafProjectOperation(
                "operation-r6-061", "/project", status, Map.of("qna.html", "<div></div>"),
                List.of("qna.html"), null, List.of(), errors,
                status == ProjectOperationStatus.APPROVED || status.isApplied(),
                LocalDateTime.of(2026, 8, 17, 0, 0),
                status.isApplied() ? LocalDateTime.of(2026, 8, 17, 0, 1) : null);
    }

    private LegacySourceManifest manifest() {
        String hash = hashFactory.sha256Hex("jsp".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new LegacySourceManifest(
                List.of(new LegacySourceManifest.SourceFile("qna.jsp", hash)), hash);
    }

    private ThymeleafBindingContract bindingContract() {
        return new ThymeleafBindingContract(
                "qna-list", LegacyScreenRole.LIST,
                new ThymeleafRouteBinding("/qna", "GET", "list", null, null,
                        false, false, List.of()),
                List.of(), List.of(), null, List.of(), null, List.of(), List.of(),
                BindingContractStatus.RESOLVED, List.of(),
                new SourceRevisionRef("qna-list", "revision-1", Instant.parse("2026-08-17T00:00:00Z")),
                Instant.parse("2026-08-17T00:00:00Z"));
    }
}
