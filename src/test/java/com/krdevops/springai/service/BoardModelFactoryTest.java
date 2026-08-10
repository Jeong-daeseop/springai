package com.krdevops.springai.service;

import com.krdevops.springai.model.board.BoardProgramMetadata;
import com.krdevops.springai.model.design.DataSourceSpec;
import com.krdevops.springai.model.design.FieldSelectionSource;
import com.krdevops.springai.model.design.FieldSource;
import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenFieldBinding;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.UiFieldRole;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BoardModelFactoryTest {

    private final BoardModelFactory factory = new BoardModelFactory();

    private List<Map<String, Object>> qnaColumns() {
        return List.of(
                column("BBS_ID", "varchar", "PRI"),
                column("NTT_ID", "bigint", "PRI"),
                column("NTT_SJ", "varchar", ""),
                column("NTCR_NM", "varchar", ""),
                column("FRST_REGIST_PNTTM", "datetime", ""),
                column("ANSWER_AT", "varchar", ""));
    }

    @Test
    void fromSchemas_screenSpecificationExplicitColumns_overridesPreferredListFields() {
        ScreenFieldBinding title = binding("nttSj", "NTT_SJ");
        ScreenFieldBinding writer = binding("ntcrNm", "NTCR_NM");
        ScreenFieldBinding answerStatus = binding("answerAt", "ANSWER_AT");
        ScreenSpecification specification = new ScreenSpecification(
                "spec", 1, ScreenSpecStatus.APPROVED, "질문과 답변", "board", "BOARD_LIST",
                "com", "LETTNBBS", List.of(DataSourceSpec.primary("com", "LETTNBBS")),
                List.of(new PageSpec("list", "BOARD_LIST", List.of(title, writer, answerStatus), List.of(),
                        FieldSelectionSource.EXPLICIT)),
                List.of(), LocalDateTime.now());

        var model = factory.fromSchemas("LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE", null,
                "Qna", "egovframework.let.qna", "5.0",
                Map.of("main", qnaColumns(), "master", List.of()),
                BoardProgramMetadata.fallback(null), specification);

        assertThat(model.listFields()).extracting("javaName")
                .containsExactly("bbsId", "nttId", "nttSj", "ntcrNm", "answerAt");
    }

    @Test
    void fromSchemas_screenSpecificationDefaultSource_fallsBackToPreferredList() {
        ScreenFieldBinding title = binding("nttSj", "NTT_SJ");
        ScreenSpecification specification = new ScreenSpecification(
                "spec", 1, ScreenSpecStatus.APPROVED, "질문과 답변", "board", "BOARD_LIST",
                "com", "LETTNBBS", List.of(DataSourceSpec.primary("com", "LETTNBBS")),
                List.of(new PageSpec("list", "BOARD_LIST", List.of(title), List.of(),
                        FieldSelectionSource.DEFAULT)),
                List.of(), LocalDateTime.now());

        var model = factory.fromSchemas("LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE", null,
                "Qna", "egovframework.let.qna", "5.0",
                Map.of("main", qnaColumns(), "master", List.of()),
                BoardProgramMetadata.fallback(null), specification);

        assertThat(model.listFields()).extracting("javaName")
                .containsExactly("nttId", "nttSj", "ntcrNm", "frstRegistPnttm", "bbsId", "answerAt");
    }

    private ScreenFieldBinding binding(String id, String column) {
        return new ScreenFieldBinding(
                id, id, UiFieldRole.GENERIC, FieldSource.column("b", column),
                true, false, false, false, "TEXT", 1.0);
    }

    @Test
    void programMetadataOverridesDisplayButKeepsCanonicalRoute() {
        List<Map<String, Object>> columns = List.of(
                column("BBS_ID", "varchar", "PRI"),
                column("NTT_ID", "bigint", "PRI"),
                column("NTT_SJ", "varchar", ""));
        BoardProgramMetadata metadata = new BoardProgramMetadata(
                "EgovInfoNotice", "/cop/bbs/", "공지사항",
                "/cop/bbs/selectBoardList.do?bbsId=BBS_NOTICE",
                "/cop/bbs/selectBoardList.do", "BBS_NOTICE", "알림정보",
                BoardProgramMetadata.Source.DATABASE, BoardProgramMetadata.Status.RESOLVED, null);

        var model = factory.fromSchemas("LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE", null,
                "InfoNotice", "egovframework.let.cop.bbs", "5.0",
                Map.of("main", columns, "master", List.of()), metadata);

        assertThat(model.display().displayName()).isEqualTo("공지사항");
        assertThat(model.display().upperMenuName()).isEqualTo("알림정보");
        assertThat(model.route().canonicalPrefix()).isEqualTo("/cop/bbs/infoNotice");
        assertThat(model.route().registeredListPath()).isEqualTo("/cop/bbs/selectBoardList.do");
        assertThat(model.route().defaultBbsId()).isEqualTo("BBS_NOTICE");
    }

    private Map<String, Object> column(String name, String type, String key) {
        return Map.of(
                "COLUMN_NAME", name,
                "DATA_TYPE", type,
                "CHARACTER_MAXIMUM_LENGTH", "varchar".equals(type) ? 100L : 0L,
                "IS_NULLABLE", "NO",
                "COLUMN_COMMENT", name,
                "COLUMN_KEY", key);
    }
}
