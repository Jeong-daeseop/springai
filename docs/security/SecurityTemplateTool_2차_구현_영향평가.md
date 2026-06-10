# SecurityTemplateTool 2차 구현 영향평가

작성일: 2026-05-24
목적: 분석에서 발견된 버그 5건 수정 착수 전 변경 범위·위험도·구현 순서 확정

---

## 발견된 버그 목록

| # | 심각도 | 항목 | 영향 |
|---|---|---|---|
| 1 | 🔴 | `EgovSecurityMetadataSource` import 누락 | 생성 코드 컴파일 실패 |
| 2 | 🔴 | `roleHierarchy()` 인덴테이션 깨짐 | 생성 코드 포맷 오류 |
| 3 | 🟡 | `@Tool` description "Spring Security 4.x" 오기 | Claude 설명 부정확 |
| 4 | 🟡 | `loginPage()` 주석 파라미터명 오기 | 개발자 혼란 유발 |
| 5 | 🟡 | 핸들러 URL: description `/egovMain.do` vs 템플릿 `/index.jsp` 불일치 | description 신뢰도 저하 |

---

## [🔴 버그 1] `EgovSecurityMetadataSource` import 누락

### 현황

`javaConfig43()` (371행):
```java
@Autowired
private EgovSecurityMetadataSource egovSecurityMetadataSource;
```

`javaConfig50()` (519~521행):
```java
private final EgovSecurityMetadataSource securityMetadataSource;
...
public EgovSecurityConfig(..., EgovSecurityMetadataSource securityMetadataSource, ...) {
```

양쪽 모두 import 블록에 `EgovSecurityMetadataSource` 관련 import 없음.
`EgovSecurityMetadataSource`는 Spring Security 표준 타입도, eGovFrame 공개 타입도 아님 — **존재하지 않는 클래스를 참조**.

XML(`contextSecurity43`) 에서 실제 사용하는 구현 클래스:
```xml
<beans:bean id="egovSecurityMetadataSource"
    class="egovframework.rte.fdl.security.intercept.EgovReloadableFilterInvocationSecurityMetadataSource">
```

### 영향

- `javaConfig("...", "4.3")` 또는 `javaConfig("...", "5.0")` 호출 시 생성된 `EgovSecurityConfig.java` 컴파일 불가
- `EgovSecurityMetadataSource`라는 이름으로 사용자가 인터페이스를 직접 만들지 않으면 해결 불가
- `securityType = "javaconfig"` 전체 경로가 사실상 사용 불가 상태

### 수정 방향

**Option A — Spring Security 표준 인터페이스 사용 (권장)**

```java
import org.springframework.security.web.access.intercept.FilterInvocationSecurityMetadataSource;

// javaConfig43: 필드 타입 변경
@Autowired
private FilterInvocationSecurityMetadataSource egovSecurityMetadataSource;

// javaConfig50: 필드 및 생성자 타입 변경
private final FilterInvocationSecurityMetadataSource securityMetadataSource;
public EgovSecurityConfig(..., FilterInvocationSecurityMetadataSource securityMetadataSource, ...) {
```

- Spring이 `EgovReloadableFilterInvocationSecurityMetadataSource`(이 인터페이스 구현체)를 자동 주입
- 구현 클래스에 의존하지 않으므로 eGovFrame 버전 업 시 안전
- `FilterInvocationSecurityMetadataSource` 자체가 Spring Security 6.x deprecated이지만, eGovFrame RTE가 이 인터페이스에 의존하므로 불가피. 기존 `FilterSecurityInterceptor` 및 `AccessDecisionManager` 항목과 동일하게 `⚠️` 주석 추가

**Option B — eGovFrame 구현 클래스 직접 참조**

```java
import egovframework.rte.fdl.security.intercept.EgovReloadableFilterInvocationSecurityMetadataSource;

private EgovReloadableFilterInvocationSecurityMetadataSource egovSecurityMetadataSource;
```

- 구현 클래스 직접 의존 — eGovFrame 내부 패키지 변경 시 깨짐
- eGovFrame 5.0에서 해당 클래스 경로 변경 가능성 있음

