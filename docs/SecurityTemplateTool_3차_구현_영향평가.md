# SecurityTemplateTool 3차 구현 영향평가

작성일: 2026-05-24
목적: eGovFrame 4.3 Security 분석에서 발견된 버그 5건 수정 착수 전 변경 범위·위험도·구현 순서 확정

---

## 발견된 버그 목록

| # | 심각도 | 항목 | 영향 |
|---|---|---|---|
| A | 🔴 | `javaConfig43()` cross-package import 4건 누락 | 생성 코드 컴파일 실패 |
| B | 🔴 | `javaConfig50()` cross-package import 4건 동일 누락 | 생성 코드 컴파일 실패 |
| C | 🔴 | `contextSecurity43()` `ref="egovUserDetailsService"` → 실제 빈명 `egovUserDetailsServiceImpl` | 서버 기동 시 `NoSuchBeanDefinitionException` |
| D | 🟡 | `successhandler`/`failurehandler` XML 방식에서 불필요 + RTE 클래스 충돌 안내 없음 | Claude가 불필요 파일 생성 / 혼란 |
| E | 🟡 | `javaConfig43()` `WebExpressionVoter` 누락 (XML contextSecurity43과 불일치) | SpEL 접근 규칙 미평가 (실사용 영향 낮음) |

---

## [🔴 버그 A] `javaConfig43()` — cross-package import 4건 누락

### 현황

`javaConfig43()` 생성 코드에서 아래 4개 클래스를 사용하지만 import가 없다.

| 클래스명 | 실제 위치 | 사용 위치 |
|---|---|---|
| `EgovUserDetailsServiceImpl` | `{pkg}.service` | `@Autowired` 필드 (369행) |
| `EgovAccessDeniedHandler` | `{pkg}.security` | `@Autowired` 필드 (377행) |
| `EgovAuthenticationSuccessHandler` | `{pkg}.security` | `@Bean` 반환 타입 (465행) |
| `EgovAuthenticationFailureHandler` | `{pkg}.security` | `@Bean` 반환 타입 (470행) |

현재 import 블록(328~345행)에는 Spring Security / Spring Framework 표준 라이브러리만 있고,
같은 프로젝트 내 `{pkg}.*` 패키지 참조가 전혀 없다.

### 영향

`javaconfig` securityType으로 생성된 `EgovSecurityConfig.java`에서:
```
cannot find symbol: class EgovUserDetailsServiceImpl
cannot find symbol: class EgovAccessDeniedHandler
cannot find symbol: class EgovAuthenticationSuccessHandler
cannot find symbol: class EgovAuthenticationFailureHandler
```
4개 `cannot find symbol` → **컴파일 즉시 실패**.

`successhandler` · `failurehandler` · `accessdeniedhandler` · `userdetailsservice` 파일을 모두 생성해도
`EgovSecurityConfig.java` 자체가 import 없으면 컴파일 불가.

### 수정 방향

import 블록에 4줄 추가 (`%s` = `pkg`):

```java
import %s.security.EgovAuthenticationSuccessHandler;
import %s.security.EgovAuthenticationFailureHandler;
import %s.security.EgovAccessDeniedHandler;
import %s.service.EgovUserDetailsServiceImpl;
```

현재 `formatted(pkg)` → `formatted(pkg, pkg, pkg, pkg, pkg)` (5개로 확장).

**`%s` 인자 순서 (변경 후):**

| 위치 | `%s` 용도 |
|---|---|
| `package %s.config;` | pkg |
| `import %s.security.EgovAuthenticationSuccessHandler;` | pkg |
| `import %s.security.EgovAuthenticationFailureHandler;` | pkg |
| `import %s.security.EgovAccessDeniedHandler;` | pkg |
| `import %s.service.EgovUserDetailsServiceImpl;` | pkg |

### 변경 범위

| 위치 | 변경 내용 |
|---|---|
| `javaConfig43()` import 블록 (344행 뒤) | 4줄 import 추가 |
| `javaConfig43()` `.formatted(pkg)` (476행) | → `.formatted(pkg, pkg, pkg, pkg, pkg)` |

---

## [🔴 버그 B] `javaConfig50()` — cross-package import 4건 동일 누락

### 현황

`javaConfig50()` 생성 코드에서 동일한 4개 클래스 사용 + import 없음.

| 클래스명 | 사용 위치 |
|---|---|
| `EgovUserDetailsServiceImpl` | 필드 선언 (523행), 생성자 파라미터 (530행) |
| `EgovAccessDeniedHandler` | 필드 선언 (527행), 생성자 파라미터 (532행) |
| `EgovAuthenticationSuccessHandler` | `@Bean` 반환 타입 (627행) |
| `EgovAuthenticationFailureHandler` | `@Bean` 반환 타입 (632행) |

### 영향

버그 A와 동일 — 컴파일 즉시 실패.

### 수정 방향

버그 A와 동일한 4줄 import 추가.

