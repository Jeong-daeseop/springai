package com.krdevops.springai.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoardProgramUrlParserTest {

    private final BoardProgramUrlParser parser = new BoardProgramUrlParser();

    @Test
    void parsesPathAndBbsIdRegardlessOfQueryOrder() {
        var first = parser.parse("/cop/bbs/selectBoardList.do?bbsId=BBSMSTR_AAAAAAAAAAAA&menuNo=10");
        var second = parser.parse("/cop/bbs/selectBoardList.do?menuNo=10&bbsId=BBSMSTR_AAAAAAAAAAAA");

        assertThat(first.path()).isEqualTo("/cop/bbs/selectBoardList.do");
        assertThat(first.bbsId()).isEqualTo("BBSMSTR_AAAAAAAAAAAA");
        assertThat(second.bbsId()).isEqualTo(first.bbsId());
    }

    @Test
    void decodesBbsIdAndAllowsMissingQuery() {
        assertThat(parser.parse("/cop/bbs/selectBoardList.do?bbsId=BBS%20NOTICE").bbsId())
                .isEqualTo("BBS NOTICE");
        assertThat(parser.parse("/cop/bbs/selectBoardList.do").bbsId()).isNull();
        assertThat(parser.parse(null).path()).isNull();
    }

    @Test
    void rejectsMalformedEncodingAndConflictingDuplicateBbsId() {
        assertThatThrownBy(() -> parser.parse("/cop/bbs/list.do?bbsId=%ZZ"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("%ZZ");
        assertThatThrownBy(() -> parser.parse("/cop/bbs/list.do?bbsId=A&bbsId=B"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("중복");
    }
}
