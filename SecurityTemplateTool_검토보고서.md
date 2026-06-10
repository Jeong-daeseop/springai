# SecurityTemplateTool 구현 완료 검토 보고서

작성일: 2026-06-09  
검토 대상: `com.krdevops.springai.tools.SecurityTemplateTool` 및 연관 구현체 전체

---

## 1. 검토 범위

| 레이어 | 파일 |
|---|---|
| Tool | `tools/SecurityTemplateTool.java` |
| Service | `service/SecurityTemplateService.java` |
| Factory | `service/security/SecurityFilePlanFactory.java` |
| Renderer | `service/security/template/SecurityTemplateRenderer.java` |
| Result | `service/security/SecurityResultBuilder.java` |
| Model | `model/SecuritySpec.java`, `VersionCapability.java`, `FilePlan.java`, `GenerationReport.java` |
| Infra | `service/initializr/VersionCapabilityResolver.java`, `FilePlanExecutor.java` |
| 템플릿 | `resources/templates/security/` 하위 25개 `.tpl` 파일 전체 |
| 테스트 | `service/security/SecurityFilePlanFactoryTest.java` |

---

## 2. 구조 평가 — 잘 된 부분

### 2-1. 아키텍처 계층 분리

```
SecurityTemplateTool (@Tool)
  └─ SecurityTemplateService          ← 조율자 (Phase 1/2 진입점 분리)
       ├─ VersionCapabilityResolver   ← 버전 문자열 → VersionCapability 변환
       ├─ SecurityFilePlanFactory     ← securityType → FilePlan 목록 조립
       │    └─ SecurityTemplateRenderer ← .tpl 파일 로드 + 변수 치환
       ├─ FilePlanExecutor            ← 디스크 쓰기 (EgovFileWriter 위임)
       └─ SecurityResultBuilder       ← GenerationReport → 사람 읽기 형태 변환
```

SRP(단일책임)와 DIP(의존역전)를 준수하는 명확한 계층 구조입니다.

### 2-2. 주요 설계 장점

**FilePlan 지연 평가**  
`Supplier<String> content`를 사용한 지연 평가로 렌더링 실패를 파일 단위로 격리합니다. 15개 파일 중 1개가 실패해도 나머지 14개는 정상 저장됩니다.

**중복 경로 제거**  
`LinkedHashSet`으로 조합 키워드(`setup-all-war-43` 등) 확장 시 중복 파일 경로를 자동 제거합니다.

**VersionCapabilityResolver 확장성**  
버전별 분기를 `VersionTable` 레코드 + `gte()` 비교로 처리하여 5.1이 출시되더라도 테이블 행 하나만 추가하면 됩니다.

**null/blank 입력 안전 처리**  
`VersionCapabilityResolver.resolve(null)` → 5.0 기본값, `SecuritySpec.of()` 팩토리에서 packageName/projectType 기본값 처리가 모두 포함되어 있습니다.

**@Tool description 정밀도**  
eGovFrame 4.3(javax/Spring 5.x)과 5.0(jakarta/Spring 6.x)의 차이, XML 방식과 Java Config 방식 혼용 불가 규칙, BootstrapMethodError 우회 방법 등 실전 지식이 상세히 기재되어 있습니다.

**테스트 구성**  
`SecurityFilePlanFactoryTest`에서 `expand()` / `plan()` / `toPlan()` / `renderSingle()` 케이스가 충실히 작성되어 있습니다.

---

## 3. 버그 — Critical (컴파일 오류 직결)

저장 경로(`SecurityFilePlanFactory.toPlan()`)와 템플릿 내 `package` 선언이 여러 곳에서 불일치합니다.  
생성된 파일을 프로젝트에 그대로 넣으면 컴파일 오류가 발생합니다.

### 3-1. `login-filter.java.tpl`

| 항목 | 현재 | 기대값 |
|---|---|---|
| 저장 경로 | `{pkg}/sec/filter/EgovSpringSecurityLoginFilter.java` | — |
| package 선언 | `package ${packageName}.security.filter;` | `package ${packageName}.sec.filter;` |

### 3-2. `success-handler.java.tpl`

| 항목 | 현재 | 기대값 |
|---|---|---|
| 저장 경로 | `{pkg}/sec/handler/EgovAuthenticationSuccessHandler.java` | — |
| package 선언 | `package ${packageName}.security;` | `package ${packageName}.sec.handler;` |

### 3-3. `failure-handler.java.tpl`

| 항목 | 현재 | 기대값 |
|---|---|---|
| 저장 경로 | `{pkg}/sec/handler/EgovAuthenticationFailureHandler.java` | — |
| package 선언 | `package ${packageName}.security;` | `package ${packageName}.sec.handler;` |

### 3-4. `access-denied-handler.java.tpl` (egov43 / egov50 두 파일 모두)

| 항목 | 현재 | 기대값 |
|---|---|---|
| 저장 경로 | `{pkg}/sec/handler/EgovAccessDeniedHandler.java` | — |
| package 선언 | `package ${packageName}.security;` | `package ${packageName}.sec.handler;` |

### 3-5. `session-mapping.java.tpl`

| 항목 | 현재 | 기대값 |
|---|---|---|
| 저장 경로 | `{pkg}/uat/uia/service/impl/EgovSessionMapping.java` | — |
| package 선언 | `package ${packageName}.security;` | `package ${packageName}.uat.uia.service.impl;` |

