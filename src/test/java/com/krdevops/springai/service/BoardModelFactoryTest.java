package com.krdevops.springai.service;

import com.krdevops.springai.model.board.BoardProgramMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BoardModelFactoryTest {

    private final BoardModelFactory factory = new BoardModelFactory();

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
