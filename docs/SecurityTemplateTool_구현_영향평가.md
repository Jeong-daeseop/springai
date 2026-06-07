# SecurityTemplateTool 구현 영향평가

작성일: 2026-05-24
목적: SecurityTemplateTool 미확인 버그 및 Gap 전체 목록화, 구현 우선순위·위험도 확정

---

## 발견된 문제 목록 (심각도 순)

---

## [🔴 위험 1] buildResult() securityType 값 불일치 — 가장 심각

### 현황

`ProjectInitializrService.buildResult()`가 Claude에게 추천하는 securityType 값:

| 조합 | buildResult() 추천값 |
|---|---|
| Boot + 5.0 | `"boot-security-filter-chain"` |
| Boot + 4.3 | `"boot-security-adapter"` |
| WAR + 5.0 | `"java-config-filter-chain"` |
| WAR + 4.3 | `"xml-legacy"` |

`SecurityTemplateService` switch가 실제 처리하는 케이스:

```java
case "webxmlfilter"       -> webXmlFilter();
case "contextsecurity"    -> contextSecurity(ver);
case "securitymapper"     -> securityMapper();
case "javaconfig"         -> ...
case "userdetailsservice" -> userDetailsService(pkg);
case "rolehierarchy"      -> roleHierarchy(pkg);
case "loginpage"          -> loginPage();
default                   -> unsupported(securityType);  // ← 항상 여기로
```

### 영향

`buildResult()`의 안내를 따라 Claude가 `getSecurityTemplate("boot-security-filter-chain", ...)` 호출 시
→ **항상 `unsupported()` 반환** → Security 템플릿 전혀 생성 불가.

완전한 무결함 경로가 존재하지 않는 상태.

### 수정 방향

**Option A** — `buildResult()` 추천값을 실제 케이스에 맞게 수정 (권장)

| 조합 | 수정 후 추천값 |
|---|---|
| Boot + 5.0 | `"javaconfig"` (egovVersion=5.0) |
| Boot + 4.3 | `"javaconfig"` (egovVersion=4.3) |
| WAR + 5.0 | `"javaconfig"` (egovVersion=5.0) |
| WAR + 4.3 | `"contextsecurity"` + `"webxmlfilter"` |

**Option B** — `SecurityTemplateService` switch 케이스를 `buildResult()` 값에 맞게 추가
- 케이스 추가 4개 (`boot-security-filter-chain`, `boot-security-adapter`, `java-config-filter-chain`, `xml-legacy`)
- 실질적으로 기존 메서드로 라우팅하는 alias 역할

**결론: Option A 채택** — 불필요한 alias 추가보다 단순화가 낫다. `buildResult()`에서 단계별 안내도 개선 가능.

---

## [🔴 위험 2] 누락 구현체 클래스 4종 — javaConfig 컴파일 실패

`javaConfig43()` / `javaConfig50()` 생성 코드에서 참조하지만 템플릿이 없는 클래스:

| 클래스 | 참조 위치 | 역할 |
|---|---|---|
| `EgovAuthenticationSuccessHandler` | javaConfig43/50 | 로그인 성공 후 URL 이동 |
| `EgovAuthenticationFailureHandler` | javaConfig43/50 | 로그인 실패 후 URL 이동 |
| `EgovAccessDeniedHandler` | javaConfig43/50 | 권한 없음(403) 처리 |
| `EgovSecurityMetadataSource` | javaConfig43/50 | COMTNROLEINFO URL-ROLE 동적 로드 |

### 영향

생성된 `EgovSecurityConfig.java` 단독으로 컴파일 불가. 프로젝트에 위 4개 클래스를 직접 구현해야 하는데 Claude가 이를 알 수 없음.

### 수정 방향

- `SecurityTemplateService`에 4개 클래스 템플릿 추가 (새 securityType 4개)
- `@Tool` description 및 `unsupported()` 메서드 목록 업데이트
- 또는 `javaConfig` 템플릿 자체에 간단한 기본 구현 인라인 포함 (파일 분리 어려움)