### 3-6. `egov43/java-config.java.tpl` — import 연쇄 불일치

위 3-2~3-5 불일치가 javaConfig 4.3의 import에 그대로 반영되어 있습니다.

```java
// 현재 (잘못됨)
import ${packageName}.security.EgovAuthenticationSuccessHandler;
import ${packageName}.security.EgovAuthenticationFailureHandler;
import ${packageName}.security.EgovAccessDeniedHandler;
import ${packageName}.service.EgovUserDetailsServiceImpl;

// 기대값
import ${packageName}.sec.handler.EgovAuthenticationSuccessHandler;
import ${packageName}.sec.handler.EgovAuthenticationFailureHandler;
import ${packageName}.sec.handler.EgovAccessDeniedHandler;
import ${packageName}.sec.service.impl.EgovUserDetailsServiceImpl;
```

---

## 4. 버그 — Minor

### 4-1. 5.0에서 `userdetailsservice` + `outputPath` 조합

`setup-war-50`에 `userdetailsservice`가 포함되지 않아 조합 키워드로는 발생하지 않습니다.  
그러나 단독 호출 시 문제가 발생합니다.

```
getSecurityTemplate("userdetailsservice", "egovframework.let.emp", "5.0", "/path/to/project", "war")
```

`SecurityTemplateRenderer`는 `egov50/user-details-service-notice.tpl`(안내 텍스트)을 반환하지만,  
`SecurityFilePlanFactory.toPlan()`의 저장 경로는 `.java` 파일로 고정되어 있습니다.

```
결과: 안내 텍스트가 .../sec/service/impl/EgovUserDetailsServiceImpl.java 로 저장됨
```

**권장 수정:** 5.0의 경우 저장 경로를 `docs/security/user-details-service-notice.md`로 분기하거나, outputPath 사용 시 경고 메시지만 반환하도록 처리합니다.

---

## 5. 설계 관찰 (버그는 아님)

### 5-1. `role-hierarchy.java.tpl` 중복 (egov43 / egov50)

두 파일의 차이는 마지막 두 줄뿐입니다.

```java
// egov43 — Spring Security 5.x
RoleHierarchyImpl impl = new RoleHierarchyImpl();
impl.setHierarchy(hierarchy.toString());
return impl;

// egov50 — Spring Security 6.x (setHierarchy deprecated)
return RoleHierarchyImpl.fromHierarchy(hierarchy.toString());
```

버전 분기 자체는 올바릅니다. 나머지 90%가 중복이므로 공통 부분을 하나의 base tpl로 통합하거나, 분기 이유를 주석으로 명시해두면 유지보수 비용이 줄어듭니다.

### 5-2. 템플릿 통합 테스트 부재

`SecurityFilePlanFactoryTest`는 `SecurityTemplateRenderer`를 Mock 처리합니다.  
실제 클래스패스 `.tpl` 파일 로딩 + 변수 치환 + 파일명 정합성을 검증하는 통합 테스트가 없어, 위 Critical 버그들이 테스트 단계에서 잡히지 않았습니다.

다음과 같은 테스트를 추가하는 것을 권장합니다.

```java
@SpringBootTest
class SecurityTemplateRendererIntegrationTest {

    @Autowired SecurityTemplateRenderer renderer;
    @Autowired VersionCapabilityResolver resolver;

    @Test
    void render_allTypes_4_3_containsCorrectPackage() {
        SecuritySpec spec = SecuritySpec.of(
            "loginfilter", "egovframework.let.emp", "war", null, resolver.resolve("4.3"));
        String result = renderer.render("loginfilter", spec);
        assertThat(result).contains("package egovframework.let.emp.sec.filter");
    }
}
```

---

## 6. 수정 필요 파일 요약

| 우선순위 | 파일 | 수정 내용 |
|---|---|---|
| **Critical** | `common/login-filter.java.tpl` | `package … .security.filter` → `.sec.filter` |
| **Critical** | `egov43/success-handler.java.tpl` | `package … .security` → `.sec.handler` |
| **Critical** | `egov43/failure-handler.java.tpl` | `package … .security` → `.sec.handler` |
| **Critical** | `egov43/access-denied-handler.java.tpl` | `package … .security` → `.sec.handler` |
| **Critical** | `egov50/access-denied-handler.java.tpl` | `package … .security` → `.sec.handler` |
| **Critical** | `common/session-mapping.java.tpl` | `package … .security` → `.uat.uia.service.impl` |
| **Critical** | `egov43/java-config.java.tpl` | import 경로 4개 수정 (security → sec.handler / sec.service.impl) |
| Minor | `SecurityFilePlanFactory.toPlan()` | 5.0 `userdetailsservice` 저장 경로 분기 추가 |
| 권장 | `SecurityTemplateRendererIntegrationTest` 신규 추가 | 템플릿 로딩 + package 선언 정합성 검증 |

---

## 7. 결론

전체 설계 구조(SRP/DIP, 지연 평가, 버전 분기, 조합 키워드)는 잘 완성되어 있습니다.  
Critical 버그는 모두 **템플릿 파일 내 `package` 선언과 `FilePlanFactory`의 저장 경로 불일치**에 집중되어 있으며, 수정 작업 자체는 각 파일 1줄씩 총 7곳입니다.  
수정 후 통합 테스트를 추가하면 동일한 유형의 오류가 재발하지 않는 안전한 구조가 됩니다.
