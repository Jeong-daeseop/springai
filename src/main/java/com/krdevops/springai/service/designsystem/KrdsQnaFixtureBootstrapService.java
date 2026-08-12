package com.krdevops.springai.service.designsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.mapper.ComponentRegistryRepository;
import com.krdevops.springai.mapper.DesignSystemProfileRepository;
import com.krdevops.springai.mapper.FigmaLibraryInventoryRepository;
import com.krdevops.springai.mapper.FigmaScreenSpecRepository;
import com.krdevops.springai.mapper.ScreenPatternRepository;
import com.krdevops.springai.mapper.VariantRuleSetRepository;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.DesignSystemProfile;
import com.krdevops.springai.model.designsystem.FigmaLibraryInventorySnapshot;
import com.krdevops.springai.model.designsystem.VariantRuleSet;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import com.krdevops.springai.model.figma.ResolvedComponentRef;
import com.krdevops.springai.service.designsystem.FigmaPropertyDriftValidator.ActualProperty;
import com.krdevops.springai.service.designsystem.FigmaPropertyDriftValidator.LibraryComponentSnapshot;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Q&A KRDS 6화면 실행에 필요한 전체 Fixture를 동일 버전 축으로 Bootstrap한다. */
@Service
public class KrdsQnaFixtureBootstrapService {
    public static final String INVENTORY_VERSION = "qna-fixture-inventory-2.1.0";
    private static final List<String> SCREEN_FILES = List.of(
            "qna-list.json", "qna-create.json", "qna-detail.json",
            "qna-answer-list.json", "qna-answer-detail.json", "qna-answer-create.json");

    private final KrdsRuntimeContractImportService contractReader;
    private final ComponentRegistryRepository registryRepository;
    private final ScreenPatternRepository patternRepository;
    private final VariantRuleSetRepository ruleSetRepository;
    private final DesignSystemProfileRepository profileRepository;
    private final FigmaScreenSpecRepository screenRepository;
    private final FigmaLibraryInventoryRepository inventoryRepository;
    private final ObjectMapper objectMapper;

