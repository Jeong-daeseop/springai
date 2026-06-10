# SecurityTemplateTool 후속 수정계획서

> 작성일: 2026-06-09  
> 대상: `SecurityTemplateTool` 수정계획서 구현 완료분 후속 보완  
> 근거: 수정계획서 전체 구현 검토 결과  
> 목표: 생성 산출물의 실제 런타임/컴파일 정합성 보완

---

## 1. 최종 보완 대상 요약

기존 수정계획서의 핵심 Fix는 대부분 반영되었다.

확인된 개선 사항:

| 항목 | 상태 |
|---|---|
| 4.3 `EgovProjectSecurityConfig.java` class명 일치 | 완료 |
| `GenericFilterBean` import 수정 | 완료 |
| 필터 `javax/jakarta` 버전 분기 | 완료 |
| `web.xml` filter-class `${packageName}` 반영 | 완료 |
| `context-security.xml` `jdbcMapClass` `${packageName}` 반영 | 완료 |
| 조합 키워드 버전 검증 | 완료 |
| package/path 일부 정합성 테스트 추가 | 완료 |

다만 아래 후속 이슈가 남아 있다.

| 번호 | 우선순위 | 대상 | 문제 |
|---|---|---|---|
| Follow-1 | P1 | `egov43/context-security.xml.tpl` + `SecurityFilePlanFactory.java` | XML 조합에서 `roleHierarchy` Bean 중복 |
| Follow-2 | P1 | `common/login-filter.java.tpl` + `web-xml-filter.tpl` | `web.xml` 직접 로딩 필터인데 생성자 주입만 존재 |
| Follow-3 | P1 | `egov43/role-hierarchy.java.tpl`, `egov50/role-hierarchy.java.tpl` | 저장 경로와 package 선언 불일치 |
| Follow-4 | P2 | `SecurityFilePlanFactory.java` | `setup-all-war-43` expand 결과에 `sessionmapping` 중복 |
| Follow-5 | P2 | `SecurityTemplateTool.java` | Tool description이 실제 조합 키워드와 불일치 |
| Follow-6 | 테스트 | `SecurityTemplateRendererIntegrationTest.java`, `SecurityFilePlanFactoryTest.java` | Follow-1~5 회귀 방지 테스트 부족 |

---

## 2. 상세 수정 계획

---

### Follow-1. 4.3 XML 조합의 `roleHierarchy` Bean 중복 제거

#### 현재 문제

`setup-war-43` / `setup-war-43-xml` 조합은 `rolehierarchy` Java Config 파일을 생성한다.

```java
private static List<String> war43XmlTypes() {
    return List.of(
            "webxmlfilter",
            "contextsecurity",
            "userdetailsservice",
            "rolehierarchy",
            "sessionmapping",
            "loginpage",
            "userdetailshelperxml");
}
```

동시에 `egov43/context-security.xml.tpl`도 `roleHierarchy` Bean을 XML로 직접 선언한다.

```xml
<beans:bean id="roleHierarchy"
    class="org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl">
    ...
</beans:bean>
```

#### 영향

생성 프로젝트가 XML 설정과 Java Config를 함께 로드하면 `roleHierarchy` Bean이 중복될 수 있다.

```text
context-security.xml
  roleHierarchy Bean

EgovRoleHierarchyConfig.java
  @Bean roleHierarchy()
```

Spring Boot 또는 Spring MVC 프로젝트에서 Bean overriding이 비활성화되어 있으면 애플리케이션 기동 실패 가능성이 있다.

#### 권장 수정안

4.3 XML 방식은 XML 파일 자체의 하드코딩 `roleHierarchy` Bean을 기본으로 사용한다.
따라서 XML 조합에서는 `rolehierarchy` Java Config 파일을 생성하지 않는다.

수정:

```java
private static List<String> war43XmlTypes() {
    return List.of(
            "webxmlfilter",
            "contextsecurity",
            "userdetailsservice",
            "sessionmapping",
            "loginpage",
            "userdetailshelperxml");
}
```

