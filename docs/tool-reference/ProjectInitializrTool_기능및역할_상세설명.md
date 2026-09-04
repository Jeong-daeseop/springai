# ProjectInitializrTool 기능 및 역할 상세 설명

## 개요

`ProjectInitializrTool`은 **Spring Initializr처럼 eGovFrame 신규 프로젝트 골격을 한 번에 생성**하는 MCP Tool입니다.
eGovFrame 4.3 / 5.0, WAR / Boot, Maven / Gradle 4가지 조합을 모두 지원하며 표준 디렉터리 구조와 설정 파일을 자동으로 생성합니다.

> ⚠️ 이 Tool이 outputPath 경로에 파일을 직접 생성합니다. Desktop Commander, Bash, 기타 도구로 대체하지 마세요.

---

## 구성 레이어

```
ProjectInitializrTool (MCP Tool 진입점)
  └── ProjectInitializrService (얇은 조율자)
        ├── VersionCapabilityResolver — egovVersion → VersionCapability 해석
        ├── FilePlanFactory           — ProjectSpec → FilePlan 목록 조립
        ├── ProjectValidator          — 사전/사후 검증 (중복 경로, namespace, Java 버전)
        ├── FilePlanExecutor          — FilePlan 루프 실행 (파일 단위 에러 격리)
        ├── ResultBuilder             — 결과 문자열 + PROJECT_CONTEXT 블록 생성
        └── GenerationHistoryRecorder — 생성 이력 DB 저장
```

---

## 기능 1: `initializeProject()` — 프로젝트 골격 생성

### 파라미터

| 파라미터 | 필수 | 설명 | 예시 |
|----------|------|------|------|
| `projectName` | ✅ | 프로젝트 폴더명 | `egov-myproject` |
| `groupId` | ✅ | Maven groupId | `kr.go.myorg` |
| `artifactId` | ✅ | Maven artifactId | `myproject` |
| `packageName` | ✅ | 기본 Java 패키지 | `egovframework.let.myproject` |
| `buildTool` | ✅ | `maven` 또는 `gradle` | `gradle` |
| `projectType` | ✅ | `war` 또는 `boot` | `war` |
| `egovVersion` | ✅ | `4.3` / `5.0` / `latest` | `5.0` |
| `outputPath` | ✅ | 생성 상위 경로 | `/Users/me/Desktop` |

> `projectType` 또는 `egovVersion` 미입력 시 사용자에게 반드시 확인

---

## 지원 조합 및 기술 스택

| projectType | egovVersion | Spring | Spring Boot | Java | Servlet |
|---|---|---|---|---|---|
| war | 4.3 | 5.3.37 | 2.7.18 | 11 | javax.servlet 4.0 |
| war | 5.0 / latest | 6.2.11 | 3.5.6 | 17 | Jakarta EE 10 |
| boot | 4.3 | 5.3.37 | 2.7.18 | 11 | mybatis-spring-boot-starter 2.x |
| boot | 5.0 / latest | 6.2.11 | 3.5.6 | 17 | mybatis-spring-boot-starter 3.x |

### egovVersion별 VersionCapability 자동 결정

| Capability | 4.3 | 5.0 |
|---|---|---|
| Jakarta EE | ❌ (javax) | ✅ (jakarta) |
| Spring 6 | ❌ | ✅ |
| Spring Boot 3 | ❌ | ✅ |
| Java 17 | ❌ (11) | ✅ |
| eGovFrame Parent POM | ❌ | ✅ |
| MyBatis Spring 3 | ❌ | ✅ |
| Spring Security | 5.8.13 | 6.5.5 |

---

## 생성 파일 목록

### 공통 (war / boot 모두)

| 파일 | 설명 |
|------|------|
| `build.gradle` 또는 `pom.xml` | 빌드 파일 (buildTool에 따라 결정) |
| `settings.gradle` | Gradle 프로젝트명 설정 (gradle 선택 시) |
| `gradle.properties` | JVM 옵션 설정 (gradle 선택 시) |
| `.gitignore` | 표준 gitignore |

### WAR 전용 (`war` + 표준 디렉터리 구조)

| 파일 | 설명 |
|------|------|
| `src/main/resources/egovframework/spring/context-common.xml` | 컴포넌트 스캔, SqlSessionFactory, MapperScanner |
| `src/main/resources/egovframework/spring/context-datasource.xml` | HikariCP DataSource 설정 |
| `src/main/resources/egovframework/spring/context-transaction.xml` | TransactionManager + AOP 트랜잭션 |
| `src/main/webapp/WEB-INF/config/.../dispatcher-servlet.xml` | Spring MVC 컨트롤러 스캔, ViewResolver |
| `src/main/webapp/WEB-INF/web.xml` | ContextLoaderListener, DispatcherServlet, 필터 |
| `src/main/webapp/index.jsp` | 기본 인덱스 페이지 |
| `src/main/webapp/WEB-INF/jsp/.../error404.jsp` | 404 에러 페이지 |
| `src/main/webapp/WEB-INF/jsp/.../error500.jsp` | 500 에러 페이지 |
| `src/main/resources/log4j2.xml` | 콘솔 + 파일 롤링 로그 설정 |

