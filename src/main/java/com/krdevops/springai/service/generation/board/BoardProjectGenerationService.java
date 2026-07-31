package com.krdevops.springai.service.generation.board;

import com.krdevops.springai.service.BoardOrchestrationResult;
import com.krdevops.springai.service.BoardOrchestrationService;
import com.krdevops.springai.service.generation.api.GenerateBoardProjectUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 게시판 소스 생성의 얇은 어댑터 — Command를 기존 {@link BoardOrchestrationService#orchestrate}
 * 호출로 그대로 변환한다. 분기가 없으므로 별도 Dispatch Service가 필요 없다 — Facade가 이 Use Case를
 * 바로 호출한다. {@code BoardOrchestrationService} 내부 로직은 이번 WP에서 수정하지 않는다
 * ({@code ORT-PRN-010}).
 */
@Service
@RequiredArgsConstructor
public class BoardProjectGenerationService implements GenerateBoardProjectUseCase {

    private final BoardOrchestrationService boardOrchestrationService;

    @Override
    public BoardOrchestrationResult execute(BoardGenerationCommand command) {
        return boardOrchestrationService.orchestrate(
                command.database(), command.domain(), command.packageName(), command.outputPath().toString(),
                command.mainTable(), command.masterTable(), command.useTable(),
                command.fileTable(), command.fileDetailTable(),
                command.egovVersion(), command.viewType(),
                command.layout().layoutMode(), command.layout().layoutView(), command.layout().breadcrumbView(),
                command.toGenerationOptions());
    }
}
