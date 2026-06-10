# LLM 라우팅 이중 구조 통합 — 옵션 분석

작성일: 2026-06-07

---

## 1. 현재 구조 — 두 개의 독립적인 라우팅 메커니즘

### 메커니즘 A: `LlmRouterService`

- **위치**: `src/main/java/com/krdevops/springai/service/LlmRouterService.java`
- **사용처**: `ToolApiController` (`/api/tools/chat`), `RagController` (`/api/rag/chat`)
- **라우팅 기준**: `taskType` 문자열 (`SIMPLE_QUERY`, `SENSITIVE_DATA`, `CODE_GENERATION`)
- **사용 API**: `ChatModel.call()` — 저수준 동기 호출

```
route("SIMPLE_QUERY")   → OpenAiChatModel.call()  ← 동기 블로킹
route("SENSITIVE_DATA") → OllamaChatModel.call()  ← 동기 블로킹
route("CODE_GENERATION") → IllegalArgumentException 발생!
```

❌ 스트리밍 없음 / 채팅 메모리 없음 / RAG 어드바이저 없음

---

### 메커니즘 B: `selectClient()`

- **위치**: `src/main/java/com/krdevops/springai/chat/service/impl/EgovSessionAwareChatServiceImpl.java:52`
- **사용처**: `EgovOllamaChatController` (`/ai/rag/stream`, `/ai/simple/stream`)
- **라우팅 기준**: 모델명 접두사 (`gpt-*`, `o1*`, `o3*` → OpenAI, 그 외 → Ollama)
- **사용 API**: `ChatClient` — 고수준, 어드바이저 파이프라인 포함

```
model.startsWith("gpt-") → openAiChatClient  ← SSE 스트리밍
그 외                    → ollamaChatClient  ← SSE 스트리밍
```

✅ SSE 스트리밍 / Redis 채팅 메모리(8메시지) / QuestionAnswerAdvisor(RAG) 포함

---

### 핵심 차이점

| 항목 | LlmRouterService (A) | selectClient() (B) |
|------|----------------------|--------------------|
| 라우팅 기준 | `taskType` 문자열 | 모델명 접두사 |
| 사용 API | `ChatModel` (저수준) | `ChatClient` (고수준) |
| 스트리밍 | ❌ 동기 블로킹 | ✅ SSE Flux |
| 채팅 메모리 | ❌ 없음 | ✅ Redis 8메시지 |
| RAG 검색 | ❌ 수동 처리 | ✅ QuestionAnswerAdvisor 자동 |
| Claude 지원 | ❌ 예외 발생 | ❌ 라우팅 없음 |

---

### 문제 발생 시나리오

```
개발자: "Ollama 기본 모델 변경해야겠다" → 라우팅 조건 수정

  → LlmRouterService 수정 ✅
  → selectClient() 는 수정 안 함 ❌
  → 웹 채팅은 여전히 이전 동작 → 버그
```

두 경로가 독립적이므로 한쪽 수정이 다른 쪽에 반영되지 않는 구조적 문제입니다.

---

## 2. 통합 옵션

### 옵션 1 — `LlmRouterService`를 `ChatClient` 반환으로 업그레이드

#### 변경 내용

```java
// 현재: String 반환 (동기)
public String chat(String taskType, String message)

// 변경 후: ChatClient 반환
public ChatClient route(String modelOrTaskType) {
    if (modelOrTaskType != null &&
        (modelOrTaskType.startsWith("gpt-") ||
         modelOrTaskType.startsWith("o1")   ||
         modelOrTaskType.startsWith("o3")   ||
         "SIMPLE_QUERY".equals(modelOrTaskType) ||
         "CLASSIFICATION".equals(modelOrTaskType)))
        return openAiChatClient;
    if ("SENSITIVE_DATA".equals(modelOrTaskType))
        return ollamaChatClient;
    return ollamaChatClient; // 기본값
}
```

- `ToolApiController`, `RagController` 에서 `ChatClient`를 직접 사용하도록 변경
- `EgovSessionAwareChatServiceImpl.selectClient()` 삭제 → `llmRouterService.route(model)` 호출

#### 장단점

