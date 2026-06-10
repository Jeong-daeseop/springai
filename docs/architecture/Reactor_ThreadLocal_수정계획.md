# Reactor + ThreadLocal 스레드 불일치 수정 계획

분석 일자: 2026-06-07

---

## 1. 현재 문제

### 근본 원인

`ThreadLocal`은 값을 설정한 스레드에서만 접근 가능한데, Reactor `Flux`의 `doFinally` 콜백은 구독 스레드와 다른 스케줄러 스레드에서 실행됩니다.
**ThreadLocal 기반 컨텍스트 전파와 Reactor 비동기 실행 모델이 구조적으로 호환되지 않습니다.**

### 문제 지점

| 문제 | 위치 | 즉시 장애 여부 |
|------|------|--------------|
| `doFinally`에서 `SessionContext.clear()` 무효 | `EgovOllamaChatController:45,70` | 메모리 누수 |
| sessionId 읽기는 현재 동작 | `EgovSessionAwareChatServiceImpl:70,126` | 즉시 장애 없음 |

### 스레드 흐름

```
[Servlet 스레드 T1]
  setupSession()
    → SessionContext.setCurrentSessionId(sessionId)   ← T1 ThreadLocal에 저장
    → streamRagResponse(message, model)
        → SessionContext.getCurrentSessionId()         ← T1에서 실행 → 현재는 정상
        → Flux 구독 시작
            → doFinally(signal -> SessionContext.clear()) ← T2(Reactor)에서 실행
                                                           → T1 ThreadLocal 정리 안 됨
                                                           → 메모리 누수
```

---

## 2. SessionContext 현재 사용 위치

| 파일 | 라인 | 호출 | 역할 |
|------|------|------|------|
| `EgovOllamaChatController.java` | :80 | `setCurrentSessionId(sessionId)` | Servlet 스레드에서 세션 ID 저장 |
| `EgovOllamaChatController.java` | :89 | `setCurrentSessionId(null)` | 세션 없을 때 null 설정 |
| `EgovOllamaChatController.java` | :33, :58 | `clear()` | 예외 시 정리 (try-catch) |
| `EgovOllamaChatController.java` | :45, :70 | `clear()` | `doFinally`에서 정리 (무효) |
| `EgovSessionAwareChatServiceImpl.java` | :70 | `getCurrentSessionId()` | RAG 스트리밍에서 세션 ID 읽기 |
| `EgovSessionAwareChatServiceImpl.java` | :126 | `getCurrentSessionId()` | 일반 스트리밍에서 세션 ID 읽기 |

---

## 3. 수정 방향 비교

| 항목 | 방향 A: 메서드 파라미터 전달 | 방향 B: Reactor Context |
|------|---------------------------|------------------------|
| 인터페이스 변경 | 필요 | 불필요 |
| 구현 복잡도 | 낮음 | 중간 |
| 디버깅 용이성 | 쉬움 (호출 스택으로 추적) | 어려움 (암묵적 전파) |
| ThreadLocal 완전 제거 | 가능 | 가능 |
| 동기 코드 혼용 | 자연스러움 | `Flux.deferContextual` 래핑 필요 |
| **권장** | **✅ 권장** | - |

### 방향 A 권장 근거

1. 컨트롤러 → 서비스 1단계 호출이므로 파라미터 전달이 가장 명확
2. `SessionContext.java` 자체 삭제 가능 → 향후 버그 재발 원천 차단
3. `EgovSessionAwareChatServiceImpl:74-78`의 동기 코드(`compressionTransformer.compress()`, `contextAssembler.build()`)를 Reactor Context 안에 넣으면 코드 의도가 불명확해짐

---

## 4. 방향 A 적용 시 변경 파일 목록

| 파일 | 변경 내용 |
|------|----------|
| `EgovSessionAwareChatService.java` | 인터페이스 시그니처에 `sessionId` 파라미터 추가 (2개 메서드) |
| `EgovSessionAwareChatServiceImpl.java` | 시그니처 변경 + `SessionContext.getCurrentSessionId()` 제거 |
| `EgovOllamaChatController.java` | `sessionId` 파라미터 전달, `SessionContext` 사용 전면 제거, `setupSession()` 반환값 변경 |
| `SessionContext.java` | **삭제** |

---

## 5. 수정 전후 코드 비교

### EgovSessionAwareChatService.java (인터페이스)

```java
// Before
Flux<ChatResponse> streamRagResponse(String message, String model);
Flux<ChatResponse> streamSimpleResponse(String message, String model);

// After
Flux<ChatResponse> streamRagResponse(String message, String model, String sessionId);
Flux<ChatResponse> streamSimpleResponse(String message, String model, String sessionId);
```

### EgovOllamaChatController.java

```java
// Before
setupSession(sessionId, message);
return egovSessionAwareChatService.streamRagResponse(message, model)
    ...
    .doFinally(signal -> SessionContext.clear());

// After
String resolvedSessionId = setupSession(sessionId, message);  // sessionId 반환
return egovSessionAwareChatService.streamRagResponse(message, model, resolvedSessionId)
    ...
    // doFinally SessionContext.clear() 제거
```

### EgovSessionAwareChatServiceImpl.java

```java
// Before
public Flux<ChatResponse> streamRagResponse(String message, String model) {
    String sessionId = SessionContext.getCurrentSessionId();
    ...
}

// After
public Flux<ChatResponse> streamRagResponse(String message, String model, String sessionId) {
    // SessionContext.getCurrentSessionId() 제거 — 파라미터로 직접 수신
    ...
}
```

### setupSession() 반환값 변경

```java
// Before: void
private void setupSession(String sessionId, String message) {
    if (...) {
        SessionContext.setCurrentSessionId(sessionId);
        ...
    } else {
        SessionContext.setCurrentSessionId(null);
    }
}

// After: String 반환
private String setupSession(String sessionId, String message) {
    if (sessionId != null && !sessionId.isEmpty() && egovChatSessionService.sessionExists(sessionId)) {
        ...
        return sessionId;
    }
    return "default";
}
```

---

## 6. 수정 시 주의사항

1. **`doFinally`의 `SessionContext.clear()` 반드시 제거** — `SessionContext` 삭제 시 자동 해결
2. **null/빈 문자열 처리 로직 이동** — `SessionContext:12-14`의 `"default"` 폴백 로직을 `setupSession()` 반환값으로 이동
3. **`ChatMemory.CONVERSATION_ID`는 변경 불필요** — 지역 변수 캡처 방식으로 이미 스레드 안전
4. **`EgovChatSessionServiceImpl`은 변경 불필요** — 이미 sessionId를 파라미터로 직접 받는 정상 패턴
5. **테스트 코드 확인** — `SessionContext` 모킹 테스트가 있다면 함께 수정

---

## 7. 수정 후 기대 효과

| 항목 | 수정 전 | 수정 후 |
|------|---------|---------|
| ThreadLocal 메모리 누수 | 발생 | 제거 |
| 세션 ID 전달 명확성 | 암묵적 (ThreadLocal) | 명시적 (파라미터) |
| 디버깅 용이성 | 낮음 | 높음 |
| Reactor 비동기 안전성 | 구조적 불일치 | 완전 해소 |
| 코드 복잡도 | `SessionContext` 우회 필요 | 직관적 파라미터 전달 |
