package com.krdevops.springai.service.generation.board;

import com.krdevops.springai.model.board.BoardProgramMetadata;
import com.krdevops.springai.model.board.BoardTableSet;
import com.krdevops.springai.service.BoardModelFactory;
import com.krdevops.springai.service.BoardProgramMetadataService;
import com.krdevops.springai.service.BoardRouteCollisionDetector;
import com.krdevops.springai.service.BoardSchemaService;
import com.krdevops.springai.service.BoardTableSetResolver;
import com.krdevops.springai.service.GenerationDesignContextService;
import com.krdevops.springai.service.ThymeleafLayoutValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BoardGenerationPlannerTest {

    @Mock BoardTableSetResolver tableSetResolver;
    @Mock BoardSchemaService schemaService;
    @Mock BoardProgramMetadataService metadataService;
    @Mock GenerationDesignContextService designContextService;
    @Mock BoardModelFactory modelFactory;
    @Mock BoardRouteCollisionDetector routeCollisionDetector;
    @Mock ThymeleafLayoutValidator layoutValidator;

    private BoardGenerationPlanner planner;

    @BeforeEach
    void setUp() {
        planner = new BoardGenerationPlanner(tableSetResolver, schemaService, metadataService,
                designContextService, modelFactory, routeCollisionDetector, layoutValidator);
    }

    private BoardGenerationCommand command(String packageName) {
        return new BoardGenerationCommand("com", "Bbs", packageName, Path.of("/tmp/out"),
                "LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE", "LETTNFILE", "LETTNFILEDETAIL",
                "5.0", "jsp", null, null, null, null);
    }

    @Test
    void invalidPackage_stopsBeforeDatabaseAccess() {
        BoardGenerationPlan plan = planner.plan(command("com.example.bbs"));

        assertThat(plan.failed()).isTrue();
        assertThat(plan.failure().kind()).isEqualTo(BoardPlanFailure.Kind.INVALID_PACKAGE);
        verifyNoInteractions(tableSetResolver, schemaService, metadataService, modelFactory);
    }

    @Test
    void missingRequiredTable_returnsPlanFailure() {
        when(tableSetResolver.resolve(anyString(), any(), any(), any(), any(), any()))
                .thenReturn(new BoardTableSet("LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE",
                        "LETTNFILE", "LETTNFILEDETAIL"));
        when(schemaService.fetchBoardSchemas(anyString(), anyString(), anyString(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("게시판 테이블 없음: LETTNBBS"));

        BoardGenerationPlan plan = planner.plan(command("egovframework.let.bbs"));

        assertThat(plan.failed()).isTrue();
        assertThat(plan.failure().kind()).isEqualTo(BoardPlanFailure.Kind.TABLE_NOT_FOUND);
        verifyNoInteractions(metadataService, designContextService, modelFactory);
    }

    @Test
    void ambiguousMetadata_stopsBeforeModelCreation() {
        when(tableSetResolver.resolve(anyString(), any(), any(), any(), any(), any()))
                .thenReturn(new BoardTableSet("LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE",
                        "LETTNFILE", "LETTNFILEDETAIL"));
        when(schemaService.fetchBoardSchemas(anyString(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(Map.of("main", List.of(), "master", List.of()));
        when(metadataService.resolve(anyString(), anyString(), anyString(), any()))
                .thenReturn(new BoardProgramMetadata(null, null, null, null, null, null, null,
                        BoardProgramMetadata.Source.DATABASE, BoardProgramMetadata.Status.AMBIGUOUS,
                        "프로그램 메타데이터가 모호합니다."));

        BoardGenerationPlan plan = planner.plan(command("egovframework.let.bbs"));

        assertThat(plan.failed()).isTrue();
        assertThat(plan.failure().kind()).isEqualTo(BoardPlanFailure.Kind.METADATA_BLOCKED);
        verifyNoInteractions(designContextService, modelFactory, routeCollisionDetector);
    }
}
