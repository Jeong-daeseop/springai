package com.krdevops.springai.tools;

import com.krdevops.springai.model.design.DesignAnalysisResult;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.SemanticDesignCandidate;
import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.service.DesignReferenceAnalysisService;
import com.krdevops.springai.service.ScreenSpecificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DesignReferenceToolTest {

    @Mock DesignReferenceAnalysisService designReferenceAnalysisService;
    @Mock ScreenSpecificationService screenSpecificationService;

    @InjectMocks
    DesignReferenceTool tool;

    private DesignAnalysisResult analysisResult(String analysisId, UiDesignSpec uiSpec) {
        return new DesignAnalysisResult(analysisId, "sha256-abc", "/tmp/ref.png", null,
                "openai", "gpt-4o-mini", "v1", List.of(1), uiSpec, List.of(), null);
    }

    private ScreenSpecification screenSpecification(String id, ScreenSpecStatus status) {
        return new ScreenSpecification(id, 1, status, "직원목록", "crud", "CRUD_LIST",
                "com", "LETTNEMPLYRINFO", List.of(), List.of(), List.of(), null);
    }

    @Test
    void analyzeDesignReference_delegatesToService() {
        DesignAnalysisResult expected = analysisResult("analysis-1", UiDesignSpec.empty("CRUD_LIST"));
        when(designReferenceAnalysisService.analyze("/tmp/ref.png", "1-3", "crud"))
                .thenReturn(expected);

        DesignAnalysisResult result = tool.analyzeDesignReference("/tmp/ref.png", "1-3", "crud");

        assertThat(result).isSameAs(expected);
    }

    @Test
    void analyzeFigmaReference_delegatesToService() {
        DesignAnalysisResult expected = analysisResult("analysis-figma", UiDesignSpec.empty("CRUD_LIST"));
        when(designReferenceAnalysisService.analyzeFigma(
                "https://www.figma.com/design/abcdef/화면?node-id=1-2", "1:2", "crud"))
                .thenReturn(expected);

        DesignAnalysisResult result = tool.analyzeFigmaReference(
                "https://www.figma.com/design/abcdef/화면?node-id=1-2", "1:2", "crud");

        assertThat(result).isSameAs(expected);
    }

    @Test
    void findReusableDesignAnalyses_delegatesToService() {
        List<SemanticDesignCandidate> expected = List.of(
                new SemanticDesignCandidate("analysis-1", 1, "CRUD_LIST", "openai",
                        "gpt-4o-mini", "v1", true, List.of()));
        when(designReferenceAnalysisService.findReusableCandidates(
                "직원 목록 화면", "CRUD_LIST", "crud", 5))
                .thenReturn(expected);

        List<SemanticDesignCandidate> result =
                tool.findReusableDesignAnalyses("직원 목록 화면", "CRUD_LIST", "crud", 5);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void createScreenSpecification_withNullDesignAnalysisId_skipsAnalysisLookup() {
        ScreenSpecification expected = screenSpecification("spec-1", ScreenSpecStatus.APPROVED);
        when(screenSpecificationService.create("com", "LETTNEMPLYRINFO", "직원목록", "crud", null))
                .thenReturn(expected);

        ScreenSpecification result = tool.createScreenSpecification(
                "com", "LETTNEMPLYRINFO", "직원목록", "crud", null);

        assertThat(result).isSameAs(expected);
        verify(designReferenceAnalysisService, never()).get(any());
    }

    @Test
    void createScreenSpecification_withBlankDesignAnalysisId_skipsAnalysisLookup() {
        ScreenSpecification expected = screenSpecification("spec-1", ScreenSpecStatus.APPROVED);
        when(screenSpecificationService.create(eq("com"), eq("LETTNEMPLYRINFO"), eq("직원목록"),
                eq("crud"), isNull()))
                .thenReturn(expected);

        ScreenSpecification result = tool.createScreenSpecification(
                "com", "LETTNEMPLYRINFO", "직원목록", "crud", "  ");

        assertThat(result).isSameAs(expected);
        verify(designReferenceAnalysisService, never()).get(any());
    }

    @Test
    void createScreenSpecification_withDesignAnalysisId_looksUpAnalysisAndPassesUiSpec() {
        UiDesignSpec uiSpec = UiDesignSpec.empty("CRUD_LIST");
        DesignAnalysisResult analysis = analysisResult("analysis-1", uiSpec);
        ScreenSpecification expected = screenSpecification("spec-1", ScreenSpecStatus.REVIEW_REQUIRED);
        when(designReferenceAnalysisService.get("analysis-1")).thenReturn(analysis);
        when(screenSpecificationService.create("com", "LETTNEMPLYRINFO", "직원목록", "crud", uiSpec))
                .thenReturn(expected);

        ScreenSpecification result = tool.createScreenSpecification(
                "com", "LETTNEMPLYRINFO", "직원목록", "crud", "analysis-1");

        assertThat(result).isSameAs(expected);
        assertThat(result.status()).isEqualTo(ScreenSpecStatus.REVIEW_REQUIRED);
        verify(designReferenceAnalysisService).get("analysis-1");
    }

    @Test
    void createScreenSpecification_passesExplicitPageColumnsThroughNewToolContract() {
        UiDesignSpec uiSpec = UiDesignSpec.empty("CRUD_LIST");
        DesignAnalysisResult analysis = analysisResult("analysis-1", uiSpec);
        ScreenSpecification expected = screenSpecification("spec-1", ScreenSpecStatus.APPROVED);
        when(designReferenceAnalysisService.get("analysis-1")).thenReturn(analysis);
        when(screenSpecificationService.create(
                "com", "LETTNEMPLYRINFO", "직원목록", "crud", uiSpec,
                List.of("USER_NM"), List.of("EMAIL_ADRES")))
                .thenReturn(expected);

        ScreenSpecification result = tool.createScreenSpecification(
                "com", "LETTNEMPLYRINFO", "직원목록", "crud", "analysis-1",
                List.of("USER_NM"), List.of("EMAIL_ADRES"));

        assertThat(result).isSameAs(expected);
        verify(screenSpecificationService).create(
                "com", "LETTNEMPLYRINFO", "직원목록", "crud", uiSpec,
                List.of("USER_NM"), List.of("EMAIL_ADRES"));
    }

    @Test
    void approveScreenSpecification_delegatesToService() {
        ScreenSpecification expected = screenSpecification("spec-1", ScreenSpecStatus.APPROVED);
        when(screenSpecificationService.approve("spec-1")).thenReturn(expected);

        ScreenSpecification result = tool.approveScreenSpecification("spec-1");

        assertThat(result).isSameAs(expected);
    }

    @Test
    void reviseScreenSpecification_delegatesToService() {
        ScreenSpecification proposed = screenSpecification("spec-1", ScreenSpecStatus.REVIEW_REQUIRED);
        ScreenSpecification revised = screenSpecification("spec-1", ScreenSpecStatus.APPROVED);
        when(screenSpecificationService.revise(proposed)).thenReturn(revised);

        ScreenSpecification result = tool.reviseScreenSpecification(proposed);

        assertThat(result).isSameAs(revised);
    }

    @Test
    void getScreenSpecification_delegatesToService() {
        ScreenSpecification expected = screenSpecification("spec-1", ScreenSpecStatus.APPROVED);
        when(screenSpecificationService.get("spec-1")).thenReturn(expected);

        ScreenSpecification result = tool.getScreenSpecification("spec-1");

        assertThat(result).isSameAs(expected);
    }
}
