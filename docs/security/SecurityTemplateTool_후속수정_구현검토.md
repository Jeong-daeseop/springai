# SecurityTemplateTool 후속수정 구현검토

> 작성일: 2026-06-09  
> 대상: `SecurityTemplateTool_후속수정계획서.md` 전체 구현 완료분  
> 검토 관점: 후속 Follow-1~6 반영 여부, 생성 산출물 런타임/컴파일 정합성, Tool contract 일치 여부

---

## 1. 검토 요약

후속수정계획서의 주요 항목은 대부분 반영되었다.

확인된 개선:

| 항목 | 상태 |
|---|---|
| Follow-1: XML 조합에서 `rolehierarchy` Java Config 제외 | 반영 |
| Follow-2: loginFilter `DelegatingFilterProxy` 전환 + 4.3 XML Bean 등록 | 반영 |
| Follow-3: `role-hierarchy` package/path 일치 | 반영 |
| Follow-4: `setup-all-war-43` 중복 제거 | 반영 |
| Follow-5: Tool description 최신화 | 반영 |
| Follow-6: 회귀 테스트 보강 | 반영 |

다만 아직 실제 생성 프로젝트를 깨뜨릴 수 있는 P1 이슈가 남아 있다.

핵심 잔여 문제:

```text
setup-war-43-xml 기본 조합이 web.xml에서 참조하는 필터 구현체를 생성하지 않음
```

---

## 2. 테스트 결과

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

해당 경고는 이번 `SecurityTemplateTool` 후속수정과 직접 관련 없다.

---

## 3. 주요 Findings

### [P1] `setup-war-43-xml`가 `webXmlFilter`를 생성하지만, `web.xml`이 참조하는 필터 구현체들을 생성하지 않음

#### 근거

`setup-war-43` / `setup-war-43-xml` 조합은 다음 타입만 생성한다.

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

하지만 `web-xml-filter.tpl`은 다음 3개 프로젝트 필터를 등록한다.

```xml
<filter>
    <filter-name>loginPolicyFilter</filter-name>
    <filter-class>
        ${packageName}.uat.uap.filter.EgovLoginPolicyFilter
    </filter-class>
</filter>
```

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

```xml
<filter>
    <filter-name>egovSpringSecurityLogoutFilter</filter-name>
    <filter-class>
        ${packageName}.sec.filter.EgovSpringSecurityLogoutFilter
    </filter-class>
</filter>
```

이 중 실제 Java 소스는 다음 securityType으로 생성된다.

| web.xml 참조 클래스 | 필요한 securityType |
|---|---|
| `${packageName}.uat.uap.filter.EgovLoginPolicyFilter` | `loginPolicyFilter` |
| `${packageName}.sec.filter.EgovSpringSecurityLoginFilter` | `loginFilter` |
| `${packageName}.sec.filter.EgovSpringSecurityLogoutFilter` | `logoutFilter` |

하지만 `setup-war-43-xml`에는 `loginfilter`, `logoutfilter`, `loginpolicyfilter`가 없다.

#### 영향

사용자가 기본 조합으로 안내되는 `setup-war-43` 또는 `setup-war-43-xml`만 실행하면:

```text
web.xml.fragment 생성됨
context-security.xml 생성됨
EgovUserDetailsServiceImpl 생성됨
EgovSessionMapping 생성됨

하지만
EgovLoginPolicyFilter 없음
EgovSpringSecurityLoginFilter 없음
EgovSpringSecurityLogoutFilter 없음
```

결과적으로 생성된 `web.xml.fragment`가 존재하지 않는 클래스를 참조한다.
런타임에 다음 문제가 발생할 수 있다.

```text
ClassNotFoundException: EgovLoginPolicyFilter
ClassNotFoundException: EgovSpringSecurityLogoutFilter
Cannot load bean class: EgovSpringSecurityLoginFilter
```

#### 권장 수정안

둘 중 하나를 선택해야 한다.

##### 안 1. `setup-war-43-xml`에 필터 구현체 3종 포함

기본 XML 셋업이 실제 동작 가능한 셋업이 되도록 필터 구현체를 포함한다.

```java
private static List<String> war43XmlTypes() {
    return List.of(
            "webxmlfilter",
            "contextsecurity",
            "userdetailsservice",
            "sessionmapping",
            "loginfilter",
            "logoutfilter",
            "loginpolicyfilter",
            "loginpage",
            "userdetailshelperxml");
}
```

이 경우 `setup-filters`와 중복되므로 `setup-all-war-43-xml`은 단순히 alias 또는 `securityMapper` 추가 조합으로 재정의한다.

예:

```java
case "setup-all-war-43", "setup-all-war-43-xml" -> {
    List<String> all = new ArrayList<>();
    all.addAll(war43XmlTypes());
    all.add("securitymapper");
    yield distinct(all);
}
```

