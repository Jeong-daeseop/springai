# eGovFrame RAG 개선 권장 사항 영향평가

작성일: 2026-05-25  
참조: docs/eGovFrame_RAG_비교분석.md

---

## 현황 재검토 (코드 실측 기반)

비교 분석 문서(eGovFrame_RAG_비교분석.md) 작성 시점과 실제 코드를 비교한 결과,
**P2(청킹), P3(질의 압축)은 이미 구현 완료** 상태임이 확인됨.

| 권장 사항 | 비교문서 기재 | 실제 현황 |
|---|---|---|
| P1 — RetrievalAugmentationAdvisor | ❌ 미구현 | ❌ 미구현 (수동 주입 방식) |
| P2 — 청킹 | ❌ 미구현 | ✅ **구현 완료** (`ChunkService` + `RagService`) |
| P3 — 질의 압축 | ❌ 미구현 | ✅ **구현 완료** (`EgovCompressionQueryTransformer`) |
| P4 — Spring AI 버전 | Milestone 상태 | Milestone 상태 (2.0.0-M6) |
| P5 — ContextAssembler 유지 | 유지 권장 | 유지 중 |

---

## P1 — RetrievalAugmentationAdvisor 도입 (미구현)

### 현재 구현 방식

```
ContextAssembler.build()
  → RagService.buildRagContext()       ← VectorStore 직접 검색 (수동)
  → 문자열로 조립
  → chatClient.prompt().system(context) ← system prompt에 통째로 주입
```

### 변경 대상 파일

| 파일 | 변경 내용 | 변경 규모 |
|---|---|---|
| `chat/config/EgovRagConfig.java` | `RetrievalAugmentationAdvisor` Bean 추가 | +10줄 |
| `chat/service/impl/EgovSessionAwareChatServiceImpl.java` | `advisors(ragAdvisor)` 추가, RAG 관련 context 주입 제거 | -5 / +3줄 |
| `service/ContextAssembler.java` | `appendRag()` 제거 또는 비활성화 | -30줄 |
| `service/RagService.java` | `buildRagContext()` 미사용 → 삭제 가능 | 선택적 |

### 영향 분석

| 항목 | 내용 |
|---|---|
| **기능 영향** | RAG 검색 결과 반환 방식 변경 (문자열 조립 → Advisor 자동 주입) |
| **ContextAssembler** | RAG 부분만 제거, 스키마/이력/관계는 유지 — 분리 가능 |
| **채팅 메모리** | `MessageChatMemoryAdvisor`와 병렬 체인 구성 — 충돌 없음 |
| **질의 압축 연동** | `EgovCompressionQueryTransformer`가 `streamRagResponse`에서 먼저 실행되므로 압축된 쿼리를 Advisor에 전달 가능 |
| **유사도 임계값** | 현재 `SearchRequest` 기본값 사용 중 → Advisor 설정 시 `similarityThreshold(0.3)` 명시 권장 |

### 리스크

| 리스크 | 수준 | 대응 |
|---|---|---|
| Spring AI 2.0.0-M6 API 변경 가능성 | 중간 | M6 `RetrievalAugmentationAdvisor` API 검증 후 적용 |
| ContextAssembler RAG 부분 이중 검색 | 낮음 | `appendRag()` 제거로 해소 |
| topK 설정 위치 이동 | 낮음 | `application.yml rag.top-k` → Advisor 설정으로 이전 |

### 구현 순서

```
1. EgovRagConfig.java — RetrievalAugmentationAdvisor Bean 추가
2. EgovSessionAwareChatServiceImpl.java — advisors()에 ragAdvisor 추가
3. ContextAssembler.java — appendRag() 비활성화 (조건부 disable 후 검증)
4. 검증 완료 후 appendRag() 완전 제거
```

**난이도:** 낮음 | **예상 작업량:** 30분~1시간

---

## P2 — 청킹 (✅ 구현 완료)

### 구현 현황

```
RagService.ingestText()
  → ChunkService.chunk(content)   ← 청킹 처리
  → List<Document> docs (청크별 메타데이터 포함)
  → vectorStore.add(docs)

RagService.ingestJavaDirectory()
  → ChunkService.chunk(content)   ← 파일별 청킹
  → List<Document> docs
  → vectorStore.add(docs)

RagService.ingestUrl()
  → ChunkService.chunk(text)      ← HTML 추출 후 청킹
  → vectorStore.add(docs)
```

청크 메타데이터: `docId`, `type`, `chunkIdx`, `total` 포함

### 평가

eGovFrame 공식 `TokenTextSplitter`와 동등한 수준의 청킹이 이미 `ChunkService`로 구현되어 있음.  
**추가 작업 불필요.**

---

## P3 — 질의 압축 (✅ 구현 완료)

### 구현 현황

