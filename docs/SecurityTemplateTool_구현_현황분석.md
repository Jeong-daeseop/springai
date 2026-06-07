# SecurityTemplateTool 구현 현황 분석

작성일: 2026-05-24
목적: SecurityTemplateTool.java / SecurityTemplateService.java 전체 구조 및 현황 분석

---

## 1. 전체 구조

```
SecurityTemplateTool.java          ← MCP Tool 진입점 (@Tool 1개)
    └── SecurityTemplateService.java   ← 실제 템플릿 생성 로직
            ├── switch(securityType)   ← 16개 case
            ├── 섹션 1: 레거시 XML 방식 (3개 메서드)
            ├── 섹션 2: Java Config 방식 (5개 메서드)
            ├── 섹션 3: 핸들러 구현체 (3개 메서드)
            ├── 섹션 4: bopr 필터 구현체 (6개 메서드)
            └── unsupported()          ← 전체 securityType 안내
```

---

## 2. 지원 securityType 전체 목록 (16개)

| securityType | 메서드 | 반환 파일 | 버전 |
|---|---|---|---|
| `webxmlfilter` | `webXmlFilter()` | web.xml 6-filter 체인 | 공통 |
| `contextsecurity` | `contextSecurity(ver)` | context-security.xml | 4.3 / 5.0 분기 |
| `securitymapper` | `securityMapper()` | URL-ROLE SQL | 공통 |
| `javaconfig` | `javaConfig43(pkg)` / `javaConfig50(pkg)` | 보안 Config Java | 4.3 / 5.0 완전 다름 |
| `userdetailsservice` | `userDetailsService(pkg, ver)` | EgovUserDetailsServiceImpl.java | 4.3만 코드 반환, 5.0은 안내 |
| `rolehierarchy` | `roleHierarchy(pkg, ver)` | EgovRoleHierarchyConfig.java | 4.3 / 5.0 분기 |
| `loginpage` | `loginPage()` | egovLoginUsr.jsp | 공통 |
| `successhandler` | `successHandler(pkg, ver)` | EgovAuthenticationSuccessHandler.java | javax/jakarta 분기 |
| `failurehandler` | `failureHandler(pkg, ver)` | EgovAuthenticationFailureHandler.java | javax/jakarta 분기 |
| `accessdeniedhandler` | `accessDeniedHandler(pkg, ver)` | EgovAccessDeniedHandler.java | javax/jakarta 분기 |
| `loginfilter` | `loginFilter(pkg)` | EgovSpringSecurityLoginFilter.java | jakarta (5.0) |
| `logoutfilter` | `logoutFilter(pkg)` | EgovSpringSecurityLogoutFilter.java | jakarta (5.0) |
| `loginpolicyfilter` | `loginPolicyFilter(pkg)` | EgovLoginPolicyFilter.java | jakarta (5.0) |
| `sessionmapping` | `sessionMapping(pkg)` | EgovSessionMapping.java | 5.0 |
| `userdetailshelper` | `userDetailsHelper(pkg)` | EgovUserDetailsHelper 사용 예시 | 공통 |
| `userdetailshelperxml` | `userDetailsHelperXml(pkg)` | context-egovuserdetailshelper.xml | 공통 |

---

## 3. 파라미터 처리

| 파라미터 | 기본값 | 적용 범위 |
|---|---|---|
| `packageName` | `egovframework.let.sample` | Java 파일 생성 메서드 (pkg 변수) |
| `egovVersion` | `5.0` | 버전 분기 메서드 (ver 변수) |
| `securityType` | 없음 (필수) | switch 라우팅, 대소문자 무관 (.toLowerCase()) |

---

## 4. 버전 분기 방식

```
ver.startsWith("4")  →  4.3 계열
그 외               →  5.0 계열 (기본값)
```

### 4.3 / 5.0 구조 차이

