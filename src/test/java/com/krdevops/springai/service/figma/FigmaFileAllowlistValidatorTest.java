package com.krdevops.springai.service.figma;

import com.krdevops.springai.config.DesignVisionProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FigmaFileAllowlistValidatorTest {

    @Test
    void allowsAllFilesWhenAllowlistIsEmpty() {
        var validator = validatorWithAllowlist(List.of());
        assertThat(validator.isFileKeyAllowed("any-file-key")).isTrue();
        assertThat(validator.isFileKeyAllowed("another-key")).isTrue();
    }

    @Test
    void allowsOnlyWhitelistedFileKeys() {
        var validator = validatorWithAllowlist(List.of("key1", "key2", "key3"));
        assertThat(validator.isFileKeyAllowed("key1")).isTrue();
        assertThat(validator.isFileKeyAllowed("key2")).isTrue();
        assertThat(validator.isFileKeyAllowed("key3")).isTrue();
        assertThat(validator.isFileKeyAllowed("key4")).isFalse();
    }

    @Test
    void enforcesTheApprovedFtcFileAndRejectsTheRetiredKrdsFile() {
        var validator = validatorWithAllowlist(List.of("mVy5h1UbORVqQoBm8Wr1bT"));

        assertThat(validator.isFileKeyAllowed("mVy5h1UbORVqQoBm8Wr1bT")).isTrue();
        assertThatThrownBy(() -> validator.validateFileKey("6fcm04dwSEH2IUizZfaZCj"))
                .isInstanceOfSatisfying(FigmaFileAllowlistValidator.FigmaAllowlistException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("FIGMA_FILE_NOT_ALLOWED"));
    }

    @Test
    void rejectsNullOrBlankFileKeys() {
        var validator = validatorWithAllowlist(List.of("key1"));
        assertThat(validator.isFileKeyAllowed(null)).isFalse();
        assertThat(validator.isFileKeyAllowed("")).isFalse();
        assertThat(validator.isFileKeyAllowed("  ")).isFalse();
    }

    @Test
    void throwsExceptionOnDisallowedFileKey() {
        var validator = validatorWithAllowlist(List.of("allowed-key"));
        assertThatThrownBy(() -> validator.validateFileKey("forbidden-key"))
                .isInstanceOfSatisfying(FigmaFileAllowlistValidator.FigmaAllowlistException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("FIGMA_FILE_NOT_ALLOWED"));
    }

    @Test
    void validatesNodeIdFormat() {
        var validator = validatorWithAllowlist(List.of());
        assertThat(validator.isNodeIdAllowed("1:2")).isTrue();
        assertThat(validator.isNodeIdAllowed("123")).isTrue();
        assertThat(validator.isNodeIdAllowed("1:2:3")).isFalse();
        assertThat(validator.isNodeIdAllowed("invalid")).isFalse();
        assertThat(validator.isNodeIdAllowed("")).isFalse();
        assertThat(validator.isNodeIdAllowed(null)).isFalse();
    }

    @Test
    void throwsExceptionOnInvalidNodeIdFormat() {
        var validator = validatorWithAllowlist(List.of());
        assertThatThrownBy(() -> validator.validateNodeId("invalid-node"))
                .isInstanceOfSatisfying(FigmaFileAllowlistValidator.FigmaAllowlistException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("FIGMA_NODE_INVALID"));
    }

    @Test
    void reloadsAllowlistDynamically() {
        var properties = new DesignVisionProperties();
        properties.getFigma().setAllowedFileKeys(List.of("key1"));
        var validator = new FigmaFileAllowlistValidator(properties);

        assertThat(validator.isFileKeyAllowed("key1")).isTrue();
        assertThat(validator.isFileKeyAllowed("key2")).isFalse();

        properties.getFigma().setAllowedFileKeys(List.of("key1", "key2", "key3"));
        validator.reloadAllowlist();

        assertThat(validator.isFileKeyAllowed("key2")).isTrue();
        assertThat(validator.isFileKeyAllowed("key3")).isTrue();
    }

    private FigmaFileAllowlistValidator validatorWithAllowlist(List<String> allowlist) {
        var properties = new DesignVisionProperties();
        properties.getFigma().setAllowedFileKeys(allowlist);
        return new FigmaFileAllowlistValidator(properties);
    }
}
