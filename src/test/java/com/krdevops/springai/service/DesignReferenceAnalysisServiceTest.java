package com.krdevops.springai.service;

import com.krdevops.springai.config.DesignVisionProperties;
import com.krdevops.springai.mapper.DesignAnalysisRepository;
import com.krdevops.springai.model.design.DesignAnalysisResult;
import com.krdevops.springai.model.design.DesignAnalysisSaveOutcome;
import com.krdevops.springai.model.design.DesignSourceType;
import com.krdevops.springai.model.design.FigmaNodeDocument;
import com.krdevops.springai.model.design.FigmaReference;
import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.model.design.VisionAnalysisRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

class DesignReferenceAnalysisServiceTest {

    @Test
    void fileCacheKeySeparatesFeatureType() throws Exception {
        Path image = Files.createTempFile("design-reference-", ".png");
        Files.write(image, new byte[]{1, 2, 3});
        ReferencePathValidator pathValidator = mock(ReferencePathValidator.class);
        ImagePreprocessor preprocessor = mock(ImagePreprocessor.class);
        VisionAnalysisClient client = mock(VisionAnalysisClient.class);
        DesignAnalysisRepository repository = mock(DesignAnalysisRepository.class);
        RagService rag = mock(RagService.class);
        DesignVisionProperties properties = new DesignVisionProperties();
        when(pathValidator.validate(image.toString())).thenReturn(image);
        when(preprocessor.preprocess(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(client.providerId()).thenReturn("openai");
        when(client.modelId()).thenReturn("gpt-4o-mini");
        when(client.supportsVision()).thenReturn(true);
        when(client.analyze(any())).thenAnswer(invocation -> UiDesignSpec.empty(
                ((VisionAnalysisRequest) invocation.getArgument(0)).featureType().equals("board")
                        ? "BOARD_LIST" : "CRUD_LIST"));
        when(repository.findExact(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(repository.saveOrGet(any())).thenAnswer(invocation -> {
            DesignAnalysisResult proposed = invocation.getArgument(0);
            return new DesignAnalysisSaveOutcome(proposed, true);
        });
        DesignReferenceAnalysisService service = service(pathValidator, preprocessor, client,
                repository, rag, properties, mock(FigmaReferenceValidator.class),
                mock(FigmaApiClient.class), mock(FigmaDesignSpecMapper.class),
                mock(FigmaCacheKeyFactory.class));

        DesignAnalysisResult crud = service.analyze(image.toString(), null, "crud");
        DesignAnalysisResult board = service.analyze(image.toString(), null, "board");

        assertThat(crud.sourceHash()).isNotEqualTo(board.sourceHash());
        assertThat(crud.featureType()).isEqualTo("crud");
        assertThat(board.featureType()).isEqualTo("board");
    }

    /**
     * R6-045/R6-T09: 설정된 모델이 Vision을 지원하지 않으면 실제 API 호출(비용·rate limit 소모)
     * 전에 명확한 코드로 즉시 실패해야 한다.
     */
    @Test
    void rejectsUnsupportedVisionModelBeforeCallingClient() throws Exception {
        Path image = Files.createTempFile("design-reference-", ".png");
        Files.write(image, new byte[]{1, 2, 3});
        ReferencePathValidator pathValidator = mock(ReferencePathValidator.class);
        ImagePreprocessor preprocessor = mock(ImagePreprocessor.class);
        VisionAnalysisClient client = mock(VisionAnalysisClient.class);
        DesignAnalysisRepository repository = mock(DesignAnalysisRepository.class);
        RagService rag = mock(RagService.class);
        DesignVisionProperties properties = new DesignVisionProperties();
        when(pathValidator.validate(image.toString())).thenReturn(image);
        when(preprocessor.preprocess(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(client.providerId()).thenReturn("openai");
        when(client.modelId()).thenReturn("gpt-3.5-turbo");
        when(client.supportsVision()).thenReturn(false);
        when(repository.findExact(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        DesignReferenceAnalysisService service = service(pathValidator, preprocessor, client,
                repository, rag, properties, mock(FigmaReferenceValidator.class),
                mock(FigmaApiClient.class), mock(FigmaDesignSpecMapper.class),
                mock(FigmaCacheKeyFactory.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.analyze(image.toString(), null, "crud"))
                .isInstanceOfSatisfying(IllegalStateException.class,
                        error -> assertThat(error.getMessage()).contains("VISION_MODEL_NOT_SUPPORTED"));
        verify(client, never()).analyze(any());
        verify(repository, never()).saveOrGet(any());
    }

    /** 실제 Vision 모델 이름 판정은 알려진 접두사 목록과의 대조라는 결정론적 규칙임을 고정한다. */
    @Test
    void supportsVisionMatchesKnownVisionModelPrefixesOnly() {
        VisionAnalysisClient gpt4o = fixedProviderModel("openai", "gpt-4o-mini");
        VisionAnalysisClient gpt35 = fixedProviderModel("openai", "gpt-3.5-turbo");
        VisionAnalysisClient qwenVl = fixedProviderModel("ollama", "qwen2.5vl:7b");
        VisionAnalysisClient llama3 = fixedProviderModel("ollama", "llama3.1:8b");

        assertThat(gpt4o.supportsVision()).isTrue();
        assertThat(gpt35.supportsVision()).isFalse();
        assertThat(qwenVl.supportsVision()).isTrue();
        assertThat(llama3.supportsVision()).isFalse();
    }

    private VisionAnalysisClient fixedProviderModel(String providerId, String modelId) {
        return new VisionAnalysisClient() {
            @Override
            public UiDesignSpec analyze(VisionAnalysisRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String providerId() {
                return providerId;
            }

            @Override
            public String modelId() {
                return modelId;
            }
        };
    }

    @Test
    void figmaAnalysisUsesDeterministicContractAndSkipsVisionClient() throws Exception {
        DesignAnalysisRepository repository = mock(DesignAnalysisRepository.class);
        VisionAnalysisClient vision = mock(VisionAnalysisClient.class);
        FigmaReferenceValidator validator = mock(FigmaReferenceValidator.class);
        FigmaApiClient apiClient = mock(FigmaApiClient.class);
        FigmaDesignSpecMapper mapper = mock(FigmaDesignSpecMapper.class);
        FigmaCacheKeyFactory cacheKeys = mock(FigmaCacheKeyFactory.class);
        FigmaReference reference = new FigmaReference("abcdef", "1:2");
        FigmaNodeDocument document = new FigmaNodeDocument("version-1",
                new ObjectMapper().readTree("{\"type\":\"FRAME\",\"name\":\"목록\"}"));
        UiDesignSpec mapped = UiDesignSpec.empty("CRUD_LIST");
        DesignVisionProperties properties = new DesignVisionProperties();
        properties.getFigma().setEnabled(true);
        properties.getFigma().setAccessToken("secret-token");
        when(validator.validate("https://www.figma.com/design/abcdef/x?node-id=1-2", null))
                .thenReturn(reference);
        when(apiClient.fetchNode(reference)).thenReturn(document);
        when(cacheKeys.create(reference, "version-1", "crud")).thenReturn("figma-hash");
        when(repository.findExact("figma-hash", "figma", "deterministic-mapper", "figma-mapper-v2"))
                .thenReturn(Optional.empty());
        when(mapper.map(document, "crud")).thenReturn(mapped);
        when(repository.saveOrGet(any())).thenAnswer(invocation -> {
            DesignAnalysisResult proposed = invocation.getArgument(0);
            return new DesignAnalysisSaveOutcome(proposed, true);
        });
        DesignReferenceAnalysisService service = service(mock(ReferencePathValidator.class),
                mock(ImagePreprocessor.class), vision, repository, mock(RagService.class), properties,
                validator, apiClient, mapper, cacheKeys);

        DesignAnalysisResult result = service.analyzeFigma(
                "https://www.figma.com/design/abcdef/x?node-id=1-2", null, " CRUD ");

        assertThat(result.sourceType()).isEqualTo(DesignSourceType.FIGMA);
        assertThat(result.figmaSource().fileVersion()).isEqualTo("version-1");
        assertThat(result.provider()).isEqualTo("figma");
        assertThat(result.model()).isEqualTo("deterministic-mapper");
        assertThat(result.analysisContractVersion()).isEqualTo("figma-mapper-v2");
        verify(vision, never()).analyze(any());
    }

    @Test
    void figmaAnalysisInfersMasterDetailFeatureTypeWhenOmitted() throws Exception {
        DesignAnalysisRepository repository = mock(DesignAnalysisRepository.class);
        VisionAnalysisClient vision = mock(VisionAnalysisClient.class);
        FigmaReferenceValidator validator = mock(FigmaReferenceValidator.class);
        FigmaApiClient apiClient = mock(FigmaApiClient.class);
        FigmaDesignSpecMapper mapper = mock(FigmaDesignSpecMapper.class);
        FigmaCacheKeyFactory cacheKeys = mock(FigmaCacheKeyFactory.class);
        FigmaReference reference = new FigmaReference("abcdef", "1:2");
        FigmaNodeDocument document = new FigmaNodeDocument("version-1",
                new ObjectMapper().readTree("{\"type\":\"FRAME\",\"name\":\"마스터 상세\"}"));
        UiDesignSpec mapped = UiDesignSpec.empty("MASTER_DETAIL");
        DesignVisionProperties properties = new DesignVisionProperties();
        properties.getFigma().setEnabled(true);
        properties.getFigma().setAccessToken("secret-token");
        when(validator.validate("https://www.figma.com/design/abcdef/x?node-id=1-2", null))
                .thenReturn(reference);
        when(apiClient.fetchNode(reference)).thenReturn(document);
        when(cacheKeys.create(reference, "version-1", "crud")).thenReturn("figma-hash");
        when(repository.findExact("figma-hash", "figma", "deterministic-mapper", "figma-mapper-v2"))
                .thenReturn(Optional.empty());
        when(mapper.map(document, "crud")).thenReturn(mapped);
        when(repository.saveOrGet(any())).thenAnswer(invocation -> {
            DesignAnalysisResult proposed = invocation.getArgument(0);
            return new DesignAnalysisSaveOutcome(proposed, true);
        });
        DesignReferenceAnalysisService service = service(mock(ReferencePathValidator.class),
                mock(ImagePreprocessor.class), vision, repository, mock(RagService.class), properties,
                validator, apiClient, mapper, cacheKeys);

        DesignAnalysisResult result = service.analyzeFigma(
                "https://www.figma.com/design/abcdef/x?node-id=1-2", null, null);

        assertThat(result.featureType()).isEqualTo("master-detail");
    }

    @Test
    void semanticCandidateIsReusableOnlyWhenExecutionContractMatches() {
        DesignAnalysisRepository repository = mock(DesignAnalysisRepository.class);
        RagService rag = mock(RagService.class);
        VisionAnalysisClient client = mock(VisionAnalysisClient.class);
        DesignVisionProperties properties = new DesignVisionProperties();
        properties.setPromptVersion("v1");
        when(client.providerId()).thenReturn("openai");
        when(client.modelId()).thenReturn("gpt-4o-mini");
        when(rag.search("게시판 목록", 5)).thenReturn(List.of(new Document(
                "분석ID: analysis-1 | archetype: BOARD_LIST",
                Map.of("type", "design_analysis"))));
        when(repository.findById("analysis-1")).thenReturn(Optional.of(analysis("BOARD_LIST", "v1")));
        DesignReferenceAnalysisService service = new DesignReferenceAnalysisService(
                mock(ReferencePathValidator.class), mock(PdfPageRasterizer.class),
                mock(ImagePreprocessor.class), client, repository, rag, properties,
                mock(FigmaReferenceValidator.class), mock(FigmaApiClient.class),
                mock(FigmaDesignSpecMapper.class), mock(FigmaCacheKeyFactory.class));

        var candidates = service.findReusableCandidates("게시판 목록", "BOARD_LIST", 5);

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.reusable()).isTrue();
            assertThat(candidate.rejectionReasons()).isEmpty();
        });
    }

    @Test
    void rejectsSemanticCandidateWhenPromptOrArchetypeDiffers() {
        DesignAnalysisRepository repository = mock(DesignAnalysisRepository.class);
        RagService rag = mock(RagService.class);
        VisionAnalysisClient client = mock(VisionAnalysisClient.class);
        DesignVisionProperties properties = new DesignVisionProperties();
        properties.setPromptVersion("v2");
        when(client.providerId()).thenReturn("openai");
        when(client.modelId()).thenReturn("gpt-4o-mini");
        when(rag.search("화면", 3)).thenReturn(List.of(new Document(
                "분석ID: analysis-1 | archetype: BOARD_LIST",
                Map.of("type", "design_analysis"))));
        when(repository.findById("analysis-1")).thenReturn(Optional.of(analysis("BOARD_LIST", "v1")));
        DesignReferenceAnalysisService service = new DesignReferenceAnalysisService(
                mock(ReferencePathValidator.class), mock(PdfPageRasterizer.class),
                mock(ImagePreprocessor.class), client, repository, rag, properties,
                mock(FigmaReferenceValidator.class), mock(FigmaApiClient.class),
                mock(FigmaDesignSpecMapper.class), mock(FigmaCacheKeyFactory.class));

        var candidate = service.findReusableCandidates("화면", "CRUD_LIST", 3).get(0);

        assertThat(candidate.reusable()).isFalse();
        assertThat(candidate.rejectionReasons())
                .contains("archetype 불일치", "featureType 불일치", "promptVersion 불일치");
    }

    @Test
    void rejectsSemanticCandidateWhenUiSpecSchemaVersionDiffers() {
        DesignAnalysisRepository repository = mock(DesignAnalysisRepository.class);
        RagService rag = mock(RagService.class);
        VisionAnalysisClient client = mock(VisionAnalysisClient.class);
        DesignVisionProperties properties = new DesignVisionProperties();
        when(client.providerId()).thenReturn("openai");
        when(client.modelId()).thenReturn("gpt-4o-mini");
        when(rag.search("화면", 3)).thenReturn(List.of(new Document(
                "분석ID: analysis-1 | archetype: CRUD_LIST",
                Map.of("type", "design_analysis"))));
        DesignAnalysisResult incompatible = new DesignAnalysisResult(
                "analysis-1", "hash", "/tmp/ref.png", null, DesignSourceType.FILE, null,
                "v1", "ui-design-spec-v0", "crud", "openai", "gpt-4o-mini", "v1",
                List.of(1), UiDesignSpec.empty("CRUD_LIST"), List.of(), LocalDateTime.now());
        when(repository.findById("analysis-1")).thenReturn(Optional.of(incompatible));
        DesignReferenceAnalysisService service = service(mock(ReferencePathValidator.class),
                mock(ImagePreprocessor.class), client, repository, rag, properties,
                mock(FigmaReferenceValidator.class), mock(FigmaApiClient.class),
                mock(FigmaDesignSpecMapper.class), mock(FigmaCacheKeyFactory.class));

        var candidate = service.findReusableCandidates("화면", "CRUD_LIST", 3).get(0);

        assertThat(candidate.reusable()).isFalse();
        assertThat(candidate.rejectionReasons()).contains("UiDesignSpec schema version 불일치");
    }

    private DesignAnalysisResult analysis(String archetype, String promptVersion) {
        return new DesignAnalysisResult(
                "analysis-1", "hash", "/tmp/ref.png", null, "openai", "gpt-4o-mini",
                promptVersion, List.of(1), UiDesignSpec.empty(archetype), List.of(), LocalDateTime.now());
    }

    private DesignReferenceAnalysisService service(
            ReferencePathValidator pathValidator, ImagePreprocessor preprocessor,
            VisionAnalysisClient vision, DesignAnalysisRepository repository, RagService rag,
            DesignVisionProperties properties, FigmaReferenceValidator figmaValidator,
            FigmaApiClient figmaClient, FigmaDesignSpecMapper mapper,
            FigmaCacheKeyFactory cacheKeys) {
        return new DesignReferenceAnalysisService(pathValidator, mock(PdfPageRasterizer.class),
                preprocessor, vision, repository, rag, properties, figmaValidator,
                figmaClient, mapper, cacheKeys);
    }
}
