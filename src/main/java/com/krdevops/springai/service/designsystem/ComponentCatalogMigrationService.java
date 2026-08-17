package com.krdevops.springai.service.designsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;

/** Legacy Catalog v1을 v2로 변환해 검토 결과만 제공한다. 파일 교체·승인은 수행하지 않는다. */
@Service
public class ComponentCatalogMigrationService {

    private static final String LEGACY_PATH = "figma/contracts/component-catalog-v1.json";
    private final ObjectMapper objectMapper;
    private final ComponentCatalogV1ToV2Converter converter;

    public ComponentCatalogMigrationService(ObjectMapper objectMapper,
            ComponentCatalogV1ToV2Converter converter) {
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
        this.converter = converter;
    }

    public ComponentCatalogV1ToV2Converter.Conversion preview() {
        try {
            byte[] source = new ClassPathResource(LEGACY_PATH).getInputStream().readAllBytes();
            return converter.convert(objectMapper.readTree(source));
        } catch (IOException e) {
            throw new IllegalStateException("CATALOG_V1_LOAD_FAILED: Legacy Catalog를 읽을 수 없습니다.", e);
        }
    }
}