**결론: Option A 채택** — 인터페이스 의존, deprecated 주석 추가.

### 변경 범위

| 위치 | 변경 내용 |
|---|---|
| `javaConfig43()` import 블록 | `FilterInvocationSecurityMetadataSource` import 추가 |
| `javaConfig43()` 필드 선언 | 타입 `EgovSecurityMetadataSource` → `FilterInvocationSecurityMetadataSource` |
| `javaConfig50()` import 블록 | `FilterInvocationSecurityMetadataSource` import 추가 |
| `javaConfig50()` 필드 선언 + 생성자 파라미터 | 타입 `EgovSecurityMetadataSource` → `FilterInvocationSecurityMetadataSource` |
| `javaConfig50()` `egovSecurityFilter()` | deprecated `⚠️` 주석에 `FilterInvocationSecurityMetadataSource` 언급 추가 |

---

## [🔴 버그 2] `roleHierarchy()` 인덴테이션 깨짐

### 현황

`buildHierarchy` text block 선언 (717~723행):
```java
String buildHierarchy = ver.startsWith("4")
    ? """
                RoleHierarchyImpl impl = new RoleHierarchyImpl();
                impl.setHierarchy(hierarchy.toString());
                return impl;"""       ← 닫는 """ 인라인 (같은 줄)
    : """
                return RoleHierarchyImpl.fromHierarchy(hierarchy.toString());""";
```

Java text block 규칙: 닫는 `"""` 가 인라인이면 **content 줄들의 최소 선행 공백**으로 strip 기준 결정.
→ 모든 줄 24칸 → 24칸 strip → `buildHierarchy` 결과는 **0칸 인덴트**.

메인 템플릿 `%s` 위치 (774행):
```
                %s    ← 소스 16칸
```
메인 템플릿 닫는 `"""` (777행)도 16칸 → 16칸 strip → `%s` 결과도 **0칸 인덴트**.

실제 생성 코드 결과 (4.3 case):
```java
        StringBuilder hierarchy = new StringBuilder();
        for (Map<String, Object> row : rows) { ... }

RoleHierarchyImpl impl = new RoleHierarchyImpl();   ← 0칸 (메서드 내부인데)
impl.setHierarchy(hierarchy.toString());             ← 0칸
return impl;                                         ← 0칸
    }
}
```

### 영향

- 생성 코드가 컴파일은 되지만 인덴테이션이 전혀 맞지 않음
- Java는 인덴트 무관하므로 런타임 동작은 정상
- 실제 프로젝트에 붙여넣으면 formatter/linter가 경고 → 개발자가 혼란
- `RoleHierarchyImpl.fromHierarchy(...)` (5.0 단일 라인)도 동일 문제

### 수정 방향

**`buildHierarchy` 각 줄에 8칸 인덴트를 직접 포함하는 문자열로 변경**

Java text block의 strip 메커니즘을 피하고, 목표 인덴트(메서드 바디 = 8칸)를 하드코딩:

```java
String buildHierarchy = ver.startsWith("4")
    ? "        RoleHierarchyImpl impl = new RoleHierarchyImpl();\n" +
      "        impl.setHierarchy(hierarchy.toString());\n" +
      "        return impl;"
    : "        return RoleHierarchyImpl.fromHierarchy(hierarchy.toString());";
```

메인 템플릿의 `%s`는 0칸으로 유지:
```
                %s    ← 16칸 소스, strip 후 0칸
```
→ `buildHierarchy`(8칸 포함) 치환 시 정확히 8칸 인덴트 생성.

또는 `String.indent()` 활용 (Java 12+, 프로젝트 Java 17):
```java
String buildHierarchy = ver.startsWith("4")
    ? "RoleHierarchyImpl impl = new RoleHierarchyImpl();\nimpl.setHierarchy(hierarchy.toString());\nreturn impl;"
          .indent(8).stripTrailing()
    : "return RoleHierarchyImpl.fromHierarchy(hierarchy.toString());".indent(8).stripTrailing();
```

**결론: 문자열 연결 방식(첫 번째) 채택** — `indent()` 반환값에 trailing newline 포함되어 별도 처리 필요, 가독성 낮음. 직접 8칸 포함이 단순.

