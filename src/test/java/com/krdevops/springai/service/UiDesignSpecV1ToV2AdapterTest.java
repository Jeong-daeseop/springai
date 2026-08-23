package com.krdevops.springai.service;

import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.model.design.UiDesignSpecV2;
import com.krdevops.springai.model.design.UiFieldRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UiDesignSpecV1ToV2AdapterTest {

    private final UiDesignSpecV1ToV2Adapter adapter = new UiDesignSpecV1ToV2Adapter();

    @Test
    void v1의_근거_누락과_불확실성을_v2에_명시한다() {
        UiDesignSpec legacy = legacy();
        UiDesignSpecV2.Source source = new UiDesignSpecV2.Source(
                UiDesignSpecV2.SourceType.IMAGE, null, null, "image-sha-1");

        UiDesignSpecV2 converted = adapter.adapt("ui-legacy-1", legacy, source);

        assertThat(converted.nodes()).allSatisfy(node -> {
            assertThat(node.evidence().legacyUnknown()).isTrue();
            assertThat(node.evidence().requiresReview()).isTrue();
            assertThat(node.evidence().sourceNodeRefs()).isEmpty();
        });
        assertThat(converted.renderabilityAssessments())
                .allMatch(value -> value.decision() == UiDesignSpecV2.RenderabilityDecision.APPROXIMATED
                        && !value.approved());
        assertThat(converted.issues()).extracting(UiDesignSpecV2.DesignIssue::code)
                .contains("LEGACY_EVIDENCE_UNAVAILABLE", "LEGACY_UNCERTAINTY");
    }

    @Test
    void 같은_v1과_Source는_동일한_Hash를_생성한다() {
        UiDesignSpecV2.Source source = new UiDesignSpecV2.Source(
                UiDesignSpecV2.SourceType.PDF, null, null, "pdf-r1");

        UiDesignSpecV2 first = adapter.adapt("ui-1", legacy(), source);
        UiDesignSpecV2 second = adapter.adapt("ui-1", legacy(), source);

        assertThat(first.contentHash()).isEqualTo(second.contentHash());
        assertThat(first.nodes()).extracting(UiDesignSpecV2.SemanticNode::semanticId)
                .doesNotHaveDuplicates();
    }

    private UiDesignSpec legacy() {
        return new UiDesignSpec(
                "CRUD_LIST", new UiDesignSpec.LayoutSpec("default", "wide", "standard"),
                List.of(new UiDesignSpec.ComponentSpec("TABLE", List.of("목록"))),
                List.of(new UiDesignSpec.ActionSpec("SEARCH", "PRIMARY")),
                List.of(new UiDesignSpec.FieldHint(
                        "title", "제목", UiFieldRole.TITLE, "TEXT", 0.8)),
                Map.of("color", "blue"), List.of(), List.of("원본 Node 근거 없음"));
    }
}
