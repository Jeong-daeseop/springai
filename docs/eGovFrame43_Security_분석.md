# eGovFrame 4.3 Security 구현 분석

작성일: 2026-05-24
대상: SecurityTemplateTool / SecurityTemplateService — eGovFrame 4.3 관련 전체 코드

---

## 1. 구성 방식 개요

eGovFrame 4.3 Security는 두 가지 독립적 방식으로 제공된다.

```
방식 A — XML (레거시 / 공공 SI 표준)
  webxmlfilter    → web.xml에 DelegatingFilterProxy 삽입
  contextsecurity → context-security.xml (Spring Security XML 네임스페이스)

방식 B — Java Config (신규 권장)
  javaconfig(4.3) → EgovSecurityConfig extends WebSecurityConfigurerAdapter

공통 컴포넌트 (A/B 모두 사용)
  userdetailsservice  → EgovUserDetailsServiceImpl.java  ({pkg}.service)
  rolehierarchy(4.3)  → EgovRoleHierarchyConfig.java     ({pkg}.config)
  loginpage           → egovLoginUsr.jsp
  successhandler(4.3) → EgovAuthenticationSuccessHandler ({pkg}.security, javax)
  failurehandler(4.3) → EgovAuthenticationFailureHandler ({pkg}.security, javax)
  accessdeniedhandler → EgovAccessDeniedHandler           ({pkg}.security, javax)
```

> 방식 A와 B는 `springSecurityFilterChain` Bean 충돌로 **동시 사용 불가**.
> `userdetailsservice` · `rolehierarchy` · 핸들러 3종은 A/B 어느 방식과도 조합 가능.

---

## 2. 방식 A — XML 방식 (contextSecurity43)

### 인증 흐름

```
HTTP 요청
  └─ DelegatingFilterProxy (web.xml, filter-name=springSecurityFilterChain)
       └─ springSecurityFilterChain
            ├─ [before FILTER_SECURITY_INTERCEPTOR]
            │    egovSecurityFilter (FilterSecurityInterceptor)
            │      ├─ securityMetadataSource: EgovReloadableFilterInvocationSecurityMetadataSource
            │      │    → 서버 시작 시 COMTNROLEINFO 전체 조회
            │      │    → URL 패턴(ROLE_PTTRN) : 필요 ROLE(AUTHOR_CODE) Map 구성
            │      └─ accessDecisionManager: AffirmativeBased
            │           ├─ RoleHierarchyVoter (roleHierarchy 참조)
            │           ├─ WebExpressionVoter
            │           └─ AuthenticatedVoter
            ├─ form-login (/uat/uia/actionLogin.do)
            │    → j_username / j_password 수신
            │    → authenticationManager (alias)
            │         └─ egovAuthenticationProvider (EgovUserDetailsHelper)
            │              ├─ userDetailsService: egovUserDetailsService (ref)
            │              └─ passwordEncoder: BCryptPasswordEncoder
            └─ session (newSession, max 1)
```

### 빈 구성 전체 목록

| 빈 ID | 클래스 | 제공 주체 |
|---|---|---|
| `springSecurityFilterChain` | (자동 생성) | Spring Security |
| `authenticationManager` | (alias) | Spring Security |
| `egovAuthenticationProvider` | `EgovUserDetailsHelper` | **eGovFrame RTE** |
| `passwordEncoder` | `BCryptPasswordEncoder` | Spring Security |
| `egovSecurityFilter` | `FilterSecurityInterceptor` | Spring Security |
| `egovSecurityMetadataSource` | `EgovReloadableFilterInvocationSecurityMetadataSource` | **eGovFrame RTE** |
| `accessDecisionManager` | `AffirmativeBased` | Spring Security |
| `roleHierarchy` | `RoleHierarchyImpl` (하드코딩) | Spring Security |
| `loginSuccessHandler` | `EgovAuthenticationSuccessHandler` | **eGovFrame RTE** |
| `loginFailureHandler` | `EgovAuthenticationFailureHandler` | **eGovFrame RTE** |
| `accessDeniedHandler` | `EgovAccessDeniedHandler` | **eGovFrame RTE** |

