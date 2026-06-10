# SecurityTemplateTool 사용 예시

## 파라미터

| 파라미터 | 필수 | 기본값 | 설명 |
|----------|:----:|--------|------|
| `securityType` | ✅ | — | 생성할 템플릿 종류 (대소문자 무관) |
| `packageName` | — | `egovframework.let.sample` | Java 파일 package 선언용 |
| `egovVersion` | — | `5.0` | `"4.3"` 또는 `"5.0"` |

---

## 시나리오 1 — eGovFrame 4.3 기존 프로젝트 XML 방식 (공공 SI 가장 일반적)

### 1-1. web.xml 필터 설정

**Claude 요청:**
```
eGovFrame 4.3 프로젝트에 Spring Security 추가하려고 해.
web.xml DelegatingFilterProxy 설정 템플릿 줘.
```

**Tool 호출:**
```
getSecurityTemplate("webXmlFilter", "", "4.3")
```

**생성 결과 요약:**
```xml
<!-- CharacterEncodingFilter (Security 필터보다 앞에 위치) -->
<filter>
    <filter-name>encodingFilter</filter-name>
    <filter-class>org.springframework.web.filter.CharacterEncodingFilter</filter-class>
    ...
</filter>

<!-- Spring Security Filter Chain -->
<filter>
    <filter-name>springSecurityFilterChain</filter-name>
    <filter-class>org.springframework.web.filter.DelegatingFilterProxy</filter-class>
</filter>
```

---

### 1-2. context-security.xml 전체 설정

**Claude 요청:**
```
eGovFrame 4.3 context-security.xml 표준 템플릿 생성해줘.
```

**Tool 호출:**
```
getSecurityTemplate("contextSecurity", "", "4.3")
```

**생성 결과 요약:**
```xml
<http auto-config="false" use-expressions="true"
      access-decision-manager-ref="accessDecisionManager">
    <intercept-url pattern="/uat/uia/**" access="IS_AUTHENTICATED_ANONYMOUSLY"/>
    <form-login login-page="/uat/uia/egovLoginUsr.do" .../>
    <logout logout-url="/uat/uia/actionLogout.do" .../>
    <session-management .../>
    <csrf/>
    <custom-filter ref="egovSecurityFilter" before="FILTER_SECURITY_INTERCEPTOR"/>
</http>
<!-- EgovReloadableFilterInvocationSecurityMetadataSource -->
<!-- AccessDecisionManager (RoleHierarchyVoter) -->
<!-- RoleHierarchyImpl -->
```

---

### 1-3. 로그인 JSP

**Claude 요청:**
```
eGovFrame 표준 로그인 JSP 만들어줘.
```

**Tool 호출:**
```
getSecurityTemplate("loginPage", "", "4.3")
```

**생성 결과 요약:**
```jsp
<form action="/uat/uia/actionLogin.do" method="post">
    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
    <input type="text"     name="j_username"/>
    <input type="password" name="j_password"/>
    <button type="submit">로그인</button>
</form>
```

---

## 시나리오 2 — eGovFrame 4.3 Java Config 방식

### 2-1. EgovSecurityConfig.java (WebSecurityConfigurerAdapter)

**Claude 요청:**
```
eGovFrame 4.3 프로젝트에 Java Config 방식 Spring Security 설정해줘.
패키지는 egovframework.let.cmm.
```

**Tool 호출:**
```
getSecurityTemplate("javaConfig", "egovframework.let.cmm", "4.3")
```

**생성 결과 요약:**
```java
package egovframework.let.cmm.config;

@Configuration
@EnableWebSecurity
public class EgovSecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring().antMatchers("/css/**", "/images/**", "/js/**");
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
                .antMatchers("/uat/uia/**").permitAll()
                .anyRequest().authenticated()
            .and()
            .formLogin()
                .loginPage("/uat/uia/egovLoginUsr.do")
                .loginProcessingUrl("/uat/uia/actionLogin.do")
            .and()
            .logout()
                .logoutUrl("/uat/uia/actionLogout.do")
            .and()
            .sessionManagement()
                .sessionFixation().newSession()
                .maximumSessions(1)
            .and()
            .and()
            .csrf().ignoringAntMatchers("/api/**")
            .and()
            .addFilterBefore(egovSecurityFilter(), FilterSecurityInterceptor.class);
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder());
    }
}
```

