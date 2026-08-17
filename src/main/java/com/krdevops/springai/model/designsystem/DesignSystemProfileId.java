package com.krdevops.springai.model.designsystem;

/**
 * R0-027: {@code designSystemProfileId}가 Token/Variable(profileVersion) · Component
 * Registry(registryVersion) · Default Layout Policy(layoutPolicyVersion) 버전을 하나의
 * 문자열로 원자적으로 결합하는 계약.
 *
 * <p>기존 각 서비스가 쓰는 {@code designSystemProfileId}(예: {@code FigmaScreenExportRequest},
 * {@code ThymeleafBindingPreviewRequest})는 여전히 {@link DesignSystemProfile#id()} 하나만
 * 가리키며 저장소에서 "그 id의 최신 PUBLISHED 버전"을 조회한다({@code findLatest()}) — 이 타입은
 * 그 대신 정확한 버전 조합 하나를 고정 참조해야 하는 곳(Bundle 재현, 감사 로그, drift 비교)에서
 * 쓰는 별도 식별자다. 기존 호출자를 이 타입으로 바꾸도록 강제하지 않는다.
 */
public record DesignSystemProfileId(
        String profileId, String profileVersion, String registryVersion, String layoutPolicyVersion) {

    private static final String SEPARATOR = ":";
    /** {@link DesignSystemProfile#layoutPolicyVersion()}이 없는(null) Profile을 나타내는 placeholder. */
    public static final String NO_LAYOUT_POLICY = "-";

    public DesignSystemProfileId {
        if (profileId == null || profileId.isBlank()) {
            throw new IllegalArgumentException("profileId는 필수입니다.");
        }
        if (profileVersion == null || profileVersion.isBlank()) {
            throw new IllegalArgumentException("profileVersion은 필수입니다.");
        }
        if (registryVersion == null || registryVersion.isBlank()) {
            throw new IllegalArgumentException("registryVersion은 필수입니다.");
        }
        layoutPolicyVersion = layoutPolicyVersion == null || layoutPolicyVersion.isBlank()
                ? NO_LAYOUT_POLICY : layoutPolicyVersion;
        if (profileId.contains(SEPARATOR) || profileVersion.contains(SEPARATOR)
                || registryVersion.contains(SEPARATOR) || layoutPolicyVersion.contains(SEPARATOR)) {
            throw new IllegalArgumentException(
                    "DESIGN_SYSTEM_PROFILE_ID_INVALID_COMPONENT: 각 구성요소는 구분자 '" + SEPARATOR + "'를 포함할 수 없습니다.");
        }
    }

    /** {@link DesignSystemProfile}의 현재 버전 조합을 그대로 원자적 식별자로 고정한다. */
    public static DesignSystemProfileId of(DesignSystemProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("profile은 필수입니다.");
        }
        return new DesignSystemProfileId(
                profile.id(), profile.version(), profile.registryVersion(), profile.layoutPolicyVersion());
    }

    /** {@link #toString()}이 만든 문자열을 역파싱한다. 구성요소가 4개가 아니면 거부한다. */
    public static DesignSystemProfileId parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value는 필수입니다.");
        }
        String[] parts = value.split(SEPARATOR, -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException(
                    "DESIGN_SYSTEM_PROFILE_ID_MALFORMED: "
                            + "profileId:profileVersion:registryVersion:layoutPolicyVersion 형식이어야 합니다: " + value);
        }
        return new DesignSystemProfileId(parts[0], parts[1], parts[2], parts[3]);
    }

    /** 이 조합이 참조하는 Profile에 Default Layout Policy가 실제로 지정돼 있었는지. */
    public boolean hasLayoutPolicy() {
        return !NO_LAYOUT_POLICY.equals(layoutPolicyVersion);
    }

    @Override
    public String toString() {
        return profileId + SEPARATOR + profileVersion + SEPARATOR + registryVersion + SEPARATOR + layoutPolicyVersion;
    }
}
