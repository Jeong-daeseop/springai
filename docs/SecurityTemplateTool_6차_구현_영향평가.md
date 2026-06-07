# SecurityTemplateTool 6차 구현 영향평가

작성일: 2026-05-24
목적: bopr_Security_구조정리.txt 기반 SecurityTemplateService.java / SecurityTemplateTool.java
     현재 구현과 실제 eGovFrame 5.0 프로젝트(bopr) 간 차이 분석

참조: docs/bopr_Security_구조정리.txt

---

## 비교 분석표

### XML 설정 방식

| 항목 | 현재 Template | bopr 실제 | 차이 |
|---|---|---|---|
| **4.3 beans XSD** | `spring-beans-4.0.xsd` ✅ | `spring-beans-4.0.xsd` | 일치 |
| **4.3 egov XSD** | `egov-security-4.3.0.xsd` ✅ | `egov-security-4.3.0.xsd` | 일치 |
| **4.3 보안 설정 요소** | `<egov-security:config>` ✅ | `<egov-security:config>` | 일치 |
| **5.0 beans XSD** | `spring-beans.xsd` ✅ | `spring-beans.xsd` | 일치 |
| **5.0 egov 네임스페이스** | `egov-security` 선언 있음 🔴 | **없음 (5.0에서 제거)** | **오류** |
| **5.0 보안 설정 요소** | `<egov-security:config>` 🔴 | `<bean class="EgovSecurityConfig">` | **오류** |
| **5.0 설정 프로퍼티** | 없음 🔴 | jdbcQuery / hash / xframe / csrf 등 15개 | **누락** |

---

### Java Config 방식

| 항목 | 현재 Template | bopr 실제 | 차이 |
|---|---|---|---|
| **4.3 진입점** | `WebSecurityConfigurerAdapter` 상속 | - | - |
| **5.0 진입점** | `SecurityFilterChain` @Bean 직접 작성 🟡 | `@Import(EgovSecurityConfiguration.class)` | **구조 다름** |
| **5.0 HttpSecurity 구성** | 직접 작성 🟡 | RTE가 EgovSecurityConfig Bean 읽어 자동 구성 | **구조 다름** |
| **BootstrapMethodError** | 안내 없음 🟡 | XML bean 직접 선언 시 발생 → @Import 우회 필수 | **누락** |

---

### 필터 체인 (webXmlFilter)

| 순서 | 현재 Template | bopr 실제 | 차이 |
|---|---|---|---|
| 1 | CharacterEncodingFilter ✅ | CharacterEncodingFilter | 일치 |
| 2 | ❌ 없음 | HTMLTagFilter (XSS 방어) | **누락** |
| 3 | ❌ 없음 | LoginPolicyFilter (계정 잠금/만료) | **누락** |
| 4 | ❌ 없음 | EgovSpringSecurityLoginFilter (DB 인증 핵심) | **누락** |
| 5 | springSecurityFilterChain ✅ | springSecurityFilterChain | 일치 |
| 6 | ❌ 없음 | EgovSpringSecurityLogoutFilter (세션 초기화) | **누락** |

---

### 인증 설정

| 항목 | 현재 Template | bopr 실제 | 차이 |
|---|---|---|---|
| **패스워드 인코더** | BCryptPasswordEncoder 🟡 | SHA-256 + Base64 + userId salt | **불일치** |
| **사용자 조회** | JdbcTemplate 직접 작성 🟡 | `jdbcUsersByUsernameQuery` 프로퍼티 주입 | **방식 다름** |
| **권한 조회** | JdbcTemplate 직접 작성 🟡 | `jdbcAuthoritiesByUsernameQuery` 프로퍼티 주입 | **방식 다름** |
| **세션 매핑** | 없음 🟡 | EgovSessionMapping (ResultSet → LoginVO → EgovUserDetails) | **누락** |
| **Globals.Auth 프로필** | 없음 🔵 | dummy / session / security 동적 선택 | **누락** |
| **context-egovuserdetailshelper.xml** | 없음 🔵 | 프로필별 UserDetails 분기 XML | **누락** |

---

### 보안 정책

