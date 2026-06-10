# SecurityTemplateTool 수정 계획서

> 작성일: 2026-06-09
> 근거 문서: `SecurityTemplateTool_구현상세_재검토.md`
> 목표: 생성 산출물이 대상 프로젝트에서 컴파일 및 런타임 동작 보장

---

## 1. 수정 범위 요약

| 번호 | 분류 | 대상 파일 | 문제 |
|---|---|---|---|
| Fix-1 | P1 | `egov43/java-config.java.tpl` | public class명 ≠ 저장 파일명 |
| Fix-2 | P1 | `common/logout-filter.java.tpl` | package 불일치 + jakarta 하드코딩 + GenericFilterBean import 오류 |
| Fix-3 | P1 | `common/login-policy-filter.java.tpl` | package 불일치 + jakarta 하드코딩 + GenericFilterBean import 오류 |
| Fix-4 | P1 | `common/login-filter.java.tpl` | jakarta 하드코딩 + GenericFilterBean import 오류 (package는 이미 수정됨) |
| Fix-5 | P1 | `common/web-xml-filter.tpl` | filter-class FQCN 하드코딩 (packageName 미반영) |
| Fix-6 | P1 | `egov43/context-security.xml.tpl` | jdbcMapClass FQCN 하드코딩 |
| Fix-7 | P1 | `egov50/context-security.xml.tpl` | jdbcMapClass FQCN 하드코딩 |
| Fix-8 | P1 | `egov43/user-details-service.java.tpl` | package 불일치 |
| Fix-9 | P1 | `SecurityFilePlanFactory.java` | `setup-war-43` XML+Java Config 동시 생성 → 분리 |
| Fix-10 | P2 | `SecurityFilePlanFactory.java` | 조합 키워드 + egovVersion 불일치 검증 없음 |
| Fix-11 | 테스트 | `SecurityTemplateRendererIntegrationTest.java` | Fix-1~8 대응 테스트 케이스 추가 |

---

## 2. 상세 수정 내용

---

### Fix-1. `egov43/java-config.java.tpl` — class명 통일

**현재:**
```java
public class EgovSecurityConfig extends WebSecurityConfigurerAdapter {
```

**수정 후:**
```java
public class EgovProjectSecurityConfig extends WebSecurityConfigurerAdapter {
```

**이유:**  
Factory 저장 경로가 `EgovProjectSecurityConfig.java`이므로 public class명이 달라 컴파일 실패.  
5.0 템플릿이 이미 `EgovProjectSecurityConfig`를 사용하므로 이름을 맞춤.

---

### Fix-2. `common/logout-filter.java.tpl` — 3가지 수정

**현재 (1번째 줄):**
```java
package ${packageName}.security.filter;
```
**수정 후:**
```java
package ${packageName}.sec.filter;
```

**현재 (import 블록):**
```java
import jakarta.servlet.FilterChain;
import jakarta.servlet.GenericFilterBean;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
```
**수정 후:**
```java
import org.springframework.web.filter.GenericFilterBean;
import ${javaxOrJakarta}.servlet.FilterChain;
import ${javaxOrJakarta}.servlet.ServletException;
import ${javaxOrJakarta}.servlet.http.HttpServletRequest;
import ${javaxOrJakarta}.servlet.http.HttpServletResponse;
import ${javaxOrJakarta}.servlet.http.HttpSession;
```

**현재 (doFilter 시그니처):**
```java
public void doFilter(jakarta.servlet.ServletRequest req,
                     jakarta.servlet.ServletResponse res,
                     FilterChain chain)
```
**수정 후:**
```java
public void doFilter(${javaxOrJakarta}.servlet.ServletRequest req,
                     ${javaxOrJakarta}.servlet.ServletResponse res,
                     FilterChain chain)
```

**이유:**
- package: Factory 경로 `sec/filter/`와 불일치
- `GenericFilterBean`: `jakarta.servlet`이 아닌 `org.springframework.web.filter` 소속
- `jakarta` 하드코딩: 4.3(javax)/5.0(jakarta) 분기 필요

---

### Fix-3. `common/login-policy-filter.java.tpl` — 3가지 수정

**현재 (1번째 줄):**
```java
package ${packageName}.security.filter;
```
**수정 후:**
```java
package ${packageName}.uat.uap.filter;
```

