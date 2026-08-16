package com.krdevops.springai.controller;

import com.krdevops.springai.model.figma.FigmaExportIssue;
import com.krdevops.springai.model.figma.FigmaExportResult;
import com.krdevops.springai.model.figma.FigmaScreenExportRequest;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import com.krdevops.springai.service.figma.FigmaRestTokenService;
import com.krdevops.springai.service.figma.FigmaScreenExportService;
import lombok.RequiredArgsConstructor;
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
import java.util.List;
import java.util.Set;

/** R6-001~004: FigmaScreenSpec 생성·조회·검증·Bundle 다운로드 API. */
@RestController
@RequestMapping("/api/figma")
@RequiredArgsConstructor
public class FigmaExportController {

    private final FigmaScreenExportService exportService;
    private final FigmaRestTokenService restTokenService;

    /** R6-012: DEC-10=REST에서 Plugin이 장기 X-API-Key 대신 쓸 단기 토큰 발급(호출 자체는 X-API-Key로 인증). */
    @PostMapping("/tokens")
    public TokenResponse issueToken() {
        if (!restTokenService.isEnabled()) {
            throw new FigmaRequestException("FIGMA_REST_TOKEN_DISABLED",
                    "app.figma.rest-token-secret이 설정되지 않아 단기 토큰 발급이 비활성화되어 있습니다.");
        }
        FigmaRestTokenService.IssuedToken issued = restTokenService.issue(Set.of(
                FigmaRestTokenService.SCOPE_SCREENS_READ,
                FigmaRestTokenService.SCOPE_REFINEMENTS_WRITE,
                FigmaRestTokenService.SCOPE_REPORTS_WRITE));
        return new TokenResponse(issued.token(), issued.expiresAt().toString());
    }

    @PostMapping("/exports")
    public FigmaExportResult export(@jakarta.validation.Valid @RequestBody FigmaScreenExportRequest request) {
        try {
            return exportService.export(request);
        } catch (IllegalArgumentException exception) {
            throw new FigmaRequestException("FIGMA_EXPORT_INVALID", exception.getMessage());
        }
    }

    @GetMapping("/screens/{screenId}")
    public FigmaScreenSpec latest(@PathVariable String screenId) {
        return exportService.findLatest(screenId)
                .orElseThrow(() -> notFound(screenId, null));
    }

    @GetMapping("/screens/{screenId}/versions/{version}")
    public FigmaScreenSpec version(@PathVariable String screenId, @PathVariable int version) {
        return exportService.findVersion(screenId, version)
                .orElseThrow(() -> notFound(screenId, version));
    }

    @PostMapping("/screens/{screenId}/validate")
    public ValidationResponse validate(
            @PathVariable String screenId,
            @RequestParam(required = false) Integer version
    ) {
        try {
            List<FigmaExportIssue> issues = exportService.validateStored(screenId, version);
            boolean valid = issues.stream().noneMatch(issue ->
                    issue.severity() == FigmaExportIssue.Severity.FATAL
                            || issue.severity() == FigmaExportIssue.Severity.ERROR);
            return new ValidationResponse(screenId, version, valid, issues);
        } catch (IllegalArgumentException exception) {
            throw notFound(screenId, version);
        }
    }

    @GetMapping("/screens/{screenId}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable String screenId,
            @RequestParam(required = false) Integer version,
            @org.springframework.web.bind.annotation.RequestHeader(
                    value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        try {
            String json = version == null
                    ? exportService.findLatestBundleAsJson(screenId)
                    : exportService.findBundleVersionAsJson(screenId, version);
            String etag = etag(json);
            if (etag.equals(ifNoneMatch)) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_MODIFIED)
                        .eTag(etag)
                        .build();
            }

            String filename = screenId + (version == null ? "" : "-v" + version)
                    + ".figma-export-bundle.json";
            ContentDisposition disposition = ContentDisposition.attachment()
                    .filename(filename, StandardCharsets.UTF_8)
                    .build();
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .eTag(etag)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .body(json.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            throw notFound(screenId, version);
        }
    }

    private String etag(String json) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(json.getBytes(StandardCharsets.UTF_8));
            return "\"" + java.util.HexFormat.of().formatHex(digest) + "\"";
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private FigmaResourceNotFoundException notFound(String screenId, Integer version) {
        String suffix = version == null ? "" : " v" + version;
        return new FigmaResourceNotFoundException(
                "FIGMA_SCREEN_NOT_FOUND",
                "FigmaScreenSpec을 찾을 수 없습니다: " + screenId + suffix);
    }

    public record TokenResponse(String token, String expiresAt) {}

    public record ValidationResponse(
            String screenId,
            Integer version,
            boolean valid,
            List<FigmaExportIssue> issues
    ) {}
}
