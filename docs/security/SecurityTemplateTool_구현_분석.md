# SecurityTemplateTool 구현 분석

작성일: 2026-05-22

---

## 한 줄 요약

eGovFrame 4.3 (`WebSecurityConfigurerAdapter`) / 5.0 (`SecurityFilterChain`) 분기를 포함한
**Spring Security 설정 템플릿 문자열을 반환**하는 MCP Tool.

---

## 구조 개요

`SecurityTemplateTool` → `SecurityTemplateService` 2-레이어 구조.

- **Tool**: `getSecurityTemplate(securityType, packageName, egovVersion)` — 파라미터 검증 없이 Service에 위임
- **Service**: 830줄. `securityType` switch로 7개 템플릿 문자열 반환

---

## 7개 securityType 라우팅

| securityType                 | 대상                                      |
| ---------------------------- | --------------------------------------- |
| `xml-legacy`                 | WAR 4.3 — security.xml namespace 방식     |
| `javaconfig`                 | WAR 4.3/5.0 — 핵심 분기 지점                  |
| `boot-security-adapter`      | Boot 4.3 — WebSecurityConfigurerAdapter |
| `boot-security-filter-chain` | Boot 5.0 — SecurityFilterChain Bean     |
| `context-security`           | applicationContext-security.xml 공통      |
| `login-page`                 | JSP 로그인 폼 (버전 공통)                       |
| `security-mapper`            | Mapper XML 참조 SQL                       |

`buildResult()`가 프로젝트 타입/버전 조합을 보고 securityType을 자동 선택:

| 조합         | 자동 선택 securityType           |
| ---------- | ---------------------------- |
| WAR + 4.3  | `xml-legacy`                 |
| WAR + 5.0  | `java-config-filter-chain`   |
| Boot + 4.3 | `boot-security-adapter`      |
| Boot + 5.0 | `boot-security-filter-chain` |

---

## 핵심 분기: `javaconfig`

```
case "javaconfig" -> ver.startsWith("4") ? javaConfig43(pkg) : javaConfig50(pkg)
```

### 4.3 (`javaConfig43`)

- `extends WebSecurityConfigurerAdapter` (Spring 5.3 이하만 존재)
- `@Override configure(HttpSecurity http)` 3개 오버라이드 — HttpSecurity, AuthenticationManagerBuilder, WebSecurity
- `antMatchers()` + `authorizeRequests()` (deprecated API)
- `.and()` 체이닝 방식
- `@Autowired EgovUserDetailsServiceImpl`

### 5.0 (`javaConfig50`)

- 상속 없음. `@Bean SecurityFilterChain filterChain(HttpSecurity http)`
- `authorizeHttpRequests()` + `requestMatchers()` (Spring 6 신규 API)
- Lambda DSL: `http.authorizeHttpRequests(auth -> auth.requestMatchers(...).permitAll()...)`
- 생성자 주입 (필드 주입 제거)
- `return http.build()` 필수

### API 대응표

| 4.3                                    | 5.0                             |
| -------------------------------------- | ------------------------------- |
| `extends WebSecurityConfigurerAdapter` | `@Bean SecurityFilterChain`     |
| `antMatchers()`                        | `requestMatchers()`             |
| `authorizeRequests()`                  | `authorizeHttpRequests()`       |
| `.and()` 체이닝                           | Lambda DSL                      |
| `configure(HttpSecurity)`              | `filterChain(HttpSecurity)`     |
| `configure(AuthManagerBuilder)`        | `AuthenticationManager @Bean`   |
| `@Autowired` 필드 주입                     | 생성자 주입                          |
| `AntPathRequestMatcher` (선택)           | `MvcRequestMatcher` (권장)        |
| `.csrf().disable()`                    | `.csrf(csrf -> csrf.disable())` |
| `void` 반환                              | `SecurityFilterChain` 반환        |

---

## 버전 공통 컴포넌트 3개

### 1. `userDetailsService(pkg)`

- `COMTNEMPLYRINFO` 테이블에서 `EMPLYR_STTUS_CODE = 'ESC01'`(정상) 조건 조회
- `COMTNEMPLYRSCRTYESTBS`에서 권한 목록 JOIN
- `UserDetails` 구현체 반환 — eGovFrame 표준 테이블 스키마 직접 반영