**import 블록 — Fix-2와 동일한 패턴으로 수정:**
```java
import org.springframework.web.filter.GenericFilterBean;
import ${javaxOrJakarta}.servlet.FilterChain;
import ${javaxOrJakarta}.servlet.ServletException;
import ${javaxOrJakarta}.servlet.http.HttpServletRequest;
import ${javaxOrJakarta}.servlet.http.HttpServletResponse;
```

**doFilter 시그니처 — Fix-2와 동일한 패턴으로 수정:**
```java
public void doFilter(${javaxOrJakarta}.servlet.ServletRequest req,
                     ${javaxOrJakarta}.servlet.ServletResponse res,
                     FilterChain chain)
```

**이유:**
- package: Factory 경로 `uat/uap/filter/`와 불일치
- `GenericFilterBean` / `jakarta` 하드코딩: Fix-2와 동일

---

### Fix-4. `common/login-filter.java.tpl` — 2가지 수정 (package는 이미 수정됨)

**import 블록 현재:**
```java
import jakarta.servlet.FilterChain;
import jakarta.servlet.GenericFilterBean;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
```
**수정 후:**
```java
import org.springframework.web.filter.GenericFilterBean;
import ${javaxOrJakarta}.servlet.FilterChain;
import ${javaxOrJakarta}.servlet.ServletException;
import ${javaxOrJakarta}.servlet.http.HttpServletRequest;
import ${javaxOrJakarta}.servlet.http.HttpServletResponse;
import ${javaxOrJakarta}.servlet.http.HttpSession;
```

**doFilter 시그니처 현재:**
```java
public void doFilter(jakarta.servlet.ServletRequest req,
                     jakarta.servlet.ServletResponse res,
                     FilterChain chain)
```
**수정 후:**
```java
public void doFilter(${javaxOrJakarta}.servlet.ServletRequest req,
                     ${javaxOrJakarta}.servlet.ServletResponse res,
                     FilterChain chain)
```

---

### Fix-5. `common/web-xml-filter.tpl` — filter-class 3곳 수정

**현재:**
```xml
<filter-class>
    egovframework.com.uat.uap.filter.EgovLoginPolicyFilter
</filter-class>

<filter-class>
    egovframework.com.sec.security.filter.EgovSpringSecurityLoginFilter
</filter-class>

<filter-class>
    egovframework.com.sec.security.filter.EgovSpringSecurityLogoutFilter
</filter-class>
```

**수정 후:**
```xml
<filter-class>
    ${packageName}.uat.uap.filter.EgovLoginPolicyFilter
</filter-class>

<filter-class>
    ${packageName}.sec.filter.EgovSpringSecurityLoginFilter
</filter-class>

<filter-class>
    ${packageName}.sec.filter.EgovSpringSecurityLogoutFilter
</filter-class>
```

**이유:**  
Factory 저장 경로는 `${packageName}` 기반인데 web.xml은 `egovframework.com.*` 하드코딩.  
사용자가 `packageName=egovframework.let.emp`로 생성하면 런타임에 필터 클래스 로드 실패.

---

### Fix-6. `egov43/context-security.xml.tpl` — jdbcMapClass 수정

**현재:**
```xml
jdbcMapClass="egovframework.let.uat.uia.service.impl.EgovSessionMapping"
```

**수정 후:**
```xml
jdbcMapClass="${packageName}.uat.uia.service.impl.EgovSessionMapping"
```

**주석도 함께 수정:**
```xml
<!--
⚠️ jdbcMapClass: sessionMapping 템플릿으로 생성한 클래스 경로와 일치해야 합니다.
   getSecurityTemplate("sessionMapping", packageName, "4.3") 로 생성된 클래스:
   {packageName}.uat.uia.service.impl.EgovSessionMapping
-->
```

---

### Fix-7. `egov50/context-security.xml.tpl` — jdbcMapClass 수정

**현재:**
```xml
<property name="jdbcMapClass"
    value="egovframework.com.uat.uia.service.impl.EgovSessionMapping"/>
```

**수정 후:**
```xml
<property name="jdbcMapClass"
    value="${packageName}.uat.uia.service.impl.EgovSessionMapping"/>
```

**주석도 함께 수정:**
```xml
<!-- ⚠️ jdbcMapClass: sessionMapping 템플릿 생성 클래스와 일치 필요
         {packageName}.uat.uia.service.impl.EgovSessionMapping -->
```

---

