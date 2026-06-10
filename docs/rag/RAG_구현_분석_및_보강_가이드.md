# RAG 구현 분석 및 보강 가이드

> 분석 일자: 2026-06-04  
> 대상 버전: Spring AI 2.0.0-M6 / Spring Boot 4.0.6

---

## 1. 현재 RAG 파이프라인 구조

```
사용자 질문
  │
  ▼
EgovCompressionQueryTransformer       ← 멀티턴 쿼리 재작성 (LLM 호출 #1)
  │
  ▼
ContextAssembler                      ← DB 스키마 + 생성 이력 + 테이블 관계
  │  (테이블명 감지 실패 시 빈 문자열)
  ▼
ChatClient
  ├── system prompt  ← assembledSystemPrompt(context) 또는 systemRole()
  ├── QuestionAnswerAdvisor            ← VectorStore 검색 → user message 추가
  └── MessageChatMemoryAdvisor         ← Redis 대화 이력 20개
  │
  ▼
Ollama LLM                            ← 실제 응답 생성 (LLM 호출 #2)
  │
  ▼
스트리밍 응답
```

### 주요 구성 파일

| 파일 | 역할 |
|------|------|
| `EgovRagConfig.java` | `QuestionAnswerAdvisor` 빈 등록 (top-k, similarity threshold) |
| `EgovChatMemoryConfig.java` | `MessageWindowChatMemory` (Redis, max 20개) |
| `EgovSessionAwareChatServiceImpl.java` | 파이프라인 오케스트레이션 |
| `EgovCompressionQueryTransformer.java` | 멀티턴 쿼리 압축 |
| `ContextAssembler.java` | DB 스키마 + 이력 + 관계 조합 |
| `RagService.java` | VectorStore 임베딩 및 검색 |
| `ChunkService.java` | 700자 / 100자 overlap 청킹 |
| `VectorStoreConfig.java` | Redis VectorStore 빈 설정 |
| `EgovDocumentServiceImpl.java` | PDF/MD 문서 임베딩 처리 |

---

## 2. 발견된 문제 및 보강 포인트

### 🔴 Critical — 구조적 결함 (즉시 수정 필요)

---

#### 2-1. RAG 검색 결과가 시스템 프롬프트에 미반영

**문제:**  
테이블명이 없는 일반 질문(예: "Spring AI RAG란?")에서 `ContextAssembler`가 빈 문자열을 반환하면 시스템 프롬프트에 RAG 참조 지시가 없는 상태로 `QuestionAnswerAdvisor`가 VectorStore 검색 결과를 user message에 추가한다. LLM은 "아래 문서를 참고하여 답변하라"는 지시 없이 문서를 받으므로 무시할 수 있다.

```java
// EgovSessionAwareChatServiceImpl.java
String context = contextAssembler.build(searchQuery); // 테이블명 없으면 "" 반환

if (!context.isEmpty()) {
    promptSpec.system(assembledSystemPrompt(context)); // DB 관련 질문만 실행
} else {
    promptSpec.system(promptBuilder.systemRole());     // "eGovFrame 전문가입니다" 만 주입
}
// QuestionAnswerAdvisor는 실행되지만 시스템 프롬프트에 참조 지시 없음
```

`EgovPromptBuilder.ragSystemPrompt()` 메서드가 정의되어 있으나 **어디서도 호출되지 않음.**

**수정 방향:**
```java
// QuestionAnswerAdvisor가 문서를 검색했을 때 시스템 프롬프트에 참조 지시 추가
if (!context.isEmpty()) {
    promptSpec.system(assembledSystemPrompt(context));
} else {
    promptSpec.system(promptBuilder.ragSystemPrompt(""));
    // 또는 QuestionAnswerAdvisor의 userTextAdvise 커스터마이징
}
```

---

#### 2-2. 컨텍스트 토큰 초과 위험

**문제:**  
현재 `num-ctx: 4096` 설정에서 실제 컨텍스트 합산량이 허용치를 초과한다.

