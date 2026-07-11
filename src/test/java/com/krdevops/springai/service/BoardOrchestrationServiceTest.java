package com.krdevops.springai.service;

import com.krdevops.springai.model.board.BoardTemplateModel;
import com.krdevops.springai.model.crud.FieldModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * BoardOrchestrationService — Mock 기반 단위 테스트.
 * DB·파일시스템에 의존하지 않고 레이어 순회·파일 수·경로·ThymeleafRuntimeConfigurer 호출을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BoardOrchestrationServiceTest {

    @Mock BoardSchemaService         boardSchemaService;
    @Mock BoardModelFactory          boardModelFactory;
    @Mock BoardTemplateRenderer      boardTemplateRenderer;
    @Mock CodeService                codeService;
    @Mock CodeValidatorService       codeValidatorService;
    @Mock GenerationHistoryService   generationHistoryService;
    @Mock ThymeleafRuntimeConfigurer thymeleafRuntimeConfigurer;
    @Spy ThymeleafLayoutValidator thymeleafLayoutValidator = new ThymeleafLayoutValidator();

    @InjectMocks
    BoardOrchestrationService service;

    private static final FieldModel BBS_ID = new FieldModel(
            "BBS_ID", "bbsId", "String", "게시판ID", true, false, false, 20, "VARCHAR");
    private static final FieldModel NTT_ID = new FieldModel(
            "NTT_ID", "nttId", "Long", "게시글번호", true, false, false, null, "BIGINT");

    private BoardTemplateModel dummyModel() {
        return new BoardTemplateModel(
                "egovframework.let.bbs", "Bbs", "bbs", "BBS",
                "COMTNBBS", "COMTNBBSMASTER", "COMTNBBSUSE",
                "/bbs/bbs", "2026-06-22", "5.0", true,
                BBS_ID, NTT_ID,
                false, null, null,
                List.of(BBS_ID, NTT_ID), List.of(), List.of(), List.of(), List.of(),
                false);
    }

    private void stubSuccess(BoardTemplateModel model) {
        when(boardSchemaService.fetchBoardSchemas(any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("COMTNBBS", List.of()));
        when(boardModelFactory.fromSchemas(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(model);
        doReturn("rendered").when(boardTemplateRenderer).renderByLayerKey(any(), any());
        doReturn("rendered").when(boardTemplateRenderer).renderByLayerKey(any(), any(), any(), any(), any(), any());
        when(codeService.saveGeneratedCode(any(), any())).thenReturn("저장 성공");
        when(codeValidatorService.validateDirectory(any())).thenReturn("OK");
        when(generationHistoryService.saveHistory(any(), any(), any(), any(), any()))
                .thenReturn("이력 저장 완료");
    }

    // ─── Thymeleaf 파일 수 ────────────────────────────────────────────────────

    @Test
    void thymeleaf_succeededFiles_is17() {
        stubSuccess(dummyModel());

        BoardOrchestrationResult result = service.orchestrate(
                "com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                "COMTNBBS", "COMTNBBSMASTER", "COMTNBBSUSE",
                "COMTNFILE", "COMTNFILEDETAIL", "5.0", "thymeleaf",
                "create", null, null);

        assertThat(result.succeededFiles()).hasSize(17);
        assertThat(result.failedFiles()).isEmpty();
    }

    @Test
    void thymeleafReuseWithoutLayout_returnsFailureBeforeSave(@TempDir Path tempDir) {
        when(boardSchemaService.fetchBoardSchemas(any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("COMTNBBS", List.of()));
        when(boardModelFactory.fromSchemas(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(dummyModel());

        BoardOrchestrationResult result = service.orchestrate(
                "com", "Bbs", "egovframework.let.bbs", tempDir.toString(),
                "COMTNBBS", "COMTNBBSMASTER", "COMTNBBSUSE",
                "COMTNFILE", "COMTNFILEDETAIL", "5.0", "thymeleaf");

        assertThat(result.successCount()).isZero();
        assertThat(result.failedFiles()).singleElement()
                .asString()
                .contains("generateThymeleafLayout")
                .contains("layoutBasePath=\"layout\"");
        verify(boardTemplateRenderer, never()).renderByLayerKey(any(), any(), any(), any(), any(), any());
        verify(codeService, never()).saveGeneratedCode(any(), any());
    }

    @Test
    void thymeleafReuseCustomLayoutView_missingFileUnderCustomBase_returnsFailureBeforeSave(
            @TempDir Path tempDir) throws Exception {
        when(boardSchemaService.fetchBoardSchemas(any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("COMTNBBS", List.of()));
        when(boardModelFactory.fromSchemas(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(dummyModel());

        Path customBase = tempDir.resolve("src/main/resources/templates/layout/admin");
        java.nio.file.Files.createDirectories(customBase);
        for (String name : List.of("default.html", "gnb.html", "lnb.html", "breadcrumb.html")) {
            java.nio.file.Files.writeString(customBase.resolve(name), "<html></html>");
        }

        BoardOrchestrationResult result = service.orchestrate(
                "com", "Bbs", "egovframework.let.bbs", tempDir.toString(),
                "COMTNBBS", "COMTNBBSMASTER", "COMTNBBSUSE",
                "COMTNFILE", "COMTNFILEDETAIL", "5.0", "thymeleaf",
                "reuse", "layout/admin/default", "layout/admin/breadcrumb");

        assertThat(result.successCount()).isZero();
        assertThat(result.failedFiles()).singleElement()
                .asString()
                .contains("generateThymeleafLayout")
                .contains("layoutBasePath=\"layout/admin\"")
                .contains("footer.html");
        verify(boardTemplateRenderer, never()).renderByLayerKey(any(), any(), any(), any(), any(), any());
        verify(codeService, never()).saveGeneratedCode(any(), any());
    }

    @Test
    void thymeleafReuseCustomLayoutView_allFilesPresent_rendersWithCustomPaths(
            @TempDir Path tempDir) throws Exception {
        stubSuccess(dummyModel());

        Path customBase = tempDir.resolve("src/main/resources/templates/layout/admin");
        java.nio.file.Files.createDirectories(customBase);
        for (String name : List.of("default.html", "gnb.html", "lnb.html", "breadcrumb.html", "footer.html")) {
            java.nio.file.Files.writeString(customBase.resolve(name), "<html></html>");
        }

        BoardOrchestrationResult result = service.orchestrate(
                "com", "Bbs", "egovframework.let.bbs", tempDir.toString(),
                "COMTNBBS", "COMTNBBSMASTER", "COMTNBBSUSE",
                "COMTNFILE", "COMTNFILEDETAIL", "5.0", "thymeleaf",
                "reuse", "layout/admin/default", "layout/admin/breadcrumb");

        // reuse 기본값은 layout 레이어를 저장하지 않으므로 12개만 성공
        assertThat(result.succeededFiles()).hasSize(12);
        assertThat(result.failedFiles()).isEmpty();
        verify(boardTemplateRenderer, atLeastOnce()).renderByLayerKey(
                any(), any(),
                eq("layout/admin/default"), eq("layout/admin/breadcrumb"), eq("layout/admin"), any());
    }

    // ─── JSP 파일 수 ─────────────────────────────────────────────────────────

    @Test
    void jsp_succeededFiles_is12() {
        stubSuccess(dummyModel());

        BoardOrchestrationResult result = service.orchestrate(
                "com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                "COMTNBBS", "COMTNBBSMASTER", "COMTNBBSUSE",
                "COMTNFILE", "COMTNFILEDETAIL", "5.0", "jsp");

        assertThat(result.succeededFiles()).hasSize(12);
    }

    // ─── layout/default.html 저장 경로 ────────────────────────────────────────

    @Test
    void thymeleaf_layoutHtml_savedUnderTemplatesRoot() {
        stubSuccess(dummyModel());

        service.orchestrate(
                "com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                "COMTNBBS", "COMTNBBSMASTER", "COMTNBBSUSE",
                "COMTNFILE", "COMTNFILEDETAIL", "5.0", "thymeleaf",
                "create", null, null);

        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(codeService, atLeastOnce()).saveGeneratedCode(pathCaptor.capture(), any());

        assertThat(pathCaptor.getAllValues())
                .anyMatch(p -> p.contains("templates/layout/default.html"));
        assertThat(pathCaptor.getAllValues())
                .anyMatch(p -> p.contains("templates/layout/gnb.html"));
    }

    @Test
    void orchestrate_updatesIndexJspToBoardListUrl() {
        stubSuccess(dummyModel());

        service.orchestrate(
                "com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                "COMTNBBS", "COMTNBBSMASTER", "COMTNBBSUSE",
                "COMTNFILE", "COMTNFILEDETAIL", "5.0", "thymeleaf",
                "create", null, null);

        verify(codeService).saveGeneratedCode(
                "/tmp/out/src/main/webapp/index.jsp",
                """
<%@ page contentType="text/html;charset=UTF-8" %>
<jsp:forward page="/bbs/bbsList.do"/>
""");
    }

    // ─── ThymeleafRuntimeConfigurer 호출 ─────────────────────────────────────

    @Test
    void thymeleaf_ensureThymeleafRuntime_isCalled() {
        stubSuccess(dummyModel());

        service.orchestrate(
                "com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                "COMTNBBS", "COMTNBBSMASTER", "COMTNBBSUSE",
                "COMTNFILE", "COMTNFILEDETAIL", "5.0", "thymeleaf",
                "create", null, null);

        verify(thymeleafRuntimeConfigurer)
                .ensureThymeleafRuntime(eq("/tmp/out"), eq("5.0"), any());
    }

    @Test
    void jsp_ensureThymeleafRuntime_notCalled() {
        stubSuccess(dummyModel());

        service.orchestrate(
                "com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                "COMTNBBS", "COMTNBBSMASTER", "COMTNBBSUSE",
                "COMTNFILE", "COMTNFILEDETAIL", "5.0", "jsp");

        verify(thymeleafRuntimeConfigurer, never()).ensureThymeleafRuntime(any(), any(), any());
    }

    // ─── 저장/렌더/검증/이력 실패 ─────────────────────────────────────────────

    @Test
    void jsp_saveFails_recordsInFailedFiles() {
        when(boardSchemaService.fetchBoardSchemas(any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("COMTNBBS", List.of()));
        when(boardModelFactory.fromSchemas(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(dummyModel());
        doReturn("rendered").when(boardTemplateRenderer).renderByLayerKey(any(), any());
        when(codeService.saveGeneratedCode(any(), any())).thenReturn("파일 저장 실패: 권한 없음");
        when(codeValidatorService.validateDirectory(any())).thenReturn("검증 실패");
        when(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).thenReturn("OK");

        BoardOrchestrationResult result = service.orchestrate(
                "com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                "COMTNBBS", "COMTNBBSMASTER", "COMTNBBSUSE",
                "COMTNFILE", "COMTNFILEDETAIL", "5.0", "jsp");

        assertThat(result.hasFailure()).isTrue();
        assertThat(result.failCount()).isEqualTo(12);
        assertThat(result.successCount()).isZero();
        assertThat(result.failedFiles())
                .allSatisfy(f -> assertThat(f).contains("파일 저장 실패: 권한 없음"));
    }

    @Test
    void jsp_renderThrows_recordsInFailedFiles() {
        when(boardSchemaService.fetchBoardSchemas(any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("COMTNBBS", List.of()));
        when(boardModelFactory.fromSchemas(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(dummyModel());
        doThrow(new RuntimeException("템플릿 로딩 실패")).when(boardTemplateRenderer).renderByLayerKey(any(), any());
        when(codeValidatorService.validateDirectory(any())).thenReturn("검증 실패");
        when(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).thenReturn("OK");

        BoardOrchestrationResult result = service.orchestrate(
                "com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                "COMTNBBS", "COMTNBBSMASTER", "COMTNBBSUSE",
                "COMTNFILE", "COMTNFILEDETAIL", "5.0", "jsp");

        assertThat(result.hasFailure()).isTrue();
        assertThat(result.failedFiles())
                .hasSize(12)
                .allSatisfy(f -> assertThat(f).contains("템플릿 로딩 실패"));
    }

    @Test
    void jsp_validationThrows_resultStillReturned() {
        stubSuccess(dummyModel());
        when(codeValidatorService.validateDirectory(any()))
                .thenThrow(new RuntimeException("검증 서비스 오류"));

        BoardOrchestrationResult result = service.orchestrate(
                "com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                "COMTNBBS", "COMTNBBSMASTER", "COMTNBBSUSE",
                "COMTNFILE", "COMTNFILEDETAIL", "5.0", "jsp");

        assertThat(result.successCount()).isEqualTo(12);
        assertThat(result.validationSummary()).contains("검증 실패:");
    }

    @Test
    void jsp_historyThrows_resultStillReturned() {
        stubSuccess(dummyModel());
        when(generationHistoryService.saveHistory(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB 연결 오류"));

        BoardOrchestrationResult result = service.orchestrate(
                "com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                "COMTNBBS", "COMTNBBSMASTER", "COMTNBBSUSE",
                "COMTNFILE", "COMTNFILEDETAIL", "5.0", "jsp");

        assertThat(result.successCount()).isEqualTo(12);
        assertThat(result.historySummary()).contains("이력 저장 실패:");
    }

    // ─── 테이블 미존재 ────────────────────────────────────────────────────────

    @Test
    void tableNotFound_returnsNotFoundResult() {
        when(boardSchemaService.fetchBoardSchemas(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("테이블 없음"));

        BoardOrchestrationResult result = service.orchestrate(
                "com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                "COMTNBBS", "COMTNBBSMASTER", "COMTNBBSUSE",
                "COMTNFILE", "COMTNFILEDETAIL", "5.0", "thymeleaf",
                "create", null, null);

        assertThat(result.tableNotFound()).isTrue();
        assertThat(result.succeededFiles()).isEmpty();
    }
}