`rolehierarchy`는 다음 경우에만 생성한다.

```text
setup-war-43-java
setup-war-50
단일 securityType=roleHierarchy
```

#### 문서 보완

`context-security.xml.tpl` 주석을 명확히 한다.

```xml
<!--
XML 방식 기본값:
  이 파일 내부의 roleHierarchy Bean을 사용합니다.

동적 DB 기반 RoleHierarchyConfig.java를 사용하려면:
  1. 아래 roleHierarchy Bean을 제거하거나 id를 변경
  2. getSecurityTemplate("roleHierarchy", packageName, "4.3") 생성 파일을 등록
-->
```

---

### Follow-2. `web.xml` 직접 로딩 필터와 생성자 주입 불일치 수정

#### 현재 문제

`web.xml.fragment`는 `EgovSpringSecurityLoginFilter`를 일반 Servlet filter-class로 직접 등록한다.

```xml
<filter>
    <filter-name>egovSpringSecurityLoginFilter</filter-name>
    <filter-class>
        ${packageName}.sec.filter.EgovSpringSecurityLoginFilter
    </filter-class>
</filter>
```

하지만 생성되는 필터 클래스는 기본 생성자가 없고 생성자 주입만 제공한다.

```java
private final UserDetailsService userDetailsService;

public EgovSpringSecurityLoginFilter(UserDetailsService userDetailsService) {
    this.userDetailsService = userDetailsService;
}
```

#### 영향

Servlet 컨테이너가 `<filter-class>`로 필터를 직접 생성할 때 기본 생성자를 요구한다.
현재 구조에서는 다음 런타임 실패가 발생할 수 있다.

```text
NoSuchMethodException: EgovSpringSecurityLoginFilter.<init>()
```

#### 권장 수정안

가장 안전한 방향은 `web.xml` 필터를 `DelegatingFilterProxy`로 바꾸고,
실제 필터 구현체는 Spring Bean으로 등록하는 것이다.

##### 2-1. `web-xml-filter.tpl` 수정

현재:

```xml
<filter>
    <filter-name>egovSpringSecurityLoginFilter</filter-name>
    <filter-class>
        ${packageName}.sec.filter.EgovSpringSecurityLoginFilter
    </filter-class>
</filter>
```

수정:

```xml
<filter>
    <filter-name>egovSpringSecurityLoginFilter</filter-name>
    <filter-class>
        org.springframework.web.filter.DelegatingFilterProxy
    </filter-class>
    <init-param>
        <param-name>targetBeanName</param-name>
        <param-value>egovSpringSecurityLoginFilter</param-value>
    </init-param>
</filter>
```

`logoutFilter`, `loginPolicyFilter`도 동일하게 적용할지 검토한다.
의존성 주입이 필요한 필터는 반드시 `DelegatingFilterProxy` 방식을 사용한다.

##### 2-2. 필터 Bean 등록 템플릿 추가

신규 템플릿 후보:

```text
common/security-filter-beans.xml.tpl
```

또는 Java Config 템플릿에 Bean 등록:

```java
@Bean
public EgovSpringSecurityLoginFilter egovSpringSecurityLoginFilter(
        UserDetailsService userDetailsService) {
    return new EgovSpringSecurityLoginFilter(userDetailsService);
}
```

1차 권장:

| 방식 | 판단 |
|---|---|
| XML Bean 등록 | WAR/XML 방식과 자연스럽게 맞음 |
| Java Config Bean 등록 | Java Config 방식에서만 적합 |

XML 방식 조합에는 `context-security.xml.tpl`에 아래 Bean을 추가하는 것이 단순하다.

```xml
<beans:bean id="egovSpringSecurityLoginFilter"
    class="${packageName}.sec.filter.EgovSpringSecurityLoginFilter">
    <beans:constructor-arg ref="egovUserDetailsServiceImpl"/>
</beans:bean>
```

