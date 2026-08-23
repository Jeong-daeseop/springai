package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.mapper.DesignCodeComponentMappingRepository;
import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import com.krdevops.springai.model.designsystem.DesignComponentRenderInput;
import org.springframework.stereotype.Service;

import java.util.Map;

/** 승인 Mapping과 Figma Instance 값을 하나의 Renderer Component 입력으로 조합한다. */
@Service
public class DesignComponentRenderInputService {

    private final DesignCodeComponentMappingRepository repository;
    private final ComponentPropertyParameterResolver propertyResolver;
    private final ComponentVariantValueResolver variantResolver;
    private final ComponentSlotRegionResolver slotResolver;

    public DesignComponentRenderInputService(
            DesignCodeComponentMappingRepository repository,
            ComponentPropertyParameterResolver propertyResolver,
            ComponentVariantValueResolver variantResolver,
            ComponentSlotRegionResolver slotResolver) {
        this.repository = repository;
        this.propertyResolver = propertyResolver;
        this.variantResolver = variantResolver;
        this.slotResolver = slotResolver;
    }

    public DesignComponentRenderInput resolve(
            String logicalType,
            String figmaComponentSetKey,
            String rendererProfile,
            Map<String, ?> figmaProperties,
            Map<String, ?> figmaSlots) {
        DesignCodeComponentMapping mapping = repository.findApproved(
                        logicalType, figmaComponentSetKey, rendererProfile)
                .orElseThrow(() -> new ApprovedComponentMappingNotFoundException(
                        logicalType, figmaComponentSetKey, rendererProfile));
        return resolve(mapping, rendererProfile, figmaProperties, figmaSlots);
    }

    public DesignComponentRenderInput resolve(
            DesignCodeComponentMapping mapping,
            String rendererProfile,
            Map<String, ?> figmaProperties,
            Map<String, ?> figmaSlots) {
        if (mapping == null || mapping.status() != DesignCodeComponentMapping.Status.APPROVED) {
            throw new IllegalArgumentException("APPROVED mapping은 필수입니다.");
        }
        if (!mapping.supportedRendererProfiles().contains(rendererProfile)) {
            throw new IllegalArgumentException("Mapping이 Renderer Profile을 지원하지 않습니다: "
                    + rendererProfile);
        }
        var propertyResolution = propertyResolver.requireResolved(mapping, figmaProperties);
        var variantResolution = variantResolver.requireResolved(mapping, propertyResolution);
        var slotResolution = slotResolver.requireResolved(mapping, figmaSlots);

        return new DesignComponentRenderInput(
                mapping.mappingId(), mapping.version(), mapping.logicalType(),
                mapping.figmaComponentSetKey(), mapping.thymeleafFragment(), rendererProfile,
                variantResolution.fragmentParameters(), slotResolution.fragmentRegions(),
                mapping.sourceRevision(), mapping.contentHash());
    }

    public static final class ApprovedComponentMappingNotFoundException extends IllegalStateException {
        public ApprovedComponentMappingNotFoundException(
                String logicalType, String figmaComponentSetKey, String rendererProfile) {
            super("승인된 Component Mapping을 찾을 수 없습니다: logicalType=" + logicalType
                    + ", figmaComponentSetKey=" + figmaComponentSetKey
                    + ", rendererProfile=" + rendererProfile);
        }
    }
}
