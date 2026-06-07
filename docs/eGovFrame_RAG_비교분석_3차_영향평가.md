# eGovFrame 공식 RAG vs 현재 프로젝트 비교 분석 3차 영향평가

작성일: 2026-05-25
참조: https://github.com/eGovFramework/egovframe-ai-rag (README-spring-ai-rag-redis-stack.md)
전제: P1(QuestionAnswerAdvisor 도입) / P2(압축 쿼리 벡터 검색 연동) / P3(Redis 메모리 확인) / P4(임계값 0.45) 완료 상태 기준

---

## 현황 재검토 (3차 — 코드 실측 기반)

2차 영향평가에서 "미구현"으로 기재한 항목 중 실제 코드 확인 결과 구현 완료 항목이 추가 확인됨.

| 항목 | 2차 영향평가 | 3차 실측 결과 |
|---|---|---|
| 세션 CRUD API | 미검토 | ✅ 완전 구현 (create/getAll/getMessages/updateTitle/delete) |
| 문서 변경 감지(해시) | ❌ 없음 | `EgovDocumentHashUtil` 구현 있음 — **미적용** 상태 |
| PDF 인덱싱 | ❌ 없음 | ❌ 없음 (변동 없음) |
| 검색 결과 로깅 | 미검토 | ❌ `LoggingDocumentRetriever` 미구현 |

---

## 1. 기술 스택 비교

| 항목 | eGovFrame 공식 | 현재 프로젝트 |
|---|---|---|
| Spring AI | 1.0.1 GA | 2.0.0-M6 Milestone |
| Spring Boot | 3.5.6 | 4.0.6 |
| 빌드 도구 | Maven | Gradle |
| LLM | Ollama | Ollama (+ OpenAI 선택) |
| Vector Store | Redis Stack | Redis Stack |
| Embedding | ONNX ko-sroberta | ONNX ko-sroberta |

---

## 2. RAG 핵심 흐름 비교

### eGovFrame 공식 — 요청별 팩토리 패턴

```
streamRagResponse(query, model)
    │
    ├─ createRagAdvisor(sessionId, compressionTransformer, retriever, flag)
    │       └─ RetrievalAugmentationAdvisor
    │               ├─ SessionAwareQueryTransformer  ← 압축기 Advisor 내부에 내장
    │               └─ LoggingDocumentRetriever      ← 검색 결과 로깅 래퍼
    │
    └─ chatClient.prompt()
            .advisors(messageChatMemoryAdvisor, ragAdvisor)  ← 요청마다 새 인스턴스
            .advisors(CONVERSATION_ID, sessionId)
            .stream()
```

### 현재 프로젝트 — 싱글톤 Bean + 수동 압축 (P1~P4 완료 후)

```
streamRagResponse(query, model)
    │
    ├─ compressionTransformer.compress(query, sessionId) → searchQuery  ← 압축 Advisor 외부
    ├─ contextAssembler.build(searchQuery)               → DB 스키마/이력/관계  ← 현재만의 강점
    │
    └─ chatClient.prompt()
            .system(context)
            .user(searchQuery)                           ← P2: 압축 쿼리로 벡터 검색 ✅
            .advisors(questionAnswerAdvisor, messageChatMemoryAdvisor)  ← 싱글톤
            .advisors(CONVERSATION_ID, sessionId)
            .stream()
```

---

## 3. 항목별 상세 비교

### 3-1. RAG Advisor

| 항목 | eGovFrame | 현재 프로젝트 | 상태 |
|---|---|---|---|
| 클래스 | `RetrievalAugmentationAdvisor` (1.0.1) | `QuestionAnswerAdvisor` (2.0.0-M6) | 버전 차이 — 기능 동등 |
| 생성 주기 | 요청마다 (팩토리) | 싱글톤 Bean | 잔존 차이 |
| 벡터 검색 래퍼 | `VectorStoreDocumentRetriever` 별도 Bean | `QuestionAnswerAdvisor.builder(vectorStore)` | 구조 차이 |
| 검색 결과 로깅 | `LoggingDocumentRetriever` 래퍼 | ❌ 없음 | 미구현 |
| 압축 쿼리 → 벡터 검색 | Advisor 내부 `SessionAwareQueryTransformer` | `.user(searchQuery)` 전달 (P2) | 동등 ✅ |

### 3-2. 질의 압축