### 2. `roleHierarchy(pkg)`

- `COMTNROLES_HIERARCHY` 테이블에서 계층 관계를 동적 로드
- `RoleHierarchyImpl`에 문자열로 주입: `"ROLE_ADMIN > ROLE_USER"`
- 정적 하드코딩 대신 DB 기반 — 운영 중 권한 계층 변경 가능

### 3. `contextSecurity50()` 반환값 = `contextSecurity43()`

- applicationContext-security.xml은 Spring Security namespace(`security:`) 기반
- Spring 5.x / 6.x 모두 동일 namespace 지원 → 버전 분기 불필요
- XML 방식 자체가 버전 독립적

---

## eGovFrame 표준 테이블 매핑

| 테이블                     | 역할                |
| ----------------------- | ----------------- |
| `COMTNEMPLYRINFO`       | 사용자 인증 (ID/PW/상태) |
| `COMTNEMPLYRSCRTYESTBS` | 사용자별 권한 목록        |
| `COMTNROLEINFO`         | URL-ROLE 매핑       |
| `COMTNROLES_HIERARCHY`  | 권한 계층 구조          |

---

## 설계 판단 포인트

**세션 기반 유지**
공공 SI 표준이 stateless REST가 아닌 세션 방식. `.sessionManagement()`에서 무효화 설정만 있고 `stateless` 설정 없음.

**CSRF 활성화**
로그인 폼에 `${_csrf.parameterName}` / `${_csrf.token}` hidden input 포함. `.csrf().disable()` 미적용이 의도적.

**eGovFrame 표준 테이블 직결**
`UserDetailsService` 구현이 eGovFrame 표준 DB 스키마를 그대로 사용 — 표준 프레임워크와 즉시 호환.

---

## 현재 제약 / 알려진 이슈

| 항목                               | 상태      | 비고                                                                              |
| -------------------------------- | ------- | ------------------------------------------------------------------------------- |
| `securityMapper()` 통합            | ⚠️ 미완   | 참조 SQL 반환만 함. 실제 `RoleHierarchyService` 구현체 없음. Claude가 프로젝트에 붙이는 구조            |
| Boot 4.3 `boot-security-adapter` | ⚠️      | `WebSecurityConfigurerAdapter`는 Spring Boot 3.x에서 제거됨 — Boot 4.3(Boot 2.7.x) 전용 |
| XML namespace URI                | ✅ 영향 없음 | Spring 5.x / 6.x 동일 namespace 지원 확인                                             |

---

## Boot 4.3 vs Boot 5.0 — 구현이 다른 이유

### 버전 연결 구조

"Boot 타입"이지만 내부적으로 완전히 다른 Spring 버전이 탑재됩니다.

```
eGovFrame 4.3  →  Spring Boot 2.7.x  →  Spring Framework 5.3.x  →  Spring Security 5.7.x
eGovFrame 5.0  →  Spring Boot 3.x    →  Spring Framework 6.x    →  Spring Security 6.x
```

### Spring Security가 무엇을 삭제했는가

| 시점                             | 변화                                           |
| ------------------------------ | -------------------------------------------- |
| Spring Security 5.7 (Boot 2.7) | `WebSecurityConfigurerAdapter` deprecated 경고 |
| Spring Security 6.0 (Boot 3.0) | `WebSecurityConfigurerAdapter` **클래스 자체 삭제** |

Boot 4.3(Boot 2.7.x)에서는 deprecated이지만 **컴파일/실행 모두 가능**.
Boot 5.0(Boot 3.x)에서는 **클래스가 없으므로 import 자체 실패** → 빌드 에러.

### 코드 수준 비교

**Boot 4.3 (`javaConfig43`) — 상속 방식**

```java
// Spring Security 5.x: 클래스가 존재함
@Configuration
@EnableWebSecurity
public class EgovSecurityConfig extends WebSecurityConfigurerAdapter {  // ← 5.x에만 존재

    @Autowired
    private EgovUserDetailsServiceImpl userDetailsService;              // 필드 주입

    @Override
    public void configure(WebSecurity web) { }                          // 3개 메서드 오버라이드

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()                                        // 구 API
                .antMatchers("/uat/uia/**").permitAll()                 // antMatchers
            .and()                                                      // .and() 체이닝
            .formLogin()
                .loginPage("/uat/uia/egovLoginUsr.do")
            .and()
            .csrf().ignoringAntMatchers("/api/**");
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) { }
}
```

