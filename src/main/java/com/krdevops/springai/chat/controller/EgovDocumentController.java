package com.krdevops.springai.chat.controller;

import com.krdevops.springai.chat.response.DocumentStatusResponse;
import com.krdevops.springai.chat.service.EgovDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.allowed-origins:http://localhost:8080}")
public class EgovDocumentController {

    private final EgovDocumentService egovDocumentService;

    @GetMapping("/status")
    public DocumentStatusResponse getStatus() {
        return egovDocumentService.getStatusResponse();
    }

    @PostMapping("/reindex")
    public String reindexDocuments() {
        return egovDocumentService.reindexDocuments();
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocumentFiles(
            @RequestParam("files") MultipartFile[] files) {
        Map<String, Object> result = egovDocumentService.uploadDocumentFiles(files);
        return Boolean.TRUE.equals(result.get("success"))
            ? ResponseEntity.ok(result)
            : ResponseEntity.badRequest().body(result);
    }
}