| 항목 | eGovFrame | 현재 프로젝트 | 상태 |
|---|---|---|---|
| 구현체 | `EgovCompressionQueryTransformer` | `EgovCompressionQueryTransformer` | 동등 ✅ |
| 압축 적용 위치 | Advisor 내부 `SessionAwareQueryTransformer` 래퍼 | `streamRagResponse()` 진입 시 먼저 실행 | 위치 차이 |
| 벡터 검색 반영 | ✅ Advisor 내부 일체 처리 | ✅ `.user(searchQuery)` (P2) | 동등 ✅ |
| 스키마/이력 검색 반영 | — (해당 기능 없음) | ✅ `contextAssembler.build(searchQuery)` | 현재 우위 ✅ |

### 3-3. 채팅 메모리

| 항목 | eGovFrame | 현재 프로젝트 | 상태 |
|---|---|---|---|
| 저장소 | `EgovRedisChatMemoryRepository` | `EgovRedisChatMemoryRepository` | 동등 ✅ |
| 직렬화 | JSON (Map 변환) | JSON (Map 변환) | 동등 ✅ |
| 창 크기 | `MessageWindowChatMemory` max 20 | `MessageWindowChatMemory` max 20 | 동등 ✅ |
| 영속성 | Redis (재시작 후 유지) | Redis (재시작 후 유지) | 동등 ✅ |

### 3-4. 세션 관리

| 기능 | eGovFrame | 현재 프로젝트 | 상태 |
|---|---|---|---|
| 새 세션 생성 | `POST /api/chat/sessions` | `createNewSession()` ✅ | 동등 ✅ |
| 전체 세션 조회 | `GET /api/chat/sessions` | `getAllSessions()` ✅ | 동등 ✅ |
| 세션 메시지 조회 | `GET /api/chat/sessions/{id}/messages` | `getSessionMessages()` ✅ | 동등 ✅ |
| 세션 제목 변경 | `PUT /api/chat/sessions/{id}/title` | `updateSessionTitle()` ✅ | 동등 ✅ |
| 세션 삭제 | `DELETE /api/chat/sessions/{id}` | `deleteSession()` ✅ | 동등 ✅ |

### 3-5. ETL 파이프라인

| 항목 | eGovFrame | 현재 프로젝트 | 상태 |
|---|---|---|---|
| Markdown 인덱싱 | `EgovMarkdownReader` | `EgovDocumentServiceImpl.loadDocumentsAsync()` | 동등 ✅ |
| PDF 인덱싱 | `EgovPdfReader` | ❌ 없음 | 미구현 |
| 문서 정규화 | `EgovContentFormatTransformer` | ❌ 없음 | 미구현 |
| 청킹 | `EgovEnhancedDocumentTransformer` | `ChunkService` | 동등 ✅ |
| 벡터 저장 | `EgovVectorStoreWriter` | `RagService.ingestText()` | 동등 ✅ |
| 변경 감지(해시) | `EgovDocumentHashUtil` 적용 | `EgovDocumentHashUtil` 구현만 있음 — **미적용** | 연결 필요 |
| 재인덱싱 중복 방지 | ✅ 해시 비교로 변경분만 저장 | ❌ 매번 전체 재저장 | 개선 필요 |

### 3-6. 유사도 임계값

| 항목 | eGovFrame | 현재 프로젝트 | 상태 |
|---|---|---|---|
| `similarityThreshold` | 0.20 (관대) | 0.45 (P4 완료) | 조정 완료 ✅ |
| `topK` | 3 | 3 | 동등 ✅ |

### 3-7. 현재 프로젝트만의 차별점

| 항목 | eGovFrame | 현재 프로젝트 |
|---|---|---|
| DB 스키마 컨텍스트 | ❌ | ✅ `ContextAssembler` → 컬럼/PK/타입 |
| 생성 이력 컨텍스트 | ❌ | ✅ `GenerationHistoryService` → 도메인 재활용 |
| 테이블 관계 컨텍스트 | ❌ | ✅ `TableRelationService` → FK/암묵적 JOIN |
| MCP Tool 서버 | ❌ | ✅ Claude Desktop 연동 도구 다수 |

---

## 4. 남은 개선 포인트 상세 분석

### P5 — EgovDocumentHashUtil 활성화 (⚠️ 중간)

#### 문제 정의

`EgovDocumentHashUtil.calculateHash(content)` 구현은 완료되어 있으나,
`EgovDocumentServiceImpl.loadDocumentsAsync()`에서 사용하지 않아
재인덱싱 시 전체 파일을 매번 재저장한다.

```java
// 현재 EgovDocumentServiceImpl.loadDocumentsAsync()
for (Path file : mdFiles) {
    String content = Files.readString(file);
    String docId = file.getFileName().toString();
    ragService.ingestText(docId, content, "document");  // ← 변경 여부 무관하게 항상 저장
    changedCount.incrementAndGet();
}
```

#### 목표 흐름

