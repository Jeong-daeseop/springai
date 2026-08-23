package com.krdevops.springai.service;

import com.krdevops.springai.model.design.DesignAnalysisResult;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.config.PipelineEvolutionProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GenerationDesignContextService {

    private final DesignReferenceAnalysisService designAnalysisService;
    private final ScreenSpecificationService screenSpecificationService;
    private final PipelineEvolutionProperties pipelineEvolutionProperties;
    private final DesignContextArtifactReferenceValidator artifactReferenceValidator;

    @Autowired
    public GenerationDesignContextService(
            DesignReferenceAnalysisService designAnalysisService,
            ScreenSpecificationService screenSpecificationService,
            PipelineEvolutionProperties pipelineEvolutionProperties,
            DesignContextArtifactReferenceValidator artifactReferenceValidator) {
        this.designAnalysisService = designAnalysisService;
        this.screenSpecificationService = screenSpecificationService;
        this.pipelineEvolutionProperties = pipelineEvolutionProperties;
        this.artifactReferenceValidator = artifactReferenceValidator;
    }

    /** Pipeline Evolution 의존성 도입 전 Java 호출자·단위 테스트 호환. */
    public GenerationDesignContextService(
            DesignReferenceAnalysisService designAnalysisService,
            ScreenSpecificationService screenSpecificationService) {
        this(designAnalysisService, screenSpecificationService,
                new PipelineEvolutionProperties(), null);
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
            specification = screenSpecificationService.create(
                    database, tableName, screenName, featureType, analysis.uiSpec());
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
