# SecurityTemplateTool 구현완료 검토

> 작성일: 2026-06-09  
> 대상: `springai-mcp` / `SecurityTemplateTool` 구현 완료분  
> 검토 관점: 코드 리뷰 — 버그, 회귀 위험, 누락 테스트 우선

---

## 1. 검토 요약

`SecurityTemplateTool`은 기존 God Class 구조를 줄이고 `SecurityFilePlanFactory`,
`SecurityTemplateRenderer`, `SecurityResultBuilder`로 책임을 분리하는 방향은 적절하다.
또한 `outputPath` 기반 직접 저장, 조합 키워드, 템플릿 외부화까지 구현되어
계획서의 큰 흐름과 맞는다.

다만 현재 상태를 “구현완료”로 보기에는 생성 산출물 정합성 문제가 크다.
특히 Java 파일의 저장 경로와 템플릿 내부 `package` 선언이 불일치하고,
4.3 조합 생성에서 XML Security와 Java Config가 동시에 포함되며,
4.3 대상에 Jakarta 필터 소스가 생성되는 문제가 있다.

---

## 2. 주요 Findings

### [P1] 생성 Java 파일의 저장 경로와 `package`/import/class명이 서로 맞지 않음

#### 근거

`SecurityFilePlanFactory`는 다음 경로로 파일을 저장한다.

```java
case "javaconfig" -> FilePlan.of(
    "src/main/java/" + pkg + "/config/EgovProjectSecurityConfig.java",
    FilePlan.FileKind.SOURCE,
    () -> renderer.render(type, spec));

case "userdetailsservice" -> FilePlan.of(
    "src/main/java/" + pkg + "/sec/service/impl/EgovUserDetailsServiceImpl.java",
    FilePlan.FileKind.SOURCE,
    () -> renderer.render(type, spec));

case "successhandler" -> FilePlan.of(
    "src/main/java/" + pkg + "/sec/handler/EgovAuthenticationSuccessHandler.java",
    FilePlan.FileKind.SOURCE,
    () -> renderer.render(type, spec));
```

하지만 템플릿 내부 package/import/class 선언은 저장 경로와 다르다.

```java
// egov43/java-config.java.tpl
package ${packageName}.config;

import ${packageName}.security.EgovAuthenticationSuccessHandler;
import ${packageName}.security.EgovAuthenticationFailureHandler;
import ${packageName}.security.EgovAccessDeniedHandler;
import ${packageName}.service.EgovUserDetailsServiceImpl;

public class EgovSecurityConfig extends WebSecurityConfigurerAdapter {
```

```java
// egov43/user-details-service.java.tpl
package ${packageName}.service;
```

```java
// egov43/success-handler.java.tpl
package ${packageName}.security;
```

#### 영향

- 생성된 Java 파일이 대상 프로젝트에서 바로 컴파일되지 않을 수 있다.
- 파일 경로와 package 선언이 달라 IDE/빌드 도구가 소스를 정상 인식하지 못한다.
- `javaConfig`는 파일명이 `EgovProjectSecurityConfig.java`인데 클래스명은 `EgovSecurityConfig`라 Java 컴파일 오류가 발생한다.
- `javaConfig`의 import 경로가 Factory가 생성한 실제 경로와 맞지 않는다.

#### 권장 수정

둘 중 하나로 기준을 고정해야 한다.

| 선택 | 설명 |
|---|---|
| 경로를 템플릿 package에 맞춤 | `userDetailsService`는 `${pkg}/service`, 핸들러는 `${pkg}/security`에 저장 |
| 템플릿 package를 Factory 경로에 맞춤 | `userDetailsService`는 `${packageName}.sec.service.impl`, 핸들러는 `${packageName}.sec.handler`로 변경 |

추가로 `javaConfig`는 파일명과 클래스명을 일치시켜야 한다.

```text
EgovProjectSecurityConfig.java ↔ public class EgovProjectSecurityConfig
또는
EgovSecurityConfig.java ↔ public class EgovSecurityConfig
```

---

### [P1] `setup-war-43` 조합이 XML Security와 Java Config를 동시에 생성함

#### 근거

Tool 설명에는 동시 선언 불가라고 안내되어 있다.

```text
주의: contextSecurity(XML의 <http>)와 javaConfig(SecurityFilterChain Bean)는
      Spring Security 설정으로 동시 선언 불가 (springSecurityFilterChain Bean 충돌).
```

