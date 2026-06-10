    # SecurityTemplateTool 구현계획서

> 작성일: 2026-06-09  
> 대상: `springai-mcp` / `SecurityTemplateTool`  
> 기준 문서: `SecurityTemplateTool-vs-ProjectInitializrTool-비교분석.md`

---

## 1. 구현 목표

`SecurityTemplateTool`을 현재의 문자열 반환 전용 God Class 구조에서
`ProjectInitializrTool`과 동일한 FilePlan 기반 생성 파이프라인으로 전환한다.

핵심 목표는 다음과 같다.

| 목표 | 설명 |
|---|---|
| Service 슬림화 | `SecurityTemplateService`를 1,608줄 템플릿 보관소에서 얇은 조율자로 변경 |
| 템플릿 외부화 | Java Text Block 하드코딩을 `.tpl` 리소스로 분리 |
| 직접 저장 지원 | `outputPath`가 있으면 파일을 직접 생성 |
| 하위 호환 유지 | `outputPath == null`이면 기존처럼 문자열 반환 |
| 조합 생성 지원 | `setup-war-43`, `setup-war-50`, `setup-filters` 같은 묶음 생성 지원 |
| 공통 인프라 재사용 | `VersionCapabilityResolver`, `FilePlanExecutor`, `GenerationHistoryRecorder` 재사용 |

---

## 2. 현재 구조 요약

```text
SecurityTemplateTool
    ↓
SecurityTemplateService
    └── switch(securityType)
        ├── webXmlFilter()
        ├── contextSecurity43()
        ├── contextSecurity50()
        ├── javaConfig43()
        ├── javaConfig50()
        └── ...
```

현재 구조의 한계는 명확하다.

| 문제 | 영향 |
|---|---|
| 1개 Service에 16종 템플릿 포함 | 수정 영향 범위가 넓고 리뷰가 어려움 |
| Java Text Block 하드코딩 | 템플릿 변경 시 Java 재컴파일 필요 |
| 파일 저장 미지원 | Claude가 문자열을 받아 다시 저장해야 함 |
| 조합 생성 미지원 | 보안 전체 셋업 시 Tool 호출이 여러 번 필요 |
| 검증/이력 부재 | 생성 성공 여부와 생성 이력을 추적하기 어려움 |

---

## 3. 목표 아키텍처

```text
SecurityTemplateTool
    ↓
SecurityTemplateService
    ├── VersionCapabilityResolver          // 기존 initializr 공통 컴포넌트 재사용
    ├── SecurityFilePlanFactory            // securityType → FilePlan 목록 조립
    │   ├── SecurityTemplateRenderer        // .tpl 로드 + 변수 치환
    │   └── SecurityPathResolver            // securityType별 저장 경로 결정
    ├── FilePlanExecutor                    // 기존 initializr 공통 컴포넌트 재사용
    ├── ProjectValidator                    // 1차는 FilePlan 검증만 재사용
    ├── SecurityResultBuilder               // 문자열/저장 결과 응답 생성
    └── GenerationHistoryRecorder           // 기존 initializr 공통 컴포넌트 재사용
```

`ProjectInitializrService`의 흐름을 그대로 따른다.

```text
입력 정규화
  → VersionCapability 해석
  → SecuritySpec 생성
  → SecurityFilePlanFactory.plan()
  → ProjectValidator.validatePlans()
  → outputPath 유무 분기
      ├── null: 단일 템플릿 문자열 반환
      └── 값 있음: FilePlanExecutor.execute()
  → 이력 기록
  → 결과 메시지 반환
```

---

## 4. 공개 API 설계

### 4-1. Tool 메서드

기존 3개 인자 호출은 유지하고, 저장/프로젝트 타입 인자를 추가한다.

```java
@Tool(description = "eGovFrame Security 템플릿 생성 또는 파일 저장")
public String getSecurityTemplate(
        String securityType,
        String packageName,
        String egovVersion,
        String outputPath,
        String projectType
) {
    return service.getSecurityTemplate(
            securityType, packageName, egovVersion, outputPath, projectType);
}
```

하위 호환을 위해 Service 레벨에는 기존 시그니처를 남긴다.

```java
public String getSecurityTemplate(String securityType, String packageName, String egovVersion) {
    return getSecurityTemplate(securityType, packageName, egovVersion, null, null);
}
```

### 4-2. 파라미터 기본값

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| `securityType` | 필수 | 단일 템플릿 또는 조합 키워드 |
| `packageName` | `egovframework.let.sample` | Java 템플릿 패키지 |
| `egovVersion` | `5.0` | `4.3`, `5.0`, `latest` 지원 |
| `outputPath` | `null` | `null`이면 문자열 반환, 값이 있으면 직접 저장 |
| `projectType` | `war` | 1차 대상은 WAR, Boot는 확장 슬롯만 확보 |

