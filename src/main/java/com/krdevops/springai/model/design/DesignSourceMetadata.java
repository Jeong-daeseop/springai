package com.krdevops.springai.model.design;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** 디자인 분석 입력 종류별 메타데이터의 공통 계약. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "sourceType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = FileDesignSourceMetadata.class, name = "FILE"),
        @JsonSubTypes.Type(value = FigmaDesignSourceMetadata.class, name = "FIGMA"),
        @JsonSubTypes.Type(value = WebCaptureDesignSourceMetadata.class, name = "WEB_CAPTURE")
})
public sealed interface DesignSourceMetadata permits FileDesignSourceMetadata,
        FigmaDesignSourceMetadata, WebCaptureDesignSourceMetadata {

    DesignSourceType sourceType();
}