| 항목 | 현재 Template | bopr 실제 | 차이 |
|---|---|---|---|
| **CSRF** | 활성화 (`<csrf/>`) 🟡 | **비활성화** (`csrf=false`) | **불일치** |
| **X-Frame-Options** | 없음 🟡 | SAMEORIGIN | **누락** |
| **X-Content-Type-Options** | 없음 🟡 | sniff=true | **누락** |
| **X-XSS-Protection** | 없음 🟡 | true | **누락** |
| **Cache-Control** | 없음 🔵 | false | **누락** |
| **alwaysUseDefaultTargetUrl** | 없음 🟡 | true | **누락** |
| **session-timeout** | 없음 🟡 | 300초 | **누락** |
| **동시 세션** | max-sessions=1 ✅ | concurrentMaxSessions=1 | 일치 |

---

### URL 설정

| 항목 | 현재 Template | bopr 실제 | 차이 |
|---|---|---|---|
| **loginPage** | `/uat/uia/egovLoginUsr.do` ✅ | `/uat/uia/egovLoginUsr.do` | 일치 |
| **loginProcessingUrl** | `/uat/uia/actionLogin.do` ✅ | `/uat/uia/actionLogin.do` | 일치 |
| **logoutUrl** | `/uat/uia/actionLogout.do` ✅ | `/uat/uia/actionLogout.do` | 일치 |
| **defaultTargetUrl** | `/index.jsp` 🟡 | `/main/Main.do` (프로젝트별) | 주석 필요 |
| **logoutSuccessUrl** | `/index.jsp` 🟡 | `/main/Main.do` (프로젝트별) | 주석 필요 |
| **accessDeniedUrl** | `/cmm/error/accessDenied.do` 🟡 | `/main/accessDenied.do` (프로젝트별) | 주석 필요 |

---

### DB 테이블 / SQL

| 항목 | 현재 Template | bopr 실제 | 차이 |
|---|---|---|---|
| **사용자 테이블** | `COMTNEMPLYRINFO` / `LETTNEMPLYRSCRTYESTBS` | `TN_USERS` (커스텀) | 프로젝트별 상이 |
| **권한 테이블** | `COMTNEMPLYRSCRTYESTBS` | `TN_EMPLYRSCRTYESTBS` | 프로젝트별 상이 |
| **롤 테이블** | `COMTNROLEINFO` | `TN_ROLEINFO` | 프로젝트별 상이 |
| **계층 테이블** | `COMTNROLES_HIERARCHY` | `TN_ROLES_HIERARCHY` | 프로젝트별 상이 |
| **계층 컬럼** | `PARNTS_ROLE`, `CHLDRN_ROLE` 🟡 | `PARNTS_ROLE`, `CHILD_ROLE` + LEFT JOIN | **컬럼명 불일치** |
| **URL 권한 SQL** | EgovReloadableFilter 자동 | `sqlRolesAndUrl` 프로퍼티로 직접 지정 | 방식 다름 |

---

### securityType 지원 현황

| securityType | 현재 지원 | bopr 기준 필요 | 차이 |
|---|---|---|---|
| `webxmlfilter` | ✅ | ✅ (필터 4개 누락) | 불완전 |
| `contextsecurity` | ✅ (4.3) / 🔴 (5.0 오류) | ✅ | 5.0 오류 |
| `javaconfig` | ✅ (4.3) / 🟡 (5.0 구조 다름) | ✅ | 5.0 구조 다름 |
| `userdetailsservice` | ✅ | ✅ (SQL 주입 방식 차이) | 방식 다름 |
| `rolehierarchy` | ✅ | ✅ | 컬럼명 불일치 |
| `loginpage` | ✅ | ✅ | 일치 |
| `successhandler` | ✅ | ✅ | 일치 |
| `failurehandler` | ✅ | ✅ | 일치 |
| `accessdeniedhandler` | ✅ | ✅ | 일치 |
| `loginfilter` | ❌ 없음 | EgovSpringSecurityLoginFilter | **누락** |
| `logoutfilter` | ❌ 없음 | EgovSpringSecurityLogoutFilter | **누락** |
| `sessionmapping` | ❌ 없음 | EgovSessionMapping | **누락** |
| `userdetailshelper` | ❌ 없음 | EgovUserDetailsHelper | **누락** |
| `loginpolicyfilter` | ❌ 없음 | EgovLoginPolicyFilter | **누락** |

