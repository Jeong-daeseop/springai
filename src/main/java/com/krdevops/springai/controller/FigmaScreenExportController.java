package com.krdevops.springai.controller;

import com.krdevops.springai.model.figma.FigmaExportBundle;
import com.krdevops.springai.model.figma.FigmaExportIssue;
import com.krdevops.springai.model.figma.FigmaExportResult;
import com.krdevops.springai.model.figma.FigmaScreenExportRequest;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import com.krdevops.springai.service.figma.FigmaScreenExportService;
import com.krdevops.springai.service.figma.FigmaScreenSpecSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * R6-001~006: Figma 화면 생성 REST API
 * GET /api/figma/screens/{screenId} — 최신 FigmaScreenSpec 조회
 * GET /api/figma/screens/{screenId}/versions/{version} — 특정 버전 조회
 * GET /api/figma/screens/{screenId}/download — Bundle 다운로드
 * POST /api/figma/screens/{screenId}/validate — 저장된 Spec 검증
 */
@Slf4j
@RestController
@RequestMapping("/api/figma/screens")
public class FigmaScreenExportController {

    private final FigmaScreenExportService exportService;
    private final FigmaScreenSpecSerializer serializer;

    public FigmaScreenExportController(
            FigmaScreenExportService exportService,
            FigmaScreenSpecSerializer serializer) {
        this.exportService = exportService;
        this.serializer = serializer;
    }

    /**
     * R6-001: 화면별 최신 FigmaScreenSpec 조회
     */
    @GetMapping("/{screenId}")
    public ResponseEntity<FigmaScreenSpec> getLatestScreen(@PathVariable String screenId) {
        return exportService.findLatest(screenId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * R6-002: 특정 버전 조회
     */
    @GetMapping("/{screenId}/v/{version}")
    public ResponseEntity<FigmaScreenSpec> getScreenVersion(
            @PathVariable String screenId,
            @PathVariable int version) {
        return exportService.findVersion(screenId, version)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * R6-003: JSON 다운로드 (DEC-10=FILE 기본값)
     * FigmaExportBundle (Spec+Profile+Registry+Metadata)을 단일 JSON 파일로 제공
     */
    @GetMapping("/{screenId}/download")
    public ResponseEntity<byte[]> downloadBundle(@PathVariable String screenId) {
        FigmaExportBundle bundle = exportService.findLatestBundle(screenId);
        String json = serializer.toJson(bundle);
        byte[] content = json.getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentLength(content.length);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(screenId + "-figma-export-bundle.json", StandardCharsets.UTF_8)
                .build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(content);
    }

    /**
     * R6-003 변형: 특정 버전 Bundle 다운로드
     */
    @GetMapping("/{screenId}/versions/{version}/download")
    public ResponseEntity<byte[]> downloadBundleVersion(
            @PathVariable String screenId,
            @PathVariable int version) {
        FigmaExportBundle bundle = exportService.findBundleVersion(screenId, version);
        String json = serializer.toJson(bundle);
        byte[] content = json.getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentLength(content.length);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(String.format("%s-v%d-figma-export-bundle.json", screenId, version),
                        StandardCharsets.UTF_8)
                .build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(content);
    }

    /**
     * R6-004: 저장된 Spec 검증 (저장된 Issue + 현재 의미 검증 통합)
     */
    @PostMapping("/{screenId}/validate")
    public ResponseEntity<ValidateResponse> validateStored(
            @PathVariable String screenId,
            @RequestParam(required = false) Integer version) {
        List<FigmaExportIssue> issues = exportService.validateStored(screenId, version);

        long fatalCount = issues.stream()
                .filter(i -> i.severity() == FigmaExportIssue.Severity.FATAL)
                .count();
        long errorCount = issues.stream()
                .filter(i -> i.severity() == FigmaExportIssue.Severity.ERROR)
                .count();

        return ResponseEntity.ok(new ValidateResponse(
                fatalCount == 0 && errorCount == 0,
                fatalCount, errorCount,
                issues, LocalDateTime.now()
        ));
    }

    /**
     * 검증 응답 DTO
     */
    public record ValidateResponse(
            boolean valid,
            long fatalCount,
            long errorCount,
            List<FigmaExportIssue> issues,
            LocalDateTime validatedAt
    ) {
    }
}
