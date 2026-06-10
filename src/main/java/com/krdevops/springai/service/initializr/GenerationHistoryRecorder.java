package com.krdevops.springai.service.initializr;

import com.krdevops.springai.model.ProjectSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 프로젝트 초기화 이력 기록.
 * Phase 2: 로그 전용 (SLF4J)
 * Phase 3+: DB 저장 (COMTN_GENERATION_HISTORY 테이블) — 선택
 */
@Component
public class GenerationHistoryRecorder {

    private static final Logger log = LoggerFactory.getLogger(GenerationHistoryRecorder.class);

    public void record(ProjectSpec s, int fileCount, int errorCount) {
        // Phase 2: 로그만
        log.info("[initializeProject] {} | {} | eGov {} | files={} errors={}",
            s.projectName(),
            s.boot() ? "boot" : "war",
            s.egovVersion(),
            fileCount,
            errorCount);

        // Phase 3+: DB 저장 (주석 해제)
        // mapper.insertHistory(GenerationHistoryVO.from(s, fileCount, errorCount));
    }
}
