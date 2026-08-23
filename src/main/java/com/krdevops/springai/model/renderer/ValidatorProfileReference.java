package com.krdevops.springai.model.renderer;

import com.krdevops.springai.model.artifact.ContentHashes;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** RendererProfile JSON 문자열에서 ID·Version·Hash를 모두 고정하는 Validator Profile 참조. */
public record ValidatorProfileReference(String profileId, String version, String contentHash) {
    private static final Pattern FORMAT = Pattern.compile(
            "^([A-Za-z0-9][A-Za-z0-9._:-]{0,127})@([1-9][0-9]*\\.[0-9]+(?:\\.[0-9]+)?)#([a-f0-9]{64})$");

    public ValidatorProfileReference {
        if (profileId == null || profileId.isBlank()) throw new IllegalArgumentException("profileId는 필수입니다.");
        if (version == null || version.isBlank()) throw new IllegalArgumentException("version은 필수입니다.");
        contentHash = ContentHashes.requireValid(contentHash);
    }

    public static ValidatorProfileReference parse(String value) {
        Matcher matcher = FORMAT.matcher(value == null ? "" : value.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "validatorProfile은 profileId@version#sha256 형식이어야 합니다.");
        }
        return new ValidatorProfileReference(matcher.group(1), matcher.group(2), matcher.group(3));
    }

    public String externalForm() {
        return profileId + "@" + version + "#" + contentHash;
    }
}
