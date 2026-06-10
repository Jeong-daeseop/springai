# SecurityTemplateTool 기능 및 역할 상세 설명

## 개요

`SecurityTemplateTool`은 **eGovFrame 4.3 / 5.0 Spring Security 설정 파일 템플릿을 자동 생성**하는 MCP Tool입니다.
XML 방식(공공 SI 레거시 호환)과 Java Config 방식(신규) 모두 지원하며, 단일 파일 또는 조합 키워드로 여러 파일을 한 번에 생성합니다.

---

## 구성 레이어

```
SecurityTemplateTool (MCP Tool 진입점)
  └── SecurityTemplateService (오케스트레이터)
        ├── SecurityFilePlanFactory  — securityType → FilePlan 목록 조립 (조합 확장 + 중복 제거)
        ├── SecurityTemplateRenderer — 각 타입별 템플릿 문자열 렌더링
        ├── SecurityResultBuilder    — 저장 결과 포맷팅 (성공/실패/경고)
        └── EgovFileWriter           — outputPath 지정 시 파일 시스템에 직접 저장
```

---

## 파라미터

| 파라미터 | 필수 | 설명 | 예시 |
|----------|------|------|------|
| `securityType` | ✅ | 생성할 템플릿 타입 또는 조합 키워드 | `setup-all-war-50` |
| `packageName` | ✅ | Java 패키지명 | `egovframework.let.emp` |
| `egovVersion` | ✅ | `"4.3"` 또는 `"5.0"` (미입력 시 5.0) | `5.0` |
| `outputPath` | ⬜ | 파일 저장 경로 (미입력 시 문자열 반환) | `/Users/me/myproject` |
| `projectType` | ⬜ | `"war"` 또는 `"boot"` (미입력 시 war) | `war` |

---

## 핵심 아키텍처 (eGovFrame Security 흐름)

```
DelegatingFilterProxy
  → springSecurityFilterChain
    → EgovSpringSecurityLoginFilter (DB 직접 인증)
      → COMTNEMPLYRINFO 사용자 조회
      → SecurityContextHolder 설정 + 세션 저장
        → COMTNROLEINFO URL 패턴 매칭
          → 접근 제어 (허용 / 거부)
```

> 공공 SI 표준: **세션 기반** (STATELESS 아님)

---

## 지원 템플릿 타입 (단일)

### 레거시 XML 방식

| securityType | 생성 파일 | 설명 |
|---|---|---|
| `webXmlFilter` | `web.xml.fragment` | 6개 필터 체인 전체 선언 ⚠️ eGovFrame 4.3 WAR 전용 |
| `contextSecurity` | `context-security.xml` | 버전별 완전히 다른 구조 (아래 참고) |
| `securityMapper` | `security-mapper.sql` | URL-ROLE 매핑 참조 SQL |

#### contextSecurity 버전별 차이

| | eGovFrame 4.3 | eGovFrame 5.0 |
|---|---|---|
| XSD | `spring-beans-4.0.xsd` + `egov-security-4.3.0.xsd` | `spring-beans.xsd` 만 사용 |
| 내용 | `<egov-security:config>` + `<http>` + `<authentication-manager>` | `EgovSecurityConfig` Bean 1개 (32개 property POJO) |
| 역할 | Security 설정 + 인증 직접 처리 | 설정값 전달만 — RTE가 `SecurityFilterChain` 자동 구성 |
| 단독 사용 | 가능 | ❌ `javaConfig(5.0)`과 반드시 함께 사용 |

### Java Config 방식

| securityType | 생성 파일 | 설명 |
|---|---|---|
| `javaConfig` | `EgovProjectSecurityConfig.java` | 4.3: `WebSecurityConfigurerAdapter` 상속 / 5.0: `@Import(EgovSecurityConfiguration.class)` 진입점만 |
| `userDetailsService` | `EgovUserDetailsServiceImpl.java` | 4.3 전용 — 5.0은 `.md` 안내 파일 생성 |
| `roleHierarchy` | `EgovRoleHierarchyConfig.java` | 4.3/5.0 공통 — `COMTNROLES_HIERARCHY` 동적 로드 |

### 핸들러 구현체 (javaConfig 4.3 전용)

| securityType | 생성 파일 | 설명 |
|---|---|---|
| `successHandler` | `EgovAuthenticationSuccessHandler.java` | 로그인 성공 후 리다이렉트 |
| `failureHandler` | `EgovAuthenticationFailureHandler.java` | 로그인 실패 후 리다이렉트 |
| `accessDeniedHandler` | `EgovAccessDeniedHandler.java` | HTTP 403 처리 — 4.3(javax)/5.0(jakarta) 분기 |

### 필터/인증 구현체

