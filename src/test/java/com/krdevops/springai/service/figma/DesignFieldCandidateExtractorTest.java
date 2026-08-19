package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.model.design.UiFieldRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 22/23번 문서 PROP-02/S-02, doc23 T-01: 동일 입력 결정성과 빈 힌트 처리 검증. */
class DesignFieldCandidateExtractorTest {

    private final DesignFieldCandidateExtractor extractor = new DesignFieldCandidateExtractor();

    private UiDesignSpec uiSpec(List<UiDesignSpec.FieldHint> hints) {
        return new UiDesignSpec("CRUD_LIST", null, List.of(), List.of(), hints, Map.of(), List.of(), List.of());
    }

    @Test
    void extractsFieldHintsInOriginalOrder() {
        var titleHint = new UiDesignSpec.FieldHint("title", "제목", UiFieldRole.TITLE, "TEXT", 0.9);
        var authorHint = new UiDesignSpec.FieldHint("author", "작성자", UiFieldRole.AUTHOR, "TEXT", 0.8);

        var result = extractor.extract(uiSpec(List.of(titleHint, authorHint)));

        assertThat(result).containsExactly(titleHint, authorHint);
    }

    @Test
    void sameInputProducesDeterministicallyEqualOutput() {
        var hint = new UiDesignSpec.FieldHint("title", "제목", UiFieldRole.TITLE, "TEXT", 0.9);
        var spec = uiSpec(List.of(hint));

        assertThat(extractor.extract(spec)).isEqualTo(extractor.extract(spec));
    }

    @Test
    void duplicateIdKeepsOnlyFirstOccurrence() {
        var first = new UiDesignSpec.FieldHint("title", "제목", UiFieldRole.TITLE, "TEXT", 0.9);
        var duplicate = new UiDesignSpec.FieldHint("title", "제목(중복)", UiFieldRole.TITLE, "TEXT", 0.5);

        var result = extractor.extract(uiSpec(List.of(first, duplicate)));

        assertThat(result).containsExactly(first);
    }

    @Test
    void nullUiSpecReturnsEmptyList() {
        assertThat(extractor.extract(null)).isEmpty();
    }

    @Test
    void emptyFieldHintsReturnsEmptyList() {
        assertThat(extractor.extract(uiSpec(List.of()))).isEmpty();
    }
}
