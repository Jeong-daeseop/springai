# eGovFrame 공식 RAG vs 현재 프로젝트 RAG 비교 분석

작성일: 2026-05-24  
참조: https://github.com/eGovFramework/egovframe-ai-rag (README-spring-ai-rag-redis-stack.md)

---

## 1. 기술 스택 비교

| 항목 | eGovFrame 공식 RAG | 현재 프로젝트 |
|---|---|---|
| Spring AI 버전 | 1.0.1 (GA) | 2.0.0-M6 (Milestone) |
| Spring Boot | 3.4.x | 4.0.6 |
| Vector Store | Redis Stack (RedisVectorStore) | Redis Stack (RedisVectorStore) |
| Embedding 모델 | ONNX (ko-sroberta-multitask) | ONNX (ko-sroberta-multitask) |
| LLM | Claude (Anthropic) | Claude (Anthropic) |
| 채팅 메모리 | InMemoryChatMemory | InMemoryChatMemory |

---

## 2. RAG 연동 방식 핵심 차이

### eGovFrame 공식: `RetrievalAugmentationAdvisor` 패턴

```java
// ChatClient 요청 시 Advisor 체인으로 RAG 자동 주입
chatClient.prompt()
    .user(message)
    .advisors(
        new MessageChatMemoryAdvisor(chatMemory),
        RetrievalAugmentationAdvisor.builder()
            .documentRetriever(VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(0.3)
                .topK(5)
                .build())
            .build()
    )
    .call()
    .content();
```

- RAG 검색 결과가 **Advisor 체인 내부에서 자동으로 시스템 프롬프트에 삽입**
- 유사도 임계값(0.3), topK(5) 설정으로 검색 품질 제어
- Spring AI가 제공하는 표준 패턴 — 유지보수성, 테스트 용이성 높음

---

### 현재 프로젝트: 수동 시스템 프롬프트 주입 패턴

```java
// EgovSessionAwareChatServiceImpl
String context = contextAssembler.build(message, sessionId);
// ContextAssembler가 RAG 결과를 직접 수집 후 system prompt 문자열에 포함
chatClient.prompt()
    .system(context)   // ← 수동 조립된 컨텍스트를 system에 직접 주입
    .user(message)
    .advisors(new MessageChatMemoryAdvisor(chatMemory))
    .call()
    .content();
```

- RAG 검색을 `ContextAssembler`가 직접 수행 후 **문자열로 조립**하여 system prompt에 삽입
- `RetrievalAugmentationAdvisor` 미사용

---

## 3. 컨텍스트 구성 비교

| 항목 | eGovFrame 공식 | 현재 프로젝트 |
|---|---|---|
| RAG 문서 검색 | ✅ VectorStore 유사도 검색 | ✅ VectorStore 유사도 검색 |
| DB 스키마 정보 | ❌ 없음 | ✅ 테이블 컬럼/타입 정보 포함 |
| 생성 이력 | ❌ 없음 | ✅ 과거 생성 소스 이력 포함 |
| 테이블 연관관계 | ❌ 없음 | ✅ FK/JOIN 관계 포함 |
| 컨텍스트 조립 | Advisor 자동 처리 | ContextAssembler 수동 조립 |
| 최대 컨텍스트 크기 | 설정 없음 (topK 제한) | MAX_CONTEXT_CHARS=4,000 |

### ContextAssembler 우선순위 (현재 프로젝트)

```
우선순위 1: DB 스키마 (테이블 컬럼/타입)
우선순위 2: 생성 이력 (과거 소스)
우선순위 3: 테이블 연관관계 (FK/JOIN)
우선순위 4: RAG 문서 (VectorStore 검색 결과)
```

eGovFrame 테이블 패턴 감지: `COMTN / COMTC / COMTH / LETGW`

---

## 4. ETL 파이프라인 비교

### eGovFrame 공식: 명시적 Reader/Transformer/Writer 분리

```java
// 문서 로드
TokenTextSplitter splitter = new TokenTextSplitter();
List<Document> chunks = splitter.split(documents);  // 청킹

// EgovEnhancedDocumentTransformer: 메타데이터 보강
// VectorStore에 배치 임베딩 후 저장
vectorStore.add(enrichedChunks);
```

- **청킹(Chunking)** 명시적 구현 (`TokenTextSplitter`)
- **메타데이터 보강** (`EgovEnhancedDocumentTransformer` 커스텀 구현)
- 배치 처리로 대용량 문서 처리 가능

---

### 현재 프로젝트: 통합 처리 (청킹 없음)

```java
// VectorStoreConfig.java
RedisVectorStore vectorStore = new RedisVectorStore(jedisPooled, embeddingModel, ...);
// 청킹 로직 없음 — 문서 전체를 그대로 임베딩
```

- 청킹 미구현 → 긴 문서는 임베딩 품질 저하 가능
- 메타데이터 보강 없음

---

## 5. 질의 압축(Query Compression) 비교

| 항목 | eGovFrame 공식 | 현재 프로젝트 |
|---|---|---|
| 질의 압축 | ✅ `RewriteQueryTransformer` (대화 맥락 반영) | ❌ 없음 |

