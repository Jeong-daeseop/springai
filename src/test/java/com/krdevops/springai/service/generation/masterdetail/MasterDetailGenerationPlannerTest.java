package com.krdevops.springai.service.generation.masterdetail;

import com.krdevops.springai.service.CrudModelFactory;
import com.krdevops.springai.service.CrudSchemaQueryService;
import com.krdevops.springai.service.GenerationDesignContextService;
import com.krdevops.springai.service.ThymeleafLayoutValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MasterDetailGenerationPlannerTest {

    @Mock CrudSchemaQueryService schema;
    @Mock CrudModelFactory modelFactory;
    @Mock GenerationDesignContextService designContext;
    @Mock ThymeleafLayoutValidator layoutValidator;
    @Mock MasterDetailRelationResolver relationResolver;

    @Test
    void invalidPackage_stopsBeforeSchemaLookup() {
        var planner = new MasterDetailGenerationPlanner(schema, modelFactory, designContext,
                layoutValidator, relationResolver);
        var command = command("com.example.order");

        var plan = planner.plan(command);

        assertThat(plan.failure().kind()).isEqualTo(MasterDetailPlanFailure.Kind.INVALID_PACKAGE);
        verifyNoInteractions(schema, modelFactory, designContext, relationResolver);
    }

    @Test
    void missingTable_returnsTypedFailure() {
        when(schema.fetchColumns("com", "MASTER")).thenReturn(List.of());
        when(schema.fetchColumns("com", "DETAIL")).thenReturn(List.of());
        var planner = new MasterDetailGenerationPlanner(schema, modelFactory, designContext,
                layoutValidator, relationResolver);

        var plan = planner.plan(command("egovframework.let.order"));

        assertThat(plan.failure().kind()).isEqualTo(MasterDetailPlanFailure.Kind.TABLE_NOT_FOUND);
        verifyNoInteractions(modelFactory, designContext, relationResolver);
    }

    private static MasterDetailGenerationCommand command(String packageName) {
        return new MasterDetailGenerationCommand("com", "MASTER", "DETAIL", "Order", packageName,
                Path.of("/tmp/out"), "auto", "5.0", "jsp", null, null);
    }
}