---

### 2-2. UserDetailsService

**Claude 요청:**
```
COMTNEMPLYRINFO 기반 UserDetailsService 구현체 만들어줘.
패키지는 egovframework.let.cmm.
```

**Tool 호출:**
```
getSecurityTemplate("userDetailsService", "egovframework.let.cmm", "4.3")
```

**생성 결과 요약:**
```java
package egovframework.let.cmm.service;

@Service
public class EgovUserDetailsServiceImpl implements UserDetailsService {
    // COMTNEMPLYRINFO WHERE EMPLYR_STTUS_CODE = 'ESC01' (재직중)
    // COMTNEMPLYRSCRTYESTBS → AUTHOR_CODE (ROLE_ADMIN, ROLE_USER)
    // authorities 없을 경우 ROLE_USER 기본 부여
}
```

---

## 시나리오 3 — eGovFrame 5.0 신규 프로젝트 Java Config 방식

### 3-1. EgovSecurityConfig.java (SecurityFilterChain Bean)

**Claude 요청:**
```
eGovFrame 5.0 신규 프로젝트 Spring Security Java Config 만들어줘.
패키지는 egovframework.let.emp.
```

**Tool 호출:**
```
getSecurityTemplate("javaConfig", "egovframework.let.emp", "5.0")
```

**생성 결과 요약:**
```java
package egovframework.let.emp.config;

@Configuration
@EnableWebSecurity
public class EgovSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/images/**", "/js/**").permitAll()
                .requestMatchers("/uat/uia/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/uat/uia/egovLoginUsr.do")
                .loginProcessingUrl("/uat/uia/actionLogin.do")
            )
            .logout(logout -> logout
                .logoutUrl("/uat/uia/actionLogout.do")
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

---

### 3-2. RoleHierarchy — COMTNROLES_HIERARCHY DB 동적 로드

**Claude 요청:**
```
COMTNROLES_HIERARCHY 테이블 기반 RoleHierarchy Config 만들어줘.
패키지는 egovframework.let.emp.
```

**Tool 호출:**
```
getSecurityTemplate("roleHierarchy", "egovframework.let.emp", "5.0")
```

**생성 결과 요약:**
```java
package egovframework.let.emp.config;

@Configuration
public class EgovRoleHierarchyConfig {

    @Bean
    public RoleHierarchy roleHierarchy() {
        // SELECT PARNTS_ROLE, CHLDRN_ROLE FROM COMTNROLES_HIERARCHY
        // → "ROLE_USER > ROLE_ADMIN\n..." 문자열 구성
        // → RoleHierarchyImpl.setHierarchy() 적용
    }
}
```

---

## 시나리오 4 — DB URL-ROLE 매핑 구조 확인

**Claude 요청:**
```
COMTNROLEINFO, COMTNROLES_HIERARCHY 어떻게 연결되는지
접근 제어 SQL 보여줘.
```

**Tool 호출:**
```
getSecurityTemplate("securityMapper", "", "")
```

**생성 결과 요약:**
```sql
-- URL 패턴 → 권한 매핑 조회
SELECT ri.ROLE_PTTRN, ar.AUTHOR_CODE
FROM   COMTNROLEINFO ri
JOIN   COMTNAUTHORROLERELATE ar ON ri.ROLE_CODE = ar.ROLE_CODE
ORDER  BY ri.ROLE_SORT ASC;

-- ROLE 계층 조회
SELECT PARNTS_ROLE, CHLDRN_ROLE FROM COMTNROLES_HIERARCHY;

-- 프로그램 목록 확인
SELECT PROGRM_FILE_NM, PROGRM_KOR_NM, URL FROM COMTNPROGRMLIST;

