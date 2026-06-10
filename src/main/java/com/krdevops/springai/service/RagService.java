package com.krdevops.springai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * RAG(Retrieval-Augmented Generation) 서비스.
 *
 * [사용 시나리오]
 *
 * 1. eGovFrame 문서 검색
 *    ingestText("egov-login", "로그인 처리 표준 가이드 내용...")
 *    → buildRagContext("표준프레임워크 로그인 처리 방법")
 *    → LLM 프롬프트에 컨텍스트로 전달
 *
 * 2. 기존 소스코드 참조 생성
 *    ingestJavaDirectory("/Users/.../my-project/src")
 *    → buildRagContext("우리 프로젝트 코딩 스타일")
 *    → Claude에 기존 코드 스타일 참조로 전달
 *
 * 3. 대화 히스토리 기반 추천
 *    ingestText("history-20260516", "COMTNEMPLYRINFO CRUD 생성 이력...")
 *    → buildRagContext("직원 관련 소스 생성")
 *    → 이전 생성 패턴 재활용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private static final Pattern TAG_PATTERN    = Pattern.compile("<[^>]+>");
    private static final Pattern SPACE_PATTERN  = Pattern.compile("\\s{2,}");
    private static final Pattern SCRIPT_PATTERN = Pattern.compile(
        "(?s)<(script|style|nav|footer|header)[^>]*>.*?</(script|style|nav|footer|header)>",
        Pattern.CASE_INSENSITIVE);

    private static final String CHUNK_IDS_PREFIX = "chat:chunk-ids:";

    private final VectorStore  vectorStore;
    private final ChunkService chunkService;
    private final RedisTemplate<String, Object> redisTemplate;
    // 리다이렉트 비활성화 — SSRF 검증 후 리다이렉트로 내부망 우회 방지
    // 주의: DNS 리바인딩 공격(검증 시 공인 IP → 연결 시 사설 IP) 완전 차단은 불가.
    //       운영 환경에서는 화이트리스트 방식 적용 권장.
    private final HttpClient   httpClient  = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    // -------------------------------------------------------------------------
    // 문서 임베딩 (ingestion)
    // -------------------------------------------------------------------------

    /**
     * 텍스트 단건을 임베딩해 Vector Store에 저장한다.
     *
     * @param docId   문서 식별자 (예: "egov-login-guide", "history-20260516")
     * @param content 임베딩할 텍스트 내용
     * @param type    문서 유형 (document / source_code / history)
     */
    public String ingestText(String docId, String content, String type) {
        // 기존 청크 삭제 — 파일 수정 시 구 청크 누적 방지
        deleteOldChunks(docId);

        List<String> chunks = chunkService.chunk(content);
        List<Document> docs = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunkDocId = chunks.size() == 1 ? docId : docId + "-" + i;
            docs.add(new Document(chunks.get(i), Map.of(
                "docId",    chunkDocId,
                "type",     type,
                "chunkIdx", String.valueOf(i),
                "total",    String.valueOf(chunks.size())
            )));
        }
        vectorStore.add(docs);

        // 새 청크 ID 저장 — 다음 재임베딩 시 삭제에 사용
        String chunkIds = docs.stream()
                .map(Document::getId)
                .collect(Collectors.joining(","));
        redisTemplate.opsForValue().set(CHUNK_IDS_PREFIX + docId, chunkIds);

        log.info("RAG 문서 등록: docId={}, type={}, chunks={}", docId, type, chunks.size());
        return "임베딩 완료: " + docId + " (" + chunks.size() + "개 청크 / " + content.length() + " chars)";
    }

    /**
     * docId에 해당하는 기존 청크를 VectorStore에서 삭제한다.
     * ingestText() 호출 전 실행되어 구 청크 누적을 방지한다.
     */
    private void deleteOldChunks(String docId) {
        Object stored = redisTemplate.opsForValue().get(CHUNK_IDS_PREFIX + docId);
        if (stored == null || stored.toString().isBlank()) return;

        List<String> ids = Arrays.asList(stored.toString().split(","));
        vectorStore.delete(ids);
        redisTemplate.delete(CHUNK_IDS_PREFIX + docId);
        log.info("기존 청크 삭제: docId={}, chunks={}", docId, ids.size());
    }

    /**
     * 디렉터리 내 .java 파일을 전부 임베딩한다. (소스코드 참조 생성용)
     *
     * @param directoryPath 스캔할 디렉터리 경로
     */
    public String ingestJavaDirectory(String directoryPath) {
        Path dir = Paths.get(directoryPath);
        if (!Files.exists(dir)) {
            return "디렉터리가 존재하지 않습니다: " + directoryPath;
        }

        try (Stream<Path> paths = Files.walk(dir)) {
            List<Path> javaFiles = paths
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());

            if (javaFiles.isEmpty()) return "Java 파일이 없습니다: " + directoryPath;

            int fileCount = 0;
            for (Path p : javaFiles) {
                try {
                    String content  = Files.readString(p);
                    String fileName = p.getFileName().toString();
                    ingestText(fileName, content, "source_code");
                    fileCount++;
                } catch (IOException e) {
                    log.warn("파일 읽기 실패: {}", p, e);
                }
            }

            log.info("RAG 소스코드 일괄 등록: dir={}, files={}", directoryPath, fileCount);
            return fileCount + "개 Java 파일 임베딩 완료: " + directoryPath;

        } catch (IOException e) {
            return "디렉터리 스캔 실패: " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // URL 크롤링 → 임베딩
    // -------------------------------------------------------------------------

    /**
     * 단일 URL의 HTML을 가져와 텍스트로 변환 후 청킹하여 Vector Store에 저장한다.
     *
     * @param url   크롤링할 URL
     * @param docId 문서 식별자 (비어있으면 URL에서 자동 추출)
     */
    public String ingestUrl(String url, String docId) {
        try {
            String html = fetchHtml(url);
            String text = extractText(html);

            if (text.isBlank()) return "텍스트 추출 실패 (빈 내용): " + url;

            String id = docId.isBlank() ? urlToDocId(url) : docId;
            // ingestText() 위임 — deleteOldChunks()로 재임베딩 시 구 청크 누적 방지
            return ingestText(id, text, "document");

        } catch (Exception e) {
            log.warn("URL 임베딩 실패: url={}, error={}", url, e.getMessage());
            return "임베딩 실패: " + url + " → " + e.getMessage();
        }
    }

    /**
     * 여러 URL을 순차적으로 크롤링하여 Vector Store에 저장한다.
     *
     * @param urls 쉼표로 구분된 URL 목록
     */
    public String ingestUrls(String urls) {
        String[] urlList = urls.split(",");
        StringBuilder sb = new StringBuilder();
        sb.append("=== URL 일괄 임베딩 결과 ===\n");
        int success = 0;
        for (String url : urlList) {
            String trimmed = url.trim();
            if (trimmed.isEmpty()) continue;
            String result = ingestUrl(trimmed, "");
            sb.append("  ").append(result).append("\n");
            if (result.startsWith("임베딩 완료")) success++;
        }
        sb.append("\n완료: ").append(success).append("/").append(urlList.length).append("개");
        return sb.toString();
    }

    // ─── 내부 유틸 ────────────────────────────────────────────────────────────

    private void validateUrl(String url) throws IOException {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IOException("잘못된 URL 형식: " + url);
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IOException("허용되지 않는 URL 스킴: " + scheme);
        }
        String host = uri.getHost();
        if (host == null) {
            throw new IOException("호스트 없는 URL: " + url);
        }
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress() || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress() || address.isAnyLocalAddress()) {
                log.warn("SSRF 시도 차단: host={}", host);
                throw new IOException("내부 네트워크 접근 차단: " + host);
            }
        } catch (UnknownHostException e) {
            throw new IOException("알 수 없는 호스트: " + host);
        }
    }

    private String fetchHtml(String url) throws IOException, InterruptedException {
        validateUrl(url);
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", "Mozilla/5.0 (compatible; eGovAI-RAG/1.0)")
            .GET()
            .build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 400) {
            throw new IOException("HTTP " + res.statusCode() + ": " + url);
        }
        return res.body();
    }

    private String extractText(String html) {
        // 1. script / style / nav / footer / header 제거
        String text = SCRIPT_PATTERN.matcher(html).replaceAll(" ");
        // 2. HTML 태그 제거
        text = TAG_PATTERN.matcher(text).replaceAll(" ");
        // 3. HTML 엔티티 디코딩 (기본)
        text = text.replace("&nbsp;", " ")
                   .replace("&amp;",  "&")
                   .replace("&lt;",   "<")
                   .replace("&gt;",   ">")
                   .replace("&quot;", "\"");
        // 4. 연속 공백 정리
        text = SPACE_PATTERN.matcher(text).replaceAll(" ").trim();
        return text;
    }

    private String urlToDocId(String url) {
        return url.replaceAll("https?://", "")
                  .replaceAll("[^a-zA-Z0-9가-힣]", "-")
                  .replaceAll("-{2,}", "-")
                  .replaceAll("^-|-$", "");
    }

    // -------------------------------------------------------------------------
    // 유사 문서 검색
    // -------------------------------------------------------------------------

    /**
     * 쿼리와 유사한 문서를 Vector Store에서 검색한다.
     *
     * @param query 검색 쿼리 (자연어)
     * @param topK  반환할 최대 문서 수
     */
    public List<Document> search(String query, int topK) {
        return vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build()
        );
    }

    /**
     * 검색 결과를 LLM 프롬프트용 컨텍스트 문자열로 포맷한다.
     *
     * 반환 형식:
     *   [참고 문서 1] docId: xxx
     *   내용...
     *   ---
     *   [참고 문서 2] ...
     */
    public String buildRagContext(String query, int topK) {
        List<Document> docs = search(query, topK);
        if (docs.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("=== 참고 문서 (").append(docs.size()).append("건) ===\n");

        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            String docId = (String) doc.getMetadata().getOrDefault("docId", "unknown");
            String type  = (String) doc.getMetadata().getOrDefault("type", "");
            sb.append("\n[참고 ").append(i + 1).append("] ")
              .append(docId)
              .append(type.isEmpty() ? "" : " (" + type + ")")
              .append("\n")
              .append(doc.getText())
              .append("\n---");
        }

        return sb.toString();
    }

}
