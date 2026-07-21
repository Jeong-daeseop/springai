package com.krdevops.springai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 기존 사용자 CSS를 보존하면서 게시판 CRUD 공통 계약만 멱등적으로 추가한다. */
@Slf4j
@Service
public class KrdsStylesConfigurer {

    public static final String START_MARKER = "/* === egov-board-crud:start === */";
    public static final String END_MARKER = "/* === egov-board-crud:end === */";
    public static final String DENSITY_START_MARKER = TableDensityCssContract.START_MARKER;
    public static final String DENSITY_END_MARKER = TableDensityCssContract.END_MARKER;
    public static final String FORM_COLUMN_LAYOUT_START_MARKER = FormColumnLayoutCssContract.START_MARKER;
    public static final String FORM_COLUMN_LAYOUT_END_MARKER = FormColumnLayoutCssContract.END_MARKER;
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
        Path war = Path.of(outputPath, "src/main/webapp/resources/css/styles.css");
        Path boot = Path.of(outputPath, "src/main/resources/static/resources/css/styles.css");
        Path target = Files.exists(war) ? war : Files.exists(boot) ? boot : null;
        if (target == null) {
            Path warResources = war.getParent();
            Path bootResources = boot.getParent();
            target = Files.isDirectory(warResources) ? war : Files.isDirectory(bootResources) ? boot : null;
            if (target == null) return new CssPatchResult(Status.NOT_FOUND, null,
                    "정적 CSS 리소스 경로를 찾을 수 없습니다. initializeProject를 먼저 실행하세요.");
        }
        try {
            String current = Files.exists(target) ? Files.readString(target, StandardCharsets.UTF_8) : "";
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
                Files.writeString(target, updated, StandardCharsets.UTF_8);
                return new CssPatchResult(Status.PATCHED, target.toString(), "기존 CRUD CSS 계약 갱신 완료");
            }
            Files.createDirectories(target.getParent());
            Files.writeString(target, current + CRUD_CSS, StandardCharsets.UTF_8);
            return new CssPatchResult(Status.PATCHED, target.toString(),
                    current.isEmpty() ? "styles.css 신규 생성" : "기존 styles.css 보강 완료");
        } catch (Exception e) {
            log.warn("[krds-styles] CSS 보강 실패: {}", e.getMessage());
            return new CssPatchResult(Status.FAILED, target.toString(), e.getMessage());
        }
    }

    /** CRUD 표 밀도 스타일을 별도 marker로 멱등 보강한다. */
    public CssPatchResult ensureTableDensityStyles(String outputPath) {
        Path war = Path.of(outputPath, "src/main/webapp/resources/css/styles.css");
        Path boot = Path.of(outputPath, "src/main/resources/static/resources/css/styles.css");
        Path target = Files.exists(war) ? war : Files.exists(boot) ? boot : null;
        if (target == null) {
            target = Files.isDirectory(war.getParent()) ? war
                    : Files.isDirectory(boot.getParent()) ? boot : null;
        }
        if (target == null) {
            return new CssPatchResult(Status.NOT_FOUND, null,
                    "정적 CSS 리소스 경로를 찾을 수 없습니다. initializeProject를 먼저 실행하세요.");
        }
        try {
            String current = Files.exists(target) ? Files.readString(target, StandardCharsets.UTF_8) : "";
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
                Files.writeString(target, current.substring(0, start) + expected + current.substring(end),
                        StandardCharsets.UTF_8);
                return new CssPatchResult(Status.PATCHED, target.toString(), "density CSS 계약 갱신 완료");
            }
            Files.createDirectories(target.getParent());
            Files.writeString(target, current + TableDensityCssContract.CSS, StandardCharsets.UTF_8);
            return new CssPatchResult(Status.PATCHED, target.toString(), "density CSS 보강 완료");
        } catch (Exception e) {
            return new CssPatchResult(Status.FAILED, target.toString(), e.getMessage());
        }
    }

    /** 등록/수정 폼 2단 배치 스타일을 별도 marker로 멱등 보강한다. */
    public CssPatchResult ensureFormColumnLayoutStyles(String outputPath) {
        Path war = Path.of(outputPath, "src/main/webapp/resources/css/styles.css");
        Path boot = Path.of(outputPath, "src/main/resources/static/resources/css/styles.css");
        Path target = Files.exists(war) ? war : Files.exists(boot) ? boot : null;
        if (target == null) {
            target = Files.isDirectory(war.getParent()) ? war
                    : Files.isDirectory(boot.getParent()) ? boot : null;
        }
        if (target == null) {
            return new CssPatchResult(Status.NOT_FOUND, null,
                    "정적 CSS 리소스 경로를 찾을 수 없습니다. initializeProject를 먼저 실행하세요.");
        }
        try {
            String current = Files.exists(target) ? Files.readString(target, StandardCharsets.UTF_8) : "";
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
                Files.writeString(target, current.substring(0, start) + expected + current.substring(end),
                        StandardCharsets.UTF_8);
                return new CssPatchResult(Status.PATCHED, target.toString(), "form column layout CSS 계약 갱신 완료");
            }
            Files.createDirectories(target.getParent());
            Files.writeString(target, current + FormColumnLayoutCssContract.CSS, StandardCharsets.UTF_8);
            return new CssPatchResult(Status.PATCHED, target.toString(), "form column layout CSS 보강 완료");
        } catch (Exception e) {
            return new CssPatchResult(Status.FAILED, target.toString(), e.getMessage());
        }
    }

    public enum Status { PATCHED, PRESERVED, NOT_FOUND, FAILED }
    public record CssPatchResult(Status status, String path, String message) {
        public boolean failed() { return status == Status.FAILED || status == Status.NOT_FOUND; }
    }
}
