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
        if (isProcessing.get()) {
            log.info("이미 인덱싱 중입니다.");
            return CompletableFuture.completedFuture(0);
        }

        isProcessing.set(true);
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

            int ingested = 0;
            for (Map.Entry<String, Path> entry : docFileMap.entrySet()) {
                String docId = entry.getKey();
                Path file = entry.getValue();
                try {
                    String content = extractContent(file);

                    String newHash = EgovDocumentHashUtil.calculateHash(content);
                    String hashKey = HASH_KEY_PREFIX + docId;
                    Object savedHash = redisTemplate.opsForValue().get(hashKey);

                    if (newHash.equals(savedHash)) {
                        log.debug("변경 없음 — 스킵: {}", docId);
                        processedCount.incrementAndGet();
                        continue;
                    }

                    ragService.ingestText(docId, content, "document");
                    redisTemplate.opsForValue().set(hashKey, newHash);
                    processedCount.incrementAndGet();
                    changedCount.incrementAndGet();
                    ingested++;
                    log.debug("임베딩 완료 (변경 감지): {}", docId);
                } catch (IOException e) {
                    log.error("파일 읽기 실패: {}", file, e);
                } catch (Exception e) {
                    log.error("임베딩 실패: {}", file.getFileName(), e);
                }
            }

            log.info("인덱싱 완료 — {}개 처리", ingested);
            return ingested;
        }, documentProcessingExecutor).whenComplete((result, ex) -> {
            isProcessing.set(false);
            if (ex != null) {
                log.error("인덱싱 중 예외 발생", ex);
            }
        });
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
