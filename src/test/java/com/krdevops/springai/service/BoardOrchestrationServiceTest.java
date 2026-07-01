package com.krdevops.springai.service;

import com.krdevops.springai.model.board.BoardTemplateModel;
import com.krdevops.springai.model.crud.FieldModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * BoardOrchestrationService — Mock 기반 단위 테스트.
 * DB·파일시스템에 의존하지 않고 레이어 순회·파일 수·경로·ThymeleafRuntimeConfigurer 호출을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class BoardOrchestrationServiceTest {

    @Mock BoardSchemaService         boardSchemaService;
    @Mock BoardModelFactory          boardModelFactory;
    @Mock BoardTemplateRenderer      boardTemplateRenderer;
    @Mock CodeService                codeService;
    @Mock CodeValidatorService       codeValidatorService;
    @Mock GenerationHistoryService   generationHistoryService;
    @Mock ThymeleafRuntimeConfigurer thymeleafRuntimeConfigurer;

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
        when(boardTemplateRenderer.renderByLayerKey(any(), any())).thenReturn("rendered");
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
                "COMTNFILE", "COMTNFILEDETAIL", "5.0", "thymeleaf");

        assertThat(result.succeededFiles()).hasSize(17);
        assertThat(result.failedFiles()).isEmpty();
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
                "COMTNFILE", "COMTNFILEDETAIL", "5.0", "thymeleaf");

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
                "COMTNFILE", "COMTNFILEDETAIL", "5.0", "thymeleaf");

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
                "COMTNFILE", "COMTNFILEDETAIL", "5.0", "thymeleaf");

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

    // ─── 테이블 미존재 ────────────────────────────────────────────────────────

    @Test
    void tableNotFound_returnsNotFoundResult() {
        when(boardSchemaService.fetchBoardSchemas(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("테이블 없음"));

        BoardOrchestrationResult result = service.orchestrate(
                "com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                "COMTNBBS", "COMTNBBSMASTER", "COMTNBBSUSE",
                "COMTNFILE", "COMTNFILEDETAIL", "5.0", "thymeleaf");

        assertThat(result.tableNotFound()).isTrue();
        assertThat(result.succeededFiles()).isEmpty();
    }
}