### Boot 전용

| 파일 | 설명 |
|------|------|
| `src/main/resources/application.yml` | datasource / mybatis / server / logging (local/prod 프로파일 분리) |
| `src/main/resources/logback-spring.xml` | 콘솔 + 파일 롤링 로그 설정 |
| `src/main/java/{pkg}/{Cls}Application.java` | `@SpringBootApplication` + `@MapperScan("{pkg}")` 메인 클래스 |
| `src/test/java/{pkg}/{Cls}ApplicationTests.java` | 기본 테스트 클래스 |

**Boot + viewType="thymeleaf" 추가 생성** (docs/crud/thymeleaf-layout-boot-support-plan.md Phase 1):

| 파일 | 설명 |
|------|------|
| `src/main/resources/templates/layout/{default,gnb,lnb,breadcrumb,footer}.html` | Thymeleaf 공통 layout 5종 (WAR와 동일 내용) |
| `src/main/resources/templates/egovframework/main/main.html` | 메인 화면 |
| `src/main/java/{pkg}/main/web/MainController.java` | `@GetMapping({"/", "/egovframework/com/main.do"})` → `egovframework/main/main` |
| `build.gradle` / `pom.xml` | `spring-boot-starter-thymeleaf` + `nz.net.ultraq.thymeleaf:thymeleaf-layout-dialect` 의존성 추가 |

동적 GNB 컴포넌트 4종·인터셉터 등록은 `generateThymeleafLayout()`이 별도 수행한다.

---

## 표준 디렉터리 구조

### WAR 프로젝트
```
{projectName}/
├── src/
│   ├── main/
│   │   ├── java/{packagePath}/
│   │   ├── resources/
│   │   │   └── egovframework/
│   │   │       ├── mapper/        ← MyBatis Mapper XML
│   │   │       └── spring/        ← context-*.xml
│   │   └── webapp/
│   │       ├── WEB-INF/
│   │       │   ├── config/egovframework/springmvc/
│   │       │   ├── jsp/egovframework/
│   │       │   └── web.xml
│   │       └── resources/
│   │           ├── css/
│   │           └── js/
│   └── test/
│       └── java/{packagePath}/
├── build.gradle (또는 pom.xml)
└── .gitignore
```

### Boot 프로젝트
```
{projectName}/
├── src/
│   ├── main/
│   │   ├── java/{packagePath}/
│   │   └── resources/
│   │       ├── egovframework/mapper/
│   │       ├── static/
│   │       │   ├── css/
│   │       │   └── js/
│   │       ├── templates/
│   │       ├── application.yml
│   │       └── logback-spring.xml
│   └── test/
│       └── java/{packagePath}/
├── build.gradle (또는 pom.xml)
└── .gitignore
```

---

## 내부 처리 흐름 (8단계)

```
①  VersionCapabilityResolver.resolve(egovVersion)
    → VersionCapability 계산 (Jakarta/Spring6/Boot3/Java17/... 플래그)

②  ProjectSpec.of(...)
    → 모든 파라미터 + VersionCapability를 담은 불변 VO 조립

③  createDirectories(spec)
    → 표준 디렉터리 구조 사전 생성

④  FilePlanFactory.plan(spec)
    → 생성할 파일 목록 (FilePlan) 조립 — Supplier 지연 렌더링

⑤  ProjectValidator.validatePlans(plans)
    → 사전 검증: 중복 경로, null 체크, 경로 탈출 방지

⑥  FilePlanExecutor.execute(spec, plans)
    → 파일 단위 에러 격리 실행 → GenerationReport 반환

⑦  ProjectValidator.validateResult(spec, report)
    → 사후 검증: 필수 파일 존재 여부, namespace 일치, Java 버전

⑧  ResultBuilder.build(spec, report)
    → PROJECT_CONTEXT 블록 포함 결과 문자열 반환
    → GenerationHistoryRecorder.record() 이력 저장
```

---

## PROJECT_CONTEXT 블록

생성 완료 후 반환값에 아래 블록이 포함됩니다. `buildFullCrudPrompt()` 등 후속 Tool에 그대로 전달하세요.

```
PROJECT_CONTEXT:
  projectName : egov-myproject
  groupId     : kr.go.myorg
  artifactId  : myproject
  packageName : egovframework.let.myproject
  projectType : war
  egovVersion : 5.0
  buildTool   : gradle
  outputPath  : /Users/me/Desktop/egov-myproject
```

---

## 기능 2: `getConfigTemplate()` — 설정 파일 단독 반환

신규 프로젝트 구성 또는 기존 프로젝트 누락 항목 보완 시 사용합니다.

### 지원 configType

