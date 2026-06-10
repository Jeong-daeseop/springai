# eGovFrame 공식 RAG vs 현재 프로젝트 비교 분석 2차 영향평가

작성일: 2026-05-25
참조: https://github.com/eGovFramework/egovframe-ai-rag (README-spring-ai-rag-redis-stack.md)
전제: P1(QuestionAnswerAdvisor 도입) 완료 상태 기준

---

## 현황 재검토 (eGovFrame 공식 코드 실측 기반)

eGovFrame 공식 README의 실제 코드와 현재 프로젝트를 비교한 결과,
1차 영향평가에서 파악하지 못한 구조적 차이 2개가 추가로 식별됨.

| 항목 | 1차 영향평가 | 2차 영향평가(추가 식별) |
|---|---|---|
| RAG Advisor 생성 방식 | 미검토 | ⚠️ 싱글톤 vs 요청별 팩토리 차이 확인 |
| 압축 쿼리 → 벡터 검색 연동 | 미검토 | ⚠️ QuestionAnswerAdvisor가 원본 쿼리로 검색하는 문제 확인 |
| 채팅 메모리 영속성 | 미검토 | ⚠️ InMemory(휘발) vs Redis(영속) 차이 확인 |
| PDF 인덱싱 | 미검토 | △ eGovFrame은 지원, 현재 프로젝트 미지원 |
| 문서 변경 감지(해시) | 미검토 | △ eGovFrame은 지원, 현재 프로젝트 미지원 |

---

## 1. Spring AI 버전 API 차이

eGovFrame 공식(1.0.1 GA)과 현재 프로젝트(2.0.0-M6)는 같은 기능을 다른 클래스명으로 제공한다.

| 기능 | eGovFrame 1.0.1 | 현재 프로젝트 2.0.0-M6 |
|---|---|---|
| RAG Advisor | `RetrievalAugmentationAdvisor` | `QuestionAnswerAdvisor` |
| 벡터 검색 래퍼 | `VectorStoreDocumentRetriever` (별도 Bean) | `QuestionAnswerAdvisor.builder(vectorStore)` 직접 |
| 채팅 메모리 저장소 | `EgovRedisChatMemoryRepository` (Redis 영속) | `InMemoryChatMemory` (휘발성) |

→ API 이름이 다를 뿐 기능은 동일. 버전 업/다운그레이드 시 클래스명 교체 필요.

---

## 2. RAG Advisor 생성 방식 차이 (구조적 결함)

### eGovFrame 공식: 요청마다 Advisor 생성 (팩토리 패턴)

```java
// EgovRagConfig.java — static 팩토리 메서드
public static Advisor createRagAdvisor(
        String sessionId,
        EgovCompressionQueryTransformer compressionTransformer,
        VectorStoreDocumentRetriever documentRetriever,
        boolean enableQueryCompression) {

    if (enableQueryCompression) {
        SessionAwareQueryTransformer sessionAwareTransformer =
            new SessionAwareQueryTransformer(compressionTransformer, sessionId);  // ← sessionId 주입

        return RetrievalAugmentationAdvisor.builder()
                .queryTransformers(sessionAwareTransformer)  // ← 압축기 Advisor 내부에 내장
                .documentRetriever(loggingRetriever)
                .build();
    }
    return RetrievalAugmentationAdvisor.builder()
            .documentRetriever(loggingRetriever).build();
}

// EgovSessionAwareChatServiceImpl.java — 매 요청마다 생성
Advisor ragAdvisor = EgovRagConfig.createRagAdvisor(
    sessionId, compressionTransformer, vectorStoreDocumentRetriever, enableQueryCompression);

return requestSpec
    .advisors(messageChatMemoryAdvisor, ragAdvisor)  // ← 매 요청 새 인스턴스
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
    .stream().chatResponse();
```

### 현재 프로젝트: 싱글톤 Bean

