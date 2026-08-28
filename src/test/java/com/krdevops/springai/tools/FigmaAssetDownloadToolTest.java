package com.krdevops.springai.tools;

import com.krdevops.springai.model.design.DesignAnalysisResult;
import com.krdevops.springai.model.design.DesignSourceType;
import com.krdevops.springai.model.design.FigmaSource;
import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.service.DesignReferenceAnalysisService;
import com.krdevops.springai.service.FigmaAssetDownloadService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FigmaAssetDownloadToolTest {

    @Test
    void delegatesFileKeyAndImageNodeIdsFromFigmaAnalysis() {
        DesignReferenceAnalysisService analysisService = mock(DesignReferenceAnalysisService.class);
        FigmaAssetDownloadService downloadService = mock(FigmaAssetDownloadService.class);
        FigmaAssetDownloadTool tool = new FigmaAssetDownloadTool(analysisService, downloadService);

        UiDesignSpec uiSpec = new UiDesignSpec("CRUD_LIST", null, List.of(), List.of(), List.of(),
                java.util.Map.of(), List.of(), List.of(), List.of(), List.of("1:2", "1:3"));
        DesignAnalysisResult analysis = new DesignAnalysisResult(
                "analysis-1", "hash-1", "figma://abc#1:1", null, DesignSourceType.FIGMA,
                new FigmaSource("abc", "1:1", "version-1"),
                "v1", UiDesignSpec.SCHEMA_VERSION, "crud", "figma", "deterministic-mapper", "v1",
                List.of(), uiSpec, List.of(), LocalDateTime.now(), null);
        when(analysisService.get("analysis-1")).thenReturn(analysis);
        when(downloadService.downloadAssets("abc", List.of("1:2", "1:3"), "/tmp/out"))
                .thenReturn(List.of("src/main/resources/static/images/figma/1-2.png"));

        List<String> saved = tool.downloadFigmaAssets("analysis-1", "/tmp/out");

        assertThat(saved).containsExactly("src/main/resources/static/images/figma/1-2.png");
    }

    @Test
    void rejectsNonFigmaAnalysis() {
        DesignReferenceAnalysisService analysisService = mock(DesignReferenceAnalysisService.class);
        FigmaAssetDownloadService downloadService = mock(FigmaAssetDownloadService.class);
        FigmaAssetDownloadTool tool = new FigmaAssetDownloadTool(analysisService, downloadService);

        UiDesignSpec uiSpec = UiDesignSpec.empty("CRUD_LIST");
        DesignAnalysisResult fileAnalysis = new DesignAnalysisResult(
                "analysis-2", "hash-2", "/tmp/ref.png", null, DesignSourceType.FILE, null,
                "v1", UiDesignSpec.SCHEMA_VERSION, "crud", "openai", "gpt-4o-mini", "v1",
                List.of(1), uiSpec, List.of(), LocalDateTime.now(), null);
        when(analysisService.get("analysis-2")).thenReturn(fileAnalysis);

        assertThatThrownBy(() -> tool.downloadFigmaAssets("analysis-2", "/tmp/out"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("analysis-2");
    }
}
