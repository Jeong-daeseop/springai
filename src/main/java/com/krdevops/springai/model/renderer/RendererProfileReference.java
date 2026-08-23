package com.krdevops.springai.model.renderer;

import com.krdevops.springai.model.artifact.ContentHashes;

/** 생성 Command가 승인 RendererProfile의 ID·Version·Hash를 고정하는 참조. */
public record RendererProfileReference(
        String profileId,
        String version,
        String contentHash
) {
    public static final String DEFAULT_PROFILE_ID = "thymeleaf-krds";
    public static final String DEFAULT_PROFILE_VERSION = "1.0";
    public static final String DEFAULT_CONTENT_HASH =
            "8e2801b468b8c3b6f361f4bf861e491f882f25911585611ed5783c20b279b5df";

    public RendererProfileReference {
        profileId = requireText(profileId, "profileId");
        version = requireText(version, "version");
        contentHash = ContentHashes.requireValid(contentHash);
    }

    public static RendererProfileReference defaultThymeleafKrds() {
        return new RendererProfileReference(
                DEFAULT_PROFILE_ID, DEFAULT_PROFILE_VERSION, DEFAULT_CONTENT_HASH);
    }

    public boolean identifies(RendererProfile profile) {
        return profile != null
                && profileId.equals(profile.profileId())
                && version.equals(profile.version())
                && contentHash.equals(profile.contentHash());
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "는 필수입니다.");
        return value.trim();
    }
}
