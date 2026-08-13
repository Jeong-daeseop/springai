package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.mapper.ComponentRegistryRepository;
import com.krdevops.springai.mapper.DesignSystemProfileRepository;
import com.krdevops.springai.mapper.ScreenPatternRepository;
import com.krdevops.springai.mapper.ScreenSpecRepository;
import com.krdevops.springai.mapper.VariantRuleSetRepository;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.role.ScreenPattern;
import com.krdevops.springai.model.designsystem.ScreenPatternDefinition;
import com.krdevops.springai.model.designsystem.VariantRuleSet;
import com.krdevops.springai.model.figma.FigmaExportBundle;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** KRV-073: 운영 상태를 변경하지 않고 이전 계약 Snapshot으로 화면 Bundle을 재생성한다. */
@Service
public class FigmaRollbackRehearsalService {
    private final ScreenSpecRepository screenSpecs;
    private final DesignSystemProfileRepository profiles;
    private final ComponentRegistryRepository registries;
    private final ScreenPatternRepository patterns;
    private final VariantRuleSetRepository ruleSets;
    private final FigmaScreenBuilderRegistry builders;
    private final FigmaScreenTypeResolver typeResolver;
    private final ScreenSemanticNormalizer normalizer;
    private final LogicalNodeIdFactory idFactory;
    private final KrdsComponentResolutionService resolver;
    private final FigmaScreenSpecValidator validator;
    private final FigmaExportBundleAssembler assembler;
    private final ObjectMapper objectMapper;

    public FigmaRollbackRehearsalService(
            ScreenSpecRepository screenSpecs, DesignSystemProfileRepository profiles,
            ComponentRegistryRepository registries, ScreenPatternRepository patterns,
            VariantRuleSetRepository ruleSets, FigmaScreenBuilderRegistry builders,
            FigmaScreenTypeResolver typeResolver, LogicalNodeIdFactory idFactory,
            ScreenSemanticNormalizer normalizer,
            KrdsComponentResolutionService resolver, FigmaScreenSpecValidator validator,
            FigmaExportBundleAssembler assembler, ObjectMapper objectMapper) {
        this.screenSpecs = screenSpecs;
        this.profiles = profiles;
        this.registries = registries;
        this.patterns = patterns;
        this.ruleSets = ruleSets;
        this.builders = builders;
        this.typeResolver = typeResolver;
        this.normalizer = normalizer;
        this.idFactory = idFactory;
        this.resolver = resolver;
        this.validator = validator;
        this.assembler = assembler;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    public RehearsalResult preview(RehearsalRequest request) {
        var business = screenSpecs.findVersion(request.screenSpecificationId(), request.screenSpecificationVersion())
                .orElseThrow(() -> new IllegalArgumentException("ScreenSpecification 버전을 찾을 수 없습니다."));
        if (business.status() != ScreenSpecStatus.APPROVED) {
            throw new IllegalStateException("ROLLBACK_SCREEN_SPEC_NOT_APPROVED");
        }
        var profile = profiles.findVersion(request.profileId(), request.profileVersion())
                .orElseThrow(() -> new IllegalArgumentException("Profile Snapshot을 찾을 수 없습니다."));
        var registry = registries.findVersion(request.profileId(), request.registryVersion())
                .orElseThrow(() -> new IllegalArgumentException("Registry Snapshot을 찾을 수 없습니다."));
        VariantRuleSet ruleSet = ruleSets.findVersion(request.ruleSetId(), request.ruleSetVersion())
                .orElseThrow(() -> new IllegalArgumentException("Rule Set Snapshot을 찾을 수 없습니다."));
        if (!profile.registryVersion().equals(registry.registryVersion())) {
            throw new IllegalStateException("ROLLBACK_PROFILE_REGISTRY_VERSION_MISMATCH");
        }
        if (profile.status() != com.krdevops.springai.model.designsystem.DesignSystemProfile.Status.PUBLISHED) {
            throw new IllegalStateException("ROLLBACK_PROFILE_NOT_PUBLISHED");
        }

        List<FigmaExportBundle> bundles = new ArrayList<>();
        Map<String, String> hashes = new LinkedHashMap<>();
        for (var page : business.pages()) {
            var screenType = typeResolver.resolveScreenType(page, business);
            var semantic = builders.builderFor(screenType).build(business, page, idFactory);
            ScreenPattern semanticPattern = normalizer.pattern(page);
            String patternVersion = request.patternVersions().get(semanticPattern.code());
            if (patternVersion == null || patternVersion.isBlank()) {
                throw new IllegalArgumentException("Pattern Snapshot 버전이 없습니다: " + semanticPattern.code());
            }
            ScreenPatternDefinition pattern = patterns.findVersion(semanticPattern, patternVersion)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Pattern Snapshot을 찾을 수 없습니다: " + semanticPattern.code() + "/" + patternVersion));
            var resolution = resolver.resolveWithSnapshots(
                    profile.id(), registry, page, screenType, business.layoutDensity(), "DESKTOP",
                    semantic, pattern, ruleSet);
            var spec = new FigmaScreenSpec(
                    page.id(), 1, business.id(), business.version(), screenType,
                    typeResolver.resolveLayoutPattern(business), business.screenName(), null, "DESKTOP",
                    business.status().name(),
                    new FigmaScreenSpec.DesignSystemRef(profile.id(), profile.version(), registry.registryVersion()),
                    resolution.content(), List.of(), resolution.pattern(), resolution.screenPatternVersion(),
                    resolution.variantRuleSetVersion(), resolution.componentContractVersion());
            var issues = validator.validate(spec);
            if (!issues.isEmpty()) throw new IllegalStateException("ROLLBACK_PREVIEW_INVALID: " + page.id() + " " + issues);
            FigmaExportBundle bundle = assembler.assemble(spec, profile, registry, pattern, ruleSet);
            bundles.add(bundle);
            hashes.put(page.id(), hash(bundle));
        }
        return new RehearsalResult("PREVIEW_ONLY", bundles.size(), Map.copyOf(hashes), List.copyOf(bundles));
    }

    private String hash(FigmaExportBundle bundle) {
        try {
            byte[] value = objectMapper.writeValueAsString(bundle).getBytes(StandardCharsets.UTF_8);
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException("Rollback Bundle Hash 생성 실패", exception);
        }
    }

    public record RehearsalRequest(
            String screenSpecificationId, int screenSpecificationVersion,
            String profileId, String profileVersion, String registryVersion,
            String ruleSetId, String ruleSetVersion, Map<String, String> patternVersions) {
        public RehearsalRequest {
            patternVersions = patternVersions == null ? Map.of() : Map.copyOf(patternVersions);
        }
    }

    public record RehearsalResult(
            String mode, int bundleCount, Map<String, String> contextHashes, List<FigmaExportBundle> bundles) {}
}