---

## 우선순위 정리

| # | 심각도 | 항목 |
|---|---|---|
| 1 | 🔴 | `contextSecurity50()` — egov-security 네임스페이스 제거 + `<bean class="EgovSecurityConfig">` 전면 재작성 |
| 2 | 🔴 | `javaConfig50()` — `@Import(EgovSecurityConfiguration.class)` 패턴 + BootstrapMethodError 주의 안내 |
| 3 | 🔴 | 5.0 `userDetailsService()` — SQL 프로퍼티 주입 방식 분기 |
| 4 | 🟡 | `webXmlFilter()` — HTMLTagFilter / LoginPolicyFilter / LoginFilter / LogoutFilter 필터 4개 추가 |
| 5 | 🟡 | `loginfilter` securityType 신규 추가 — EgovSpringSecurityLoginFilter.java |
| 6 | 🟡 | `logoutfilter` securityType 신규 추가 — EgovSpringSecurityLogoutFilter.java |
| 7 | 🟡 | `sessionmapping` securityType 신규 추가 — EgovSessionMapping.java |
| 8 | 🟡 | `userdetailshelper` securityType 신규 추가 — EgovUserDetailsHelper.java |
| 9 | 🟡 | 패스워드 인코더 — BCrypt vs SHA-256 선택 주석 안내 |
| 10 | 🟡 | 보안 헤더 설정 — xframe / xss / sniff / cacheControl |
| 11 | 🟡 | session-timeout / alwaysUseDefaultTargetUrl 안내 |
| 12 | 🟡 | 역할 계층 SQL 컬럼명 안내 (CHLDRN_ROLE vs CHILD_ROLE) |
| 13 | 🔵 | `loginpolicyfilter` securityType 신규 추가 — EgovLoginPolicyFilter.java |
| 14 | 🔵 | `userdetailshelperxml` — context-egovuserdetailshelper.xml (dummy/session/security 프로필 분기) |
| 15 | 🔵 | 컨트롤러 인증 정보 사용법 안내 (EgovUserDetailsHelper 코드 스니펫) |

---

---

## eGovFrame 5.0 구조 심층 분석 (추가)

작성일: 2026-05-24
출처: Claude Desktop 세션 분석 — bopr EgovSecurityConfiguration 실제 코드 기반

### egov-security XML 네임스페이스 제거 3가지 원인

| 원인 | 내용 | Template 영향 |
|---|---|---|
| Spring Security 6 구조 변경 | WebSecurityConfigurerAdapter 삭제 → XML namespace 기반 클래스 소멸 | `<egov-security:config>` 자체가 무효 |
| Jakarta EE 마이그레이션 | javax.* → jakarta.* → 클래스 로딩 체인 파괴 | namespace parser 동작 불가 |
| BootstrapMethodError | Java 17 + XML BeanDefinition + CGLIB 프록시 충돌 | `EgovSecurityConfiguration` XML 선언 불가 |

---

### 5.0 3계층 구조

```
context-security.xml
  └── <bean class="EgovSecurityConfig"> (순수 POJO — Security API 의존성 없음)
        ↓ 빈 주입
BoprSecurityConfig.java
  └── @Configuration @Import(EgovSecurityConfiguration.class) (내용 없음 — 진입점만)
        ↓ @Import
EgovSecurityConfiguration.java
  └── @EnableWebSecurity + SecurityFilterChain @Bean + AuthenticationManager @Bean
```

**EgovSecurityConfig가 XML `<bean>` 선언 가능한 이유:**
Security API 의존성이 없는 순수 데이터 홀더 → CGLIB 프록시 불필요 → BootstrapMethodError 없음.

**EgovSecurityConfiguration이 @Import 필수인 이유:**
@EnableWebSecurity → 연쇄 @Configuration @Import → Java 17 invoke dynamic 바이트코드.
XML BeanDefinition 기반 CGLIB 프록시 처리 불가 → BootstrapMethodError.

---

### Spring Security 5 → 6 대응표 (이 프로젝트 기준)

