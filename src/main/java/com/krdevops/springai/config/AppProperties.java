package com.krdevops.springai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private List<String> documentPaths = List.of(System.getProperty("user.home") + "/documents/egovframe-docs-main");
    private String apiKey;
    private List<String> openaiModels = List.of("gpt-4o-mini", "gpt-4o");
    /** R5-004/R4-003: 기본 Figma 다운로드를 SSOT Bundle로 전환할지 여부. */
    private boolean figmaSsotBundleEnabled = false;
    /** document-paths 폴더 반복 자동 스캔(인제스트) 활성화 여부. 기본 비활성 — 명시적으로 켜야 동작한다. */
    private boolean documentAutoScanEnabled = false;
    /** 자동 스캔 주기(ms). 기본 30분. */
    private long documentAutoScanIntervalMs = 1800000L;
    /** 삭제 정리 세이프가드 — 고아 문서 비율이 이 값을 넘으면 정리를 건너뛴다(디렉터리 마운트 실패 등으로 인한 오탐 대량삭제 방지). */
    private double documentCleanupMaxOrphanRatio = 0.5;
    /**
     * 문서 스캔 시 한 배치에 동시 제출할 파일 개수.
     * documentProcessingExecutor의 큐 용량(app.resilience.bulkhead.indexing-concurrency * 25, 기본 50)보다
     * 충분히 작아야 RejectedExecutionException 없이 대용량 폴더(수백 개)도 안전하게 처리된다.
     */
    private int documentScanBatchSize = 20;
}
