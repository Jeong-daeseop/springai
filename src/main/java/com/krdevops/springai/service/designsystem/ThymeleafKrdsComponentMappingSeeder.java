package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.mapper.DesignCodeComponentMappingRepository;
import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping.PropertyMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * KRDS CRUD 화면용 {@link DesignCodeComponentMapping} 6종을 APPROVED 상태로 Runtime Repository에
 * 적재한다({@code app.design-system.component-mapping-seed.enabled=true}일 때만).
 *
 * <p>{@code figmaComponentSetKey}는 {@code FigmaUiDesignSpecV2Mapper}가 만드는 논리 키
 * {@code "krds:" + logicalType}와 맞춘다 — 이 둘이 일치해야
 * {@code RequiredComponentMappingApplyGate.repository.findApproved(...)}가 성사된다.
 * 대화형 승인(preview/fragment 계약 검증)을 거치지 않고 직접 저장하며, 실제 fragment 파일 존재
 * 여부는 생성 시점 대상 프로젝트에서 별도로 검증된다(B3).</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.design-system.component-mapping-seed",
        name = "enabled", havingValue = "true")
public class ThymeleafKrdsComponentMappingSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ThymeleafKrdsComponentMappingSeeder.class);
    private static final String PROFILE = RequiredComponentMappingApplyGate.THYMELEAF_KRDS_PROFILE;
    private static final String VERSION = "1.0";
    private static final String SOURCE_REVISION = "krds-v1.0.0";
    private static final String ACTOR = "component-mapping-seeder";
    private static final String FIXTURE_SCHEMA = "1.0";

    private final DesignCodeComponentMappingRepository repository;
    private final DesignCodeComponentMappingHashService hashService;

    public ThymeleafKrdsComponentMappingSeeder(
            DesignCodeComponentMappingRepository repository,
            DesignCodeComponentMappingHashService hashService) {
        this.repository = repository;
        this.hashService = hashService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int seeded = 0;
        for (DesignCodeComponentMapping mapping : mappings()) {
            if (repository.findVersion(mapping.mappingId(), VERSION).isPresent()) continue;
            repository.saveImmutable(mapping);
            seeded++;
        }
        log.info("KRDS Thymeleaf 컴포넌트 매핑 시드 완료: 신규 {}건 (profile={})", seeded, PROFILE);
    }

    List<DesignCodeComponentMapping> mappings() {
        return List.of(
                mapping("button", "components/krds-button :: button",
                        List.of(param("Type", "variant", "primary"),
                                param("Size", "size", "medium"),
                                param("Label", "label", "버튼")),
                        Map.of("Type", "primary", "Size", "medium", "Label", "버튼")),
                mapping("text-input", "components/krds-text-input :: textInput",
                        List.of(param("Size", "size", "medium"),
                                param("Label", "label", "항목")),
                        Map.of("Size", "medium", "Label", "항목")),
                mapping("select", "components/krds-select :: select",
                        List.of(param("Size", "size", "medium"),
                                param("Label", "label", "항목")),
                        Map.of("Size", "medium", "Label", "항목")),
                mapping("date-input", "components/krds-date-input :: dateInput",
                        List.of(param("Type", "mode", "single"),
                                param("Label", "label", "날짜")),
                        Map.of("Type", "single", "Label", "날짜")),
                mapping("data-table", "components/krds-data-table :: dataTable",
                        List.of(param("Type", "variant", "basic")),
                        Map.of("Type", "basic")),
                mapping("pagination", "components/krds-pagination :: pagination",
                        List.of(param("Type", "variant", "pc")),
                        Map.of("Type", "pc")));
    }

    private DesignCodeComponentMapping mapping(
            String logicalType, String thymeleafFragment,
            List<PropertyMapping> propertyMappings, Map<String, Object> figmaProperties) {
        Map<String, Object> fixtureModel = Map.of(
                "schemaVersion", FIXTURE_SCHEMA,
                "figmaProperties", figmaProperties,
                "figmaSlots", Map.of(),
                "contextVariables", Map.of());
        DesignCodeComponentMapping unhashed = new DesignCodeComponentMapping(
                "krds-" + logicalType, VERSION, DesignCodeComponentMapping.Status.APPROVED,
                "0".repeat(64), logicalType, "krds:" + logicalType, thymeleafFragment,
                propertyMappings, List.of(), fixtureModel, List.of(PROFILE), SOURCE_REVISION,
                ACTOR, Instant.EPOCH);
        String contentHash = hashService.compute(unhashed);
        return new DesignCodeComponentMapping(
                unhashed.mappingId(), VERSION, DesignCodeComponentMapping.Status.APPROVED,
                contentHash, logicalType, "krds:" + logicalType, thymeleafFragment,
                propertyMappings, List.of(), fixtureModel, List.of(PROFILE), SOURCE_REVISION,
                ACTOR, Instant.EPOCH);
    }

    private static PropertyMapping param(String figmaProperty, String fragmentParameter, Object defaultValue) {
        return new PropertyMapping(figmaProperty, fragmentParameter, Map.of(), false, defaultValue);
    }
}
