# SecurityTemplateTool 5차 구현 영향평가

작성일: 2026-05-24
목적: eGovFrame 4.3 / 5.0 XSD 스키마 차이 기반 contextSecurity 누락·오류 항목 구현 전 영향 확정

---

## 발견된 버그 목록

| # | 심각도 | 항목 | 영향 |
|---|---|---|---|
| A | 🔴 | `contextSecurity43()` beans XSD `spring-beans.xsd` → `spring-beans-4.0.xsd` 오류 | XML 파서 스키마 불일치 경고 / 일부 환경 기동 실패 |
| B | 🔴 | `contextSecurity43()` egov-security 네임스페이스 선언 전체 누락 | eGovFrame RTE namespace handler 초기화 실패 가능 |
| C | 🔴 | `contextSecurity43()` `<egov-security:config>` 요소 누락 | eGovFrame RTE 보안 설정 미적용 |
| D | 🔴 | `contextSecurity50()` 독립 메서드 없이 `contextSecurity43()` 그대로 반환 | 5.0에서 4.3 XSD 생성 — 버전 불일치 |
| E | 🟡 | `SecurityTemplateTool.java` @Tool description XSD 버전 안내 없음 | Claude가 두 버전 XML 차이를 인지 못함 |

---

## eGovFrame 4.3 vs 5.0 XSD 정의

| 항목 | eGovFrame 4.3 (Spring 5.3 기반) | eGovFrame 5.0 (Spring 6 기반) |
|---|---|---|
| beans namespace XSD | `spring-beans-4.0.xsd` | `spring-beans.xsd` |
| security namespace XSD | `spring-security.xsd` | `spring-security.xsd` |
| egov-security XSD | `egov-security-4.3.0.xsd` | `egov-security-5.0.0.xsd` |
| egov-security namespace URI | `http://www.egovframe.go.kr/schema/egov-security` | 동일 |
| Java Servlet API | `javax.*` | `jakarta.*` |
| Spring Security Config API | `antMatchers` / `authorizeRequests` / `.and()` | `requestMatchers` / `authorizeHttpRequests` / Lambda DSL |

---

## [🔴 버그 A] `contextSecurity43()` — beans XSD 버전 오류

### 현황

```xml
<!-- 현재 생성 (103~104행) -->
http://www.springframework.org/schema/beans
http://www.springframework.org/schema/beans/spring-beans.xsd
```

`spring-beans.xsd` = Spring 6.x 기본 XSD.
eGovFrame 4.3은 Spring 5.3 기반 → `spring-beans-4.0.xsd` 사용.

### 영향

- IDE(STS/Eclipse)에서 XML 유효성 검사 시 `spring-beans.xsd`와 `spring-beans-4.0.xsd`는 스키마 버전 차이로 경고 발생.
- Strict 스키마 검증 환경에서는 빈 파싱 단계 오류 가능.
- 실제 Spring 5.3 런타임은 classpath 내 버전 XSD를 우선 사용하므로 **런타임 영향은 낮음**.
  단, 교육/공공 SI 환경의 IDE 정적 검사에서 지속적 경고 유발.

### 수정 방향

```xml
<!-- 변경 전 -->
http://www.springframework.org/schema/beans/spring-beans.xsd

<!-- 변경 후 -->
http://www.springframework.org/schema/beans/spring-beans-4.0.xsd
```

### 변경 범위

| 위치 | 변경 내용 |
|---|---|
| `contextSecurity43()` 104행 | `spring-beans.xsd` → `spring-beans-4.0.xsd` |

---

## [🔴 버그 B] `contextSecurity43()` — egov-security 네임스페이스 선언 전체 누락

### 현황

```xml
<!-- 현재 — egov-security 네임스페이스 없음 -->
<beans:beans xmlns="http://www.springframework.org/schema/security"
    xmlns:beans="http://www.springframework.org/schema/beans"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="
        http://www.springframework.org/schema/beans
        http://www.springframework.org/schema/beans/spring-beans-4.0.xsd
        http://www.springframework.org/schema/security
        http://www.springframework.org/schema/security/spring-security.xsd">
```

### 영향

`<egov-security:config>` 요소 사용 시 namespace handler 없음 → Spring 컨테이너 파싱 단계에서:
```
org.xml.sax.SAXParseException: The prefix "egov-security" for element
"egov-security:config" is not bound.
```
→ **ApplicationContext 로딩 실패 (서버 기동 불가)**.

### 수정 방향

```xml
<!-- 추가할 네임스페이스 선언 (4.3) -->
<beans:beans xmlns="http://www.springframework.org/schema/security"
    xmlns:beans="http://www.springframework.org/schema/beans"
    xmlns:egov-security="http://www.egovframe.go.kr/schema/egov-security"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="
        http://www.springframework.org/schema/beans
        http://www.springframework.org/schema/beans/spring-beans-4.0.xsd
        http://www.springframework.org/schema/security
        http://www.springframework.org/schema/security/spring-security.xsd
        http://www.egovframe.go.kr/schema/egov-security
        http://www.egovframe.go.kr/schema/egov-security/egov-security-4.3.0.xsd">
```

