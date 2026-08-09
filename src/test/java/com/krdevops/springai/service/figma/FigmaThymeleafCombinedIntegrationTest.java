package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.LegacyRepositoryDdlProperties;
import com.krdevops.springai.controller.FigmaOperationsController;
import com.krdevops.springai.controller.ThymeleafOperationsController;
import com.krdevops.springai.mapper.FigmaDesignOperationRepository;
import com.krdevops.springai.model.contract.ArtifactRef;
import com.krdevops.springai.model.contract.SourceRevisionRef;
import com.krdevops.springai.model.figma.contract.FigmaDesignOperation;
import com.krdevops.springai.model.figma.contract.FigmaDesignOperationStatus;
import com.krdevops.springai.model.figma.contract.FigmaDesignRequest;
import com.krdevops.springai.model.thymeleaf.ProjectOperationStatus;
import com.krdevops.springai.service.FigmaApiClient;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.thymeleaf.ProjectOperationStateService;
import com.krdevops.springai.service.thymeleaf.ThymeleafProjectWorkflowService;
import com.krdevops.springai.service.thymeleaf.ThymeleafToolAuthorizationService;
import com.krdevops.springai.service.thymeleaf.ValidationGateExecutor;
import com.krdevops.springai.tools.ThymeleafProjectWorkflowTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

/**
 * ARCH-0819/ARCH-0821: 승인된 Figma Operation과 Thymeleaf 프로젝트 Operation이 같은 증적(content
 * hash)을 공유한 채 각자 종료 상태까지 도달하는지 검증한다.
 *
 * <p>Figma 원장은 실제 MySQL(`egov-mysql`)에, Thymeleaf 워크플로우는 인메모리로 구동한다.
 * 실제 Figma REST 접근은 필요 없다 — 이 흐름의 요청은 editableNodeIds가 없어 scope 재검증이
 * 호출되지 않으며, 그 사실 자체를 대역 클라이언트 무호출로 확인한다.
 *
 * <p>실 DB를 요구하므로 이름은 `*IntegrationTest` 관례를 따르고 위치도 `service/**` 아래에 둔다 —
 * `build.gradle`의 `-Pci` 제외 패턴(`**&#47;service&#47;**&#47;*IntegrationTest.class`)에 걸려야 CI가 깨지지 않는다.
 */
class FigmaThymeleafCombinedIntegrationTest {

    private static final String TEMPLATE_PATH = "src/main/resources/templates/users/list.html";
    private static final String TEMPLATE_HTML =
            "<main><table><tr th:each=\"user : ${users}\"><td th:text=\"${user.name}\"></td></tr></table></main>";