| 항목 | 4.3 | 5.0 |
|---|---|---|
| `contextSecurity` | egov-security 네임스페이스 + `<egov-security:config>` + Bean 11개 | spring-beans.xsd만 + `EgovSecurityConfig` POJO Bean 1개 32개 property |
| `javaConfig` | `WebSecurityConfigurerAdapter` 상속, SecurityFilterChain 직접 구현 | `@Import(EgovSecurityConfiguration.class)` 진입점만 (내용 없음) |
| `userDetailsService` | `EgovUserDetailsServiceImpl.java` 코드 반환 | "불필요" 안내 텍스트 반환 |
| `successHandler` | javax 임포트 | jakarta 임포트 |
| `roleHierarchy` | `impl.setHierarchy()` 인스턴스 방식 | `RoleHierarchyImpl.fromHierarchy()` static 팩토리 |

---

## 5. 핵심 아키텍처 흐름 (5.0 기준)

```
HTTP 요청
  ↓
① CharacterEncodingFilter (UTF-8)
  ↓
② HTMLTagFilter (XSS 방어)
  ↓
③ LoginPolicyFilter (비밀번호 만료/계정 잠금 체크, /actionLogin.do만)
  ↓
④ EgovSpringSecurityLoginFilter (DB 직접 인증 → SecurityContext 설정)
  ↓
⑤ springSecurityFilterChain (DelegatingFilterProxy → EgovSecurityConfiguration)
  │   context-security.xml EgovSecurityConfig Bean 읽어 자동 구성:
  │   - URL 접근제어 (regex 매처, DB 동적 로드)
  │   - 세션 관리 (최대 1세션)
  │   - 보안 헤더 (X-Frame-Options, X-XSS-Protection, sniff)
  │   - CSRF 비활성화
  ↓
⑥ EgovSpringSecurityLogoutFilter (loginVO=null → /egov_security_logout, /actionLogout.do만)
```

---

## 6. 주의사항 / 골격만 제공하는 부분

### loginFilter — verifyPassword() 미구현 (의도적)
```java
private boolean verifyPassword(...) {
    throw new UnsupportedOperationException("verifyPassword() 구현 필요");
}
```
→ 프로젝트마다 암호화 방식이 다르므로 (SHA-256+Base64 or BCrypt) 골격만 제공, 직접 구현 필요

### loginPolicyFilter — DB 조회 로직 주석 처리
→ 계정 정책 테이블이 프로젝트별로 상이하므로 TODO 주석만 제공

### sessionMapping — LoginVO 주석 처리
```java
// LoginVO loginVO = new LoginVO();  // ⚠️ 프로젝트 VO 클래스로 교체
return new EgovUserDetails(userId, password, true, null);  // null 자리에 loginVO 교체 필요
```
→ 프로젝트 LoginVO 클래스명이 다르므로 골격만 제공

### contextSecurity43() — dataSource alias 미적용
→ `dataSource` 고정값 사용 중. 5.0의 `egov.dataSource` alias (순환참조 방지) 패턴이 4.3에는 미적용

---

## 7. 현재 구조의 특이사항

| 항목 | 내용 |
|---|---|
| `webxmlfilter` ver 미사용 | ver 파라미터 없음 — 5.0 전용 필터 클래스명(bopr) 하드코딩 |
| `loginfilter` / `logoutfilter` / `loginpolicyfilter` / `sessionmapping` | ver 파라미터 없이 항상 jakarta(5.0) 기준 생성 |
| `userdetailshelperxml` pkg 미사용 | pkg 파라미터를 받지만 XML 내용에서 실제로 사용하지 않음 (eGovFrame COM 패키지 고정) |

---

## 8. 구현 이력

| 차수 | 주요 내용 |
|---|---|
| 1~4차 | 초기 구현 (contextSecurity43 / javaConfig43 / 핸들러 기본 구조) |
| 5차 | XSD 스키마 수정 (spring-beans-4.0.xsd / egov-security-4.3.0.xsd), egov-security:config 추가 |
| 6차 | bopr 비교 분석 — webXmlFilter 6-filter 추가, loginfilter/logoutfilter/sessionmapping 등 신규 6개 |
| 7차 | contextSecurity50() / javaConfig50() 전면 재작성 (egov-security 네임스페이스 제거, @Import 패턴) |
