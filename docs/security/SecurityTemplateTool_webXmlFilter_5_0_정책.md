# SecurityTemplateTool webXmlFilter 5.0 호출 정책

> 작성일: 2026-06-10  
> 대상: `SecurityTemplateTool` / `webXmlFilter` securityType  
> 결론: `webXmlFilter`는 eGovFrame 4.3 WAR XML Security 전용으로 제한한다.

---

## 1. 문제 요약

현재 `webXmlFilter` 템플릿은 eGovFrame 4.3 XML Security 구조에 맞춰져 있다.

하지만 Tool 호출은 다음처럼 5.0 버전으로도 가능하다.

```text
getSecurityTemplate(
  securityType = "webXmlFilter",
  packageName = "egovframework.let.emp",
  egovVersion = "5.0",
  outputPath = null,
  projectType = "war"
)
```

이 경우 결과물이 겉보기로는 정상 생성되지만,
실제 5.0 Security 설정과 연결이 완결되지 않을 수 있다.

핵심 문제:

```text
webXmlFilter는 egovSpringSecurityLoginFilter Bean을 기대한다.
하지만 5.0 context-security.xml에는 해당 Bean 등록이 없다.
```

---

## 2. webXmlFilter가 생성하는 내용

`webXmlFilter`는 `web.xml.fragment`를 생성한다.

필터 체인은 다음과 같다.

```text
1. CharacterEncodingFilter
2. HTMLTagFilter
3. LoginPolicyFilter
4. EgovSpringSecurityLoginFilter
5. springSecurityFilterChain
6. EgovSpringSecurityLogoutFilter
```

특히 로그인 필터는 직접 class를 생성하지 않고 `DelegatingFilterProxy`로 Spring Bean을 찾는다.

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

즉 `web.xml.fragment`는 Spring 컨테이너 안에 아래 Bean이 있다고 가정한다.

```text
egovSpringSecurityLoginFilter
```

---

## 3. 4.3에서는 왜 정상인가

eGovFrame 4.3 XML Security 템플릿에는 `egovSpringSecurityLoginFilter` Bean이 등록되어 있다.

```xml
<beans:bean id="egovSpringSecurityLoginFilter"
    class="${packageName}.sec.filter.EgovSpringSecurityLoginFilter">
    <beans:constructor-arg ref="egovUserDetailsServiceImpl"/>
</beans:bean>
```

그리고 `setup-war-43-xml` 조합에는 다음 구현체가 포함된다.

```text
loginFilter
→ EgovSpringSecurityLoginFilter.java
```

따라서 4.3에서는 연결이 완결된다.

```text
web.xml.fragment
  → DelegatingFilterProxy
  → targetBeanName=egovSpringSecurityLoginFilter
  → context-security.xml Bean
  → EgovSpringSecurityLoginFilter.java
```

즉 4.3 XML 방식에서는 `webXmlFilter`가 정상적으로 동작할 수 있다.

---

## 4. 5.0에서는 왜 문제가 되는가

eGovFrame 5.0 Security 설정은 4.3과 구조가 다르다.

5.0은 다음 구조를 사용한다.

```text
context-security.xml
  → EgovSecurityConfig POJO Bean

EgovProjectSecurityConfig.java
  → @Import(EgovSecurityConfiguration.class)

EgovSecurityConfiguration
  → SecurityFilterChain 자동 구성
```

즉 5.0 `context-security.xml`은 설정값을 담은 `EgovSecurityConfig` Bean 중심이다.

현재 5.0 `context-security.xml`에는 아래 Bean이 없다.

```text
egovSpringSecurityLoginFilter
```

그래서 5.0에서 `webXmlFilter`를 단독 생성하면 다음 상태가 된다.

```text
web.xml.fragment
  → targetBeanName=egovSpringSecurityLoginFilter
  → 5.0 context-security.xml에 해당 Bean 없음
  → 런타임 실패 가능
```

대표적인 위험:

```text
NoSuchBeanDefinitionException: egovSpringSecurityLoginFilter
```

또는 관련 필터 구현체/Bean 연결 실패가 발생할 수 있다.

---

## 5. 현재 조합에서는 왜 덜 위험한가

5.0 기본 조합인 `setup-war-50`에는 `webXmlFilter`가 포함되어 있지 않다.

```text
setup-war-50
= contextSecurity
+ javaConfig
+ roleHierarchy
+ loginPage
+ userDetailsHelperXml
```

따라서 조합 호출 기준으로는 `webXmlFilter` 문제가 직접 발생하지 않는다.

문제는 단일 호출이다.

```text
getSecurityTemplate("webXmlFilter", packageName, "5.0", ...)
```

현재 이 호출이 가능하면,
사용자는 5.0 프로젝트에 맞지 않는 `web.xml.fragment`를 받을 수 있다.

---

## 6. 정책 선택지

### 안 1. webXmlFilter를 4.3 전용으로 제한

가장 안전하고 단순한 선택이다.

정책:

```text
egovVersion=4.3:
  webXmlFilter 허용

egovVersion=5.0:
  webXmlFilter 미지원
  setup-war-50 사용 안내
```

예상 안내 메시지:

```text
webXmlFilter는 eGovFrame 4.3 WAR XML Security 전용입니다.
eGovFrame 5.0에서는 setup-war-50 또는 contextSecurity + javaConfig를 사용하세요.
```

장점:

| 항목 | 설명 |
|---|---|
| 안정성 | 5.0에서 불완전한 `web.xml.fragment` 생성을 막음 |
| 구현 단순성 | Renderer 또는 Factory에서 버전 체크만 추가 |
| 조합 정책과 일치 | `setup-war-50`에 이미 `webXmlFilter`가 없음 |