→ XML 방식에서는 핸들러 3종 모두 **eGovFrame RTE가 직접 제공**한다.
   `successhandler` / `failurehandler` / `accessdeniedhandler` securityType 파일 생성 불필요.

### 정적 자원 제외 방식

```xml
<http pattern="/css/**"    security="none"/>
<http pattern="/images/**" security="none"/>
<http pattern="/js/**"     security="none"/>
<http pattern="/favicon.ico" security="none"/>
```

Java Config의 `WebSecurity.ignoring()`과 동일 효과. Security 필터 체인 자체 미적용.

### intercept-url 설계

```xml
<intercept-url pattern="/uat/uia/**"           access="IS_AUTHENTICATED_ANONYMOUSLY"/>
<intercept-url pattern="/cmm/fms/FileDown.do"  access="IS_AUTHENTICATED_ANONYMOUSLY"/>
<intercept-url pattern="/sym/ccm/zip/**"        access="IS_AUTHENTICATED_ANONYMOUSLY"/>
<!-- 나머지 URL은 egovSecurityFilter가 COMTNROLEINFO 기반 동적 제어 -->
```

`<intercept-url>`은 로그인 URL 등 명시 허용만 담당. 나머지는 `egovSecurityFilter`(COMTNROLEINFO DB 조회)가 처리.

### 세션 관리

```xml
<session-management session-fixation-protection="newSession"
                    invalid-session-url="/uat/uia/egovLoginUsr.do">
    <concurrency-control max-sessions="1"
                         error-if-maximum-exceeded="false"
                         expired-url="/uat/uia/egovLoginUsr.do?expired=1"/>
</session-management>
```

- `newSession`: 로그인 성공 시 새 세션 발급 (세션 고정 공격 방지)
- `max-sessions=1`: 중복 로그인 시 기존 세션 만료 (error-if-maximum-exceeded=false)
- `invalid-session-url`: 만료 세션 접근 시 로그인 페이지 리다이렉트

### CSRF

```xml
<csrf/>
```

기본 활성화. JSP 폼에서 `${_csrf.parameterName}` / `${_csrf.token}` 필수.

---

## 3. 방식 B — Java Config 방식 (javaConfig43)

### 인증 흐름

```
HTTP 요청
  └─ WebSecurityConfigurerAdapter.configure(HttpSecurity)
       ├─ WebSecurity.ignoring() → /css/** /images/** /js/** /favicon.ico (Security 제외)
       ├─ authorizeRequests
       │    ├─ /uat/uia/**   permitAll
       │    ├─ /cmm/fms/FileDown.do permitAll
       │    └─ anyRequest authenticated
       ├─ addFilterBefore(egovSecurityFilter, FilterSecurityInterceptor.class)
       │    FilterSecurityInterceptor
       │      ├─ authenticationManager: authenticationManagerBean() (WebSecurityConfigurerAdapter 제공)
       │      ├─ accessDecisionManager: AffirmativeBased(RoleHierarchyVoter, AuthenticatedVoter)
       │      └─ securityMetadataSource: FilterInvocationSecurityMetadataSource
       │           (EgovReloadableFilterInvocationSecurityMetadataSource 구현체 주입)
       ├─ formLogin
       │    ├─ loginPage: /uat/uia/egovLoginUsr.do
       │    ├─ loginProcessingUrl: /uat/uia/actionLogin.do
       │    ├─ usernameParameter: j_username
       │    ├─ passwordParameter: j_password
       │    ├─ successHandler: EgovAuthenticationSuccessHandler("/index.jsp")
       │    └─ failureHandler: EgovAuthenticationFailureHandler("/uat/uia/egovLoginUsr.do?login_error=1")
       ├─ logout (/uat/uia/actionLogout.do → /index.jsp, invalidateSession)
       ├─ sessionManagement (newSession, max 1)
       ├─ csrf (ignoringAntMatchers("/api/**"))
       └─ exceptionHandling (EgovAccessDeniedHandler)

configure(AuthenticationManagerBuilder)
  └─ userDetailsService(EgovUserDetailsServiceImpl) + passwordEncoder(BCrypt)
```