-- 메뉴-프로그램 연결 확인
SELECT m.MENU_NO, m.MENU_NM, m.PROGRM_FILE_NM, p.URL
FROM   COMTNMENUINFO m
JOIN   COMTNPROGRMLIST p ON m.PROGRM_FILE_NM = p.PROGRM_FILE_NM;
```

---

## 시나리오 5 — 신규 도메인 추가 시 전체 흐름 (AuthTool 연계)

**Claude 요청:**
```
직원관리 도메인 (/emp/employer) 신규 추가 시
메뉴·권한 등록부터 Security 설정까지 전체 진행해줘.
```

**Claude 자동 실행 순서:**

```
Step 1. getProgramList("employer")
        → 중복 등록 여부 사전 확인

Step 2. generateMenuInsertSql("10000", "/emp/employer", "직원관리", "EgovEmployerList")
        → COMTNMENUINFO + COMTNPROGRMLIST INSERT SQL 반환

Step 3. generateAuthInsertSql("/emp/employer", "직원관리", "emp")
        → COMTNROLEINFO + COMTNAUTHORROLERELATE INSERT SQL 반환

Step 4. getSecurityTemplate("securityMapper", "", "")
        → SQL 실행 후 URL-ROLE 매핑 확인 방법 안내

Step 5. getSecurityTemplate("loginPage", "", "")
        → 로그인 JSP CSRF 토큰 확인
```

---

## 버전별 javaConfig API 비교

| 항목 | eGovFrame 4.3 (`"4.3"`) | eGovFrame 5.0 (`"5.0"`) |
|------|------------------------|------------------------|
| 기반 클래스 | `extends WebSecurityConfigurerAdapter` | `SecurityFilterChain @Bean` |
| 메서드 | `configure(HttpSecurity http)` override | `filterChain(HttpSecurity http)` |
| 정적 자원 제외 | `WebSecurity.ignoring().antMatchers()` | `requestMatchers().permitAll()` |
| URL 매처 | `antMatchers()` | `requestMatchers()` |
| 인가 메서드 | `authorizeRequests()` | `authorizeHttpRequests()` |
| DSL 스타일 | `.and()` 체이닝 | Lambda DSL |
| CSRF 제외 | `csrf().ignoringAntMatchers()` | `csrf(c -> c.ignoringRequestMatchers())` |
| 인증 설정 | `configure(AuthenticationManagerBuilder)` override | `UserDetailsService @Bean` |

---

## 시나리오 6 — 단일 파일 직접 저장 (Phase 2)

outputPath를 지정하면 파일을 직접 생성한다. 문자열 반환 없이 저장 결과만 반환된다.

### 6-1. javaConfig 파일 저장

**Claude 요청:**
```
eGovFrame 4.3 javaConfig 파일을 /Users/me/egov-project에 저장해줘.
패키지는 egovframework.let.emp.
```

**Tool 호출:**
```
getSecurityTemplate(
  securityType  = "javaConfig",
  packageName   = "egovframework.let.emp",
  egovVersion   = "4.3",
  outputPath    = "/Users/me/egov-project",
  projectType   = "war"
)
```

**반환 결과:**
```
✅ Security 템플릿 저장 완료

저장 경로: /Users/me/egov-project
생성 파일: 1개

  + src/main/java/egovframework/let/emp/config/EgovProjectSecurityConfig.java
```

---

### 6-2. context-security.xml 저장

**Tool 호출:**
```
getSecurityTemplate(
  securityType  = "contextSecurity",
  packageName   = "egovframework.let.emp",
  egovVersion   = "5.0",
  outputPath    = "/Users/me/egov-project",
  projectType   = "war"
)
```

**반환 결과:**
```
✅ Security 템플릿 저장 완료

저장 경로: /Users/me/egov-project
생성 파일: 1개

  + src/main/resources/egovframework/spring/context-security.xml
