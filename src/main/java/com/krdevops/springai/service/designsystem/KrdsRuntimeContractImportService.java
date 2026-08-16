package com.krdevops.springai.service.designsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.mapper.ComponentRegistryRepository;
import com.krdevops.springai.mapper.ScreenPatternRepository;
import com.krdevops.springai.mapper.VariantRuleSetRepository;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ScreenPatternDefinition;
import com.krdevops.springai.model.designsystem.VariantRuleSet;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

/** 버전 관리되는 KRDS 계약 파일을 Runtime Repository에 불변 Snapshot으로 적재한다. */
@Service
public class KrdsRuntimeContractImportService {

    public static final String PATTERNS_RESOURCE = "figma/contracts/screen-patterns-v1.json";
    public static final String REGISTRY_RESOURCE = "figma/contracts/qna/krds-component-registry-v2.2.2-candidate.json";
    public static final String RULE_SET_RESOURCE = "figma/contracts/qna/variant-rule-set-krds-v2.2.2-candidate.json";

    private final ComponentRegistryRepository registryRepository;
    private final ScreenPatternRepository patternRepository;
    private final VariantRuleSetRepository ruleSetRepository;
    private final ObjectMapper objectMapper;

    public KrdsRuntimeContractImportService(
            ComponentRegistryRepository registryRepository,
            ScreenPatternRepository patternRepository,
            VariantRuleSetRepository ruleSetRepository,
            ObjectMapper objectMapper
    ) {
        this.registryRepository = registryRepository;
        this.patternRepository = patternRepository;
        this.ruleSetRepository = ruleSetRepository;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    public ContractSet readDefaultQnaContracts() {
        return readContracts(
                new ClassPathResource(PATTERNS_RESOURCE),
                new ClassPathResource(REGISTRY_RESOURCE),
                new ClassPathResource(RULE_SET_RESOURCE));
    }

    public ContractSet readContracts(Resource patterns, Resource registry, Resource ruleSet) {
        try (InputStream patternsInput = patterns.getInputStream();
             InputStream registryInput = registry.getInputStream();
             InputStream rulesInput = ruleSet.getInputStream()) {
            PatternCatalog catalog = objectMapper.readValue(patternsInput, PatternCatalog.class);
            ComponentRegistry componentRegistry = objectMapper.readValue(registryInput, ComponentRegistry.class);
            VariantRuleSet variantRuleSet = objectMapper.readValue(rulesInput, VariantRuleSet.class);
            if (!componentRegistry.profileId().equals(variantRuleSet.profileId())
                    || !componentRegistry.registryVersion().equals(variantRuleSet.registryVersion())) {
                throw new IllegalStateException("KRDS_CONTRACT_VERSION_MISMATCH: Registry와 Rule Set 연결이 다릅니다.");
            }
            return new ContractSet(catalog.patterns(), componentRegistry, variantRuleSet);
        } catch (Exception exception) {
            throw new IllegalStateException("KRDS Runtime 계약 파일을 읽을 수 없습니다.", exception);
        }
    }

    public ImportResult importDefaultQnaContracts() {
        return importContracts(readDefaultQnaContracts());
    }

    /** 운영 Registry가 이미 승인된 경우 Rule Set만 별도 Import한다. */
    public ImportResult importDefaultQnaRuleSet() {
        ContractSet contracts = readDefaultQnaContracts();
        ruleSetRepository.saveImmutable(contracts.ruleSet());
        return new ImportResult(contracts.registry().profileId(), contracts.registry().registryVersion(),
                contracts.ruleSet().id(), contracts.ruleSet().version(), contracts.patterns().size());
    }

    /** 운영 Registry 버전에 맞춘 Rule Set 후보를 외부 Fixture에서 Import한다. */
    public ImportResult importRuleSet(VariantRuleSet ruleSet) {
        ruleSetRepository.saveImmutable(ruleSet);
        return new ImportResult(ruleSet.profileId(), ruleSet.registryVersion(),
                ruleSet.id(), ruleSet.version(), ruleSet.rules().size());
    }

    public ImportResult importContracts(ContractSet contracts) {
        ComponentRegistry registry = contracts.registry();
        registryRepository.findVersion(registry.profileId(), registry.registryVersion())
                .ifPresentOrElse(existing -> {
                    if (!existing.equals(registry)) {
                        throw new IllegalStateException("COMPONENT_REGISTRY_VERSION_CONFLICT: "
                                + registry.profileId() + "/" + registry.registryVersion());
                    }
                }, () -> registryRepository.saveImmutable(registry));
        contracts.patterns().forEach(patternRepository::saveImmutable);
        ruleSetRepository.saveImmutable(contracts.ruleSet());
        return new ImportResult(registry.profileId(), registry.registryVersion(),
                contracts.ruleSet().id(), contracts.ruleSet().version(), contracts.patterns().size());
    }

    public record ContractSet(
            List<ScreenPatternDefinition> patterns,
            ComponentRegistry registry,
            VariantRuleSet ruleSet
    ) {
        public ContractSet {
            patterns = patterns == null ? List.of() : List.copyOf(patterns);
            if (registry == null || ruleSet == null) {
                throw new IllegalArgumentException("Registry와 Rule Set은 필수입니다.");
            }
        }
    }

    public record ImportResult(
            String profileId,
            String registryVersion,
            String ruleSetId,
            String ruleSetVersion,
            int patternCount
    ) {}

    private record PatternCatalog(String version, List<ScreenPatternDefinition> patterns) {}
}