### 필드 주입 구조

```java
@Autowired private EgovUserDetailsServiceImpl             userDetailsService;    // {pkg}.service
@Autowired private FilterInvocationSecurityMetadataSource egovSecurityMetadataSource; // Spring Security I/F
@Autowired private EgovAccessDeniedHandler                egovAccessDeniedHandler;    // {pkg}.security
@Autowired private RoleHierarchy                          roleHierarchy;         // EgovRoleHierarchyConfig Bean
```

### Bean 구성

```java
@Bean FilterSecurityInterceptor egovSecurityFilter()
  → authenticationManagerBean() + accessDecisionManager() + egovSecurityMetadataSource

@Bean AccessDecisionManager accessDecisionManager()
  → AffirmativeBased[RoleHierarchyVoter(roleHierarchy), AuthenticatedVoter]

@Bean PasswordEncoder passwordEncoder()
  → BCryptPasswordEncoder

@Bean EgovAuthenticationSuccessHandler loginSuccessHandler()
  → new EgovAuthenticationSuccessHandler("/index.jsp")

@Bean EgovAuthenticationFailureHandler loginFailureHandler()
  → new EgovAuthenticationFailureHandler("/uat/uia/egovLoginUsr.do?login_error=1")
```

### XML 방식 대비 차이점

| 항목 | XML (방식 A) | Java Config (방식 B) |
|---|---|---|
| AuthenticationProvider | `EgovUserDetailsHelper` (RTE 래퍼) | `DaoAuthenticationProvider` (Spring Security 내장) |
| UserDetailsService 참조 | bean ref `egovUserDetailsService` | 직접 `EgovUserDetailsServiceImpl` 타입 주입 |
| 핸들러 구현 | RTE 제공 클래스 직접 사용 | `successhandler`/`failurehandler` securityType으로 별도 생성 |
| roleHierarchy | XML 하드코딩 (대안: EgovRoleHierarchyConfig 안내) | EgovRoleHierarchyConfig @Bean 반드시 필요 |
| accessDecisionManager Voter | RoleHierarchyVoter + **WebExpressionVoter** + AuthenticatedVoter | RoleHierarchyVoter + AuthenticatedVoter (**WebExpressionVoter 없음**) |
| 정적 자원 제외 | `<http security="none"/>` | `WebSecurity.ignoring().antMatchers()` |

---

## 4. 공통 컴포넌트 — eGovFrame 4.3 버전 특성

### userDetailsService (ver 무관)

```
패키지: {pkg}.service
클래스: EgovUserDetailsServiceImpl
빈 이름: egovUserDetailsServiceImpl (Spring 기본 — @Service 어노테이션 기준)

인증 로직:
  1. COMTNEMPLYRINFO WHERE EMPLYR_ID=? AND EMPLYR_STTUS_CODE='ESC01'
     → PASSWORD, LOCK_AT 조회
  2. COMTNEMPLYRSCRTYESTBS WHERE SCRTY_DTRMN_TRGET_ID=?
     → AUTHOR_CODE 조회 → SimpleGrantedAuthority 목록
  3. 권한 미설정 시 ROLE_USER 기본 부여
  4. User.builder() → username / password / authorities / accountLocked 반환
```

### roleHierarchy (ver=4.3)

