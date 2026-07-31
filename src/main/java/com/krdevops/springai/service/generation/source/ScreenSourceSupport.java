package com.krdevops.springai.service.generation.source;

import com.krdevops.springai.model.crud.CrudViewType;

/** {@link ScreenSourceGenerator} 구현체들이 공유하는 layerKey/egovVersion 계산 로직. */
final class ScreenSourceSupport {

    private ScreenSourceSupport() {
    }

    static String layerKey(CrudViewType viewType, String screenLabel) {
        return (viewType == CrudViewType.THYMELEAF ? "thymeleaf" : "jsp") + screenLabel;
    }

    static String resolveEgovVersion(String egovVersion) {
        return (egovVersion == null || egovVersion.isBlank()) ? "5.0" : egovVersion;
    }
}
