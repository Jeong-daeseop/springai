package com.krdevops.springai.model.figma.hybrid;

import java.util.List;

/** 원본 document.json에서 DB Binding 가능한 ScreenSpecification 후보를 만드는 입력. */
public record FigmaHybridCandidateRequest(
        String artifactId,
        String database,
        String primaryTable,
        String screenName,
        String featureType,
        List<String> listColumns,
        List<String> detailColumns
) {
    public FigmaHybridCandidateRequest {
        if (artifactId == null || artifactId.isBlank()) {
            throw new IllegalArgumentException("artifactId는 필수입니다.");
        }
        if (database == null || database.isBlank()) {
            throw new IllegalArgumentException("database는 사람 확인 필수 입력입니다.");
        }
        if (primaryTable == null || primaryTable.isBlank()) {
            throw new IllegalArgumentException("primaryTable은 사람 확인 필수 입력입니다.");
        }
        if (screenName == null || screenName.isBlank()) {
            throw new IllegalArgumentException("screenName은 사람 확인 필수 입력입니다.");
        }
        featureType = featureType == null || featureType.isBlank() ? "crud" : featureType;
        listColumns = listColumns == null ? List.of() : List.copyOf(listColumns);
        detailColumns = detailColumns == null ? List.of() : List.copyOf(detailColumns);
    }
}
