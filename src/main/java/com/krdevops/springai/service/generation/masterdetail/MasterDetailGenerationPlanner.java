package com.krdevops.springai.service.generation.masterdetail;

import com.krdevops.springai.model.crud.CrudLayoutMode;
import com.krdevops.springai.model.crud.CrudProgramMetadata;
import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.model.crud.ScreenSubsetMode;
import com.krdevops.springai.model.masterdetail.MasterDetailTemplateModel;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.service.CrudModelFactory;
import com.krdevops.springai.service.CrudSchemaQueryService;
import com.krdevops.springai.service.GenerationDesignContextService;
import com.krdevops.springai.service.MasterDetailTemplateRenderer;
import com.krdevops.springai.service.ThymeleafLayoutValidator;
import com.krdevops.springai.util.CrudMappingUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MasterDetailGenerationPlanner {

    private final CrudSchemaQueryService schemaQueryService;
    private final CrudModelFactory modelFactory;
    private final GenerationDesignContextService designContextService;
    private final ThymeleafLayoutValidator layoutValidator;
    private final MasterDetailRelationResolver relationResolver;

    public MasterDetailGenerationPlan plan(MasterDetailGenerationCommand command) {
        if (command.packageName() == null || !command.packageName().startsWith("egovframework.let.")) {
            return MasterDetailGenerationPlan.rejected(new MasterDetailPlanFailure(
                    MasterDetailPlanFailure.Kind.INVALID_PACKAGE,
                    "packageName은 egovframework.let.* 형식이어야 합니다", List.of(command.packageName())));
        }
        List<Map<String, Object>> masterColumns = schemaQueryService.fetchColumns(command.database(), command.masterTable());
        List<Map<String, Object>> detailColumns = schemaQueryService.fetchColumns(command.database(), command.detailTable());
        if (masterColumns.isEmpty() || detailColumns.isEmpty()) {
            return MasterDetailGenerationPlan.rejected(new MasterDetailPlanFailure(
                    MasterDetailPlanFailure.Kind.TABLE_NOT_FOUND, "마스터 또는 디테일 테이블을 찾지 못했습니다",
                    List.of(command.masterTable(), command.detailTable())));
        }
        CrudViewType viewType = CrudViewType.from(command.viewType());
        ScreenSpecification spec = designContextService.resolve(command.database(), command.masterTable(),
                command.domain(), "master-detail", command.designContext().designReferenceId(),
                command.designContext().screenSpecificationId());
        String detailDomain = deriveDetailDomain(command.detailTable());
        CrudTemplateModel master = modelFactory.fromSchema(command.masterTable(), command.domain(), command.packageName(),
                command.egovVersion(), masterColumns, CrudProgramMetadata.fallback(null), viewType,
                ScreenSubsetMode.NONE, spec);
        CrudTemplateModel detail = modelFactory.fromSchema(command.detailTable(), detailDomain, command.packageName(),
                command.egovVersion(), detailColumns, CrudProgramMetadata.fallback(null), viewType,
                ScreenSubsetMode.NONE, null);
        MasterDetailRelationResolver.Relation relation = relationResolver.resolve(
                master.pk().columnName(), detailColumns);
        MasterDetailTemplateModel model = new MasterDetailTemplateModel(
                master, detail, relation.fkColumn(), relation.fkField());
        CrudLayoutMode layoutMode = viewType == CrudViewType.THYMELEAF
                ? CrudLayoutMode.from(command.layout().layoutMode()) : CrudLayoutMode.CREATE;
        ThymeleafLayoutValidator.LayoutReference reference = viewType == CrudViewType.THYMELEAF
                ? layoutValidator.resolve(command.layout().layoutView(), command.layout().breadcrumbView())
                : layoutValidator.resolve(null, null);
        if (viewType == CrudViewType.THYMELEAF && layoutMode == CrudLayoutMode.REUSE) {
            var validation = layoutValidator.validateExisting(command.outputPath().toString(),
                    reference.layoutView(), reference.breadcrumbView());
            if (!validation.valid()) {
                return MasterDetailGenerationPlan.rejected(new MasterDetailPlanFailure(
                        MasterDetailPlanFailure.Kind.LAYOUT_MISSING, "layout 검증 실패",
                        List.of(layoutValidator.missingLayoutMessage(command.outputPath().toString(), validation))));
            }
        }
        return new MasterDetailGenerationPlan(model, spec, viewType, layoutMode, reference, List.of(), null);
    }

    private static String deriveDetailDomain(String table) {
        String value = table == null ? "Detail" : table.replaceFirst("(?i)^LETTN", "");
        String camel = CrudMappingUtils.toCamelCase(value.replaceAll("[^A-Za-z0-9가-힣]", " ").trim());
        return camel.isBlank() ? "Detail" : Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
    }
}
