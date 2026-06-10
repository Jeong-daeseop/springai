# SecurityTemplateTool vs ProjectInitializrTool — 구조 비교분석

> 작성일: 2026-06-09
> 대상: springai-mcp 프로젝트

---

## 1. 규모 비교

| 항목 | `ProjectInitializrTool` | `SecurityTemplateTool` |
|---|---|---|
| @Tool 메서드 수 | **2개** (initializeProject, getConfigTemplate) | **1개** (getSecurityTemplate) |
| Service 라인 수 | **114줄** (ProjectInitializrService) | **1,608줄** (SecurityTemplateService) |
| securityType 종류 | — | **16종** |
| 파일 직접 저장 | **O** (FilePlanExecutor → EgovFileWriter) | **X** (문자열 반환만) |

---

## 2. 아키텍처 구조 비교

### ProjectInitializrTool (Phase 1~4 완료)

```
ProjectInitializrTool
    ↓
ProjectInitializrService  (114줄 — 얇은 조율자)
    ├── VersionCapabilityResolver  (버전 해석)
    ├── FilePlanFactory            (FilePlan 목록 조립)
    │   ├── StaticTemplateRenderer → .tpl 파일 치환
    │   └── BuildFileRenderer      → 6개 Builder 클래스
    ├── FilePlanExecutor           (파일 단위 실행)
    ├── ProjectValidator           (사전/사후 검증)
    ├── ResultBuilder              (결과 빌드)
    └── GenerationHistoryRecorder  (이력 기록)
```

### SecurityTemplateTool (현재)

```
SecurityTemplateTool
    ↓
SecurityTemplateService  (1,608줄 — God Class)
    └── switch(securityType) → 16개 메서드 직접 반환
        (webXmlFilter, contextSecurity, javaConfig43/50,
         userDetailsService, loginFilter ... 전부 한 클래스)
```

---

## 3. 핵심 차이점

| 관점 | `ProjectInitializrTool` | `SecurityTemplateTool` |
|---|---|---|
| **Service 역할** | 얇은 조율자 (위임) | God Class (모든 것 직접 처리) |
| **템플릿 위치** | `.tpl` 리소스 파일 외부화 | Java Text Block 하드코딩 |
| **버전 분기** | VersionCapability record | 메서드명에 `43`/`50` suffix |
| **파일 저장** | Tool 내부에서 직접 저장 | Claude에게 위임 |
| **검증** | ProjectValidator (사전/사후) | 없음 |
| **이력 기록** | GenerationHistoryRecorder | 없음 |
| **조합 생성** | plan() 한 번에 전체 생성 | 1개씩 개별 호출 필요 |

---

## 4. SecurityTemplateTool 지원 securityType 목록

### 레거시 XML 방식

| securityType | 생성 파일 | 비고 |
|---|---|---|
| `webXmlFilter` | web.xml 6-filter 체인 | 선언 순서 포함 |
| `contextSecurity` | context-security.xml | 4.3/5.0 XSD 완전히 다름 |
| `securityMapper` | URL-ROLE 매핑 SQL | COMTNROLEINFO / COMTNROLES_HIERARCHY |

### Java Config 방식

| securityType | 생성 파일 | 비고 |
|---|---|---|
| `javaConfig` | EgovProjectSecurityConfig.java | 4.3: WebSecurityConfigurerAdapter / 5.0: @Import |
| `userDetailsService` | EgovUserDetailsServiceImpl.java | 4.3 전용 |
| `roleHierarchy` | EgovRoleHierarchyConfig.java | 4.3/5.0 공통 |

### 인증/로그아웃 필터

| securityType | 생성 파일 | 비고 |
|---|---|---|
| `loginFilter` | EgovSpringSecurityLoginFilter.java | DB 직접 인증 |
| `logoutFilter` | EgovSpringSecurityLogoutFilter.java | 세션 초기화 |
| `loginPolicyFilter` | EgovLoginPolicyFilter.java | 비밀번호 만료/계정 잠금 |
| `sessionMapping` | EgovSessionMapping.java | DB ResultSet → EgovUserDetails |

### 핸들러 (javaConfig 4.3 전용)

