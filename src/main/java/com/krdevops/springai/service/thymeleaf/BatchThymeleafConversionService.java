package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.contract.SourceRevisionRef;
import com.krdevops.springai.model.thymeleaf.BatchConversionRequest;
import com.krdevops.springai.model.thymeleaf.BatchConversionResult;
import com.krdevops.springai.model.thymeleaf.LegacyScreenAnalysis;
import com.krdevops.springai.model.thymeleaf.LegacyScreenRole;
import com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperation;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * I-6: 여러 JSP를 일괄 변환하는 배치 오케스트레이션 서비스.
 * 프로젝트 스캔 → 병렬/순차 처리 → 결과 집계.
 */
@Service
public class BatchThymeleafConversionService {

    private final ProjectJspScanner jspScanner;
    private final JspSourceReader jspReader;
    private final ControllerSourceReader controllerReader;
    private final VoSourceReader voReader;
    private final ThymeleafConversionOrchestrationService orchestrationService;

    public BatchThymeleafConversionService(
            ProjectJspScanner jspScanner,
            JspSourceReader jspReader,
            ControllerSourceReader controllerReader,
            VoSourceReader voReader,
            ThymeleafConversionOrchestrationService orchestrationService
    ) {
        this.jspScanner = jspScanner;
        this.jspReader = jspReader;
        this.controllerReader = controllerReader;
        this.voReader = voReader;
        this.orchestrationService = orchestrationService;
    }

    public BatchConversionResult convertBatch(BatchConversionRequest request) {
        Instant startedAt = Instant.now();

        List<ProjectJspScanner.ScannedJspFile> scannedFiles = jspScanner.scanJspFiles(
                request.projectRoot(), request.jspPattern(), request.excludePatterns());

        List<BatchConversionResult.ConversionItemResult> itemResults = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        if (request.parallelExecution()) {
            processParallel(request, scannedFiles, itemResults, successCount, failureCount);
        } else {
            processSequential(request, scannedFiles, itemResults, successCount, failureCount);
        }

        Instant completedAt = Instant.now();
        int skippedCount = scannedFiles.size() - successCount.get() - failureCount.get();

        return new BatchConversionResult(
                request.batchId(),
                scannedFiles.size(),
                successCount.get(),
                failureCount.get(),
                skippedCount,
                itemResults,
                startedAt,
                completedAt
        );
    }

    private void processSequential(
            BatchConversionRequest request,
            List<ProjectJspScanner.ScannedJspFile> scannedFiles,
            List<BatchConversionResult.ConversionItemResult> itemResults,
            AtomicInteger successCount,
            AtomicInteger failureCount) {
        for (ProjectJspScanner.ScannedJspFile scanned : scannedFiles) {
            long startTime = System.currentTimeMillis();
            try {
                String targetPath = inferTargetPath(request.outputBaseDirectory(), scanned.jspRelativePath());
                LegacyScreenRole screenRole = inferScreenRole(scanned.jspRelativePath());
                String screenId = "batch-" + request.batchId() + "-" + scanned.jspRelativePath().hashCode();

                LegacyScreenAnalysis analysis = analyzeScreen(
                        request.projectRoot(), screenId, screenRole, scanned);

                ThymeleafGenerationStageResult<ThymeleafConversionOperation> result =
                        orchestrationService.analyzeAndPreview(analysis, inferPageTitle(scanned), targetPath);

                if (result.successful()) {
                    long durationMs = System.currentTimeMillis() - startTime;
                    itemResults.add(new BatchConversionResult.ConversionItemResult(
                            scanned.jspRelativePath(),
                            BatchConversionResult.ConversionItemResult.Status.SUCCESS.name(),
                            targetPath,
                            null,
                            durationMs
                    ));
                    successCount.incrementAndGet();
                } else {
                    long durationMs = System.currentTimeMillis() - startTime;
                    itemResults.add(new BatchConversionResult.ConversionItemResult(
                            scanned.jspRelativePath(),
                            BatchConversionResult.ConversionItemResult.Status.FAILED.name(),
                            targetPath,
                            String.join("; ", result.issues().stream()
                                    .map(i -> i.code() + ": " + i.message())
                                    .toList()),
                            durationMs
                    ));
                    failureCount.incrementAndGet();
                }
            } catch (Exception exception) {
                long durationMs = System.currentTimeMillis() - startTime;
                itemResults.add(new BatchConversionResult.ConversionItemResult(
                        scanned.jspRelativePath(),
                        BatchConversionResult.ConversionItemResult.Status.FAILED.name(),
                        null,
                        exception.getMessage(),
                        durationMs
                ));
                failureCount.incrementAndGet();
            }
        }
    }