**Boot 5.0 (`javaConfig50`) — Bean 방식**

```java
// Spring Security 6.x: WebSecurityConfigurerAdapter 클래스 없음 → 상속 불가
@Configuration
@EnableWebSecurity
public class EgovSecurityConfig {                                       // ← 상속 없음

    public EgovSecurityConfig(EgovUserDetailsServiceImpl userDetailsService) { }  // 생성자 주입

    @Bean                                                               // ← Bean으로 등록
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth                         // 신규 API
                .requestMatchers("/uat/uia/**").permitAll()             // requestMatchers
                .anyRequest().authenticated()
            )                                                           // Lambda DSL
            .formLogin(form -> form
                .loginPage("/uat/uia/egovLoginUsr.do")
            )
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"));

        return http.build();                                            // ← 반드시 반환
    }
}
```

### 변경 포인트 4가지

| 항목         | Boot 4.3 (Security 5.x)                | Boot 5.0 (Security 6.x)            | 변경 이유       |
| ---------- | -------------------------------------- | ---------------------------------- | ----------- |
| **클래스 구조** | `extends WebSecurityConfigurerAdapter` | 상속 없음, `@Bean SecurityFilterChain` | 클래스 삭제      |
| **URL 매처** | `antMatchers()`                        | `requestMatchers()`                | MVC 통합 강화   |
| **설정 API** | `authorizeRequests()`                  | `authorizeHttpRequests()`          | 타입 안전성 개선   |
| **DSL 방식** | `.and()` 체이닝                           | Lambda DSL                         | 가독성/컴파일 안전성 |

### 핵심 한 줄

> Boot 4.3은 Security 5.x(아직 존재), Boot 5.0은 Security 6.x(삭제됨) — 같은 목적이지만 **쓸 수 있는 API 자체가 다릅니다.** "어쩔 수 없이" 다른 것.

---

## WAR 4.3 (xml-legacy) vs WAR 5.0 (java-config-filter-chain) — 구현이 다른 이유

Boot 4.3↔5.0과는 **다른 차원의 이유**입니다. Boot는 "API 삭제" 때문이었지만, WAR은 **설정 방식 자체의 철학 변화**가 핵심입니다.

### 두 방식의 구조

**WAR 4.3 — xml-legacy**

```
web.xml
  └─ DelegatingFilterProxy (springSecurityFilterChain)
       └─ context-security.xml
            └─ <http> namespace 태그로 전체 설정
```

**WAR 5.0 — java-config-filter-chain**

```
WebApplicationInitializer (또는 web.xml 최소화)
  └─ @Configuration EgovSecurityConfig
       └─ @Bean SecurityFilterChain filterChain(HttpSecurity http)
```

### xml-legacy 실제 내용 (contextSecurity43)

```xml
<!-- web.xml: DelegatingFilterProxy가 Security 진입점 -->
<filter>
    <filter-name>springSecurityFilterChain</filter-name>
    <filter-class>org.springframework.web.filter.DelegatingFilterProxy</filter-class>
</filter>

<!-- context-security.xml: XML 네임스페이스로 전체 제어 -->
<http auto-config="false" use-expressions="true"
      access-decision-manager-ref="accessDecisionManager">

    <intercept-url pattern="/uat/uia/**" access="IS_AUTHENTICATED_ANONYMOUSLY"/>

    <form-login
        login-page="/uat/uia/egovLoginUsr.do"
        login-processing-url="/uat/uia/actionLogin.do"
        default-target-url="/index.jsp"/>

    <logout logoutUrl="/uat/uia/actionLogout.do" invalidate-session="true"/>

    <session-management session-fixation-protection="newSession">
        <concurrency-control max-sessions="1"/>
    </session-management>

    <csrf/>
    <custom-filter ref="egovSecurityFilter" before="FILTER_SECURITY_INTERCEPTOR"/>
</http>
```

### java-config-filter-chain 실제 내용 (javaConfig50)

