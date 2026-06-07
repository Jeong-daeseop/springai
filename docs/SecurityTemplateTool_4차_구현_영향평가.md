# SecurityTemplateTool 4차 구현 영향평가

작성일: 2026-05-24
목적: 실제 eGovFrame 4.3 프로젝트(enterprise-business-43 / common-all-43) 비교 분석 기반
     SecurityTemplateService.java / SecurityTemplateTool.java 누락·불일치 항목 정리

참조: /Users/jeongdaeseob/.claude/projects/-Users-jeongdaeseob-workspace-egov-myproject1/memory/project_security_comparison.md

---

## 실제 프로젝트 기준 비교

| 항목 | enterprise-business-43 (LET계열/XML) | common-all-43 (COM계열/JavaConfig) |
|---|---|---|
| 필터 설정 | web.xml | EgovWebApplicationInitializer.java |
| 패키지 계열 | `let.*` | `com.*` |
| DB 테이블 접두사 | `LETTN*` | `COMTN*` |
| 세션 타임아웃 | 600분 (10시간) | 60분 (1시간) |
| loginUrl | `/uat/uia/actionSecurityLogin.do` | `/uat/uia/egovLoginUsr.do` |
| logoutSuccessUrl | `/uat/uia/egovLoginUsr.do` | `/EgovContent.do` |
| defaultTargetUrl | `/uat/uia/actionMain.do` | `/EgovContent.do` |
| alwaysUseDefaultTargetUrl | 미설정 | `true` |
| CSRF | 미설정 | `false` (명시적 비활성화) |
| xframeOptions | 미설정 | `SAMEORIGIN` |
| xssProtection | 미설정 | `true` |
| HTTP Firewall | 활성화 (allowSemicolon=true) | 주석 처리됨 |

---

## [🔴 버그 1] DB 테이블명 하드코딩 — LET 계열 미지원

### 현황

`userDetailsService()`, `roleHierarchy()`, `securityMapper()` 전체가 `COMTN*` 테이블만 사용.

```java
// userDetailsService() — 현재
"FROM COMTNEMPLYRINFO WHERE EMPLYR_ID = ?"
"FROM COMTNEMPLYRSCRTYESTBS WHERE SCRTY_DTRMN_TRGET_ID = ?"

// roleHierarchy() — 현재
"SELECT PARNTS_ROLE, CHLDRN_ROLE FROM COMTNROLES_HIERARCHY"

// securityMapper() — 현재
"FROM COMTNROLEINFO ri JOIN COMTNAUTHORROLERELATE ar ..."
```

패키지가 `egovframework.let.*`이면 실제로는 `LETTN*` 테이블이어야 함:

| 역할 | COMTN계열 | LETTN계열 |
|---|---|---|
| 직원 보안 설정 | `COMTNEMPLYRSCRTYESTBS` | `LETTNEMPLYRSCRTYESTBS` |
| 역할 정보 | `COMTNROLEINFO` | `LETTNROLEINFO` |
| 역할 계층 | `COMTNROLES_HIERARCHY` | `LETTNROLES_HIERARCHY` |

### 영향

`packageName`이 `egovframework.let.*`인 경우 생성된 코드가 존재하지 않는 테이블을 조회
→ 서버 기동 시 또는 로그인 시 SQL 오류 발생.

### 수정 방향

`getSecurityTemplate()` 또는 각 메서드에서 `packageName`의 `.let.` 포함 여부로 분기:

```java
// 예시
boolean isLet = pkg.contains(".let.");
String emplyrScrtyTable = isLet ? "LETTNEMPLYRSCRTYESTBS" : "COMTNEMPLYRSCRTYESTBS";
String roleInfoTable    = isLet ? "LETTNROLEINFO"         : "COMTNROLEINFO";
String rolesHierTable   = isLet ? "LETTNROLES_HIERARCHY"  : "COMTNROLES_HIERARCHY";
```

### 변경 범위

| 위치 | 변경 내용 |
|---|---|
| `getSecurityTemplate()` | `isLet` 플래그 계산 후 하위 메서드에 전달 |
| `userDetailsService(pkg)` → `userDetailsService(pkg, isLet)` | `COMTNEMPLYRSCRTYESTBS` → 동적 테이블명 |
| `roleHierarchy(pkg, ver)` → `roleHierarchy(pkg, ver, isLet)` | `COMTNROLES_HIERARCHY` → 동적 테이블명 |
| `securityMapper()` | `COMTNROLEINFO` / `COMTNAUTHORROLERELATE` → 동적 테이블명 |

---

## [🔴 버그 2] loginProcessingUrl 실제 프로젝트와 불일치