```
패키지: {pkg}.config
클래스: EgovRoleHierarchyConfig
특이사항: Spring Security 5.x → RoleHierarchyImpl.setHierarchy() 사용
         (Spring Security 6.x의 fromHierarchy() 정적 팩토리 미사용)

DB 조회:
  SELECT PARNTS_ROLE, CHLDRN_ROLE FROM COMTNROLES_HIERARCHY
  → "{PARNTS_ROLE} > {CHLDRN_ROLE}\n" 형태로 계층 문자열 조합
  → RoleHierarchyImpl.setHierarchy(hierarchy.toString())
```

### 핸들러 3종 (ver=4.3) — javax 버전

```
패키지: {pkg}.security
import: javax.servlet.* (eGovFrame 5.0은 jakarta.servlet.*)

EgovAuthenticationSuccessHandler
  extends SimpleUrlAuthenticationSuccessHandler
  → super(defaultTargetUrl) 생성자
  → onAuthenticationSuccess() 오버라이드 (커스텀 로직 삽입 지점)

EgovAuthenticationFailureHandler
  extends SimpleUrlAuthenticationFailureHandler
  → super(defaultFailureUrl) 생성자
  → onAuthenticationFailure() 오버라이드 (실패 횟수 기록, 계정 잠금 지점)

EgovAccessDeniedHandler
  implements AccessDeniedHandler @Component
  → response.sendRedirect(contextPath + "/cmm/error/accessDenied.do")
  → URL 하드코딩 (변경 필요 시 소스 수정)
```

---

## 5. 발견된 버그 (eGovFrame 4.3 관련 신규)

---

### [🔴 버그 A] `javaConfig43()` — cross-package import 4건 누락

생성되는 `EgovSecurityConfig.java`가 참조하는 클래스들의 import가 없다.

| 클래스 | 실제 패키지 | 현재 import |
|---|---|---|
| `EgovUserDetailsServiceImpl` | `{pkg}.service` | ❌ 없음 |
| `EgovAccessDeniedHandler` | `{pkg}.security` | ❌ 없음 |
| `EgovAuthenticationSuccessHandler` | `{pkg}.security` | ❌ 없음 |
| `EgovAuthenticationFailureHandler` | `{pkg}.security` | ❌ 없음 |

**영향**: `javaConfig43()` 생성 코드 컴파일 즉시 실패.
`successhandler` / `failurehandler` / `accessdeniedhandler` / `userdetailsservice` securityType 파일을 모두 생성해도 `javaConfig43()`의 `EgovSecurityConfig.java`가 이 클래스들을 import 없이 참조하므로 `cannot find symbol` 오류.

**수정 방향**:

`javaConfig43()` import 블록에 추가 (`pkg`는 `formatted()` 인자):
```java
import %s.security.EgovAuthenticationSuccessHandler;
import %s.security.EgovAuthenticationFailureHandler;
import %s.security.EgovAccessDeniedHandler;
import %s.service.EgovUserDetailsServiceImpl;
```

`formatted(pkg)` → `formatted(pkg, pkg, pkg, pkg, pkg)` 로 인자 순서 조정 필요.

---

### [🔴 버그 B] `javaConfig50()` — cross-package import 4건 동일 누락

`javaConfig43()`과 동일한 4개 클래스를 참조하지만 import 없음.

```java
private final EgovUserDetailsServiceImpl           userDetailsService;   // import 없음
private final EgovAccessDeniedHandler              accessDeniedHandler;  // import 없음
...
@Bean EgovAuthenticationSuccessHandler loginSuccessHandler() {...}       // import 없음
@Bean EgovAuthenticationFailureHandler loginFailureHandler() {...}       // import 없음
```

**수정 방향**: `javaConfig43()`과 동일. `formatted(pkg)` → `formatted(pkg, pkg, pkg, pkg, pkg)` 조정.

---

### [🔴 버그 C] `contextSecurity43()` — `egovUserDetailsService` 빈 ref 불일치

XML (182~184행):
```xml
<beans:bean id="egovAuthenticationProvider"
    class="egovframework.rte.fdl.security.userdetails.EgovUserDetailsHelper">
    <beans:property name="userDetailsService" ref="egovUserDetailsService"/>
```

