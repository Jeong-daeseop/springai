package com.krdevops.springai.service;

import com.krdevops.springai.config.WebCaptureProperties;
import com.krdevops.springai.mapper.DesignAnalysisRepository;
import com.krdevops.springai.model.capture.RenderedDesignDocument;
import com.krdevops.springai.model.capture.SafeDesignProjection;
import com.krdevops.springai.model.design.DesignAnalysisResult;
import com.krdevops.springai.model.design.DesignAnalysisSaveOutcome;
import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.model.design.WebCaptureDesignSourceMetadata;
import com.krdevops.springai.policy.WebCaptureProjectionPolicy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
public class WebCaptureAnalysisService {
    private final DesignArtifactService artifactService;
    private final WebCaptureProjectionPolicy projectionPolicy;
    private final RenderedDesignSpecMapper mapper;
    private final WebCaptureCacheKeyFactory cacheKeyFactory;
    private final DesignAnalysisRepository repository;
    private final WebCaptureProperties properties;

    public WebCaptureAnalysisService(DesignArtifactService artifactService,
            WebCaptureProjectionPolicy projectionPolicy, RenderedDesignSpecMapper mapper,
            WebCaptureCacheKeyFactory cacheKeyFactory, DesignAnalysisRepository repository,
            WebCaptureProperties properties) {
        this.artifactService = artifactService;
        this.projectionPolicy = projectionPolicy;
        this.mapper = mapper;
        this.cacheKeyFactory = cacheKeyFactory;
        this.repository = repository;
        this.properties = properties;
    }

    public DesignAnalysisResult analyze(String artifactId, String featureType) {
        String normalizedFeatureType = featureType == null || featureType.isBlank()
                ? "crud" : featureType.trim().toLowerCase(Locale.ROOT);
        RenderedDesignDocument document = artifactService.readDocument(artifactId);
        String cacheKey = cacheKeyFactory.create(document.contentHash(), normalizedFeatureType,
                document.schemaVersion(), properties.getMapperVersion());
        return repository.findExact(cacheKey, "web-capture", "deterministic-mapper", properties.getMapperVersion())
                .orElseGet(() -> mapAndSave(artifactId, normalizedFeatureType, document, cacheKey));
    }

    private DesignAnalysisResult mapAndSave(String artifactId, String featureType,
                                            RenderedDesignDocument document, String cacheKey) {
        SafeDesignProjection safe = projectionPolicy.project(document);
        UiDesignSpec uiSpec = mapper.map(safe, featureType);
        DesignAnalysisResult result = DesignAnalysisResult.webCapture(
                UUID.randomUUID().toString(), cacheKey,
                new WebCaptureDesignSourceMetadata(artifactId, document.documentKey(),
                        document.contentHash(), document.schemaVersion()),
                properties.getMapperVersion(), featureType, uiSpec,
                uiSpec.uncertainties(), LocalDateTime.now());
        DesignAnalysisSaveOutcome outcome = repository.saveOrGet(result);
        // WEB_CAPTURE는 Release 1에서 의도적으로 RAG에 적재하지 않는다.
        return outcome.result();
    }
}