### 현황

```java
// 현재 생성 (contextSecurity43, javaConfig43, javaConfig50)
loginProcessingUrl("/uat/uia/actionLogin.do")

// enterprise-business-43 실제
/uat/uia/actionSecurityLogin.do

// common-all-43 실제
/uat/uia/egovLoginUsr.do  (loginPage URL과 동일 — POST 처리)
```

현재 생성 URL이 어느 실제 프로젝트와도 일치하지 않음.

### 영향

생성된 파일을 그대로 사용하면 로그인 처리 URL이 실제 컨트롤러와 불일치
→ 로그인 폼 submit 시 404 또는 Spring MVC 컨트롤러로 라우팅 오류.

### 수정 방향

최소: 주석으로 안내 추가

```xml
<!-- ⚠️ loginProcessingUrl 프로젝트별 URL 확인 필요
     enterprise-business-43: /uat/uia/actionSecurityLogin.do
     common-all-43:          /uat/uia/egovLoginUsr.do (POST)
     현재값은 eGovFrame 관례 기반 예시 — 실제 컨트롤러 URL로 변경 필요 -->
login-processing-url="/uat/uia/actionLogin.do"
```

권장: `securityType` 파라미터 추가 (`let` / `com`) 또는 별도 파라미터로 URL 주입.

### 변경 범위

| 위치 | 변경 내용 |
|---|---|
| `contextSecurity43()` login-processing-url | 주석 안내 추가 |
| `javaConfig43()` `.loginProcessingUrl()` | 주석 안내 추가 |
| `javaConfig50()` `.loginProcessingUrl()` | 주석 안내 추가 |
| `loginPage()` form action | 주석 안내 추가 |

---

## [🟡 이슈 3] 보안 헤더(headers) 설정 없음

### 현황

`contextSecurity43()`에 `<headers>` 블록 없음.
common-all-43 기준 실제 설정:

```xml
<headers>
    <frame-options policy="SAMEORIGIN"/>   <!-- 클릭재킹 방지 -->
    <xss-protection enabled="true" block="true"/>
</headers>
```

### 영향

SAMEORIGIN, XSS 방어 헤더 미전송 → 공공 SI 보안 감리 지적 가능성.

### 수정 방향

`contextSecurity43()` `<http>` 블록 내 추가:
```xml
<!-- 보안 헤더 -->
<headers>
    <frame-options policy="SAMEORIGIN"/>
    <xss-protection enabled="true" block="true"/>
</headers>
```

---

## [🟡 이슈 4] web.xml session-timeout 미설정

### 현황

`webXmlFilter()`에 세션 타임아웃 설정 없음.

### 수정 방향

```xml
<!-- 세션 타임아웃 설정 (분 단위) -->
<session-config>
    <session-timeout>60</session-timeout>
    <!-- enterprise-business-43: 600 / common-all-43: 60 -->
</session-config>
```

---

## [🟡 이슈 5] EgovSessionMapping(jdbcMapClass) 누락

### 현황

실제 프로젝트에서 세션 매핑에 사용되는 클래스 설정이 `contextSecurity43()`에 없음.

enterprise-business-43:
```xml
<beans:property name="jdbcMapClass"
    value="egovframework.let.uat.uia.service.impl.EgovSessionMapping"/>
```

common-all-43:
```xml
<beans:property name="jdbcMapClass"
    value="egovframework.com.sec.security.common.EgovSessionMapping"/>
```

### 수정 방향

`egovSecurityMetadataSource` Bean에 주석으로 안내:
```xml
<!-- jdbcMapClass: 세션 매핑 클래스 (프로젝트별 구현체로 설정)
     LET 계열: egovframework.let.uat.uia.service.impl.EgovSessionMapping
     COM 계열: egovframework.com.sec.security.common.EgovSessionMapping
<beans:property name="jdbcMapClass" value="egovframework.let.uat.uia.service.impl.EgovSessionMapping"/>
-->
```

---

## [🟡 이슈 6] HTTP Firewall allowSemicolon 설정 없음

### 현황

enterprise-business-43의 실제 설정이 누락:

```xml
<beans:bean id="httpFirewall"
    class="org.springframework.security.web.firewall.StrictHttpFirewall">
    <beans:property name="allowSemicolon" value="true"/>
</beans:bean>
<http-firewall ref="httpFirewall"/>
```

### 영향

세미콜론(`;`) 포함 URL 요청 시 Spring Security가 400 차단.
eGovFrame URL에 세미콜론이 포함된 경우 서비스 오류.

### 수정 방향