| 항목 | 내용 |
|------|------|
| ✅ 장점 | 라우팅 로직이 `LlmRouterService` 한 곳에만 존재 — 단일 진실 공급원 |
| ✅ 장점 | 모든 경로(REST API, 웹 채팅)가 동일한 `ChatClient` 사용 → 기능 일관성 |
| ✅ 장점 | 향후 LLM 추가 시 `LlmRouterService` 한 곳만 수정 |
| ✅ 장점 | 장기적으로 아키텍처 정합성 높음 |
| ❌ 단점 | `ToolApiController`, `RagController` 호출 방식 전면 변경 필요 |
| ❌ 단점 | REST API는 스트리밍 불필요한데 `ChatClient` 강제 → 오버엔지니어링 가능성 |
| ❌ 단점 | `RagController.ragChat()` 수동 RAG 컨텍스트 조립 로직 재설계 필요 |
| ❌ 단점 | 수정 범위 넓음 — 4개 클래스 동시 변경 |

**수정 대상**: `LlmRouterService` + `ToolApiController` + `RagController` + `EgovSessionAwareChatServiceImpl`

---

### 옵션 2 — `selectClient()`가 `LlmRouterService`에 위임

#### 변경 내용

```java
// LlmRouterService — 모델명 인식 메서드 추가
public LlmTarget routeByModel(String model) {
    if (model != null &&
        (model.startsWith("gpt-") ||
         model.startsWith("o1")   ||
         model.startsWith("o3")))
        return LlmTarget.OPENAI;
    return LlmTarget.OLLAMA;
}

// EgovSessionAwareChatServiceImpl — selectClient() 위임 호출로 변경
private ChatClient selectClient(String model) {
    LlmRouterService.LlmTarget target = llmRouterService.routeByModel(model);
    return target == LlmTarget.OPENAI ? openAiChatClient : ollamaChatClient;
}
```

- `ToolApiController`, `RagController` 코드 변경 없음
- 기존 `taskType` 기반 `route()`, `chat()` 메서드 그대로 유지

#### 장단점

| 항목 | 내용 |
|------|------|
| ✅ 장점 | 수정 범위 최소 — 2개 클래스, 총 10줄 이내 |
| ✅ 장점 | `ToolApiController`, `RagController` 코드 변경 없음 |
| ✅ 장점 | 기존 `taskType` 기반 라우팅 하위 호환 유지 |
| ✅ 장점 | 리스크 낮음 — 기존 동작 보장 |
| ❌ 단점 | 라우팅 로직이 여전히 두 군데 — `route(taskType)` + `routeByModel(model)` |
| ❌ 단점 | REST API는 여전히 동기 블로킹 — 근본 문제 미해결 |
| ❌ 단점 | `LlmRouterService`가 두 가지 라우팅 기준(taskType/modelName) 모두 관리 → 책임 혼재 |

**수정 대상**: `LlmRouterService` (메서드 추가) + `EgovSessionAwareChatServiceImpl` (2줄 변경)

---

## 3. 비교 요약

| 비교 항목 | 옵션 1 | 옵션 2 |
|-----------|--------|--------|
| 라우팅 단일화 | ✅ 완전 통합 | ❌ 부분 통합 |
| 수정 범위 | 넓음 (4개 클래스) | 좁음 (2개 클래스) |
| 하위 호환성 | ❌ API 변경 필요 | ✅ 기존 코드 유지 |
| 기능 일관성 | ✅ 모든 경로 동일 | ❌ REST는 여전히 동기 |
| 미래 확장성 | ✅ 높음 | 🟡 보통 |
| 구현 리스크 | 높음 | 낮음 |
| 구현 난이도 | 중간-높음 | 낮음 |

---

## 4. 결론 및 권장

| 상황 | 권장 옵션 |
|------|----------|
| 단기 안정화, 빠른 동기화 필요 | **옵션 2** |
| LLM 추가 예정, 장기 아키텍처 정비 | **옵션 1** |

### 단계적 접근 (권장)
1. **1단계** — 옵션 2 적용: 즉시 `selectClient()` 위임 연결로 모델명 라우팅 동기화
2. **2단계** — `ToolApiController`, `RagController` 의 실제 사용 빈도 확인
3. **3단계** — 사용 빈도가 높다면 옵션 1로 전환하여 완전 통합

현재 REST API(`/api/tools/chat`, `/api/rag/chat`)가 Claude Desktop MCP SSE의 보조 경로로만 사용된다면 옵션 2로 충분합니다.
