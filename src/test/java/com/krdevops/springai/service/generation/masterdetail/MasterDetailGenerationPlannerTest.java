package com.krdevops.springai.service.generation.masterdetail;

import com.krdevops.springai.model.masterdetail.MasterDetailTemplateModel;
import com.krdevops.springai.service.CrudModelFactory;
import com.krdevops.springai.service.CrudSchemaQueryService;
import com.krdevops.springai.service.GenerationDesignContextService;
import com.krdevops.springai.service.ThymeleafLayoutValidator;
import com.krdevops.springai.service.generation.model.DesignContextReference;
import com.krdevops.springai.util.CrudMappingUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * MasterDetail은 자체 ModelFactory 없이 {@link CrudModelFactory}를 마스터/디테일에 각각
 * 한 번씩 재사용해서 조합한다(자체 Factory가 없는 것 자체가 설계 선택). 이 재사용·조합 가정을
 * 검증하는 테스트가 이전까지 없었다 — 아래 클래스는 그 공백을 메운다.
 */
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

    @Test
    void masterAndDetailAreComposedFromTwoIndependentCrudModelFactoryCalls() {
        CrudModelFactory realModelFactory = new CrudModelFactory();
        MasterDetailRelationResolver realRelationResolver = new MasterDetailRelationResolver();

        when(schema.fetchColumns("com", "LETTNORDER")).thenReturn(masterColumns());
        when(schema.fetchColumns("com", "LETTNORDERITEM")).thenReturn(detailColumns());
        when(designContext.resolve("com", "LETTNORDER", "Order", "master-detail", null, null))
                .thenReturn(null);
        when(layoutValidator.resolve(null, null))
                .thenReturn(new ThymeleafLayoutValidator.LayoutReference(null, null, null));

        var planner = new MasterDetailGenerationPlanner(schema, realModelFactory, designContext,
                layoutValidator, realRelationResolver);
        var command = new MasterDetailGenerationCommand("com", "LETTNORDER", "LETTNORDERITEM", "Order",
                "egovframework.let.order", Path.of("/tmp/out"), "auto", "5.0", "jsp",
                null, DesignContextReference.empty());

        var plan = planner.plan(command);

        assertThat(plan.failed()).isFalse();
        MasterDetailTemplateModel model = plan.model();

        // 마스터/디테일은 CrudModelFactory.fromSchema()를 각각 독립 호출한 결과 — urlPrefix가 서로 다르다
        assertThat(model.master().tableName()).isEqualTo("LETTNORDER");
        assertThat(model.detail().tableName()).isEqualTo("LETTNORDERITEM");
        assertThat(model.master().urlPrefix()).isEqualTo("/order/order");
        assertThat(model.detail().urlPrefix()).isNotEqualTo(model.master().urlPrefix());

        // deriveDetailDomain: "LETTN" 접두어 제거 후 PascalCase
        assertThat(model.detail().domain()).isEqualTo("Orderitem");

        // 관계 해석: 디테일에 마스터 PK와 동일한 이름의 컬럼이 있으면 그걸 FK로 채택
        assertThat(model.master().pk().columnName()).isEqualTo("ORDER_ID");
        assertThat(model.fkColumn()).isEqualTo("ORDER_ID");
        assertThat(model.fkField()).isEqualTo(CrudMappingUtils.toCamelCase("ORDER_ID"));

        // ScreenSubsetMode.NONE으로 호출되므로 두 모델 모두 detail/list가 화면명세로 축소되지 않고
        // 전체 컬럼(민감필드 제외) 기준으로 만들어진다 — screenSpecification=null인 경우와 동일한 폴백 경로.
        assertThat(model.master().detailFields()).extracting("columnName")
                .containsExactlyInAnyOrder("ORDER_ID", "ORDER_NM");
    }

    private static List<Map<String, Object>> masterColumns() {
        return List.of(
                column("ORDER_ID", "varchar", "PRI", "NO", "주문ID"),
                column("ORDER_NM", "varchar", "", "NO", "주문명"));
    }

    private static List<Map<String, Object>> detailColumns() {
        return List.of(
                column("ORDER_ITEM_ID", "varchar", "PRI", "NO", "주문상품ID"),
                column("ORDER_ID", "varchar", "", "NO", "주문ID(FK)"),
                column("ITEM_NM", "varchar", "", "NO", "상품명"));
    }

    private static Map<String, Object> column(
            String name, String dataType, String columnKey, String isNullable, String comment) {
        return Map.of(
                "COLUMN_NAME", name,
                "DATA_TYPE", dataType,
                "CHARACTER_MAXIMUM_LENGTH", 100,
                "IS_NULLABLE", isNullable,
                "COLUMN_KEY", columnKey,
                "COLUMN_COMMENT", comment);
    }
}
