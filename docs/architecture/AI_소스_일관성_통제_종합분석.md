# 생성형 AI 소스 일관성 통제 종합 분석

작성일: 2026-05-20

---

## 1. 문제 정의

생성형 AI(LLM)는 확률적 모델이기 때문에 **동일한 입력에 대해 매번 다른 출력을 생성**할 수 있습니다.
eGovFrame MCP에서 소스 생성 시 이 비결정성이 코드 품질·구조 불일치로 이어집니다.

### 현재 소스 생성 파이프라인

```
buildFullCrudPrompt()       ← 플레이스홀더 값 Map 생성 (서버, 결정적)
        ↓
getCodeTemplate(layer)      ← 고정 템플릿 반환 (서버, 결정적)
        ↓
Claude / LLM 이 플레이스홀더 치환  ← ❌ 비결정적 구간
        ↓
saveGeneratedCode()         ← 파일 저장 (서버, 결정적)
        ↓
validateGeneratedCodeDirectory()    ← 키워드 포함 여부 검사 (서버, 결정적)
```

**비결정성이 발생하는 구간은 LLM 치환 단계 하나**입니다.
이 구간을 통제하는 것이 일관성 확보의 핵심입니다.

---

## 2. 비결정성 원인 분류

| 원인 | 현황 | 영향 |
|---|---|---|
| `temperature > 0` | `application.yaml`의 Ollama: `0.3` | 동일 입력, 다른 확률 분포로 토큰 선택 |
| 자유 해석 | `@Tool` description이 느슨함 | 임의 메서드·주석·import 추가 |
| 컨텍스트 의존 | 이전 대화가 생성에 영향 | 누적 대화 길수록 편차 증가 |
| 플레이스홀더 오치환 | `{{DOMAIN_KR}}`을 LLM이 추론 | 한국어명이 매번 다를 수 있음 |
| 모델 라우팅 | `LlmRouterService`가 CODE_GENERATION → CLAUDE 라우팅 | Claude 모델 버전에 따라 편차 |
| 임의 임포트 추가 | LLM이 "유용한" import를 추가 | 컴파일 오류 또는 의존성 불일치 |

---

## 3. 통제 영역별 분석

### 3.1 프롬프트 통제

#### 현재 상태

`CrudPromptBuilderService.buildFullCrudPrompt()`가 생성하는 프롬프트에는
플레이스홀더 값이 포함되지만 **치환 행동에 대한 제약 지시가 없습니다.**

```java
// CrudPromptBuilderService — 현재 프롬프트 조립 구조
sb.append("=== eGovFrame 5.x CRUD 전체 소스 생성 지시 ===\n\n");
// ... 플레이스홀더 값 목록 ...
// ← 행동 제약(금지 사항) 지시문 없음
```

`CodeTemplateTool.getCodeTemplate()` `@Tool` description:

```
반환된 템플릿의 {{플레이스홀더}}를 실제 값으로 치환하여 소스를 완성하세요.
```

→ "치환하라"는 지시만 있고 **"추가 금지"·"변형 금지" 제약이 없습니다.**

#### 개선 방향

**A. `@Tool` description에 네거티브 제약 추가**

```java
@Tool(description = """
        eGovFrame 5.x 표준 소스 코드 템플릿을 반환합니다.

        [사용 규칙 — 반드시 준수]
        1. 반환된 템플릿의 {{플레이스홀더}}만 실제 값으로 치환하세요.
        2. 템플릿에 없는 메서드, 주석, import를 절대 추가하지 마세요.
        3. 플레이스홀더가 없는 줄은 한 글자도 변경하지 마세요.
        4. 클래스 선언·어노테이션·패키지 구조를 그대로 유지하세요.
        5. {{VO_FIELDS}}는 buildFullCrudPrompt()가 제공한 값 그대로 사용하세요.
        """)
```

**B. `buildFullCrudPrompt()` 프롬프트 끝에 제약 블록 추가**