```

---

## 시나리오 7 — 조합 키워드로 전체 보안 셋업 (Phase 3)

### 7-1. eGovFrame 4.3 전체 보안 셋업

한 번의 호출로 15개 파일을 생성한다.

**Claude 요청:**
```
eGovFrame 4.3 WAR 프로젝트 보안 설정 전체 셋업해줘.
프로젝트 경로는 /Users/me/egov-war-43, 패키지는 egovframework.let.sample.
```

**Tool 호출:**
```
getSecurityTemplate(
  securityType  = "setup-all-war-43",
  packageName   = "egovframework.let.sample",
  egovVersion   = "4.3",
  outputPath    = "/Users/me/egov-war-43",
  projectType   = "war"
)
```

**반환 결과:**
```
✅ Security 템플릿 저장 완료

저장 경로: /Users/me/egov-war-43
생성 파일: 15개

  + src/main/webapp/WEB-INF/web.xml.fragment
  + src/main/resources/egovframework/spring/context-security.xml
  + src/main/java/egovframework/let/sample/config/EgovProjectSecurityConfig.java
  + src/main/java/egovframework/let/sample/sec/service/impl/EgovUserDetailsServiceImpl.java
  + src/main/java/egovframework/let/sample/sec/config/EgovRoleHierarchyConfig.java
  + src/main/webapp/WEB-INF/jsp/egovframework/com/uat/uia/egovLoginUsr.jsp
  + src/main/resources/egovframework/spring/context-egovuserdetailshelper.xml
  + src/main/java/egovframework/let/sample/sec/filter/EgovSpringSecurityLoginFilter.java
  + src/main/java/egovframework/let/sample/sec/filter/EgovSpringSecurityLogoutFilter.java
  + src/main/java/egovframework/let/sample/uat/uap/filter/EgovLoginPolicyFilter.java
  + src/main/java/egovframework/let/sample/uat/uia/service/impl/EgovSessionMapping.java
  + src/main/java/egovframework/let/sample/sec/handler/EgovAuthenticationSuccessHandler.java
  + src/main/java/egovframework/let/sample/sec/handler/EgovAuthenticationFailureHandler.java
  + src/main/java/egovframework/let/sample/sec/handler/EgovAccessDeniedHandler.java
  + src/main/resources/egovframework/sqlmap/security/security-mapper.sql
```

---

### 7-2. eGovFrame 5.0 전체 보안 셋업

**Tool 호출:**
```
getSecurityTemplate(
  securityType  = "setup-all-war-50",
  packageName   = "egovframework.let.sample",
  egovVersion   = "5.0",
  outputPath    = "/Users/me/egov-war-50",
  projectType   = "war"
)
```

**반환 결과:**
```
✅ Security 템플릿 저장 완료

저장 경로: /Users/me/egov-war-50
생성 파일: 11개

  + src/main/resources/egovframework/spring/context-security.xml
  + src/main/java/egovframework/let/sample/config/EgovProjectSecurityConfig.java
  + src/main/java/egovframework/let/sample/sec/config/EgovRoleHierarchyConfig.java
  + src/main/webapp/WEB-INF/jsp/egovframework/com/uat/uia/egovLoginUsr.jsp
  + src/main/resources/egovframework/spring/context-egovuserdetailshelper.xml
  + src/main/java/egovframework/let/sample/sec/filter/EgovSpringSecurityLoginFilter.java
  + src/main/java/egovframework/let/sample/sec/filter/EgovSpringSecurityLogoutFilter.java
  + src/main/java/egovframework/let/sample/uat/uap/filter/EgovLoginPolicyFilter.java
  + src/main/java/egovframework/let/sample/uat/uia/service/impl/EgovSessionMapping.java
  + src/main/java/egovframework/let/sample/sec/handler/EgovAccessDeniedHandler.java
  + src/main/resources/egovframework/sqlmap/security/security-mapper.sql