    @TempDir Path projectRoot;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final OperationHashFactory hashFactory = new OperationHashFactory(objectMapper);
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:mysql://localhost:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
            System.getenv().getOrDefault("DB_USERNAME", "ebt"),
            System.getenv().getOrDefault("DB_PASSWORD", "ebt01")));
    private final FigmaDesignOperationRepository figmaRepository = new FigmaDesignOperationRepository(
            jdbcTemplate, objectMapper, hashFactory, new FigmaDesignOperationStateService(),
            new LegacyRepositoryDdlProperties());
    private final FigmaApiClient figmaApiClient = mock(FigmaApiClient.class);
    private final FigmaDesignOperationService figmaService = new FigmaDesignOperationService(
            figmaRepository, new FigmaDesignOperationStateService(), figmaApiClient);

    private ThymeleafOperationsController rest;
    private ThymeleafProjectWorkflowTool mcp;

    @BeforeEach
    void wire() {
        figmaRepository.createTableIfNotExists();
        var workflow = new ThymeleafProjectWorkflowService(
                new ProjectOperationStateService(), new ValidationGateExecutor(), hashFactory);
        rest = new ThymeleafOperationsController(workflow, null);
        mcp = new ThymeleafProjectWorkflowTool(
                new ThymeleafToolAuthorizationService("shared"), workflow, objectMapper);
    }

    @Test
    void approvedFigmaDesignAndThymeleafProjectShareTheSameEvidence() throws Exception {
        String screenId = "user-list-" + UUID.randomUUID();
        String designHash = hashFactory.sha256Hex(TEMPLATE_HTML.getBytes(StandardCharsets.UTF_8));
        FigmaDesignRequest request = FigmaDesignRequest.textDescription(
                "사용자 목록 화면 " + screenId, "test-file-key");
        FigmaDesignOperation created = figmaRepository.createOrReuse(request, screenId);

        try {
            // Figma: 승인된 화면명세로 만든 export bundle 증적을 달고 PREVIEW_READY까지 진행한다.
            SourceRevisionRef sourceRevision = new SourceRevisionRef(screenId, "1:" + designHash, Instant.now());
            ArtifactRef bundle = new ArtifactRef(
                    screenId + "-bundle", "FIGMA_EXPORT_BUNDLE",
                    "figma-bundles/" + screenId + "/1", designHash, Instant.now());
            FigmaDesignOperation previewReady = figmaRepository.appendTransition(
                    created.operationId(), FigmaDesignOperationStatus.PREVIEW_READY,
                    sourceRevision, List.of(), List.of(bundle));
            assertThat(previewReady.status()).isEqualTo(FigmaDesignOperationStatus.PREVIEW_READY);

            // ARCH-0821: Plugin이 Apply를 요구하기 전에는 APPLIED로 갈 수 없다.
            assertThatThrownBy(() -> figmaService.reportApplied(created.operationId(), pluginReport(screenId)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FIGMA_OPERATION_INVALID_TRANSITION");

            figmaRepository.appendTransition(created.operationId(), FigmaDesignOperationStatus.APPLY_REQUIRED,
                    sourceRevision, List.of(), List.of(bundle));

            // Thymeleaf: 같은 화면을 preview → approve → apply → revalidate로 VALIDATED까지 만든다.
            var preview = rest.preview(new ThymeleafOperationsController.PreviewRequest(
                    projectRoot.toString(), Map.of(TEMPLATE_PATH, TEMPLATE_HTML)));
            String thymeleafOperationId = preview.operation().operationId();
            mcp.approveThymeleafProject("shared", thymeleafOperationId, preview.previewHash());
            mcp.applyThymeleafProject("shared", thymeleafOperationId);
            mcp.revalidateThymeleafProject("shared", thymeleafOperationId);

            // Figma: Plugin 보고서가 도착하면 같은 증적을 유지한 채 APPLIED로 종료한다.
            FigmaDesignOperation applied = figmaService.reportApplied(
                    created.operationId(), pluginReport(screenId));

            assertThat(applied.status()).isEqualTo(FigmaDesignOperationStatus.APPLIED);
            assertThat(rest.report(thymeleafOperationId).getBody().operation().status())
                    .isEqualTo(ProjectOperationStatus.VALIDATED);

            // 결합 증적: 실제로 디스크에 쓰인 템플릿의 hash가 Figma Operation이 들고 있는 bundle
            // contentHash와 같아야 한다. 어느 한쪽 내용이 달라지면 이 단정이 깨진다.
            byte[] written = Files.readAllBytes(projectRoot.resolve(TEMPLATE_PATH));
            assertThat(hashFactory.sha256Hex(written)).isEqualTo(designHash);
            assertThat(applied.artifacts()).singleElement().satisfies(artifact -> {
                assertThat(artifact.artifactType()).isEqualTo("FIGMA_EXPORT_BUNDLE");
                assertThat(artifact.contentHash()).isEqualTo(designHash);
            });
            assertThat(applied.sourceRevision().revisionToken()).isEqualTo("1:" + designHash);

            // editableNodeIds가 없는 요청이므로 실제 Figma 접근은 한 번도 일어나지 않아야 한다.
            assertThat(mockingDetails(figmaApiClient).getInvocations()).isEmpty();
        } finally {
            cleanup(created);
        }
    }

    private FigmaOperationsController.PluginApplyReport pluginReport(String screenId) {
        return new FigmaOperationsController.PluginApplyReport(
                screenId, List.of(), 0, 1, 0, "Thymeleaf 화면과 동일 증적으로 적용 완료");
    }

    private void cleanup(FigmaDesignOperation created) {
        jdbcTemplate.update(
                "DELETE FROM AI_FIGMA_DESIGN_OPERATION WHERE OPERATION_ID = ?", created.operationId());
        jdbcTemplate.update(
                "DELETE FROM AI_FIGMA_DESIGN_OPERATION_IDEMPOTENCY WHERE REQUEST_HASH = ?", created.requestHash());
    }
}
