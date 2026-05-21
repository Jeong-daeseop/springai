package com.krdevops.springai.chat.service.impl;

import com.krdevops.springai.chat.response.DocumentStatusResponse;
import com.krdevops.springai.chat.service.EgovDocumentService;
import com.krdevops.springai.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.stream.Stream;

@Slf4j
@Service
public class EgovDocumentServiceImpl implements EgovDocumentService {

    @Value("${app.document-path:${user.home}/documents/egovframe-docs-main}")
    private String documentPath;

    private final RagService ragService;
    private final Executor documentProcessingExecutor;

    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger totalCount = new AtomicInteger(0);
    private final AtomicInteger changedCount = new AtomicInteger(0);

    public EgovDocumentServiceImpl(RagService ragService,
                                   @Qualifier("documentProcessingExecutor") Executor documentProcessingExecutor) {
        this.ragService = ragService;
        this.documentProcessingExecutor = documentProcessingExecutor;
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
            Path dir = Paths.get(documentPath);
            if (!Files.exists(dir)) {
                log.warn("문서 디렉터리 없음: {}", documentPath);
                return 0;
            }

            List<Path> mdFiles;
            try (Stream<Path> stream = Files.walk(dir)) {
                mdFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".md"))
                    .toList();
            } catch (IOException e) {
                log.error("문서 디렉터리 스캔 실패: {}", documentPath, e);
                return 0;
            }

            totalCount.set(mdFiles.size());
            log.info("인덱싱 시작 — 파일 {}개: {}", mdFiles.size(), documentPath);

            int ingested = 0;
            for (Path file : mdFiles) {
                try {
                    String content = Files.readString(file);
                    String docId = file.getFileName().toString();
                    ragService.ingestText(docId, content, "document");
                    processedCount.incrementAndGet();
                    changedCount.incrementAndGet();
                    ingested++;
                    log.debug("임베딩 완료: {}", docId);
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
    public Map<String, Object> uploadMarkdownFiles(MultipartFile[] files) {
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

        File dir = new File(documentPath);
        if (!dir.exists()) dir.mkdirs();

        int uploaded = 0;
        for (MultipartFile file : files) {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.endsWith(".md")) {
                result.put("success", false);
                result.put("message", "마크다운(.md) 파일만 업로드 가능합니다.");
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