| securityType | 생성 파일 | 비고 |
|---|---|---|
| `successHandler` | EgovAuthenticationSuccessHandler.java | 4.3 전용 |
| `failureHandler` | EgovAuthenticationFailureHandler.java | 4.3 전용 |
| `accessDeniedHandler` | EgovAccessDeniedHandler.java | 4.3/5.0 분기 |

### 공통

| securityType | 생성 파일 | 비고 |
|---|---|---|
| `loginPage` | egovLoginUsr.jsp | CSRF 토큰 포함 |
| `userDetailsHelper` | EgovUserDetailsHelper 사용 예시 | 4.3/5.0 공통 |
| `userDetailsHelperXml` | context-egovuserdetailshelper.xml | Profile 분기 XML |

---

## 5. 현재 방식의 문제점

### 5-1. God Class (1,608줄)

```
SecurityTemplateService
  ├── getSecurityTemplate()    — 진입점
  ├── webXmlFilter()
  ├── contextSecurity()
  ├── javaConfig43()
  ├── javaConfig50()
  ├── userDetailsService()
  ├── roleHierarchy()
  ├── loginFilter()
  ├── logoutFilter()
  ├── loginPolicyFilter()
  ├── sessionMapping()
  ├── successHandler()
  ├── failureHandler()
  ├── accessDeniedHandler()
  ├── loginPage()
  ├── userDetailsHelper()
  └── userDetailsHelperXml()
     (전부 Java Text Block 하드코딩)
```

### 5-2. 파일 저장 미지원

```
현재 흐름:
getSecurityTemplate() → 문자열 반환 → Claude 수신 → saveGeneratedCode() 호출

문제:
- 16개 securityType 각각 별도 Tool 호출 필요
- Claude 토큰 소비 (왕복 × 파일 수)
- 조합 생성 불가 (예: WAR 4.3 전체 Security 셋업)
```

---

## 6. 개선 방향 — ProjectInitializrTool 수준으로 리팩터링

### 목표 구조

```
SecurityTemplateTool
    ↓
SecurityTemplateService  (얇은 조율자)
    ├── VersionCapabilityResolver  (ProjectInitializr와 공유)
    ├── SecurityFilePlanFactory    (FilePlan 목록 조립)
    │   ├── SecurityStaticRenderer → .tpl 파일 외부화
    │   └── 버전별 Builder 클래스
    ├── FilePlanExecutor           (ProjectInitializr와 공유)
    └── GenerationHistoryRecorder  (ProjectInitializr와 공유)
```

### 조합 키워드 추가 (outputPath 연동)

| securityType 키워드 | 자동 생성 파일 묶음 |
|---|---|
| `setup-war-43` | webXmlFilter + contextSecurity + javaConfig + userDetailsService + roleHierarchy + loginPage |
| `setup-war-50` | contextSecurity(5.0) + javaConfig(5.0) + roleHierarchy + loginPage |
| `setup-filters` | loginFilter + logoutFilter + loginPolicyFilter + sessionMapping |

### 파라미터 변경

```java
// 현재
getSecurityTemplate(String securityType, String packageName, String egovVersion)

// 개선 후
getSecurityTemplate(String securityType, String packageName, String egovVersion,
                    @Nullable String outputPath,   // null → 문자열 반환 (하위 호환)
                    @Nullable String projectType)  // "war" / "boot"
```

---

## 7. 결론

```
현재                               개선 후
─────────────────────────────────  ──────────────────────────────────
SecurityTemplateService            SecurityTemplateService
  God Class (1,608줄)                얇은 조율자
  16개 메서드 직접 포함               SecurityFilePlanFactory
  Java Text Block 하드코딩              ├── .tpl 파일 외부화
  파일 저장 불가                        └── 버전별 Builder
  조합 생성 불가                     FilePlanExecutor (공유)
                                    조합 키워드 지원
                                    outputPath로 직접 저장
```

`ProjectInitializrService`가 Phase 1~4를 거쳐 완성된 구조를
**`SecurityTemplateService`에도 동일하게 적용**하는 것이 자연스러운 다음 단계.

공통 인프라(`VersionCapabilityResolver`, `FilePlanExecutor`, `EgovFileWriter`,
`GenerationHistoryRecorder`)는 재사용 가능하므로 중복 없이 확장 가능.
