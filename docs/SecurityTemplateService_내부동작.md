# SecurityTemplateService 내부 동작

## 개요

eGovFrame 5.x 표준 Spring Security 설정 파일 템플릿을 securityType 별로 반환하는 서비스.
DB 접근 없이 순수 문자열(템플릿 코드)만 반환한다.

---

## 호출 흐름

```
Claude Desktop
    │ 자연어 요청
    ▼
SecurityTemplateTool.getSecurityTemplate(securityType, packageName)
    │ @Tool 어노테이션 — Claude가 자동 라우팅
    ▼
SecurityTemplateService.getSecurityTemplate(securityType, packageName)
    │ packageName null/blank → "egovframework.let.sample" 기본값 적용
    │ securityType.toLowerCase() → switch 분기
    ▼
각 private 메서드 → 템플릿 문자열 반환
```

---

## 진입점 메서드

```java
public String getSecurityTemplate(String securityType, String packageName)
```

| 파라미터 | 설명 | 기본값 |
|----------|------|--------|
| `securityType` | 생성할 템플릿 종류 (대소문자 무관) | 없음 (필수) |
| `packageName` | Java 파일 package 선언에 사용할 패키지명 | `egovframework.let.sample` |

**packageName 처리 로직:**
```java
String pkg = (packageName == null || packageName.isBlank())
             ? "egovframework.let.sample" : packageName;
```

---

## switch 분기 구조

```java
return switch (securityType.toLowerCase()) {
    case "webxmlfilter"       -> webXmlFilter();
    case "contextsecurity"    -> contextSecurity();
    case "securitymapper"     -> securityMapper();
    case "javaconfig"         -> javaConfig(pkg);
    case "userdetailsservice" -> userDetailsService(pkg);
    case "rolehierarchy"      -> roleHierarchy(pkg);
    case "loginpage"          -> loginPage();
    default                   -> unsupported(securityType);
};
```

---

## securityType 별 상세 동작

### 1. `webXmlFilter` → `webXmlFilter()`

- **파일 종류:** XML (web.xml 삽입용 스니펫)
- **packageName:** 미사용
- **출력 내용:**
  - `CharacterEncodingFilter` 설정 (UTF-8, Security 필터보다 앞에 위치)
  - `DelegatingFilterProxy` 설정 (`springSecurityFilterChain` 위임)
  - `contextConfigLocation` 파라미터 (`context-security.xml` 포함)
- **역할:** Spring Security 필터 체인의 진입점 등록

```
모든 HTTP 요청
    ↓
CharacterEncodingFilter (UTF-8 강제)
    ↓
DelegatingFilterProxy
    ↓
springSecurityFilterChain (Spring Context의 Bean에 위임)
```

---

### 2. `contextSecurity` → `contextSecurity()`

- **파일 종류:** XML (`context-security.xml` 전체)
- **packageName:** 미사용
- **출력 내용 (11개 Bean):**

| 번호 | Bean ID | 역할 |
|------|---------|------|
| 1 | `<http>` (static) | CSS/images/js 정적 자원 Security 제외 |
| 2 | `<http>` (main) | 메인 Security 설정 블록 |
| 3 | `authenticationManager` | 인증 관리자 |
| 4 | `egovAuthenticationProvider` | DB 기반 인증 Provider |
| 5 | `passwordEncoder` | BCryptPasswordEncoder |
| 6 | `egovSecurityFilter` | FilterSecurityInterceptor (DB 동적 URL 제어) |
| 7 | `egovSecurityMetadataSource` | COMTNROLEINFO 로드 (URL 패턴 → ROLE Map) |
| 8 | `accessDecisionManager` | RoleHierarchyVoter 포함 접근 결정 |
| 9 | `roleHierarchy` | ROLE 계층 구조 (XML 하드코딩) |
| 10 | `loginSuccessHandler` / `loginFailureHandler` | 로그인 결과 처리 |
| 11 | `accessDeniedHandler` | 접근 거부 처리 |

- **세션 설정:** `SessionCreationPolicy.IF_REQUIRED` (Session 기반 유지)
- **CSRF:** 활성화 (`<csrf/>`)

---

### 3. `securityMapper` → `securityMapper()`

- **파일 종류:** SQL (참조용)
- **packageName:** 미사용
- **출력 내용 (참조 SQL 5개):**

| SQL | 용도 |
|-----|------|
| URL 패턴 → 권한 매핑 조회 | `COMTNROLEINFO JOIN COMTNAUTHORROLERELATE` |
| ROLE 계층 조회 | `COMTNROLES_HIERARCHY` |
| 프로그램 목록 확인 | `COMTNPROGRMLIST` |
| 메뉴-프로그램 연결 확인 | `COMTNMENUINFO JOIN COMTNPROGRMLIST` |

- **목적:** `EgovReloadableFilterInvocationSecurityMetadataSource`가 서버 시작 시 자동 실행하는 쿼리 구조를 개발자가 이해하도록 참조 제공

---

### 4. `javaConfig` → `javaConfig(pkg)`

- **파일 종류:** Java (`EgovSecurityConfig.java`)
- **packageName:** `{pkg}.config` 로 package 선언에 삽입
- **출력 내용:**

