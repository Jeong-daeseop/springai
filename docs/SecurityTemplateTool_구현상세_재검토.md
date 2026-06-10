# SecurityTemplateTool 구현상세 재검토

> 작성일: 2026-06-09  
> 대상: `springai-mcp` / `SecurityTemplateTool` 구현 완료분  
> 검토 관점: 실제 Tool 호출 후 생성물이 컴파일/동작 가능한지, 버전/조합 정책이 설명과 일치하는지, 테스트가 이를 잡는지 확인

---

## 1. 최종 판정

아직 “제대로 구현 완료”로 보기는 어렵다.

구조 분리, `.tpl` 외부화, `outputPath` 저장, 5.0 `userDetailsService` 안내문 `.md` 저장 같은 방향은 좋아졌다.
하지만 실제 생성물을 대상 프로젝트에 넣었을 때 컴파일 또는 런타임이 깨질 수 있는 P1 이슈가 남아 있다.

현재 상태는 다음처럼 판단하는 것이 적절하다.

```text
구조 리팩터링 골격: 완료에 가까움
실제 사용 가능한 생성기 품질: 미완성
```

---

## 2. 검증 결과

### 2-1. 관련 테스트 재실행

```bash
./gradlew test --rerun-tasks \
  --tests 'com.krdevops.springai.service.security.SecurityTemplateRendererIntegrationTest' \
  --tests 'com.krdevops.springai.service.security.SecurityFilePlanFactoryTest' \
  --tests 'com.krdevops.springai.service.SecurityTemplateServiceTest'
```

결과:

```text
BUILD SUCCESSFUL
```

### 2-2. 전체 테스트 재실행

```bash
./gradlew test --rerun-tasks
```

결과:

```text
BUILD SUCCESSFUL
```

컴파일 경고:

```text
GenericJackson2JsonRedisSerializer deprecated/removal warning 2건
```

해당 경고는 `SecurityTemplateTool` 구현과 직접 관련은 없다.

### 2-3. 테스트 통과에 대한 해석

테스트는 통과하지만, 현재 테스트가 실제 생성물 정합성을 충분히 검증하지 못한다.

특히 다음 항목은 테스트에서 놓치고 있다.

| 검증 항목 | 현재 테스트 커버 |
|---|---:|
| 모든 Java 템플릿의 저장 경로와 package 선언 일치 | 부분 |
| 파일명과 public class명 일치 | X |
| `web.xml`의 filter-class와 실제 생성 필터 FQCN 일치 | X |
| `context-security.xml`의 `jdbcMapClass`와 생성 클래스 FQCN 일치 | X |
| 4.3 결과물에 Jakarta API 혼입 여부 | X |
| `setup-war-43`에서 XML Security와 Java Config 충돌 여부 | X |
| 조합 키워드와 `egovVersion` 불일치 차단 | X |

---

## 3. 주요 Findings

### [P1] 4.3 `javaConfig`는 파일명과 public class명이 달라 컴파일 실패

#### 근거

`SecurityFilePlanFactory`는 `javaconfig`를 다음 경로로 저장한다.

```java
case "javaconfig" -> FilePlan.of(
        "src/main/java/" + pkg + "/config/EgovProjectSecurityConfig.java",
        FilePlan.FileKind.SOURCE,
        () -> renderer.render(type, spec));
```

하지만 4.3 템플릿의 public class명은 다르다.

```java
// templates/security/egov43/java-config.java.tpl
public class EgovSecurityConfig extends WebSecurityConfigurerAdapter {
```

#### 영향

Java public class 규칙상 다음 조합은 컴파일 오류가 난다.

```text
파일명: EgovProjectSecurityConfig.java
클래스: public class EgovSecurityConfig
```

#### 권장 수정

둘 중 하나로 통일한다.

