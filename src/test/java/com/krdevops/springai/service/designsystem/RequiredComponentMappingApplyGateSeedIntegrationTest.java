package com.krdevops.springai.service.designsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.PipelineEvolutionProperties;
import com.krdevops.springai.mapper.DesignCodeComponentMappingRepository;
import com.krdevops.springai.model.contract.VersionedArtifactReference;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.UiDesignSpecV2;
import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import com.krdevops.springai.model.designsystem.DesignComponentRenderInput;
import com.krdevops.springai.service.UiDesignSpecArtifactReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * B4 검증 관문 — {@link ThymeleafKrdsComponentMappingSeeder}가 적재하는 실제 6종 시드 Mapping을
 * {@link RequiredComponentMappingApplyGate}에 통과시켜, V2 Apply 경로가 6개 {@link DesignComponentRenderInput}을
 * 확정하고(승인 Mapping 존재), 하나라도 빠지면 "승인 Mapping 누락"으로 fail-closed 함을 확인한다.
 *
 * <p>Mapping 내부 계약(Fixture Adapter·RenderInput 해석)은
 * {@link ThymeleafKrdsComponentMappingSeederTest}가, Gate 단위 동작은
 * {@link RequiredComponentMappingApplyGateTest}가 각각 커버한다. 이 테스트는 <b>시드 데이터 ↔ Gate
 * end-to-end</b>만 본다.
 */
class RequiredComponentMappingApplyGateSeedIntegrationTest {

    private static final List<String> LOGICAL_TYPES = List.of(
            "button", "text-input", "select", "date-input", "data-table", "pagination");
    private static final String UI_SPEC_ID = "ui-spec-krds";

    private final PipelineEvolutionProperties properties = new PipelineEvolutionProperties();
    private final UiDesignSpecArtifactReader reader = mock(UiDesignSpecArtifactReader.class);
    private final UiDesignSpecArtifactReader.ReadResult readResult =
            mock(UiDesignSpecArtifactReader.ReadResult.class);
    private final DesignCodeComponentMappingRepository repository =
            mock(DesignCodeComponentMappingRepository.class);

    private final List<DesignCodeComponentMapping> seeded = new ThymeleafKrdsComponentMappingSeeder(
            mock(DesignCodeComponentMappingRepository.class),
            new DesignCodeComponentMappingHashService(new ObjectMapper())).mappings();

    private final RequiredComponentMappingApplyGate gate = new RequiredComponentMappingApplyGate(
            properties, reader, repository, new ComponentFixtureModelAdapter(),
            new DesignComponentRenderInputService(repository,
                    new ComponentPropertyParameterResolver(), new ComponentVariantValueResolver(),
                    new ComponentSlotRegionResolver()));

    @BeforeEach
    void enableV2Apply() {
        properties.setMode(PipelineEvolutionProperties.Mode.V2_APPLY);
        when(reader.read(UI_SPEC_ID)).thenReturn(readResult);
        when(readResult.spec()).thenReturn(uiSpecWithAllComponents());
    }

    @Test
    void 시드_6종이_모두_승인되어_있으면_6개_RenderInput을_확정한다() {
        stubRepositoryExcluding(Set.of());

        List<DesignComponentRenderInput> inputs =
                gate.requireForApply(screenSpecification(), "thymeleaf-krds");

        assertThat(inputs).hasSize(6)
                .extracting(DesignComponentRenderInput::logicalType)
                .containsExactlyInAnyOrderElementsOf(LOGICAL_TYPES);
        assertThat(inputs).extracting(DesignComponentRenderInput::thymeleafFragment)
                .containsExactlyInAnyOrder(
                        "components/krds-button :: button",
                        "components/krds-text-input :: textInput",
                        "components/krds-select :: select",
                        "components/krds-date-input :: dateInput",
                        "components/krds-data-table :: dataTable",
                        "components/krds-pagination :: pagination");
        assertThat(inputs).allSatisfy(input ->
                assertThat(input.rendererProfile()).isEqualTo("thymeleaf-krds"));
    }

    @Test
    void select_시드가_빠지면_그_logicalType의_승인_Mapping_누락으로_Apply를_차단한다() {
        stubRepositoryExcluding(Set.of("select"));

        assertThatThrownBy(() -> gate.requireForApply(screenSpecification(), "thymeleaf-krds"))
                .isInstanceOfSatisfying(
                        RequiredComponentMappingApplyGate.RequiredComponentMappingException.class,
                        exception -> assertThat(exception.issues())
                                .containsExactly("승인 Mapping 누락: select/krds:select (thymeleaf-krds)"));
    }