**결론: 별도 securityType으로 각각 추가.**
구현 복잡도가 낮고(핸들러 2개는 단순), `EgovSecurityMetadataSource`는 eGovFrame RTE 제공 여부 확인 필요.

---

## [🟡 위험 3] roleHierarchy 처리 방식 불일치

### 현황

| 위치 | 방식 |
|---|---|
| `contextSecurity43()` XML | 하드코딩 (`ROLE_USER > ROLE_ADMIN`) |
| `javaConfig43()` Java | 하드코딩 (`ROLE_USER > ROLE_ADMIN`) |
| `roleHierarchy()` 템플릿 | DB 동적 로드 (`COMTNROLES_HIERARCHY`) |
| `javaConfig50()` Java | 생성자 주입 (`RoleHierarchy roleHierarchy`) → `EgovRoleHierarchyConfig` 외부 의존 |

### 영향

- `javaConfig43()`의 하드코딩 roleHierarchy와 `roleHierarchy()` 템플릿을 동시 사용 시 Bean 중복
- 운영 중 권한 계층 변경 시 `contextSecurity43()` / `javaConfig43()` 재배포 필요 (DB 동적 로드 불가)

### 수정 방향

- `contextSecurity43()` XML: roleHierarchy 빈을 `EgovRoleHierarchyConfig` 참조 방식으로 교체
- `javaConfig43()` Java: roleHierarchy `@Bean` 인라인 선언 제거 → 생성자 주입(`RoleHierarchy roleHierarchy`)으로 전환 (javaConfig50과 동일 패턴)
- 수정 범위: `contextSecurity43()` 일부 + `javaConfig43()` roleHierarchy 블록

---

## [🟡 위험 4] RoleHierarchyImpl.setHierarchy() deprecated

### 현황

`roleHierarchy()` 템플릿 (719~729행):

```java
RoleHierarchyImpl impl = new RoleHierarchyImpl();
impl.setHierarchy(hierarchy.toString());  // ← Spring Security 6.x deprecated
return impl;
```

### 영향

Spring Security 6.x에서 deprecation 경고 발생. 실제 동작은 하지만 미래 제거 예정.

### 수정 방향

```java
// Spring Security 6.x 권장
return RoleHierarchyImpl.fromHierarchy(hierarchy.toString());
```

단, `fromHierarchy()`는 Spring Security 6.x에서 추가됨.
4.3(Spring Security 5.x) 호환 필요 시 `setHierarchy()` 유지. → **버전 분기 필요**:

```java
// 분기 방안 (템플릿에 주석으로 안내)
// Spring Security 5.x (eGovFrame 4.3): impl.setHierarchy(...)
// Spring Security 6.x (eGovFrame 5.0): RoleHierarchyImpl.fromHierarchy(...)
```

---

## [🟡 위험 5] webXmlFilter contextConfigLocation 중복 선언

### 현황

`webXmlFilter()` 스니펫이 `contextConfigLocation`을 새로 선언:

```xml
<context-param>
    <param-name>contextConfigLocation</param-name>
    <param-value>
        classpath*:egovframework/spring/context-*.xml
        classpath*:egovframework/spring/context-security.xml
    </param-value>
</context-param>
```

실제 eGovFrame WAR 프로젝트의 `web.xml`에는 이미 `contextConfigLocation`이 선언되어 있음.
그대로 붙여넣으면 **중복 선언** → Tomcat이 두 번째 선언 무시 또는 오류.

### 영향

Claude가 스니펫을 기계적으로 추가하면 web.xml에 `contextConfigLocation` 2개 선언 → 의도한 context-security.xml 미로드 가능.

### 수정 방향

스니펫 형식 변경: 전체 `<context-param>` 블록 대신 **추가할 value만 주석으로 안내**:

```xml
<!-- 기존 contextConfigLocation <param-value>에 아래 경로 추가 -->
<!--   classpath*:egovframework/spring/context-security.xml  -->
```

---

## [🟡 위험 6] 버전 분기 허점

### 현황