`ref="egovUserDetailsService"` — 이 이름의 빈을 찾는다.

`userdetailsservice` securityType이 생성하는 클래스:
```java
@Service
public class EgovUserDetailsServiceImpl implements UserDetailsService {
```

Spring 기본 빈 이름 규칙: `@Service` 어노테이션 + 이름 미지정 → **클래스명 첫 글자 소문자** = `egovUserDetailsServiceImpl`.

**`egovUserDetailsService` ≠ `egovUserDetailsServiceImpl`** → `NoSuchBeanDefinitionException` 런타임 오류.

**수정 방향 (Option A)**: XML ref를 실제 빈 이름으로 수정 (권장)
```xml
<beans:property name="userDetailsService" ref="egovUserDetailsServiceImpl"/>
```

**수정 방향 (Option B)**: `EgovUserDetailsServiceImpl`에 빈 이름 명시
```java
@Service("egovUserDetailsService")
public class EgovUserDetailsServiceImpl implements UserDetailsService {
```
→ `userdetailsservice` securityType 파일을 수정해야 하므로 범위가 넓음.

**결론**: Option A — XML ref 수정이 단순하고 Java 코드에 영향 없음.

---

### [🟡 이슈 D] `successhandler`/`failurehandler` — XML 방식에서 사용 불필요

XML 방식(contextSecurity43)이 사용하는 핸들러:
```xml
<beans:bean id="loginSuccessHandler"
    class="egovframework.rte.fdl.security.userdetails.EgovAuthenticationSuccessHandler">
```
→ **eGovFrame RTE 제공 클래스** (별도 생성 불필요)

`successhandler` securityType이 생성하는 클래스:
```java
package {pkg}.security;
public class EgovAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler
```
→ **동일 이름, 다른 패키지** — XML에서 이미 RTE 클래스를 사용 중이므로 사용자 생성 클래스와 충돌 가능.

XML 방식에서 `successhandler`/`failurehandler`를 생성해서 XML에 교체 적용하려면 fully-qualified class name 명시 필요:
```xml
<beans:bean id="loginSuccessHandler"
    class="{pkg}.security.EgovAuthenticationSuccessHandler">
```

`@Tool` description 또는 `webXmlFilter()` 주석에 안내 없음 → Claude가 XML 방식 + successhandler 동시 사용 시 혼란 가능.

**수정 방향**: `@Tool` description에 용도 명확화 추가:
- `successhandler`/`failurehandler` → "Java Config 방식 전용. XML 방식에서는 eGovFrame RTE 클래스를 contextSecurity에서 직접 참조하므로 불필요"

---

### [🟡 이슈 E] `javaConfig43()` accessDecisionManager — `WebExpressionVoter` 누락

XML contextSecurity43 (210~219행):
```xml
<beans:bean class="org.springframework.security.access.vote.AffirmativeBased">
    <beans:constructor-arg>
        <beans:list>
            <beans:bean class="...RoleHierarchyVoter"/>
            <beans:bean class="...WebExpressionVoter"/>   ← 포함
            <beans:bean class="...AuthenticatedVoter"/>
        </beans:list>
    </beans:constructor-arg>
</beans:bean>
```

Java Config javaConfig43 (452~457행):
```java
return new AffirmativeBased(Arrays.asList(
    new RoleHierarchyVoter(roleHierarchy),
    new AuthenticatedVoter()
    // WebExpressionVoter 없음
));
```

`WebExpressionVoter`는 `access="hasRole('ROLE_ADMIN')"` 같은 SpEL 표현식 평가를 담당.

`contextSecurity43`에서 `<intercept-url access="IS_AUTHENTICATED_ANONYMOUSLY"/>` 같은 표현식을 사용하는데, Java Config에서 `WebExpressionVoter`가 없으면 SpEL 표현식 기반 접근 규칙이 평가되지 않는다.

