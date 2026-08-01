package com.krdevops.springai.service.generation.board;

import com.krdevops.springai.service.KrdsStylesConfigurer;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BoardCssProcessorTest {

    @Test
    void cssPatchFailure_isReturnedAsProcessorFailure() {
        KrdsStylesConfigurer styles = mock(KrdsStylesConfigurer.class);
        when(styles.ensureBoardCrudStyles("/tmp/out"))
                .thenReturn(new KrdsStylesConfigurer.CssPatchResult(
                        KrdsStylesConfigurer.Status.FAILED, "/tmp/out/styles.css", "쓰기 실패"));
        var processor = new BoardCssProcessor(styles);
        GenerationContext context = new GenerationContext("board", "com", "BBS", "Notice",
                "egovframework.let.notice", "/tmp/out", "5.0", "jsp", Map.of());

        var result = processor.process(new GenerationProcessingContext(context, null, null, null));

        assertThat(result.success()).isFalse();
        assertThat(result.failureSummary()).isEqualTo("게시판 CSS 보강 실패");
        assertThat(result.failures()).singleElement().extracting("description")
                .asString().contains("styles.css");
    }
}