```java
// EgovRagConfig.java — @Bean (싱글톤, sessionId 모름)
@Bean
public QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore) {
    return QuestionAnswerAdvisor.builder(vectorStore)
            .searchRequest(SearchRequest.builder()
                    .topK(topK).similarityThreshold(similarityThreshold).build())
            .build();
}

// EgovSessionAwareChatServiceImpl.java
String searchQuery = compressionTransformer.compress(query, sessionId);  // ← 압축 Advisor 외부
String context = contextAssembler.build(searchQuery);                    // ← 스키마/이력/관계

return requestSpec
    .advisors(questionAnswerAdvisor, messageChatMemoryAdvisor)  // ← 싱글톤 재사용
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
    .stream().chatResponse();
```

---

## 3. 압축 쿼리 → 벡터 검색 연동 문제 (⚠️ P2 — 높음)

### 문제 정의

`QuestionAnswerAdvisor`는 `before()` 시점에 **user 메시지 텍스트를 그대로 검색 쿼리로 사용**한다.
현재 구조에서 `.user(query)`에 원본 쿼리가 들어가므로 압축이 벡터 검색에 반영되지 않는다.

```
[현재 흐름]
원본 query: "그거 수정해줘"
    │
    ├─→ compressionTransformer.compress()  → "COMTNEMPLYRINFO 직원 수정 CRUD"  (압축 성공)
    │       └─→ contextAssembler.build()   → 스키마/이력/관계 (압축 쿼리 사용)  ✅
    │
    └─→ QuestionAnswerAdvisor.before()     → "그거 수정해줘" 로 벡터 검색       ❌
```

```
[목표 흐름 — eGovFrame 방식]
원본 query: "그거 수정해줘"
    │
    └─→ RetrievalAugmentationAdvisor.before()
            └─→ SessionAwareQueryTransformer.transform()  → "COMTNEMPLYRINFO 직원 수정 CRUD"
                    └─→ VectorStoreDocumentRetriever.retrieve()  → 압축 쿼리로 검색  ✅
```

### 영향

- 다중 턴 대화에서 대명사("그거", "이거", "저 테이블")가 포함된 질문 시 벡터 검색 품질 저하
- 스키마/이력 검색은 압축 쿼리 사용으로 정상이나, RAG 문서 검색은 원본 쿼리 사용으로 불일치

### 변경 대상 파일

| 파일 | 변경 내용 | 변경 규모 |
|---|---|---|
| `chat/service/impl/EgovSessionAwareChatServiceImpl.java` | `.user(query)` → `.user(searchQuery)` | 1줄 |

### 리스크

| 리스크 | 수준 | 대응 |
|---|---|---|
| LLM에 압축 쿼리가 user 메시지로 노출 | 낮음 | 압축 쿼리는 원본의 의미를 보존하므로 응답 품질 영향 없음 |
| 압축 실패 시 압축 쿼리 = 원본 쿼리 | 없음 | `EgovCompressionQueryTransformer`가 실패 시 원본 반환 |

### 구현 방법

```java
// EgovSessionAwareChatServiceImpl.java
// 변경 전
var requestSpec = promptSpec.user(query);

// 변경 후
var requestSpec = promptSpec.user(searchQuery);  // 압축된 쿼리를 user 메시지로 전달
```

**난이도:** 매우 낮음 | **예상 작업량:** 5분

---

## 4. 채팅 메모리 영속성 (⚠️ P3 — 중간)

### 현재 문제

```java
// 현재: InMemoryChatMemory (휘발성)
// 서버 재시작 시 모든 대화 이력 소멸
// EgovCompressionQueryTransformer가 이력 없는 상태로 시작 → 압축 품질 저하
```

### eGovFrame 공식 구현

```java
// EgovRedisChatMemoryRepository implements ChatMemoryRepository
@Override
public void saveAll(String conversationId, List<Message> messages) {
    String key = "chat:memory:" + conversationId;
    String messagesJson = objectMapper.writeValueAsString(toSimpleMessages(messages));
    redisTemplate.opsForValue().set(key, messagesJson);
}

@Override
public List<Message> findByConversationId(String conversationId) {
    String key = "chat:memory:" + conversationId;
    Object value = redisTemplate.opsForValue().get(key);
    return deserialize(value);
}
```

### 영향 분석