| configType | 생성 파일 | 설명 |
|---|---|---|
| `contextCommon` | `context-common.xml` | 컴포넌트 스캔, SqlSessionFactory, MapperScanner |
| `contextDatasource` | `context-datasource.xml` | HikariCP DataSource 설정 |
| `contextTransaction` | `context-transaction.xml` | TransactionManager + AOP 트랜잭션 |
| `dispatcherServlet` | `dispatcher-servlet.xml` | Spring MVC ViewResolver, 파일 업로드 |
| `webXml` | `web.xml` | Jakarta EE 6.0 기준 ContextLoaderListener, 필터 |
| `logback` | `logback-spring.xml` | Boot 전용 — 콘솔 + 파일 롤링 |
| `log4j2` | `log4j2.xml` | WAR 전용 — 콘솔 + 파일 롤링 |
| `applicationYml` | `application.yml` | Boot 전용 — local/prod 프로파일 분리 |

> `contextCommon`, `dispatcherServlet`, `applicationYml`에서 `packageName` 사용
> 생략 시 `egovframework.let.sample` 적용

---

## 테스트 예시문

### 프로젝트 생성
```
eGovFrame 5.0 WAR 프로젝트 생성해줘
projectName=egov-myproject, groupId=kr.go.myorg, artifactId=myproject
packageName=egovframework.let.myproject, buildTool=gradle
projectType=war, egovVersion=5.0, outputPath=/Users/me/Desktop
```
```
eGovFrame 4.3 Spring Boot 프로젝트 만들어줘
projectType=boot, egovVersion=4.3, buildTool=maven
```

### 설정 파일 단독 생성
```
context-common.xml 템플릿 줘 (패키지: egovframework.let.emp)
```
```
application.yml 템플릿 생성해줘 (packageName=egovframework.let.emp)
```
```
dispatcher-servlet.xml 설정 파일 보여줘
```

---

## 후속 워크플로우

```
Step 1. initializeProject(viewType="jsp")
        → 프로젝트 골격 생성 + PROJECT_CONTEXT 블록 획득
        → Thymeleaf 화면을 쓸 프로젝트여도 viewType="jsp"(기본값)로 초기화한다.
          viewType="thymeleaf"로 초기화하면 정적 GNB가 든 layout 5종/main.html/ViewResolver가
          먼저 생성되는데 generateThymeleafLayout()이 이를 어차피 다시 만든다(overwriteLayout 기본값 true).
          중복이며, 나중에 overwriteLayout=false로 재호출하면 정적 gnb.html이 남아 동적 GNB가 깨진다.
          Thymeleaf 공통 layout은 generateThymeleafLayout()에서만 만든다.

Step 1-T. (Thymeleaf 프로젝트만) generateThymeleafLayout(outputPath, packageName)
        → layout 5종 + GNB 메뉴 컴포넌트 4종 + servlet-context.xml patch (최초 1회)

Step 2. SecurityTemplateTool.getSecurityTemplate()
        → Spring Security 설정 파일 생성
        → egovVersion에 맞는 조합 키워드 사용
          (4.3: setup-all-war-43-xml / 5.0: setup-all-war-50)

Step 3. CrudPromptBuilderTool.buildFullCrudPrompt()
        → PROJECT_CONTEXT의 egovVersion을 egovVersion 파라미터로 전달
        → 도메인별 CRUD 소스 생성

Step 4. MenuTool.generateMenuInsertSql()
        → 생성된 URL로 메뉴 등록

Step 5. AuthTool.generateAuthInsertSql()
        → URL 접근 권한 등록
```

---

## 관련 파일

| 파일 | 역할 |
|------|------|
| `tools/ProjectInitializrTool.java` | MCP Tool 진입점 (`@Tool` 어노테이션) |
| `service/ProjectInitializrService.java` | 얇은 조율자 (8단계 파이프라인) |
| `service/initializr/VersionCapabilityResolver.java` | egovVersion → VersionCapability 해석 |
| `service/initializr/FilePlanFactory.java` | ProjectSpec → FilePlan 목록 조립 + 버전 상수 관리 |
| `service/initializr/FilePlanExecutor.java` | FilePlan 루프 실행 (파일 단위 에러 격리) |
| `service/initializr/ProjectValidator.java` | 사전/사후 검증 |
| `service/initializr/ResultBuilder.java` | 결과 문자열 + PROJECT_CONTEXT 블록 생성 |
| `service/initializr/GenerationHistoryRecorder.java` | 생성 이력 DB 저장 |
| `service/initializr/template/BuildFileRenderer.java` | pom.xml / build.gradle / web.xml 렌더링 |
| `service/initializr/template/StaticTemplateRenderer.java` | context-*.xml / application.yml 등 렌더링 |
| `model/ProjectSpec.java` | 프로젝트 스펙 불변 VO |
| `model/VersionCapability.java` | 버전별 Capability 플래그 VO |
| `model/FilePlan.java` | 파일 경로 + 렌더러 VO |
| `model/GenerationReport.java` | 생성 결과 VO (created, errors) |
| `resources/templates/egov/` | 각종 설정 파일 tpl 템플릿 |
