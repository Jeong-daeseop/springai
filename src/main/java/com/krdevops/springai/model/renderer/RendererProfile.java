package com.krdevops.springai.model.renderer;

import com.krdevops.springai.model.artifact.ContentHashes;

import java.util.HashSet;
import java.util.List;

/** FreeMarker 기반 Thymeleaf 생성기의 버전·기능·검증 경계를 고정하는 불변 Profile. */
public record RendererProfile(
        String profileId,
        String version,
        String contentHash,
        Status status,
        RendererType rendererType,
        TemplateEngine templateEngine,
        String templateSetVersion,
        String templateSetHash,
        String componentMappingVersion,
        List<String> supportedFeatures,
        List<String> forbiddenFallbacks,
        String outputConventionVersion,
        String validatorProfile,
        List<String> supportedViewTypes
) {
    public static final String SCHEMA_VERSION = "1.0";

    public RendererProfile {
        profileId = requireText(profileId, "profileId");
        version = requireText(version, "version");
        contentHash = ContentHashes.requireValid(contentHash);
        if (status == null) throw new IllegalArgumentException("status는 필수입니다.");
        if (rendererType == null) throw new IllegalArgumentException("rendererType은 필수입니다.");
        if (templateEngine == null) throw new IllegalArgumentException("templateEngine은 필수입니다.");
        templateSetVersion = requireText(templateSetVersion, "templateSetVersion");
        templateSetHash = ContentHashes.requireValid(templateSetHash);
        componentMappingVersion = requireText(componentMappingVersion, "componentMappingVersion");
        supportedFeatures = immutableUnique(supportedFeatures, "supportedFeatures");
        forbiddenFallbacks = immutableUnique(forbiddenFallbacks, "forbiddenFallbacks");
        outputConventionVersion = requireText(outputConventionVersion, "outputConventionVersion");
        validatorProfile = requireText(validatorProfile, "validatorProfile");
        ValidatorProfileReference.parse(validatorProfile);
        supportedViewTypes = immutableUnique(supportedViewTypes, "supportedViewTypes");
        if (supportedViewTypes.isEmpty()) {
            throw new IllegalArgumentException("supportedViewTypes는 하나 이상이어야 합니다.");
        }
    }

    public enum Status { DRAFT, APPROVED, SUPERSEDED }
    public enum RendererType { THYMELEAF }
    public enum TemplateEngine { FREEMARKER }

    public ValidatorProfileReference validatorProfileReference() {
        return ValidatorProfileReference.parse(validatorProfile);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "는 필수입니다.");
        return value.trim();
    }

    private static List<String> immutableUnique(List<String> values, String field) {
        List<String> normalized = (values == null ? List.<String>of() : values).stream()
                .map(value -> requireText(value, field + " 항목")).toList();
        if (new HashSet<>(normalized).size() != normalized.size()) {
            throw new IllegalArgumentException(field + "에는 중복 항목을 선언할 수 없습니다.");
        }
        return List.copyOf(normalized);
    }
}
