package com.krdevops.springai.service.generation.model;

import java.nio.file.Path;

/**
 * 생성할 파일 1개의 계획. 명세서 §10.3.
 *
 * <p>{@code displayName}은 결과 VO의 성공/실패 목록에 노출되는 이름이며 파일 시스템 경로의
 * 마지막 조각과 항상 같지는 않다 — 예: Thymeleaf layout 레이어는 경로가
 * {@code .../templates/layout/default.html}이지만 표시 이름은 {@code layout/default.html}이다.
 */
public record FileBlueprint(
        String layerKey,
        String displayName,
        Path targetPath,
        RenderRequest renderRequest
) {
    public FileBlueprint {
        if (layerKey == null || layerKey.isBlank()) {
            throw new IllegalArgumentException("layerKey는 필수입니다.");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName은 필수입니다.");
        }
        if (targetPath == null || targetPath.toString().isBlank()) {
            throw new IllegalArgumentException("targetPath는 필수입니다.");
        }
        layerKey = layerKey.trim();
        displayName = displayName.trim();
        targetPath = targetPath.normalize();
    }
}