| securityType | 생성 파일 | 설명 |
|---|---|---|
| `loginFilter` | `EgovSpringSecurityLoginFilter.java` | DB 직접 인증 — `formLogin()` 우회 |
| `logoutFilter` | `EgovSpringSecurityLogoutFilter.java` | 세션 `loginVO=null` 초기화 |
| `loginPolicyFilter` | `EgovLoginPolicyFilter.java` | 비밀번호 만료/계정 잠금 사전 체크 |
| `sessionMapping` | `EgovSessionMapping.java` | DB ResultSet → `EgovUserDetails` 변환 |

### 공통

| securityType | 생성 파일 | 설명 |
|---|---|---|
| `loginPage` | `egovLoginUsr.jsp` | CSRF 토큰 포함 표준 로그인 폼 |
| `userDetailsHelper` | `user-details-helper-example.md` | 컨트롤러 사용 예시 |
| `userDetailsHelperXml` | `context-egovuserdetailshelper.xml` | dummy/session/security Profile 분기 XML |

---

## 조합 키워드 (setup-*)

### 4.3 XML Security 방식 (공공 SI 표준)

| 키워드 | 파일 수 | 구성 |
|---|---|---|
| `setup-war-43-xml` | 9개 | webXmlFilter + contextSecurity + userDetailsService + sessionMapping + loginFilter + logoutFilter + loginPolicyFilter + loginPage + userDetailsHelperXml |
| `setup-all-war-43-xml` | 10개 | setup-war-43-xml + securityMapper |
| `setup-war-43` | — | `setup-war-43-xml` alias (하위 호환) |
| `setup-all-war-43` | — | `setup-all-war-43-xml` alias (하위 호환) |

### 4.3 Java Config 방식

| 키워드 | 파일 수 | 구성 |
|---|---|---|
| `setup-war-43-java` | 7개 | javaConfig + userDetailsService + roleHierarchy + successHandler + failureHandler + accessDeniedHandler + loginPage |
| `setup-all-war-43-java` | 12개 | setup-war-43-java + setup-filters + securityMapper |

### 5.0 방식

| 키워드 | 파일 수 | 구성 |
|---|---|---|
| `setup-war-50` | 5개 | contextSecurity + javaConfig + roleHierarchy + loginPage + userDetailsHelperXml |
| `setup-all-war-50` | 11개 | setup-war-50 + setup-filters + accessDeniedHandler + securityMapper |

### 부분 조합

| 키워드 | 파일 수 | 구성 |
|---|---|---|
| `setup-filters` | 4개 | loginFilter + logoutFilter + loginPolicyFilter + sessionMapping |
| `setup-handlers-43` | 3개 | successHandler + failureHandler + accessDeniedHandler |

---

## 버전 불일치 검증

버전 간 키워드 오용을 방지하기 위해 자동으로 오류를 반환합니다.

```
setup-war-43-* + egovVersion=5.0  → ❌ 예외 발생
setup-war-50   + egovVersion=4.3  → ❌ 예외 발생
```

---

## outputPath 동작 방식

| outputPath | 동작 |
|---|---|
| **미입력** | 템플릿 내용을 문자열로 반환 (하위 호환) |
| **입력** | 지정 경로 하위에 파일 직접 생성, 결과 보고서 반환 |

#### 저장 결과 예시
```
✅ Security 템플릿 저장 완료

저장 경로: /Users/me/myproject
생성 파일: 10개

  + src/main/webapp/WEB-INF/web.xml.fragment
  + src/main/resources/egovframework/spring/context-security.xml
  + src/main/java/egovframework/let/emp/sec/service/impl/EgovUserDetailsServiceImpl.java
  ...
```

---

## 생성 파일 경로 매핑

| securityType | 저장 경로 |
|---|---|
| `webxmlfilter` | `src/main/webapp/WEB-INF/web.xml.fragment` |
| `contextsecurity` | `src/main/resources/egovframework/spring/context-security.xml` |
| `securitymapper` | `src/main/resources/egovframework/sqlmap/security/security-mapper.sql` |
| `javaconfig` | `src/main/java/{pkg}/config/EgovProjectSecurityConfig.java` |
| `userdetailsservice` (4.3) | `src/main/java/{pkg}/sec/service/impl/EgovUserDetailsServiceImpl.java` |
| `userdetailsservice` (5.0) | `docs/security/user-details-service-notice.md` |
| `rolehierarchy` | `src/main/java/{pkg}/sec/config/EgovRoleHierarchyConfig.java` |
| `loginfilter` | `src/main/java/{pkg}/sec/filter/EgovSpringSecurityLoginFilter.java` |
| `logoutfilter` | `src/main/java/{pkg}/sec/filter/EgovSpringSecurityLogoutFilter.java` |
| `loginpolicyfilter` | `src/main/java/{pkg}/uat/uap/filter/EgovLoginPolicyFilter.java` |
| `sessionmapping` | `src/main/java/{pkg}/uat/uia/service/impl/EgovSessionMapping.java` |
| `successhandler` | `src/main/java/{pkg}/sec/handler/EgovAuthenticationSuccessHandler.java` |
| `failurehandler` | `src/main/java/{pkg}/sec/handler/EgovAuthenticationFailureHandler.java` |
| `accessdeniedhandler` | `src/main/java/{pkg}/sec/handler/EgovAccessDeniedHandler.java` |
| `loginpage` | `src/main/webapp/WEB-INF/jsp/egovframework/com/uat/uia/egovLoginUsr.jsp` |
| `userdetailshelperxml` | `src/main/resources/egovframework/spring/context-egovuserdetailshelper.xml` |