### Fix-8. `egov43/user-details-service.java.tpl` — package 수정

**현재 (1번째 줄):**
```java
package ${packageName}.service;
```

**수정 후:**
```java
package ${packageName}.sec.service.impl;
```

**이유:**  
Factory 저장 경로 `sec/service/impl/EgovUserDetailsServiceImpl.java`와 불일치.  
4.3 java-config import도 이미 `${packageName}.sec.service.impl`로 수정되어 있어 맞춤.

---

### Fix-9. `SecurityFilePlanFactory.java` — `setup-war-43` 분리

**현재:**
```java
private static List<String> war43Types() {
    return List.of(
            "webxmlfilter",
            "contextsecurity",   // XML <http> Security
            "javaconfig",        // @EnableWebSecurity Java Config ← 충돌
            "userdetailsservice",
            "rolehierarchy",
            "loginpage",
            "userdetailshelperxml");
}
```

**수정 후:**

`setup-war-43` → XML 방식 전용 (공공 SI 표준):
```java
private static List<String> war43XmlTypes() {
    // XML 방식: context-security.xml <http> 블록 기반
    // ⚠️ javaConfig(Java Config) 와 동시 사용 불가
    // ℹ️ userdetailsservice 포함 이유:
    //    egov43/context-security.xml의 egovAuthenticationProvider가
    //    ref="egovUserDetailsServiceImpl" 로 이 Bean을 직접 참조함.
    //    XML Security 방식에서도 반드시 Spring Bean으로 등록되어야 함.
    return List.of(
            "webxmlfilter",
            "contextsecurity",
            "userdetailsservice",
            "rolehierarchy",
            "sessionmapping",
            "loginpage",
            "userdetailshelperxml");
}

private static List<String> war43JavaTypes() {
    // Java Config 방식: WebSecurityConfigurerAdapter 기반
    // ⚠️ contextSecurity(XML <http>) 와 동시 사용 불가
    return List.of(
            "javaconfig",
            "userdetailsservice",
            "rolehierarchy",
            "successhandler",
            "failurehandler",
            "accessdeniedhandler",
            "loginpage");
}
```

`expand()` 조합 키워드 변경:

| 기존 키워드 | 변경 후 키워드 | 방식 | 파일 수 |
|---|---|---|---|
| `setup-war-43` | `setup-war-43-xml` | XML Security | 7 |
| (신규) | `setup-war-43-java` | Java Config | 7 |
| `setup-all-war-43` | `setup-all-war-43-xml` | XML + 필터 + securityMapper | 12 |
| (신규) | `setup-all-war-43-java` | Java Config + 핸들러 + 필터 + securityMapper | 12 |

> 파일 수 계산: setup-war-43-xml(7) + setup-filters(4) + securityMapper(1) = 12  
> 파일 수 계산: setup-war-43-java(7) + setup-filters(4) + securityMapper(1) = 12  
> 기존 `setup-war-43` 키워드는 `setup-war-43-xml`로 동작하도록 alias 처리하여 하위 호환 유지.

---

### Fix-10. `SecurityFilePlanFactory.java` — 조합 키워드 버전 검증

suffix 기반 검증은 `setup-war-43-xml`, `setup-all-war-43-xml` 등 `-xml`로 끝나는 키워드를
잡지 못하는 구멍이 생긴다. **명시적 키워드 세트** 방식으로 구현한다.

`expand()` 메서드 진입부에 추가:

```java
// 4.3 전용 조합 키워드 세트
private static final Set<String> V43_COMBOS = Set.of(
    "setup-war-43", "setup-war-43-xml", "setup-war-43-java",
    "setup-all-war-43", "setup-all-war-43-xml", "setup-all-war-43-java",
    "setup-handlers-43"
);

// 5.0 전용 조합 키워드 세트
private static final Set<String> V50_COMBOS = Set.of(
    "setup-war-50",
    "setup-all-war-50"
);
```

```java
// expand() 진입부에 추가
String lower = securityType.toLowerCase();
if (V43_COMBOS.contains(lower) && cap.jakarta()) {
    throw new IllegalArgumentException(
        lower + "은 egovVersion=4.3 전용입니다. " +
        "5.0 셋업에는 setup-war-50 또는 setup-all-war-50을 사용하세요.");
}
if (V50_COMBOS.contains(lower) && !cap.jakarta()) {
    throw new IllegalArgumentException(
        lower + "은 egovVersion=5.0 전용입니다. " +
        "4.3 셋업에는 setup-war-43-xml 또는 setup-all-war-43-xml을 사용하세요.");
}
```

