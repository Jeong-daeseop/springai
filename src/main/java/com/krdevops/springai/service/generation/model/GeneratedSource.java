package com.krdevops.springai.service.generation.model;

import com.krdevops.springai.model.crud.CrudViewType;

import java.nio.file.Path;

/** 단일 화면 Source 미리보기 결과 — 파일 저장 없이 Source와 권장 경로만 담는다. 명세서 §13.1. */
public record GeneratedSource(
        FeatureType featureType,
        String domain,
        ScreenType screenType,
        CrudViewType viewType,
        String layerKey,
        Path recommendedPath,
        String source
) {}
