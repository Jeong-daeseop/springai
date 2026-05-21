# 공통 PromptBuilder 분리 가이드

작성일: 2026-05-21

---

## 1. 현재 상태 진단

### 프롬프트 조립 현황

현재 프롬프트는 4곳에서 각자 독립적으로 조립됩니다.

```
EgovSessionAwareChatServiceImpl   ──▶ "당신은 eGovFrame 전문 AI..."  (한국어 하드코딩)
EgovPromptEngineeringUtil         ──▶ "You are a helpful AI..."      (영어, 미사용)
CrudPromptBuilderService          ──▶ StringBuilder 직접 조립         (CRUD 전용)
MasterDetailService               ──▶ StringBuilder 직접 조립         (동일 제약 중복)
```

---

## 2. 발견된 문제 3가지

### 문제 1: 시스템 역할 문자열 불일치

시스템 역할(system role)이 4곳에서 제각각 다르게 정의되어 있습니다.

| 위치 | 역할 문자열 | 언어 |
|---|---|---|
| `EgovSessionAwareChatServiceImpl` | `"당신은 eGovFrame 전문 AI 어시스턴트입니다."` | 한국어 |
| `EgovPromptEngineeringUtil.createZeroShotPrompt()` | `"You are a helpful AI assistant."` | 영어 |
| `EgovPromptEngineeringUtil.createContextBasedPrompt()` | `"You are a helpful AI assistant with access to the following context:"` | 영어 |
| `EgovCompressionQueryTransformer` | `"You are a query rewriting assistant."` | 영어 |

**영향:** 같은 질문이라도 어느 경로로 처리되느냐에 따라 LLM이 다른 역할을 부여받습니다. eGovFrame 전문가 역할이 일관되게 적용되지 않습니다.

---

### 문제 2: CRUD 제약 문구 중복

아래 동일한 제약 블록이 두 서비스에 각각 하드코딩되어 있습니다.

**`CrudPromptBuilderService.java` (line 94~99):**
```
[소스 생성 제약 — 필수 준수]
  - 각 레이어는 반드시 getCodeTemplate(layer)가 반환한 템플릿을 기반으로 생성하세요.
  - 위 [플레이스홀더 치환 규칙]의 값을 정확히 대입하고 임의 해석하지 마세요.
  - 템플릿 구조·어노테이션·상속·import를 변경하지 마세요.
  - 플레이스홀더 외 메서드·주석·필드 추가·삭제 금지.
  - {{DOMAIN_KR}} 등 한국어 값은 위 규칙에 명시된 값만 사용하세요.
```

**`MasterDetailService.java`:** 동일 블록 복사본 존재

**`[생성 완료 후 필수 처리]` 블록** 역시 두 곳에 동일하게 중복됩니다.

**영향:** 제약 규칙을 수정할 때 두 곳을 동시에 찾아 수정해야 합니다. 한 곳만 수정하면 CRUD와 Master-Detail이 서로 다른 제약을 따르는 상태가 됩니다.

---

### 문제 3: EgovPromptEngineeringUtil 미사용

`EgovPromptEngineeringUtil`은 다양한 프롬프트 패턴 메서드를 제공하지만, 실제 채팅 서비스에서 **전혀 사용되지 않습니다.**

```java
// EgovPromptEngineeringUtil에 존재하는 메서드 (모두 미사용)
createZeroShotPrompt()
createContextBasedPrompt(String context)
createFewShotLearningPrompt(String context)
createChainOfThoughtPrompt()
createCodeGenerationPrompt(String language, String requirement)
createRoleBasedPrompt(String role, String task)
createDynamicFewShotPrompt(String context, List<Map.Entry<String, String>> examples)
```

`EgovSessionAwareChatServiceImpl`은 이 유틸을 import조차 하지 않고, 역할 문자열을 직접 인라인으로 작성합니다.

