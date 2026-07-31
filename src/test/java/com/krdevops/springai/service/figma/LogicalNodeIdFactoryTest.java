package com.krdevops.springai.service.figma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LogicalNodeIdFactoryTest {

    private final LogicalNodeIdFactory factory = new LogicalNodeIdFactory();

    @Test
    void 동일한_입력은_항상_동일한_ID를_생성한다() {
        assertThat(factory.page("user-list")).isEqualTo("user-list");
        assertThat(factory.section("user-list", "search")).isEqualTo("user-list/search");
        assertThat(factory.field("user-list", "search", "userId"))
                .isEqualTo("user-list/search/userId");
        assertThat(factory.action("user-list", "CREATE"))
                .isEqualTo("user-list/action/create");
    }

    @Test
    void 경로_충돌을_유발하는_세그먼트를_거부한다() {
        assertThat(factory.section("user-list", "table/row"))
                .isEqualTo("user-list/table/row");
        assertThatThrownBy(() -> factory.section("user-list", "search//query"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("section");
        assertThatThrownBy(() -> factory.field("user-list", "form", "사용자명"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fieldId");
        assertThatThrownBy(() -> factory.page(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageId");
    }
}