| Spring Security 5 (구) | Spring Security 6 (신) | 위치 |
|---|---|---|
| `extends WebSecurityConfigurerAdapter` | `@Bean SecurityFilterChain` | `EgovSecurityConfiguration:336` |
| `configure(HttpSecurity)` override | `http.authorizeHttpRequests(...)` | `EgovSecurityConfiguration:355` |
| `configure(AuthenticationManagerBuilder)` override | `@Bean AuthenticationManager` | `EgovSecurityConfiguration:204` |
| `antMatchers()` | `requestMatchers()` + `PathPatternRequestMatcher` | `EgovSecurityConfiguration:346` |
| `XML <security:http>` 네임스페이스 | `EgovSecurityConfig POJO` + Java Config | `context-security.xml:12` |

---

### 현재 Template 대비 5.0 수정 포인트 확정

| # | 파일/메서드 | 현재 구현 | 올바른 방향 |
|---|---|---|---|
| 1 | `contextSecurity50()` 보안설정 요소 | `<egov-security:config>` 🔴 | `<bean class="EgovSecurityConfig">` + 15개 property |
| 2 | `javaConfig50()` 진입점 | SecurityFilterChain 직접 구현 🔴 | `@Import(EgovSecurityConfiguration.class)` 진입점만 |
| 3 | `contextSecurity50()` dataSource | `dataSource` 🟡 | `egov.dataSource` (alias — 순환참조 방지) |
| 4 | `javaConfig50()` AuthenticationManager | `AuthenticationConfiguration` 위임 🟡 | RTE `EgovSecurityConfiguration`이 담당 → 템플릿 불필요 |
| 5 | `SecurityTemplateTool.java` description | 5.0 구조 안내 없음 🟡 | 3계층 구조 + BootstrapMethodError 주의 안내 |

---

### EgovSecurityConfig Bean 주요 프로퍼티 목록 (contextSecurity50 재작성 기준)

```xml
<bean id="securityConfig"
    class="org.egovframe.rte.fdl.security.config.EgovSecurityConfig">
    <!-- 로그인/로그아웃 URL -->
    <property name="loginUrl"                       value="/uat/uia/egovLoginUsr.do"/>
    <property name="loginProcessUrl"                value="/uat/uia/actionLogin.do"/>
    <property name="logoutUrl"                      value="/uat/uia/actionLogout.do"/>
    <property name="logoutSuccessUrl"               value="/main/Main.do"/>
    <property name="loginFailureUrl"                value="/uat/uia/egovLoginUsr.do?login_error=1"/>
    <property name="accessDeniedUrl"                value="/main/accessDenied.do"/>
    <property name="defaultTargetUrl"               value="/main/Main.do"/>
    <property name="alwaysUseDefaultTargetUrl"      value="true"/>
    <!-- DataSource -->
    <property name="dataSource"                     value="egov.dataSource"/>
    <!-- 사용자/권한 조회 SQL -->
    <property name="jdbcUsersByUsernameQuery"
        value="SELECT USER_ID, USER_NM, PASSWORD, 1 ENABLED, DEPT_ID FROM TN_USERS WHERE USER_ID=?"/>
    <property name="jdbcAuthoritiesByUsernameQuery"
        value="SELECT A.SCRTY_DTRMN_TRGET_ID USER_ID, A.AUTHOR_CODE AUTHORITY FROM TN_EMPLYRSCRTYESTBS A, TN_USERS B WHERE A.SCRTY_DTRMN_TRGET_ID=B.USER_ID AND B.USER_ID=?"/>
    <property name="jdbcMapClass"
        value="egovframework.com.uat.uia.service.impl.EgovSessionMapping"/>
    <!-- 비밀번호 해시 -->
    <property name="hash"                           value="sha-256"/>
    <property name="hashBase64"                     value="true"/>
    <!-- 세션 -->
    <property name="concurrentMaxSessons"           value="1"/>
    <property name="concurrentExpiredUrl"           value="/EgovContent.do"/>
    <property name="errorIfMaximumExceeded"         value="false"/>
    <!-- 보안 헤더 -->
    <property name="sniff"                          value="true"/>
    <property name="xframeOptions"                  value="SAMEORIGIN"/>
    <property name="xssProtection"                  value="true"/>
    <property name="cacheControl"                   value="false"/>
    <!-- CSRF -->
    <property name="csrf"                           value="false"/>
    <property name="csrfAccessDeniedUrl"            value="/egovCSRFAccessDenied.do"/>
    <!-- 요청 매처 -->
    <property name="requestMatcherType"             value="regex"/>
    <!-- 인증 없이 접근 허용 경로 -->
    <property name="permitAllList"                  value="/css/**,/images/**,/js/**"/>
    <!-- DB 권한 매핑 SQL -->
    <property name="sqlRolesAndUrl"
        value="SELECT a.ROLE_PTTRN url, b.AUTHOR_CODE authority FROM TN_ROLEINFO a, TN_AUTHORROLERELATE b WHERE a.ROLE_CODE=b.ROLE_CODE AND a.ROLE_TY='url' ORDER BY a.ROLE_SORT"/>
    <property name="sqlRolesAndMethod"
        value="SELECT a.ROLE_PTTRN method, b.AUTHOR_CODE authority FROM TN_ROLEINFO a, TN_AUTHORROLERELATE b WHERE a.ROLE_CODE=b.ROLE_CODE AND a.ROLE_TY='method' ORDER BY a.ROLE_SORT"/>
    <property name="sqlHierarchicalRoles"
        value="SELECT a.CHILD_ROLE child, a.PARNTS_ROLE parent FROM TN_ROLES_HIERARCHY a LEFT JOIN TN_ROLES_HIERARCHY b ON (a.CHILD_ROLE=b.PARNTS_ROLE)"/>
    <!-- 메서드/포인트컷 보안 -->
    <property name="supportMethod"                  value="true"/>
    <property name="supportPointcut"                value="false"/>
</bean>
```