```text
안 1:
  파일명: EgovProjectSecurityConfig.java
  클래스: public class EgovProjectSecurityConfig

안 2:
  파일명: EgovSecurityConfig.java
  클래스: public class EgovSecurityConfig
```

5.0 템플릿은 `EgovProjectSecurityConfig`를 사용하므로,
버전 공통 파일명을 유지하려면 4.3 클래스명을 `EgovProjectSecurityConfig`로 바꾸는 쪽이 단순하다.

---

### [P1] 생성된 필터 템플릿 import가 잘못되어 컴파일되지 않음

#### 근거

공통 필터 템플릿들이 `jakarta.servlet.GenericFilterBean`을 import한다.

```java
// common/login-filter.java.tpl
import jakarta.servlet.FilterChain;
import jakarta.servlet.GenericFilterBean;
import jakarta.servlet.ServletException;
```

```java
// common/logout-filter.java.tpl
import jakarta.servlet.FilterChain;
import jakarta.servlet.GenericFilterBean;
import jakarta.servlet.ServletException;
```

```java
// common/login-policy-filter.java.tpl
import jakarta.servlet.FilterChain;
import jakarta.servlet.GenericFilterBean;
import jakarta.servlet.ServletException;
```

#### 영향

`GenericFilterBean`은 일반적으로 Spring의 다음 클래스다.

```java
org.springframework.web.filter.GenericFilterBean
```

따라서 현재 생성된 Java 소스는 5.0에서도 컴파일 실패 가능성이 높다.

또한 공통 필터 템플릿이 `jakarta.servlet`를 하드코딩하므로,
`setup-all-war-43`으로 4.3 프로젝트에 생성하면 4.3/Servlet 4 계열에서 컴파일되지 않는다.

#### 권장 수정

`GenericFilterBean`은 Spring import로 변경한다.

```java
import org.springframework.web.filter.GenericFilterBean;
```

Servlet API는 버전별로 분기한다.

```java
import ${javaxOrJakarta}.servlet.FilterChain;
import ${javaxOrJakarta}.servlet.ServletException;
import ${javaxOrJakarta}.servlet.http.HttpServletRequest;
import ${javaxOrJakarta}.servlet.http.HttpServletResponse;
```

메서드 시그니처도 하드코딩 대신 변수 기반으로 맞춘다.

```java
public void doFilter(${javaxOrJakarta}.servlet.ServletRequest req,
                     ${javaxOrJakarta}.servlet.ServletResponse res,
                     FilterChain chain)
```

---

### [P1] `web.xml` 필터 class와 실제 생성 필터 package가 서로 다름

#### 근거

`web-xml-filter.tpl`은 다음 클래스를 하드코딩한다.

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

하지만 Factory는 필터를 사용자가 입력한 `packageName` 아래에 생성한다.

```java
case "loginfilter" -> FilePlan.of(
        "src/main/java/" + pkg + "/sec/filter/EgovSpringSecurityLoginFilter.java",
        FilePlan.FileKind.SOURCE,
        () -> renderer.render(type, spec));

case "logoutfilter" -> FilePlan.of(
        "src/main/java/" + pkg + "/sec/filter/EgovSpringSecurityLogoutFilter.java",
        FilePlan.FileKind.SOURCE,
        () -> renderer.render(type, spec));

case "loginpolicyfilter" -> FilePlan.of(
        "src/main/java/" + pkg + "/uat/uap/filter/EgovLoginPolicyFilter.java",
        FilePlan.FileKind.SOURCE,
        () -> renderer.render(type, spec));
```

#### 영향

예를 들어 다음 호출을 하면:

```text
packageName = egovframework.let.emp
securityType = setup-all-war-43
```

실제 생성 필터는 다음 경로에 놓인다.

```text
egovframework.let.emp.sec.filter.EgovSpringSecurityLoginFilter
egovframework.let.emp.sec.filter.EgovSpringSecurityLogoutFilter
egovframework.let.emp.uat.uap.filter.EgovLoginPolicyFilter
```