### 변경 범위

| 위치 | 변경 내용 |
|---|---|
| `contextSecurity43()` 99~106행 | egov-security namespace + XSD 2줄 추가 |

---

## [🔴 버그 C] `contextSecurity43()` — `<egov-security:config>` 요소 누락

### 현황

현재 context-security.xml 생성 결과에 `<egov-security:config>` 요소가 없음.

### 실제 eGovFrame 4.3 context-security.xml 구조

```xml
<!-- eGovFrame RTE 보안 핵심 설정 -->
<egov-security:config
    loginUrl="/uat/uia/egovLoginUsr.do"
    logoutUrl="/uat/uia/actionLogout.do"
    loginFailUrl="/uat/uia/egovLoginUsr.do?login_error=1"
    accessDeniedUrl="/cmm/error/accessDenied.do"
    dataSource="egov_dataSource"
    jdbcMapClass="egovframework.let.uat.uia.service.impl.EgovSessionMapping"
    requestMatcherType="regex"/>
```

### 영향

`<egov-security:config>` 없이도 Spring Security XML 설정은 로딩됨.
단, eGovFrame RTE가 이 요소를 통해 내부 초기화(`EgovReloadableFilterInvocationSecurityMetadataSource` 등록 등)를 수행하는 경우 **기능 일부 미작동 가능**.

`jdbcMapClass` 미설정 → EgovSessionMapping 세션 매핑 클래스 미적용 → 세션 처리 이슈.

### 수정 방향

`contextSecurity43()` XML 내 `<http>` 블록 앞에 추가:

```xml
<!-- eGovFrame RTE 보안 초기화 설정 -->
<egov-security:config
    loginUrl="/uat/uia/egovLoginUsr.do"
    logoutUrl="/uat/uia/actionLogout.do"
    loginFailUrl="/uat/uia/egovLoginUsr.do?login_error=1"
    accessDeniedUrl="/cmm/error/accessDenied.do"
    dataSource="dataSource"
    jdbcMapClass="egovframework.let.uat.uia.service.impl.EgovSessionMapping"
    requestMatcherType="regex"/>
<!-- ⚠️ jdbcMapClass: LET 계열 → egovframework.let.uat.uia.service.impl.EgovSessionMapping
                      COM 계열 → egovframework.com.sec.security.common.EgovSessionMapping -->
```

### 변경 범위

| 위치 | 변경 내용 |
|---|---|
| `contextSecurity43()` `<http>` 블록 앞 | `<egov-security:config>` 요소 추가 |

---

## [🔴 버그 D] `contextSecurity50()` — `contextSecurity43()` 위임으로 XSD 버전 불일치

### 현황

```java
// 현재 (262~266행)
private String contextSecurity50() {
    // eGovFrame 5.0 — context-security.xml은 4.3과 구조 동일
    // eGovFrame 런타임 클래스(egovframework.rte.fdl.security.*) 경로도 동일
    // Spring Security XML 네임스페이스가 버전 간 하위 호환 유지
    return contextSecurity43();     ← 4.3 XSD 그대로 반환
}
```

5.0 호출 시 생성되는 XML:
- `spring-beans-4.0.xsd` (버그 A 수정 후) → 5.0은 `spring-beans.xsd` 이어야 함
- `egov-security-4.3.0.xsd` (버그 B 수정 후) → 5.0은 `egov-security-5.0.0.xsd` 이어야 함

### 영향

egovVersion=5.0으로 생성 시:
- Spring 6 프로젝트에 `spring-beans-4.0.xsd` / `egov-security-4.3.0.xsd` 삽입
- IDE 스키마 검증 오류 + eGovFrame 5.0 RTE가 4.3.0 XSD를 읽다 파싱 실패 가능

### 수정 방향

`contextSecurity50()`을 독립 메서드로 분리:

```java
private String contextSecurity50() {
    return """
            <?xml version="1.0" encoding="UTF-8"?>
            <beans:beans xmlns="http://www.springframework.org/schema/security"
                xmlns:beans="http://www.springframework.org/schema/beans"
                xmlns:egov-security="http://www.egovframe.go.kr/schema/egov-security"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="
                    http://www.springframework.org/schema/beans
                    http://www.springframework.org/schema/beans/spring-beans.xsd
                    http://www.springframework.org/schema/security
                    http://www.springframework.org/schema/security/spring-security.xsd
                    http://www.egovframe.go.kr/schema/egov-security
                    http://www.egovframe.go.kr/schema/egov-security/egov-security-5.0.0.xsd">

                <!-- 내용은 contextSecurity43()과 동일 — XSD 선언만 다름 -->
                ...
            """;
}
```