    @Test
    void 여러_시드가_빠지면_모든_누락을_모아_보고한다() {
        stubRepositoryExcluding(Set.of("date-input", "pagination"));

        assertThatThrownBy(() -> gate.requireForApply(screenSpecification(), "thymeleaf-krds"))
                .isInstanceOfSatisfying(
                        RequiredComponentMappingApplyGate.RequiredComponentMappingException.class,
                        exception -> assertThat(exception.issues()).containsExactlyInAnyOrder(
                                "승인 Mapping 누락: date-input/krds:date-input (thymeleaf-krds)",
                                "승인 Mapping 누락: pagination/krds:pagination (thymeleaf-krds)"));
    }

    @Test
    void INSTANCE의_componentProperties가_시드_Fixture_기본값을_덮어쓴다() {
        UiDesignSpecV2.InferenceEvidence evidence = new UiDesignSpecV2.InferenceEvidence(
                List.of("1:2"), 0.99, "figma-component", false, false);
        // button 인스턴스가 variant=secondary, size=large를 명시 (시드 Fixture 기본값은 primary/medium)
        UiDesignSpecV2.ComponentReference buttonRef = new UiDesignSpecV2.ComponentReference(
                "button", "krds:button", null, Map.of("Type", "secondary", "Size", "large"));
        UiDesignSpecV2.SemanticNode buttonNode = new UiDesignSpecV2.SemanticNode(
                "button-node", "field", "button", null, Map.of(), buttonRef,
                List.of(), List.of(), evidence);
        UiDesignSpecV2 spec = new UiDesignSpecV2(
                UI_SPEC_ID, "2.0", "c".repeat(64),
                new UiDesignSpecV2.Source(
                        UiDesignSpecV2.SourceType.FIGMA, "file", "1:1", "krds-v1.0.0"),
                null, List.of(buttonNode), List.of(), List.of(), List.of(), List.of(), 0.99);
        when(readResult.spec()).thenReturn(spec);
        stubRepositoryExcluding(Set.of());

        DesignComponentRenderInput button = gate.requireForApply(screenSpecification(), "thymeleaf-krds")
                .stream().filter(input -> input.logicalType().equals("button")).findFirst().orElseThrow();

        assertThat(button.fragmentParameters())
                .containsEntry("variant", "secondary")   // 인스턴스 값
                .containsEntry("size", "large")           // 인스턴스 값
                .containsEntry("label", "버튼");           // 인스턴스 미지정 → 시드 Fixture 기본값 유지
    }

    private void stubRepositoryExcluding(Set<String> excludedLogicalTypes) {
        when(repository.findApproved(anyString(), anyString(), eq("thymeleaf-krds")))
                .thenAnswer(invocation -> {
                    String logicalType = invocation.getArgument(0);
                    String componentSetKey = invocation.getArgument(1);
                    if (excludedLogicalTypes.contains(logicalType)) {
                        return Optional.empty();
                    }
                    return seeded.stream()
                            .filter(mapping -> mapping.logicalType().equals(logicalType)
                                    && mapping.figmaComponentSetKey().equals(componentSetKey))
                            .findFirst();
                });
    }

    private UiDesignSpecV2 uiSpecWithAllComponents() {
        UiDesignSpecV2.InferenceEvidence evidence = new UiDesignSpecV2.InferenceEvidence(
                List.of("1:2"), 0.99, "figma-component", false, false);
        List<UiDesignSpecV2.SemanticNode> nodes = new ArrayList<>();
        for (String logicalType : LOGICAL_TYPES) {
            UiDesignSpecV2.ComponentReference reference = new UiDesignSpecV2.ComponentReference(
                    logicalType, "krds:" + logicalType, null);
            nodes.add(new UiDesignSpecV2.SemanticNode(
                    logicalType + "-node", "field", logicalType, null, Map.of(), reference,
                    List.of(), List.of(), evidence));
        }
        return new UiDesignSpecV2(
                UI_SPEC_ID, "2.0", "c".repeat(64),
                new UiDesignSpecV2.Source(
                        UiDesignSpecV2.SourceType.FIGMA, "file", "1:1", "krds-v1.0.0"),
                null, nodes, List.of(), List.of(), List.of(), List.of(), 0.99);
    }

    private ScreenSpecification screenSpecification() {
        return new ScreenSpecification(
                "screen-krds", 1, ScreenSpecStatus.APPROVED, "직원", "crud", "table",
                "ebt", "LETTNEMPLYRINFO", List.of(), List.of(), List.of(),
                null, null, null, null, LocalDateTime.now(),
                new VersionedArtifactReference(UI_SPEC_ID, "UI_DESIGN_SPEC_V2", "2.0",
                        "c".repeat(64), "krds-v1.0.0"),
                null);
    }
}