---

---

## javaConfig50() 문제 상세 분석 및 해결 방안 (추가)

작성일: 2026-05-24

### 문제 1 — 클래스명 충돌 (🔴 런타임 오류)

현재 생성 클래스명: `EgovSecurityConfig`
RTE 기존 클래스: `org.egovframe.rte.fdl.security.config.EgovSecurityConfig`

→ 동일 이름 → Spring 컨테이너 빈 충돌.

---

### 문제 2 — RTE 내부 컴포넌트 미연결 (🔴 기능 누락)

| 기능 | 현재 Template 구현 | RTE 제공 실제 구현 |
|---|---|---|
| URL 권한 매핑 | `FilterSecurityInterceptor` (deprecated) | `EgovMultipleRoleAuthorizationManager` |
| UserDetailsService | `EgovUserDetailsServiceImpl` 직접 작성 | `EgovJdbcUserDetailsManager` |
| AuthenticationManager | `AuthenticationConfiguration` 위임 | `DaoAuthenticationProvider` + `RoleHierarchyAuthoritiesMapper` |
| DB URL 동적 로드 | `EgovReloadableFilterInvocationSecurityMetadataSource` 수동 등록 | `EgovSecurityConfiguration`이 자동 구성 |

→ 현재 템플릿은 RTE가 이미 구현한 것을 중복으로 다시 작성하는 구조.

---

### 문제 3 — BootstrapMethodError 위험

```java
// 현재 (위험)
@Configuration
@EnableWebSecurity
public class EgovSecurityConfig { ... }   // 직접 @EnableWebSecurity

// 올바른 방식
@Configuration
@Import(EgovSecurityConfiguration.class)  // @Import로 RTE 위임 — BootstrapMethodError 우회
public class EgovProjectSecurityConfig { }
```

---

### 해결 방안 — javaConfig50() 생성 내용 전면 교체