---

## 5. securityType 정책

### 5-1. 단일 생성 키

| securityType | 생성 대상 | 4.3 | 5.0 | 저장 경로 |
|---|---|---:|---:|---|
| `webXmlFilter` | web.xml 필터 체인 조각 | O | O | `src/main/webapp/WEB-INF/web.xml.fragment` |
| `contextSecurity` | Spring Security XML | O | O | `src/main/resources/egovframework/spring/context-security.xml` |
| `securityMapper` | URL-ROLE SQL | O | O | `src/main/resources/egovframework/sqlmap/security/security-mapper.sql` |
| `javaConfig` | `EgovProjectSecurityConfig.java` | O | O | `src/main/java/{pkg}/config/EgovProjectSecurityConfig.java` |
| `userDetailsService` | `EgovUserDetailsServiceImpl.java` | O | 안내 메시지 | `src/main/java/{pkg}/sec/service/impl/EgovUserDetailsServiceImpl.java` |
| `roleHierarchy` | `EgovRoleHierarchyConfig.java` | O | O | `src/main/java/{pkg}/sec/config/EgovRoleHierarchyConfig.java` |
| `loginFilter` | `EgovSpringSecurityLoginFilter.java` | O | O | `src/main/java/{pkg}/sec/filter/EgovSpringSecurityLoginFilter.java` |
| `logoutFilter` | `EgovSpringSecurityLogoutFilter.java` | O | O | `src/main/java/{pkg}/sec/filter/EgovSpringSecurityLogoutFilter.java` |
| `loginPolicyFilter` | `EgovLoginPolicyFilter.java` | O | O | `src/main/java/{pkg}/uat/uap/filter/EgovLoginPolicyFilter.java` |
| `sessionMapping` | `EgovSessionMapping.java` | O | O | `src/main/java/{pkg}/uat/uia/service/impl/EgovSessionMapping.java` |
| `successHandler` | 성공 핸들러 | O | 제한 | `src/main/java/{pkg}/sec/handler/EgovAuthenticationSuccessHandler.java` |
| `failureHandler` | 실패 핸들러 | O | 제한 | `src/main/java/{pkg}/sec/handler/EgovAuthenticationFailureHandler.java` |
| `accessDeniedHandler` | 접근거부 핸들러 | O | O | `src/main/java/{pkg}/sec/handler/EgovAccessDeniedHandler.java` |
| `loginPage` | JSP 로그인 화면 | O | O | `src/main/webapp/WEB-INF/jsp/egovframework/com/uat/uia/egovLoginUsr.jsp` |
| `userDetailsHelper` | Helper 사용 예시 | O | O | `docs/security/user-details-helper-example.md` |
| `userDetailsHelperXml` | Helper XML | O | O | `src/main/resources/egovframework/spring/context-egovuserdetailshelper.xml` |

`안내 메시지`는 5.0에서 해당 파일이 불필요함을 설명하는 메시지를 반환한다는 의미다.
5.0 `userDetailsService` 호출 시 반환 예시:

```
5.0에서는 EgovUserDetailsServiceImpl이 불필요합니다.
RTE EgovSecurityConfiguration이 EgovJdbcUserDetailsManager를 자동 구성합니다.
사용자 조회 쿼리는 context-security.xml의 jdbcUsersByUsernameQuery 프로퍼티로 설정하세요.
```

### 5-2. 조합 생성 키

| securityType | 포함 파일 | 용도 |
|---|---|---|
| `setup-war-43` | `webXmlFilter`, `contextSecurity`, `javaConfig`, `userDetailsService`, `roleHierarchy`, `loginPage`, `userDetailsHelperXml` | eGovFrame 4.3 WAR 보안 기본 셋업 |
| `setup-war-50` | `contextSecurity`, `javaConfig`, `roleHierarchy`, `loginPage`, `userDetailsHelperXml` | eGovFrame 5.0 WAR 보안 기본 셋업 |
| `setup-filters` | `loginFilter`, `logoutFilter`, `loginPolicyFilter`, `sessionMapping` | DB 인증 필터/세션 매핑 셋업 |
| `setup-handlers-43` | `successHandler`, `failureHandler`, `accessDeniedHandler` | 4.3 Java Config 핸들러 셋업 |
| `setup-all-war-43` | `setup-war-43` + `setup-filters` + `setup-handlers-43` + `securityMapper` | 4.3 전체 보안 셋업 |
| `setup-all-war-50` | `setup-war-50` + `setup-filters` + `accessDeniedHandler` + `securityMapper` | 5.0 전체 보안 셋업 |

