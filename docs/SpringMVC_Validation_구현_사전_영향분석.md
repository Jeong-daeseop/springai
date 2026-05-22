# Spring MVC Validation 구현 사전 영향 분석

작성일: 2026-05-22
목적: Validation 구현 착수 전 변경 범위·위험 요소·구현 순서 확정

---

## 변경 대상 파일 전체 목록

| 파일 | 역할 | 변경 규모 |
|---|---|---|
| `CodeTemplateTool.java` (624L) | VO/Controller/JSP 템플릿 | 대 |
| `CrudPromptBuilderService.java` (333L) | `buildVoFields()` 어노테이션 생성 | 중 |
| `MasterDetailService.java` (339L) | 독립 `buildVoFields()` 별도 존재 | 중 |
| `ProjectInitializrService.java` (1248L) | WAR pom.xml/Gradle Hibernate Validator 추가 | 소 |
| `EgovPromptBuilder.java` | `crudConstraints()` 규칙 문구 수정 | 소 |

---

## 위험 1 — `egovVersion` 미공유 (구조적 이슈)

**가장 근본적인 문제.** `CodeTemplateTool.getCodeTemplate(layer)`는 `egovVersion`을 파라미터로 받지 않아, 4.3/5.0 분기 없이 단일 템플릿만 반환한다.

```
buildFullCrudPrompt(database, tableName, domain, packageName, outputPath)
    └─ egovVersion ← 전달 안 됨
         ↓
    getCodeTemplate("vo")  ← 어떤 버전인지 모름
         ↓
    javax.* 써야 하나 jakarta.* 써야 하나 → 판단 불가
```

### 선택지

| 방안 | 장점 | 단점 |
|---|---|---|
| A. `getCodeTemplate(layer, egovVersion)` 파라미터 추가 | 정확한 분기 가능 | Tool description, McpConfig, CodeService, buildFullCrudPrompt() 연쇄 수정 |
| **B. `{{VALIDATION_IMPORT}}` 플레이스홀더 삽입** | 기존 인터페이스 유지 | 플레이스홀더 추가로 generateSource() 값 Map에 항목 추가 필요 |

→ **방안 B 채택**: 기존 플레이스홀더 치환 구조를 그대로 활용, 파급 범위 최소화.
`buildFullCrudPrompt()`에 `egovVersion` 오버로드 추가로 부분 해소.

---

## 위험 2 — `MasterDetailService`의 독립 `buildVoFields()` 존재

`CrudPromptBuilderService`와 `MasterDetailService`가 **각각** `buildVoFields()`를 보유한다.

```
CrudPromptBuilderService.buildVoFields()  ← CHARACTER_MAXIMUM_LENGTH 조회 ✅
MasterDetailService.buildVoFields()       ← CHARACTER_MAXIMUM_LENGTH 조회 ❌ (SQL에 없음)
```

`MasterDetailService.fetchColumns()` SQL (수정 전):
```java
"SELECT c.COLUMN_NAME, c.DATA_TYPE, c.IS_NULLABLE, c.COLUMN_COMMENT, ..."
// CHARACTER_MAXIMUM_LENGTH 없음 → @Size 생성 불가
```

→ `MasterDetailService.fetchColumns()` SQL에도 `CHARACTER_MAXIMUM_LENGTH` 추가 필요.
**2곳 독립 수정 필요.**

---

## 위험 3 — `EgovPromptBuilder.crudConstraints()` 규칙 충돌

현재 AI에게 내리는 지시가 validation 추가와 **정면 충돌**한다.

```java
// EgovPromptBuilder.java — 수정 전
"  - 템플릿 구조·어노테이션·상속·import를 변경하지 마세요.\n"

// CodeTemplateTool.java — 수정 전
"2. 템플릿에 없는 메서드, 주석, import를 추가하지 마세요."
"5. import는 템플릿에 명시된 항목만 사용하세요."
```

→ 템플릿에 validation import를 추가하면 이 규칙들은 자연스럽게 준수된다.
**수정 순서**: 템플릿 수정(`CodeTemplateTool`) 먼저 → `crudConstraints()` 문구 조정 순으로 진행.

---

## 위험 4 — JSP `<form:form>` 전환 시 모델 속성명 불일치

수정 화면(`jspUpdtTemplate`) 전환 시 Controller와 JSP 간 `modelAttribute` 이름이 불일치한다.

```java
// Controller 수정 전 — 수정 화면 메서드
model.addAttribute("result", vo);              // ← 이름이 "result"
return "{{DOMAIN_LC}}/Egov{{DOMAIN}}Updt";

// JSP 수정 전 — 수정 폼
<input type="hidden" name="{{PK_FIELD}}" value="${result.{{PK_FIELD}}}"/>  // "result" 참조
```

`<form:form modelAttribute="{{DOMAIN_LC}}VO">` 로 전환하면:

```java
// Controller도 함께 수정 필요
model.addAttribute("{{DOMAIN_LC}}VO", vo);     // "result" → "{{DOMAIN_LC}}VO"
```

→ **`jspUpdtTemplate`과 `controllerTemplate`의 수정 화면 메서드를 동시에 수정해야 한다.**
한쪽만 바꾸면 런타임 오류 발생.