    private void processParallel(
            BatchConversionRequest request,
            List<ProjectJspScanner.ScannedJspFile> scannedFiles,
            List<BatchConversionResult.ConversionItemResult> itemResults,
            AtomicInteger successCount,
            AtomicInteger failureCount) {
        ExecutorService executor = Executors.newFixedThreadPool(request.maxConcurrency());
        try {
            for (ProjectJspScanner.ScannedJspFile scanned : scannedFiles) {
                executor.submit(() -> {
                    long startTime = System.currentTimeMillis();
                    try {
                        String targetPath = inferTargetPath(request.outputBaseDirectory(), scanned.jspRelativePath());
                        LegacyScreenRole screenRole = inferScreenRole(scanned.jspRelativePath());
                        String screenId = "batch-" + request.batchId() + "-" + scanned.jspRelativePath().hashCode();

                        LegacyScreenAnalysis analysis = analyzeScreen(
                                request.projectRoot(), screenId, screenRole, scanned);

                        ThymeleafGenerationStageResult<ThymeleafConversionOperation> result =
                                orchestrationService.analyzeAndPreview(analysis, inferPageTitle(scanned), targetPath);

                        if (result.successful()) {
                            long durationMs = System.currentTimeMillis() - startTime;
                            synchronized (itemResults) {
                                itemResults.add(new BatchConversionResult.ConversionItemResult(
                                        scanned.jspRelativePath(),
                                        BatchConversionResult.ConversionItemResult.Status.SUCCESS.name(),
                                        targetPath,
                                        null,
                                        durationMs
                                ));
                            }
                            successCount.incrementAndGet();
                        } else {
                            long durationMs = System.currentTimeMillis() - startTime;
                            synchronized (itemResults) {
                                itemResults.add(new BatchConversionResult.ConversionItemResult(
                                        scanned.jspRelativePath(),
                                        BatchConversionResult.ConversionItemResult.Status.FAILED.name(),
                                        targetPath,
                                        String.join("; ", result.issues().stream()
                                                .map(i -> i.code() + ": " + i.message())
                                                .toList()),
                                        durationMs
                                ));
                            }
                            failureCount.incrementAndGet();
                        }
                    } catch (Exception exception) {
                        long durationMs = System.currentTimeMillis() - startTime;
                        synchronized (itemResults) {
                            itemResults.add(new BatchConversionResult.ConversionItemResult(
                                    scanned.jspRelativePath(),
                                    BatchConversionResult.ConversionItemResult.Status.FAILED.name(),
                                    null,
                                    exception.getMessage(),
                                    durationMs
                            ));
                        }
                        failureCount.incrementAndGet();
                    }
                });
            }
            executor.shutdown();
            if (!executor.awaitTermination(30, TimeUnit.MINUTES)) {
                executor.shutdownNow();
                throw new IllegalStateException("배치 변환 타임아웃");
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("배치 변환 중단됨", exception);
        }
    }

    private LegacyScreenAnalysis analyzeScreen(
            Path projectRoot,
            String screenId,
            LegacyScreenRole screenRole,
            ProjectJspScanner.ScannedJspFile scanned) throws IOException {
        String jspContent = Files.readString(scanned.jspAbsolutePath());
        var jspEvidence = jspReader.read(scanned.jspRelativePath(), jspContent);

        var controllerEvidence = (scanned.inferredControllerPath() != null)
                ? controllerReader.read(
                        scanned.inferredControllerPath(),
                        Files.readString(projectRoot.resolve(scanned.inferredControllerPath())))
                : null;

        var voEvidence = (scanned.inferredVoPath() != null)
                ? voReader.read(
                        scanned.inferredVoPath(),
                        Files.readString(projectRoot.resolve(scanned.inferredVoPath())))
                : null;

        return new LegacyScreenAnalysis(
                screenId, screenRole, jspEvidence, controllerEvidence, voEvidence,
                new SourceRevisionRef("batch-" + projectRoot.getFileName(),
                        Integer.toHexString(jspContent.hashCode()), Instant.now()),
                List.of(), Instant.now());
    }

    private LegacyScreenRole inferScreenRole(String jspRelativePath) {
        String lower = jspRelativePath.toLowerCase();
        if (lower.contains("list")) return LegacyScreenRole.LIST;
        if (lower.contains("regist") || lower.contains("create")) return LegacyScreenRole.FORM;
        if (lower.contains("detail")) return LegacyScreenRole.DETAIL;
        return LegacyScreenRole.LIST;
    }

    private String inferTargetPath(String outputBase, String jspRelativePath) {
        return outputBase + "/" + jspRelativePath.replace(".jsp", ".html");
    }

    private String inferPageTitle(ProjectJspScanner.ScannedJspFile scanned) {
        String name = scanned.jspRelativePath()
                .replaceAll("^.*/", "")
                .replaceAll("\\.jsp$", "")
                .replaceAll("^Egov", "");
        return name + " 화면";
    }
}