예상 파일 수:

```text
setup-war-43-xml:
  webXmlFilter
  contextSecurity
  userDetailsService
  sessionMapping
  loginFilter
  logoutFilter
  loginPolicyFilter
  loginPage
  userDetailsHelperXml
  = 9개

setup-all-war-43-xml:
  setup-war-43-xml + securityMapper
  = 10개
```

##### 안 2. `setup-war-43-xml`에서 `webXmlFilter` 제외

기본 XML 셋업은 Security XML 중심으로 두고,
필터 체인까지 포함하는 것은 `setup-all-war-43-xml`로 제한한다.

```java
private static List<String> war43XmlTypes() {
    return List.of(
            "contextsecurity",
            "userdetailsservice",
            "sessionmapping",
            "loginpage",
            "userdetailshelperxml");
}
```

`setup-all-war-43-xml`:

```java
all.add("webxmlfilter");
all.addAll(war43XmlTypes());
all.addAll(filterTypes());
all.add("securitymapper");
yield distinct(all);
```

이 방식은 `setup-war-43-xml`만으로는 web.xml 필터 체인을 생성하지 않는다.
따라서 Tool description에서 다음을 명확히 해야 한다.

```text
setup-war-43-xml:
  context-security.xml 중심 기본 설정
  web.xml 필터 체인 미포함

setup-all-war-43-xml:
  web.xml 필터 체인 + 필터 구현체 포함 전체 설정
```

##### 권장 선택

현재 Tool description에서 `setup-war-43-xml`을 “기본 셋업”으로 안내하고 있으므로,
안 1이 더 자연스럽다.

즉, `setup-war-43-xml`은 web.xml이 참조하는 필터 구현체를 함께 생성해야 한다.

---

### [P1] `setup-war-43-xml`의 `web.xml`은 `egovSpringSecurityLoginFilter` Bean을 기대하지만 해당 필터 소스가 조합에 없음

#### 근거

`web-xml-filter.tpl`은 `DelegatingFilterProxy` + `targetBeanName` 방식으로 로그인 필터를 등록한다.

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

4.3 `context-security.xml.tpl`도 Spring Bean을 등록한다.

```xml
<beans:bean id="egovSpringSecurityLoginFilter"
    class="${packageName}.sec.filter.EgovSpringSecurityLoginFilter">
    <beans:constructor-arg ref="egovUserDetailsServiceImpl"/>
</beans:bean>
```

하지만 해당 class 파일은 `loginFilter` securityType을 생성해야 존재한다.
`setup-war-43-xml`에는 `loginfilter`가 포함되어 있지 않다.

#### 영향

`setup-war-43-xml`만 실행하면 `context-security.xml` 내부 Bean class도 존재하지 않는다.
Spring 컨텍스트 초기화 시 Bean class 로드 실패 가능성이 있다.

#### 권장 수정

앞선 Finding과 동일하게 `setup-war-43-xml`에 `loginfilter`를 포함해야 한다.

```java
private static List<String> war43XmlTypes() {
    return List.of(
            "webxmlfilter",
            "contextsecurity",
            "userdetailsservice",
            "sessionmapping",
            "loginfilter",
            "logoutfilter",
            "loginpolicyfilter",
            "loginpage",
            "userdetailshelperxml");
}
```

---

### [P2] `webXmlFilter` 단일 반환/저장 사용 시 5.0 조합과 계약이 애매함

#### 근거

`web-xml-filter.tpl`은 공통 템플릿이다.
하지만 로그인 필터는 4.3 `context-security.xml`의 다음 Bean 등록에 의존한다.

```xml
<beans:bean id="egovSpringSecurityLoginFilter"
    class="${packageName}.sec.filter.EgovSpringSecurityLoginFilter">
    <beans:constructor-arg ref="egovUserDetailsServiceImpl"/>
</beans:bean>
```

5.0 `context-security.xml.tpl`에는 같은 Bean 등록이 없다.

#### 영향

다음 호출이 허용된다면:

```text
getSecurityTemplate("webXmlFilter", packageName, "5.0")
```

생성되는 `web.xml.fragment`는 `egovSpringSecurityLoginFilter` Bean을 기대하지만,
5.0 Security 설정은 그 Bean을 제공하지 않는다.

#### 권장 수정

둘 중 하나를 선택한다.

| 선택 | 설명 |
|---|---|
| 5.0에서 `webXmlFilter` 미지원 | `renderer.render("webxmlfilter", 5.0)` 또는 Factory에서 예외 처리 |
| 5.0용 filter bean 등록 제공 | 5.0 context-security 또는 별도 Bean 템플릿에 `egovSpringSecurityLoginFilter` 등록 |