---

## 6. 신규/변경 클래스 설계

### 6-1. `SecuritySpec`

위치: `com.krdevops.springai.model.SecuritySpec`

```java
public record SecuritySpec(
        String securityType,
        String packageName,
        String projectType,
        Path root,
        VersionCapability capability
) {
    public static SecuritySpec of(
            String securityType,
            String packageName,
            String projectType,
            String outputPath,
            VersionCapability capability) {
        ...
    }
}
```

역할:

| 책임 | 내용 |
|---|---|
| 입력 정규화 | blank 값 기본값 처리 |
| 버전 정보 보관 | `VersionCapability` 포함 |
| 루트 경로 보관 | `outputPath`를 `Path root`로 변환 |
| 패키지 경로 계산 | `packageName.replace('.', '/')` helper 제공 |

### 6-2. `SecurityFilePlanFactory`

위치: `com.krdevops.springai.service.security.SecurityFilePlanFactory`

역할:

| 메서드 | 책임 |
|---|---|
| `List<FilePlan> plan(SecuritySpec spec)` | 단일/조합 securityType을 FilePlan 목록으로 변환 |
| `String renderSingle(SecuritySpec spec)` | 하위 호환 문자열 반환용 단일 템플릿 렌더링 |
| `List<String> expand(String securityType, VersionCapability cap)` | 조합 키워드 확장 |
| `FilePlan toPlan(String type, SecuritySpec spec)` | securityType별 저장 경로와 렌더러 연결 |

구현 원칙:

- `switch`는 `SecurityFilePlanFactory`에만 둔다.
- `SecurityTemplateService`에는 securityType별 템플릿 메서드를 두지 않는다.
- 조합 키워드는 중복 파일 경로가 생기지 않도록 `LinkedHashSet`으로 확장한다.

### 6-3. `SecurityTemplateRenderer`

위치: `com.krdevops.springai.service.security.template.SecurityTemplateRenderer`

역할:

| 메서드 | 책임 |
|---|---|
| `String render(String templateName, SecuritySpec spec)` | `.tpl` 로드 후 변수 치환 |
| `String templateName(String type, VersionCapability cap)` | 버전별 템플릿 파일명 결정 |

초기 구현은 기존 `DefaultStaticTemplateRenderer`와 `ClassPathTemplateLoader`를 재사용한다.
필요할 경우 security 전용 wrapper만 둔다.

### 6-4. `SecurityResultBuilder`

위치: `com.krdevops.springai.service.security.SecurityResultBuilder`

역할:

| 상황 | 반환 |
|---|---|
| 문자열 반환 | 기존과 동일하게 템플릿 내용만 반환 |
| 파일 저장 성공 | 생성 경로, 파일 수, 버전, projectType, 다음 작업 안내 |
| 일부 실패 | 성공 파일 목록 + 실패 파일 목록 + 원인 |
| 지원하지 않는 키 | 지원 목록과 예시 호출 반환 |

### 6-5. `SecurityTemplateService`

변경 후 역할:

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityTemplateService {

    private final VersionCapabilityResolver resolver;
    private final SecurityFilePlanFactory factory;
    private final FilePlanExecutor executor;
    private final ProjectValidator validator;
    private final SecurityResultBuilder resultBuilder;
    private final GenerationHistoryRecorder recorder;

    public String getSecurityTemplate(String securityType, String packageName, String egovVersion) {
        return getSecurityTemplate(securityType, packageName, egovVersion, null, null);
    }

    public String getSecurityTemplate(String securityType, String packageName, String egovVersion,
                                      String outputPath, String projectType) {
        VersionCapability cap = resolver.resolve(egovVersion);
        SecuritySpec spec = SecuritySpec.of(securityType, packageName, projectType, outputPath, cap);

        if (outputPath == null || outputPath.isBlank()) {
            return factory.renderSingle(spec);
        }

        List<FilePlan> plans = factory.plan(spec);
        validator.validatePlans(plans);
        GenerationReport report = executor.execute(spec.toProjectSpec(), plans);
        recorder.record(spec.toProjectSpec(), report.totalFiles(), report.errors().size());
        return resultBuilder.build(spec, report);
    }
}
```

`FilePlanExecutor`가 현재 `ProjectSpec` 기반이면 1차 구현에서는 `SecuritySpec.toProjectSpec()` 어댑터를 둔다.
장기적으로는 `FilePlanExecutor.execute(Path root, List<FilePlan> plans)` 오버로드를 추가할 수 있다.

---

## 7. 템플릿 리소스 구조

위치: `src/main/resources/templates/security`

```text
templates/security/
  common/
    web-xml-filter.tpl
    security-mapper.sql.tpl
    login-page.jsp.tpl
    role-hierarchy.java.tpl
    login-filter.java.tpl
    logout-filter.java.tpl
    login-policy-filter.java.tpl
    session-mapping.java.tpl
    user-details-helper.md.tpl
    user-details-helper-xml.tpl

  egov43/
    context-security.xml.tpl
    java-config.java.tpl
    user-details-service.java.tpl
    success-handler.java.tpl
    failure-handler.java.tpl
    access-denied-handler.java.tpl

  egov50/
    context-security.xml.tpl
    java-config.java.tpl
    access-denied-handler.java.tpl
