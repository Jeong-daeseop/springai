package com.krdevops.springai.service.generation.board;

import com.krdevops.springai.model.board.BoardGenerationOptions;
import com.krdevops.springai.model.board.BoardProgramMetadata;
import com.krdevops.springai.model.board.BoardTableSet;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.service.BoardProgramMetadataService;
import com.krdevops.springai.service.BoardSchemaService;
import com.krdevops.springai.service.BoardTableSetResolver;
import com.krdevops.springai.service.GenerationDesignContextService;
import com.krdevops.springai.service.ScreenSpecificationPromptFormatter;
import com.krdevops.springai.service.generation.api.BuildBoardPromptUseCase;
import com.krdevops.springai.service.generation.model.PromptGenerationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * llmProvider=claude(그 외) 경로 — 게시판(BBS) 테이블 세트·스키마·프로그램 메타데이터·화면명세를
 * 해석한 뒤 Claude가 직접 소스를 작성할 수 있도록 Prompt 문자열을 빌드한다. auto 경로
 * ({@link BoardGenerationPlanner})와 동일하게 이 정보들을 먼저 해석해 명시 파라미터가 조용히
 * 무시되지 않도록 한다(CRUD의 {@code CrudPromptGenerationService}와 동일 원칙).
 */
@Service
@RequiredArgsConstructor
public class BoardPromptGenerationService implements BuildBoardPromptUseCase {

    private final BoardTableSetResolver tableSetResolver;
    private final BoardSchemaService schemaService;
    private final BoardProgramMetadataService programMetadataService;
    private final GenerationDesignContextService designContextService;
    private final ScreenSpecificationPromptFormatter screenSpecificationPromptFormatter;

    @Override
    public PromptGenerationResult execute(BoardGenerationCommand command) {
        BoardTableSet tables = tableSetResolver.resolve(command.database(), command.mainTable(),
                command.masterTable(), command.useTable(), command.fileTable(), command.fileDetailTable());
        Map<String, List<Map<String, Object>>> schemas = schemaService.fetchBoardSchemas(
                command.database(), tables.mainTable(), tables.masterTable(),
                tables.useTable(), tables.fileTable(), tables.fileDetailTable());

        BoardGenerationOptions options = command.toGenerationOptions();
        BoardProgramMetadata metadata = programMetadataService.resolve(
                command.database(), command.domain(), tables.masterTable(), options);
        if (metadata.blocksGeneration()) {
            throw new IllegalArgumentException(metadata.message());
        }

        ScreenSpecification screenSpecification = designContextService.resolve(
                command.database(), tables.mainTable(), metadata.programKoreanName(), "board",
                options.designReferenceId(), options.screenSpecificationId());

        StringBuilder sb = new StringBuilder();
        sb.append("=== eGovFrame 5.x 게시판(BBS) 소스 생성 지시 ===\n\n");
        appendScreenSpecification(sb, screenSpecification);
        appendTableComposition(sb, tables);
        appendBusinessRules(sb);
        appendProgramMetadata(sb, metadata);
        appendBasicInfo(sb, command);
        appendSchemas(sb, schemas, tables);
        appendGenerationTarget(sb, command);

        return new PromptGenerationResult(sb.toString());
    }

    private void appendScreenSpecification(StringBuilder sb, ScreenSpecification screenSpecification) {
        if (screenSpecification == null) return;
        sb.append(screenSpecificationPromptFormatter.format(screenSpecification)).append('\n');
        if (!screenSpecification.componentGeometry().isEmpty()) {
            sb.append("[디자인 기하 정보 사용 규칙]\n")
                    .append("- componentGeometry는 Figma 원본 좌표·간격·색상·폰트 참고값입니다.\n")
                    .append("- 화면 구조와 마크업은 반드시 기존 krds-*/egov-* 클래스 체계를 그대로 유지하세요.\n")
                    .append("- 임의의 커스텀 클래스나 인라인 style로 KRDS 구조를 대체하지 마세요.\n")
                    .append("- 좌표를 인라인 style이나 고정 px width/height로 옮기면 반응형이 깨집니다. ")
                    .append("표·검색패널 등 기존 KRDS 클래스에는 이미 반응형 @media 규칙이 내장되어 ")
                    .append("있으니(예: krds-table-wrap의 767px 이하 모바일 대응) 클래스만 정확히 ")
                    .append("유지하세요.\n\n");
        }
    }

