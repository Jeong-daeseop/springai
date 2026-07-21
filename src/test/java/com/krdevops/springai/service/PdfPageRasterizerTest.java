package com.krdevops.springai.service;

import com.krdevops.springai.config.DesignVisionProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfPageRasterizerTest {

    private final PdfPageRasterizer rasterizer = new PdfPageRasterizer(new DesignVisionProperties());

    @Test
    void parsesOneBasedRangesAndRemovesDuplicates() {
        assertThat(rasterizer.parsePageRange("1-3,3,5", 6))
                .containsExactly(1, 2, 3, 5);
    }

    @Test
    void rejectsOutOfRangePage() {
        assertThatThrownBy(() -> rasterizer.parsePageRange("7", 6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("범위를 벗어났습니다");
    }
}