```java
// EgovCompressionQueryTransformer.compress()
// - 세션 히스토리 기반 후속 질문 재작성
// - 대명사 → 구체적 용어 치환
// - isIncompleteQuery() 로 짧은 질문 바이패스
// - isLikelyAnswer() 로 LLM 오답 방지

// EgovSessionAwareChatServiceImpl.streamRagResponse()
String searchQuery = enableQueryCompression
    ? compressionTransformer.compress(query, sessionId)  // 압축 적용
    : query;                                              // 바이패스

// application.yml
// rag.enable-query-compression: true  (설정으로 제어 가능)
```

eGovFrame 공식 `RewriteQueryTransformer`와 동등한 기능을 **커스텀으로 구현 완료.**  
차이점: eGovFrame 공식은 Spring AI 표준 인터페이스 구현, 현재 프로젝트는 직접 ChatClient 호출.

### 평가

현재 구현이 기능적으로 충분함. **추가 작업 불필요.**  
단, `streamSimpleResponse()`에는 압축이 적용되지 않는 점은 설계 의도 확인 필요.

---

## P4 — Spring AI 버전 (관찰 중)

### 현재 상태

```
Spring AI 2.0.0-M6 (Milestone)
Spring Boot 4.0.6
```

### 영향 분석

| 항목 | 내용 |
|---|---|
| **GA 버전 출시 시점** | 미정 (2.0.0 GA 릴리즈 노트 모니터링 필요) |
| **API 변경 가능성** | Milestone → GA 전환 시 API breaking change 가능 |
| **현재 동작 안정성** | 2.0.0-M6 기준 현재 프로젝트 정상 동작 확인됨 |
| **다운그레이드 리스크** | 1.0.1 GA로 다운그레이드 시 Spring Boot 4.x 호환성 문제 가능 |

### 결정

**현 버전 유지.** Spring Boot 4.x + Spring AI 2.x 조합으로 개발 진행.  
GA 릴리즈 시 마이그레이션 가이드 검토 후 업그레이드.

---

## P5 — ContextAssembler DB 특화 기능 유지

### 현재 구현 강점

```
ContextAssembler.build(query, topK)
  1순위: DB 스키마 (컬럼·PK·타입)         ← eGovFrame 공식에 없는 기능
  2순위: 생성 이력 (과거 소스 패턴 재활용) ← eGovFrame 공식에 없는 기능
  3순위: 테이블 관계 (FK·암묵적 JOIN)     ← eGovFrame 공식에 없는 기능
  4순위: RAG 문서 (VectorStore 검색)       ← P1 적용 시 Advisor로 이전
```

eGovFrame CRUD 소스 자동 생성의 핵심 차별점 — **유지 확정.**

### P1 적용 후 ContextAssembler 역할 변화

```
P1 적용 전: 스키마 + 이력 + 관계 + RAG 모두 조립
P1 적용 후: 스키마 + 이력 + 관계만 조립 (RAG → Advisor 이전)
```

`MAX_CONTEXT_CHARS=4,000` 제한 완화 검토 가능 — RAG 제외 후 스키마/이력/관계에 더 많은 예산 할당 가능.

---

## 최종 결정 사항

| 항목 | 결정 | 우선순위 | 상태 |
|---|---|---|---|
| P1 — QuestionAnswerAdvisor 도입 | 구현 완료 | 높음 | ✅ 완료 (2026-05-25) |
| P2 — 청킹 | 이미 완료 — 추가 작업 없음 | — | ✅ 완료 |
| P3 — 질의 압축 | 이미 완료 — 추가 작업 없음 | — | ✅ 완료 |
| P4 — Spring AI 버전 | 현 버전(2.0.0-M6) 유지 | 낮음 | ⬜ 관찰 중 |
| P5 — ContextAssembler 유지 | 유지 확정 | — | ✅ 유지 |

### P1 구현 결과 (2026-05-25 완료)

- [x] Spring AI 2.0.0-M6 실제 클래스: `QuestionAnswerAdvisor` (`spring-ai-advisors-vector-store` 별도 의존성 추가 필요)
- [x] `RetrievalAugmentationAdvisor`는 2.0.0-M6에 존재하지 않음 — `QuestionAnswerAdvisor`로 대체
- [x] `VectorStoreDocumentRetriever` 없음 — `QuestionAnswerAdvisor.builder(vectorStore)` 직접 사용
- [x] `ContextAssembler.appendRag()` 제거 완료, `MAX_CONTEXT_CHARS` 4000→6000 확대

**변경 파일:**
- `build.gradle` — `spring-ai-advisors-vector-store` 의존성 추가
- `chat/config/EgovRagConfig.java` — `QuestionAnswerAdvisor` Bean 추가 (similarityThreshold, topK 주입)
- `chat/service/impl/EgovSessionAwareChatServiceImpl.java` — `questionAnswerAdvisor` 주입, advisors 체인 추가, `topK` 필드 제거
- `service/ContextAssembler.java` — `ragService` 필드 제거, `appendRag()` 제거, `build(String)` 시그니처 변경
