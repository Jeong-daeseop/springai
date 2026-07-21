package com.krdevops.springai.service;

import com.krdevops.springai.config.DesignVisionProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReferencePathValidatorTest {

    @Test
    void acceptsImageInsideAllowedRoot(@TempDir Path root) throws Exception {
        DesignVisionProperties properties = new DesignVisionProperties();
        properties.setAllowedPaths(List.of(root.toString()));
        Path image = root.resolve("reference.png");
        Files.write(image, new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3});

        assertThat(new ReferencePathValidator(properties).validate(image.toString())).isEqualTo(image.toRealPath());
    }

    @Test
    void rejectsMismatchedExtensionAndMagic(@TempDir Path root) throws Exception {
        DesignVisionProperties properties = new DesignVisionProperties();
        properties.setAllowedPaths(List.of(root.toString()));
        Path image = root.resolve("reference.png");
        Files.writeString(image, "not-an-image");

        assertThatThrownBy(() -> new ReferencePathValidator(properties).validate(image.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("일치하지 않습니다");
    }
}