eGovFrame 공식은 다중 턴 대화에서 이전 맥락을 반영한 질의 재작성으로 검색 정확도 향상.  
현재 프로젝트는 원본 질의를 그대로 VectorStore에 전달.

---

## 6. EgovRagConfig 비교

### eGovFrame 공식 (풀 구성)

```java
@Bean
public RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(VectorStore vectorStore) {
    return RetrievalAugmentationAdvisor.builder()
        .documentRetriever(VectorStoreDocumentRetriever.builder()
            .vectorStore(vectorStore)
            .similarityThreshold(0.3)
            .topK(5)
            .build())
        .queryTransformers(new RewriteQueryTransformer(chatClient))  // 질의 압축
        .build();
}
```

### 현재 프로젝트 (ChatClient만 선언)

```java
// EgovRagConfig.java
@Bean
public ChatClient chatClient(ChatClient.Builder builder) {
    return builder.build();
}
// VectorStoreDocumentRetriever, RetrievalAugmentationAdvisor 없음
// RAG 검색은 ContextAssembler가 직접 수행
```

---

## 7. 채팅 메모리 비교

두 프로젝트 모두 **동일한 패턴** 사용:

```java
// 공통 패턴
InMemoryChatMemory chatMemory = new InMemoryChatMemory();
advisors(new MessageChatMemoryAdvisor(chatMemory, sessionId, 10))
```

- 세션 ID 기반 분리
- 최근 10턴 메모리 유지
- In-memory (서버 재시작 시 초기화)

---

## 8. API 구조 비교

두 프로젝트 모두 **동일한 REST 엔드포인트 구조**:

```
POST /api/chat/message    → 일반 질의
POST /api/chat/stream     → 스트리밍 응답
GET  /api/chat/history    → 대화 이력
DELETE /api/chat/session  → 세션 초기화
```

---

## 9. 종합 차이점 요약

| 항목 | eGovFrame 공식 | 현재 프로젝트 | 평가 |
|---|---|---|---|
| RAG 연동 방식 | Advisor 패턴 (표준) | 수동 system prompt 주입 | eGovFrame ✅ |
| 컨텍스트 풍부도 | RAG만 | 스키마+이력+관계+RAG | 현재 프로젝트 ✅ |
| 청킹 | ✅ TokenTextSplitter | ❌ 없음 | eGovFrame ✅ |
| 질의 압축 | ✅ RewriteQueryTransformer | ❌ 없음 | eGovFrame ✅ |
| 메타데이터 보강 | ✅ EgovEnhancedDocumentTransformer | ❌ 없음 | eGovFrame ✅ |
| 유지보수성 | Spring AI 표준 패턴 | 커스텀 조립 | eGovFrame ✅ |
| eGovFrame CRUD 특화 | ❌ 범용 | ✅ DB 스키마/이력 통합 | 현재 프로젝트 ✅ |
| Spring AI 버전 | 1.0.1 GA (안정) | 2.0.0-M6 Milestone | eGovFrame ✅ |

---

## 10. 현재 프로젝트 적용 권장 사항

### P1 (즉시) — RetrievalAugmentationAdvisor 도입

현재의 수동 RAG 검색(`ContextAssembler.ragSearch()`)을 `RetrievalAugmentationAdvisor`로 교체.  
ContextAssembler에서 DB 스키마/이력/관계 부분은 유지하고, RAG 부분만 Advisor로 이전.

```java
// EgovRagConfig.java 개선안
@Bean
public RetrievalAugmentationAdvisor ragAdvisor(VectorStore vectorStore) {
    return RetrievalAugmentationAdvisor.builder()
        .documentRetriever(VectorStoreDocumentRetriever.builder()
            .vectorStore(vectorStore)
            .similarityThreshold(0.3)
            .topK(5)
            .build())
        .build();
}
```

---

### P2 (단기) — 청킹 구현

현재 문서 전체를 단일 임베딩으로 처리 → 긴 문서 검색 품질 저하 위험.

```java
TokenTextSplitter splitter = new TokenTextSplitter(500, 50); // 500토큰, 50 overlap
List<Document> chunks = splitter.split(rawDocuments);
vectorStore.add(chunks);
```

---

### P3 (단기) — RewriteQueryTransformer 도입

다중 턴 대화에서 "그거 수정해줘" 같은 대명사 지시를 이전 컨텍스트로 재작성.  
현재는 원본 질의 그대로 VectorStore 검색 → 맥락 누락 가능.

---

### P4 (중기) — Spring AI 버전 GA 안정화 시 업그레이드 검토

현재 2.0.0-M6는 Milestone 버전. API 변경 가능성 있음.  
1.0.1 GA 기준 eGovFrame 공식 패턴이 안정적.

---

### P5 (유지) — ContextAssembler DB 특화 기능은 현재 프로젝트만의 강점

DB 스키마 + 생성 이력 + 테이블 연관관계 통합은 eGovFrame 공식 RAG에 없는 현재 프로젝트만의 차별점.  
eGovFrame CRUD 소스 자동 생성 품질을 높이는 핵심 — 유지 권장.
