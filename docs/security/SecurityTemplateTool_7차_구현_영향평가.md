# SecurityTemplateTool 7차 구현 영향평가

작성일: 2026-05-24
목적: eGovFrame 5.0 실제 구조(bopr) 기반
     contextSecurity50() / javaConfig50() 전면 재작성 전 영향 범위 확정

---

## 발견된 버그 목록

| # | 심각도 | 항목 | 영향 |
|---|---|---|---|
| A | 🔴 | `contextSecurity50()` egov-security 네임스페이스 + `<egov-security:config>` 사용 | 5.0에서 namespace 제거됨 → 파싱 실패 |
| B | 🔴 | `contextSecurity50()` `<http>` / `<authentication-manager>` / Bean 선언 전체 | 5.0은 EgovSecurityConfig POJO Bean 1개로 대체됨 |
| C | 🔴 | `javaConfig50()` 클래스명 `EgovSecurityConfig` | RTE `org.egovframe.rte.fdl.security.config.EgovSecurityConfig`와 충돌 |
| D | 🔴 | `javaConfig50()` SecurityFilterChain 직접 구현 | RTE `EgovSecurityConfiguration`을 전혀 활용하지 않는 구조 |
| E | 🟡 | `javaConfig50()` 5.0에서 successhandler/failurehandler/userdetailsservice 불필요 | RTE가 자동 처리 — 불필요한 파일 생성 유도 |
| F | 🟡 | `@Tool` description — 5.0 구조 안내 없음 | Claude가 5.0 패턴을 4.3처럼 안내 |

---

## [🔴 버그 A+B] `contextSecurity50()` — 전면 재작성

### 현황

현재 `contextSecurity50()`이 생성하는 XML (280~456행):
```xml
<!-- 현재 — 5.0에서 동작 불가 -->
xmlns:egov-security="http://www.egovframe.go.kr/schema/egov-security"
egov-security-5.0.0.xsd
<egov-security:config .../>          ← 5.0에서 제거된 namespace 요소
<http auto-config="false" ...>       ← EgovSecurityConfiguration이 담당
<authentication-manager ...>         ← EgovSecurityConfiguration이 담당
<beans:bean id="egovAuthenticationProvider" ...>  ← 불필요
... (Bean 11개)
```

### 5.0 올바른 구조

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- 5.0: egov-security 네임스페이스 없음 — spring-beans.xsd만 사용 -->
<beans xmlns="http://www.springframework.org/schema/beans"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="
        http://www.springframework.org/schema/beans
        http://www.springframework.org/schema/beans/spring-beans.xsd">

    <!-- EgovSecurityConfig: 순수 POJO — Security API 의존성 없음 -->
    <!-- EgovSecurityConfiguration(@Import)이 이 Bean을 읽어 SecurityFilterChain 자동 구성 -->
    <bean id="securityConfig"
        class="org.egovframe.rte.fdl.security.config.EgovSecurityConfig">
        ... (32개 property)
    </bean>

</beans>
```

### 영향

| 항목 | 영향 |
|---|---|
| `contextSecurity43()` | **없음** — 독립 메서드, 변경 없음 |
| `contextSecurity50()` | 전체 교체 (XML 루트 네임스페이스 + 내용 전부) |
| `contextSecurity()` 라우팅 | **없음** — `ver.startsWith("4")` 분기 유지 |
| 기존 생성 완료 파일 | **없음** — 신규 생성 시에만 적용 |

### 변경 범위

| 위치 | 변경 내용 | 규모 |
|---|---|---|
| `contextSecurity50()` 280~456행 전체 | XML 루트 → `<beans>` (spring-beans.xsd만) + `<bean class="EgovSecurityConfig">` 32개 property | 중 — 전면 교체 |

---

## [🔴 버그 C+D] `javaConfig50()` — 전면 재작성

### 현황

현재 `javaConfig50()`이 생성하는 Java 파일 (682~848행):
```java
// 현재 — 3가지 문제
@Configuration
@EnableWebSecurity
public class EgovSecurityConfig {           // ← RTE 클래스명 충돌
    private final EgovUserDetailsServiceImpl userDetailsService;   // ← RTE가 담당
    private final FilterInvocationSecurityMetadataSource ...;      // ← RTE가 담당

