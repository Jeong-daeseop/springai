package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.DesignSystemProfile;
import com.krdevops.springai.model.figma.ComponentRegistrySnapshot;
import com.krdevops.springai.model.figma.DesignSystemProfileSnapshot;
import com.krdevops.springai.model.figma.FigmaExportBundle;
import com.krdevops.springai.model.figma.FigmaExportMetadata;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** R2-014: Export 시점의 DesignSystemProfile/ComponentRegistry 스냅샷과 FigmaScreenSpec을 하나의 FigmaExportBundle로 조립한다. */
@Component
public class FigmaExportBundleAssembler {

    public FigmaExportBundle assemble(FigmaScreenSpec spec, DesignSystemProfile profile, ComponentRegistry registry) {
        LocalDateTime now = LocalDateTime.now();
        FigmaExportMetadata metadata = new FigmaExportMetadata(
                now, FigmaScreenSpec.SCHEMA_VERSION, spec.screenSpecificationVersion(),
                profile.version(), registry == null ? profile.registryVersion() : registry.registryVersion());
        ComponentRegistry effectiveRegistry = registry == null
                ? new ComponentRegistry(profile.id(), profile.version(), profile.registryVersion(), null, java.util.Map.of())
                : registry;
        return new FigmaExportBundle(
                spec,
                new DesignSystemProfileSnapshot(profile, now),
                new ComponentRegistrySnapshot(effectiveRegistry, now),
                metadata);
    }
}
