package com.krdevops.springai.service;

import com.krdevops.springai.config.DesignVisionProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FigmaReferenceValidatorTest {

    private final DesignVisionProperties properties = new DesignVisionProperties();
    private final FigmaReferenceValidator validator = new FigmaReferenceValidator(properties);

    @Test
    void acceptsSupportedUrlAndCanonicalizesNodeId() {
        var reference = validator.validate(
                "https://www.figma.com/design/Abc_def-123/직원화면?node-id=12-34", "12:34");

        assertThat(reference.fileKey()).isEqualTo("Abc_def-123");
        assertThat(reference.nodeId()).isEqualTo("12:34");
    }

    @Test
    void rejectsHostBypassAndUnsupportedScheme() {
        assertThatThrownBy(() -> validator.validate(
                "https://www.figma.com.evil.com/design/abcdef/x?node-id=1-2", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("허용되지 않은 Figma URL입니다.");
        assertThatThrownBy(() -> validator.validate(
                "http://www.figma.com/design/abcdef/x?node-id=1-2", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("허용되지 않은 Figma URL입니다.");
    }

    @Test
    void rejectsUnsupportedFileTypeAndMismatchedNodeId() {
        assertThatThrownBy(() -> validator.validate(
                "https://www.figma.com/board/abcdef/x?node-id=1-2", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지원하지 않는 Figma 파일 유형");
        assertThatThrownBy(() -> validator.validate(
                "https://www.figma.com/file/abcdef/x?node-id=1-2", "1:3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("일치하지 않습니다");
    }

    @Test
    void enforcesConfiguredFileAllowlistWithoutLeakingKeyInError() {
        properties.getFigma().setAllowedFileKeys(List.of("allowed1"));

        assertThatThrownBy(() -> validator.validate(
                "https://www.figma.com/file/blocked1/x?node-id=1-2", null))
                .isInstanceOf(SecurityException.class)
                .hasMessage("허용되지 않은 Figma 파일입니다.")
                .hasMessageNotContaining("blocked1");
    }

    @Test
    void requiresNodeId() {
        assertThatThrownBy(() -> validator.validate(
                "https://www.figma.com/design/abcdef/x", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nodeId가 필요");
    }
}
