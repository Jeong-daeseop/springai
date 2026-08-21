package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.figma.contract.FigmaDesignRequestType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** R6-030: 명시 타입 우선 및 입력 문맥 fallback 계약을 고정한다. */
class FigmaDesignRequestRouterTest {

    private final FigmaDesignRequestRouter router = new FigmaDesignRequestRouter();

    @Test
    void explicitTypeWinsWhenItMatchesRequestContract() {
        assertThat(router.determineType(
                "modify_existing", "수정", false, true, false))
                .isEqualTo(FigmaDesignRequestType.MODIFY_EXISTING);
    }

    @Test
    void contextualTypeIsUsedWhenExplicitTypeIsMissing() {
        assertThat(router.determineType(
                null, "참조 화면", true, false, false))
                .isEqualTo(FigmaDesignRequestType.REFERENCE_STYLE);
        assertThat(router.determineType(
                null, "컴포넌트 화면", false, false, true))
                .isEqualTo(FigmaDesignRequestType.COMPONENT_SPECIFIED);
    }

    @Test
    void textDescriptionIsSafeFallback() {
        assertThat(router.determineType(
                null, "새 화면", false, false, false))
                .isEqualTo(FigmaDesignRequestType.TEXT_DESCRIPTION);
        assertThat(router.getConfidence(FigmaDesignRequestType.TEXT_DESCRIPTION, false))
                .isEqualTo(0.6);
    }
}