**영향:** 유틸 클래스가 사문화된 코드(dead code)로 방치됩니다. 신규 개발자는 이 유틸을 써야 하는지 알 수 없습니다.

---

## 3. 공통 PromptBuilder 설계

### 구조 개요

```
┌──────────────────────────────────────────┐
│          EgovPromptBuilder               │  ← 신규 (공통 조립기)
│                                          │
│  systemRole()      → 역할 문자열 단일화  │
│  crudConstraints() → CRUD 제약 블록      │
│  postGeneration()  → 생성 완료 후 처리   │
│  ragSystemPrompt() → RAG 컨텍스트 포함   │
└──────────────┬───────────────────────────┘
               │
       ┌───────┼───────────────┐
       ▼       ▼               ▼
  ChatService  CrudPrompt    MasterDetail
               BuilderService  Service
```

### 신규 파일: `EgovPromptBuilder.java`

**위치:** `src/main/java/com/krdevops/springai/service/EgovPromptBuilder.java`

```java
@Component
public class EgovPromptBuilder {

    // ── 시스템 역할 (단일 진실 공급원) ─────────────────────────────────

    /** 일반 채팅용 시스템 역할 */
    public String systemRole() {
        return "당신은 eGovFrame 5.x 전문 AI 어시스턴트입니다.\n" +
               "전자정부 표준프레임워크 기반의 소스 생성, 쿼리 분석, 아키텍처 안내를 담당합니다.";
    }

    /** RAG 컨텍스트 포함 시스템 역할 */
    public String ragSystemPrompt(String ragContext) {
        return systemRole() + "\n\n아래 참고 문서를 기반으로 답변하세요:\n\n" + ragContext;
    }

    // ── CRUD 소스 생성 공통 제약 ────────────────────────────────────────

    /** [소스 생성 제약] 블록 — CrudPromptBuilderService, MasterDetailService 공유 */
    public String crudConstraints() {
        return """
                [소스 생성 제약 — 필수 준수]
                  - 각 레이어는 반드시 getCodeTemplate(layer)가 반환한 템플릿을 기반으로 생성하세요.
                  - 위 [플레이스홀더 치환 규칙]의 값을 정확히 대입하고 임의 해석하지 마세요.
                  - 템플릿 구조·어노테이션·상속·import를 변경하지 마세요.
                  - 플레이스홀더 외 메서드·주석·필드 추가·삭제 금지.
                  - {{DOMAIN_KR}} 등 한국어 값은 위 규칙에 명시된 값만 사용하세요.
                """;
    }

    /** [생성 완료 후 필수 처리] 블록 */
    public String postGeneration(String outputPath, String tableName,
                                  String domain, String packageName, String domainLc) {
        return "[생성 완료 후 필수 처리]\n" +
               "  1. validateGeneratedCodeDirectory(\"" + outputPath + "\")\n" +
               "  2. saveGenerationHistory(\"" + tableName + "\", \"" + domain + "\", \"" +
                    packageName + "\", \"" + outputPath + "\", \"10개 파일\")\n" +
               "  3. checkProjectHealth(projectRootPath, \"" + domainLc + "\")\n";
    }
}
```

---

## 4. 변경 파일 목록

### 신규 생성

| 파일 | 역할 |
|---|---|
| `service/EgovPromptBuilder.java` | 공통 역할·제약 문자열 관리 |

### 변경

| 파일 | 변경 내용 |
|---|---|
| `chat/service/impl/EgovSessionAwareChatServiceImpl.java` | 인라인 역할 문자열 → `promptBuilder.ragSystemPrompt()` 호출로 교체 |
| `service/CrudPromptBuilderService.java` | `[소스 생성 제약]`, `[생성 완료 후 필수 처리]` 블록 → `promptBuilder` 메서드 호출로 교체 |
| `service/MasterDetailService.java` | 동일 중복 블록 → `promptBuilder` 메서드 호출로 교체 |

### 삭제 또는 정리

