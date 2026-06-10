package com.krdevops.springai.chat.service;

import com.krdevops.springai.chat.response.DocumentStatusResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface EgovDocumentService {
    CompletableFuture<Integer> loadDocumentsAsync();
    boolean isProcessing();
    int getProcessedCount();
    int getTotalCount();
    int getChangedCount();
    Map<String, Object> uploadDocumentFiles(MultipartFile[] files);
    String reindexDocuments();
    DocumentStatusResponse getStatusResponse();
}
