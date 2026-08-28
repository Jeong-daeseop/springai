package com.krdevops.springai.tools;

import com.krdevops.springai.model.design.DesignAnalysisResult;
import com.krdevops.springai.model.design.DesignFidelityReport;
import com.krdevops.springai.model.design.DesignSourceType;
import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.service.DesignFidelityComparator;
import com.krdevops.springai.service.DesignReferenceAnalysisService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DesignFidelityToolTest {

    @Test
    void delegatesLookupAndComparisonToCollaborators() {
        DesignReferenceAnalysisService analysisService = mock(DesignReferenceAnalysisService.class);
        DesignFidelityComparator comparator = mock(DesignFidelityComparator.class);
        DesignFidelityTool tool = new DesignFidelityTool(analysisService, comparator);

        UiDesignSpec originalSpec = UiDesignSpec.empty("CRUD_LIST");
        UiDesignSpec renderedSpec = UiDesignSpec.empty("CRUD_LIST");
        DesignAnalysisResult original = new DesignAnalysisResult(
                "original-1", "hash-1", "/tmp/a.png", null, DesignSourceType.FILE, null,
                "v1", UiDesignSpec.SCHEMA_VERSION, "crud", "openai", "gpt-4o-mini", "v1",
                List.of(1), originalSpec, List.of(), LocalDateTime.now(), null);
        DesignAnalysisResult rendered = new DesignAnalysisResult(
                "rendered-1", "hash-2", "/tmp/b.png", null, DesignSourceType.FILE, null,
                "v1", UiDesignSpec.SCHEMA_VERSION, "crud", "web-capture", "deterministic-mapper", "v1",
                List.of(), renderedSpec, List.of(), LocalDateTime.now(), null);
        when(analysisService.get("original-1")).thenReturn(original);
        when(analysisService.get("rendered-1")).thenReturn(rendered);
        DesignFidelityReport expected = new DesignFidelityReport(
                "original-1", "rendered-1", 1.0, 1.0, 1.0, 1.0, List.of(), List.of());
        when(comparator.compare("original-1", originalSpec, "rendered-1", renderedSpec)).thenReturn(expected);

        DesignFidelityReport result = tool.compareDesignFidelity("original-1", "rendered-1");

        assertThat(result).isEqualTo(expected);
        verify(comparator).compare("original-1", originalSpec, "rendered-1", renderedSpec);
    }
}
