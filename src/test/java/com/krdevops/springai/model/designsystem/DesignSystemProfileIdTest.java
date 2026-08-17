package com.krdevops.springai.model.designsystem;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** R0-027: profileId:profileVersion:registryVersion:layoutPolicyVersion 원자적 식별자 계약. */
class DesignSystemProfileIdTest {

    @Test
    void ofCombinesAllFourVersionsIntoOneAtomicString() {
        DesignSystemProfile profile = new DesignSystemProfile(
                "krds", "KRDS", "1.0.0", "registry-3", "file-key",
                DesignSystemProfile.Status.PUBLISHED, Map.of(), Map.of(),
                "layout-2", null);

        DesignSystemProfileId id = DesignSystemProfileId.of(profile);

        assertThat(id.toString()).isEqualTo("krds:1.0.0:registry-3:layout-2");
        assertThat(id.hasLayoutPolicy()).isTrue();
    }

    /** R1-015 이전 8-필드 호환 생성자로 만든 Profile은 layoutPolicyVersion이 null이다. */
    @Test
    void ofUsesPlaceholderWhenLayoutPolicyVersionIsAbsent() {
        DesignSystemProfile profile = new DesignSystemProfile(
                "krds", "KRDS", "1.0.0", "registry-3", "file-key",
                DesignSystemProfile.Status.PUBLISHED, Map.of(), Map.of());

        DesignSystemProfileId id = DesignSystemProfileId.of(profile);

        assertThat(id.toString()).isEqualTo("krds:1.0.0:registry-3:-");
        assertThat(id.hasLayoutPolicy()).isFalse();
    }

    @Test
    void parseRoundTripsToStringOutput() {
        DesignSystemProfileId original = new DesignSystemProfileId("krds", "1.0.0", "registry-3", "layout-2");

        DesignSystemProfileId parsed = DesignSystemProfileId.parse(original.toString());

        assertThat(parsed).isEqualTo(original);
    }

    @Test
    void parseRejectsWrongSegmentCount() {
        assertThatThrownBy(() -> DesignSystemProfileId.parse("krds:1.0.0:registry-3"))
                .isInstanceOfSatisfying(IllegalArgumentException.class,
                        error -> assertThat(error.getMessage()).contains("DESIGN_SYSTEM_PROFILE_ID_MALFORMED"));

        assertThatThrownBy(() -> DesignSystemProfileId.parse("krds:1.0.0:registry-3:layout-2:extra"))
                .isInstanceOfSatisfying(IllegalArgumentException.class,
                        error -> assertThat(error.getMessage()).contains("DESIGN_SYSTEM_PROFILE_ID_MALFORMED"));
    }

    @Test
    void constructorRejectsBlankRequiredComponents() {
        assertThatThrownBy(() -> new DesignSystemProfileId("", "1.0.0", "registry-3", "layout-2"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DesignSystemProfileId("krds", null, "registry-3", "layout-2"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DesignSystemProfileId("krds", "1.0.0", " ", "layout-2"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 구성요소 안에 구분자가 섞이면 parse() 왕복이 깨지므로 생성 시점에 차단한다. */
    @Test
    void constructorRejectsSeparatorInsideComponent() {
        assertThatThrownBy(() -> new DesignSystemProfileId("krds:evil", "1.0.0", "registry-3", "layout-2"))
                .isInstanceOfSatisfying(IllegalArgumentException.class,
                        error -> assertThat(error.getMessage()).contains("DESIGN_SYSTEM_PROFILE_ID_INVALID_COMPONENT"));
    }
}
