package com.krdevops.springai.service.thymeleaf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.ArtifactStoreProperties;
import com.krdevops.springai.model.thymeleaf.BaselineApprovalRequest;
import com.krdevops.springai.model.thymeleaf.ProjectOperationStatus;
import com.krdevops.springai.model.thymeleaf.ThymeleafOperationSnapshot;
import com.krdevops.springai.model.thymeleaf.ThymeleafProjectOperation;
import com.krdevops.springai.service.artifact.ArtifactService;
import com.krdevops.springai.service.artifact.FilesystemArtifactStore;
import com.krdevops.springai.service.artifact.RecordingArtifactCatalog;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.write.SafePathResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ARCH-0810: 명시적 baseline 승인만이 시각 비교 기준을 만든다는 계약 검증. */
class BaselineApprovalServiceTest {

    private static final String RELATIVE = "src/main/resources/templates/users/list.html";
    private static final String SCREEN_ID = "users-list";

    @TempDir Path projectRoot;
    @TempDir Path baselineRoot;
    @TempDir Path artifactStoreRoot;
    @TempDir Path runnerDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final OperationHashFactory hashFactory = new OperationHashFactory(new ObjectMapper());

    private ThymeleafOperationStore store;
    private RecordingArtifactCatalog catalog;
    private ArtifactService artifactService;
    private BrowserGateDirectoryResolver resolver;
    private String operationId;

    @BeforeEach
    void setUp() {
        store = new InMemoryThymeleafOperationStore();
        catalog = new RecordingArtifactCatalog();
        ArtifactStoreProperties properties = new ArtifactStoreProperties();
        properties.setRootPath(artifactStoreRoot);
        artifactService = new ArtifactService(new FilesystemArtifactStore(properties), catalog);
        resolver = new BrowserGateDirectoryResolver(new SafePathResolver(), baselineRoot);
        operationId = storedOperation(ProjectOperationStatus.APPLIED);
    }

    @Test
    void approvalPromotesEveryViewportAndRecordsTwoArtifactsEach() {
        var result = service(runner("v1", null)).approve(request(List.of()));

        assertThat(result.viewports()).hasSize(3);
        assertThat(result.viewports()).allSatisfy(viewport -> {
            assertThat(viewport.previousContentHash()).isNull();
            assertThat(viewport.baselineArtifactId()).isNotBlank();
            assertThat(viewport.approvalArtifactId()).isNotBlank();
        });
        for (String viewport : List.of("desktop", "tablet", "mobile")) {
            assertThat(resolver.baselineDirectory(projectRoot).resolve(SCREEN_ID + "-" + viewport + ".png"))
                    .exists();
        }
        assertThat(catalog.ofType("THYMELEAF_VISUAL_BASELINE")).hasSize(3);
        assertThat(catalog.ofType("THYMELEAF_BASELINE_APPROVAL")).hasSize(3);
    }

    @Test
    void approvalMetadataCarriesProjectKeyAndActor() throws Exception {
        service(runner("v1", null)).approve(request(List.of("desktop")));

        var approval = catalog.ofType("THYMELEAF_BASELINE_APPROVAL").get(0);
        Map<?, ?> metadata = objectMapper.readValue(
                artifactService.read(approval).orElseThrow(), Map.class);

        assertThat(metadata.get("schemaVersion")).isEqualTo("thymeleaf-baseline-approval-v1");
        assertThat(metadata.get("operationId")).isEqualTo(operationId);
        assertThat(metadata.get("viewport")).isEqualTo("desktop");
        assertThat(metadata.get("projectRootKey")).isEqualTo(resolver.projectKey(projectRoot));
        assertThat(metadata.get("previousContentHash")).isNull();
        assertThat(metadata.get("actor")).isEqualTo("system");
    }