```java
package {pkg}.config;

@Configuration
@EnableWebSecurity
public class EgovSecurityConfig {
    // 생성자 주입: userDetailsService, securityMetadataSource,
    //              accessDeniedHandler, roleHierarchy

    SecurityFilterChain filterChain(HttpSecurity http)  // 메인 Security 설정
    FilterSecurityInterceptor egovSecurityFilter()       // DB 동적 URL 접근 제어
    AccessDecisionManager accessDecisionManager()        // RoleHierarchyVoter 포함
    PasswordEncoder passwordEncoder()                    // BCrypt
    EgovAuthenticationSuccessHandler loginSuccessHandler()
    EgovAuthenticationFailureHandler loginFailureHandler()
}
```

- **주의:** `EgovSecurityMetadataSource`, `EgovAccessDeniedHandler`는 eGovFrame 런타임 의존 클래스 — 프로젝트별 별도 구현 필요

---

### 5. `userDetailsService` → `userDetailsService(pkg)`

- **파일 종류:** Java (`EgovUserDetailsServiceImpl.java`)
- **packageName:** `{pkg}.service` 로 package 선언에 삽입
- **출력 내용:**

```java
package {pkg}.service;

@Service
public class EgovUserDetailsServiceImpl implements UserDetailsService {

    // Step 1: COMTNEMPLYRINFO 조회
    //   WHERE EMPLYR_ID = ? AND EMPLYR_STTUS_CODE = 'ESC01'  (재직중만)
    //   → password, locked 추출

    // Step 2: COMTNEMPLYRSCRTYESTBS 조회
    //   WHERE SCRTY_DTRMN_TRGET_ID = ?
    //   → AUTHOR_CODE 목록 → GrantedAuthority 변환

    // Step 3: UserDetails 반환
    //   authorities 없을 경우 → ROLE_USER 기본 부여
}
```

- **연관 테이블:**

| 테이블 | 용도 |
|--------|------|
| `COMTNEMPLYRINFO` | 사용자 ID / 비밀번호 / 잠금여부 / 재직상태 |
| `COMTNEMPLYRSCRTYESTBS` | 사용자별 AUTHOR_CODE (ROLE_ADMIN, ROLE_USER 등) |

---

### 6. `roleHierarchy` → `roleHierarchy(pkg)`

- **파일 종류:** Java (`EgovRoleHierarchyConfig.java`)
- **packageName:** `{pkg}.config` 로 package 선언에 삽입
- **출력 내용:**

```java
package {pkg}.config;

@Configuration
public class EgovRoleHierarchyConfig {

    @Bean
    public RoleHierarchy roleHierarchy() {
        // COMTNROLES_HIERARCHY 동적 로드
        // SELECT PARNTS_ROLE, CHLDRN_ROLE FROM COMTNROLES_HIERARCHY
        // → "ROLE_USER > ROLE_ADMIN\n..." 문자열 구성
        // → RoleHierarchyImpl.setHierarchy() 적용
    }
}
```

- **실제 DB 데이터 (com DB):**

| PARNTS_ROLE | CHLDRN_ROLE | 의미 |
|-------------|-------------|------|
| `ROLE_USER` | `ROLE_ADMIN` | ROLE_USER가 ROLE_ADMIN 권한 자동 상속 |
| `IS_AUTHENTICATED_FULLY` | `ROLE_USER` | 완전 인증 사용자 → ROLE_USER 상속 |
| `IS_AUTHENTICATED_REMEMBERED` | `IS_AUTHENTICATED_FULLY` | Remember-Me → 완전인증 상속 |
| `IS_AUTHENTICATED_ANONYMOUSLY` | `IS_AUTHENTICATED_REMEMBERED` | 익명 → Remember-Me 상속 |
| `ROLE_ANONYMOUS` | `IS_AUTHENTICATED_ANONYMOUSLY` | 익명 Role 연결 |

---

### 7. `loginPage` → `loginPage()`

- **파일 종류:** JSP (`egovLoginUsr.jsp`)
- **packageName:** 미사용
- **출력 내용:**

```jsp
<form action="/uat/uia/actionLogin.do" method="post">
    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
    <!-- 로그인 실패 / 세션 만료 메시지 -->
    <input type="text"     name="j_username"/>
    <input type="password" name="j_password"/>
    <button type="submit">로그인</button>
</form>
```

- **CSRF 토큰:** Spring Security 기본 활성화 상태 필수 포함
- **action URL:** `context-security.xml`의 `login-processing-url`과 반드시 일치

---

### 8. `default` → `unsupported(securityType)`

- 잘못된 securityType 입력 시 사용 가능한 전체 목록을 안내 문자열로 반환

---

## packageName 사용 여부 요약

| securityType | packageName 사용 | 생성되는 package 선언 |
|---|:---:|---|
| `webXmlFilter` | 미사용 | — (XML) |
| `contextSecurity` | 미사용 | — (XML) |
| `securityMapper` | 미사용 | — (SQL) |
| `javaConfig` | **사용** | `{packageName}.config` |
| `userDetailsService` | **사용** | `{packageName}.service` |
| `roleHierarchy` | **사용** | `{packageName}.config` |
| `loginPage` | 미사용 | — (JSP) |

---

## 설계 원칙

| 원칙 | 내용 |
|------|------|
| 레거시 우선 | XML 방식(contextSecurity)이 공공 SI 기본 |
| DB 접근 없음 | 순수 문자열 반환 — JdbcTemplate 의존 없음 |
| 무상태 | 내부 상태(필드) 없음 — Thread-safe |
| 독립성 | 다른 Service/Tool과 결합 없음 |
| 대소문자 무관 | `securityType.toLowerCase()` 처리 |