단, `egovUserDetailsServiceImpl` Bean 이름이 실제 `@Service` 기본 Bean 이름과 일치해야 한다.

#### 대안

필터 클래스에 기본 생성자를 추가하고 `WebApplicationContextUtils`로 Bean을 lookup하는 방식도 가능하지만,
테스트/유지보수 관점에서 `DelegatingFilterProxy + Spring Bean` 방식이 더 낫다.

---

### Follow-3. `role-hierarchy.java.tpl` package/path 불일치 수정

#### 현재 문제

Factory는 `rolehierarchy`를 다음 경로에 저장한다.

```java
case "rolehierarchy" -> FilePlan.of(
        "src/main/java/" + pkg + "/sec/config/EgovRoleHierarchyConfig.java",
        FilePlan.FileKind.SOURCE,
        () -> renderer.render(type, spec));
```

하지만 템플릿 package는 4.3/5.0 모두 다음과 같다.

```java
package ${packageName}.config;
```

#### 영향

생성 파일 경로와 package 선언이 불일치한다.
빌드 도구에 따라 컴파일 자체는 가능할 수 있지만,
생성 산출물 품질 기준으로는 명백히 깨진 상태다.

#### 권장 수정

템플릿 package를 Factory 경로에 맞춘다.

```java
package ${packageName}.sec.config;
```

수정 대상:

```text
src/main/resources/templates/security/egov43/role-hierarchy.java.tpl
src/main/resources/templates/security/egov50/role-hierarchy.java.tpl
```

---

### Follow-4. `setup-all-war-43`의 `sessionmapping` 중복 제거

#### 현재 문제

`war43XmlTypes()`에 `sessionmapping`이 포함되어 있고,
`filterTypes()`에도 `sessionmapping`이 포함되어 있다.

```java
private static List<String> war43XmlTypes() {
    return List.of(..., "sessionmapping", ...);
}

private static List<String> filterTypes() {
    return List.of(
            "loginfilter",
            "logoutfilter",
            "loginpolicyfilter",
            "sessionmapping");
}
```

`setup-all-war-43`은 두 목록을 모두 합친다.

```java
all.addAll(war43XmlTypes());
all.addAll(filterTypes());
all.add("securitymapper");
```

#### 영향

`expand()` 결과에는 중복이 포함되고,
`plan()`에서 경로 기준으로만 제거된다.

문제:

- 테스트명 `contains12UniqueTypes`가 실제로는 중복 포함 개수를 검증한다.
- 결과 메시지나 향후 dryRun/preview에서 파일 수 혼선 가능
- 조합 키워드 의미가 명확하지 않음

#### 권장 수정

`setup-all-*` 조합 확장 단계에서 중복을 제거한다.

```java
private static List<String> distinct(List<String> types) {
    return new ArrayList<>(new LinkedHashSet<>(types));
}
```

적용:

```java
case "setup-all-war-43", "setup-all-war-43-xml" -> {
    List<String> all = new ArrayList<>();
    all.addAll(war43XmlTypes());
    all.addAll(filterTypes());
    all.add("securitymapper");
    yield distinct(all);
}
```

기대 개수:

```text
war43XmlTypes 6개
filterTypes 4개
securityMapper 1개
중복 sessionmapping 없음
→ setup-all-war-43-xml: 10개 또는 11개
```

Follow-1에서 `rolehierarchy`를 XML 조합에서 제외하면:

```text
war43XmlTypes:
  webXmlFilter
  contextSecurity
  userDetailsService
  sessionMapping
  loginPage
  userDetailsHelperXml
  = 6개

setup-filters:
  loginFilter
  logoutFilter
  loginPolicyFilter
  sessionMapping
  = 4개

securityMapper:
  = 1개

중복 sessionMapping 제거 후:
  6 + 3 + 1 = 10개
```