---

### Fix-11. 테스트 케이스 추가

#### `SecurityTemplateRendererIntegrationTest.java` — 개별 템플릿 검증

| 테스트 메서드 | 검증 내용 |
|---|---|
| `javaConfig_43_classNameMatchesFileName` | `EgovProjectSecurityConfig` class명 확인 |
| `logoutFilter_packageDeclaration_matchesStoragePath` | `.sec.filter` package |
| `logoutFilter_43_noHardcodedJakarta` | `jakarta.servlet` 미포함 (4.3) |
| `logoutFilter_50_usesJakarta` | `jakarta.servlet` 포함 (5.0) |
| `loginPolicyFilter_packageDeclaration_matchesStoragePath` | `.uat.uap.filter` package |
| `loginPolicyFilter_43_noHardcodedJakarta` | `jakarta.servlet` 미포함 (4.3) |
| `loginFilter_43_noHardcodedJakarta` | `jakarta.servlet` 미포함 (4.3) |
| `userDetailsService_43_packageDeclaration_matchesStoragePath` | `.sec.service.impl` package |

#### `SecurityTemplateRendererIntegrationTest.java` — 생성물 간 연결 검증 (신규)

단순히 `${packageName}` 반영 여부만 확인하는 것으로는 부족하다.
**web.xml ↔ 필터 템플릿**, **context-security.xml ↔ sessionMapping 템플릿** 사이의
실제 FQCN 일치 여부를 교차 검증한다.

| 테스트 메서드 | 검증 방식 |
|---|---|
| `webXmlFilter_loginFilterClass_matchesLoginFilterPackage` | webXmlFilter 렌더링 결과에서 loginFilter FQCN 추출 → loginFilter 렌더링의 `package` 선언과 동일한지 비교 |
| `webXmlFilter_logoutFilterClass_matchesLogoutFilterPackage` | 동일 방식 — logoutFilter |
| `webXmlFilter_loginPolicyFilterClass_matchesLoginPolicyFilterPackage` | 동일 방식 — loginPolicyFilter |
| `contextSecurity_43_jdbcMapClass_matchesSessionMappingPackage` | context-security.xml(4.3)의 jdbcMapClass 값 → sessionMapping 렌더링의 `package` + 클래스명과 일치 확인 |
| `contextSecurity_50_jdbcMapClass_matchesSessionMappingPackage` | 동일 방식 — 5.0 |

검증 패턴 예시:

```
// webXmlFilter 결과에서 FQCN 추출
String webXml = renderer.render("webxmlfilter", spec);
String loginFilterFqcn = extractFilterClass(webXml, "egovSpringSecurityLoginFilter");
// → "egovframework.let.emp.sec.filter.EgovSpringSecurityLoginFilter"

// loginFilter 결과에서 package 선언 추출
String loginFilter = renderer.render("loginfilter", spec);
String loginFilterPkg = extractPackage(loginFilter);
// → "egovframework.let.emp.sec.filter"

assertThat(loginFilterFqcn).startsWith(loginFilterPkg);
assertThat(loginFilterFqcn).endsWith("EgovSpringSecurityLoginFilter");
```

#### `SecurityFilePlanFactoryTest.java` — 조합/버전 검증

| 테스트 메서드 | 검증 내용 |
|---|---|
| `expand_setupWar43Xml_doesNotContainJavaConfig` | XML 조합에 `javaconfig` 없음 |
| `expand_setupWar43Java_doesNotContainContextSecurity` | Java Config 조합에 `contextsecurity` 없음 |
| `expand_setupWar43Xml_containsSessionMapping` | XML 조합에 `sessionmapping` 포함 |
| `expand_setupWar43WithVersion50_throwsIllegalArgumentException` | 버전 불일치 예외 |
| `expand_setupWar43XmlWithVersion50_throwsIllegalArgumentException` | alias 키워드도 버전 검증 통과 |
| `expand_setupAllWar43XmlWithVersion50_throwsIllegalArgumentException` | all 변형도 버전 검증 통과 |
| `expand_setupWar50WithVersion43_throwsIllegalArgumentException` | 5.0 조합 + 4.3 버전 예외 |

---

## 3. 수정 순서

