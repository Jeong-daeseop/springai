package com.krdevops.springai.service.designsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import com.fasterxml.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentCatalogV1ToV2ConverterTest {

    @Test
    void legacyCatalogConvertsDeterministicallyButFailsClosedForMissingAtomicTargets() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var validator = new ComponentCatalogValidator();
        var converter = new ComponentCatalogV1ToV2Converter(mapper, validator);
        byte[] source = new ClassPathResource("figma/contracts/component-catalog-v1.json")
                .getInputStream().readAllBytes();

        var first = converter.convert(mapper.readTree(source));
        var second = converter.convert(mapper.readTree(source));
        JsonNode golden = mapper.readTree(new ClassPathResource(
                "figma/contracts/component-catalog-v1-to-v2-golden.json").getInputStream());

        assertThat(first.catalog().schemaVersion()).isEqualTo("component-catalog-v2");
        assertThat(first.catalog().components()).containsKeys("krds.button", "egov.dataTable", "egov.listPage");
        for (JsonNode key : golden.path("requiredComponentKeys")) {
            assertThat(first.catalog().components()).containsKey(key.asText());
        }
        assertThat(first.catalog().components().get("egov.dataTable").composition())
                .containsExactly("krds.tableHeader", "krds.tableCell");
        assertThat(first.issues()).extracting(issue -> issue.code())
                .containsAll(
                        java.util.stream.StreamSupport.stream(golden.path("requiredIssueCodes").spliterator(), false)
                                .map(JsonNode::asText).toList());
        assertThat(first.valid()).isFalse();
        assertThat(first.catalog()).isEqualTo(second.catalog());
        assertThat(first.issues()).isEqualTo(second.issues());
    }
}