하지만 `web.xml`은 `egovframework.com...` 클래스를 찾는다.
런타임에 필터 클래스를 로드하지 못할 수 있다.

#### 권장 수정

`web-xml-filter.tpl`의 filter-class를 `${packageName}` 기반으로 치환한다.

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

단, 필터 템플릿 package도 Factory 저장 경로와 먼저 일치시켜야 한다.

---

### [P1] `context-security.xml`의 `jdbcMapClass`가 생성된 `EgovSessionMapping`과 연결되지 않음

#### 근거

4.3 `context-security.xml`은 `jdbcMapClass`를 하드코딩한다.

```xml
<egov-security:config
    ...
    jdbcMapClass="egovframework.let.uat.uia.service.impl.EgovSessionMapping"
    requestMatcherType="regex"/>
```

5.0 `context-security.xml`도 하드코딩한다.

```xml
<property name="jdbcMapClass"
    value="egovframework.com.uat.uia.service.impl.EgovSessionMapping"/>
```

하지만 `sessionmapping` 생성 경로는 다음과 같다.

```java
case "sessionmapping" -> FilePlan.of(
        "src/main/java/" + pkg + "/uat/uia/service/impl/EgovSessionMapping.java",
        FilePlan.FileKind.SOURCE,
        () -> renderer.render(type, spec));
```

#### 영향

`packageName`이 `egovframework.let.emp`이면 실제 생성 클래스는 다음이다.

```text
egovframework.let.emp.uat.uia.service.impl.EgovSessionMapping
```

하지만 XML은 다음을 참조한다.

```text
4.3: egovframework.let.uat.uia.service.impl.EgovSessionMapping
5.0: egovframework.com.uat.uia.service.impl.EgovSessionMapping
```

조합 생성 결과가 서로 연결되지 않는다.

#### 권장 수정

4.3/5.0 템플릿 모두 `${packageName}`을 사용한다.

```xml
jdbcMapClass="${packageName}.uat.uia.service.impl.EgovSessionMapping"
```

```xml
<property name="jdbcMapClass"
    value="${packageName}.uat.uia.service.impl.EgovSessionMapping"/>
```

---

### [P1] `setup-war-43`이 금지한다고 설명한 XML Security + Java Config를 동시에 생성함

#### 근거

Tool 설명은 동시 선언 불가라고 안내한다.

```text
주의: contextSecurity(XML의 <http>)와 javaConfig(SecurityFilterChain Bean)는
      Spring Security 설정으로 동시 선언 불가 (springSecurityFilterChain Bean 충돌).
```

하지만 `war43Types()`는 둘을 함께 포함한다.

```java
private static List<String> war43Types() {
    return List.of(
            "webxmlfilter",
            "contextsecurity",
            "javaconfig",
            "userdetailsservice",
            "rolehierarchy",
            "loginpage",
            "userdetailshelperxml");
}
```

#### 영향

`setup-war-43`을 기본 셋업으로 적용하면 다음이 동시에 생성된다.

```text
context-security.xml
EgovProjectSecurityConfig.java
```

4.3 환경에서 XML `<http>`와 `@EnableWebSecurity` Java Config가 같이 로드되면
`springSecurityFilterChain` 또는 보안 설정 충돌 가능성이 크다.

#### 권장 수정

4.3 셋업을 XML 방식과 Java Config 방식으로 분리한다.

```text
setup-war-43-xml
  webXmlFilter
  contextSecurity
  roleHierarchy
  loginPage
  userDetailsHelperXml

setup-war-43-java
  javaConfig
  userDetailsService
  roleHierarchy
  successHandler
  failureHandler
  accessDeniedHandler
  loginPage
```

공공 SI 기본값을 유지하려면 `setup-war-43`은 XML 방식만 생성하는 것이 안전하다.

---

### [P2] 조합 키워드와 `egovVersion` 불일치를 검증하지 않음