| 파일 | 처리 방법 |
|---|---|
| `chat/util/EgovPromptEngineeringUtil.java` | 미사용 메서드 제거 또는 클래스 삭제 (영어 역할 문자열 전부 미사용) |

---

## 5. 단계별 구현 순서

### Step 1 — EgovPromptBuilder 신규 작성

`src/main/java/com/krdevops/springai/service/EgovPromptBuilder.java` 생성.  
위 설계 코드 그대로 작성.

### Step 2 — EgovSessionAwareChatServiceImpl 교체

**Before:**
```java
promptSpec = promptSpec.system(
    "당신은 eGovFrame 전문 AI 어시스턴트입니다.\n\n" +
    "아래 참고 문서를 기반으로 답변하세요:\n\n" + ragContext);
```

**After:**
```java
promptSpec = promptSpec.system(promptBuilder.ragSystemPrompt(ragContext));
```

RAG 없는 경로도 동일하게 `promptBuilder.systemRole()` 적용.

### Step 3 — CrudPromptBuilderService 교체

**Before (`line 94~99, 123~128`):**
```java
sb.append("[소스 생성 제약 — 필수 준수]\n");
sb.append("  - 각 레이어는 반드시...\n");
// ...
sb.append("[생성 완료 후 필수 처리]\n");
sb.append("  1. validateGeneratedCodeDirectory(...)");
// ...
```

**After:**
```java
sb.append(promptBuilder.crudConstraints());
// ...
sb.append(promptBuilder.postGeneration(outputPath, tableName, domain, packageName, domainLc));
```

### Step 4 — MasterDetailService 교체

Step 3와 동일 패턴 적용.

### Step 5 — EgovPromptEngineeringUtil 정리

영어 역할 문자열 메서드를 삭제하거나 클래스 전체를 제거.  
실제로 사용하는 메서드가 없으므로 클래스 삭제가 가장 깔끔합니다.

---

## 6. 구현 후 기대 효과

| 항목 | 변경 전 | 변경 후 |
|---|---|---|
| 시스템 역할 정의 위치 | 4곳 분산 | 1곳 (`EgovPromptBuilder`) |
| CRUD 제약 블록 | 2곳 중복 | 1곳 공유 |
| 역할 언어 일관성 | 한국어/영어 혼재 | 한국어 통일 |
| 역할 수정 시 영향 범위 | 4개 파일 수정 | 1개 파일 수정 |
| 미사용 코드 | `EgovPromptEngineeringUtil` 7개 메서드 | 제거 |

---

## 7. 주의사항

### 기능 추가 없음

이 작업은 **순수 리팩토링**입니다. LLM에게 전달되는 실제 프롬프트 내용은 동일합니다.  
단, 시스템 역할 문자열을 한국어로 통일하면 RAG 없는 경로의 응답 품질이 소폭 개선될 수 있습니다 (현재는 역할 없이 응답).

### 구현 우선순위

현재 코드가 동작 중이므로 긴급 구현은 필요 없습니다.  
다른 기능(ERD 생성, SQL 생성) 구현 전후 어느 시점에 해도 충돌이 없습니다.

### `EgovCompressionQueryTransformer` 역할 문자열

쿼리 재작성 전용 역할(`"You are a query rewriting assistant."`)은 기능적으로 다른 역할이므로  
`EgovPromptBuilder`에 통합하지 않고 그대로 유지합니다.

---

## 8. 관련 파일 경로

```
src/main/java/com/krdevops/springai/
├── service/
│   ├── EgovPromptBuilder.java          ← 신규
│   ├── CrudPromptBuilderService.java   ← 변경
│   └── MasterDetailService.java        ← 변경
└── chat/
    ├── service/impl/
    │   └── EgovSessionAwareChatServiceImpl.java  ← 변경
    └── util/
        └── EgovPromptEngineeringUtil.java        ← 삭제 예정
```
