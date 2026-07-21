package com.krdevops.springai.service;

import com.krdevops.springai.model.design.DesignAnalysisResult;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GenerationDesignContextService {

    private final DesignReferenceAnalysisService designAnalysisService;
    private final ScreenSpecificationService screenSpecificationService;

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
        return specification;
    }
}
