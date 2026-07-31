package com.krdevops.springai.service;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * R4-01: website-figma-contract의 fixture를 Java validator로 재검증하고,
 * springai가 실제로 사용하는 classpath schema가 계약 프로젝트의 원본과 checksum까지 일치하는지 확인한다.
 */
class WebsiteFigmaContractCrossValidationTest {

    private static final Path FIXTURES = Path.of("website-figma-contract/fixtures");
    private final RenderedDesignSchemaValidator validator = new RenderedDesignSchemaValidator();

    @Test
    void validMinimalFixturePassesJavaValidator() throws Exception {
        String json = Files.readString(FIXTURES.resolve("valid-minimal.json"));
        assertThatCode(() -> validator.validate(json)).doesNotThrowAnyException();
    }

    @Test
    void invalidEnumFixtureIsRejectedByJavaValidator() throws Exception {
        String json = Files.readString(FIXTURES.resolve("invalid-enum.json"));
        assertThatThrownBy(() -> validator.validate(json)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidSourceContentHashFixtureIsRejectedByJavaValidator() throws Exception {
        String json = Files.readString(FIXTURES.resolve("invalid-source-content-hash.json"));
        assertThatThrownBy(() -> validator.validate(json)).isInstanceOf(IllegalArgumentException.class);
    }

    /** 참조 무결성은 JSON Schema가 아니라 도메인 검증(RenderedDesignDocumentValidator) 영역이라 schema 자체는 통과해야 한다. */
    @Test
    void invalidReferenceFixturePassesSchemaButIsADomainConcern() throws Exception {
        String json = Files.readString(FIXTURES.resolve("invalid-reference.json"));
        assertThatCode(() -> validator.validate(json)).doesNotThrowAnyException();
    }

    @Test
    void classpathSchemaMatchesContractProjectSchemaChecksum() throws Exception {
        byte[] contractBytes = Files.readAllBytes(
                Path.of("website-figma-contract/rendered-design-document-v1.schema.json"));
        byte[] classpathBytes;
        try (InputStream stream = RenderedDesignSchemaValidator.class
                .getResourceAsStream("/contracts/rendered-design-document-v1.schema.json")) {
            assertThat(stream).as("springai classpath에 schema가 없습니다.").isNotNull();
            classpathBytes = stream.readAllBytes();
        }
        String contractChecksum = sha256(contractBytes);
        String classpathChecksum = sha256(classpathBytes);
        assertThat(classpathChecksum)
                .as("schema mismatch: website-figma-contract(%s) != springai classpath(%s)",
                        contractChecksum, classpathChecksum)
                .isEqualTo(contractChecksum);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
