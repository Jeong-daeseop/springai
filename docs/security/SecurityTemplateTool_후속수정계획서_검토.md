# SecurityTemplateTool 후속수정계획서 검토

> 작성일: 2026-06-09  
> 근거 문서: `SecurityTemplateTool_후속수정계획서.md`  
> 목적: 각 Follow 항목별 타당성 검토 및 권장 구현 순서 정리

---

## 1. 항목별 검토

---

### Follow-3 — `role-hierarchy.java.tpl` package 불일치

**평가: P1 맞음, 가장 먼저 처리 권장**

- Factory 경로: `sec/config/EgovRoleHierarchyConfig.java`
- 템플릿 package: `${packageName}.config` ← 불일치

단순 1줄 수정이면서 컴파일 오류 직결 버그입니다.  
다른 항목에 의존성이 없으므로 독립적으로 선행 처리합니다.

**수정 대상:**

```
src/main/resources/templates/security/egov43/role-hierarchy.java.tpl
src/main/resources/templates/security/egov50/role-hierarchy.java.tpl
```

변경:

```java
// 현재
package ${packageName}.config;

// 수정 후
package ${packageName}.sec.config;
```

---

### Follow-2 — `EgovSpringSecurityLoginFilter` 생성자 주입 불일치

**평가: 런타임에서 가장 위험한 이슈**

`<filter-class>`로 직접 등록된 필터를 Servlet 컨테이너가 인스턴스화할 때  
기본 생성자가 없으면 즉시 `NoSuchMethodException`이 발생하여 서버가 기동되지 않습니다.  
조용히 나중에 실패하는 버그가 아니라 **기동 즉시 실패**하는 수준입니다.

**DelegatingFilterProxy 방향은 맞습니다.**

다만 구현 시 두 가지를 주의합니다.

**① Bean ID 일치 필수**

`web.xml`의 `targetBeanName`과 `context-security.xml`의 `<bean id>`가 반드시 일치해야 합니다.

```xml
<!-- web.xml (수정 후) -->
<filter>
    <filter-name>egovSpringSecurityLoginFilter</filter-name>
    <filter-class>org.springframework.web.filter.DelegatingFilterProxy</filter-class>
    <init-param>
        <param-name>targetBeanName</param-name>
        <param-value>egovSpringSecurityLoginFilter</param-value>
    </init-param>
</filter>

<!-- context-security.xml에 추가 -->
<beans:bean id="egovSpringSecurityLoginFilter"
    class="${packageName}.sec.filter.EgovSpringSecurityLoginFilter">
    <beans:constructor-arg ref="egovUserDetailsServiceImpl"/>
</beans:bean>
```

`EgovSpringSecurityLoginFilter`는 `@Service` 어노테이션이 없는 클래스이므로  
Spring 자동 Bean 이름을 기대할 수 없습니다. `context-security.xml`에 **명시적으로 `id` 지정**해야 합니다.

**② logoutFilter/loginPolicyFilter는 DelegatingFilterProxy 불필요**

두 필터는 생성자 주입이 없습니다.  
Servlet 컨테이너가 기본 생성자로 직접 인스턴스화 가능합니다.  
이 두 필터까지 DelegatingFilterProxy로 전환하면 오히려 복잡도만 높아집니다.

**적용 범위: `EgovSpringSecurityLoginFilter`만 DelegatingFilterProxy 적용**

---

### Follow-1 — `roleHierarchy` Bean 중복

**평가: P1 맞음, Follow-4와 반드시 묶어서 처리**

`war43XmlTypes()`에서 `rolehierarchy`를 제거하는 방향은 맞습니다.

`context-security.xml.tpl`은 이미 `roleHierarchy` Bean을 XML로 직접 선언합니다.  
`rolehierarchy` Java Config 파일도 함께 생성하면 동일 Bean 이름이 중복됩니다.

Spring Boot 3.x / Spring 6.x는 Bean overriding 기본값이 `false`이므로  
두 Bean이 공존하면 애플리케이션 기동 자체가 실패합니다.

**Follow-4와 묶어서 처리해야 하는 이유:**

`war43XmlTypes()`에서 `rolehierarchy`를 제거하면 개수가 변하고,  
Follow-4의 `distinct()` 적용 후 `setup-all-war-43-xml` 최종 개수가 바뀝니다.  
두 항목을 따로 적용하면 테스트 기대값을 두 번 고쳐야 합니다.