| 항목 | 크기 |
|------|------|
| ChatMemory 20개 × ~500토큰 | ~10,000 토큰 |
| ContextAssembler (MAX 6,000자) | ~3,000 토큰 |
| RAG 청크 3개 × 700자 | ~1,050 토큰 |
| 시스템 프롬프트 | ~100 토큰 |
| **합계** | **~14,150 토큰** |

`num-ctx: 4096`으로는 수용 불가 → LLM이 컨텍스트 앞부분(이력, RAG 결과)을 잘라냄.

**수정 방향:**
```yaml
# application.yaml
ollama:
  chat:
    options:
      num-ctx: 8192      # 최소 8192 이상으로 확대
chat:
  memory:
    max-messages: 8      # 20 → 8로 축소
```

---

#### 2-3. `initializeSchema` 설정 충돌

**문제:**  
동일 설정이 Java 코드와 YAML에서 상반된 값으로 정의되어 있다.

```yaml
# application.yaml:51
initialize-schema: false    ← Spring Boot Auto-config에 전달
```
```java
// VectorStoreConfig.java:43
.initializeSchema(true)     ← @Bean 직접 설정 (실제 적용값)
```

`@Bean`이 Auto-config를 오버라이드하므로 실제로는 `true`로 동작하나, YAML 주석과 불일치로 혼란 유발.

**수정 방향:**  
YAML을 `true`로 일치시키거나 YAML 항목을 제거하고 Java 코드 주석으로 명시.

---

### 🟡 중간 — 품질 저하 요인 (개선 권장)

---

#### 2-4. VectorStore 문서 타입 혼재 — 검색 필터 없음

**문제:**  
VectorStore에 세 종류 문서가 혼재하지만 `QuestionAnswerAdvisor`는 구분 없이 전체 검색.

| type | 내용 |
|------|------|
| `document` | eGovFrame 교재 PDF, docs MD |
| `source_code` | Java 소스 파일 |
| `history` | 코드 생성 이력 |

채팅 RAG에서 `source_code` 청크가 섞이면 교재 기반 답변 품질이 저하된다.

**수정 방향:**
```java
// EgovRagConfig.java
QuestionAnswerAdvisor.builder(vectorStore)
    .searchRequest(SearchRequest.builder()
        .topK(topK)
        .similarityThreshold(similarityThreshold)
        .filterExpression("type == 'document'")  // 교재 문서만 검색
        .build())
    .build();
```

---

#### 2-5. 유사도 임계값 0.45 — 너무 낮음

**문제:**  
`rag.similarity.threshold: 0.45`는 관련성이 낮은 청크도 검색 결과에 포함시킨다.  
`ko-sroberta-multitask` 기준으로 0.45는 의미적 유사성이 매우 낮은 수준이다.

**수정 방향:**
```yaml
rag:
  similarity:
    threshold: 0.60    # 0.45 → 0.60으로 상향
```

---

#### 2-6. 중복 docId — 파일명 충돌

**문제:**  
`textbook`과 `docs` 두 경로에 동일한 파일명이 존재할 경우 Redis 해시 키 충돌 발생.

```java
// EgovDocumentServiceImpl.java
String docId = file.getFileName().toString();      // 파일명만 사용 (경로 무시)
String hashKey = HASH_KEY_PREFIX + docId;          // 동일 파일명 → 충돌
```

**수정 방향:**
```java
// 경로 기반 상대 경로를 docId로 사용
Path baseDir = Paths.get(pathStr);
String docId = baseDir.relativize(file).toString().replace(File.separator, "_");
```

---

#### 2-7. 문서 업데이트 시 구 청크 잔존

**문제:**  
파일 변경 시 새 청크를 추가하지만 기존 청크를 삭제하지 않아 중복 청크가 누적된다.

```java
// RagService.java
vectorStore.add(docs);   // 기존 청크 삭제 없이 추가만 함
```

**수정 방향:**
```java
// 기존 청크 삭제 후 추가
vectorStore.delete(List.of(docId + "-0", docId + "-1", ...));  // 또는 필터 기반 삭제
vectorStore.add(docs);
```

---

#### 2-8. 쿼리 압축이 항상 LLM 추가 호출 발생

**문제:**  
`enableQueryCompression: true`이면 히스토리가 없는 첫 질문도 Redis 조회 → 빈 리스트 확인 후 원본 반환하는 과정에서 불필요한 지연 발생. 히스토리가 있는 경우 LLM 호출이 1회 추가된다.