    @Bean
    public SecurityFilterChain filterChain(...) { ... }  // ← RTE EgovSecurityConfiguration이 담당
    @Bean
    public AuthenticationManager authenticationManager(...) { ... } // ← RTE가 담당
    @Bean
    public FilterSecurityInterceptor egovSecurityFilter(...) { ... } // ← RTE가 담당
}
```

### 5.0 올바른 구조

```java
package {pkg}.config;

import org.egovframe.rte.fdl.security.config.EgovSecurityConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * eGovFrame 5.0 Spring Security Java Config 진입점
 *
 * 3계층 구조:
 *   1. context-security.xml → EgovSecurityConfig Bean (설정값 POJO)
 *   2. 이 클래스            → @Import 진입점 (내용 없음)
 *   3. EgovSecurityConfiguration → RTE, SecurityFilterChain 자동 구성
 *
 * ⚠️ EgovSecurityConfiguration을 XML <bean>으로 직접 선언하면
 *    Spring Security 6.5 + Java 17 환경에서 BootstrapMethodError 발생.
 *    반드시 @Import 방식으로 로드해야 함.
 *
 * EgovSecurityConfiguration이 자동 구성하는 항목:
 *   - SecurityFilterChain (URL 접근제어 / 세션 / 헤더 / CSRF)
 *   - AuthenticationManager (DaoAuthenticationProvider + RoleHierarchyAuthoritiesMapper)
 *   - EgovJdbcUserDetailsManager (사용자/권한 SQL 조회)
 *   - EgovMultipleRoleAuthorizationManager (DB URL 권한 동적 로드)
 */
@Configuration
@Import(EgovSecurityConfiguration.class)
public class EgovProjectSecurityConfig {
    // 내용 없음 — 진입점 역할만
}
```

### formatted() 인자 변경

| 현재 | 변경 후 |
|---|---|
| `.formatted(pkg, pkg, pkg, pkg, pkg)` | `.formatted(pkg)` |
| pkg 5개 (import 4개 + package) | pkg 1개 (package만) |

### 영향

| 항목 | 영향 |
|---|---|
| `javaConfig43()` | **없음** — 독립 메서드, 변경 없음 |
| `javaConfig50()` | 전체 교체 (import 전부 제거 + 내용 30줄로 축소) |
| `getSecurityTemplate()` switch | **없음** — 라우팅 로직 변경 없음 |
| `successhandler` / `failurehandler` (5.0용) | 생성은 가능하나 불필요 — description 안내로 대응 |

### 변경 범위

| 위치 | 변경 내용 | 규모 |
|---|---|---|
| `javaConfig50()` 682~848행 전체 | import 전체 제거 + 클래스명 교체 + @Import 1줄로 축소 | 중 — 전면 교체 |
| `.formatted(pkg, pkg, pkg, pkg, pkg)` → `.formatted(pkg)` | 인자 5개 → 1개 | 극소 |

---

## [🟡 이슈 E] 5.0에서 불필요한 securityType 안내

### 현황

현재 `@Tool` description에 4.3 / 5.0 공통으로 아래 securityType을 안내:
- `userDetailsService` → EgovUserDetailsServiceImpl.java
- `successHandler` → EgovAuthenticationSuccessHandler.java
- `failureHandler` → EgovAuthenticationFailureHandler.java

5.0 RTE(`EgovSecurityConfiguration`)가 자동 처리하는 항목:
- `EgovJdbcUserDetailsManager` — userDetailsService 대체
- 로그인 성공/실패 핸들러 — EgovSecurityConfig.defaultTargetUrl / loginFailureUrl로 처리

### 영향

Claude Desktop이 5.0 프로젝트에서 불필요한 파일(EgovUserDetailsServiceImpl.java 등)을 생성할 수 있음.

### 수정 방향

`@Tool` description에 5.0 분기 안내 추가:
```
userDetailsService → egovVersion=4.3 전용
                     5.0은 EgovSecurityConfig.jdbcUsersByUsernameQuery 프로퍼티로 대체
                     (EgovJdbcUserDetailsManager가 RTE 내부에서 자동 구성)