```java
// 개선 후
for (Path file : mdFiles) {
    String content = Files.readString(file);
    String docId = file.getFileName().toString();
    String newHash = EgovDocumentHashUtil.calculateHash(content);
    String savedHash = hashStore.get(docId);           // ← Redis에서 이전 해시 조회

    if (newHash.equals(savedHash)) {                   // ← 변경 없으면 스킵
        processedCount.incrementAndGet();
        continue;
    }

    ragService.ingestText(docId, content, "document"); // ← 변경분만 저장
    hashStore.put(docId, newHash);                     // ← 새 해시 저장
    changedCount.incrementAndGet();
}
```

#### 변경 대상 파일

| 파일 | 변경 내용 | 변경 규모 |
|---|---|---|
| `chat/service/impl/EgovDocumentServiceImpl.java` | 해시 비교 로직 추가 | +15줄 |
| `chat/config/EgovRedisConfig.java` 또는 별도 | 해시 저장소 (`chat:hash:{docId}`) | 기존 RedisTemplate 재사용 |

#### 리스크

| 리스크 | 수준 | 대응 |
|---|---|---|
| Redis 장애 시 해시 조회 실패 → 전체 재저장 | 낮음 | catch → 기존 방식(전체 저장) 폴백 |
| 기존 저장 문서와 해시 불일치 초기 1회 전체 재저장 | 없음 | 첫 실행은 정상 동작 |

**난이도:** 낮음 | **예상 작업량:** 30분

---

### P6 — LoggingDocumentRetriever 패턴 (△ 낮음)

#### 문제 정의

eGovFrame은 `VectorStoreDocumentRetriever`를 `LoggingDocumentRetriever`로 래핑하여
RAG 검색 결과를 실시간 로깅한다. 현재 `QuestionAnswerAdvisor`는 검색 결과를
`AdvisedResponse` 메타데이터(`QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS`)로 반환하지만
별도 로깅이 없어 검색 품질 모니터링이 불가능하다.

#### 개선 방법 (선택적)

`QuestionAnswerAdvisor` 후처리 시점에 로깅 추가. 또는 `streamRagResponse()`에서
`AdvisedResponse`의 메타데이터를 꺼내 로깅하는 방식.

**난이도:** 낮음 | **우선순위:** 낮음 (기능 영향 없음, 모니터링 목적)

---

### P7 — PDF 인덱싱 (△ 낮음)

#### 현황

eGovFrame 공식 문서 중 PDF 형식 존재. 현재 `EgovDocumentServiceImpl`은 `.md` 파일만 처리.

#### 개선 방법

Spring AI `PagePdfDocumentReader` 또는 Apache PDFBox 추가.
`RagService`에 `ingestPdf(path)` 메서드 추가.

**난이도:** 중간 | **우선순위:** 낮음 (현재 eGovFrame 문서 대부분 Markdown 제공)

---

## 5. 최종 결정 사항

| 항목 | 결정 | 우선순위 | 상태 |
|---|---|---|---|
| P1 — QuestionAnswerAdvisor 도입 | 완료 | 높음 | ✅ 완료 (2026-05-25) |
| P2 — 압축 쿼리 → 벡터 검색 연동 | 완료 | 높음 | ✅ 완료 (2026-05-25) |
| P3 — 채팅 메모리 Redis 전환 | 이미 구현됨 | — | ✅ 완료 |
| P4 — 유사도 임계값 0.70 → 0.45 | 완료 | 낮음 | ✅ 완료 (2026-05-25) |
| P5 — EgovDocumentHashUtil 활성화 | 완료 | 중간 | ✅ 완료 (2026-05-25) |
| P6 — LoggingDocumentRetriever 패턴 | 보류 | 낮음 | ⬜ 보류 |
| P7 — PDF 인덱싱 | 보류 | 낮음 | ⬜ 보류 |

---

## 6. eGovFrame 공식 대비 현재 프로젝트 완성도

| 영역 | 완성도 | 비고 |
|---|---|---|
| RAG 벡터 검색 | ✅ 동등 | QuestionAnswerAdvisor (버전 차이만) |
| 질의 압축 | ✅ 동등 | P2 완료로 벡터 검색에도 반영 |
| 채팅 메모리 | ✅ 동등 | Redis 영속 완비 |
| 세션 관리 | ✅ 동등 | CRUD 완비 |
| ETL 파이프라인 | ⚠️ 90% | 해시 미적용 / PDF 미지원 |
| 검색 모니터링 | ⚠️ 미흡 | LoggingDocumentRetriever 없음 |
| DB 특화 컨텍스트 | ✅ 현재 우위 | eGovFrame에 없는 차별점 |