| 항목 | InMemoryChatMemory | Redis 기반 |
|---|---|---|
| 서버 재시작 후 이력 | 소멸 | 유지 |
| 다중 인스턴스 환경 | 인스턴스별 독립 | 공유 |
| 압축 품질 (재시작 후) | 저하 (이력 없음) | 정상 유지 |
| Redis 의존성 | 없음 | 필요 (이미 Redis 사용 중) |

### 변경 대상 파일

| 파일 | 변경 내용 | 변경 규모 |
|---|---|---|
| `repository/EgovRedisChatMemoryRepository.java` | 신규 생성 (`ChatMemoryRepository` 구현) | +80줄 |
| `config/EgovChatMemoryConfig.java` | `MessageWindowChatMemory` + Redis 저장소 Bean | +20줄 |

### 리스크

| 리스크 | 수준 | 대응 |
|---|---|---|
| Redis 장애 시 메모리 조회 실패 | 중간 | try-catch + 빈 List 반환 fallback |
| JSON 직렬화 복잡도 | 낮음 | Spring AI Message → Map 변환 패턴 참조 |
| 기존 `MessageChatMemoryAdvisor` Bean 변경 | 낮음 | 생성자 파라미터만 교체 |

**난이도:** 중간 | **예상 작업량:** 2~3시간

---

## 5. 유사도 임계값 차이

| 항목 | eGovFrame 공식 | 현재 프로젝트 |
|---|---|---|
| `similarityThreshold` | `0.20` (관대, 누락 최소화) | `0.70` (엄격, 정확도 우선) |
| `topK` | `3` | `3` |

eGovFrame은 임계값을 낮게 잡아 검색 결과를 많이 가져온 뒤 LLM이 필터링하는 전략.
현재 프로젝트는 높게 잡아 관련성 높은 것만 가져오는 전략.

→ ONNX `ko-sroberta-multitask` 모델 기준, 한국어 문서는 코사인 유사도 0.70 이상이면 매우 높은 관련성.
현재 값(0.70)이 지나치게 엄격하면 관련 문서가 검색되지 않을 수 있음.
**0.40~0.50으로 조정 검토 권장.**

---

## 6. ETL 및 문서 관리 차이

| 항목 | eGovFrame 공식 | 현재 프로젝트 | 우선순위 |
|---|---|---|---|
| PDF 인덱싱 | ✅ `EgovPdfReader` | ❌ 없음 | 낮음 |
| 문서 정규화 | ✅ `EgovContentFormatTransformer` | ❌ 없음 | 낮음 |
| 변경 감지(해시) | ✅ `EgovDocumentHashUtil` | ❌ 재인덱싱 시 중복 저장 가능 | 중간 |
| 청킹 | ✅ `EgovEnhancedDocumentTransformer` | ✅ `ChunkService` | 동등 |

---

## 7. 최종 결정 사항

| 항목 | 결정 | 우선순위 | 상태 |
|---|---|---|---|
| P1 — QuestionAnswerAdvisor 도입 | 완료 | 높음 | ✅ 완료 (2026-05-25) |
| P2 — 압축 쿼리 → 벡터 검색 연동 | 완료 | 높음 | ✅ 완료 (2026-05-25) |
| P3 — 채팅 메모리 Redis 전환 | 이미 완료 — 추가 작업 없음 | — | ✅ 완료 |
| P4 — 유사도 임계값 조정 (0.70 → 0.45) | 완료 | 낮음 | ✅ 완료 (2026-05-25) |
| P5 — PDF 인덱싱 | 보류 | 낮음 | ⬜ 보류 |
| P6 — 문서 변경 감지(해시) | 보류 | 낮음 | ⬜ 보류 |

### P2 구현 상세 (즉시 가능)

```java
// EgovSessionAwareChatServiceImpl.java — streamRagResponse()
// 변경 전
var requestSpec = promptSpec.user(query);
// 변경 후
var requestSpec = promptSpec.user(searchQuery);
```

### P3 구현 순서

```
1. EgovRedisChatMemoryRepository.java 생성 (ChatMemoryRepository 구현)
2. EgovChatMemoryConfig.java 생성 (MessageWindowChatMemory Bean + Redis 저장소 주입)
3. 기존 InMemoryChatMemory Bean 제거
4. 검증: 서버 재시작 후 대화 이력 유지 확인
```