따라서 테스트 기대값도 10개로 수정한다.

---

### Follow-5. `SecurityTemplateTool` Tool description 업데이트

#### 현재 문제

실제 구현은 다음과 같다.

```text
setup-war-43      → setup-war-43-xml alias
setup-war-43-xml  → XML Security 방식
setup-war-43-java → Java Config 방식
setup-all-war-43  → setup-all-war-43-xml alias
```

하지만 Tool 설명은 여전히 예전 조합을 안내한다.

```text
setup-war-43 → webXmlFilter + contextSecurity + javaConfig + ...
setup-all-war-43 → 15개 파일
```

#### 영향

LLM Tool description이 실제 contract와 다르면 사용자가 잘못 호출하거나,
모델이 실제 구현과 다른 조합을 기대하고 호출할 수 있다.

#### 권장 수정

`SecurityTemplateTool.java` 설명을 실제 구현과 맞춘다.

```text
[조합 생성 키워드 — outputPath와 함께 사용]

setup-war-43
  → setup-war-43-xml alias
  → 4.3 WAR XML Security 기본 셋업
  → webXmlFilter + contextSecurity + userDetailsService +
    sessionMapping + loginPage + userDetailsHelperXml

setup-war-43-xml
  → 4.3 XML Security 방식
  → contextSecurity(XML <http>) 사용
  → javaConfig 미포함

setup-war-43-java
  → 4.3 Java Config 방식
  → javaConfig + userDetailsService + roleHierarchy +
    successHandler + failureHandler + accessDeniedHandler + loginPage
  → contextSecurity 미포함

setup-all-war-43
  → setup-all-war-43-xml alias

setup-all-war-43-xml
  → setup-war-43-xml + setup-filters + securityMapper
  → 중복 sessionMapping 제거 후 10개 파일

setup-all-war-43-java
  → setup-war-43-java + setup-filters + securityMapper
  → 12개 파일

setup-war-50
  → 5.0 WAR Security 기본 셋업

setup-all-war-50
  → setup-war-50 + setup-filters + accessDeniedHandler + securityMapper
```

파일 수는 Follow-1~4 반영 후 최종 값으로 재계산해서 기재한다.

---

### Follow-6. 테스트 보강

#### 6-1. RoleHierarchy 중복 방지 테스트

`SecurityFilePlanFactoryTest`:

```java
@Test
void expand_setupWar43Xml_doesNotContainRoleHierarchy() {
    List<String> result = factory.expand("setup-war-43-xml", resolver.resolve("4.3"));
    assertThat(result).doesNotContain("rolehierarchy");
}
```

`SecurityTemplateRendererIntegrationTest`:

```java
@Test
void contextSecurity43_containsXmlRoleHierarchyBean() {
    String xml = renderer.render("contextsecurity", spec("contextsecurity", "4.3"));
    assertThat(xml).contains("<beans:bean id=\"roleHierarchy\"");
}
```

#### 6-2. LoginFilter Web.xml 생성 방식 테스트

`web.xml`이 `DelegatingFilterProxy`와 `targetBeanName`을 사용하는지 확인한다.

```java
@Test
void webXml_loginFilter_usesDelegatingFilterProxy() {
    String webXml = renderer.render("webxmlfilter", spec("webxmlfilter", "4.3"));
    assertThat(webXml).contains("org.springframework.web.filter.DelegatingFilterProxy");
    assertThat(webXml).contains("<param-value>egovSpringSecurityLoginFilter</param-value>");
}
```

#### 6-3. LoginFilter Bean 등록 테스트

`context-security.xml.tpl`에 Bean 등록을 추가하는 경우:

```java
@Test
void contextSecurity43_registersLoginFilterBean() {
    String xml = renderer.render("contextsecurity", spec("contextsecurity", "4.3"));
    assertThat(xml).contains("id=\"egovSpringSecurityLoginFilter\"");
    assertThat(xml).contains("${packageName}.sec.filter.EgovSpringSecurityLoginFilter");
}
```

