package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.PathType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * R1-033/R1-034: classpath의 Semantic Figma JSON Schema 구조 검증 결과를
 * JSON Pointer와 가장 가까운 logicalNodeId가 포함된 공통 오류로 변환한다.
 */
@Component
public class FigmaContractSchemaValidator {

    private static final String SCHEMA_ROOT = "/contracts/";
    private static final String SCREEN_SCHEMA_ID =
            "https://schemas.krdevops.local/figma-screen-spec-v1";
    private static final List<String> SCHEMA_FILES = List.of(
            "figma-common-v1.schema.json",
            "figma-screen-spec-v1.schema.json"
    );

    private final ObjectMapper objectMapper;
    private final Schema screenSchema;

    public FigmaContractSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
        Map<String, String> schemas = loadSchemas();
        SchemaRegistryConfig config = SchemaRegistryConfig.builder()
                .pathType(PathType.JSON_POINTER)
                .build();
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemaRegistryConfig(config).schemas(schemas));
        this.screenSchema = registry.getSchema(SchemaLocation.of(SCREEN_SCHEMA_ID));
    }

    public List<SchemaViolation> validate(FigmaScreenSpec spec) {
        JsonNode document = objectMapper.valueToTree(spec);
        try {
            String json = objectMapper.writeValueAsString(spec);
            return screenSchema.validate(json, InputFormat.JSON).stream()
                    .map(error -> {
                        String pointer = error.getInstanceLocation().toString();
                        return new SchemaViolation(
                                "SCHEMA_" + error.getKeyword().toUpperCase(Locale.ROOT),
                                pointer,
                                findLogicalNodeId(document, pointer),
                                error.getMessage());
                    })
                    .sorted(java.util.Comparator
                            .comparing(SchemaViolation::jsonPointer)
                            .thenComparing(SchemaViolation::code))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("FigmaScreenSpec Schema 검증용 JSON 직렬화 실패", exception);
        }
    }

    private Map<String, String> loadSchemas() {
        Map<String, String> schemas = new LinkedHashMap<>();
        for (String file : SCHEMA_FILES) {
            String content = readResource(file);
            try {
                String id = objectMapper.readTree(content).path("$id").asText();
                if (id.isBlank()) {
                    throw new IllegalStateException(file + "에 $id가 없습니다.");
                }
                schemas.put(id, content);
            } catch (IOException exception) {
                throw new IllegalStateException(file + "을 읽을 수 없습니다.", exception);
            }
        }
        return schemas;
    }

    private String readResource(String file) {
        try (InputStream stream = FigmaContractSchemaValidator.class
                .getResourceAsStream(SCHEMA_ROOT + file)) {
            if (stream == null) {
                throw new IllegalStateException("Semantic Figma Schema를 찾을 수 없습니다: " + file);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Semantic Figma Schema를 읽을 수 없습니다: " + file, exception);
        }
    }

    private String findLogicalNodeId(JsonNode document, String pointer) {
        String candidatePointer = pointer;
        while (candidatePointer != null) {
            JsonNode candidate = candidatePointer.isEmpty()
                    ? document : document.at(candidatePointer);
            if (candidate.isObject() && candidate.hasNonNull("logicalNodeId")) {
                return candidate.path("logicalNodeId").asText();
            }
            int separator = candidatePointer.lastIndexOf('/');
            candidatePointer = separator < 0 ? null : candidatePointer.substring(0, separator);
        }
        return null;
    }

    public record SchemaViolation(
            String code,
            String jsonPointer,
            String logicalNodeId,
            String message
    ) {
    }
}