하지만 실제 조합은 둘을 함께 포함한다.

```java
private static List<String> war43Types() {
    return List.of(
            "webxmlfilter",
            "contextsecurity",
            "javaconfig",
            "userdetailsservice",
            "rolehierarchy",
            "loginpage",
            "userdetailshelperxml");
}
```

#### 영향

- `context-security.xml`의 `<http>` 설정과 `@EnableWebSecurity` Java Config가 동시에 로드될 수 있다.
- `springSecurityFilterChain` 또는 Security 설정 충돌 가능성이 크다.
- 사용자는 “기본 셋업” 키워드를 믿고 적용했는데 런타임 충돌을 만날 수 있다.

#### 권장 수정

4.3 조합 키워드를 XML 방식과 Java Config 방식으로 분리한다.

```text
setup-war-43-xml
  webXmlFilter + contextSecurity + roleHierarchy + loginPage + userDetailsHelperXml

setup-war-43-java
  javaConfig + userDetailsService + roleHierarchy + successHandler +
  failureHandler + accessDeniedHandler + loginPage
```

또는 `setup-war-43`은 공공 SI 기본값인 XML 방식만 생성하고,
Java Config는 별도 키워드로 분리한다.

---

### [P1] 4.3 전체 셋업에 Jakarta 필터 소스가 포함됨

#### 근거

`setup-all-war-43`은 `filterTypes()`를 포함한다.

```java
private static List<String> filterTypes() {
    return List.of(
            "loginfilter",
            "logoutfilter",
            "loginpolicyfilter",
            "sessionmapping");
}
```

하지만 공통 필터 템플릿은 Jakarta를 하드코딩한다.

```java
// common/login-filter.java.tpl
package ${packageName}.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.GenericFilterBean;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
```

#### 영향

- eGovFrame 4.3 / Servlet 4 / Java 11 계열에서는 `javax.servlet`가 필요하다.
- 4.3 프로젝트에 생성된 필터 소스가 컴파일되지 않을 수 있다.
- `egovVersion=4.3` 조합 키워드의 신뢰도가 떨어진다.

#### 권장 수정

필터 템플릿을 버전별로 분리하거나 `${javaxOrJakarta}` 변수를 사용한다.

```java
import ${javaxOrJakarta}.servlet.FilterChain;
import ${javaxOrJakarta}.servlet.ServletException;
import ${javaxOrJakarta}.servlet.http.HttpServletRequest;
```

단, `GenericFilterBean`은 `org.springframework.web.filter.GenericFilterBean` 여부도 함께 확인해야 한다.
현재 `jakarta.servlet.GenericFilterBean` import는 일반적인 Servlet API 클래스가 아니므로 별도 검토가 필요하다.

---

### [P2] 5.0 `userDetailsService` 저장 호출이 안내문을 `.java` 파일로 저장함

#### 근거

Renderer는 5.0에서 안내 템플릿을 선택한다.

```java
case "userdetailsservice" -> is43 ? "egov43/user-details-service.java.tpl"
                                  : "egov50/user-details-service-notice.tpl";
```

하지만 Factory는 항상 Java 파일 경로로 저장한다.

```java
case "userdetailsservice" -> FilePlan.of(
    "src/main/java/" + pkg + "/sec/service/impl/EgovUserDetailsServiceImpl.java",
    FilePlan.FileKind.SOURCE,
    () -> renderer.render(type, spec));
```

5.0 안내 템플릿은 Java 코드가 아니다.

```text
⚠️ eGovFrame 5.0에서는 EgovUserDetailsServiceImpl이 필요하지 않습니다.

[5.0 대체 방식]
RTE EgovSecurityConfiguration이 EgovJdbcUserDetailsManager를 자동 구성합니다.
```

#### 영향

- `getSecurityTemplate("userDetailsService", ..., "5.0", outputPath, ...)` 호출 시 일반 안내문이 `.java` 파일로 저장된다.
- 대상 프로젝트 컴파일 오류를 만든다.

#### 권장 수정

5.0에서는 다음 중 하나로 처리한다.

| 방식 | 설명 |
|---|---|
| 저장 금지 | 5.0 `userDetailsService` 저장 요청은 안내 메시지만 반환하고 FilePlan 생성 안 함 |
| `.md`로 저장 | `docs/security/user-details-service-5.0-notice.md`로 저장 |
| 조합에서 제외 | 이미 `setup-war-50`에서는 제외되어 있으므로 단일 저장도 정책 일관화 |

