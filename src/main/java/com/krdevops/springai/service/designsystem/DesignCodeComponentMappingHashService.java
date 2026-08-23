package com.krdevops.springai.service.designsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** Version·상태·승인 메타데이터를 제외한 Mapping 의미 payload의 결정론적 SHA-256을 계산한다. */
@Service
public class DesignCodeComponentMappingHashService {

    private final ObjectMapper canonicalMapper;

    public DesignCodeComponentMappingHashService(ObjectMapper objectMapper) {
        this.canonicalMapper = objectMapper.copy().findAndRegisterModules()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public String compute(DesignCodeComponentMapping mapping) {
        if (mapping == null) throw new IllegalArgumentException("mapping은 필수입니다.");
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("logicalType", mapping.logicalType());
            payload.put("figmaComponentSetKey", mapping.figmaComponentSetKey());
            payload.put("thymeleafFragment", mapping.thymeleafFragment());
            payload.put("propertyMappings", mapping.propertyMappings());
            payload.put("slotMappings", mapping.slotMappings());
            payload.put("fixtureModel", mapping.fixtureModel());
            payload.put("supportedRendererProfiles", mapping.supportedRendererProfiles());
            payload.put("sourceRevision", mapping.sourceRevision());
            return ContentHashes.sha256Hex(canonicalMapper.writeValueAsBytes(payload));
        } catch (Exception exception) {
            throw new IllegalStateException("DesignCodeComponentMapping Content Hash 계산에 실패했습니다.", exception);
        }
    }
}
