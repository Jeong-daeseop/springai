# LLM 라우팅 통합 — 옵션 1 영향 검토

작성일: 2026-06-07  
검토 방법: 실제 코드 직접 확인 (LlmRouterService, ToolApiController, RagController, EgovSessionAwareChatServiceImpl, EgovRagConfig)

---

## 1. 변경 대상 파일 (4개)

### 1.1 `LlmRouterService.java` — 핵심 변경

**현재 구조:**
```java
// 주입: ChatModel (저수준)
private final OpenAiChatModel openAiChatModel;
private final OllamaChatModel ollamaChatModel;

// 반환: String (동기 블로킹)
public String chat(String taskType, String message)
```

**변경 후 구조:**
```java
// 주입: ChatClient (고수준) — EgovRagConfig에 이미 @Bean 존재
@Qualifier("openAiChatClient") ChatClient openAiChatClient;
@Qualifier("ollamaChatClient") ChatClient ollamaChatClient;

// 신규: ChatClient 반환 (라우팅 핵심)
public ChatClient route(String modelOrTaskType)

// 기존 chat() 시그니처 유지 — 내부적으로 route() 위임 (하위 호환)
public String chat(String taskType, String message) {
    return route(taskType).prompt().user(message).call().content();
}
```

**영향:** `EgovRagConfig`에 `openAiChatClient`, `ollamaChatClient` 빈이 이미 존재 → 추가 의존성 없음

---

### 1.2 `ToolApiController.java` — 변경 없음

**현재 코드 (`ToolApiController.java:74`):**
```java
String result = llmRouterService.chat(body.get("taskType"), body.get("message"));
return Map.of("result", result, "routedTo", body.get("taskType"));
```

`chat()` 메서드 시그니처 유지 시 → **이 파일 변경 불필요**

---

### 1.3 `RagController.java` — 변경 없음

**현재 코드 (`RagController.java:96`):**
```java
String answer = llmRouterService.chat(taskType, prompt);
```

`chat()` 메서드 시그니처 유지 시 → **이 파일 변경 불필요**

---

### 1.4 `EgovSessionAwareChatServiceImpl.java` — 핵심 변경

**현재 코드:**
```java
// selectClient() 독립 구현 — 라우팅 로직 중복
private ChatClient selectClient(String model) {
    if (model != null &&
        (model.startsWith("gpt-") || model.startsWith("o1") || model.startsWith("o3")))
        return openAiChatClient;
    return ollamaChatClient;
}
```

**변경 후:**
```java
// LlmRouterService 주입 추가
private final LlmRouterService llmRouterService;

// selectClient() → route() 위임 (라우팅 중복 제거)
private ChatClient selectClient(String model) {
    return llmRouterService.route(model);
}
```

---

## 2. 위험 요소 분석

| 항목 | 위험도 | 내용 |
|------|--------|------|
| 빈 순환 의존 | 🟢 없음 | `LlmRouterService` → `ChatClient`(EgovRagConfig) → 단방향, 순환 없음 |
| `chat()` 하위 호환 | 🟢 없음 | 시그니처 유지 + 내부 위임으로 구현 → `ToolApiController`, `RagController` 변경 불필요 |
| `route()` 라우팅 기준 통합 | 🟡 낮음 | taskType(`SIMPLE_QUERY`) + 모델명(`gpt-4o`) 두 기준을 하나의 메서드로 처리 → 분기 로직 명확히 작성 필요 |
| 생성자 파라미터 추가 | 🟡 낮음 | `EgovSessionAwareChatServiceImpl` 생성자 파라미터 7→8개 증가 |
| `ChatClient` 직접 주입 제거 | 🟡 낮음 | `EgovSessionAwareChatServiceImpl`에서 `openAiChatClient`, `ollamaChatClient` 직접 주입 제거 가능 — 테스트 코드 영향 확인 필요 |

---

## 3. 실제 수정 범위

`chat()` 메서드 시그니처를 유지하는 방식으로 구현하면 수정 범위가 **예상보다 훨씬 작음:**

| 파일 | 변경 유형 | 변경량 |
|------|----------|--------|
| `LlmRouterService.java` | `ChatModel` → `ChatClient` 주입, `route()` 신규 추가 | ~20줄 |
| `EgovSessionAwareChatServiceImpl.java` | `selectClient()` 위임 변경, 생성자 파라미터 추가 | ~10줄 |
| `ToolApiController.java` | **변경 없음** | 0줄 |
| `RagController.java` | **변경 없음** | 0줄 |

---

## 4. `route()` 메서드 라우팅 기준 설계

두 가지 입력(taskType / 모델명)을 하나의 메서드로 처리하는 분기 로직:

```java
public ChatClient route(String modelOrTaskType) {
    if (modelOrTaskType == null) return ollamaChatClient;

    // 모델명 기반 (웹 채팅 경로)
    if (modelOrTaskType.startsWith("gpt-") ||
        modelOrTaskType.startsWith("o1")   ||
        modelOrTaskType.startsWith("o3"))
        return openAiChatClient;

    // taskType 기반 (REST API 경로)
    return switch (modelOrTaskType.toUpperCase()) {
        case "CLASSIFICATION", "SIMPLE_QUERY" -> openAiChatClient;
        case "SENSITIVE_DATA"                 -> ollamaChatClient;
        default                               -> ollamaChatClient;
    };
}
```

---

## 5. 의존성 변화

### 변경 전

```
ToolApiController       → LlmRouterService → OpenAiChatModel
RagController           → LlmRouterService → OllamaChatModel
EgovSessionAwareChatServiceImpl → openAiChatClient (직접)
                                → ollamaChatClient (직접)
```

### 변경 후

```
ToolApiController       → LlmRouterService → openAiChatClient (ChatClient)
RagController           → LlmRouterService → ollamaChatClient (ChatClient)
EgovSessionAwareChatServiceImpl → LlmRouterService → openAiChatClient / ollamaChatClient
```

---

## 6. 결론

| 항목 | 평가 |
|------|------|
| 리스크 | 낮음 — 빈 순환 없음, 하위 호환 유지 가능 |
| 실제 수정 파일 | 2개 (`LlmRouterService`, `EgovSessionAwareChatServiceImpl`) |
| 실제 변경량 | 약 30줄 |
| 기대 효과 | 라우팅 로직 단일 진실 공급원 확보, 향후 LLM 추가 시 1곳만 수정 |
| 진행 권고 | **진행 가능** |