---

## 3. 테스트 결과

관련 테스트를 실행했다.

```bash
./gradlew test \
  --tests 'com.krdevops.springai.service.SecurityTemplateServiceTest' \
  --tests 'com.krdevops.springai.service.security.SecurityFilePlanFactoryTest'
```

결과:

```text
BUILD SUCCESSFUL
```

컴파일 경고:

```text
GenericJackson2JsonRedisSerializer deprecated/removal warning 2건
```

해당 경고는 SecurityTemplateTool 구현과 직접 관련은 없다.

---

## 4. 테스트 갭

현재 테스트는 통과하지만, 실제 생성 산출물 문제를 잡지 못한다.

### 4-1. Service 테스트 한계

`SecurityTemplateServiceTest`는 `SecurityFilePlanFactory`, `FilePlanExecutor`,
`SecurityResultBuilder`를 mock으로 대체한다.

따라서 다음을 검증하지 못한다.

| 항목 | 현재 검증 여부 |
|---|---:|
| 실제 `.tpl` 파일 로딩 | X |
| 변수 치환 결과 | X |
| 파일 저장 경로와 package 선언 일치 | X |
| 조합 키워드 실제 파일 생성 | X |
| 4.3/5.0 javax/jakarta 일관성 | X |

### 4-2. Factory 테스트 한계

`SecurityFilePlanFactoryTest`도 `SecurityTemplateRenderer`를 mock 처리한다.

따라서 `FilePlan.relativePath()`만 일부 검증하고,
실제 템플릿 내용과 경로의 정합성은 확인하지 않는다.

---

## 5. 추가해야 할 테스트

### 5-1. 템플릿 렌더링 테스트

실제 `SecurityTemplateRenderer`를 사용해 모든 단일 키를 렌더링한다.

검증 항목:

- 지원 securityType 16종 렌더링 성공
- 4.3 `contextSecurity`에 `egov-security-4.3.0.xsd` 포함
- 5.0 `contextSecurity`에 `EgovSecurityConfig` 포함
- 4.3 Java 템플릿에 `jakarta.servlet` 미포함
- 5.0 Java 템플릿에 `javax.servlet` 미포함

### 5-2. FilePlan 경로/package 정합성 테스트

Java 파일 FilePlan에 대해 렌더링 결과의 `package` 선언과 저장 경로를 비교한다.

예시:

```text
src/main/java/egovframework/let/sample/config/EgovSecurityConfig.java
↔ package egovframework.let.sample.config;
```

파일명과 public class명도 함께 검증한다.

```text
EgovSecurityConfig.java
↔ public class EgovSecurityConfig
```

### 5-3. 조합 생성 통합 테스트

임시 디렉터리에 실제 저장한다.

```java
service.getSecurityTemplate(
    "setup-war-43",
    "egovframework.let.sample",
    "4.3",
    tempDir.toString(),
    "war"
);
```

검증:

- 실제 파일 수
- 필수 파일 존재
- 생성 Java 파일 package/path 일치
- 4.3 결과물에 Jakarta namespace 혼입 없음
- 5.0 결과물에 javax namespace 혼입 없음

---

## 6. 우선 수정 순서

1. `javaConfig` 파일명과 클래스명 불일치 수정
2. Factory 저장 경로와 템플릿 package 선언 일치
3. `setup-war-43`을 XML 방식과 Java Config 방식으로 분리
4. 필터 템플릿의 Jakarta 하드코딩 제거 또는 버전별 분리
5. 5.0 `userDetailsService` 저장 정책 수정
6. 실제 렌더링/저장 통합 테스트 추가

---

## 7. 결론

구조 리팩터링 방향은 좋다.

```text
SecurityTemplateService
  God Class → 얇은 조율자

SecurityFilePlanFactory
  securityType → FilePlan 조립

SecurityTemplateRenderer
  .tpl 외부화 + 변수 치환

FilePlanExecutor
  outputPath 직접 저장
```

하지만 현재 구현은 생성 산출물이 대상 프로젝트에서 바로 동작한다는 보장이 부족하다.
특히 경로/package/class명 불일치와 4.3/5.0 namespace 혼입은 사용자 프로젝트를 깨뜨릴 수 있는
중요 이슈다.

따라서 “구조 구현 완료”로는 볼 수 있지만,
“사용 가능한 생성기 구현 완료”로 보기 위해서는 산출물 정합성 수정과 통합 테스트 보강이 필요하다.
