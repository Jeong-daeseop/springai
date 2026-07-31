package com.krdevops.springai.service.generation.source;

import com.krdevops.springai.model.crud.CrudProgramMetadata;
import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.model.crud.ScreenSubsetMode;
import com.krdevops.springai.model.masterdetail.MasterDetailLayerDefinition;
import com.krdevops.springai.model.masterdetail.MasterDetailTemplateModel;
import com.krdevops.springai.service.CrudModelFactory;
import com.krdevops.springai.service.CrudSchemaQueryService;
import com.krdevops.springai.service.MasterDetailTemplateRenderer;
import com.krdevops.springai.service.generation.model.FeatureType;
import com.krdevops.springai.service.generation.model.GenerateScreenSourceCommand;
import com.krdevops.springai.service.generation.model.GeneratedSource;
import com.krdevops.springai.util.CrudMappingUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 마스터-디테일 마스터 화면(List/Detail/Regist/Updt) 1개를 렌더링한다. 파일은 저장하지 않는다.
 *
 * <p>{@code detectFkColumn}/{@code deriveDetailDomain}은 {@code MasterDetailOrchestrationService}에도
 * 동일 로직이 중복되어 있다(WP-6 범위 밖, 의도적으로 남겨둔 중복).
 */
@Component
@RequiredArgsConstructor
public class MasterDetailScreenSourceGenerator implements ScreenSourceGenerator {

    private final CrudSchemaQueryService crudSchemaQueryService;
    private final CrudModelFactory crudModelFactory;
    private final MasterDetailTemplateRenderer masterDetailTemplateRenderer;

    @Override
    public boolean supports(FeatureType featureType) {
        return featureType == FeatureType.MASTER_DETAIL;
    }

    @Override
    public GeneratedSource generate(GenerateScreenSourceCommand command) {
        String database = command.database();
        String masterTable = command.primaryTable();
        String detailTable = command.secondaryTable();

        List<Map<String, Object>> masterColumns = crudSchemaQueryService.fetchColumns(database, masterTable);
        if (masterColumns.isEmpty()) {
            throw new ScreenSourceNotFoundException("마스터 테이블을 찾을 수 없습니다: " + database + "." + masterTable);
        }
        List<Map<String, Object>> detailColumns = crudSchemaQueryService.fetchColumns(database, detailTable);
        if (detailColumns.isEmpty()) {
            throw new ScreenSourceNotFoundException("디테일 테이블을 찾을 수 없습니다: " + database + "." + detailTable);
        }

        String resolvedVersion = resolveEgovVersion(command.egovVersion());
        CrudViewType viewType = CrudViewType.from(command.viewType());
        String detailDomain = deriveDetailDomain(detailTable);
        CrudTemplateModel masterModel = crudModelFactory.fromSchema(
                masterTable, command.domain(), command.packageName(), resolvedVersion, masterColumns,
                CrudProgramMetadata.fallback(null), viewType, ScreenSubsetMode.NONE, null);
        CrudTemplateModel detailModel = crudModelFactory.fromSchema(
                detailTable, detailDomain, command.packageName(), resolvedVersion, detailColumns,
                CrudProgramMetadata.fallback(null), viewType, ScreenSubsetMode.NONE, null);
        String fkColumn = detectFkColumn(masterModel.pk().columnName(), detailColumns);
        MasterDetailTemplateModel model = new MasterDetailTemplateModel(
                masterModel, detailModel, fkColumn, CrudMappingUtils.toCamelCase(fkColumn));
        String layerKey = layerKey(viewType, command.screenType().label());
        String code = masterDetailTemplateRenderer.renderByLayerKey(layerKey, model);
        Path recommendedPath = resolveScreenPath(command.outputPath(), command.packageName(), masterModel.domainLc(),
                command.domain(), detailDomain, viewType, layerKey);
        return new GeneratedSource(
                FeatureType.MASTER_DETAIL, command.domain(), command.screenType(), viewType, layerKey,
                recommendedPath, code);
    }

    private Path resolveScreenPath(
            Path outputPath, String packageName, String domainLc, String masterDomain, String detailDomain,
            CrudViewType viewType, String layerKey) {
        MasterDetailLayerDefinition layer = MasterDetailLayerDefinition.forViewType(viewType).stream()
                .filter(candidate -> candidate.layerKey().equals(layerKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 layerKey: " + layerKey));
        String pkgSub = packageName.replace("egovframework.let.", "").replace(".", "/");
        String fileName = MasterDetailLayerDefinition.resolveFileName(layer, masterDomain, detailDomain);
        return Path.of(outputPath + "/" + layer.resolveSubPath(pkgSub, domainLc) + fileName);
    }

    private String layerKey(CrudViewType viewType, String screenLabel) {
        return (viewType == CrudViewType.THYMELEAF ? "thymeleaf" : "jsp") + screenLabel;
    }

    private String resolveEgovVersion(String egovVersion) {
        return (egovVersion == null || egovVersion.isBlank()) ? "5.0" : egovVersion;
    }

    private String detectFkColumn(String masterPkColumn, List<Map<String, Object>> detailColumns) {
        return detailColumns.stream()
                .map(c -> (String) c.get("COLUMN_NAME"))
                .filter(masterPkColumn::equals)
                .findFirst()
                .orElse(masterPkColumn);
    }

    private String deriveDetailDomain(String tableName) {
        String base = tableName.replace("LETTN", "").replace("LETTS", "").replace("LETTC", "");
        String[] parts = base.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isBlank()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.toString();
    }
}