```

---

### 7-3. 필터만 별도 추가

기존 프로젝트에 DB 인증 필터를 추가할 때 사용한다.

**Tool 호출:**
```
getSecurityTemplate(
  securityType  = "setup-filters",
  packageName   = "egovframework.let.emp",
  egovVersion   = "5.0",
  outputPath    = "/Users/me/egov-existing",
  projectType   = "war"
)
```

**반환 결과:**
```
✅ Security 템플릿 저장 완료

저장 경로: /Users/me/egov-existing
생성 파일: 4개

  + src/main/java/egovframework/let/emp/sec/filter/EgovSpringSecurityLoginFilter.java
  + src/main/java/egovframework/let/emp/sec/filter/EgovSpringSecurityLogoutFilter.java
  + src/main/java/egovframework/let/emp/uat/uap/filter/EgovLoginPolicyFilter.java
  + src/main/java/egovframework/let/emp/uat/uia/service/impl/EgovSessionMapping.java
```

---

## 조합 키워드 요약

### 4.3 XML Security 방식 (공공 SI 표준)

| securityType | alias | 파일 수 | 구성 |
|---|---|:---:|---|
| `setup-war-43` | `setup-war-43-xml` | 9 | webXmlFilter + contextSecurity + userDetailsService + sessionMapping + loginFilter + logoutFilter + loginPolicyFilter + loginPage + userDetailsHelperXml |
| `setup-all-war-43` | `setup-all-war-43-xml` | 10 | setup-war-43-xml + securityMapper |

> ※ `setup-war-43` / `setup-all-war-43`은 각각 XML alias. 동일하게 동작한다.  
> ※ **조합 원칙**: webXmlFilter가 참조하는 필터 구현체 3종(loginFilter/logoutFilter/loginPolicyFilter)이 포함되어 있어 조합 안에서 참조가 완결된다.  
> ※ javaConfig / roleHierarchy / 핸들러 미포함 — contextSecurity XML이 이미 XML Bean으로 선언하므로 Java Config와 동시 로드 시 Bean 중복으로 기동 실패.

### 4.3 Java Config 방식 (WebSecurityConfigurerAdapter)

| securityType | 파일 수 | 구성 |
|---|:---:|---|
| `setup-war-43-java` | 7 | javaConfig + userDetailsService + roleHierarchy + successHandler + failureHandler + accessDeniedHandler + loginPage |
| `setup-all-war-43-java` | 12 | setup-war-43-java + setup-filters + securityMapper |

> ※ contextSecurity(XML `<http>`) 미포함 — XML 방식과 동시 사용 불가.  
> ※ 핸들러 3종은 Java Config 조합에만 포함. XML 방식은 RTE가 핸들러를 직접 제공한다.

### 5.0

| securityType | 파일 수 | 구성 |
|---|:---:|---|
| `setup-war-50` | 5 | contextSecurity + javaConfig + roleHierarchy + loginPage + userDetailsHelperXml |
| `setup-all-war-50` | 11 | setup-war-50 + setup-filters + accessDeniedHandler + securityMapper |

### 필터 / 핸들러 단독 조합

| securityType | 파일 수 | 구성 |
|---|:---:|---|
| `setup-filters` | 4 | loginFilter + logoutFilter + loginPolicyFilter + sessionMapping |
| `setup-handlers-43` | 3 | successHandler + failureHandler + accessDeniedHandler |

> ※ `setup-handlers-43`은 XML 조합에 사용하지 않는다. Java Config 조합 또는 단독 사용 전용.

---

> **조합 키워드 사용 시 주의사항**
> - 반드시 `outputPath`와 함께 사용해야 한다. `outputPath` 없이 조합 키워드를 사용하면 지원하지 않는 securityType 안내 메시지가 반환된다.
> - 4.3 전용 키워드(`setup-war-43-*`)에 `egovVersion=5.0` 지정 시 예외 발생.
> - 5.0 전용 키워드(`setup-war-50`, `setup-all-war-50`)에 `egovVersion=4.3` 지정 시 예외 발생.
> - `webXmlFilter`는 **4.3 WAR XML Security 전용**. 5.0에서 단독 호출 시 오류 메시지 반환.
