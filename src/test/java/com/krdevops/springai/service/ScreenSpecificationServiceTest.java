package com.krdevops.springai.service;

import com.krdevops.springai.mapper.ScreenSpecRepository;
import com.krdevops.springai.model.design.DataSourceSpec;
import com.krdevops.springai.model.design.ActionPlacement;
import com.krdevops.springai.model.design.FieldSource;
import com.krdevops.springai.model.design.FormColumnLayout;
import com.krdevops.springai.model.design.LayoutDensity;
import com.krdevops.springai.model.design.SearchPanelPlacement;
import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenFieldBinding;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.UiFieldRole;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScreenSpecificationServiceTest {

    @Test
    void revisionValidatesPhysicalColumnAndIncrementsVersion() {
        CrudSchemaQueryService schema = mock(CrudSchemaQueryService.class);
        ScreenSpecRepository repository = mock(ScreenSpecRepository.class);
        ScreenDataBindingResolver resolver = mock(ScreenDataBindingResolver.class);
        ScreenSpecificationService service = new ScreenSpecificationService(
                schema, mock(ScreenSpecAssembler.class), resolver, new ScreenSpecValidator(), repository);
        ScreenSpecification current = specification(1, "NTT_SJ");
        when(repository.findLatest("spec-1")).thenReturn(Optional.of(current));
        when(schema.fetchColumns("com", "LETTNBBS"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "NTT_SJ")));

        ScreenSpecification revised = service.revise(specification(1, "NTT_SJ"));

        assertThat(revised.version()).isEqualTo(2);
        assertThat(revised.status()).isEqualTo(ScreenSpecStatus.APPROVED);
        verify(repository).save(revised);
    }

    @Test
    void revisionPreservesCurrentLayoutDensity() {
        CrudSchemaQueryService schema = mock(CrudSchemaQueryService.class);
        ScreenSpecRepository repository = mock(ScreenSpecRepository.class);
        ScreenSpecificationService service = new ScreenSpecificationService(
                schema, mock(ScreenSpecAssembler.class), mock(ScreenDataBindingResolver.class),
                new ScreenSpecValidator(), repository);
        ScreenSpecification current = specification(1, "NTT_SJ", LayoutDensity.COMPACT);
        ScreenSpecification proposed = specification(1, "NTT_SJ", LayoutDensity.COMFORTABLE);
        when(repository.findLatest("spec-1")).thenReturn(Optional.of(current));
        when(schema.fetchColumns("com", "LETTNBBS"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "NTT_SJ")));

        ScreenSpecification revised = service.revise(proposed);

        assertThat(revised.layoutDensity()).isEqualTo(LayoutDensity.COMPACT);
    }

    @Test
    void revisionPreservesCurrentFormColumnLayout() {
        CrudSchemaQueryService schema = mock(CrudSchemaQueryService.class);
        ScreenSpecRepository repository = mock(ScreenSpecRepository.class);
        ScreenSpecificationService service = new ScreenSpecificationService(
                schema, mock(ScreenSpecAssembler.class), mock(ScreenDataBindingResolver.class),
                new ScreenSpecValidator(), repository);
        ScreenSpecification current = specification(
                1, "NTT_SJ", LayoutDensity.STANDARD, FormColumnLayout.TWO_COLUMN);
        ScreenSpecification proposed = specification(
                1, "NTT_SJ", LayoutDensity.STANDARD, FormColumnLayout.SINGLE_COLUMN);
        when(repository.findLatest("spec-1")).thenReturn(Optional.of(current));
        when(schema.fetchColumns("com", "LETTNBBS"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "NTT_SJ")));

        ScreenSpecification revised = service.revise(proposed);

        assertThat(revised.formColumnLayout()).isEqualTo(FormColumnLayout.TWO_COLUMN);
    }

    @Test
    void revisionPreservesCurrentActionAndSearchPanelPlacement() {
        CrudSchemaQueryService schema = mock(CrudSchemaQueryService.class);
        ScreenSpecRepository repository = mock(ScreenSpecRepository.class);
        ScreenSpecificationService service = new ScreenSpecificationService(
                schema, mock(ScreenSpecAssembler.class), mock(ScreenDataBindingResolver.class),
                new ScreenSpecValidator(), repository);
        ScreenSpecification current = specification(
                1, "NTT_SJ", LayoutDensity.STANDARD, FormColumnLayout.SINGLE_COLUMN,
                ActionPlacement.BOTTOM_RIGHT, SearchPanelPlacement.NONE);
        ScreenSpecification proposed = specification(
                1, "NTT_SJ", LayoutDensity.STANDARD, FormColumnLayout.SINGLE_COLUMN,
                ActionPlacement.TOP_RIGHT, SearchPanelPlacement.ABOVE_TABLE);
        when(repository.findLatest("spec-1")).thenReturn(Optional.of(current));
        when(schema.fetchColumns("com", "LETTNBBS"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "NTT_SJ")));

        ScreenSpecification revised = service.revise(proposed);

        assertThat(revised.actionPlacement()).isEqualTo(ActionPlacement.BOTTOM_RIGHT);
        assertThat(revised.searchPanelPlacement()).isEqualTo(SearchPanelPlacement.NONE);
    }

    private ScreenSpecification specification(int version, String column) {
        return specification(version, column, LayoutDensity.STANDARD, FormColumnLayout.SINGLE_COLUMN);
    }

    private ScreenSpecification specification(int version, String column, LayoutDensity density) {
        return specification(version, column, density, FormColumnLayout.SINGLE_COLUMN);
    }

    private ScreenSpecification specification(
            int version, String column, LayoutDensity density, FormColumnLayout formColumnLayout) {
        return specification(version, column, density, formColumnLayout,
                ActionPlacement.TOP_RIGHT, SearchPanelPlacement.ABOVE_TABLE);
    }

    private ScreenSpecification specification(
            int version, String column, LayoutDensity density, FormColumnLayout formColumnLayout,
            ActionPlacement actionPlacement, SearchPanelPlacement searchPanelPlacement) {
        ScreenFieldBinding title = new ScreenFieldBinding(
                "title", "제목", UiFieldRole.TITLE, FieldSource.column("t", column),
                true, true, true, true, "TEXT", 1.0);
        return new ScreenSpecification(
                "spec-1", version, ScreenSpecStatus.REVIEW_REQUIRED, "공지", "board", "BOARD",
                "com", "LETTNBBS", List.of(DataSourceSpec.primary("com", "LETTNBBS")),
                List.of(new PageSpec("list", "BOARD_LIST", List.of(title), PageSpec.migrateActions("SEARCH"))),
                List.of(), density, formColumnLayout, actionPlacement, searchPanelPlacement, LocalDateTime.now());
    }
}