단, `javaConfig43()`에서는 `authorizeRequests().antMatchers(...).permitAll()`을 사용하므로 SpEL 표현식 대신 Java 메서드 체인 방식이라 `WebExpressionVoter` 없어도 동작. 하지만 XML과 일관성 차이가 존재한다.

**결론**: 기능 영향 낮음. XML 방식과의 설계 일관성 측면에서 언급 수준.

---

## 6. 방식별 사용 시나리오 및 securityType 호출 순서

### 시나리오 A: XML 방식 (레거시 / 공공 SI)

```
1. getSecurityTemplate("webxmlfilter", ..., "4.3")
   → web.xml에 DelegatingFilterProxy 삽입 (contextConfigLocation 주석 안내 포함)

2. getSecurityTemplate("contextsecurity", ..., "4.3")
   → context-security.xml 생성
   ※ 단, egovUserDetailsService ref → egovUserDetailsServiceImpl로 수동 수정 필요 (버그 C)

3. getSecurityTemplate("userdetailsservice", ..., "4.3")
   → EgovUserDetailsServiceImpl.java ({pkg}.service)

4. getSecurityTemplate("rolehierarchy", ..., "4.3")
   → EgovRoleHierarchyConfig.java ({pkg}.config)
   → context-security.xml roleHierarchy 빈을 EgovRoleHierarchyConfig로 교체

5. getSecurityTemplate("loginpage", ..., "4.3")
   → egovLoginUsr.jsp

※ successhandler / failurehandler — XML 방식에서 불필요 (RTE 제공)
※ accessdeniedhandler — XML 방식에서 불필요 (RTE 제공)
```

### 시나리오 B: Java Config 방식 (신규)

```
1. getSecurityTemplate("javaconfig", ..., "4.3")
   → EgovSecurityConfig.java ({pkg}.config, extends WebSecurityConfigurerAdapter)
   ※ 단, cross-package import 4건 수동 추가 필요 (버그 A)

2. getSecurityTemplate("userdetailsservice", ..., "4.3")
   → EgovUserDetailsServiceImpl.java ({pkg}.service)

3. getSecurityTemplate("rolehierarchy", ..., "4.3")
   → EgovRoleHierarchyConfig.java ({pkg}.config)

4. getSecurityTemplate("successhandler", ..., "4.3")
   → EgovAuthenticationSuccessHandler.java ({pkg}.security, javax)

5. getSecurityTemplate("failurehandler", ..., "4.3")
   → EgovAuthenticationFailureHandler.java ({pkg}.security, javax)

6. getSecurityTemplate("accessdeniedhandler", ..., "4.3")
   → EgovAccessDeniedHandler.java ({pkg}.security, javax)

7. getSecurityTemplate("loginpage", ..., "4.3")
   → egovLoginUsr.jsp

※ webxmlfilter / contextsecurity — Java Config 방식에서 불필요
```

---

## 7. 버그 전체 요약 (eGovFrame 4.3 신규 발견)

| # | 심각도 | 버그 | 영향 |
|---|---|---|---|
| A | 🔴 | `javaConfig43()` cross-package import 4건 누락 | 생성 코드 컴파일 실패 |
| B | 🔴 | `javaConfig50()` cross-package import 4건 동일 누락 | 생성 코드 컴파일 실패 |
| C | 🔴 | `contextSecurity43()` `ref="egovUserDetailsService"` → 실제 빈명 `egovUserDetailsServiceImpl` 불일치 | 서버 기동 시 `NoSuchBeanDefinitionException` |
| D | 🟡 | `successhandler`/`failurehandler` XML 방식에서 사용 불필요 + RTE 클래스명 충돌 안내 없음 | Claude가 불필요 파일 생성 가능 |
| E | 🟡 | `javaConfig43()` `WebExpressionVoter` 누락 (XML과 불일치) | SpEL 기반 접근 규칙 미평가 (실사용 영향 낮음) |