실제 렌더링 결과 기준으로 `${packageName}`이 치환된 FQCN을 검증한다.

#### 6-4. RoleHierarchy package/path 테스트

```java
@Test
void roleHierarchy_43_packageDeclaration_matchesStoragePath() {
    String result = renderer.render("rolehierarchy", spec("rolehierarchy", "4.3"));
    assertThat(result).contains("package egovframework.let.emp.sec.config;");
}
```

5.0도 동일하게 추가한다.

#### 6-5. setup-all 중복 제거 테스트

```java
@Test
void expand_setupAllWar43Xml_returnsDistinctTypes() {
    List<String> result = factory.expand("setup-all-war-43-xml", resolver.resolve("4.3"));
    assertThat(result).doesNotHaveDuplicates();
    assertThat(result).hasSize(10);
}
```

---

## 3. 수정 순서

의존 관계상 아래 순서를 권장한다.

```text
Step 1. Follow-3 적용
  - role-hierarchy.java.tpl package 수정
  - package/path 테스트 추가

Step 2. Follow-1 적용
  - war43XmlTypes()에서 rolehierarchy 제거
  - context-security.xml.tpl 주석 보강
  - roleHierarchy 중복 방지 테스트 추가

Step 3. Follow-2 적용
  - web.xml loginFilter를 DelegatingFilterProxy로 변경
  - context-security.xml.tpl에 loginFilter Bean 등록
  - 필요 시 logoutFilter/loginPolicyFilter도 같은 방식 검토

Step 4. Follow-4 적용
  - expand() 단계 distinct 처리
  - setup-all-war-43 기대 파일 수 테스트 수정

Step 5. Follow-5 적용
  - SecurityTemplateTool.java @Tool description 업데이트
  - SecurityTemplateTool_사용예시.md 업데이트

Step 6. 전체 테스트 실행
  - ./gradlew test --rerun-tasks
```

---

## 4. 수정 후 기대 결과

| 항목 | 수정 전 | 수정 후 |
|---|---|---|
| `roleHierarchy` | XML Bean + Java Config Bean 중복 가능 | XML 조합에서는 XML Bean만 사용 |
| `EgovSpringSecurityLoginFilter` | web.xml 직접 생성 + 생성자 주입 불일치 | DelegatingFilterProxy + Spring Bean |
| `EgovRoleHierarchyConfig.java` | 저장 경로와 package 불일치 | `sec/config`로 일치 |
| `setup-all-war-43` | `sessionmapping` 중복 | expand 결과 중복 제거 |
| Tool description | 실제 구현과 불일치 | 실제 조합 키워드와 파일 수 반영 |

---

## 5. 최종 완료 기준

다음 조건을 모두 만족해야 한다.

```text
1. ./gradlew test --rerun-tasks 통과
2. setup-war-43-xml에 javaconfig/rolehierarchy 미포함
3. setup-war-43-java에 contextsecurity 미포함
4. setup-all-war-43-xml expand 결과 중복 없음
5. web.xml의 loginFilter가 DelegatingFilterProxy 방식으로 등록됨
6. context-security.xml에 egovSpringSecurityLoginFilter Bean 등록됨
7. roleHierarchy 템플릿 package와 Factory 저장 경로 일치
8. Tool description이 실제 expand() contract와 일치
```

---

## 6. 결론

수정계획서 1차 구현으로 큰 구조와 다수의 P1 문제는 해결되었다.

하지만 아직 실제 런타임 관점에서 중요한 문제가 남아 있다.

```text
핵심 잔여 리스크:
  - roleHierarchy Bean 중복
  - web.xml 직접 필터 로딩과 생성자 주입 불일치
  - Tool description contract 불일치
```

위 후속 수정까지 반영되어야 `SecurityTemplateTool`을 실제 프로젝트에 적용 가능한 생성기로 볼 수 있다.