XML 본문(http 블록, Bean 선언 등)은 4.3과 동일하게 유지.
XSD 선언 3줄만 변경:
- `spring-beans-4.0.xsd` → `spring-beans.xsd`
- `egov-security-4.3.0.xsd` → `egov-security-5.0.0.xsd`

### 변경 범위

| 위치 | 변경 내용 |
|---|---|
| `contextSecurity50()` 전체 | `contextSecurity43()` 위임 제거 → 독립 XML 본문 작성 |

---

## [🟡 이슈 E] `SecurityTemplateTool.java` — @Tool description XSD 안내 없음

### 현황

description에 4.3 / 5.0 분기 설명이 Java Config API 차이만 언급:
```
[레거시 XML 방식 — eGovFrame 4.3 / 5.0 공통]
  contextSecurity → context-security.xml (Spring Security XML 네임스페이스 전체 설정)
```

"4.3 / 5.0 공통"이라고 표기되어 있어 Claude가 두 버전 XML이 실제로 다른 XSD를 사용함을 모름.

### 수정 방향

```
[레거시 XML 방식]
  contextSecurity → context-security.xml
                    egovVersion=4.3: spring-beans-4.0.xsd + egov-security-4.3.0.xsd
                    egovVersion=5.0: spring-beans.xsd     + egov-security-5.0.0.xsd
                    XSD 선언이 버전에 따라 다르므로 egovVersion 명시 필요
```

### 변경 범위

| 위치 | 변경 내용 |
|---|---|
| `SecurityTemplateTool.java` description 21행 | contextSecurity 설명에 XSD 버전 분기 안내 추가 |

---

## 비파괴성 검토

| 항목 | 기존 동작 영향 | 이유 |
|---|---|---|
| `contextSecurity43()` XSD 수정 | **없음** | 런타임은 classpath XSD 우선 — 생성 파일 텍스트만 변경 |
| `contextSecurity43()` egov-security 네임스페이스 추가 | **없음** | 추가 선언 = 기존 요소 무영향 |
| `contextSecurity43()` `<egov-security:config>` 추가 | **주의** | eGovFrame RTE 의존성(egov-security namespace handler)이 없으면 파싱 오류. 단, 이미 eGovFrame 4.3 프로젝트에는 RTE 존재 → **정상 환경에서 영향 없음** |
| `contextSecurity50()` 독립 분리 | **없음** | 4.3 호출 경로 무변경. 5.0 생성 내용만 개선 |
| `@Tool` description 수정 | **없음** | Claude 안내 개선, 생성 로직 미변경 |

---

## 구현 순서 (의존성 기반)

```
[1단계] contextSecurity43() XSD 선언 수정 (버그 A + B 동시)
        spring-beans-4.0.xsd 교체 + egov-security 네임스페이스 추가
        ↓

[2단계] contextSecurity43() <egov-security:config> 요소 추가 (버그 C)
        1단계 네임스페이스 추가 완료 후 진행
        ↓

[3단계] contextSecurity50() 독립 메서드 분리 (버그 D)
        1단계 내용 기반으로 5.0 XSD로 교체
        (1단계와 동시 진행 가능)
        ↓

[4단계] SecurityTemplateTool.java @Tool description 수정 (이슈 E)
        contextSecurity XSD 버전 분기 안내
```

---

## 변경 파일 및 범위 요약

| 파일 | 변경 위치 | 변경 규모 |
|---|---|---|
| `SecurityTemplateService.java` | `contextSecurity43()` 99~106행 XSD 선언 | 소 — 6줄 수정/추가 |
| `SecurityTemplateService.java` | `contextSecurity43()` `<egov-security:config>` 추가 | 소 — 8줄 추가 |
| `SecurityTemplateService.java` | `contextSecurity50()` 전체 독립 분리 | 중 — 전체 XML 본문 작성 |
| `SecurityTemplateTool.java` | description 21행 contextSecurity 설명 | 극소 — 2줄 수정 |

---

## 최종 결정 사항

| 항목 | 결정 | 완료 |
|---|---|---|
| `contextSecurity43()` beans XSD 버전 수정 | ✅ **구현** (1단계) | ✅ 2026-05-24 완료 |
| `contextSecurity43()` egov-security 네임스페이스 추가 | ✅ **구현** (1단계) | ✅ 2026-05-24 완료 |
| `contextSecurity43()` `<egov-security:config>` 추가 | ✅ **구현** (2단계) | ✅ 2026-05-24 완료 |
| `contextSecurity50()` 독립 메서드 분리 | ✅ **구현** (3단계) | ✅ 2026-05-24 완료 |
| `@Tool` description XSD 버전 안내 | ✅ **구현** (4단계) | ✅ 2026-05-24 완료 |