    private void appendTableComposition(StringBuilder sb, BoardTableSet tables) {
        sb.append("[테이블 구성]\n");
        sb.append("  게시글(main)         : ").append(tables.mainTable()).append('\n');
        sb.append("  게시판마스터(master) : ").append(tables.masterTable()).append('\n');
        if (tables.useTable() != null) {
            sb.append("  사용권한(use)        : ").append(tables.useTable()).append('\n');
        }
        if (tables.fileTable() != null) {
            sb.append("  첨부파일(file)       : ").append(tables.fileTable()).append('\n');
        }
        if (tables.fileDetailTable() != null) {
            sb.append("  첨부상세(fileDetail) : ").append(tables.fileDetailTable()).append('\n');
        }
        sb.append('\n');
    }

    private void appendBusinessRules(StringBuilder sb) {
        sb.append("[게시판 업무 규칙 — 반드시 반영]\n");
        sb.append("- 복합 PK: 게시글(main) 테이블은 BBS_ID + NTT_ID 두 컬럼을 함께 기본키로 사용합니다. ")
                .append("Mapper의 조회·수정·삭제 조건절에 항상 두 컬럼을 함께 사용하세요.\n");
        sb.append("- 논리삭제: 아래 main 스키마에서 사용여부·삭제여부 성격의 컬럼(COLUMN_COMMENT 참고)이 있으면 ")
                .append("물리 DELETE 대신 그 컬럼을 갱신하는 방식으로 삭제를 구현하세요.\n");
        sb.append("- 조회수 증가: 아래 main 스키마에서 조회수 성격의 컬럼이 있으면, 상세 화면 조회 직전에 ")
                .append("해당 컬럼을 +1 갱신하는 UPDATE를 실행하세요.\n");
        sb.append("- 마스터명 조회: 목록·상세 화면에 게시판마스터(master) 테이블을 조인해 게시판 이름을 표시하세요.\n\n");
    }

    private void appendProgramMetadata(StringBuilder sb, BoardProgramMetadata metadata) {
        sb.append("[프로그램 메타데이터]\n");
        sb.append("  프로그램 파일명 : ").append(valueOrDash(metadata.programFileName())).append('\n');
        sb.append("  등록 URL        : ").append(valueOrDash(metadata.registeredUrl())).append('\n');
        sb.append("  한글명          : ").append(valueOrDash(metadata.programKoreanName())).append('\n');
        sb.append("  기본 bbsId      : ").append(valueOrDash(metadata.defaultBbsId())).append('\n');
        if (metadata.message() != null) {
            sb.append("  참고: ").append(metadata.message()).append('\n');
        }
        sb.append('\n');
    }

    private void appendBasicInfo(StringBuilder sb, BoardGenerationCommand command) {
        sb.append("[기본 정보]\n");
        sb.append("  DB          : ").append(command.database()).append('\n');
        sb.append("  도메인      : ").append(command.domain()).append('\n');
        sb.append("  패키지      : ").append(command.packageName()).append('\n');
        sb.append("  출력 경로   : ").append(command.outputPath()).append('\n');
        sb.append("  egovVersion : ").append(command.egovVersion()).append('\n');
        sb.append("  viewType    : ").append(command.viewType()).append('\n');
        if ("thymeleaf".equalsIgnoreCase(command.viewType())) {
            String layoutMode = command.layout().layoutMode() == null ? "reuse" : command.layout().layoutMode();
            sb.append("  layoutMode  : ").append(layoutMode).append('\n');
        }
        sb.append('\n');
    }

    private void appendSchemas(
            StringBuilder sb, Map<String, List<Map<String, Object>>> schemas, BoardTableSet tables) {
        schemas.forEach((role, columns) -> {
            sb.append("[스키마: ").append(role).append(" (").append(tableNameFor(role, tables)).append(")]\n");
            for (Map<String, Object> column : columns) {
                sb.append("  - ").append(column.get("COLUMN_NAME"))
                        .append(" ").append(column.get("DATA_TYPE"))
                        .append(" NULL=").append(column.get("IS_NULLABLE"))
                        .append(" KEY=").append(column.get("COLUMN_KEY"))
                        .append(" 설명=").append(column.get("COLUMN_COMMENT")).append('\n');
            }
            sb.append('\n');
        });
    }

    private void appendGenerationTarget(StringBuilder sb, BoardGenerationCommand command) {
        sb.append("[생성 대상]\n");
        sb.append("목록/상세/등록/수정 화면(Controller/Service/Mapper/VO 포함)을 ")
                .append(command.viewType())
                .append(" 뷰로 작성하고, 위 출력 경로에 저장하세요. Thymeleaf 화면은 layout/GNB를 재사용하고 ")
                .append("인라인 style 없이 krds-*/egov-* 클래스를 사용하세요.\n");
    }

    private String tableNameFor(String role, BoardTableSet tables) {
        return switch (role) {
            case "main" -> tables.mainTable();
            case "master" -> tables.masterTable();
            case "use" -> tables.useTable();
            case "file" -> tables.fileTable();
            case "fileDetail" -> tables.fileDetailTable();
            default -> role;
        };
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
