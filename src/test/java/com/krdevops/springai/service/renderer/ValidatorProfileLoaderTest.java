package com.krdevops.springai.service.renderer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.renderer.RendererProfile;
import com.krdevops.springai.model.renderer.ValidatorProfileReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidatorProfileLoaderTest {

    private static final String HASH =
            "74a5db0f2aeb6b54133238c4bfb9f86e58955867638c18b15da1859c9f38fd52";
    private final ValidatorProfileLoader loader = new ValidatorProfileLoader(
            new ObjectMapper().findAndRegisterModules());

    @Test
    void 정규화참조가승인된9개Gate정책을정확히조회한다() {
        ValidatorProfileReference reference = ValidatorProfileReference.parse(
                "thymeleaf-krds-validator@1.0#" + HASH);

        var profile = loader.loadApproved(reference);

        assertThat(reference.externalForm())
                .isEqualTo("thymeleaf-krds-validator@1.0#" + HASH);
        assertThat(profile.gatePolicies()).hasSize(9);
        assertThat(profile.requiredEvidence()).containsExactly(
                "BINDING_VALIDATION", "BUILD_VALIDATION", "BROWSER_RENDER",
                "ACCESSIBILITY", "VISUAL_PARITY");
    }

    @Test
    void 참조Hash가배포ValidatorProfile과다르면차단한다() {
        ValidatorProfileReference wrong = new ValidatorProfileReference(
                "thymeleaf-krds-validator", "1.0", "a".repeat(64));

        assertThatThrownBy(() -> loader.loadApproved(wrong))
                .isInstanceOfSatisfying(ValidatorProfileLoader.ValidatorProfileLoadException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("VALIDATOR_PROFILE_HASH_MISMATCH"));
    }

    @Test
    void RendererProfile검증이Validator참조까지재검증한다() throws Exception {
        RendererProfile profile = new RendererProfileLoader(
                new ObjectMapper().findAndRegisterModules(), new RendererProfileValidator())
                .load("thymeleaf-krds", "1.0");
        RendererProfileValidator validator = new RendererProfileValidator(
                null, null, loader);

        assertThat(validator.validate(profile, RendererProfileValidator.Purpose.APPLY).valid())
                .isTrue();
    }

    @Test
    void 축약문자열이나Hash없는참조는모델진입전에거부한다() {
        assertThatThrownBy(() -> ValidatorProfileReference.parse("thymeleaf-validator-1.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("profileId@version#sha256");
    }
}