```java
package {pkg}.config;

import org.egovframe.rte.fdl.security.config.EgovSecurityConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * eGovFrame 5.0 Spring Security Java Config 진입점
 *
 * 구조: 3계층 분리
 *   1. context-security.xml  → EgovSecurityConfig Bean (설정값 POJO)
 *   2. 이 클래스            → @Import 진입점 (내용 없음)
 *   3. EgovSecurityConfiguration → RTE 제공, SecurityFilterChain 실제 구성
 *
 * ⚠️ EgovSecurityConfiguration을 XML <bean>으로 직접 선언하면
 *    Spring Security 6.5 + Java 17 환경에서 BootstrapMethodError 발생.
 *    반드시 이 클래스처럼 @Import 방식으로 로드해야 함.
 *
 * 필요 파일:
 *   - context-security.xml (getSecurityTemplate("contextsecurity", pkg, "5.0"))
 *     → EgovSecurityConfig Bean에 loginUrl, dataSource, SQL, 보안헤더 등 설정
 *
 * EgovSecurityConfiguration이 자동 구성하는 항목:
 *   - SecurityFilterChain (URL 접근 제어, 세션, 헤더, CSRF)
 *   - AuthenticationManager (DaoAuthenticationProvider + RoleHierarchyAuthoritiesMapper)
 *   - EgovJdbcUserDetailsManager (사용자/권한 SQL 조회)
 *   - DB 기반 URL 권한 동적 로드 (EgovMultipleRoleAuthorizationManager)
 */
@Configuration
@Import(EgovSecurityConfiguration.class)
public class EgovProjectSecurityConfig {
    // 내용 없음 — 진입점 역할만
    // 실제 Security 구성은 EgovSecurityConfiguration이 담당
    // 설정값은 context-security.xml의 EgovSecurityConfig Bean에서 주입
}
```

---

### contextSecurity50() + javaConfig50() 관계 재정립

두 securityType이 반드시 함께 사용되어야 함:

```
contextsecurity (5.0)              javaConfig (5.0)
        ↓                                  ↓
context-security.xml         EgovProjectSecurityConfig.java
<bean class="EgovSecurityConfig">   @Import(EgovSecurityConfiguration.class)
  loginUrl / dataSource /                   ↑
  SQL / 보안헤더 / CSRF ...                 |
        └──────────── EgovSecurityConfiguration이 읽어서 SecurityFilterChain 구성
```

현재는 두 securityType이 독립적으로 동작하는 것처럼 생성 → 실제로는 종속 관계.

---

### javaConfig50() 수정 전후 비교

| 항목 | 현재 | 올바른 방향 |
|---|---|---|
| 생성 클래스명 | `EgovSecurityConfig` (RTE 클래스와 충돌) 🔴 | `EgovProjectSecurityConfig` |
| 클래스 내용 | SecurityFilterChain 직접 구현 🔴 | `@Import(EgovSecurityConfiguration.class)` 1줄 |
| RTE 연동 | 없음 🔴 | EgovSecurityConfiguration이 자동 처리 |
| contextsecurity 관계 | 독립 🟡 | 종속 (함께 사용 필수) |
| BootstrapMethodError | 안내 없음 🟡 | JavaDoc 주의 안내 포함 |

---

### SecurityTemplateTool.java @Tool description 수정 필요

```
javaConfig (5.0) → EgovProjectSecurityConfig.java
                   @Import(EgovSecurityConfiguration.class) 진입점만 생성
                   반드시 contextsecurity(5.0)과 함께 사용
                   EgovSecurityConfiguration(RTE)이 SecurityFilterChain 자동 구성
                   ⚠️ XML <bean> 직접 선언 시 BootstrapMethodError 발생
```

---

## 최종 결정 사항

| 항목 | 결정 | 완료 |
|---|---|---|
| `contextSecurity50()` 전면 재작성 | ✅ 구현 완료 | 7차에서 완료 |
| `javaConfig50()` @Import 패턴 | ✅ 구현 완료 | 7차에서 완료 |
| 5.0 `userDetailsService()` 분기 | ✅ 구현 완료 | 2026-05-24 |
| `webXmlFilter()` 필터 4개 추가 | ✅ 구현 완료 | 2026-05-24 |
| `loginfilter` securityType 신규 | ✅ 구현 완료 | 2026-05-24 |
| `logoutfilter` securityType 신규 | ✅ 구현 완료 | 2026-05-24 |
| `sessionmapping` securityType 신규 | ✅ 구현 완료 | 2026-05-24 |
| `userdetailshelper` securityType 신규 | ✅ 구현 완료 | 2026-05-24 |
| 패스워드 인코더 안내 | ✅ 구현 완료 | loginFilter 주석으로 안내 |
| 보안 헤더 / session-timeout | ✅ 구현 완료 | contextSecurity50() 프로퍼티로 포함 |
| `loginpolicyfilter` securityType 신규 | ✅ 구현 완료 | 2026-05-24 |
| `userdetailshelperxml` securityType 신규 | ✅ 구현 완료 | 2026-05-24 |