#### 근거

`expand()`는 `VersionCapability cap`을 받지만 실제로 사용하지 않는다.

```java
List<String> expand(String securityType, VersionCapability cap) {
    return switch (securityType.toLowerCase()) {
        case "setup-war-43" -> war43Types();
        case "setup-war-50" -> war50Types();
        ...
    };
}
```

#### 영향

다음처럼 모순된 호출이 가능하다.

```text
securityType = setup-war-43
egovVersion = 5.0
```

이 경우 파일 묶음은 4.3 조합인데,
템플릿 렌더링은 5.0 기준으로 진행될 수 있다.

반대로 다음 호출도 가능하다.

```text
securityType = setup-war-50
egovVersion = 4.3
```

이러면 조합명과 실제 생성 템플릿 버전이 섞인다.

#### 권장 수정

조합 키워드의 버전 suffix와 `egovVersion`을 검증한다.

```java
if (securityType.endsWith("-43") && cap.jakarta()) {
    throw new IllegalArgumentException("setup-*-43은 egovVersion=4.3에서만 사용할 수 있습니다.");
}

if (securityType.endsWith("-50") && !cap.jakarta()) {
    throw new IllegalArgumentException("setup-*-50은 egovVersion=5.0에서만 사용할 수 있습니다.");
}
```

또는 조합 키워드가 버전을 결정하도록 하고,
`egovVersion`을 무시하지 말고 명확히 normalize한다.

---

### [P2] package/path 정합성 수정이 부분적으로만 적용됨

#### 근거

`loginFilter`는 `${packageName}.sec.filter`로 수정되어 Factory 저장 경로와 맞는다.

```java
// common/login-filter.java.tpl
package ${packageName}.sec.filter;
```

하지만 `logoutFilter`와 `loginPolicyFilter`는 여전히 다르다.

```java
// common/logout-filter.java.tpl
package ${packageName}.security.filter;
```

```java
// common/login-policy-filter.java.tpl
package ${packageName}.security.filter;
```

Factory 저장 경로는 다음이다.

```java
case "logoutfilter" -> FilePlan.of(
        "src/main/java/" + pkg + "/sec/filter/EgovSpringSecurityLogoutFilter.java",
        ...);

case "loginpolicyfilter" -> FilePlan.of(
        "src/main/java/" + pkg + "/uat/uap/filter/EgovLoginPolicyFilter.java",
        ...);
```

`userDetailsService`도 불일치한다.

```java
// egov43/user-details-service.java.tpl
package ${packageName}.service;
```

```java
// Factory
"src/main/java/" + pkg + "/sec/service/impl/EgovUserDetailsServiceImpl.java"
```

#### 영향

생성된 Java 파일이 저장 경로와 package 선언이 달라 IDE/빌드에서 문제를 만들 수 있다.

#### 권장 수정

모든 Java 템플릿에 대해 아래 규칙을 일괄 적용한다.

```text
저장 경로:
src/main/java/{packagePath}/sec/filter/EgovSpringSecurityLoginFilter.java

package:
package {packageName}.sec.filter;
```

```text
저장 경로:
src/main/java/{packagePath}/uat/uap/filter/EgovLoginPolicyFilter.java

package:
package {packageName}.uat.uap.filter;
```

```text
저장 경로:
src/main/java/{packagePath}/sec/service/impl/EgovUserDetailsServiceImpl.java

package:
package {packageName}.sec.service.impl;
```

---

## 4. 현재 구현에서 개선된 점

이전 구현 대비 개선된 부분도 있다.

| 항목 | 상태 |
|---|---|
| `SecurityTemplateService` God Class 축소 | 개선됨 |
| `SecurityFilePlanFactory` 분리 | 구현됨 |
| `SecurityTemplateRenderer` 분리 | 구현됨 |
| `.tpl` 템플릿 외부화 | 구현됨 |
| `outputPath` 직접 저장 | 구현됨 |
| 5.0 `userDetailsService` 안내문 `.md` 저장 | 개선됨 |
| 일부 package/path 테스트 추가 | 개선됨 |