```java
case "javaconfig" -> ver.startsWith("4") ? javaConfig43(pkg) : javaConfig50(pkg);
case "contextsecurity" -> ver.startsWith("4") ? contextSecurity43() : contextSecurity50();
```

`ver`에 `"3.0"`, `"6.0"`, `""` 등 입력 시 모두 5.0으로 처리.

### 영향

낮음. 실사용에서 `"4.3"` 또는 `"5.0"` 이외 값이 들어올 가능성이 적음.

### 수정 방향

```java
case "javaconfig" -> (ver.startsWith("4") ? javaConfig43(pkg) : javaConfig50(pkg));
// 또는 명시적 처리
ver.equals("4.3") ? javaConfig43(pkg) : javaConfig50(pkg)
```

---

## 구현 순서 (의존성 기반)

```
[1단계] buildResult() securityType 불일치 수정 (부작용 없음 — 추천값 문자열만 변경)
        ↓

[2단계] roleHierarchy 처리 일관화
        contextSecurity43() roleHierarchy 빈 → 외부 의존 방식 주석 안내
        javaConfig43() roleHierarchy @Bean 인라인 → 생성자 주입 전환
        ↓

[3단계] RoleHierarchyImpl.setHierarchy() deprecated 수정
        roleHierarchy() 템플릿: 버전별 주석 안내 + 5.0 fromHierarchy() 사용
        ↓

[4단계] webXmlFilter contextConfigLocation 안내 방식 변경
        ↓

[5단계] 누락 구현체 클래스 4종 추가
        successHandler / failureHandler → 구현 단순 (새 securityType 2개)
        accessDeniedHandler → 구현 단순 (새 securityType 1개)
        EgovSecurityMetadataSource → eGovFrame RTE 제공 여부 먼저 확인 필요
        ↓

[6단계] 버전 분기 허점 수정 (선택, 영향 낮음)
```

---

## 변경 파일 및 범위

| 파일 | 변경 범위 |
|---|---|
| `ProjectInitializrService.java` | `buildResult()` securityType 추천값 4개 수정 |
| `SecurityTemplateService.java` | `javaConfig43()` roleHierarchy 전환, `roleHierarchy()` fromHierarchy 분기, `webXmlFilter()` 안내 방식, 누락 클래스 템플릿 추가 |
| `SecurityTemplateTool.java` | `@Tool` description securityType 목록 업데이트 |

---

## 비파괴성 검토

| 항목 | 기존 동작 영향 | 이유 |
|---|---|---|
| buildResult() securityType 수정 | **없음** | 안내 문자열만 변경, 실제 생성 로직 미변경 |
| javaConfig43() roleHierarchy 전환 | **없음** | 생성되는 템플릿 파일 변경, 기존 생성 완료 파일 불변 |
| roleHierarchy() fromHierarchy | **없음** | 신규 프로젝트 생성에만 적용 |
| 누락 클래스 템플릿 추가 | **없음** | 신규 securityType 추가, 기존 7개 미변경 |

---

## 최종 결정 사항

| 항목 | 결정 | 완료 |
|---|---|---|
| buildResult() securityType 불일치 | ✅ **구현** (1순위) | ✅ 2026-05-24 완료 |
| 누락 클래스 successHandler/failureHandler/accessDeniedHandler | ✅ **구현** (5단계) | ✅ 2026-05-24 완료 |
| EgovSecurityMetadataSource 템플릿 | 🔶 **eGovFrame RTE 제공 여부 확인 후 결정** | — 보류 |
| roleHierarchy 일관화 | ✅ **구현** (2단계) | ✅ 2026-05-24 완료 |
| RoleHierarchyImpl deprecated | ✅ **구현** (3단계, 버전 분기) | ✅ 2026-05-24 완료 (`fromHierarchy()` 5.0 적용) |
| webXmlFilter 안내 방식 | ✅ **구현** (4단계) | ✅ 2026-05-24 완료 (전체 블록 → 주석 안내) |
| 버전 분기 허점 | 🔶 **선택** (영향 낮음) | — 보류 |
