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
        if (spec == null || profile == null || registry == null) {
            throw new IllegalArgumentException("Spec, Profile, Registry가 모두 있어야 Bundle을 조립할 수 있습니다.");
        }
        FigmaScreenSpec.DesignSystemRef ref = spec.designSystem();
        if (ref == null
                || !ref.profileId().equals(profile.id())
                || !ref.profileVersion().equals(profile.version())
                || !ref.registryVersion().equals(profile.registryVersion())
                || !ref.profileId().equals(registry.profileId())
                || !ref.profileVersion().equals(registry.profileVersion())
                || !ref.registryVersion().equals(registry.registryVersion())) {
            throw new IllegalArgumentException(
                    "BUNDLE_VERSION_MISMATCH: Spec이 참조한 Profile/Registry 버전과 Snapshot이 일치하지 않습니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        FigmaExportMetadata metadata = new FigmaExportMetadata(
                now,
                spec.semanticPattern() == null ? FigmaScreenSpec.SCHEMA_VERSION : FigmaScreenSpec.SCHEMA_VERSION_V2,
                spec.screenSpecificationVersion(), profile.version(),
                registry.registryVersion(),
                spec.screenPatternVersion(), spec.variantRuleSetVersion(), spec.componentContractVersion());
        return new FigmaExportBundle(
                spec,
                new DesignSystemProfileSnapshot(profile, now),
                new ComponentRegistrySnapshot(registry, now),
                metadata);
    }
}