```java
@Configuration
@EnableWebSecurity
public class EgovSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/uat/uia/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/uat/uia/egovLoginUsr.do")
                .loginProcessingUrl("/uat/uia/actionLogin.do")
            )
            .logout(logout -> logout
                .logoutUrl("/uat/uia/actionLogout.do")
                .invalidateHttpSession(true)
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(1)
            )
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
            .addFilterBefore(egovSecurityFilter(), FilterSecurityInterceptor.class);

        return http.build();
    }
}
```

### 항목별 비교

| 항목                     | WAR 4.3 xml-legacy                 | WAR 5.0 java-config-filter-chain |
| ---------------------- | ---------------------------------- | -------------------------------- |
| **설정 파일**              | `web.xml` + `context-security.xml` | `EgovSecurityConfig.java`        |
| **설정 언어**              | XML 태그 (`<http>`, `<form-login>`)  | Java 코드 (Lambda DSL)             |
| **진입점**                | `DelegatingFilterProxy` in web.xml | `@Bean SecurityFilterChain`      |
| **URL 매처**             | XML `pattern` 속성                   | `requestMatchers()`              |
| **Bean 정의**            | XML `<beans:bean id="...">`        | `@Bean` 메서드                      |
| **오류 발견 시점**           | 런타임 (XML 파싱)                       | 컴파일 타임 (타입 검사)                   |
| **Spring Security 지원** | 5.x / 6.x 모두 XML namespace 유지      | 6.x 권장 방식                        |

### XML namespace가 6.x에서도 동작하는데 왜 바꿨는가

`SecurityTemplateService`에 이 힌트가 있습니다:

```java
private String contextSecurity50() {
    // Spring Security XML 네임스페이스가 버전 간 하위 호환 유지
    return contextSecurity43();     // ← 동일 XML 반환
}
```

**XML 방식은 Spring 6.x에서도 기술적으로 동작합니다.** 그럼에도 WAR 5.0에서 Java Config를 선택한 이유:

**1. Spring Security 팀의 공식 권고**
> "XML namespace continues to work, but Java configuration is the recommended approach for new projects." — Spring Security 6.0 릴리즈 노트

**2. 설정 충돌 방지**
```
context-security.xml의 <http> 태그
      +
EgovSecurityConfig의 @Bean SecurityFilterChain
      ↓
springSecurityFilterChain Bean 이중 등록 → 기동 실패
```
XML과 Java Config를 동시에 쓸 수 없음. 5.0에서 Java Config를 선택하면 XML을 완전히 제거 가능.

**3. eGovFrame 5.0 코드베이스 방향**
`javax.*` → `jakarta.*` 전환과 함께 전반적으로 **XML 설정 축소, Java Config 확대** 방향으로 전환. Security만 XML 유지하면 방향성 불일치.

**4. 컴파일 타임 검증**
```xml
<!-- XML: 오타가 있어도 파싱 성공, 런타임에 실패 -->
<!-- login-procesing-url ← 오타 (s 누락) -->
<form-login login-procesing-url="/uat/uia/actionLogin.do"/>
```
```
// Java Config: 컴파일 에러 → 즉시 발견
.formLogin(form -> form.loginProcesingUrl(""))  // 컴파일 에러
```

### 핵심 한 줄

> Boot 4.3↔5.0은 **"API가 삭제되어 어쩔 수 없이"** 다른 것.
> WAR 4.3↔5.0은 **"기술적으로 XML도 동작하지만 의도적으로 Java Config를 선택"** 한 것.

---

## 호출 흐름

```
사용자: "이 프로젝트에 Spring Security 설정 추가해줘"
    │
    ▼
buildResult()          ← projectType + egovVersion 보고 securityType 자동 결정
    │
    ▼
getSecurityTemplate(securityType, packageName, egovVersion)
    │
    ▼
SecurityTemplateService.getTemplate()
    │  switch(securityType)
    ├─ "javaconfig" → ver.startsWith("4") ? javaConfig43() : javaConfig50()
    ├─ "context-security" → contextSecurity43() (버전 공통)
    ├─ "login-page" → loginPage()
    └─ ...
    │  템플릿 문자열 반환
    ▼
Claude가 {{PACKAGE}} 등 플레이스홀더 치환 후 saveGeneratedCode()로 저장
```