    @Test
    void singleFailedViewportRejectsWholeApprovalWithoutWritingAnyBaseline() {
        assertThatThrownBy(() -> service(runner("v1", "mobile")).approve(request(List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("THYMELEAF_BASELINE_CANDIDATE_RENDER_FAILED");

        assertThat(resolver.baselineDirectory(projectRoot)).doesNotExist();
        assertThat(catalog.findAll()).isEmpty();
    }

    @Test
    void reapprovalReportsPreviousContentHashAndReplacesBaseline() throws Exception {
        var first = service(runner("v1", null)).approve(request(List.of("desktop")));
        var second = service(runner("v2", null)).approve(request(List.of("desktop")));

        assertThat(second.viewports().get(0).previousContentHash())
                .isEqualTo(first.viewports().get(0).newContentHash());
        assertThat(second.viewports().get(0).newContentHash())
                .isNotEqualTo(first.viewports().get(0).newContentHash());
        assertThat(Files.readString(resolver.baselineDirectory(projectRoot)
                .resolve(SCREEN_ID + "-desktop.png"))).contains("v2");
    }

    @Test
    void approvalIsRejectedBeforeApply() {
        String pending = storedOperation(ProjectOperationStatus.PREVIEW_READY);

        assertThatThrownBy(() -> service(runner("v1", null)).approve(new BaselineApprovalRequest(
                pending, SCREEN_ID, RELATIVE, null, "<html></html>", List.of(), List.of(), null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("THYMELEAF_BASELINE_APPROVAL_REQUIRES_APPLIED");
        assertThat(resolver.baselineDirectory(projectRoot)).doesNotExist();
    }

    @Test
    void unknownRelativeFileIsRejected() {
        assertThatThrownBy(() -> service(runner("v1", null)).approve(new BaselineApprovalRequest(
                operationId, SCREEN_ID, "templates/other.html", null, "<html></html>",
                List.of(), List.of(), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("THYMELEAF_BASELINE_FILE_NOT_IN_OPERATION");
    }

    @Test
    void unknownOperationIsRejected() {
        assertThatThrownBy(() -> service(runner("v1", null)).approve(new BaselineApprovalRequest(
                "missing-operation", SCREEN_ID, null, null, "<html></html>", List.of(), List.of(), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("THYMELEAF_OPERATION_NOT_FOUND");
    }

    private BaselineApprovalService service(BrowserValidationGateExecutor gate) {
        return new BaselineApprovalService(store, gate, resolver, hashFactory, objectMapper, artifactService);
    }

    private BaselineApprovalRequest request(List<String> viewports) {
        return new BaselineApprovalRequest(operationId, SCREEN_ID, RELATIVE, null,
                "<!doctype html><html><body><main>list</main></body></html>", viewports, List.of(), null);
    }

    private String storedOperation(ProjectOperationStatus status) {
        String id = "operation-" + status.name().toLowerCase() + "-" + System.nanoTime();
        var operation = new ThymeleafProjectOperation(id, projectRoot.toString(), status,
                Map.of(RELATIVE, "<main>list</main>"), List.of(RELATIVE), null, List.of(), List.of(),
                true, LocalDateTime.now(), LocalDateTime.now());
        store.createOrReuse(new ThymeleafOperationSnapshot(
                1, operation, projectRoot.toString(), Map.of(), "design-rev-1", "preview-hash-" + id));
        return id;
    }

    /**
     * 실제 Chromium 대신 요청 JSON을 읽어 스크린샷 파일을 만들고 report를 출력하는 대역이다 —
     * 승인 로직이 "서버가 방금 렌더한 파일만" 승격하는지 확인하려면 파일이 실제로 있어야 한다.
     */
    private BrowserValidationGateExecutor runner(String contentMarker, String failingViewport) {
        try {
            Path script = Files.createTempFile(runnerDirectory, "fake-browser-gate-", ".mjs");
            Files.writeString(script, """
                    import fs from 'node:fs';
                    import path from 'node:path';
                    const requestPath = process.argv[process.argv.indexOf('--request') + 1];
                    const request = JSON.parse(fs.readFileSync(requestPath, 'utf8'));
                    fs.mkdirSync(request.artifactDirectory, { recursive: true });
                    const failing = %s;
                    const viewports = [['desktop',1440,1200],['tablet',768,1024],['mobile',390,844]]
                      .map(([name, width, height]) => {
                        const screenshotPath = path.join(request.artifactDirectory, `${request.screenId}-${name}.png`);
                        fs.writeFileSync(screenshotPath, `fake-png-${request.screenId}-${name}-%s`);
                        const failed = failing === name;
                        return { name, width, height,
                          renderStatus: failed ? 'FAILED' : 'PASSED',
                          accessibilityStatus: 'PASSED', visualStatus: 'BASELINE_MISSING',
                          renderIssues: failed ? ['렌더 실패'] : [], accessibilityIssues: [], violations: [],
                          screenshotPath: failed ? null : screenshotPath,
                          baselinePath: null, diffPath: null, differenceRatio: null,
                          visualIssues: ['기준 이미지가 없습니다.'], durationMs: 1 };
                      });
                    process.stdout.write(JSON.stringify({
                      schemaVersion: 'browser-validation-report-v1', screenId: request.screenId,
                      blocked: true, createdAt: '2026-08-07T00:00:00Z',
                      browser: { engine: 'fake', version: '0' }, reportPath: null, viewports }));
                    """.formatted(failingViewport == null ? "null" : "'" + failingViewport + "'", contentMarker));
            return new BrowserValidationGateExecutor(objectMapper, "node", script, Duration.ofSeconds(20));
        } catch (Exception exception) {
            throw new IllegalStateException("대역 runner 준비 실패", exception);
        }
    }
}