**수정 방향:**
- 세션의 메시지 수를 빠르게 확인하는 캐시 레이어 추가
- 또는 히스토리 2개 미만이면 압축 스킵

---

### 🟢 낮음 — 장기 개선

---

#### 2-9. HyDE (Hypothetical Document Embeddings) 미구현

짧은 질문을 가상 답변으로 확장 후 임베딩하여 벡터 유사도 검색 품질을 향상시키는 기법.  
현재 `EgovCompressionQueryTransformer`를 확장하여 HyDE를 함께 적용 가능.

#### 2-10. RAG 검색 결과 출처 미표시

`QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS` 메타데이터를 활용하면  
응답에 "5차시 Spring AI 교재 p.12 참조" 형태의 출처 표시 가능.

#### 2-11. PDF 페이지 단위 청킹 미활용

`PagePdfDocumentReader`는 페이지별 Document를 반환하는데,  
이를 다시 700자로 분할하면 페이지 중간에서 의미 단위가 끊김.  
PDF는 페이지 단위, Markdown은 `##` 섹션 단위 청킹으로 분리 권장.

---

## 3. 수정 현황 (2026-06-04 기준)

### ✅ 완료

| 순위 | 항목 | 수정 파일 | 변경 내용 |
|------|------|----------|----------|
| 1 | RAG 시스템 프롬프트 미반영 수정 | `EgovSessionAwareChatServiceImpl.java` | `systemRole()` → `ragSystemPrompt("")` |
| 2 | `num-ctx` 확대 | `application.yaml` | 4096 → 8192 |
| 3 | `max-messages` 축소 | `application.yaml` | 20 → 8 |
| 4 | VectorStore 타입 필터 추가 | `EgovRagConfig.java` | `filterExpression("type == 'document'")` |
| 5 | 유사도 임계값 상향 | `application.yaml` | 0.45 → 0.60 |
| 6 | `top-k` 확대 | `application.yaml` | 3 → 5 |
| 7 | `initializeSchema` 충돌 정리 | `application.yaml` | `false` → `true` |
| 8 | 중복 docId 수정 | `EgovDocumentServiceImpl.java` | 파일명 → baseDir 기준 상대 경로 |

### ⚠️ 완료 후 검토 필요

| 항목 | 파일 | 내용 |
|------|------|------|
| `ragSystemPrompt("")` 빈 문구 노출 | `EgovPromptBuilder.java` | `ragContext`가 빈 문자열일 때 "아래 참고 문서를 기반으로 답변하세요:" 문구가 그대로 출력됨 — 조건 처리 검토 필요 |
| 유사도 임계값 0.60 적정성 | `application.yaml` | `ko-sroberta-multitask` 실제 점수 분포 미확인 — 운영 전 실 질의 테스트로 검증 필요 |
| docId 변경으로 전체 재임베딩 발생 | — | 서버 재시작 후 첫 인덱싱 시 54개 파일 전체 재임베딩됨 (예상된 동작) |

---

### ❌ 미진행

| 순위 | 항목 | 수정 대상 파일 | 예상 영향 |
|------|------|--------------|-----------|
| 1 | 구 청크 삭제 로직 추가 | `RagService.java` | 파일 변경 시 이전 청크가 VectorStore에 누적되는 문제 해소 |
| 2 | 쿼리 압축 조건 개선 | `EgovCompressionQueryTransformer.java` | 히스토리 2개 미만 시 LLM 호출 생략 → 응답 속도 개선 |
| 3 | HyDE 구현 | `EgovCompressionQueryTransformer.java` | 짧은 질문을 가상 답변으로 확장 후 임베딩 → 검색 품질 향상 |
| 4 | RAG 검색 결과 출처 표시 | 채팅 응답 후처리 | `QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS` 활용, "교재 p.12 참조" 형태 출처 표시 |
| 5 | PDF 페이지 단위 청킹 | `EgovDocumentServiceImpl.java` | PDF는 페이지 단위, MD는 `##` 섹션 단위로 분리 청킹 |