---

## 주요 주의사항

| 항목 | 내용 |
|---|---|
| contextSecurity + javaConfig 동시 사용 | ❌ `springSecurityFilterChain` Bean 충돌 — 둘 중 하나만 사용 |
| webXmlFilter | ⚠️ eGovFrame 4.3 WAR XML 전용 — 5.0에서는 `setup-war-50` 사용 |
| roleHierarchy (XML 조합) | ⚠️ `setup-war-43-xml`에 미포함 — context-security.xml이 이미 XML로 선언 |
| userDetailsService (5.0) | ⚠️ 4.3 전용 — 5.0은 RTE `EgovJdbcUserDetailsManager`가 자동 대체 |
| loginFilter web.xml 등록 | ⚠️ `DelegatingFilterProxy + targetBeanName` 방식 — Bean 직접 등록 불가 |
| loginFilter 순서 | ⚠️ `springSecurityFilterChain` **앞에** 반드시 선언 |
| EgovSecurityConfiguration 직접 선언 | ⚠️ XML `<bean>`으로 직접 선언 시 Spring Security 6.5 + Java 17에서 `BootstrapMethodError` 발생 |
| verifyPassword() | ⚠️ `loginFilter` 사용 시 SHA-256+Base64 또는 BCrypt 구현 필수 |

---

## 테스트 예시문

### 단일 파일 생성
```
eGovFrame 5.0 context-security.xml 생성해줘
securityType=contextSecurity, packageName=egovframework.let.emp, egovVersion=5.0
```
```
4.3 loginFilter 템플릿 생성해줘 (패키지: egovframework.let.board)
```

### 조합 생성 (문자열 반환)
```
eGovFrame 5.0 전체 보안 설정 파일 생성해줘
securityType=setup-all-war-50, packageName=egovframework.let.emp, egovVersion=5.0
```
```
4.3 XML Security 방식 전체 셋업 생성해줘
securityType=setup-all-war-43-xml, packageName=egovframework.let.emp, egovVersion=4.3
```

### 조합 생성 (파일 직접 저장)
```
eGovFrame 5.0 보안 파일을 프로젝트에 직접 저장해줘
securityType=setup-all-war-50
packageName=egovframework.let.emp
egovVersion=5.0
outputPath=/Users/me/myproject
```

---

## MenuTool / AuthTool과의 연계 워크플로우

```
Step 1. SecurityTemplateTool (setup-all-war-50 등)
        → Spring Security 기반 설정 파일 일괄 생성

Step 2. MenuTool.getMenuStructure()
        → 메뉴 트리 파악

Step 3. MenuTool.generateMenuInsertSql()
        → COMTNPROGRMLIST + COMTNMENUINFO 등록

Step 4. AuthTool.generateAuthInsertSql()
        → COMTNROLEINFO + COMTNAUTHORROLERELATE 등록
        (securityMapper가 이 테이블을 참조하여 URL 권한 반영)

Step 5. 서버 재기동 → 메뉴 노출 + 접근 제어 확인
```

---

## 관련 파일

| 파일 | 역할 |
|------|------|
| `tools/SecurityTemplateTool.java` | MCP Tool 진입점 (`@Tool` 어노테이션) |
| `service/SecurityTemplateService.java` | 비즈니스 로직 오케스트레이터 |
| `service/security/SecurityFilePlanFactory.java` | securityType → FilePlan 조립 (조합 확장 + 중복 제거) |
| `service/security/SecurityResultBuilder.java` | 저장 결과 포맷팅 |
| `service/initializr/EgovFileWriter.java` | 파일 시스템 저장 |
| `model/SecuritySpec.java` | 입력값 VO |
| `model/FilePlan.java` | 파일 경로 + 렌더러 VO |
| `model/GenerationReport.java` | 생성 결과 VO (created, warnings, errors) |
| `resources/templates/security/` | 버전별 템플릿 파일 (.tpl) |