### 변경 범위

| 위치 | 변경 내용 |
|---|---|
| `roleHierarchy()` `buildHierarchy` 선언 (717~723행) | text block → 문자열 연결로 교체, 8칸 인덴트 포함 |
| 메인 템플릿 `%s` (774행) | 변경 없음 |

---

## [🟡 버그 3] `@Tool` description "Spring Security 4.x" 오기

### 현황

`SecurityTemplateTool.java` 16행:
```java
eGovFrame 4.3(Spring Security 4.x)과 5.0(Spring Security 6.x) 모두 지원합니다.
```

eGovFrame 4.3은 Spring Framework 5.x 기반 → Spring Security **5.x**.
Spring Security 4.x는 Spring Framework 4.x 대응 버전으로 eGovFrame 4.3과 매핑 안 됨.

### 영향

- Claude가 `javaConfig43()` 생성 결과를 "Spring Security 4.x 코드"로 설명할 수 있음
- 개발자가 Spring Security 4.x 문서를 참조하면 API 불일치 발생
- `javaConfig43()` 내부 JavaDoc은 "Spring Security 4.x~5.x 표준"이라고 표현하여 괴리 존재

### 수정 방향

```java
// 변경 전
eGovFrame 4.3(Spring Security 4.x)과 5.0(Spring Security 6.x) 모두 지원합니다.

// 변경 후
eGovFrame 4.3(Spring Security 5.x)과 5.0(Spring Security 6.x) 모두 지원합니다.
```

### 변경 범위

| 위치 | 변경 내용 |
|---|---|
| `SecurityTemplateTool.java` 16행 | `"4.x"` → `"5.x"` |

---

## [🟡 버그 4] `loginPage()` 주석 파라미터명 오기

### 현황

`SecurityTemplateService.java` 835~839행:
```java
[참고] input name 매핑
  j_username → Spring Security 기본 username 파라미터
  j_password → Spring Security 기본 password 파라미터
```

Spring Security 기본 username 파라미터명: `username`
Spring Security 기본 password 파라미터명: `password`

`j_username`/`j_password`는 eGovFrame이 `.usernameParameter("j_username")`으로 명시 설정한 **eGovFrame 관례**이지 Spring Security 기본값이 아님.

### 영향

- 개발자가 주석을 읽고 `j_username`이 Spring Security 기본값이라고 오해 가능
- 다른 프레임워크 연동 시 파라미터명을 임의로 변경하려다 Spring Security 기본값과 혼동
- JSP 폼에서 `name="j_username"` 자체는 정확하므로 **런타임 동작에는 영향 없음**

### 수정 방향

```java
// 변경 전
  j_username → Spring Security 기본 username 파라미터
  j_password → Spring Security 기본 password 파라미터

// 변경 후
  j_username → eGovFrame 관례 파라미터명
               (Spring Security 기본값: username — .usernameParameter("j_username")으로 명시 설정됨)
  j_password → eGovFrame 관례 파라미터명
               (Spring Security 기본값: password — .passwordParameter("j_password")으로 명시 설정됨)
```

### 변경 범위

| 위치 | 변경 내용 |
|---|---|
| `loginPage()` 836~839행 | 주석 파라미터 설명 수정 |

---

## [🟡 버그 5] 핸들러 URL: `@Tool` description vs 실제 템플릿 불일치

### 현황

`SecurityTemplateTool.java` 39행:
```
javaConfig 내부에서 new EgovAuthenticationSuccessHandler("/egovMain.do") 참조
```

실제 `javaConfig43()` 463행 / `javaConfig50()` 622행:
```java
@Bean
public EgovAuthenticationSuccessHandler loginSuccessHandler() {
    return new EgovAuthenticationSuccessHandler("/index.jsp");  // ← /index.jsp
}
```

description은 `/egovMain.do`, 생성 코드는 `/index.jsp`.

### 영향

