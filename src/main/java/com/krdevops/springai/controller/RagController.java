package com.krdevops.springai.controller;

import com.krdevops.springai.service.LlmRouterService;
import com.krdevops.springai.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG REST API.
 * 문서 임베딩 등록 / 유사 문서 검색 / RAG 기반 LLM 응답.
 *
 * 인증: X-API-Key 헤더 (SecurityConfig — /api/** 적용)
 */
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;
    private final LlmRouterService llmRouterService;

    /**
     * 텍스트 단건 임베딩 등록.
     *
     * 요청: { "docId": "egov-login", "content": "...", "type": "document" }
     * type: document(문서) / source_code(소스코드) / history(생성이력)
     */
    @PostMapping("/ingest")
    public Map<String, String> ingest(@RequestBody Map<String, String> body) {
        String result = ragService.ingestText(
            body.get("docId"),
            body.get("content"),
            body.getOrDefault("type", "document")
        );
        return Map.of("result", result);
    }

    /**
     * 디렉터리 내 .java 파일 일괄 임베딩.
     *
     * 요청: { "directoryPath": "/Users/.../my-project/src/main/java" }
     */
    @PostMapping("/ingest/directory")
    public Map<String, String> ingestDirectory(@RequestBody Map<String, String> body) {
        String result = ragService.ingestJavaDirectory(body.get("directoryPath"));
        return Map.of("result", result);
    }

    /**
     * 유사 문서 검색.
     *
     * 요청: { "query": "로그인 처리 방법", "topK": 3 }
     */
    @PostMapping("/search")
    public Map<String, Object> search(@RequestBody Map<String, Object> body) {
        String query = (String) body.get("query");
        int topK = body.containsKey("topK") ? ((Number) body.get("topK")).intValue() : 3;

        List<Document> docs = ragService.search(query, topK);
        List<Map<String, Object>> results = docs.stream()
            .map(doc -> Map.<String, Object>of(
                "docId",   doc.getMetadata().getOrDefault("docId", "unknown"),
                "type",    doc.getMetadata().getOrDefault("type", ""),
                "content", doc.getText().substring(0, Math.min(300, doc.getText().length())) + "..."
            ))
            .collect(Collectors.toList());

        return Map.of("query", query, "count", results.size(), "results", results);
    }

    /**
     * RAG 기반 LLM 응답.
     * Vector Store에서 유사 문서를 검색해 컨텍스트로 붙인 후 LLM에 전달한다.
     *
     * 요청: { "query": "표준프레임워크 로그인 처리 방법", "taskType": "CLASSIFICATION", "topK": 3 }
     * taskType: CLASSIFICATION / SIMPLE_QUERY / SENSITIVE_DATA
     *           (CODE_GENERATION은 Claude Desktop MCP SSE 사용)
     */
    @PostMapping("/chat")
    public Map<String, String> ragChat(@RequestBody Map<String, Object> body) {
        String query    = (String) body.get("query");
        String taskType = (String) body.getOrDefault("taskType", "SIMPLE_QUERY");
        int topK        = body.containsKey("topK") ? ((Number) body.get("topK")).intValue() : 3;

        String context = ragService.buildRagContext(query, topK);
        String prompt  = context.isEmpty()
            ? query
            : context + "\n\n위 참고 문서를 바탕으로 다음 질문에 답하세요:\n" + query;

        String answer = llmRouterService.chat(taskType, prompt);
        return Map.of(
            "query",     query,
            "taskType",  taskType,
            "hasContext", String.valueOf(!context.isEmpty()),
            "answer",    answer
        );
    }
}
