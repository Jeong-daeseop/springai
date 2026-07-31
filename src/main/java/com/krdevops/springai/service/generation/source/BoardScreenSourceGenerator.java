package com.krdevops.springai.service.generation.source;

import com.krdevops.springai.model.board.BoardGenerationOptions;
import com.krdevops.springai.model.board.BoardLayerDefinition;
import com.krdevops.springai.model.board.BoardProgramMetadata;
import com.krdevops.springai.model.board.BoardTableSet;
import com.krdevops.springai.model.board.BoardTemplateModel;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.service.BoardModelFactory;
import com.krdevops.springai.service.BoardProgramMetadataService;
import com.krdevops.springai.service.BoardSchemaService;
import com.krdevops.springai.service.BoardTableSetResolver;
import com.krdevops.springai.service.BoardTemplateRenderer;
import com.krdevops.springai.service.generation.model.BoardTableOptions;
import com.krdevops.springai.service.generation.model.FeatureType;
import com.krdevops.springai.service.generation.model.GenerateScreenSourceCommand;
import com.krdevops.springai.service.generation.model.GeneratedSource;
import com.krdevops.springai.service.generation.model.ProgramMetadataOverrides;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** 게시판(BBS) 화면(List/Detail/Regist/Updt) 1개를 렌더링한다. 파일은 저장하지 않는다. */
@Component
@RequiredArgsConstructor
public class BoardScreenSourceGenerator implements ScreenSourceGenerator {

    private final BoardTableSetResolver boardTableSetResolver;
    private final BoardSchemaService boardSchemaService;
    private final BoardProgramMetadataService boardProgramMetadataService;
    private final BoardModelFactory boardModelFactory;
    private final BoardTemplateRenderer boardTemplateRenderer;

    @Override
    public boolean supports(FeatureType featureType) {
        return featureType == FeatureType.BOARD;
    }

    @Override
    public GeneratedSource generate(GenerateScreenSourceCommand command) {
        BoardTableOptions requested = command.boardTables() == null ? BoardTableOptions.empty() : command.boardTables();
        BoardTableSet tables = boardTableSetResolver.resolve(
                command.database(), requested.mainTable(), requested.masterTable(),
                requested.useTable(), requested.fileTable(), requested.fileDetailTable());
        String resolvedVersion = resolveEgovVersion(command.egovVersion());
        CrudViewType viewType = CrudViewType.from(command.viewType());

        Map<String, List<Map<String, Object>>> schemas;
        try {
            schemas = boardSchemaService.fetchBoardSchemas(
                    command.database(), tables.mainTable(), tables.masterTable(), tables.useTable(),
                    tables.fileTable(), tables.fileDetailTable());
        } catch (IllegalArgumentException e) {
            throw new ScreenSourceNotFoundException(e.getMessage());
        }

        ProgramMetadataOverrides program = command.program() == null ? ProgramMetadataOverrides.empty() : command.program();
        BoardProgramMetadata metadata = boardProgramMetadataService.resolve(
                command.database(), command.domain(), tables.masterTable(), new BoardGenerationOptions(
                        program.programFileName(), program.programUrl(), program.programKoreanName(),
                        program.programStorePath(), command.defaultBbsId()));
        if (metadata.blocksGeneration()) {
            throw new ScreenSourceNotFoundException(metadata.message());
        }
        BoardTemplateModel model = boardModelFactory.fromSchemas(
                tables.mainTable(), tables.masterTable(), tables.useTable(), tables.fileDetailTable(),
                command.domain(), command.packageName(), resolvedVersion, schemas, metadata);
        String layerKey = layerKey(viewType, command.screenType().label());
        String code = boardTemplateRenderer.renderByLayerKey(layerKey, model);
        Path recommendedPath = resolveScreenPath(
                command.outputPath(), command.packageName(), model.domainLc(), command.domain(), viewType, layerKey);
        return new GeneratedSource(
                FeatureType.BOARD, command.domain(), command.screenType(), viewType, layerKey, recommendedPath, code);
    }

    private Path resolveScreenPath(
            Path outputPath, String packageName, String domainLc, String domain,
            CrudViewType viewType, String layerKey) {
        BoardLayerDefinition layer = BoardLayerDefinition.forViewType(viewType).stream()
                .filter(candidate -> candidate.layerKey().equals(layerKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 layerKey: " + layerKey));
        String pkgSub = packageName.replace("egovframework.let.", "").replace(".", "/");
        String fileName = BoardLayerDefinition.resolveFileName(layer.layerKey(), domain, layer.fileNameSuffix());
        return Path.of(outputPath + "/" + layer.resolveSubPath(pkgSub, domainLc) + fileName);
    }

    private String layerKey(CrudViewType viewType, String screenLabel) {
        return (viewType == CrudViewType.THYMELEAF ? "thymeleaf" : "jsp") + screenLabel;
    }

    private String resolveEgovVersion(String egovVersion) {
        return (egovVersion == null || egovVersion.isBlank()) ? "5.0" : egovVersion;
    }
}