단점:

| 항목 | 설명 |
|---|---|
| 5.0 web.xml 커스텀 필터 체인 미지원 | 필요 시 별도 설계 필요 |

### 안 2. 5.0에서도 webXmlFilter를 지원하도록 Bean까지 생성

5.0에서도 `webXmlFilter`를 허용하려면 단순히 템플릿만 반환해서는 안 된다.

추가로 맞춰야 할 항목:

```text
1. 5.0 context-security.xml에 egovSpringSecurityLoginFilter Bean 등록
2. loginFilter / logoutFilter / loginPolicyFilter 5.0 생성물 포함
3. EgovSecurityConfiguration 자동 SecurityFilterChain과 커스텀 필터 체인 충돌 여부 검증
4. CSRF / loginProcessUrl / logoutUrl 동작 검증
```

단점:

| 항목 | 설명 |
|---|---|
| 구현 복잡도 | 5.0 자동 구성과 커스텀 필터 체인의 관계를 새로 설계해야 함 |
| 런타임 위험 | SecurityFilterChain 중복 또는 순서 충돌 가능 |
| 테스트 부담 | 5.0 통합 테스트가 필요 |

따라서 현재 단계에서는 권장하지 않는다.

---

## 7. 권장 정책

권장안은 명확하다.

```text
webXmlFilter는 eGovFrame 4.3 WAR XML Security 전용으로 제한한다.
```

구체 정책:

| egovVersion | webXmlFilter |
|---|---|
| `4.3` | 허용 |
| `5.0` | 미지원 / 안내 메시지 반환 |

5.0에서는 다음 조합을 사용하도록 안내한다.

```text
setup-war-50
```

또는 단일 템플릿 조합:

```text
contextSecurity + javaConfig
```

---

## 8. 구현 위치

### 8-1. Renderer에서 제한

가장 일관적인 방식이다.

문자열 반환 경로와 파일 저장 경로 모두 `renderer.render()`를 거치므로,
Renderer에서 막으면 모든 경로에 적용된다.

예시:

```java
String templateName(String type, VersionCapability cap) {
    boolean is43 = !cap.jakarta();
    return switch (type.toLowerCase()) {
        case "webxmlfilter" -> {
            if (!is43) {
                throw new IllegalArgumentException(
                    "webXmlFilter는 eGovFrame 4.3 WAR XML Security 전용입니다. " +
                    "5.0에서는 setup-war-50을 사용하세요.");
            }
            yield "common/web-xml-filter.tpl";
        }
        ...
    };
}
```

장점:

```text
getSecurityTemplate("webXmlFilter", ..., "5.0", null, ...)
→ 차단

getSecurityTemplate("webXmlFilter", ..., "5.0", outputPath, ...)
→ 차단
```

### 8-2. Factory에서 제한

저장 경로에서는 막을 수 있지만,
문자열 반환 경로에서는 `factory.renderSingle()`이 바로 `renderer.render()`를 호출하므로
중복 검증이 필요할 수 있다.

따라서 Renderer에서 제한하는 방식이 더 낫다.

---

## 9. 테스트 계획

### 9-1. Renderer 테스트

```java
@Test
void webXmlFilter_withEgov50_throwsUnsupported() {
    SecuritySpec spec = spec("webxmlfilter", "5.0");

    assertThatThrownBy(() -> renderer.render("webxmlfilter", spec))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("4.3")
        .hasMessageContaining("setup-war-50");
}
```

### 9-2. Service 테스트

`SecurityTemplateService`는 `IllegalArgumentException`을 잡아 unsupported 메시지를 반환한다.
따라서 Service 레벨에서는 안내 메시지를 검증한다.

```java
@Test
void getSecurityTemplate_webXmlFilter_50_returnsUnsupportedMessage() {
    String result = service.getSecurityTemplate(
        "webXmlFilter",
        "egovframework.let.emp",
        "5.0",
        null,
        "war"
    );

    assertThat(result).contains("지원하지 않는 securityType");
    assertThat(result).contains("webXmlFilter");
}
```

단, 더 친절한 메시지를 원하면 `unsupported()`와 별도로
버전 불일치 메시지를 유지하도록 Service 처리 방식을 조정할 수 있다.

예:

```text
webXmlFilter는 eGovFrame 4.3 WAR XML Security 전용입니다.
5.0에서는 setup-war-50을 사용하세요.
```

---

## 10. Tool description 수정

`SecurityTemplateTool.java` 설명에도 명시한다.

```text
webXmlFilter
  → eGovFrame 4.3 WAR XML Security 전용
  → web.xml 6-filter 체인 전체
  → egovVersion=5.0에서는 사용하지 않음
  → 5.0에서는 setup-war-50 사용
```

5.0 섹션에도 명시한다.

```text
setup-war-50
  → webXmlFilter 미포함
  → EgovSecurityConfiguration 기반 SecurityFilterChain 자동 구성
```

---

## 11. 최종 결론

`webXmlFilter`는 현재 구조상 4.3 XML Security와 강하게 결합되어 있다.

```text
webXmlFilter
  → egovSpringSecurityLoginFilter Bean 필요
  → 4.3 context-security.xml에 Bean 등록
  → 5.0 context-security.xml에는 Bean 없음
```

따라서 5.0 단일 호출을 허용하면 불완전한 생성물이 만들어질 수 있다.

최종 정책:

```text
webXmlFilter는 4.3 WAR XML Security 전용으로 제한한다.
5.0에서는 setup-war-50을 사용하도록 안내한다.
```

이렇게 하면 “생성은 됐지만 런타임에서 깨지는” 애매한 결과물을 막을 수 있다.