`contextSecurity43()` 끝 부분에 주석 형태로 추가:
```xml
<!-- HTTP Firewall 설정 (URL에 세미콜론 포함 시 활성화)
<beans:bean id="httpFirewall"
    class="org.springframework.security.web.firewall.StrictHttpFirewall">
    <beans:property name="allowSemicolon" value="true"/>
</beans:bean>
<http-firewall ref="httpFirewall"/>
-->
```

---

## [🔵 보완 7] 로그인 실패 횟수/계정 잠금 고급 처리 없음

### 현황

`userDetailsService()`는 `LOCK_AT = 'Y'` 단순 체크만.
common-all-43은 실패 횟수 카운트(`processLoginIncorrect`) + `getLockCount()` 자동 잠금 지원.

### 수정 방향

주석으로 확장 포인트 안내:
```java
// 로그인 실패 횟수 관리가 필요한 경우:
// 1. COMTNEMPLYRINFO.LOGIN_FAIL_CNT 컬럼 추가
// 2. EgovAuthenticationFailureHandler에서 실패 시 카운트 증가
// 3. loadUserByUsername에서 실패 횟수 >= 임계값이면 accountLocked(true)
```

---

## [🔵 보완 8] alwaysUseDefaultTargetUrl 미설정

### 현황

common-all-43에서는 로그인 성공 시 항상 defaultTargetUrl로 이동(`alwaysUseDefaultTargetUrl=true`).
현재 `javaConfig43()`은 `.defaultSuccessUrl("/index.jsp")` (alwaysUse 기본값 false).

### 수정 방향

```java
// alwaysUseDefaultTargetUrl이 false(기본값)이면
// 로그인 전 접근하려던 URL로 리다이렉트됨 (SavedRequest 우선)
// 항상 메인으로 이동하려면:
.defaultSuccessUrl("/index.jsp", true)   // alwaysUse=true
```

주석으로 안내 추가.

---

## 구현 순서 (의존성 기반)

```
[1단계] DB 테이블 LET/COM 계열 분기 (버그 1 — Critical)
        userDetailsService / roleHierarchy / securityMapper 영향
        ↓

[2단계] loginProcessingUrl 주석 안내 (버그 2 — Critical)
        contextSecurity43 / javaConfig43 / javaConfig50 / loginPage 4곳
        ↓

[3단계] 보안 헤더 추가 (이슈 3)
        contextSecurity43 <headers> 블록
        ↓

[4단계] session-timeout 추가 (이슈 4)
        webXmlFilter() <session-config>
        ↓

[5단계] EgovSessionMapping 주석 안내 (이슈 5)
        contextSecurity43 egovSecurityMetadataSource Bean
        ↓

[6단계] HTTP Firewall 주석 안내 (이슈 6)
        contextSecurity43 끝 부분
        ↓

[7단계] 로그인 실패 횟수/alwaysUseDefaultTargetUrl 주석 안내 (보완 7, 8)
        userDetailsService / javaConfig43 / javaConfig50
```

---

## 변경 파일 및 범위

| 파일 | 변경 위치 | 변경 규모 |
|---|---|---|
| `SecurityTemplateService.java` | `getSecurityTemplate()` isLet 분기 | 소 |
| `SecurityTemplateService.java` | `userDetailsService()` 테이블명 동적 처리 | 소 |
| `SecurityTemplateService.java` | `roleHierarchy()` 테이블명 동적 처리 | 소 |
| `SecurityTemplateService.java` | `securityMapper()` 테이블명 동적 처리 | 소 |
| `SecurityTemplateService.java` | `contextSecurity43()` headers/firewall/sessionMapping 주석 | 소 |
| `SecurityTemplateService.java` | `contextSecurity43()/javaConfig43()/javaConfig50()/loginPage()` loginProcessingUrl 주석 | 극소 |
| `SecurityTemplateService.java` | `webXmlFilter()` session-config 추가 | 극소 |

---

## 최종 결정 사항

| 항목 | 결정 | 완료 |
|---|---|---|
| LET/COM 계열 DB 테이블 분기 | ⏳ **구현 예정** (1단계) | - |
| loginProcessingUrl 주석 안내 | ⏳ **구현 예정** (2단계) | - |
| 보안 헤더(headers) 추가 | ⏳ **구현 예정** (3단계) | - |
| session-timeout 추가 | ⏳ **구현 예정** (4단계) | - |
| EgovSessionMapping 주석 안내 | ⏳ **구현 예정** (5단계) | - |
| HTTP Firewall 주석 안내 | ⏳ **구현 예정** (6단계) | - |
| 로그인 실패 횟수/alwaysUse 주석 | ⏳ **구현 예정** (7단계) | - |