    public KrdsQnaFixtureBootstrapService(
            KrdsRuntimeContractImportService contractReader,
            ComponentRegistryRepository registryRepository,
            ScreenPatternRepository patternRepository,
            VariantRuleSetRepository ruleSetRepository,
            DesignSystemProfileRepository profileRepository,
            FigmaScreenSpecRepository screenRepository,
            FigmaLibraryInventoryRepository inventoryRepository,
            ObjectMapper objectMapper) {
        this.contractReader = contractReader;
        this.registryRepository = registryRepository;
        this.patternRepository = patternRepository;
        this.ruleSetRepository = ruleSetRepository;
        this.profileRepository = profileRepository;
        this.screenRepository = screenRepository;
        this.inventoryRepository = inventoryRepository;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    @Transactional
    public BootstrapResult bootstrap() {
        var contracts = contractReader.readDefaultQnaContracts();
        ComponentRegistry registry = contracts.registry();
        VariantRuleSet publishedRules = new VariantRuleSet(
                contracts.ruleSet().id(), contracts.ruleSet().version(),
                contracts.ruleSet().profileId(), contracts.ruleSet().registryVersion(),
                VariantRuleSet.Status.PUBLISHED, contracts.ruleSet().rules());

        saveRegistry(registry);
        contracts.patterns().forEach(patternRepository::saveImmutable);
        ruleSetRepository.saveImmutable(publishedRules);

        DesignSystemProfile profile = new DesignSystemProfile(
                registry.profileId(), "KRDS Q&A 6 Screens", registry.profileVersion(),
                registry.registryVersion(), registry.library().fileKey(),
                DesignSystemProfile.Status.PUBLISHED, Map.of(), Map.of());
        profileRepository.findVersion(profile.id(), profile.version()).ifPresent(existing -> {
            if (!existing.equals(profile)) throw new IllegalStateException(
                    "DESIGN_SYSTEM_PROFILE_VERSION_CONFLICT: " + profile.id() + "/" + profile.version());
        });
        if (profileRepository.findVersion(profile.id(), profile.version()).isEmpty()) profileRepository.save(profile);

        FigmaLibraryInventorySnapshot inventory = inventory(registry);
        inventoryRepository.saveImmutable(inventory);

        List<String> screenIds = new ArrayList<>();
        for (String file : SCREEN_FILES) {
            FigmaScreenSpec fixture = readScreen(file);
            FigmaScreenSpec approved = approve(enrichListFixture(fixture));
            validateScreenVersions(approved, profile, publishedRules);
            screenRepository.save(approved);
            screenIds.add(approved.screenId());
        }
        return new BootstrapResult(profile.id(), profile.version(), registry.registryVersion(),
                publishedRules.id(), publishedRules.version(), contracts.patterns().size(),
                INVENTORY_VERSION, List.copyOf(screenIds));
    }

    private void saveRegistry(ComponentRegistry registry) {
        registryRepository.findVersion(registry.profileId(), registry.registryVersion()).ifPresent(existing -> {
            if (!existing.equals(registry)) throw new IllegalStateException(
                    "COMPONENT_REGISTRY_VERSION_CONFLICT: " + registry.profileId() + "/" + registry.registryVersion());
        });
        if (registryRepository.findVersion(registry.profileId(), registry.registryVersion()).isEmpty()) {
            registryRepository.saveImmutable(registry);
        }
    }

    private FigmaLibraryInventorySnapshot inventory(ComponentRegistry registry) {
        Map<String, LibraryComponentSnapshot> components = new LinkedHashMap<>();
        registry.components().forEach((logicalType, entry) -> {
            Map<String, ActualProperty> properties = new LinkedHashMap<>();
            entry.properties().values().forEach(mapping -> properties.put(mapping.figmaProperty(),
                    new ActualProperty(mapping.type().name(), new LinkedHashSet<>(mapping.values().values()))));
            entry.variantAxes().values().forEach(axis -> properties.put(axis.figmaProperty(),
                    new ActualProperty(ComponentRegistryEntry.PropertyType.VARIANT.name(), axis.allowedValues())));
            components.put(logicalType, new LibraryComponentSnapshot(
                    entry.componentSetKey(), properties, entry.variants()));
        });
        return new FigmaLibraryInventorySnapshot(registry.profileId(), registry.registryVersion(),
                INVENTORY_VERSION, Instant.parse("2026-08-12T00:00:00Z"), components);
    }

    private FigmaScreenSpec readScreen(String file) {
        try (InputStream input = new ClassPathResource("figma/contracts/qna/v2/" + file).getInputStream()) {
            return objectMapper.readValue(input, FigmaScreenSpec.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Q&A Screen Fixture를 읽을 수 없습니다: " + file, exception);
        }
    }

    private FigmaScreenSpec approve(FigmaScreenSpec source) {
        return new FigmaScreenSpec(source.screenId(), source.screenVersion(),
                source.screenSpecificationId(), source.screenSpecificationVersion(), source.screenType(),
                source.layoutPattern(), source.name(), source.route(), source.viewport(), "APPROVED",
                source.designSystem(), source.content(), source.issues(), source.semanticPattern(),
                source.screenPatternVersion(), source.variantRuleSetVersion(), source.componentContractVersion());
    }

    /** v2 원본의 단일 Cell placeholder를 6열×(Header+3행) Table Recipe로 승격한다. */
    private FigmaScreenSpec enrichListFixture(FigmaScreenSpec source) {
        if (source.semanticPattern() != com.krdevops.springai.model.design.role.ScreenPattern.CRUD_LIST) return source;
        FigmaNodeSpec template = findFirstResolvedCell(source.content());
        List<String> headers = List.of("번호", "제목", "작성자", "등록일", "처리상태", "답변상태");
        List<List<String>> samples = source.screenId().contains("answer")
                ? List.of(
                    List.of("103", "배송 문의드립니다", "김민수", "2026.08.12", "접수", "답변대기"),
                    List.of("102", "회원정보 수정 문의", "이서연", "2026.08.11", "처리중", "답변작성"),
                    List.of("101", "서비스 이용 방법", "박지훈", "2026.08.10", "완료", "답변완료"))
                : List.of(
                    List.of("103", "배송 일정은 어떻게 확인하나요?", "김민수", "2026.08.12", "공개", "답변대기"),
                    List.of("102", "회원정보를 수정하고 싶습니다", "이서연", "2026.08.11", "공개", "답변완료"),
                    List.of("101", "서비스 이용 방법을 알려주세요", "박지훈", "2026.08.10", "비공개", "답변완료"));
        List<FigmaNodeSpec> rows = new ArrayList<>();
        rows.add(tableRow(source.screenId(), "header", headers, template, true));
        for (int index = 0; index < samples.size(); index++) {
            rows.add(tableRow(source.screenId(), "row-" + (index + 1), samples.get(index), template, false));
        }
        FigmaNodeSpec table = new FigmaNodeSpec(source.screenId() + "/table",
                FigmaNodeSpec.NodeType.SECTION, "krds.dataTable",
                Map.of("semanticRole", "data.table", "layoutRecipe", "krds.dataTable.v1",
                        "columnCount", 6, "sampleRowCount", 3), rows);
        List<FigmaNodeSpec> children = new ArrayList<>();
        source.content().children().stream()
                .filter(node -> "page.header".equals(node.properties().get("semanticRole")))
                .forEach(children::add);
        source.content().children().stream()
                .filter(node -> "search.panel".equals(node.properties().get("semanticRole")))
                .map(this::constrainSearchPanel)
                .forEach(children::add);
        children.add(table);
        source.content().children().stream()
                .filter(node -> "data.pagination".equals(node.properties().get("semanticRole")))
                .forEach(children::add);
        List<FigmaNodeSpec> actions = source.content().children().stream()
                .filter(node -> node.type().equals("egov.actionArea")
                        || String.valueOf(node.properties().get("semanticRole")).startsWith("action."))
                .toList();
        if (!actions.isEmpty()) {
            if (actions.size() == 1 && actions.get(0).type().equals("egov.actionArea")) children.add(actions.get(0));
            else children.add(new FigmaNodeSpec(source.screenId() + "/action", FigmaNodeSpec.NodeType.SECTION,
                    "egov.actionArea", Map.of("placement", "BOTTOM_RIGHT"), actions));
        }
        FigmaNodeSpec content = new FigmaNodeSpec(source.content().logicalNodeId(), source.content().nodeType(),
                source.content().type(), Map.of("layoutRecipe", "krds.listPage.v1",
                        "contentMaxWidth", 1280, "sectionGap", 40), children);
        return new FigmaScreenSpec(source.screenId(), 6, source.screenSpecificationId(),
                source.screenSpecificationVersion(), source.screenType(), source.layoutPattern(), source.name(),
                source.route(), source.viewport(), source.status(), source.designSystem(), content, source.issues(),
                source.semanticPattern(), source.screenPatternVersion(), source.variantRuleSetVersion(),
                source.componentContractVersion());
    }

    private FigmaNodeSpec constrainSearchPanel(FigmaNodeSpec source) {
        Map<String, Object> properties = new java.util.LinkedHashMap<>(source.properties());
        properties.put("componentMaxWidth", 960);
        return new FigmaNodeSpec(source.logicalNodeId(), source.nodeType(), source.type(),
                Map.copyOf(properties), source.componentResolution(), source.children());
    }

    private FigmaNodeSpec tableRow(
            String screenId, String rowId, List<String> values, FigmaNodeSpec template, boolean header) {
        List<FigmaNodeSpec> cells = java.util.stream.IntStream.range(0, values.size())
                .mapToObj(index -> resolvedCell(screenId, rowId, index, values.get(index), template, header))
                .toList();
        return new FigmaNodeSpec(screenId + "/table/" + rowId,
                header ? FigmaNodeSpec.NodeType.SECTION : FigmaNodeSpec.NodeType.REPEAT,
                header ? "krds.dataTable.header" : "krds.dataTable.row",
                Map.of("rowType", header ? "HEADER" : "BODY"), cells);
    }

    private FigmaNodeSpec resolvedCell(
            String screenId, String rowId, int index, String value, FigmaNodeSpec template, boolean header) {
        ResolvedComponentRef source = template.componentResolution();
        String property = source.componentProperties().keySet().stream().findFirst().orElse("Body#288:22");
        ResolvedComponentRef resolution = new ResolvedComponentRef(
                source.role(), source.logicalType(), source.componentSetKey(), source.variantKey(),
                source.variantProperties(), Map.of(property, value), source.contractVersion(),
                source.ruleSetVersion(), source.ruleId(), source.contextHash() + ":" + rowId + ":" + index);
        return new FigmaNodeSpec(screenId + "/table/" + rowId + "/cell-" + (index + 1),
                FigmaNodeSpec.NodeType.COMPONENT, "krds.tableCell",
                Map.of("semanticRole", "data.table.cell", "label", value,
                        "columnIndex", index, "columnWidthPercent", columnWidthPercent(index),
                        "header", header), resolution, List.of());
    }

    /** 번호 8%, 제목 32%, 나머지 컬럼 15% 비율. 합계 100이며 Header와 Body에 동일하게 적용한다. */
    private int columnWidthPercent(int index) {
        return switch (index) {
            case 0 -> 8;
            case 1 -> 32;
            default -> 15;
        };
    }

    private FigmaNodeSpec findFirstResolvedCell(FigmaNodeSpec node) {
        if ("krds.tableCell".equals(node.type()) && node.componentResolution() != null) return node;
        for (FigmaNodeSpec child : node.children()) {
            try { return findFirstResolvedCell(child); }
            catch (IllegalStateException ignored) { }
        }
        throw new IllegalStateException("QNA_LIST_TABLE_CELL_TEMPLATE_MISSING: " + node.logicalNodeId());
    }

    private void validateScreenVersions(FigmaScreenSpec screen, DesignSystemProfile profile, VariantRuleSet rules) {
        if (!screen.designSystem().profileId().equals(profile.id())
                || !screen.designSystem().profileVersion().equals(profile.version())
                || !screen.designSystem().registryVersion().equals(profile.registryVersion())
                || !screen.variantRuleSetVersion().equals(rules.version())) {
            throw new IllegalStateException("QNA_SCREEN_VERSION_MISMATCH: " + screen.screenId());
        }
        assertResolved(screen.content());
    }

    private void assertResolved(FigmaNodeSpec node) {
        if (node.nodeType() == FigmaNodeSpec.NodeType.COMPONENT && node.componentResolution() == null) {
            throw new IllegalStateException("QNA_SCREEN_UNRESOLVED_COMPONENT: " + node.logicalNodeId());
        }
        node.children().forEach(this::assertResolved);
    }

    public record BootstrapResult(
            String profileId, String profileVersion, String registryVersion,
            String ruleSetId, String ruleSetVersion, int patternCount,
            String inventoryVersion, List<String> screenIds) {}
}