successHandler / failureHandler → javaConfig 4.3 전용
                     5.0은 EgovSecurityConfig.defaultTargetUrl / loginFailureUrl로 처리
```

---

## [🟡 이슈 F] `@Tool` description — 5.0 구조 안내 없음

### 수정 방향

```
javaConfig (5.0) → EgovProjectSecurityConfig.java
                   @Import(EgovSecurityConfiguration.class) 진입점만 생성
                   반드시 contextsecurity(5.0)과 함께 사용 (종속 관계)
                   EgovSecurityConfiguration(RTE)이 SecurityFilterChain 자동 구성
                   ⚠️ XML <bean> 직접 선언 시 BootstrapMethodError 발생

contextSecurity (5.0) → EgovSecurityConfig Bean 프로퍼티 선언 XML
                        javaConfig(5.0)과 함께 사용 필수
                        egov-security XML 네임스페이스 없음 (5.0에서 제거)
```

---

## 비파괴성 검토

| 항목 | 기존 동작 영향 | 이유 |
|---|---|---|
| `contextSecurity43()` | **없음** | 독립 메서드 — 변경 없음 |
| `contextSecurity50()` 교체 | **없음** | 기존 생성 완료 파일 불변. 신규 생성만 변경 |
| `javaConfig43()` | **없음** | 독립 메서드 — 변경 없음 |
| `javaConfig50()` 교체 | **없음** | 기존 생성 완료 파일 불변. 신규 생성만 변경 |
| `formatted()` 인자 축소 | **없음** | 기존 `%s` 5개 → 1개로 줄어드는 것이므로 기존 파일 무영향 |
| `@Tool` description 수정 | **없음** | Claude 안내 개선, 생성 로직 미변경 |

---

## 구현 순서

```
[1단계] contextSecurity50() 전면 재작성 (버그 A+B)
        egov-security 네임스페이스 제거
        XML 루트 → <beans> (spring-beans.xsd만)
        내용 → <bean class="EgovSecurityConfig"> 32개 property
        ↓

[2단계] javaConfig50() 전면 재작성 (버그 C+D)
        import 전체 제거
        클래스명 EgovSecurityConfig → EgovProjectSecurityConfig
        내용 → @Import(EgovSecurityConfiguration.class) 진입점만
        formatted(pkg, pkg, pkg, pkg, pkg) → formatted(pkg)
        ↓

[3단계] SecurityTemplateTool.java @Tool description 수정 (이슈 E+F)
        javaConfig 5.0 — @Import 패턴 안내
        contextsecurity 5.0 — EgovSecurityConfig Bean 안내
        userDetailsService / successHandler / failureHandler — 5.0 불필요 안내
```

---

## 변경 파일 및 범위 요약

| 파일 | 변경 위치 | 변경 규모 |
|---|---|---|
| `SecurityTemplateService.java` | `contextSecurity50()` 280~456행 전체 교체 | 중 |
| `SecurityTemplateService.java` | `javaConfig50()` 682~848행 전체 교체 | 중 |
| `SecurityTemplateTool.java` | `@Tool` description javaConfig / contextSecurity / userDetailsService 5.0 안내 | 소 |

---

## 최종 결정 사항

| 항목 | 결정 | 완료 |
|---|---|---|
| `contextSecurity50()` 전면 재작성 | ✅ **구현 완료** (1단계) | 2026-05-24 |
| `javaConfig50()` 전면 재작성 | ✅ **구현 완료** (2단계) | 2026-05-24 |
| `@Tool` description 5.0 구조 안내 | ✅ **구현 완료** (3단계) | 2026-05-24 |
| `\A/WEB-INF/jsp/.*\Z` 이스케이프 버그 수정 | ✅ **빌드 오류 수정** | 2026-05-24 |