**`%s` 인자 순서 (변경 후):**

| 위치 | `%s` 용도 |
|---|---|
| `package %s.config;` | pkg |
| `import %s.security.EgovAuthenticationSuccessHandler;` | pkg |
| `import %s.security.EgovAuthenticationFailureHandler;` | pkg |
| `import %s.security.EgovAccessDeniedHandler;` | pkg |
| `import %s.service.EgovUserDetailsServiceImpl;` | pkg |

### 변경 범위

| 위치 | 변경 내용 |
|---|---|
| `javaConfig50()` import 블록 (499행 뒤) | 4줄 import 추가 |
| `javaConfig50()` `.formatted(pkg)` (638행) | → `.formatted(pkg, pkg, pkg, pkg, pkg)` |

---

## [🔴 버그 C] `contextSecurity43()` — `egovUserDetailsService` 빈 ref 불일치

### 현황

XML 템플릿 (182~184행):
```xml
<beans:bean id="egovAuthenticationProvider"
    class="egovframework.rte.fdl.security.userdetails.EgovUserDetailsHelper">
    <beans:property name="userDetailsService" ref="egovUserDetailsService"/>
```

`ref="egovUserDetailsService"` → Spring이 이 이름의 빈을 컨테이너에서 조회.

`userdetailsservice` securityType 생성 코드 (664~665행):
```java
@Service
public class EgovUserDetailsServiceImpl implements UserDetailsService {
```

Spring `@Service` 기본 빈 이름 규칙: **클래스명 첫 글자 소문자** = `egovUserDetailsServiceImpl`.

`egovUserDetailsService` ≠ `egovUserDetailsServiceImpl` → **빈 이름 불일치**.

### 영향

서버 기동 시:
```
org.springframework.beans.factory.NoSuchBeanDefinitionException:
No bean named 'egovUserDetailsService' available
```

XML 방식 + `userdetailsservice` securityType을 함께 사용하는 표준 시나리오에서 **항상 발생**.

### 수정 방향

**Option A — XML ref 수정 (권장)**

```xml
<!-- 변경 전 -->
<beans:property name="userDetailsService" ref="egovUserDetailsService"/>

<!-- 변경 후 -->
<beans:property name="userDetailsService" ref="egovUserDetailsServiceImpl"/>
```

- 영향 범위: `contextSecurity43()` 1줄 수정
- `userdetailsservice` securityType 파일 무변경

**Option B — `@Service` 빈 이름 명시**

```java
@Service("egovUserDetailsService")
public class EgovUserDetailsServiceImpl implements UserDetailsService {
```

- `userdetailsservice` securityType 파일 수정 필요
- Java Config 방식에서는 빈 이름을 타입으로 주입하므로 영향 없음
- XML ref와 Java Config를 동시에 지원하는 경우 이름 명시가 명확할 수 있음

**결론: Option A 채택** — 수정 범위 최소화, Java Config 방식에 영향 없음.

### 변경 범위

| 위치 | 변경 내용 |
|---|---|
| `contextSecurity43()` 183행 | `ref="egovUserDetailsService"` → `ref="egovUserDetailsServiceImpl"` |

---

## [🟡 이슈 D] `successhandler`/`failurehandler` XML 방식 사용 주의 안내 없음

### 현황

`contextSecurity43()` XML은 핸들러를 **eGovFrame RTE 제공 클래스**로 직접 사용:
```xml
<beans:bean id="loginSuccessHandler"
    class="egovframework.rte.fdl.security.userdetails.EgovAuthenticationSuccessHandler">
```

`successhandler` securityType이 생성하는 클래스:
```java
package {pkg}.security;
public class EgovAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler
```

→ **동일 클래스명, 다른 패키지** — XML 방식에서 사용자 생성 클래스로 교체하려면 XML의 class 속성을 fully-qualified name으로 수동 변경 필요.
→ `@Tool` description에 이 관계 안내 없음 → Claude가 XML 방식에서도 `successhandler`를 불필요하게 생성 가능.

### 영향

- 런타임 오류 없음 (사용자가 XML ref를 수정하지 않으면 RTE 클래스 사용)
- 혼란 유발: 왜 `successhandler`로 생성한 클래스가 적용 안 되는지 디버깅 시간 낭비

### 수정 방향

`@Tool` description의 `successhandler` / `failurehandler` 설명에 안내 추가:

```
successHandler / failureHandler → Java Config 방식 전용.
  XML 방식(contextSecurity)에서는 eGovFrame RTE가 핸들러 클래스를 직접 제공하므로 불필요.
  XML에서 커스텀 핸들러 교체 시 class 속성을 fully-qualified name으로 수정 필요.
```

### 변경 범위

| 위치 | 변경 내용 |
|---|---|
| `SecurityTemplateTool.java` `@Tool` description 39~47행 | successHandler / failureHandler 설명에 "Java Config 방식 전용" 안내 추가 |

---

