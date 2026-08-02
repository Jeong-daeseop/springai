package com.krdevops.springai.service.thymeleaf.mcp;

import com.krdevops.springai.model.thymeleaf.LegacyConversionRequest;
import com.krdevops.springai.model.thymeleaf.LegacyScreenAnalysis;
import com.krdevops.springai.model.thymeleaf.LegacyScreenRole;
import com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperation;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult;
import com.krdevops.springai.service.thymeleaf.LegacyScreenAnalysisAssemblerService;
import com.krdevops.springai.service.thymeleaf.ProjectJspScanner;
import com.krdevops.springai.service.thymeleaf.ThymeleafConversionOrchestrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * R6-064: Tool → MCP Facade 패턴.
 * {@link com.krdevops.springai.tools.ThymeleafConversionTool}의 원시 파라미터를 도메인 객체로
 * 변환하고, 결과를 JSON 문자열로 직렬화한다.
 *
 * <p>기존 {@link com.krdevops.springai.service.generation.mcp.ThymeleafLayoutMcpFacade}와
 * {@link com.krdevops.springai.service.figma.FigmaMcpFacadeService}의 패턴을 따른다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThymeleafConversionMcpFacade {

    private final LegacyScreenAnalysisAssemblerService analysisAssembler;
    private final ThymeleafConversionOrchestrationService orchestrationService;
    private final ProjectJspScanner jspScanner;
    private final ObjectMapper objectMapper;

    /**
     * 프로젝트 내 JSP 파일 목록 스캔.
     * 분석 전 화면 발견 및 대상 선정에 사용.
     */
    public String scanLegacyJspFiles(
            String projectRootPath,
            @Nullable String globPattern,
            @Nullable List<String> excludePatterns) {
        log.info("Scanning JSP files: projectRoot={}, pattern={}", projectRootPath, globPattern);

        String effectivePattern = globPattern != null ? globPattern : "**/WEB-INF/jsp/**/*.jsp";
        List<String> effectiveExcludes = excludePatterns != null ? excludePatterns : List.of();

        Path projectRoot = Path.of(projectRootPath);
        List<ProjectJspScanner.ScannedJspFile> scanned = jspScanner.scanJspFiles(
                projectRoot, effectivePattern, effectiveExcludes);

        log.info("Scan complete: found {} JSP files", scanned.size());
        return serializeObject(new ScanResult(scanned.size(), scanned));
    }

    /**
     * 단일 화면 분석 + 렌더링 + Preview까지 진행.
     * 승인 전에는 파일을 건드리지 않음.
     */
    public String analyzeAndPreviewLegacyScreen(
            String projectRootPath,
            String screenId,
            String screenRole,
            String jspRelativePath,
            String controllerRelativePath,
            String voRelativePath,
            String pageTitle,
            String targetRelativePath) {
        log.info("Analyzing screen: screenId={}, role={}, jsp={}", screenId, screenRole, jspRelativePath);

        LegacyConversionRequest request = new LegacyConversionRequest(
                "req-" + UUID.randomUUID(),
                projectRootPath,
                screenId,
                LegacyScreenRole.valueOf(screenRole.toUpperCase()),
                jspRelativePath,
                controllerRelativePath,
                voRelativePath,
                Instant.now()
        );

        LegacyScreenAnalysis analysis = analysisAssembler.analyze(request);
        log.debug("Analysis done: {} forms, {} fields", analysis.jsp().forms().size(), analysis.jsp().displayFields().size());

        ThymeleafGenerationStageResult<ThymeleafConversionOperation> result =
                orchestrationService.analyzeAndPreview(analysis, pageTitle, targetRelativePath);

        if (result.successful()) {
            log.info("Preview ready: operationId={}", result.value().operationId());
            return serializeOperation(result.value());
        } else {
            log.warn("Preview failed: {} issues", result.issues().size());
            return serializeFailure(result);
        }
    }

    /**
     * 미리보기 승인. 파일은 건드리지 않음.
     */
    public String approveThymeleafConversion(String operationId) {
        log.info("Approving operation: {}", operationId);
        ThymeleafConversionOperation approved = orchestrationService.approve(operationId);
        log.info("Operation approved: {}", operationId);
        return serializeOperation(approved);
    }

    /**
     * 승인된 작업을 실제 프로젝트에 적용.
     * Apply 직전에 소스를 다시 읽어 충돌 검증.
     */
    public String applyThymeleafConversion(
            String operationId,
            String projectRootPath,
            String screenId,
            String screenRole,
            String jspRelativePath,
            String controllerRelativePath,
            String voRelativePath) {
        log.info("Applying operation: operationId={}, screenId={}", operationId, screenId);

        // Apply 직전 현재 소스를 다시 읽어 revision 충돌 검증
        LegacyConversionRequest currentRequest = new LegacyConversionRequest(
                "req-apply-" + UUID.randomUUID(),
                projectRootPath,
                screenId,
                LegacyScreenRole.valueOf(screenRole.toUpperCase()),
                jspRelativePath,
                controllerRelativePath,
                voRelativePath,
                Instant.now()
        );

        LegacyScreenAnalysis currentAnalysis = analysisAssembler.analyze(currentRequest);
        Path projectRoot = Path.of(projectRootPath);

        ThymeleafGenerationStageResult<ThymeleafConversionOperation> result =
                orchestrationService.apply(operationId, projectRoot, currentAnalysis);

        if (result.successful()) {
            log.info("Applied successfully: {} artifacts", result.value().artifacts().size());
            return serializeOperation(result.value());
        } else {
            log.warn("Apply failed: {} issues", result.issues().size());
            return serializeFailure(result);
        }
    }

    // ===== Private Methods =====

    private String serializeOperation(ThymeleafConversionOperation operation) {
        try {
            return objectMapper.writeValueAsString(operation);
        } catch (Exception e) {
            log.error("Failed to serialize ThymeleafConversionOperation", e);
            return "{}";
        }
    }

    private String serializeFailure(ThymeleafGenerationStageResult<?> result) {
        try {
            return objectMapper.writeValueAsString(new FailureResponse(result.issues()));
        } catch (Exception e) {
            log.error("Failed to serialize failure response", e);
            return "{}";
        }
    }

    private String serializeObject(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to serialize object", e);
            return "{}";
        }
    }

    // ===== Response DTOs =====

    record ScanResult(int count, List<ProjectJspScanner.ScannedJspFile> files) {}

    record FailureResponse(java.util.List<com.krdevops.springai.model.contract.GenerationIssue> issues) {}
}