**수정 후 개수 계산 (Follow-1 + Follow-4 동시 적용 기준):**

```
war43XmlTypes():
  webXmlFilter, contextSecurity, userDetailsService,
  sessionMapping, loginPage, userDetailsHelperXml = 6개

filterTypes():
  loginFilter, logoutFilter, loginPolicyFilter, sessionMapping = 4개

securityMapper = 1개

중복 sessionMapping 제거 = -1개

최종: 6 + 4 + 1 - 1 = 10개
```

문서의 계산(10개)과 일치합니다.

---

### Follow-4 — `sessionmapping` 중복

**평가: Follow-1과 묶어서 처리**

`distinct()` 적용 위치는 `expand()` 반환값에서 처리합니다.  
`plan()`의 LinkedHashSet은 경로(path) 기준 중복 제거이므로,  
타입(type) 레벨 중복은 `expand()`에서 정리하는 것이 의미상 더 명확합니다.

```java
// expand() 내 setup-all 케이스에 적용
case "setup-all-war-43", "setup-all-war-43-xml" -> {
    List<String> all = new ArrayList<>();
    all.addAll(war43XmlTypes());
    all.addAll(filterTypes());
    all.add("securitymapper");
    yield distinct(all);   // ← 타입 레벨 중복 제거
}

// 헬퍼
private static List<String> distinct(List<String> types) {
    return new ArrayList<>(new LinkedHashSet<>(types));
}
```

---

### Follow-5 — Tool description 업데이트

**평가: Follow-1~4 완료 후 마지막에 처리**

Follow-1~4 적용 후 파일 수와 조합 목록이 확정되면 한 번에 정리합니다.  
Follow-1~4 이전에 먼저 바꾸면 파일 수가 또 틀려서 두 번 고치게 됩니다.

**업데이트 내용:**

- `setup-war-43` → `setup-war-43-xml` alias임을 명시
- `setup-war-43-xml`이 `javaconfig`/`rolehierarchy` 미포함임을 명시
- `setup-war-43-java`가 `contextsecurity` 미포함임을 명시
- `setup-all-war-43-xml` 파일 수: 10개
- `setup-all-war-43-java` 파일 수: 12개

---

### Follow-6 — 테스트 보강

**평가: 테스트 내용은 모두 타당, Follow-1~5 완료 후 일괄 추가**

문서의 테스트 케이스 목록은 모두 의미 있는 회귀 방지 테스트입니다.

추가 제안:

- `expand_setupAllWar43Xml_returnsDistinctTypes` — `hasSize(10)` + `doesNotHaveDuplicates()`
- `roleHierarchy_50_packageDeclaration_matchesStoragePath` — 5.0도 동일 검증

---

## 2. 항목 간 의존 관계

```
Follow-3  ──────────────────────────────────────── 독립
Follow-1 + Follow-4 ─────────────────────────────  묶어서 처리 (개수 계산 동시 확정)
Follow-2  ────────────────────────────────────────  독립 (Follow-3 이후 진행 권장)
Follow-5  ────────────────────────── Follow-1~4 완료 후
Follow-6  ────────────────────────── Follow-1~5 완료 후
```

---

## 3. 권장 구현 순서

| 단계 | 항목 | 근거 |
|---|---|---|
| Step 1 | Follow-3 | 독립적, 1줄 수정, 컴파일 오류 직결 |
| Step 2 | Follow-1 + Follow-4 | war43XmlTypes 변경 + distinct + 테스트 기대값 동시 확정 |
| Step 3 | Follow-2 | web.xml DelegatingFilterProxy + context-security.xml Bean 등록 |
| Step 4 | Follow-5 | 파일 수/조합 확정 후 description 최종 정리 |
| Step 5 | Follow-6 | 전체 테스트 보강 + `./gradlew test --rerun-tasks` 통과 확인 |

---

## 4. 최종 완료 기준

```
1. ./gradlew test --rerun-tasks 통과
2. role-hierarchy.java.tpl (4.3/5.0) package = sec.config
3. setup-war-43-xml에 javaconfig / rolehierarchy 미포함
4. setup-all-war-43-xml expand 결과 중복 없음, 개수 10
5. web.xml의 EgovSpringSecurityLoginFilter = DelegatingFilterProxy 방식
6. context-security.xml에 egovSpringSecurityLoginFilter Bean 등록
7. Tool description이 실제 expand() contract와 일치
```
