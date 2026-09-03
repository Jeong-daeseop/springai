package com.krdevops.springai.service;

import com.krdevops.springai.model.thymeleaf.ResolvedDesignTokens;
import com.krdevops.springai.model.write.ProjectChangeSet;
import com.krdevops.springai.model.write.ProjectWritePolicy;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.write.ApplyOutcome;
import com.krdevops.springai.service.write.ApprovedProjectWritePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 기존 사용자 CSS를 보존하면서 게시판 CRUD 공통 계약만 멱등적으로 추가한다.
 *
 * <p>WP7 5차 pass: 저장은 {@code Files.writeString} 원시 호출 대신 공용
 * {@link ApprovedProjectWritePort}({@link ProjectWritePolicy#ATOMIC_APPROVED})로 위임한다 —
 * 사용자가 직접 편집한 CSS를 보존하는 계약이므로 drift 검사 없이 덮어쓰는 BEST_EFFORT는 맞지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KrdsStylesConfigurer {

    private static final String WAR_RELATIVE_PATH = "src/main/webapp/resources/css/styles.css";
    private static final String BOOT_RELATIVE_PATH = "src/main/resources/static/resources/css/styles.css";

    private final CodeService codeService;
    private final ApprovedProjectWritePort writePort;
    private final OperationHashFactory hashFactory;

    public static final String START_MARKER = "/* === egov-board-crud:start === */";
    public static final String END_MARKER = "/* === egov-board-crud:end === */";
    public static final String DENSITY_START_MARKER = TableDensityCssContract.START_MARKER;
    public static final String DENSITY_END_MARKER = TableDensityCssContract.END_MARKER;
    public static final String FORM_COLUMN_LAYOUT_START_MARKER = FormColumnLayoutCssContract.START_MARKER;
    public static final String FORM_COLUMN_LAYOUT_END_MARKER = FormColumnLayoutCssContract.END_MARKER;
    public static final String DESIGN_MD_TOKEN_START_MARKER = "/* === egov-design-md-tokens:start === */";
    public static final String DESIGN_MD_TOKEN_END_MARKER = "/* === egov-design-md-tokens:end === */";
    static final String CRUD_CSS = """

/* === egov-board-crud:start === */
/* 화면 스코프 → 구조 클래스 → 요소 modifier 순으로 책임을 좁힌다. */
:root {
    --egov-screen-control-height: 48px;
    --egov-screen-control-padding-x: 16px;
    --egov-screen-textarea-min-height: 220px;
    --egov-screen-font-size: 16px;
    --egov-screen-link-font-size: 13px;
}
.egov-crud-page { font-size: var(--egov-screen-font-size); line-height: 1.5; }
.egov-crud-page .egov-search-form { width: 100%; }
.egov-crud-page .egov-control {
    min-height: var(--egov-screen-control-height);
    padding-inline: var(--egov-screen-control-padding-x);
    font-size: var(--egov-screen-font-size);
}
.egov-crud-page textarea.egov-control.egov-textarea {
    min-height: var(--egov-screen-textarea-min-height);
    padding-block: 12px;
}
.egov-crud-page .egov-btn { min-height: var(--egov-screen-control-height); font-size: 15px; }
.egov-crud-page .egov-btn.small { min-height: 40px; }
.egov-crud-page .egov-list-table,
.egov-crud-page .egov-form-table { width: 100%; font-size: 15px; }
.egov-crud-page .egov-pagination,
.egov-crud-page .egov-pagination ol { display: flex; align-items: center; justify-content: center; }
.egov-crud-page .egov-pagination a,
.egov-crud-page .egov-pagination li { min-width: 40px; min-height: 40px; line-height: 40px; text-align: center; }
.krds-btn {
    --krds-button--size-height-medium: 38px;
    --krds-button--size-height-small: 34px;
    --krds-button--size-height-large: 38px;
    --krds-button--padding-x-medium: 16px;
}
.krds-input {
    --krds-input--size-height-medium: 38px;
    --krds-input--size-height-large: 38px;
    --krds-input--size-height-small: 34px;
    --krds-input--textarea-size-height: var(--egov-screen-textarea-min-height);
}
.krds-form-select {
    --krds-form-select--size-height-medium: 38px;
    --krds-form-select--size-height-large: 38px;
    --krds-form-select--size-height-small: 34px;
}
.krds-table-wrap .tbl.data {
    --krds-table--data-tbody-padding: 10px;
    --krds-table--data-tbody-padding-sides: 16px;
    --krds-table--data-thead-th-padding: 10px;
    --krds-table--data-thead-th-padding-sides: 16px;
}
.krds-pagination { display: flex; align-items: center; justify-content: center; gap: 4px; }
.krds-pagination ol { display: flex; align-items: center; gap: 4px; list-style: none; margin: 0; padding: 0; }
.krds-pagination li a,
.krds-pagination > a[class^="btn-"] {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 32px;
    height: 32px;
    padding: 0 6px;
    border-radius: 4px;
    font-size: 14px;
}
.egov-crud-page .egov-primary-text,
.egov-crud-page .egov-detail-link,
.egov-crud-page .egov-file-detail-link,
.egov-crud-page .egov-file-empty,
.egov-crud-page .egov-post-nav-link { font-size: var(--egov-screen-link-font-size); }
/* === egov-board-crud:end === */
""";

    public CssPatchResult ensureBoardCrudStyles(String outputPath) {
        TargetResolution resolution = resolveTarget(outputPath);
        if (resolution == null) {
            return new CssPatchResult(Status.NOT_FOUND, null,
                    "정적 CSS 리소스 경로를 찾을 수 없습니다. initializeProject를 먼저 실행하세요.");
        }
        Path target = resolution.path();
        try {
            boolean existed = Files.exists(target);
            String current = existed ? Files.readString(target, StandardCharsets.UTF_8) : "";
            String beforeHash = existed ? hashFactory.sha256Hex(current.getBytes(StandardCharsets.UTF_8)) : null;
            if (current.contains(START_MARKER)) {
                int start = current.indexOf(START_MARKER);
                int end = current.indexOf(END_MARKER, start);
                if (end < 0) {
                    return new CssPatchResult(Status.FAILED, target.toString(),
                            "CRUD CSS 종료 marker가 없어 기존 CSS를 안전하게 갱신할 수 없습니다.");
                }
                end += END_MARKER.length();
                String expected = CRUD_CSS.trim();
                String existing = current.substring(start, end).trim();
                if (existing.equals(expected)) {
                    return new CssPatchResult(Status.PRESERVED, target.toString(), "기존 보강 블록 유지");
                }
                String updated = current.substring(0, start) + expected + current.substring(end);
                writeChange(outputPath, resolution.relativePath(), beforeHash, updated);
                return new CssPatchResult(Status.PATCHED, target.toString(), "기존 CRUD CSS 계약 갱신 완료");
            }
            writeChange(outputPath, resolution.relativePath(), beforeHash, current + CRUD_CSS);
            return new CssPatchResult(Status.PATCHED, target.toString(),
                    current.isEmpty() ? "styles.css 신규 생성" : "기존 styles.css 보강 완료");
        } catch (IOException e) {
            log.warn("[krds-styles] CSS 보강 실패: {}", e.getMessage());
            return new CssPatchResult(Status.FAILED, target.toString(), e.getMessage());
        }
    }

    /** CRUD 표 밀도 스타일을 별도 marker로 멱등 보강한다. */
    public CssPatchResult ensureTableDensityStyles(String outputPath) {
        TargetResolution resolution = resolveTarget(outputPath);
        if (resolution == null) {
            return new CssPatchResult(Status.NOT_FOUND, null,
                    "정적 CSS 리소스 경로를 찾을 수 없습니다. initializeProject를 먼저 실행하세요.");
        }
        Path target = resolution.path();
        try {
            boolean existed = Files.exists(target);
            String current = existed ? Files.readString(target, StandardCharsets.UTF_8) : "";
            String beforeHash = existed ? hashFactory.sha256Hex(current.getBytes(StandardCharsets.UTF_8)) : null;
            String expected = TableDensityCssContract.CSS.trim();
            int start = current.indexOf(DENSITY_START_MARKER);
            if (start >= 0) {
                int end = current.indexOf(DENSITY_END_MARKER, start);
                if (end < 0) {
                    return new CssPatchResult(Status.FAILED, target.toString(),
                            "table density CSS 종료 marker가 없어 안전하게 갱신할 수 없습니다.");
                }
                end += DENSITY_END_MARKER.length();
                String existing = current.substring(start, end).trim();
                if (existing.equals(expected)) {
                    return new CssPatchResult(Status.PRESERVED, target.toString(), "기존 density 블록 유지");
                }
                writeChange(outputPath, resolution.relativePath(), beforeHash,
                        current.substring(0, start) + expected + current.substring(end));
                return new CssPatchResult(Status.PATCHED, target.toString(), "density CSS 계약 갱신 완료");
            }
            writeChange(outputPath, resolution.relativePath(), beforeHash, current + TableDensityCssContract.CSS);
            return new CssPatchResult(Status.PATCHED, target.toString(), "density CSS 보강 완료");
        } catch (IOException e) {
            return new CssPatchResult(Status.FAILED, target.toString(), e.getMessage());
        }
    }

    /** 등록/수정 폼 2단 배치 스타일을 별도 marker로 멱등 보강한다. */
    public CssPatchResult ensureFormColumnLayoutStyles(String outputPath) {
        TargetResolution resolution = resolveTarget(outputPath);
        if (resolution == null) {
            return new CssPatchResult(Status.NOT_FOUND, null,
                    "정적 CSS 리소스 경로를 찾을 수 없습니다. initializeProject를 먼저 실행하세요.");
        }
        Path target = resolution.path();
        try {
            boolean existed = Files.exists(target);
            String current = existed ? Files.readString(target, StandardCharsets.UTF_8) : "";
            String beforeHash = existed ? hashFactory.sha256Hex(current.getBytes(StandardCharsets.UTF_8)) : null;
            String expected = FormColumnLayoutCssContract.CSS.trim();
            int start = current.indexOf(FORM_COLUMN_LAYOUT_START_MARKER);
            if (start >= 0) {
                int end = current.indexOf(FORM_COLUMN_LAYOUT_END_MARKER, start);
                if (end < 0) {
                    return new CssPatchResult(Status.FAILED, target.toString(),
                            "form column layout CSS 종료 marker가 없어 안전하게 갱신할 수 없습니다.");
                }
                end += FORM_COLUMN_LAYOUT_END_MARKER.length();
                String existing = current.substring(start, end).trim();
                if (existing.equals(expected)) {
                    return new CssPatchResult(Status.PRESERVED, target.toString(), "기존 form column layout 블록 유지");
                }
                writeChange(outputPath, resolution.relativePath(), beforeHash,
                        current.substring(0, start) + expected + current.substring(end));
                return new CssPatchResult(Status.PATCHED, target.toString(), "form column layout CSS 계약 갱신 완료");
            }
            writeChange(outputPath, resolution.relativePath(), beforeHash,
                    current + FormColumnLayoutCssContract.CSS);
            return new CssPatchResult(Status.PATCHED, target.toString(), "form column layout CSS 보강 완료");
        } catch (IOException e) {
            return new CssPatchResult(Status.FAILED, target.toString(), e.getMessage());
        }
    }

    /**
     * DESIGN.md에서 해석된 디자인 토큰을 별도 marker로 멱등 보강한다.
     *
     * <p>값은 항상 {@code var(--krds-...)} 형태의 KRDS CSS 변수 참조이며, raw hex/px 값을
     * 직접 쓰지 않는다 — {@link com.krdevops.springai.service.thymeleaf.CompanyDesignTokenResolver}의
     * "이름만, 값 아님" 계약을 그대로 유지한다. 반영할 토큰이 없으면 파일을 건드리지 않는다.
     */
    public CssPatchResult ensureDesignMdTokenStyles(String outputPath, ResolvedDesignTokens tokens) {
        String body = buildDesignMdTokenCss(tokens);
        if (body == null) {
            return new CssPatchResult(Status.PRESERVED, null, "반영할 디자인 토큰이 없어 건너뜀");
        }
        TargetResolution resolution = resolveTarget(outputPath);
        if (resolution == null) {
            return new CssPatchResult(Status.NOT_FOUND, null,
                    "정적 CSS 리소스 경로를 찾을 수 없습니다. initializeProject를 먼저 실행하세요.");
        }
        Path target = resolution.path();
        try {
            boolean existed = Files.exists(target);
            String current = existed ? Files.readString(target, StandardCharsets.UTF_8) : "";
            String beforeHash = existed ? hashFactory.sha256Hex(current.getBytes(StandardCharsets.UTF_8)) : null;
            String expected = body.trim();
            int start = current.indexOf(DESIGN_MD_TOKEN_START_MARKER);
            if (start >= 0) {
                int end = current.indexOf(DESIGN_MD_TOKEN_END_MARKER, start);
                if (end < 0) {
                    return new CssPatchResult(Status.FAILED, target.toString(),
                            "DESIGN.md 토큰 CSS 종료 marker가 없어 안전하게 갱신할 수 없습니다.");
                }
                end += DESIGN_MD_TOKEN_END_MARKER.length();
                String existing = current.substring(start, end).trim();
                if (existing.equals(expected)) {
                    return new CssPatchResult(Status.PRESERVED, target.toString(), "기존 DESIGN.md 토큰 블록 유지");
                }
                writeChange(outputPath, resolution.relativePath(), beforeHash,
                        current.substring(0, start) + expected + current.substring(end));
                return new CssPatchResult(Status.PATCHED, target.toString(), "DESIGN.md 토큰 CSS 갱신 완료");
            }
            writeChange(outputPath, resolution.relativePath(), beforeHash, current + body);
            return new CssPatchResult(Status.PATCHED, target.toString(), "DESIGN.md 토큰 CSS 보강 완료");
        } catch (IOException e) {
            return new CssPatchResult(Status.FAILED, target.toString(), e.getMessage());
        }
    }

    private String buildDesignMdTokenCss(ResolvedDesignTokens tokens) {
        StringBuilder body = new StringBuilder();
        appendDeclarations(body, tokens.colorTokens());
        appendDeclarations(body, tokens.typographyTokens());
        appendDeclarations(body, tokens.spacingTokens());
        appendDeclarations(body, tokens.radiusTokens());
        appendDeclarations(body, tokens.layoutTokens());
        if (body.isEmpty()) {
            return null;
        }
        return "\n" + DESIGN_MD_TOKEN_START_MARKER + "\n:root {\n" + body + "}\n"
                + DESIGN_MD_TOKEN_END_MARKER + "\n";
    }

    private void appendDeclarations(StringBuilder body, Map<String, String> tokens) {
        if (tokens == null) {
            return;
        }
        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            body.append("    --design-md-").append(sanitize(entry.getKey()))
                    .append(": var(").append(entry.getValue()).append(");\n");
        }
    }

    private static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "unknown";
        }
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    private TargetResolution resolveTarget(String outputPath) {
        Path war = Path.of(outputPath, WAR_RELATIVE_PATH);
        Path boot = Path.of(outputPath, BOOT_RELATIVE_PATH);
        if (Files.exists(war)) return new TargetResolution(war, WAR_RELATIVE_PATH);
        if (Files.exists(boot)) return new TargetResolution(boot, BOOT_RELATIVE_PATH);
        if (Files.isDirectory(war.getParent())) return new TargetResolution(war, WAR_RELATIVE_PATH);
        if (Files.isDirectory(boot.getParent())) return new TargetResolution(boot, BOOT_RELATIVE_PATH);
        return null;
    }

    private void writeChange(String outputPath, String relativePath, String beforeHash, String after)
            throws IOException {
        codeService.validateOutputRoot(outputPath);
        ProjectChangeSet changeSet = new ProjectChangeSet(
                outputPath, null,
                List.of(new ProjectChangeSet.FileChange(relativePath, beforeHash, after, null)),
                List.of(), ProjectWritePolicy.ATOMIC_APPROVED);
        ApplyOutcome outcome = writePort.apply(changeSet);
        if (outcome.status() != ApplyOutcome.Status.APPLIED) {
            String detail = switch (outcome.status()) {
                case CONFLICT -> "적용 직전 파일이 변경됨: " + outcome.conflictingPaths();
                case ROLLED_BACK -> outcome.failureDetail();
                case ROLLBACK_FAILED -> "복구까지 실패함(" + outcome.failureDetail()
                        + ") — 원본 상태로 안 돌아갔을 수 있습니다: " + outcome.failureMessages();
                default -> "알 수 없는 결과: " + outcome.status();
            };
            throw new IOException(detail);
        }
    }

    private record TargetResolution(Path path, String relativePath) {
    }

    public enum Status { PATCHED, PRESERVED, NOT_FOUND, FAILED }
    public record CssPatchResult(Status status, String path, String message) {
        public boolean failed() { return status == Status.FAILED || status == Status.NOT_FOUND; }
    }
}
