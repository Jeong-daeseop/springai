package com.krdevops.springai.service.generation.board;

import com.krdevops.springai.model.board.BoardGenerationOptions;
import com.krdevops.springai.model.board.BoardProgramMetadata;
import com.krdevops.springai.model.board.BoardTableSet;
import com.krdevops.springai.model.board.BoardTemplateModel;
import com.krdevops.springai.model.crud.CrudLayoutMode;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.service.BoardModelFactory;
import com.krdevops.springai.service.BoardProgramMetadataService;
import com.krdevops.springai.service.BoardRouteCollisionDetector;
import com.krdevops.springai.service.BoardSchemaService;
import com.krdevops.springai.service.BoardTableSetResolver;
import com.krdevops.springai.service.GenerationDesignContextService;
import com.krdevops.springai.service.ThymeleafLayoutValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 게시판 생성의 사전 단계만 담당한다.
 *
 * <p>Board 생성에 필요한 테이블 해석·스키마·메타데이터·화면명세·레이아웃·URL
 * 검증 규칙을 Planner 경계로 고정하기 위한 WP-5 첫 단계다. 파일 저장, 렌더링, 검증 결과
 * 문자열 조립은 수행하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BoardGenerationPlanner {

    private static final String PACKAGE_PREFIX = "egovframework.let.";

    private final BoardTableSetResolver tableSetResolver;
    private final BoardSchemaService schemaService;
    private final BoardProgramMetadataService metadataService;
    private final GenerationDesignContextService designContextService;
    private final BoardModelFactory modelFactory;
    private final BoardRouteCollisionDetector routeCollisionDetector;
    private final ThymeleafLayoutValidator layoutValidator;

    public BoardGenerationPlan plan(BoardGenerationCommand command) {
        if (command.packageName() == null || !command.packageName().startsWith(PACKAGE_PREFIX)) {
            return BoardGenerationPlan.rejected(new BoardPlanFailure(
                    BoardPlanFailure.Kind.INVALID_PACKAGE,
                    "packageName 검증 실패",
                    List.of("packageName은 egovframework.let.* 형식이어야 합니다: " + command.packageName())));
        }

        BoardTableSet tables = tableSetResolver.resolve(command.database(), command.mainTable(), command.masterTable(),
                command.useTable(), command.fileTable(), command.fileDetailTable());
        Map<String, List<Map<String, Object>>> schemas;
        try {
            schemas = schemaService.fetchBoardSchemas(command.database(), tables.mainTable(), tables.masterTable(),
                    tables.useTable(), tables.fileTable(), tables.fileDetailTable());
        } catch (IllegalArgumentException exception) {
            return BoardGenerationPlan.rejected(new BoardPlanFailure(
                    BoardPlanFailure.Kind.TABLE_NOT_FOUND,
                    "게시판 필수 테이블 없음",
                    List.of(exception.getMessage())));
        }

        BoardGenerationOptions options = command.toGenerationOptions();
        BoardProgramMetadata metadata = metadataService.resolve(
                command.database(), command.domain(), tables.masterTable(), options);
        List<String> warnings = new ArrayList<>();
        if (metadata.message() != null) warnings.add(metadata.message());
        if (metadata.blocksGeneration()) {
            return BoardGenerationPlan.rejected(new BoardPlanFailure(
                    BoardPlanFailure.Kind.METADATA_BLOCKED,
                    "메타데이터 검증 실패",
                    List.of(metadata.message())));
        }

        ScreenSpecification screenSpecification = designContextService.resolve(
                command.database(), tables.mainTable(), metadata.programKoreanName(), "board",
                options.designReferenceId(), options.screenSpecificationId());
        CrudViewType viewType = CrudViewType.from(command.viewType());
        CrudLayoutMode layoutMode = viewType == CrudViewType.THYMELEAF
                ? CrudLayoutMode.from(command.layout().layoutMode()) : CrudLayoutMode.CREATE;
        ThymeleafLayoutValidator.LayoutReference layoutReference = viewType == CrudViewType.THYMELEAF
                ? layoutValidator.resolve(command.layout().layoutView(), command.layout().breadcrumbView())
                : layoutValidator.resolve(null, null);

        BoardTemplateModel model = screenSpecification == null
                ? modelFactory.fromSchemas(tables.mainTable(), tables.masterTable(), tables.useTable(),
                        tables.fileDetailTable(), command.domain(), command.packageName(), command.egovVersion(),
                        schemas, metadata)
                : modelFactory.fromSchemas(tables.mainTable(), tables.masterTable(), tables.useTable(),
                        tables.fileDetailTable(), command.domain(), command.packageName(), command.egovVersion(),
                        schemas, metadata, screenSpecification);

        List<String> conflicts = routeCollisionDetector.findConflicts(
                command.outputPath().toString(),
                model.route().hasListAlias() ? model.route().registeredListPath() : null,
                "Egov" + command.domain() + "Controller.java");
        if (!conflicts.isEmpty()) {
            return BoardGenerationPlan.rejected(new BoardPlanFailure(
                    BoardPlanFailure.Kind.ALIAS_CONFLICT,
                    "URL 검증 실패",
                    List.of("Controller URL alias 충돌(ambiguous mapping 위험): " + conflicts)));
        }

        if (viewType == CrudViewType.THYMELEAF && layoutMode == CrudLayoutMode.REUSE) {
            ThymeleafLayoutValidator.LayoutValidationResult validation = layoutValidator.validateExisting(
                    command.outputPath().toString(), layoutReference.layoutView(), layoutReference.breadcrumbView());
            if (!validation.valid()) {
                return BoardGenerationPlan.rejected(new BoardPlanFailure(
                        BoardPlanFailure.Kind.LAYOUT_MISSING,
                        "layout 검증 실패",
                        List.of(layoutValidator.missingLayoutMessage(command.outputPath().toString(), validation))));
            }
        }

        log.info("[board-plan] 준비 완료: table={}, domain={}, viewType={}",
                tables.mainTable(), command.domain(), viewType.value());
        return new BoardGenerationPlan(tables, schemas, metadata, model, screenSpecification, viewType,
                layoutMode, layoutReference, warnings, null);
    }
}