- Claude가 description을 기반으로 "로그인 성공 후 /egovMain.do로 이동합니다"라고 설명했는데, 실제 생성 코드는 `/index.jsp`로 설정됨
- 실제 eGovFrame 표준 메인 페이지 URL은 프로젝트마다 다르므로 어느 쪽도 "정답"은 아님
- **런타임 동작에는 영향 없음** (사용자가 직접 URL 수정해야 하는 값)

### 수정 방향

**description을 실제 템플릿에 맞게 수정 (권장)**

description이 생성 코드의 실제 기본값을 안내해야 하므로 `/index.jsp`로 통일:

```java
// 변경 전
javaConfig 내부에서 new EgovAuthenticationSuccessHandler("/egovMain.do") 참조

// 변경 후
javaConfig 내부에서 new EgovAuthenticationSuccessHandler("/index.jsp") 참조
(실제 서비스 URL로 변경 필요 — 예: /egovMain.do)
```

### 변경 범위

| 위치 | 변경 내용 |
|---|---|
| `SecurityTemplateTool.java` 39행 | `/egovMain.do` → `/index.jsp` + 변경 안내 |

---

## 구현 순서 (의존성 기반)

```
[1단계] roleHierarchy() 인덴테이션 수정 (부작용 없음 — 생성 문자열 포맷만 변경)
        buildHierarchy 선언 방식 변경 (text block → 문자열 연결, 8칸 인덴트 포함)
        ↓

[2단계] EgovSecurityMetadataSource import 수정 (범위 가장 큼)
        javaConfig43(): import 추가 + 필드 타입 변경
        javaConfig50(): import 추가 + 필드 타입 변경 + 생성자 파라미터 타입 변경
        ↓

[3단계] loginPage() 주석 수정 (한 줄 수정)
        ↓

[4단계] SecurityTemplateTool.java 수정 2건
        "4.x" → "5.x" 오기 수정
        "/egovMain.do" → "/index.jsp" + 변경 안내
```

---

## 변경 파일 및 범위

| 파일 | 변경 위치 | 변경 규모 |
|---|---|---|
| `SecurityTemplateService.java` | `roleHierarchy()` 717~723행 | 소 — 6줄 교체 |
| `SecurityTemplateService.java` | `javaConfig43()` import+필드 328~372행 | 소 — import 1줄 추가, 타입명 1곳 변경 |
| `SecurityTemplateService.java` | `javaConfig50()` import+필드+생성자 480~532행 | 소 — import 1줄 추가, 타입명 2곳 변경 |
| `SecurityTemplateService.java` | `loginPage()` 835~839행 | 소 — 주석 2줄 수정 |
| `SecurityTemplateTool.java` | `@Tool` description 16행, 39행 | 소 — 문자열 2곳 수정 |

---

## 비파괴성 검토

| 항목 | 기존 동작 영향 | 이유 |
|---|---|---|
| `roleHierarchy()` 인덴트 수정 | **없음** | 생성 코드 포맷만 변경, 컴파일/런타임 동일 |
| `javaConfig43/50()` import + 타입 변경 | **없음** | 생성 코드 변경 (기존 생성 완료 파일 불변), Spring DI 주입 동작 동일 |
| `loginPage()` 주석 수정 | **없음** | 주석만 변경 |
| `@Tool` description 수정 | **없음** | Claude 설명 정확도 개선, 실제 생성 로직 미변경 |

---

## 최종 결정 사항

| 항목 | 결정 | 완료 |
|---|---|---|
| `EgovSecurityMetadataSource` import → `FilterInvocationSecurityMetadataSource` | ✅ **구현** (2단계) | ✅ 2026-05-24 완료 |
| `roleHierarchy()` 인덴테이션 → 문자열 연결 방식으로 교체 | ✅ **구현** (1단계) | ✅ 2026-05-24 완료 |
| `@Tool` description "4.x" → "5.x" | ✅ **구현** (4단계) | ✅ 2026-05-24 완료 |
| `loginPage()` 주석 파라미터 설명 수정 | ✅ **구현** (3단계) | ✅ 2026-05-24 완료 |
| 핸들러 URL description `/egovMain.do` → `/index.jsp` | ✅ **구현** (4단계) | ✅ 2026-05-24 완료 |