현재 `setup-war-50`에는 `webXmlFilter`가 포함되어 있지 않으므로,
1차로는 5.0 `webXmlFilter`를 미지원 처리하는 것이 더 안전하다.

Tool description에도 다음을 명시한다.

```text
webXmlFilter:
  4.3 WAR XML 방식 전용
  5.0에서는 setup-war-50 조합에 포함되지 않으며 단독 사용 비권장
```

---

## 4. 확인된 개선 사항

### 4-1. Follow-1 반영

`setup-war-43-xml` 조합에서 `rolehierarchy` Java Config가 제외되었다.

```java
assertThat(result).doesNotContain("rolehierarchy");
```

4.3 `context-security.xml.tpl`에는 XML `roleHierarchy` Bean 사용 주석이 보강되었다.

### 4-2. Follow-2 일부 반영

`loginFilter`는 `web.xml`에서 `DelegatingFilterProxy` 방식으로 변경되었다.

```xml
<filter-class>
    org.springframework.web.filter.DelegatingFilterProxy
</filter-class>
<init-param>
    <param-name>targetBeanName</param-name>
    <param-value>egovSpringSecurityLoginFilter</param-value>
</init-param>
```

4.3 `context-security.xml.tpl`에는 `egovSpringSecurityLoginFilter` Bean 등록이 추가되었다.

### 4-3. Follow-3 반영

4.3/5.0 `role-hierarchy.java.tpl` package가 Factory 저장 경로와 맞춰졌다.

```java
package ${packageName}.sec.config;
```

### 4-4. Follow-4 반영

`setup-all-war-43` 조합에 `distinct()`가 적용되어 중복 제거가 들어갔다.

```java
yield distinct(all);
```

### 4-5. Follow-5 반영

`SecurityTemplateTool` description이 최신 조합 키워드와 파일 수를 대부분 반영했다.

### 4-6. Follow-6 반영

테스트가 추가되었다.

대표 테스트:

```java
expand_setupWar43_returns6Types()
expand_setupAllWar43_contains10UniqueTypes()
roleHierarchy_43_packageDeclaration_matchesStoragePath()
webXml_loginFilter_usesDelegatingFilterProxy()
contextSecurity_43_registersLoginFilterBean()
```

---

## 5. 추가 테스트 제안

현재 테스트는 개별 템플릿 정합성은 많이 보강되었지만,
조합 단위의 “참조 클래스가 실제 생성되는지”는 아직 부족하다.

### 5-1. `setup-war-43-xml` web.xml 참조 클래스 생성 여부 테스트

```java
@Test
void plan_setupWar43Xml_containsAllWebXmlReferencedFilters() {
    SecuritySpec spec = SecuritySpec.of(
            "setup-war-43-xml",
            "egovframework.let.emp",
            "war",
            "/tmp/dummy",
            resolver.resolve("4.3"));

    List<FilePlan> plans = factory.plan(spec);

    assertThat(plans)
        .extracting(FilePlan::relativePath)
        .contains(
            "src/main/java/egovframework/let/emp/sec/filter/EgovSpringSecurityLoginFilter.java",
            "src/main/java/egovframework/let/emp/sec/filter/EgovSpringSecurityLogoutFilter.java",
            "src/main/java/egovframework/let/emp/uat/uap/filter/EgovLoginPolicyFilter.java"
        );
}
```

### 5-2. `webXmlFilter` 5.0 미지원 테스트

5.0에서 미지원 처리하기로 하면:

```java
@Test
void webXmlFilter_withEgov50_throwsUnsupported() {
    SecuritySpec spec = spec("webxmlfilter", "5.0");
    assertThatThrownBy(() -> renderer.render("webxmlfilter", spec))
        .isInstanceOf(IllegalArgumentException.class);
}
```

또는 Factory 단에서 막는다면 Factory 테스트에 추가한다.

---

## 6. 결론

후속수정계획서 구현은 큰 방향으로 잘 진행되었다.

하지만 아직 다음 P1이 남아 있어 완료 판정은 어렵다.

```text
setup-war-43-xml가 web.xml을 생성하면서
web.xml이 참조하는 필터 구현체를 생성하지 않는다.
```

이 문제를 해결하려면 `setup-war-43-xml`에 필터 3종을 포함하거나,
`setup-war-43-xml`에서 `webXmlFilter`를 제외하고 필터 체인은 `setup-all-war-43-xml`에서만 생성해야 한다.

현재 Tool description이 `setup-war-43-xml`을 “기본 셋업”으로 안내하므로,
권장 수정은 다음이다.

```text
setup-war-43-xml에 loginFilter + logoutFilter + loginPolicyFilter 포함
```

이후 조합 단위 테스트로 `web.xml` 참조 클래스가 실제 FilePlan에 포함되는지 검증해야 한다.
