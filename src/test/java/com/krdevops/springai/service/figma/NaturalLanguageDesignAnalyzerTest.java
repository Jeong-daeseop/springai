package com.krdevops.springai.service.figma;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NaturalLanguageDesignAnalyzerTest {
    private final NaturalLanguageDesignAnalyzer analyzer = new NaturalLanguageDesignAnalyzer();

    @Test
    void extractsQualifiedTableAndFormType() {
        var result = analyzer.analyze("appdb.user_account 등록 form 화면", "mobile");
        assertThat(result.database()).isEqualTo("appdb");
        assertThat(result.tableName()).isEqualTo("user_account");
        assertThat(result.screenType()).isEqualTo("FORM");
        assertThat(result.platform()).isEqualTo("MOBILE");
        assertThat(result.issueCode()).isNull();
        assertThat(result.confidence()).isGreaterThan(0.9);
    }

    @Test
    void leavesUnboundPromptAwaitingTableBinding() {
        var result = analyzer.analyze("사용자 목록과 검색 화면", null);
        assertThat(result.hasTableBinding()).isFalse();
        assertThat(result.issueCode()).isEqualTo("TABLE_BINDING_REQUIRED");
        assertThat(result.screenType()).isEqualTo("LIST");
    }
}
