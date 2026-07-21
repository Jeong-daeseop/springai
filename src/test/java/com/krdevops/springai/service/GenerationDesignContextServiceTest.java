package com.krdevops.springai.service;

import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenerationDesignContextServiceTest {

    private final DesignReferenceAnalysisService analysisService = mock(DesignReferenceAnalysisService.class);
    private final ScreenSpecificationService specificationService = mock(ScreenSpecificationService.class);
    private final GenerationDesignContextService service =
            new GenerationDesignContextService(analysisService, specificationService);

    @Test
    void resolvesApprovedSpecification() {
        ScreenSpecification approved = specification(ScreenSpecStatus.APPROVED);
        when(specificationService.get("spec-1")).thenReturn(approved);
        when(specificationService.revalidate(approved)).thenReturn(approved);

        assertThat(service.resolve("com", "LETTNBBS", "공지", "board", null, "spec-1"))
                .isSameAs(approved);
    }

    @Test
    void reviewRequiredSpecificationBlocksGeneration() {
        ScreenSpecification reviewRequired = specification(ScreenSpecStatus.REVIEW_REQUIRED);
        when(specificationService.get("spec-1")).thenReturn(reviewRequired);
        when(specificationService.revalidate(reviewRequired)).thenReturn(reviewRequired);

        assertThatThrownBy(() -> service.resolve(
                "com", "LETTNBBS", "공지", "board", null, "spec-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APPROVED");
    }

    private ScreenSpecification specification(ScreenSpecStatus status) {
        return new ScreenSpecification("spec-1", 1, status, "공지", "board", "BOARD",
                "com", "LETTNBBS", List.of(), List.of(), List.of(), LocalDateTime.now());
    }
}