등록 화면(`jspRegistTemplate`)은 이미 `model.addAttribute("{{DOMAIN_LC}}VO", new {{DOMAIN}}VO())`로 일치하므로 Controller 수정 불필요.

---

## 위험 5 — `CodeTemplateTool` Tool description 규칙과 실제 동작 불일치

Tool description rule 2, 5가 validation import 추가 후에도 그대로 남으면 AI가 혼란을 겪을 수 있다.

```
"2. 템플릿에 없는 import를 추가하지 마세요."
"5. import는 템플릿에 명시된 항목만 사용하세요."
```

→ 템플릿에 validation import를 추가하면 이 규칙들은 자연스럽게 준수된다.
**별도 문구 수정 불필요.**

---

## 위험 6 — Hibernate Validator 미포함 시 어노테이션 무효

`javax/jakarta.validation-api`는 API jar만 포함하고 있어 구현체가 없으면
VO에 `@NotBlank` 등을 추가해도 **검증이 실행되지 않는다**.

| 버전 | 구현체 | EL 구현체 |
|---|---|---|
| 4.3.x | `hibernate-validator:6.2.5.Final` | `jakarta.el:3.0.4` |
| 5.0.x | `hibernate-validator:8.0.1.Final` | `jakarta.el:4.0.2` |

→ **P1 최우선**: Hibernate Validator 의존성 추가 없이 다른 모든 항목을 구현해도 동작하지 않는다.

---

## 구현 순서 (의존성 기반)

```
[1단계] ProjectInitializrService — Hibernate Validator 의존성 추가
        warPomXml() · warBuildGradle() validationDep 변수 수정
        ↓ (이것 없이 아래가 무의미)

[2단계] MasterDetailService.fetchColumns() — CHARACTER_MAXIMUM_LENGTH SQL 추가
        CrudPromptBuilderService는 이미 조회 중이므로 패스

[3단계] CodeTemplateTool
        3-a. voTemplate()         — {{VALIDATION_IMPORT}} 플레이스홀더 삽입
        3-b. controllerTemplate() — 등록/수정에 @ModelAttribute @Valid + BindingResult 추가
                                    수정뷰 model.addAttribute("result") → "{{DOMAIN_LC}}VO"
        3-c. jspRegistTemplate()  — <form:form modelAttribute> + form taglib 전환
        3-d. jspUpdtTemplate()    — <form:form modelAttribute> + <form:hidden path>
                                    ${result.*} 참조 제거

[4단계] CrudPromptBuilderService.buildVoFields()
        — IS_NULLABLE → @NotBlank/@NotNull, CHARACTER_MAXIMUM_LENGTH → @Size 생성
        — {{VALIDATION_IMPORT}} 플레이스홀더 값(javax.* vs jakarta.*) 주입
        — buildFullCrudPrompt(egovVersion) 오버로드 추가

[5단계] MasterDetailService.buildVoFields()
        — CrudPromptBuilderService와 동일 로직 적용 (독립 메서드이므로 별도 수정)

[6단계] EgovPromptBuilder.crudConstraints()
        — "import를 변경하지 마세요" → validation 어노테이션 import 예외 허용으로 완화
```

---

## 변경 영향 범위

```
CodeTemplateTool 변경
  ├─ CodeService.generateSource()         ← 자동 반영 (템플릿 소비자)
  ├─ CrudPromptBuilderService (단일 CRUD 경로)
  └─ MasterDetailService (마스터-디테일 경로) ← 별도 독립 수정 필요

ProjectInitializrService 변경
  ├─ warPomXml()      ← Hibernate Validator 추가
  └─ warBuildGradle() ← 동일
  (Boot는 spring-boot-starter-validation이 이미 포함)
```

---

## 비파괴성 검토

| 항목 | 기존 생성 코드 영향 | 이유 |
|---|---|---|
| 템플릿 변경 | **없음** | 이미 저장된 파일은 불변, 재생성 시 새 템플릿 적용 |
| `buildVoFields()` 변경 | **없음** | 새 CRUD 생성 요청에만 적용 |
| Hibernate Validator 추가 | **없음** | 신규 `initializeProject()` 호출에만 적용 |
| JSP `<form:form>` 전환 | **없음** | 신규 생성분에만 적용 |

**이미 생성된 코드에 소급 적용 없음. 현재 운영 중인 시스템에 영향 없음.**

---

## 구현 결과 (2026-05-22 완료)

| 단계 | 파일 | 상태 |
|---|---|---|
| 1단계 | `ProjectInitializrService.java` — `warPomXml()` / `warBuildGradle()` | ✅ 완료 |
| 2단계 | `MasterDetailService.java` — `fetchColumns()` SQL | ✅ 완료 |
| 3단계 | `CodeTemplateTool.java` — 4개 템플릿 | ✅ 완료 |
| 4단계 | `CrudPromptBuilderService.java` — `buildVoFields()` / `buildFullCrudPrompt()` | ✅ 완료 |
| 5단계 | `MasterDetailService.java` — `buildVoFields()` | ✅ 완료 |
| 6단계 | `EgovPromptBuilder.java` — `crudConstraints()` | ✅ 완료 |
