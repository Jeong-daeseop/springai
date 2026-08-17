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

    /**
     * R6-T20: 동일 입력으로 previewReport()를 두 번 호출해도 1~9단계의 inputHash/outputHash
     * 체인이 바이트 단위로 완전히 같아야 한다 — 재실행 결정성. startedAt/completedAt은
     * Instant.now() 기반이라 실행마다 다른 게 정상이므로 해시만 비교한다.
     */
    @Test
    void previewReportIsDeterministicAcrossRepeatedCallsWithIdenticalInput() {
        ThymeleafProjectOperation operation = operation(ProjectOperationStatus.PREVIEW_READY, List.of());
        String previewHash = hashFactory.canonicalHash(Map.of("preview", "determinism"));
        LegacySourceManifest manifest = manifest();
        ThymeleafBindingContract contract = bindingContract();
        Map<String, String> files = Map.of("templates/qna/list.html", "<html></html>");

        ThymeleafGenerationReport first = service.previewReport(
                operation, previewHash, "/project", "design-v1", manifest, contract, files);
        ThymeleafGenerationReport second = service.previewReport(
                operation, previewHash, "/project", "design-v1", manifest, contract, files);

        assertThat(first.stages()).hasSize(10);
        assertThat(second.stages()).hasSize(10);
        for (int i = 0; i < 9; i++) {
            assertThat(second.stages().get(i).inputHash())
                    .as("stage %d inputHash", i).isEqualTo(first.stages().get(i).inputHash());
            assertThat(second.stages().get(i).outputHash())
                    .as("stage %d outputHash", i).isEqualTo(first.stages().get(i).outputHash());
        }
        assertThat(second.requestHash()).isEqualTo(first.requestHash());
        assertThat(second.projectFingerprint()).isEqualTo(first.projectFingerprint());
        assertThat(second.generatedFiles()).isEqualTo(first.generatedFiles());
    }

    /**
     * R6-T20: 입력이 하나라도 달라지면(생성 파일 내용 변경) 그 이후 체인 해시도 달라져야 한다 —
     * 결정성 테스트의 반대 극단(다른 입력은 다른 해시를 내야 "우연히 항상 같은 값"이 아님을 보인다).
     */
    @Test
    void previewReportProducesDifferentHashesWhenGeneratedFileContentChanges() {
        ThymeleafProjectOperation operation = operation(ProjectOperationStatus.PREVIEW_READY, List.of());
        String previewHash = hashFactory.canonicalHash(Map.of("preview", "determinism-diff"));
        LegacySourceManifest manifest = manifest();
        ThymeleafBindingContract contract = bindingContract();

        ThymeleafGenerationReport first = service.previewReport(
                operation, previewHash, "/project", "design-v1", manifest, contract,
                Map.of("templates/qna/list.html", "<html>v1</html>"));
        ThymeleafGenerationReport second = service.previewReport(
                operation, previewHash, "/project", "design-v1", manifest, contract,
                Map.of("templates/qna/list.html", "<html>v2</html>"));

        boolean anyStageHashDiffers = false;
        for (int i = 0; i < 9; i++) {
            if (!second.stages().get(i).outputHash().equals(first.stages().get(i).outputHash())) {
                anyStageHashDiffers = true;
                break;
            }
        }
        assertThat(anyStageHashDiffers).as("생성 파일 내용이 다르면 최소 한 단계의 outputHash는 달라야 한다").isTrue();
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
