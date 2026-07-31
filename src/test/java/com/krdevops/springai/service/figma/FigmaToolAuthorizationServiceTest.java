package com.krdevops.springai.service.figma;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FigmaToolAuthorizationServiceTest {

    @Test
    void acceptsOnlyConfiguredSharedSecret() {
        FigmaToolAuthorizationService service = new FigmaToolAuthorizationService("figma-secret");

        assertThatCode(() -> service.authorize("figma-secret")).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.authorize("wrong"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("실패");
        assertThatThrownBy(() -> service.authorize(null))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void blankServerSecretDisablesTheToolsSecurely() {
        FigmaToolAuthorizationService service = new FigmaToolAuthorizationService("");

        assertThatThrownBy(() -> service.authorize(""))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("설정되지 않았습니다");
    }
}