의존 관계를 고려한 수정 순서:

```
Step 1. 템플릿 파일 수정 (Fix-1 ~ Fix-8)
        → 독립적으로 병렬 수정 가능

Step 2. SecurityFilePlanFactory.java 수정 (Fix-9, Fix-10)
        → Fix-9 완료 후 Fix-10 추가

Step 3. SecurityTemplateRendererIntegrationTest.java 보강 (Fix-11)
        → Fix-1~10 완료 후 테스트 실행으로 검증

Step 4. SecurityTemplateTool.java @Tool description 업데이트
        반영 내용:
        · setup-war-43-xml / setup-war-43-java 신규 키워드 설명
        · setup-all-war-43-xml / setup-all-war-43-java 신규 키워드 설명
        · setup-war-43 (기존) → setup-war-43-xml alias임을 명시
        · setup-all-war-43 (기존) → setup-all-war-43-xml alias임을 명시
        · XML 방식과 Java Config 방식 동시 사용 불가 주의사항 재강조
        · 조합 키워드는 반드시 egovVersion과 일치해야 함 명시
          (예: setup-war-43-* → egovVersion=4.3, setup-war-50 → egovVersion=5.0)

Step 5. SecurityTemplateTool_사용예시.md 업데이트
        반영 내용:
        · 시나리오 7-1 (setup-all-war-43) → setup-all-war-43-xml 기준으로 수정
        · 시나리오 7-2 (setup-all-war-50) 유지
        · 시나리오 7-4 (신규): setup-war-43-java 예시 추가
        · 조합 키워드 요약 표 업데이트 (alias 정책 포함)
```

---

## 4. 수정 후 기대 결과

### 4-1. 생성 파일 정합성

| 생성 파일 | 수정 전 | 수정 후 |
|---|---|---|
| `EgovProjectSecurityConfig.java` (4.3) | 컴파일 실패 (class명 불일치) | 컴파일 통과 |
| `EgovSpringSecurityLoginFilter.java` (4.3) | 컴파일 실패 (jakarta import) | 컴파일 통과 |
| `EgovSpringSecurityLogoutFilter.java` (4.3) | 컴파일 실패 (jakarta import + 잘못된 package) | 컴파일 통과 |
| `EgovLoginPolicyFilter.java` (4.3) | 컴파일 실패 (jakarta import + 잘못된 package) | 컴파일 통과 |
| `web.xml.fragment` | 런타임 필터 로드 실패 | 올바른 FQCN |
| `context-security.xml` (4.3/5.0) | 런타임 EgovSessionMapping 로드 실패 | 올바른 FQCN |
| `EgovUserDetailsServiceImpl.java` | IDE package 오류 | 저장 경로 일치 |

### 4-2. 조합 키워드 구분

| 키워드 | 방식 | 포함 파일 |
|---|---|---|
| `setup-war-43-xml` | XML Security | webXmlFilter + contextSecurity + userDetailsService + roleHierarchy + sessionMapping + loginPage + userDetailsHelperXml (7개) |
| `setup-war-43-java` | Java Config | javaConfig + userDetailsService + roleHierarchy + successHandler + failureHandler + accessDeniedHandler + loginPage (7개) |
| `setup-war-50` | 변경 없음 | 동일 (5개) |
| `setup-all-war-43-xml` | XML + 필터 | setup-war-43-xml + setup-filters + securityMapper (12개) |
| `setup-all-war-43-java` | Java Config + 핸들러 + 필터 | setup-war-43-java + setup-filters + securityMapper (12개) |
| `setup-all-war-50` | 변경 없음 | 동일 (11개) |

> `setup-war-43` (기존) → `setup-war-43-xml` alias 유지 (하위 호환)

---

## 5. 수정 범위 외 사항

다음은 이번 수정 범위에 포함하지 않는다.

| 항목 | 이유 |
|---|---|
| `egov43/role-hierarchy.java.tpl` 중복 | 버전 분기 자체는 올바름. 단순 코드 중복이므로 별도 리팩터링 대상 |
| `HTMLTagFilter` filter-class (`egovframework.com.cmm.filter.HTMLTagFilter`) | RTE 공통 컴포넌트 FQCN이므로 프로젝트별 고정값이 맞음 |
| `springSecurityFilterChain` `DelegatingFilterProxy` filter-class | Spring 표준 클래스이므로 변경 불필요 |