## [🟡 이슈 E] `javaConfig43()` — `WebExpressionVoter` 누락

### 현황

`contextSecurity43()` XML의 `accessDecisionManager`:
```xml
<beans:list>
    <beans:bean class="...RoleHierarchyVoter"/>
    <beans:bean class="...WebExpressionVoter"/>   ← 포함
    <beans:bean class="...AuthenticatedVoter"/>
</beans:list>
```

`javaConfig43()` Java Config의 `accessDecisionManager()`:
```java
return new AffirmativeBased(Arrays.asList(
    new RoleHierarchyVoter(roleHierarchy),
    new AuthenticatedVoter()
    // WebExpressionVoter 없음
));
```

`WebExpressionVoter` 역할: `access="hasRole('ADMIN')"` 같은 SpEL 표현식 평가.

### 영향 검토

`javaConfig43()`의 `authorizeRequests()`는 SpEL이 아닌 Java 메서드 체인:
```java
.antMatchers("/uat/uia/**").permitAll()
.anyRequest().authenticated()
```
SpEL 표현식(`hasRole()`, `isAuthenticated()` 등)을 직접 쓰지 않으므로 `WebExpressionVoter` 없어도 동작.

단, 개발자가 `javaConfig43()`에 SpEL 규칙을 추가하면 평가 불가.
XML 방식과 달리 `WebExpressionVoter` 없는 이유 설명도 없음.

### 수정 방향

**🔶 선택 — 이번 구현에서 제외 (영향 낮음)**

이유:
- `authorizeRequests()` Java 체인 방식에서 SpEL 없이 동작
- `WebExpressionVoter`를 추가해도 기존 동작 변화 없음
- 개발자가 SpEL이 필요하면 직접 추가 가능

대신 `javaConfig43()` JavaDoc에 주석 안내:
```java
// WebExpressionVoter 미포함 — SpEL 표현식 사용 시 직접 추가 필요
// contextSecurity43 XML과 달리 Java 메서드 체인 authorizeRequests 방식 사용
```

---

## 구현 순서 (의존성 기반)

```
[1단계] contextSecurity43() ref 수정 (1줄, 부작용 없음)
        ref="egovUserDetailsService" → ref="egovUserDetailsServiceImpl"
        ↓

[2단계] javaConfig43() import 4건 + formatted 인자 수정
        import 추가 4줄 / formatted(pkg) → formatted(pkg, pkg, pkg, pkg, pkg)
        ↓

[3단계] javaConfig50() import 4건 + formatted 인자 수정 (2단계와 독립, 동시 가능)
        ↓

[4단계] SecurityTemplateTool.java @Tool description 수정
        successHandler / failureHandler "Java Config 방식 전용" 안내 추가
        ↓

[5단계] javaConfig43() JavaDoc WebExpressionVoter 주석 추가 (선택)
```

---

## 변경 파일 및 범위

| 파일 | 변경 위치 | 변경 규모 |
|---|---|---|
| `SecurityTemplateService.java` | `contextSecurity43()` 183행 | 극소 — ref 값 1곳 |
| `SecurityTemplateService.java` | `javaConfig43()` import 블록 + formatted | 소 — 4줄 추가 + formatted 인자 수정 |
| `SecurityTemplateService.java` | `javaConfig50()` import 블록 + formatted | 소 — 4줄 추가 + formatted 인자 수정 |
| `SecurityTemplateTool.java` | `@Tool` description 39~47행 | 소 — 안내 문구 추가 |

---

## 비파괴성 검토

| 항목 | 기존 동작 영향 | 이유 |
|---|---|---|
| `contextSecurity43()` ref 수정 | **없음** | 기존 생성 완료 XML 파일 불변. 신규 생성 시 올바른 ref 적용 |
| `javaConfig43()` import 추가 | **없음** | 추가 import = 기존 클래스 미영향. 생성 코드 변경만 |
| `javaConfig50()` import 추가 | **없음** | 동일 |
| `formatted()` 인자 수 변경 | **없음** | 기존 `%s`(1개)와 추가 `%s`(4개)가 모두 동일 `pkg` 값 |
| `@Tool` description 수정 | **없음** | Claude 설명 개선, 실제 생성 로직 미변경 |

---

## 최종 결정 사항

| 항목 | 결정 | 완료 |
|---|---|---|
| `javaConfig43()` cross-package import 4건 | ✅ **구현** (2단계) | ✅ 2026-05-24 완료 |
| `javaConfig50()` cross-package import 4건 | ✅ **구현** (3단계) | ✅ 2026-05-24 완료 |
| `contextSecurity43()` ref 불일치 | ✅ **구현** (1단계) | ✅ 2026-05-24 완료 |
| `successhandler`/`failurehandler` description 안내 | ✅ **구현** (4단계) | ✅ 2026-05-24 완료 |
| `WebExpressionVoter` JavaDoc 주석 추가 | ✅ **구현** (5단계) | ✅ 2026-05-24 완료 |
