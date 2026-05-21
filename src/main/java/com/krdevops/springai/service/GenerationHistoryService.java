package com.krdevops.springai.service;

import com.krdevops.springai.mapper.GenerationHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationHistoryService {

    private final GenerationHistoryRepository repository;
    private final RagService ragService;

    /**
     * 소스 생성 이력을 DB에 저장하고 RAG Vector Store에도 등록한다.
     */
    public String saveHistory(String tableName, String domain, String packageName,
                              String outputPath, String generatedFiles) {
        long id = repository.insert(tableName, domain, packageName, outputPath, generatedFiles);

        // RAG history 타입으로 등록 — 이후 ragSearch로 이전 패턴 재활용 가능
        String ragContent = String.format(
            "테이블: %s | 도메인: %s | 패키지: %s | 출력경로: %s | 생성파일: %s",
            tableName, domain, packageName, outputPath, generatedFiles
        );
        try {
            ragService.ingestText("history-" + id, ragContent, "history");
        } catch (Exception e) {
            log.warn("RAG 이력 등록 실패 (DB 저장은 완료): {}", e.getMessage());
        }

        log.info("생성 이력 저장: id={}, table={}, domain={}", id, tableName, domain);
        return String.format("생성 이력 저장 완료 (ID: %d) — 테이블: %s, 도메인: %s, 파일: %s",
            id, tableName, domain, generatedFiles);
    }

    /**
     * 특정 테이블의 최근 생성 이력을 한 줄 요약으로 반환한다. (ContextAssembler용)
     * 이력이 없으면 빈 문자열을 반환한다.
     */
    public String getRecentSummary(String tableName) {
        List<Map<String, Object>> list = repository.selectByKeyword(tableName);
        if (list.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        // 최근 3건까지만 간략 포맷
        list.stream().limit(3).forEach(row ->
            sb.append("  [").append(row.get("createdAt")).append("] ")
              .append("테이블: ").append(row.get("tableName"))
              .append(" | 도메인: ").append(row.get("domain"))
              .append(" | 패키지: ").append(row.get("packageName"))
              .append(" | 경로: ").append(row.get("outputPath"))
              .append("\n")
        );
        return sb.toString();
    }

    /**
     * 키워드로 생성 이력을 검색한다.
     * keyword가 비어 있으면 최근 20건을 반환한다.
     */
    public String getHistory(String keyword) {
        List<Map<String, Object>> list = repository.selectByKeyword(keyword);
        if (list.isEmpty()) {
            return keyword == null || keyword.isBlank()
                ? "저장된 생성 이력이 없습니다."
                : "'" + keyword + "' 검색 결과가 없습니다.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== 생성 이력 (").append(list.size()).append("건) ===\n");
        for (Map<String, Object> row : list) {
            sb.append("\n[ID: ").append(row.get("id")).append("] ")
              .append(row.get("createdAt")).append("\n")
              .append("  테이블  : ").append(row.get("tableName")).append("\n")
              .append("  도메인  : ").append(row.get("domain")).append("\n")
              .append("  패키지  : ").append(row.get("packageName")).append("\n")
              .append("  출력경로: ").append(row.get("outputPath")).append("\n")
              .append("  생성파일: ").append(row.get("generatedFiles")).append("\n")
              .append("---");
        }
        return sb.toString();
    }
}
