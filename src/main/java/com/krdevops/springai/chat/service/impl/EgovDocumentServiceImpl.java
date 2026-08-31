package com.krdevops.springai.chat.service.impl;

import com.krdevops.springai.chat.response.DocumentStatusResponse;
import com.krdevops.springai.chat.service.EgovDocumentService;
import com.krdevops.springai.chat.util.EgovDocumentHashUtil;
import com.krdevops.springai.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import com.krdevops.springai.config.AppProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class EgovDocumentServiceImpl implements EgovDocumentService {

    private static final String HASH_KEY_PREFIX = "chat:hash:";

    private final RagService ragService;
    private final Executor documentProcessingExecutor;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AppProperties appProperties;

    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger totalCount = new AtomicInteger(0);
    private final AtomicInteger changedCount = new AtomicInteger(0);

    public EgovDocumentServiceImpl(RagService ragService,
                                   @Qualifier("documentProcessingExecutor") Executor documentProcessingExecutor,
                                   RedisTemplate<String, Object> redisTemplate,
                                   AppProperties appProperties) {
        this.ragService = ragService;
        this.documentProcessingExecutor = documentProcessingExecutor;
        this.redisTemplate = redisTemplate;
        this.appProperties = appProperties;
    }

    @Override
    public boolean isProcessing() { return isProcessing.get(); }

    @Override
    public int getProcessedCount() { return processedCount.get(); }

    @Override
    public int getTotalCount() { return totalCount.get(); }

    @Override
    public int getChangedCount() { return changedCount.get(); }

    @Override
    public CompletableFuture<Integer> loadDocumentsAsync() {
        if (!isProcessing.compareAndSet(false, true)) {
            log.info("이미 인덱싱 중입니다.");
            return CompletableFuture.completedFuture(0);
        }
        processedCount.set(0);
        totalCount.set(0);
        changedCount.set(0);

        return CompletableFuture.supplyAsync(() -> {
            // docId = baseDir 상대 경로로 계산 → 서로 다른 디렉터리의 동일 파일명 충돌 방지
            Map<String, Path> docFileMap = new LinkedHashMap<>();
            for (String pathStr : appProperties.getDocumentPaths()) {
                Path dir = Paths.get(pathStr);
                if (!Files.exists(dir)) {
                    log.warn("문서 디렉터리 없음: {}", pathStr);
                    continue;
                }
                try (Stream<Path> stream = Files.walk(dir)) {
                    stream.filter(Files::isRegularFile)
                          .filter(p -> p.toString().endsWith(".md") || p.toString().endsWith(".pdf"))
                          .forEach(file -> {
                              String docId = dir.relativize(file).toString().replace(File.separator, "_");
                              docFileMap.put(docId, file);
                          });
                } catch (IOException e) {
                    log.error("문서 디렉터리 스캔 실패: {}", pathStr, e);
                }
            }

            totalCount.set(docFileMap.size());
            log.info("인덱싱 시작 — 파일 {}개 (경로 {}개)", docFileMap.size(), appProperties.getDocumentPaths().size());

            AtomicInteger ingested = new AtomicInteger(0);

            // documentProcessingExecutor(코어/큐 용량이 작음)를 한 번에 통째로 넘치게 하지 않도록,
            // 전체 파일을 배치로 나눠 배치 하나가 끝나야 다음 배치를 제출한다.
            // 이렇게 하면 문서 개수가 아무리 많아져도 동시 진행 작업 수는 배치 크기로 고정된다.
            List<Map.Entry<String, Path>> entries = new ArrayList<>(docFileMap.entrySet());
            int batchSize = Math.max(1, appProperties.getDocumentScanBatchSize());

            for (int start = 0; start < entries.size(); start += batchSize) {
                List<Map.Entry<String, Path>> batch =
                        entries.subList(start, Math.min(start + batchSize, entries.size()));

                List<CompletableFuture<Void>> batchFutures = batch.stream()
                    .map(entry -> CompletableFuture.runAsync(() -> {
                        try {
                            String docId = entry.getKey();
                            Path file = entry.getValue();
                            String content = extractContent(file);
                            String newHash = EgovDocumentHashUtil.calculateHash(content);
                            String hashKey = HASH_KEY_PREFIX + docId;
                            Object savedHash = redisTemplate.opsForValue().get(hashKey);
                            if (newHash.equals(savedHash)) {
                                log.debug("변경 없음 — 스킵: {}", docId);
                                processedCount.incrementAndGet();
                                return;
                            }
                            ragService.ingestText(docId, content, "document");
                            redisTemplate.opsForValue().set(hashKey, newHash);
                            processedCount.incrementAndGet();
                            changedCount.incrementAndGet();
                            ingested.incrementAndGet();
                            log.debug("임베딩 완료 (변경 감지): {}", docId);
                        } catch (IOException e) {
                            log.error("파일 읽기 실패: {}", entry.getValue(), e);
                        } catch (Exception e) {
                            log.error("임베딩 실패: {}", entry.getValue().getFileName(), e);
                        }
                    }, documentProcessingExecutor))
                    .toList();

                CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0])).join();
            }

            cleanupOrphanDocuments(docFileMap.keySet());
            log.info("인덱싱 완료 — {}개 처리", ingested.get());
            return ingested.get();
        }, documentProcessingExecutor).whenComplete((result, ex) -> {
            isProcessing.set(false);
            if (ex != null) {
                log.error("인덱싱 중 예외 발생", ex);
            }
        });
    }

    /**
     * document-paths 폴더를 주기적으로 재스캔해 자동으로 인제스트한다.
     * {@code app.document-auto-scan-enabled=true} 로 켜야 동작한다(기본 비활성).
     */
    @Scheduled(fixedDelayString = "${app.document-auto-scan-interval-ms:1800000}")
    public void autoScanDocuments() {
        if (!appProperties.isDocumentAutoScanEnabled()) {
            return;
        }
        log.info("문서 자동 스캔 시작");
        loadDocumentsAsync().exceptionally(ex -> {
            log.error("문서 자동 스캔 실패", ex);
            return 0;
        });
    }

    /**
     * 이번 스캔에서 더 이상 발견되지 않는(원본 파일이 삭제된) 문서의 임베딩을 정리한다.
     * 디렉터리 마운트 실패 등으로 스캔이 비정상적으로 적게 잡히는 경우의 오탐 대량삭제를 막기 위해,
     * 고아 문서 비율이 {@code app.document-cleanup-max-orphan-ratio} 임계값을 넘으면 정리를 건너뛴다.
     */
    private void cleanupOrphanDocuments(Set<String> currentDocIds) {
        if (currentDocIds.isEmpty()) {
            log.warn("스캔 결과가 비어 있어 삭제 정리를 건너뜁니다(문서 디렉터리 접근 실패 등 의심).");
            return;
        }

        Set<String> hashKeys = redisTemplate.keys(HASH_KEY_PREFIX + "*");
        if (hashKeys == null || hashKeys.isEmpty()) {
            return;
        }

        Set<String> existingDocIds = hashKeys.stream()
                .map(k -> k.substring(HASH_KEY_PREFIX.length()))
                .collect(Collectors.toSet());

        Set<String> orphanDocIds = new HashSet<>(existingDocIds);
        orphanDocIds.removeAll(currentDocIds);
        if (orphanDocIds.isEmpty()) {
            return;
        }

        double orphanRatio = orphanDocIds.size() / (double) existingDocIds.size();
        double maxRatio = appProperties.getDocumentCleanupMaxOrphanRatio();
        if (orphanRatio > maxRatio) {
            log.warn("삭제 정리 건너뜀 — 고아 문서 비율({}/{} = {}%)이 임계값({}%)을 초과했습니다. "
                            + "문서 디렉터리 접근 실패 가능성이 있으니 확인하십시오.",
                    orphanDocIds.size(), existingDocIds.size(),
                    Math.round(orphanRatio * 100), Math.round(maxRatio * 100));
            return;
        }

        for (String docId : orphanDocIds) {
            ragService.deleteDocument(docId);
            redisTemplate.delete(HASH_KEY_PREFIX + docId);
            log.info("삭제된 파일 정리: docId={}", docId);
        }
        log.info("삭제 정리 완료 — {}개 문서 제거", orphanDocIds.size());
    }

    @Override
    public Map<String, Object> uploadDocumentFiles(MultipartFile[] files) {
        Map<String, Object> result = new HashMap<>();
        if (files == null || files.length == 0) {
            result.put("success", false);
            result.put("message", "업로드할 파일이 없습니다.");
            return result;
        }
        if (files.length > 5) {
            result.put("success", false);
            result.put("message", "최대 5개 파일만 업로드할 수 있습니다.");
            return result;
        }

        File dir = new File(appProperties.getDocumentPaths().get(0));
        if (!dir.exists()) dir.mkdirs();

        int uploaded = 0;
        for (MultipartFile file : files) {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null
                    || (!originalFilename.endsWith(".md") && !originalFilename.endsWith(".pdf"))) {
                result.put("success", false);
                result.put("message", "마크다운(.md) 또는 PDF(.pdf) 파일만 업로드 가능합니다.");
                return result;
            }
            String filename = Paths.get(originalFilename).getFileName().toString();
            File dest = new File(dir, filename);
            try {
                if (!dest.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator)) {
                    result.put("success", false);
                    result.put("message", "허용되지 않는 파일 경로: " + filename);
                    return result;
                }
                file.transferTo(dest);
                uploaded++;
            } catch (IOException e) {
                result.put("success", false);
                result.put("message", filename + " 저장 실패: " + e.getMessage());
                return result;
            }
        }

        result.put("success", true);
        result.put("uploaded", uploaded);
        return result;
    }

    private String extractContent(Path file) throws IOException {
        if (file.toString().endsWith(".pdf")) {
            try {
                PagePdfDocumentReader reader = new PagePdfDocumentReader(new FileSystemResource(file));
                return reader.get().stream()
                        .map(Document::getText)
                        .collect(Collectors.joining("\n"));
            } catch (Exception e) {
                // ForkPDFLayoutTextStripper 레이아웃 파싱 실패 시 PDFBox 직접 추출로 폴백
                log.warn("레이아웃 파싱 실패, PDFBox 단순 추출로 폴백: {}", file.getFileName());
                try (PDDocument doc = Loader.loadPDF(file.toFile())) {
                    return new PDFTextStripper().getText(doc);
                }
            }
        }
        return Files.readString(file);
    }

    @Override
    public String reindexDocuments() {
        loadDocumentsAsync().exceptionally(ex -> {
            log.error("재인덱싱 실패", ex);
            return 0;
        });
        return "문서 재인덱싱이 시작되었습니다.";
    }

    @Override
    public DocumentStatusResponse getStatusResponse() {
        return new DocumentStatusResponse(isProcessing(), processedCount.get(), totalCount.get(), changedCount.get());
    }
}