```
[소스 생성 제약 — 필수 준수]
- 각 레이어별 getCodeTemplate(layer) 반환 템플릿을 기반으로 생성하세요.
- 템플릿 구조·순서·어노테이션을 그대로 유지하세요.
- 아래 플레이스홀더 값을 정확히 대입하고 임의 해석하지 마세요.
- 플레이스홀더 외 코드 추가·변경·삭제 금지.
- import는 템플릿에 명시된 항목만 사용하세요.
```

**C. Few-shot 예시 삽입**

```
[올바른 치환 예시]
  입력 템플릿: public class {{DOMAIN}}VO {
  치환 결과:  public class EmployerVO {  ← {{DOMAIN}} = "Employer"
  
[잘못된 치환 예시 — 절대 금지]
  추가 메서드 삽입: public String toString() { ... }  ← 금지
  어노테이션 추가: @Builder  ← 금지
  import 추가: import java.util.Objects;  ← 금지
```

---

### 3.2 템플릿 통제

#### 현재 상태

`CodeTemplateTool`은 17개의 플레이스홀더를 정의하고 있습니다:

```
{{PACKAGE}}, {{DOMAIN}}, {{DOMAIN_LC}}, {{DOMAIN_KR}},
{{TABLE_NAME}}, {{VO_FIELDS}}, {{PK_FIELD}}, {{PK_COLUMN}},
{{PK_TYPE}}, {{MAPPER_COLUMNS}}, {{INSERT_COLUMNS}},
{{JSP_LIST_COLUMNS}}, {{JSP_DETAIL_ROWS}}, {{JSP_FORM_INPUTS}},
{{INSERT_VALUES}}, {{UPDATE_SET}}, {{URL_PREFIX}}, {{DATE}}
```

`buildFullCrudPrompt()`에서 이 값들을 **모두 서버에서 계산**합니다.
즉, 치환에 필요한 모든 정보가 이미 서버에 존재합니다.

#### 근본 문제

LLM이 치환을 수행하는 것 자체가 비결정성의 원인입니다.
**서버에서 직접 치환하면 LLM 개입을 완전히 제거**할 수 있습니다.

#### 개선 방향 — 서버 사이드 치환 Tool

```java
// CodeService 신규 메서드
public String generateSource(String layer, Map<String, String> values) {
    String template = codeTemplateTool.getCodeTemplate(layer);
    for (Map.Entry<String, String> entry : values.entrySet()) {
        template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
    }
    // 미치환 플레이스홀더 탐지
    if (template.contains("{{")) {
        String remaining = template.lines()
            .filter(l -> l.contains("{{"))
            .collect(java.util.stream.Collectors.joining("\n"));
        log.warn("미치환 플레이스홀더 감지:\n{}", remaining);
    }
    return template;
}
```

```java
// CodeSaverTool 또는 신규 Tool
@Tool(description = """
        eGovFrame 소스를 서버에서 직접 생성합니다. (LLM 치환 불필요)
        buildFullCrudPrompt()가 반환한 valuesJson을 그대로 전달하세요.
        
        [layer]: vo, controller, service, serviceImpl, mapper, mapperXml,
                 jspList, jspDetail, jspRegist, jspUpdt
        [valuesJson]: {"PACKAGE":"...","DOMAIN":"...","DOMAIN_LC":"...",...}
        """)
public String generateSource(String layer, String valuesJson) {
    // JSON → Map 파싱 후 CodeService.generateSource() 호출
}
```

**이 방식으로 전환 시 일관성 100% 보장.**

---

### 3.3 코드 규칙 통제

#### eGovFrame 5.x 필수 명명 규칙

| 레이어 | 클래스명 패턴 | 패키지 |
|---|---|---|
| Controller | `Egov{Domain}Controller` | `{package}.web` |
| Service (인터페이스) | `Egov{Domain}Service` | `{package}.service` |
| ServiceImpl | `Egov{Domain}ServiceImpl` | `{package}.service.impl` |
| Mapper | `{Domain}Mapper` | `{package}.service.impl` |
| VO | `{Domain}VO` | `{package}.service` |
| MapperXml | `{Domain}Mapper.xml` | `resources/egovframework/mapper/{domain}/` |
| JSP | `Egov{Domain}List/Detail/Regist/Updt.jsp` | `WEB-INF/jsp/egovframework/{domain}/` |

