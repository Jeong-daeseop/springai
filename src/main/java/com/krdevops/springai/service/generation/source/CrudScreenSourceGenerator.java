package com.krdevops.springai.service.generation.source;

import com.krdevops.springai.model.crud.CrudLayerDefinition;
import com.krdevops.springai.model.crud.CrudProgramMetadata;
import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.model.crud.ScreenSubsetMode;
import com.krdevops.springai.service.CrudModelFactory;
import com.krdevops.springai.service.CrudSchemaQueryService;
import com.krdevops.springai.service.CrudTemplateRenderer;
import com.krdevops.springai.service.generation.model.FeatureType;
import com.krdevops.springai.service.generation.model.GenerateScreenSourceCommand;
import com.krdevops.springai.service.generation.model.GeneratedSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** 단일 테이블 CRUD 화면(List/Detail/Regist/Updt) 1개를 렌더링한다. 파일은 저장하지 않는다. */
@Component
@RequiredArgsConstructor
public class CrudScreenSourceGenerator implements ScreenSourceGenerator {

    private final CrudSchemaQueryService crudSchemaQueryService;
    private final CrudModelFactory crudModelFactory;
    private final CrudTemplateRenderer crudTemplateRenderer;

    @Override
    public boolean supports(FeatureType featureType) {
        return featureType == FeatureType.CRUD;
    }

    @Override
    public GeneratedSource generate(GenerateScreenSourceCommand command) {
        String database = command.database();
        String tableName = command.primaryTable();

        List<Map<String, Object>> columns = crudSchemaQueryService.fetchColumns(database, tableName);
        if (columns.isEmpty()) {
            throw new ScreenSourceNotFoundException("테이블을 찾을 수 없습니다: " + database + "." + tableName);
        }

        String resolvedVersion = resolveEgovVersion(command.egovVersion());
        CrudViewType viewType = CrudViewType.from(command.viewType());
        ScreenSubsetMode subsetMode = viewType == CrudViewType.THYMELEAF
                ? ScreenSubsetMode.LIST_AND_DETAIL : ScreenSubsetMode.LIST_ONLY;
        CrudTemplateModel model = crudModelFactory.fromSchema(
                tableName, command.domain(), command.packageName(), resolvedVersion, columns,
                CrudProgramMetadata.fallback(null), viewType, subsetMode, null);
        String layerKey = layerKey(viewType, command.screenType().label());
        String code = crudTemplateRenderer.renderByLayerKey(layerKey, model);
        Path recommendedPath = resolveScreenPath(
                command.outputPath(), command.packageName(), model.domainLc(), command.domain(), viewType, layerKey);
        return new GeneratedSource(
                FeatureType.CRUD, command.domain(), command.screenType(), viewType, layerKey, recommendedPath, code);
    }

    private Path resolveScreenPath(
            Path outputPath, String packageName, String domainLc, String domain,
            CrudViewType viewType, String layerKey) {
        CrudLayerDefinition layer = CrudLayerDefinition.forViewType(viewType).stream()
                .filter(candidate -> candidate.layerKey().equals(layerKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 layerKey: " + layerKey));
        String pkgSub = packageName.replace("egovframework.let.", "").replace(".", "/");
        String fileName = CrudLayerDefinition.resolveFileName(layerKey, domain, layer.fileNameSuffix());
        return Path.of(outputPath + "/" + layer.resolveSubPath(pkgSub, domainLc) + fileName);
    }

    private String layerKey(CrudViewType viewType, String screenLabel) {
        return (viewType == CrudViewType.THYMELEAF ? "thymeleaf" : "jsp") + screenLabel;
    }

    private String resolveEgovVersion(String egovVersion) {
        return (egovVersion == null || egovVersion.isBlank()) ? "5.0" : egovVersion;
    }
}
