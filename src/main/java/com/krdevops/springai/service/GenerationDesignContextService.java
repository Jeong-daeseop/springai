package com.krdevops.springai.service;

import com.krdevops.springai.model.design.DesignAnalysisResult;
import com.krdevops.springai.model.design.DesignSourceType;
import com.krdevops.springai.model.design.FigmaDesignSourceMetadata;
import com.krdevops.springai.model.design.FileDesignSourceMetadata;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.UiDesignSpecV2;
import com.krdevops.springai.model.contract.VersionedArtifactReference;
import com.krdevops.springai.config.PipelineEvolutionProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class GenerationDesignContextService {

    private final DesignReferenceAnalysisService designAnalysisService;
    private final ScreenSpecificationService screenSpecificationService;
    private final PipelineEvolutionProperties pipelineEvolutionProperties;
    private final DesignContextArtifactReferenceValidator artifactReferenceValidator;
    private final UiDesignSpecV1ToV2Adapter uiDesignSpecV1ToV2Adapter;
    private final UiDesignSpecV2ArtifactWriter uiDesignSpecV2ArtifactWriter;

    @Autowired
    public GenerationDesignContextService(
            DesignReferenceAnalysisService designAnalysisService,
            ScreenSpecificationService screenSpecificationService,
            PipelineEvolutionProperties pipelineEvolutionProperties,
            DesignContextArtifactReferenceValidator artifactReferenceValidator,
            UiDesignSpecV1ToV2Adapter uiDesignSpecV1ToV2Adapter,
            UiDesignSpecV2ArtifactWriter uiDesignSpecV2ArtifactWriter) {
        this.designAnalysisService = designAnalysisService;
        this.screenSpecificationService = screenSpecificationService;
        this.pipelineEvolutionProperties = pipelineEvolutionProperties;
        this.artifactReferenceValidator = artifactReferenceValidator;
        this.uiDesignSpecV1ToV2Adapter = uiDesignSpecV1ToV2Adapter;
        this.uiDesignSpecV2ArtifactWriter = uiDesignSpecV2ArtifactWriter;
    }

    /** V2 배선 도입 전 Java 호출자·단위 테스트 호환. */
    public GenerationDesignContextService(
            DesignReferenceAnalysisService designAnalysisService,
            ScreenSpecificationService screenSpecificationService,
            PipelineEvolutionProperties pipelineEvolutionProperties,
            DesignContextArtifactReferenceValidator artifactReferenceValidator) {
        this(designAnalysisService, screenSpecificationService, pipelineEvolutionProperties,
                artifactReferenceValidator, null, null);
    }

    /** Pipeline Evolution 의존성 도입 전 Java 호출자·단위 테스트 호환. */
    public GenerationDesignContextService(
            DesignReferenceAnalysisService designAnalysisService,
            ScreenSpecificationService screenSpecificationService) {
        this(designAnalysisService, screenSpecificationService,
                new PipelineEvolutionProperties(), null, null, null);
    }

    public ScreenSpecification resolve(
            String database,
            String tableName,
            String screenName,
            String featureType,
            String designReferenceId,
            String screenSpecificationId) {
        ScreenSpecification specification = null;
        if (screenSpecificationId != null && !screenSpecificationId.isBlank()) {
            specification = screenSpecificationService.get(screenSpecificationId);
        } else if (designReferenceId != null && !designReferenceId.isBlank()) {
            DesignAnalysisResult analysis = designAnalysisService.get(designReferenceId);
            if (shouldUseV2Path()) {
                UiDesignSpecV2 v2 = uiDesignSpecV1ToV2Adapter.adapt(
                        analysis.analysisId(), analysis.uiSpec(), toV2Source(analysis));
                VersionedArtifactReference designRef = uiDesignSpecV2ArtifactWriter.write(v2);
                specification = screenSpecificationService.createFromV2(
                        database, tableName, screenName, featureType, v2, designRef, null, null);
            } else {
                specification = screenSpecificationService.create(
                        database, tableName, screenName, featureType, analysis.uiSpec());
            }
        }
        if (specification == null) return null;
        specification = screenSpecificationService.revalidate(specification);
        if (!database.equalsIgnoreCase(specification.database())
                || !tableName.equalsIgnoreCase(specification.primaryTable())) {
            throw new IllegalArgumentException("화면명세의 데이터 소스가 생성 대상과 다릅니다: "
                    + specification.database() + "." + specification.primaryTable());
        }
        if (specification.status() != ScreenSpecStatus.APPROVED) {
            throw new IllegalStateException("APPROVED 화면명세만 코드 생성에 사용할 수 있습니다: "
                    + specification.id() + " (" + specification.status() + ")");
        }
        validateArtifactReferences(specification);
        return specification;
    }

    /**
     * 디자인 참조에서 화면명세를 만들 때 v1 → v2 어댑터 + Artifact 영속화 경로를 쓸지 여부.
     * V2_PREVIEW/V2_APPLY에서만 활성화한다 — DISABLED/OBSERVE/DUAL_READ는 기존 v1 create()를
     * 유지해 마이그레이션 가드(PipelineMigrationGuard/LegacyCompatibilityService)와 충돌하지 않는다.
     */
    private boolean shouldUseV2Path() {
        return uiDesignSpecV1ToV2Adapter != null && uiDesignSpecV2ArtifactWriter != null
                && pipelineEvolutionProperties.usesV2Preview();
    }

    private static UiDesignSpecV2.Source toV2Source(DesignAnalysisResult analysis) {
        String fallbackRevision = analysis.sourceHash();
        if (analysis.sourceType() == DesignSourceType.FIGMA
                && analysis.sourceMetadata() instanceof FigmaDesignSourceMetadata figma) {
            String revision = figma.fileVersion() != null && !figma.fileVersion().isBlank()
                    ? figma.fileVersion() : fallbackRevision;
            return new UiDesignSpecV2.Source(
                    UiDesignSpecV2.SourceType.FIGMA, figma.fileKey(), figma.nodeId(), revision);
        }
        if (analysis.sourceType() == DesignSourceType.WEB_CAPTURE) {
            return new UiDesignSpecV2.Source(
                    UiDesignSpecV2.SourceType.WEB_CAPTURE, null, null, fallbackRevision);
        }
        UiDesignSpecV2.SourceType type = UiDesignSpecV2.SourceType.IMAGE;
        if (analysis.sourceMetadata() instanceof FileDesignSourceMetadata file
                && file.sourcePath() != null
                && file.sourcePath().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            type = UiDesignSpecV2.SourceType.PDF;
        }
        return new UiDesignSpecV2.Source(type, null, null, fallbackRevision);
    }

    private void validateArtifactReferences(ScreenSpecification specification) {
        boolean mandatory = pipelineEvolutionProperties.usesV2Apply();
        boolean validateWhenPresent = pipelineEvolutionProperties.readsV2Artifacts();
        if (!mandatory && !validateWhenPresent) return;
        if (artifactReferenceValidator == null) {
            if (mandatory) {
                throw new IllegalStateException("V2 Apply Artifact 검증기가 구성되지 않았습니다.");
            }
            return;
        }
        if (specification.uiDesignSpecReference() == null) {
            if (mandatory) {
                throw new DesignContextArtifactReferenceValidator.DesignContextArtifactException(
                        com.krdevops.springai.model.contract.PipelineEvolutionErrorCode.DESIGN_EVIDENCE_MISSING,
                        "V2 Apply에는 UiDesignSpec Artifact 참조가 필요합니다.");
            }
            return;
        }
        artifactReferenceValidator.requireActiveExact(specification.uiDesignSpecReference());
        if (specification.designSystemSnapshotReference() != null) {
            artifactReferenceValidator.requireActiveExact(
                    specification.designSystemSnapshotReference());
        }
    }
}
