package com.krdevops.springai.model.figma.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * I-1: Figma 디자인 요청의 통일된 계약.
 * 7가지 요청 유형 모두 이 구조로 통합됨.
 * 명세서 §5.1 기반.
 */
public record FigmaDesignRequest(
        @JsonProperty("type")
        FigmaDesignRequestType type,

        @JsonProperty("prompt")
        String prompt,

        @JsonProperty("fileKey")
        String fileKey,

        @JsonProperty("referenceNodeIds")
        List<String> referenceNodeIds,

        @JsonProperty("editableNodeIds")
        List<String> editableNodeIds,

        @JsonProperty("imageNodeIds")
        List<String> imageNodeIds,

        @JsonProperty("targetPlatform")
        String targetPlatform,

        @JsonProperty("components")
        List<String> components,

        @JsonProperty("screens")
        List<FigmaScreenRequest> screens,

        /**
         * R6-032~038(2026-08-18): 실제 Bundle 생성은 {@code ScreenSpecAssembler}를 거치므로
         * DB 테이블에 바인딩돼야 한다(기존 CRUD 생성 아키텍처와 통일). REFERENCE_STYLE·
         * IMAGE_REFERENCE·COMPONENT_SPECIFIED·단일 화면 MULTI_SCREEN_FLOW 항목에 필요하다.
         */
        @JsonProperty("database")
        String database,

        @JsonProperty("tableName")
        String tableName,

        @JsonProperty("screenName")
        String screenName,

        @JsonProperty("featureType")
        String featureType,

        /**
         * MODIFY_EXISTING·PLATFORM_CONVERT가 대상으로 삼는 기존 화면명세. Figma nodeId →
         * ScreenSpecification 자동 매핑 테이블이 없어(별도 기능), 호출자가 직접 지정해야 한다.
         */
        @JsonProperty("screenSpecificationId")
        String screenSpecificationId,

        /** R6-T08: 참조/수정 대상 노드가 속해야 하는 Figma Page ID. 기존 요청은 생략 가능하다. */
        @JsonProperty("pageId")
        String pageId
) {
    public FigmaDesignRequest {
        if (type == null) {
            throw new IllegalArgumentException("type은 필수입니다");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt는 필수입니다");
        }
        if (fileKey == null || fileKey.isBlank()) {
            throw new IllegalArgumentException("fileKey는 필수입니다");
        }
    }

    /** DB 바인딩 필드 도입(2026-08-18) 전 호출자 호환. */
    public FigmaDesignRequest(
            FigmaDesignRequestType type, String prompt, String fileKey,
            List<String> referenceNodeIds, List<String> editableNodeIds, List<String> imageNodeIds,
            String targetPlatform, List<String> components, List<FigmaScreenRequest> screens) {
        this(type, prompt, fileKey, referenceNodeIds, editableNodeIds, imageNodeIds,
                targetPlatform, components, screens, null, null, null, null, null);
    }

    /** pageId 도입 전 canonical 생성 호출자 호환용 생성자. */
    public FigmaDesignRequest(
            FigmaDesignRequestType type, String prompt, String fileKey,
            List<String> referenceNodeIds, List<String> editableNodeIds, List<String> imageNodeIds,
            String targetPlatform, List<String> components, List<FigmaScreenRequest> screens,
            String database, String tableName, String screenName, String featureType,
            String screenSpecificationId) {
        this(type, prompt, fileKey, referenceNodeIds, editableNodeIds, imageNodeIds,
                targetPlatform, components, screens, database, tableName, screenName, featureType,
                screenSpecificationId, null);
    }

    /** 새 필드를 채운 동일 요청 사본을 만든다(정규화 등 부분 갱신용). */
    public FigmaDesignRequest withNodeIds(
            List<String> referenceNodeIds, List<String> editableNodeIds, List<String> imageNodeIds) {
        return new FigmaDesignRequest(type, prompt, fileKey, referenceNodeIds, editableNodeIds, imageNodeIds,
                targetPlatform, components, screens, database, tableName, screenName, featureType,
                screenSpecificationId, pageId);
    }

    /** R6-T08: page 소속 검증 대상 Page를 명시한 동일 요청 사본. */
    public FigmaDesignRequest withPageId(String pageId) {
        return new FigmaDesignRequest(type, prompt, fileKey, referenceNodeIds, editableNodeIds, imageNodeIds,
                targetPlatform, components, screens, database, tableName, screenName, featureType,
                screenSpecificationId, pageId);
    }

    /**
     * 22/23번 문서 A-01a: {@code AWAITING_TABLE_BINDING}에서 {@code bindFigmaDesignRequestTable}로
     * database/tableName만 채워 넣은 동일 요청 사본을 만든다.
     */
    public FigmaDesignRequest withDatabaseTable(String database, String tableName) {
        return new FigmaDesignRequest(type, prompt, fileKey, referenceNodeIds, editableNodeIds, imageNodeIds,
                targetPlatform, components, screens, database, tableName, screenName, featureType,
                screenSpecificationId, pageId);
    }

    /**
     * TEXT_DESCRIPTION 요청 생성 헬퍼
     */
    public static FigmaDesignRequest textDescription(String prompt, String fileKey) {
        return new FigmaDesignRequest(
                FigmaDesignRequestType.TEXT_DESCRIPTION,
                prompt, fileKey,
                null, null, null, null, null, null
        );
    }

    /** R6-032: 자연어 분석기가 명시적으로 추출한 DB 바인딩을 보존한다. */
    public static FigmaDesignRequest textDescription(
            String prompt, String fileKey, String database, String tableName,
            String screenName, String featureType) {
        return new FigmaDesignRequest(
                FigmaDesignRequestType.TEXT_DESCRIPTION, prompt, fileKey,
                null, null, null, null, null, null,
                database, tableName, screenName, featureType, null);
    }

    /**
     * REFERENCE_STYLE 요청 생성 헬퍼
     */
    public static FigmaDesignRequest referenceStyle(String prompt, String fileKey, List<String> nodeIds) {
        return new FigmaDesignRequest(
                FigmaDesignRequestType.REFERENCE_STYLE,
                prompt, fileKey,
                nodeIds, null, null, null, null, null
        );
    }

    /** R6-033: 참조 Figma 화면을 실제 Bundle까지 생성하려면 DB 테이블이 필요하다. */
    public static FigmaDesignRequest referenceStyle(
            String prompt, String fileKey, List<String> nodeIds,
            String database, String tableName, String screenName, String featureType) {
        return new FigmaDesignRequest(
                FigmaDesignRequestType.REFERENCE_STYLE,
                prompt, fileKey, nodeIds, null, null, null, null, null,
                database, tableName, screenName, featureType, null
        );
    }

    /**
     * MODIFY_EXISTING 요청 생성 헬퍼
     */
    public static FigmaDesignRequest modifyExisting(String prompt, String fileKey, List<String> editableNodeIds) {
        return new FigmaDesignRequest(
                FigmaDesignRequestType.MODIFY_EXISTING,
                prompt, fileKey,
                null, editableNodeIds, null, null, null, null
        );
    }

    /** R6-034: 어떤 기존 화면명세를 재생성할지 명시한다. */
    public static FigmaDesignRequest modifyExisting(
            String prompt, String fileKey, List<String> editableNodeIds, String screenSpecificationId) {
        return new FigmaDesignRequest(
                FigmaDesignRequestType.MODIFY_EXISTING,
                prompt, fileKey, null, editableNodeIds, null, null, null, null,
                null, null, null, null, screenSpecificationId
        );
    }

    /**
     * IMAGE_REFERENCE 요청 생성 헬퍼
     */
    public static FigmaDesignRequest imageReference(String prompt, String fileKey, List<String> imageNodeIds) {
        return new FigmaDesignRequest(
                FigmaDesignRequestType.IMAGE_REFERENCE,
                prompt, fileKey,
                null, null, imageNodeIds, null, null, null
        );
    }

    /** R6-035: 이미지 노드를 분석해 실제 Bundle까지 생성하려면 DB 테이블이 필요하다. */
    public static FigmaDesignRequest imageReference(
            String prompt, String fileKey, List<String> imageNodeIds,
            String database, String tableName, String screenName, String featureType) {
        return new FigmaDesignRequest(
                FigmaDesignRequestType.IMAGE_REFERENCE,
                prompt, fileKey, null, null, imageNodeIds, null, null, null,
                database, tableName, screenName, featureType, null
        );
    }

    /**
     * MULTI_SCREEN_FLOW 요청 생성 헬퍼
     */
    public static FigmaDesignRequest multiScreenFlow(String prompt, String fileKey, List<FigmaScreenRequest> screens) {
        return new FigmaDesignRequest(
                FigmaDesignRequestType.MULTI_SCREEN_FLOW,
                prompt, fileKey,
                null, null, null, null, null, screens
        );
    }

    /**
     * COMPONENT_SPECIFIED 요청 생성 헬퍼
     */
    public static FigmaDesignRequest componentSpecified(String prompt, String fileKey, List<String> componentNames) {
        return new FigmaDesignRequest(
                FigmaDesignRequestType.COMPONENT_SPECIFIED,
                prompt, fileKey,
                null, null, null, null, componentNames, null
        );
    }

    /** R6-037: 지정 컴포넌트를 실제로 배치한 Bundle까지 생성하려면 DB 테이블이 필요하다. */
    public static FigmaDesignRequest componentSpecified(
            String prompt, String fileKey, List<String> componentNames,
            String database, String tableName, String screenName, String featureType) {
        return new FigmaDesignRequest(
                FigmaDesignRequestType.COMPONENT_SPECIFIED,
                prompt, fileKey, null, null, null, null, componentNames, null,
                database, tableName, screenName, featureType, null
        );
    }

    /**
     * PLATFORM_CONVERT 요청 생성 헬퍼
     */
    public static FigmaDesignRequest platformConvert(String prompt, String fileKey, List<String> sourceNodeIds, String targetPlatform) {
        return new FigmaDesignRequest(
                FigmaDesignRequestType.PLATFORM_CONVERT,
                prompt, fileKey,
                sourceNodeIds, null, null, targetPlatform, null, null
        );
    }

    /** R6-038: 어떤 기존 화면명세를 변환할지 명시한다. */
    public static FigmaDesignRequest platformConvert(
            String prompt, String fileKey, List<String> sourceNodeIds, String targetPlatform,
            String screenSpecificationId) {
        return new FigmaDesignRequest(
                FigmaDesignRequestType.PLATFORM_CONVERT,
                prompt, fileKey, sourceNodeIds, null, null, targetPlatform, null, null,
                null, null, null, null, screenSpecificationId
        );
    }
}