#### 필수 상속·구현 규칙

| 레이어 | 필수 조건 |
|---|---|
| Controller | `@Controller` + `@RequestMapping` + `EgovPropertyService` 주입 |
| ServiceImpl | `EgovAbstractServiceImpl` 상속 + `implements Egov{Domain}Service` |
| Mapper | `@Mapper` + `extends EgovAbstractMapper` |
| VO | `@Getter @Setter` + `pageIndex`, `searchCondition`, `searchKeyword` 필드 |
| MapperXml | `resultMap` + `selectList`/`selectTotCnt`/`insert`/`update`/`delete` ID |

#### 코드 규칙을 템플릿에 강제하는 방법

현재 `CodeTemplateTool`의 `voTemplate()`에서 `egovframework.rte`를 import합니다.

```java
import egovframework.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;
```

이것은 **구버전 groupId**입니다. 템플릿 자체를 수정해야 합니다:

```java
// 수정 전 (eGovFrame 3.x 구버전)
import egovframework.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;

// 수정 후 (eGovFrame 4.x+)
import org.egovframe.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;
```

→ **템플릿 자체가 규칙의 단일 진실 공급원(Single Source of Truth)이 되어야 합니다.**

---

### 3.4 검증 절차 통제

#### 현재 `CodeValidatorService` 한계

현재 검증은 **키워드 포함 여부(contains)** 만 확인합니다:

```java
// 현재 — 구조 위치 무관, 존재 여부만 확인
private void check(String content, String keyword, String label, ...) {
    if (content.contains(keyword)) {
        passed.add("✅ " + label);
    } else {
        failed.add("❌ " + label);
    }
}
```

이 방식의 한계:
- `@Controller`가 주석 안에 있어도 통과
- 클래스명 패턴(`Egov{Domain}Controller`) 검사 불가
- 상속 위치·구조 검사 불가
- 미치환 플레이스홀더(`{{DOMAIN}}`) 탐지 불가

#### 개선 — 정규식 기반 구조 검사

```java
// Controller 강화 검증
private void checkController(String content, List<String> passed, List<String> failed) {
    // 기존 contains 유지
    check(content, "@Controller", "@Controller 선언", passed, failed);
    
    // 추가: 클래스명 패턴
    checkPattern(content,
        "@Controller\\s.*\\s*public class Egov\\w+Controller",
        "Egov{Domain}Controller 명명 규칙",
        passed, failed);
    
    // 추가: 미치환 플레이스홀더
    checkNoPattern(content,
        "\\{\\{\\w+\\}\\}",
        "미치환 플레이스홀더 없음",
        passed, failed);
    
    // 기존 항목들...
}

// ServiceImpl 강화 검증
private void checkServiceImpl(String content, List<String> passed, List<String> failed) {
    // 추가: 상속 구조 패턴
    checkPattern(content,
        "public class \\w+ServiceImpl extends EgovAbstractServiceImpl",
        "EgovAbstractServiceImpl 상속 구조",
        passed, failed);
    
    // 추가: implements 인터페이스
    checkPattern(content,
        "implements Egov\\w+Service",
        "Egov{Domain}Service 인터페이스 구현",
        passed, failed);
}

// VO 강화 검증
private void checkVO(String content, List<String> passed, List<String> failed) {
    // 추가: 검색 필드 그룹 존재
    checkPattern(content,
        "private (int|Integer) pageIndex",
        "pageIndex int 타입 필드",
        passed, failed);
    
    checkPattern(content,
        "private String searchCondition",
        "searchCondition 필드",
        passed, failed);
}

// MapperXml 강화 검증
private void checkMapperXml(String content, List<String> passed, List<String> failed) {
    // 추가: 5개 필수 쿼리 ID 존재
    for (String id : List.of("selectList", "selectTotCnt", "insert", "update", "delete")) {
        checkPattern(content,
            "id=\"" + id + "\"",
            id + " 쿼리 ID",
            passed, failed);
    }
}

// 정규식 헬퍼
private void checkPattern(String content, String regex, String label,
                           List<String> passed, List<String> failed) {
    if (Pattern.compile(regex, Pattern.DOTALL).matcher(content).find()) {
        passed.add("✅ " + label);
    } else {
        failed.add("❌ " + label);
    }
}

private void checkNoPattern(String content, String regex, String label,
                             List<String> passed, List<String> failed) {
    if (!Pattern.compile(regex).matcher(content).find()) {
        passed.add("✅ " + label);
    } else {
        failed.add("❌ " + label + " [미치환 플레이스홀더 발견]");
    }
}
```