```

템플릿 변수는 `ProjectInitializr`와 동일한 스타일을 우선한다.

| 변수 | 예시 |
|---|---|
| `${packageName}` | `egovframework.let.sample` |
| `${packagePath}` | `egovframework/let/sample` |
| `${basePackage}` | `sample` |
| `${egovVersion}` | `4.3` / `5.0` |
| `${javaxOrJakarta}` | `javax` / `jakarta` |

---

## 8. 구현 단계

### Phase 1. 구조 분리 + 템플릿 외부화 (통합)

목표: 외부 동작을 바꾸지 않으면서 구조 분리와 `.tpl` 외부화를 한 번에 완료한다.
분리만 하고 외부화하지 않은 중간 상태는 오히려 복잡도를 높이므로 통합한다.

작업:

1. `SecuritySpec` 추가
2. `SecurityFilePlanFactory` 추가
3. 기존 `SecurityTemplateService`의 16개 private 템플릿 메서드를 Factory 또는 Renderer로 이동
4. `templates/security/common`, `egov43`, `egov50` 디렉터리 생성 후 `.tpl` 파일 분리
5. `SecurityTemplateRenderer`에서 `ClassPathTemplateLoader` 재사용
6. packageName, packagePath, egovVersion, javaxOrJakarta 치환 처리
7. 기존 3개 인자 API 결과가 완전히 동일한지 테스트

완료 기준:

- 기존 `getSecurityTemplate(securityType, packageName, egovVersion)` 호출 결과 유지
- `SecurityTemplateService`는 조율 코드 중심으로 축소
- Java 코드에서 대형 Text Block 제거 — 템플릿 수정 시 Java 소스 변경 불필요
- 4.3/5.0 분기 템플릿명이 명시적으로 드러남
- 단일 생성 키 16종 모두 통과

### Phase 2. outputPath 저장 지원

목표: `outputPath`가 들어오면 파일을 직접 저장한다.
Phase 2 진입 전에 `FilePlanExecutor.execute(Path root, List<FilePlan>)` 오버로드를 먼저 추가하여 `ProjectSpec` 강결합을 해소한다.

작업:

1. `FilePlanExecutor`에 `execute(Path root, List<FilePlan> plans)` 오버로드 추가
2. Tool 메서드 인자에 `outputPath`, `projectType` 추가
3. `SecurityFilePlanFactory.plan()` 구현
4. web.xml 존재 여부 분기 — 있으면 삽입 위치 안내 + 조각 반환, 없으면 전체 파일 생성
5. `ProjectValidator.validatePlans()` 재사용
6. `SecurityResultBuilder`로 저장 결과 반환

완료 기준:

- 단일 securityType 저장 가능
- `ProjectSpec` 없이 `Path root`만으로 FilePlanExecutor 실행 가능
- web.xml 존재 여부에 따라 전체/조각 분기 동작
- 저장 경로 탈출 검증 적용
- 실패 파일이 있어도 나머지 파일 실행 결과 확인 가능

### Phase 3. 조합 키워드 지원

목표: 보안 셋업 파일 묶음을 한 번에 생성한다.

작업:

1. `setup-war-43`, `setup-war-50`, `setup-filters` 추가
2. `setup-all-war-43`, `setup-all-war-50` 추가
3. 조합 확장 순서 고정
4. 중복 경로 제거 (`LinkedHashSet` 사용)
5. 결과 메시지에 포함 파일 목록 출력

완료 기준:

- eGovFrame 4.3 WAR 기본 보안 셋업이 한 번에 생성됨
- eGovFrame 5.0 WAR 기본 보안 셋업이 한 번에 생성됨
- 필터/핸들러 조합을 별도 호출 가능

### Phase 4. 검증 및 문서화

목표: 회귀 방지와 사용성 문서를 보강한다.

작업:

1. `SecurityTemplateServiceTest` 추가
2. `SecurityFilePlanFactoryTest` 추가
3. 템플릿 로딩 실패 테스트
4. unsupported securityType 테스트
5. `SecurityTemplateTool_사용예시.md` 업데이트

완료 기준:

- 단일 키 16종 렌더링 테스트 통과
- 조합 키워드 FilePlan 개수/경로 테스트 통과
- 4.3/5.0 버전별 대표 템플릿 스냅샷 검증

---

## 9. 테스트 계획

### 9-1. 단위 테스트

| 테스트 | 검증 내용 |
|---|---|
| `renderSingle_webXmlFilter_returnsContent` | 기존 문자열 반환 유지 |
| `renderSingle_contextSecurity_43_uses43Template` | 4.3 XSD/namespace 포함 |
| `renderSingle_contextSecurity_50_uses50Template` | 5.0 XSD/namespace 포함 |
| `plan_setupWar43_containsExpectedFiles` | 4.3 조합 파일 목록 |
| `plan_setupWar50_containsExpectedFiles` | 5.0 조합 파일 목록 |
| `plan_rejectsUnsupportedSecurityType` | 미지원 키 오류 메시지 |
| `plan_rejectsPathTraversal` | 경로 탈출 방어 |

### 9-2. 통합 테스트

임시 디렉터리에 실제 파일을 생성한다.

```text
getSecurityTemplate(
  "setup-all-war-43",
  "egovframework.let.sample",
  "4.3",
  "/tmp/security-demo",
  "war"
)
```

검증:

- `context-security.xml` 생성
- `context-egovuserdetailshelper.xml` 생성
- Java 파일 package 선언 일치
- JSP 파일 생성
- SQL 파일 생성
- 결과 메시지의 파일 수와 실제 파일 수 일치

---

## 10. 위험 요소와 대응

| 위험 | 대응 |
|---|---|
| 기존 문자열 결과 변경 | Phase 1에서 기존 출력 스냅샷 테스트 선행 |
| 5.0 Jakarta/Servlet 패키지 혼선 | `VersionCapability`에서 namespace 기준을 단일화 |
| web.xml 전체 파일 덮어쓰기 위험 | 기존 파일 존재 여부로 분기: 있으면 삽입 위치 안내 + 조각 반환, 없으면 전체 파일 생성 |
| `FilePlanExecutor`가 `ProjectSpec`에 강결합 | Phase 3 진입 전에 `FilePlanExecutor.execute(Path root, List<FilePlan>)` 오버로드 추가 — SecuritySpec/ProjectSpec 무관하게 동작 |
| 템플릿 파일이 너무 많아짐 | `common`, `egov43`, `egov50`으로 폴더 구분 |
| 조합 생성 순서 오류 | `LinkedHashMap`/`LinkedHashSet`으로 선언 순서 고정 |

---

## 11. 권장 작업 순서

1. `SecuritySpec`와 `SecurityFilePlanFactory`를 추가한다.
2. 기존 문자열 반환 경로를 Factory로 옮기고 `.tpl`로 외부화한다 (구조 분리 + 외부화 통합).
3. 기존 API의 회귀 테스트를 만든다.
4. `FilePlanExecutor` 오버로드 추가 후 `outputPath` 저장 경로를 추가한다.
5. 조합 키워드를 추가한다.
6. 사용 예시 문서를 갱신한다.

가장 안전한 첫 커밋 단위는 다음과 같다.

```text
commit 1: SecuritySpec / SecurityFilePlanFactory / .tpl 외부화 추가, 기존 반환 동작 유지 (Phase 1)
commit 2: FilePlanExecutor 오버로드 + outputPath 직접 저장 지원 (Phase 2)
commit 3: setup-* 조합 키워드 지원 (Phase 3)
commit 4: 테스트와 사용 문서 보강 (Phase 4)
```

---

## 12. 최종 완료 기준

```text
현재
  SecurityTemplateService
    - 1,608줄
    - 16개 템플릿 하드코딩
    - 문자열 반환만 가능

완료 후
  SecurityTemplateService
    - 얇은 조율자
    - FilePlan 기반 생성
    - .tpl 템플릿 외부화
    - 문자열 반환 + 직접 저장 모두 지원
    - setup-* 조합 생성 지원
    - ProjectInitializr 공통 인프라 재사용
```

최종적으로 `SecurityTemplateTool`은 단순 템플릿 복사 도구가 아니라,
eGovFrame 4.3/5.0 보안 셋업을 프로젝트에 직접 배치할 수 있는 생성 파이프라인이 된다.