하지만 개선된 구조 위에서 산출물 정합성 검증이 더 필요하다.

---

## 5. 테스트 보강 제안

### 5-1. 파일명과 public class명 일치 테스트

Java FilePlan에 대해 다음을 검증한다.

```text
relativePath 파일명 == 렌더링 결과의 public class명 + ".java"
```

예:

```text
EgovProjectSecurityConfig.java
public class EgovProjectSecurityConfig
```

### 5-2. 저장 경로와 package 선언 일치 테스트

Java FilePlan에 대해 다음을 검증한다.

```text
src/main/java/egovframework/let/emp/sec/filter/EgovSpringSecurityLoginFilter.java
package egovframework.let.emp.sec.filter;
```

모든 Java securityType을 대상으로 해야 한다.

### 5-3. `web.xml` FQCN 정합성 테스트

`setup-*` 조합 생성 결과에서 `web.xml.fragment`가 참조하는 필터 class가
실제로 생성된 Java 파일의 package/class와 일치하는지 확인한다.

### 5-4. `context-security.xml` FQCN 정합성 테스트

`context-security.xml`의 `jdbcMapClass`가 `sessionmapping` 생성 클래스와 일치하는지 확인한다.

### 5-5. 4.3/5.0 namespace 테스트

4.3 생성 Java에는 `jakarta.servlet`가 없어야 한다.

```text
egovVersion=4.3 → jakarta.servlet 미포함
egovVersion=5.0 → javax.servlet 미포함
```

### 5-6. 조합 키워드 버전 검증 테스트

다음 호출은 실패해야 한다.

```text
setup-war-43 + egovVersion=5.0
setup-war-50 + egovVersion=4.3
setup-all-war-43 + egovVersion=5.0
setup-all-war-50 + egovVersion=4.3
```

---

## 6. 수정 우선순위

1. `EgovProjectSecurityConfig.java`와 public class명 불일치 수정
2. 필터 템플릿의 `GenericFilterBean` import 수정
3. 필터 템플릿의 `javax/jakarta` 버전 분기 적용
4. `web.xml` filter-class를 `${packageName}` 기반으로 수정
5. `context-security.xml` `jdbcMapClass`를 `${packageName}` 기반으로 수정
6. 모든 Java 템플릿의 package 선언과 Factory 저장 경로 일치
7. `setup-war-43`을 XML 방식과 Java Config 방식으로 분리
8. 조합 키워드와 `egovVersion` 불일치 검증
9. 실제 렌더링/저장 통합 테스트 강화

---

## 7. 결론

`SecurityTemplateTool`은 구조적으로는 좋은 방향으로 이동했다.

```text
SecurityTemplateService
  God Class → 얇은 조율자

SecurityFilePlanFactory
  securityType → FilePlan 조립

SecurityTemplateRenderer
  .tpl 외부화 + 변수 치환

FilePlanExecutor
  outputPath 직접 저장
```

하지만 현재 구현은 생성 산출물이 대상 프로젝트에서 바로 동작한다는 보장이 부족하다.

특히 다음 이슈는 반드시 수정해야 한다.

```text
1. 파일명과 public class명 불일치
2. 잘못된 GenericFilterBean import
3. 4.3에 Jakarta API 혼입
4. web.xml filter-class와 실제 생성 클래스 불일치
5. context-security.xml jdbcMapClass와 실제 생성 클래스 불일치
6. setup-war-43의 XML Security + Java Config 동시 생성
```

따라서 현재 상태는 “구조 리팩터링 구현”으로는 의미가 있지만,
“실제 사용 가능한 SecurityTemplateTool 구현완료”로 판정하기에는 아직 부족하다.