#### 검증 게이트 절차

```
소스 생성 (LLM 치환 또는 서버 사이드 치환)
        ↓
validateGeneratedCodeDirectory()    ← 1차: 레이어별 구조 검사
        ↓
미준수 항목 있으면 재생성 요청 또는 경고
        ↓
checkProjectHealth()                ← 2차: 도메인 전체 완성도 점검
```

---

### 3.5 LLM 설정 통제

#### 현재 설정

```yaml
# application.yaml
ollama:
  chat:
    options:
      temperature: 0.3   # ← 소스 생성에 여전히 편차 가능
```

#### 개선

```yaml
ollama:
  chat:
    options:
      temperature: 0.0   # 완전 결정적 출력
      top_p: 1.0         # greedy decoding과 동일 효과
      seed: 42           # 동일 시드 → 동일 결과 (모델이 지원하는 경우)
```

#### LLM 라우팅과 일관성

`LlmRouterService`에서 `CODE_GENERATION`은 Claude로 라우팅됩니다.
Claude는 temperature 파라미터를 API 레벨에서 직접 제어해야 합니다:

```java
// LlmRouterService — 코드 생성 시 temperature 강제
ChatOptions codeOptions = ChatOptions.builder()
    .temperature(0.0)
    .build();
```

---

### 3.6 파이프라인 통제

#### 현재 흐름의 위험 구간

```
buildFullCrudPrompt()  →  [LLM]  →  saveGeneratedCode()
                           ↑
                        ❌ 비결정적
```

#### 이상적 통제 흐름

```
buildFullCrudPrompt()
  플레이스홀더 값 Map 생성 (결정적)
        ↓
generateSource(layer, valuesJson)   ← 신규 Tool
  서버에서 String.replace() 치환 (결정적)
        ↓
saveGeneratedCode(filePath, code)   ← 기존 Tool
  파일 저장 (결정적)
        ↓
validateGeneratedCodeDirectory()    ← 기존 Tool + 정규식 강화
  구조 검사 (결정적)
        ↓
checkProjectHealth()                ← 기존 Tool
  도메인 완성도 점검 (결정적)
```

**이 파이프라인에서 LLM의 역할은 Tool 호출 순서 결정뿐입니다.**
소스 생성 자체는 완전히 결정적이 됩니다.

---

## 4. 통제 방안 비교 매트릭스

| 통제 영역 | 방안 | 일관성 보장 수준 | 구현 난이도 | 적용 시점 |
|---|---|---|---|---|
| LLM 설정 | temperature=0, seed 고정 | 60~70% | 매우 낮음 | 즉시 |
| 프롬프트 | @Tool description 제약 강화 | 70~80% | 낮음 | 즉시 |
| 프롬프트 | buildFullCrudPrompt 제약 블록 | 75~85% | 낮음 | 즉시 |
| 검증 | CodeValidatorService 정규식 강화 | 사후 탐지 | 낮음 | 단기 |
| 검증 | 미치환 플레이스홀더 탐지 | 사후 탐지 | 낮음 | 단기 |
| 템플릿 | 서버 사이드 치환 (generateSource) | **100%** | 중간 | 중기 |
| 파이프라인 | LLM 역할 → Tool 호출만 | **100%** | 중간 | 중기 |

---

## 5. 단계별 구현 로드맵

### Phase 1 — 즉시 (코드 변경 없음)

```
[ 1-1 ] application.yaml
  ollama.chat.options.temperature: 0.3 → 0.0

[ 1-2 ] CodeTemplateTool.getCodeTemplate() @Tool description
  네거티브 제약 지시문 추가
  "템플릿 외 추가·변경 절대 금지" 명시

[ 1-3 ] CrudPromptBuilderService.buildFullCrudPrompt()
  프롬프트 끝에 [소스 생성 제약] 블록 추가
```

### Phase 2 — 단기 (CodeValidatorService 강화)

```
[ 2-1 ] Pattern import 추가
  import java.util.regex.Pattern;

[ 2-2 ] checkPattern() / checkNoPattern() 헬퍼 추가

[ 2-3 ] 각 checkXxx() 메서드에 정규식 기반 구조 검사 추가
  - 클래스명 패턴 (Egov{Domain}Controller 등)
  - 상속 구조 패턴 (extends EgovAbstractServiceImpl)
  - 미치환 플레이스홀더 탐지 ({{\\w+}} 패턴)
  - MapperXml 5개 필수 쿼리 ID 검사

[ 2-4 ] CodeTemplateTool voTemplate() import 수정
  egovframework.rte → org.egovframe.rte
```

### Phase 3 — 중기 (서버 사이드 치환 Tool)

```
[ 3-1 ] CodeService.generateSource(layer, Map<String,String>) 추가

[ 3-2 ] CodeSaverTool 또는 신규 Tool에 @Tool generateSource() 추가
  - JSON 파싱 → Map 변환
  - CodeService.generateSource() 호출
  - 미치환 플레이스홀더 경고 포함

[ 3-3 ] McpConfig에 빈 등록 (기존 패턴 동일)

[ 3-4 ] buildFullCrudPrompt() 안내 텍스트 변경
  "LLM이 치환" → "generateSource() Tool 호출로 서버 치환"
```

---

## 6. Phase 2 구현 대상 파일 목록

| 파일 | 변경 내용 |
|---|---|
| `application.yaml` | `temperature: 0.3` → `temperature: 0.0` |
| `CodeTemplateTool.java` | `@Tool` description 네거티브 제약 추가, `egovframework.rte` → `org.egovframe.rte` (voTemplate 내 import) |
| `CrudPromptBuilderService.java` | 프롬프트 끝에 `[소스 생성 제약]` 블록 추가 |
| `CodeValidatorService.java` | `Pattern` import 추가, `checkPattern()` 헬퍼 추가, 각 check 메서드 정규식 강화, 미치환 플레이스홀더 탐지 추가 |

## Phase 3 구현 대상 파일 목록

| 파일 | 변경 내용 |
|---|---|
| `CodeService.java` | `generateSource(layer, Map<String,String>)` 메서드 추가 |
| `CodeSaverTool.java` | `@Tool generateSource(layer, valuesJson)` 추가 |
| `McpConfig.java` | 변경 없음 (기존 빈 자동 인식) |

---

## 7. 최종 일관성 보장 수준

```
Phase 0 (현재)  : ~50%   — LLM 자유 치환, temperature 0.3
Phase 1 완료    : ~75%   — temperature 0.0 + 프롬프트 제약
Phase 2 완료    : ~85%   — 사후 정규식 검증 + 미치환 탐지
Phase 3 완료    : 100%   — 서버 사이드 치환, LLM 미개입
```

### 핵심 원칙

> **LLM의 역할을 "소스 생성자"에서 "Tool 호출자"로 전환하는 것이 근본 해결책입니다.**
>
> 프롬프트·템플릿·LLM 설정은 보조 수단이며,
> 서버 사이드 치환(`generateSource Tool`)이 100% 일관성을 보장하는 유일한 방법입니다.
