# Thymeleaf 공통 layout · 동적 GNB — Spring Boot 지원 구현 계획

이 문서는 `thymeleaf-layout-dynamic-gnb-design.md` / `thymeleaf-layout-dynamic-gnb-plan.md`(WAR 기준 1차 구현 완료)의 후속으로,
**`projectType="boot"` 프로젝트에서도 Thymeleaf 공통 layout과 DB 기반 동적 GNB가 끝까지 동작하도록** 하는 전체 범위를 정의한다.

> **핵심 인식**: "Boot용 `WebMvcConfigurer` 인터셉터 등록"은 Boot 지원의 **한 조각(약 1/5)**이다.
> `generateThymeleafLayout` 하나만 고쳐서는 안 되며, 상위의 `ProjectInitializrTool`(boot+thymeleaf 산출물 생성)까지 함께 손대야 한다.

**진행 상황**: Phase 1~7 구현 완료. Phase 4/7의 Testcontainers 부팅 하네스까지 추가함(`build.gradle` testImplementation + `gradle.lockfile` 갱신, `bootGnbIntegrationTest` 전용 태스크). 단위·컴파일 테스트는 전부 통과하며, Testcontainers MySQL을 실제로 띄우는 3개 테스트는 Docker가 있는 환경에서만 실행되고(없으면 `assumeTrue`로 skip) 로컬 개발 환경의 Docker Desktop 29 ↔ Testcontainers 1.20.4(docker-java 3.4.0) API 비호환으로 이 머신에서는 skip 상태다. CI/호환 Docker 환경에서 `./gradlew bootGnbIntegrationTest`로 실행된다.

| Phase | 상태 | 산출물 |
|---|---|---|
| 1. ProjectInitializr boot+thymeleaf | ✅ | `FilePlanFactory.thymeleafLayoutFilePlans()` 공용 추출 + `bootFiles()` 분기, `bootMainController()`(`/` 매핑), `directoryPlans` boot 분기, `BootBuildGradleBuilder`/`BootPomBuilder` thymeleaf 의존성 |
| 2. ProjectTypeDetector | ✅ | `service/generation/layout/ProjectTypeDetector.java` (web.xml→WAR / application.yml→BOOT / else UNKNOWN) + `ProjectTypeDetectorTest` |
| 3. generateThymeleafLayout Boot 분기 | ✅ | `BootMvcConfigConfigurer.java`(+테스트), `ThymeleafLayoutGenerationService` 타입 분기, `LayoutGenerationResult.projectType`, `ThymeleafLayoutResultFormatter` Boot 표기, `@Tool` 설명 갱신 |
| 4. MyBatis/스캔 정합성 | ✅ 검증 하네스 | `BootGnbBootIntegrationTest.generatedGnbMapper_selectsTopLevelMenusFromRealMySql` — Testcontainers MySQL에 `LETTNMENUINFO`/`LETTNPROGRMLIST` seed 후 생성 Mapper XML의 실제 SQL·resultMap 검증. Docker 환경에서 실행 |
| 5. CRUD 생성 경로 Boot 정합성 | ✅ | `CrudEntryPointProcessor`에 `ProjectTypeDetector` 주입 — Boot면 WAR 진입점(index.jsp) 갱신 skip |
| 6. 문서 갱신 | ✅ | CLAUDE.md, ThymeleafLayoutTool·ProjectInitializrTool 상세문서, dynamic-gnb-plan 상호 참조 |
| 7. 테스트 | ✅ | 단위: `ProjectTypeDetectorTest`(6), `BootMvcConfigConfigurerTest`(3), `ProjectInitializrBoot50ThymeleafWorkflowTest`(3), `ThymeleafLayoutGenerationServiceTest` Boot 케이스. **컴파일**: `GnbGeneratedSourcesCompileTest`(2) — 생성 Boot 소스를 in-process javac+Lombok으로 컴파일. **부팅(Testcontainers)**: `BootGnbBootIntegrationTest`(3) — Mapper 실조회 / `EgovGnbMenuInterceptor.postHandle` gnbMenus 주입 / `EgovWebMvcConfig`가 인터셉터를 `InterceptorRegistry`에 등록 |

### 테스트 하네스 상세 (Phase 7)

| 파일 | Docker 필요 | 검증 내용 |
|---|---|---|
| `GeneratedProjectCompiler` | ✗ | 생성 프로젝트 `*.java`를 현재 테스트 classpath + Lombok processorpath로 in-process 컴파일, 격리 `URLClassLoader` 노출 |
| `BootLayoutFixture` | ✗ | 실제 `ThymeleafLayoutGenerationService`로 Boot(application.yml) 프로젝트에 layout/GNB/`EgovWebMvcConfig` 생성 |
| `GnbGeneratedSourcesCompileTest` | ✗ | 생성 소스 컴파일 성공 + Lombok `@RequiredArgsConstructor`로 `EgovGnbMenuInterceptor(GnbMenuMapper)`/`EgovWebMvcConfig(GnbMenuMapper)` 생성자 존재 확인 |
| `BootGnbBootIntegrationTest` | ✓ Testcontainers `mysql:8.0` | 컴파일된 Mapper를 raw MyBatis(`SqlSessionFactory`)로 실제 MySQL 조회 → `GnbMenuVO` 매핑, 인터셉터 `postHandle` → `gnbMenus`/`currentTopMenuNo` 모델 주입, `EgovWebMvcConfig.addInterceptors()` → registry 등록 |

- 실행: `./gradlew bootGnbIntegrationTest` (Docker 없으면 `assumeTrue`로 각 테스트 skip). 이름이 `*IntegrationTest`라 `build.gradle`의 `ci` 프로퍼티 분기에서 `**/service/**/*IntegrationTest.class` 패턴으로 CI 빠른 세트에서 제외됨.
- 로컬 Docker Desktop 29.x + Testcontainers 1.20.4는 `/info` 400 응답으로 컨테이너 기동이 실패(skip)한다. Testcontainers/docker-java 버전 상향 또는 호환 Docker 데몬 환경에서 실행 가능.

**구현 중 계획 대비 변경**:
- 3.2/3.5 — `EgovWebMvcConfig`는 인터셉터 등록만 담당하고 `addViewControllers("/")`는 넣지 않음. 메인 뷰 매핑은 Phase 1의 Boot `MainController`(`@GetMapping({"/", "/egovframework/com/main.do"})`)가 담당 — `@Controller` 매핑과 `ViewControllerRegistry`의 중복/모호성 회피.
- 3.4 — Boot에서 `MyBatisRuntimeConfigurer.ensureConfigured()`는 계속 호출하되(`context-common.xml` 없으면 깔끔한 skipped 결과 반환), 결과 포맷터가 `skipped`를 "생략:"으로 표시하고 Boot 안내를 덧붙임.

---

## 1. 목표와 범위

### 1.1 목표

`initializeProject(projectType="boot", viewType="thymeleaf")` → `generateThymeleafLayout()` → `buildFullCrudPrompt(viewType="thymeleaf")` 흐름이
WAR와 **동등한 결과**(공통 layout + 매 요청 동적 GNB + Thymeleaf 렌더링)를 내도록 한다.

### 1.2 In scope

| # | 항목 |
|---|---|
| A | `ProjectInitializrTool` boot 경로가 `viewType="thymeleaf"`일 때 layout 5종·`main.html`·메인 진입점·Thymeleaf 의존성을 생성 |
| B | 프로젝트 타입(WAR/Boot) 감지 유틸 신설 — `generateThymeleafLayout`가 대상 프로젝트 구조를 보고 분기 |
| C | `generateThymeleafLayout`의 Boot 분기: `servlet-context.xml` patch 대신 **`WebMvcConfigurer` `@Configuration` 클래스**로 `EgovGnbMenuInterceptor` 등록 (멱등) |
| D | Boot에서 GNB `Mapper` 빈이 실제로 등록되는지 검증하고, 필요 시 `application.yml` / `@MapperScan` 보강 |
| E | 메인 화면 진입점(`/` → `egovframework/main/main`) 확보 — Boot는 WAR의 `index.jsp` forward + `MainController`가 없음 |
| F | `buildFullCrudPrompt` / `buildBoardFeature` / `buildMasterDetailPrompt` Thymeleaf 경로의 Boot 정합성 점검 |
| G | 문서 갱신 (CLAUDE.md, ThymeleafLayoutTool·ProjectInitializrTool 상세문서, dynamic-gnb 설계·계획 문서) |
| H | 테스트 (`initializr` boot+thymeleaf 워크플로우, `generateThymeleafLayout` boot 분기, Boot 통합 부팅 테스트) |

### 1.3 Out of scope

- eGovFrame 4.3(`javax.servlet`) Boot — Jakarta(5.0) Boot만 지원. 4.3는 후속.
- LNB 동적화 (기존 계획 10절대로 별도 과제 유지).
- GNB 조회 캐싱 (기존 계획대로 후속 과제).
- Maven WAR가 아닌 **Gradle WAR**의 Thymeleaf 런타임 보강 (별도 이슈 — 아래 6절 참고).

---

## 2. 현재 상태 — Boot에서 막히는 지점 (코드 증거)

| # | 지점 | 근거 | 결과 |
|---|---|---|---|
| G1 | `initializeProject` boot 경로가 Thymeleaf 산출물을 **전혀** 만들지 않음 | `FilePlanFactory.bootFiles()`에 `if (s.thymeleaf())` 분기 없음. layout 5종·`main.html`·`MainController`·`templates/` 디렉터리는 `warFiles()` (line 125~137) 및 디렉터리 branch(line 71)에만 존재. `ProjectSpec.normalizeViewType()`은 조합 검증을 하지 않아 `boot+thymeleaf`가 예외 없이 통과 | `viewType="thymeleaf"`가 Boot에서 **사실상 no-op** |
| G2 | Boot 빌드 파일에 Thymeleaf 의존성 없음 | `BootBuildGradleBuilder` / `BootPomBuilder`에 `thymeleaf` 문자열 0건. `spring-boot-starter-thymeleaf`·`thymeleaf-layout-dialect` 미포함 | ViewResolver·`layout:decorate` 불가 |
| G3 | 인터셉터 등록 불가 | `ServletContextConfigurer.patch()` — `servlet-context.xml` 부재 시 `failed=false` + "건너뜀 (Boot라면 정상 — WebMvcConfigurer 방식 별도 필요)" 반환 | `EgovGnbMenuInterceptor`가 어디에도 등록 안 됨 → 死 코드 |
| G4 | Thymeleaf 런타임 보강 skip | `ThymeleafRuntimeConfigurer`는 `pom.xml` / `servlet-context.xml` 존재 시에만 동작. Boot(gradle)엔 둘 다 없음 | `runtimeSkipped` 여부와 무관하게 내부에서 전부 `Optional.empty()` |
| G5 | `generateThymeleafLayout`에 타입 인지 수단 없음 | 전체 경로에 `projectType` 파라미터·`build.gradle` 파싱·`@SpringBootApplication` 탐지 없음 (grep 0건) | WAR/Boot 분기 불가 |
| G6 | 메인 진입점 부재 | WAR는 `index.jsp` → `/egovframework/com/main.do` forward + `FilePlanFactory.mainController()` 자동 생성. Boot bootFiles엔 없음. `generateThymeleafLayout`의 FTL layout은 brand 링크가 `@{/}` (`gnb.html.ftl:10`) | Boot에서 `/` 및 메인 뷰 매핑 없음 |

### 2.1 이미 해결되어 있는 것 (재사용 가능)

| 항목 | 근거 |
|---|---|
| MyBatis mapper 위치 | `application.yml.tpl` — `mybatis.mapper-locations: classpath*:egovframework/mapper/**/*.xml` 이미 존재. `GnbMenuMapper.xml`(`resources/egovframework/mapper/cmm/`)이 이 glob에 매칭됨 |
| Mapper 인터페이스 스캔 | `BootApplication.java.tpl` — `@MapperScan("${packageName}")` 이미 존재. `GnbMenuMapper.java`(`{packageName}.cmm.service`)가 스캔 범위에 포함됨 |
| Controller 스캔 | `@SpringBootApplication`이 `{packageName}` 하위 자동 스캔 → 생성 CRUD Controller 자동 등록 (WAR의 servlet-context component-scan patch 불필요) |
| 정적 리소스 경로 | `CrudPromptBuilderService:483` 이미 "WAR는 webapp/resources/**, BOOT는 static/resources/**" 인지 |

→ **G4에서 우려한 MyBatis 배선은 Boot 표준 스캐폴드가 이미 처리한다.** `packageName`만 일치하면 별도 보강 없이 동작할 가능성이 높다(Phase 4에서 검증).

---

## 3. 설계 결정

### 3.1 프로젝트 타입 감지 (G5 해소)

`ProjectTypeDetector`(신설, `service/generation/layout/` 또는 `service/initializr/`)를 도입한다.

```
WAR  판정: {root}/src/main/webapp/WEB-INF/web.xml 존재
BOOT 판정: 위가 없고 {root}/src/main/resources/application.yml|application.properties 존재
그 외    : UNKNOWN → 기존 WAR 경로로 폴백 + 경고 메시지 (현행 동작 보존)
```

- `generateThymeleafLayout`에 **파라미터를 추가하지 않는다** — 구조로 판정(호출부 부담 최소화, 오지정 위험 제거).
- `ProjectScannerService.detectConfigFiles()`가 유사 로직을 이미 갖고 있으므로 공용 헬퍼로 추출 검토.

### 3.2 인터셉터 등록: `WebMvcConfigurer` 생성 (G3 해소)

Boot 판정 시 `ServletContextConfigurer.patch()` 대신 **`BootMvcConfigConfigurer`(신설)** 가 다음을 수행:

- 대상 파일: `src/main/java/{packageName 경로}/config/EgovWebMvcConfig.java` (신규 생성)
- 내용:
  ```java
  @Configuration
  public class EgovWebMvcConfig implements WebMvcConfigurer {
      private final EgovGnbMenuInterceptor egovGnbMenuInterceptor;
      // 생성자 주입
      @Override public void addInterceptors(InterceptorRegistry registry) {
          registry.addInterceptor(egovGnbMenuInterceptor).addPathPatterns("/**");
      }
      // 3.5의 메인 뷰 컨트롤러도 여기서 함께 등록
      @Override public void addViewControllers(ViewControllerRegistry registry) {
          registry.addViewController("/").setViewName("egovframework/main/main");
      }
  }
  ```
- **멱등성**: 파일이 이미 있으면 — `addInterceptor(...EgovGnbMenuInterceptor...)` 문자열 포함 여부로 판정해 skip(WAR의 "이미 등록됨"과 동일한 UX). 파일은 있으나 등록 라인이 없으면 `addInterceptors` 메서드 본문에 라인 삽입, 메서드 자체가 없으면 메서드 추가. **안전하게 삽입 못 하면 실패 메시지 + 수동 안내**(WAR의 `</beans>` 개수 가드와 같은 철학).
- `overwriteLayout`과의 관계: 이 config 클래스는 layout 파일이 아니므로 `overwriteLayout` 영향 밖. 항상 "없으면 생성 / 있으면 멱등 보강".

### 3.3 Thymeleaf 런타임: Boot auto-configuration 활용 (G2·G4 해소)

- Boot는 `spring-boot-starter-thymeleaf`만 있으면 `ThymeleafAutoConfiguration`이 ViewResolver·TemplateEngine을 자동 구성 → **WAR처럼 XML bean을 주입할 필요 없음**.
- `thymeleaf-layout-dialect`는 클래스패스에만 있으면 Boot가 `LayoutDialect` 빈을 자동 등록(`@ConditionalOnClass`).
- 따라서 Boot 분기에서 `ThymeleafRuntimeConfigurer`는 **호출하지 않는다.** 대신 Phase 1에서 빌드 파일에 두 의존성을 넣는 것으로 끝.
- 템플릿 접두/접미(`classpath:/templates/`, `.html`)는 Boot 기본값과 일치하므로 커스텀 불필요.

### 3.4 MyBatis (G4 — 검증 위주)

- 기대: `application.yml`의 `mapper-locations` glob + `@MapperScan("{packageName}")`으로 `GnbMenuMapper`가 자동 배선됨.
- Phase 4에서 실제 부팅 테스트로 확인. 만약 `@MapperScan` 범위 밖(예: 사용자가 `packageName`을 다르게 준 경우)이면:
  - `generateThymeleafLayout` Boot 분기가 `{packageName}.cmm.service`가 `@MapperScan` value에 포함되는지 `BootApplication.java`를 읽어 확인, 불일치 시 **경고만** 반환(자동 수정하지 않음 — `@MapperScan` 편집은 부작용이 큼).

### 3.5 메인 화면 진입점 (G6 해소)

- Boot는 `index.jsp` forward 방식이 없으므로 `EgovWebMvcConfig.addViewControllers()`로 `/` → `egovframework/main/main` 뷰를 등록(3.2에 포함).
- `main.html`은 `generateThymeleafLayout`의 `MainPageRenderer`가 이미 `templates/egovframework/main/main.html`에 생성하므로 별도 파일 불필요.
- Phase 1에서 `initializeProject` boot+thymeleaf가 만드는 `main.html`과 중복되지 않도록 한쪽으로 일원화(생성기 소유권: `generateThymeleafLayout`).

---

## 4. Phase별 구현 계획

### Phase 1 — `ProjectInitializrTool` boot + thymeleaf 산출물 (G1·G2·G6)

| 변경 파일 | 내용 |
|---|---|
| `service/initializr/FilePlanFactory.java` | `bootFiles(s)`에 `if (s.thymeleaf())` 분기 추가: `templates/layout/{default,gnb,lnb,breadcrumb,footer}.html`, `templates/egovframework/main/main.html` 생성. `directoryPlans()` boot 분기에 `templates/layout`·`templates/egovframework/main` 추가. **단, `default.html` 등 문자열 리터럴은 WAR/Boot 공용이므로 재사용** (WAR와 동일 산출) |
| `service/initializr/FilePlanFactory.java` | boot+thymeleaf일 때 `MainController`는 만들지 않음 — Phase 3의 `EgovWebMvcConfig`가 `/` 뷰 컨트롤러를 담당(중복 매핑 방지). 대신 Phase 3 미실행 시를 대비해 안내 문구를 결과에 포함 |
| `service/initializr/template/BootBuildGradleBuilder.java` | `s.thymeleaf()`일 때 `spring-boot-starter-thymeleaf` + `nz.net.ultraq.thymeleaf:thymeleaf-layout-dialect:3.4.0`(Spring6) 추가 |
| `service/initializr/template/BootPomBuilder.java` | 동일 의존성 Maven 버전으로 추가 |
| `model/ProjectSpec.java` | (선택) `boot && thymeleaf && egovVersion==4.3` 조합을 명시적으로 거부 — 1차 미지원 |
| `tools/ProjectInitializrTool.java` | `@Tool` 설명의 `viewType="thymeleaf"` 항목이 WAR 전용처럼 읽히지 않도록 "boot에서도 layout 생성, 단 인터셉터 등록은 generateThymeleafLayout이 수행" 반영 |

**주의**: 현재 CLAUDE.md 권장 순서는 "boot여도 `viewType="jsp"`로 초기화"다. Phase 1 완료 후에도 이 권장은 **유지**한다(레이아웃 소유권을 `generateThymeleafLayout` 한 곳으로 두는 편이 여전히 단순). Phase 1은 "사용자가 이미 `boot+thymeleaf`로 초기화한 경우에도 깨지지 않게" 하는 방어적 성격.

### Phase 2 — 프로젝트 타입 감지 (G5)

| 변경/신설 파일 | 내용 |
|---|---|
| `service/generation/layout/ProjectTypeDetector.java` (신설) | 3.1 규칙. `enum ProjectType { WAR, BOOT, UNKNOWN }` + `detect(Path root)` |
| 테스트 | war/boot/unknown 픽스처 3종 |

### Phase 3 — `generateThymeleafLayout` Boot 분기 (G3·G6)

| 변경/신설 파일 | 내용 |
|---|---|
| `service/generation/layout/BootMvcConfigConfigurer.java` (신설) | 3.2·3.5. `configure(Path outputPath, String packageName)` → `EgovWebMvcConfig.java` 생성/멱등 보강. 반환 타입은 `ServletContextConfigurer.ServletContextPatchResult`와 동형(`message`, `failed`)으로 맞춰 결과 포맷터 재사용 |
| `service/generation/layout/ThymeleafLayoutGenerationService.java` | `servletContextConfigurer.patch(...)` 호출부를 타입 분기로 교체: `WAR → ServletContextConfigurer.patch()` / `BOOT → BootMvcConfigConfigurer.configure()` / `UNKNOWN → 기존 WAR 경로 + 경고`. Boot 분기에서는 `thymeleafRuntimeConfigurer.ensureThymeleafRuntime()` 및 `myBatisRuntimeConfigurer.ensureConfigured()` 호출 skip(3.3·3.4), 대신 `@MapperScan` 범위 확인 경고만 |
| `service/generation/layout/LayoutGenerationResult.java` | `projectType` 필드 추가(결과 메시지에 "Boot 감지 — WebMvcConfigurer 등록" 노출) |
| `service/generation/mcp/ThymeleafLayoutResultFormatter.java` | Boot 결과 라인 포맷 추가 |
| `tools/ThymeleafLayoutTool.java` | `@Tool` 설명의 "[1차 구현 제약] WAR 프로젝트만 지원" → "WAR: servlet-context.xml patch / Boot: WebMvcConfigurer 클래스 생성" 로 갱신 |

### Phase 4 — MyBatis / 스캔 정합성 검증 (G4)

| 작업 | 내용 |
|---|---|
| 통합 테스트 | Phase 1으로 생성한 boot 프로젝트에 `generateThymeleafLayout` 적용 후 `@SpringBootTest`로 `GnbMenuMapper` 빈 주입·`EgovGnbMenuInterceptor` 등록 확인 |
| 보강 판단 | 위 테스트가 통과하면 코드 변경 없음. 실패 시 `BootMvcConfigConfigurer`가 `application.yml`의 `mapper-locations` 존재를 확인하고 없으면 경고(자동 편집은 하지 않음) |

### Phase 5 — CRUD 생성 경로 Boot 정합성 (F)

| 작업 | 내용 |
|---|---|
| 점검 | `buildFullCrudPrompt(viewType="thymeleaf")` → `CrudGenerationPlanner` / `CrudEntryPointProcessor`가 Boot에서 `WarEntryPointConfigurer`(index.jsp 갱신)를 건너뛰는지 확인. WAR 전용 처리는 타입 가드로 감싸기 |
| 점검 | 생성 Controller가 `@Controller` + 뷰명 반환 방식이라 Boot에서도 그대로 동작하는지(스캔은 `@SpringBootApplication`이 담당) |
| 점검 | `layoutMode=reuse` 기본값 검사가 `templates/layout/*.html` 존재만 보므로 Boot에서도 그대로 유효 |

### Phase 6 — 문서 갱신 (G)

| 파일 | 내용 |
|---|---|
| `CLAUDE.md` | ThymeleafLayoutTool "1차 구현 제약 = WAR만" 문구 갱신. 권장 순서에 Boot 경로 명시 |
| `docs/tool-reference/ThymeleafLayoutTool_기능및역할_상세설명.md` | "WAR 전용" 표 항목 → "WAR/Boot 분기" 재작성, `EgovWebMvcConfig.java` 산출물 추가 |
| `docs/tool-reference/ProjectInitializrTool_기능및역할_상세설명.md` | boot+thymeleaf 산출물 목록 갱신 |
| `docs/crud/thymeleaf-layout-dynamic-gnb-design.md` / `-plan.md` | "Boot 미지원(후속)" → "Boot 지원(본 문서)" 상호 참조 추가 |

### Phase 7 — 테스트 (H)

| 레벨 | 테스트 |
|---|---|
| 단위 | `ProjectTypeDetector` 3종, `BootMvcConfigConfigurer` 멱등성(신규 생성 / 이미 등록 / 메서드 없음 / 안전 삽입 불가) |
| 워크플로우 | `ProjectInitializrBoot50ThymeleafWorkflowTest`(신설) — 기존 `ProjectInitializrWar50ManualWorkflowTest` / `...Boot50StaticResourceWorkflowTest` 패턴 재사용 |
| 통합 | `@SpringBootTest` — 생성 boot 프로젝트가 실제 부팅되고 `/`·CRUD URL 요청 시 `gnbMenus` 모델 주입 확인 (Testcontainers MySQL 또는 임베디드 대체) |

---

## 5. 변경 · 신설 파일 총괄

**신설**
- `service/generation/layout/ProjectTypeDetector.java`
- `service/generation/layout/BootMvcConfigConfigurer.java`
- 생성 산출물: `{packageName}/config/EgovWebMvcConfig.java` (Boot 프로젝트에)
- 테스트 3~4종

**수정**
- `service/initializr/FilePlanFactory.java` (boot+thymeleaf 분기)
- `service/initializr/template/BootBuildGradleBuilder.java`, `BootPomBuilder.java` (thymeleaf 의존성)
- `model/ProjectSpec.java` (4.3+boot+thymeleaf 거부, 선택)
- `service/generation/layout/ThymeleafLayoutGenerationService.java` (타입 분기)
- `service/generation/layout/LayoutGenerationResult.java`, `service/generation/mcp/ThymeleafLayoutResultFormatter.java` (결과 표현)
- `tools/ThymeleafLayoutTool.java`, `tools/ProjectInitializrTool.java` (`@Tool` 설명)
- `service/generation/crud/*` (WAR 전용 처리 타입 가드 — Phase 5 점검 결과에 따라)
- 문서 5종 (Phase 6)

---

## 6. 리스크 · 미해결 질문

| # | 항목 | 대응 |
|---|---|---|
| R1 | **Gradle WAR의 Thymeleaf 런타임 미보강** — `ThymeleafRuntimeConfigurer`가 `pom.xml`만 봄. Boot 지원과 별개로 이미 존재하는 갭 | 본 계획 범위 밖으로 명시. 별도 이슈로 분리(Gradle WAR는 `build.gradle` 의존성 보강 + `servlet-context.xml` ViewResolver는 이미 됨) |
| R2 | `EgovWebMvcConfig.java` 생성 위치 `{packageName}.config`가 사용자 기존 config와 충돌 | 파일 존재 시 멱등 보강만. 클래스명 충돌 시 실패 + 수동 안내 |
| R3 | `@MapperScan` value가 `packageName`과 다르게 커스터마이즈된 경우 GNB Mapper 빈 누락 | 자동 편집 안 함 — 경고 반환(3.4). 문서에 "packageName 일치" 제약 유지 |
| R4 | Boot auto-config ViewResolver 순서 — JSP resolver가 공존하면 우선순위 문제 | Boot+thymeleaf 프로젝트는 JSP resolver를 만들지 않음(Phase 1). `spring.thymeleaf.*` 기본값으로 충분 |
| R5 | `initializeProject` 권장(`viewType="jsp"`)과 Phase 1(`boot+thymeleaf` 지원)의 메시지 상충 | 권장은 유지하되 "thymeleaf로 초기화해도 안전"으로 완화. 둘 다 `generateThymeleafLayout` 필수는 동일 |
| Q1 | Boot에서 `EgovGnbMenuInterceptor`가 `postHandle`로 모델 주입 — Boot의 `RedirectView`/`@ResponseBody` 응답에서 기존 skip 조건(6.1)이 그대로 유효한가 | Phase 4 통합 테스트에서 확인 |
| Q2 | `generateThymeleafLayout`를 파라미터 없이 구조 감지로 분기 vs 명시 파라미터 추가 | 3.1에서 구조 감지로 결정. 리뷰에서 재확인 |

---

## 7. 후속 과제 (본 계획 이후)

- eGovFrame 4.3(`javax.servlet`) Boot 지원
- Gradle WAR Thymeleaf 런타임 보강 (R1)
- GNB 조회 캐싱 (`@Cacheable` 또는 기동 시 로드)
- LNB 동적화 (dynamic-gnb-plan 10절)
- `WebMvcConfigurer` 방식으로 WAR도 통일할지 검토(현재 WAR는 XML patch) — 유지보수 단일화 관점

---

## 8. 완료 기준 (DoD)

1. `initializeProject(projectType="boot", viewType="thymeleaf", egovVersion="5.0")` → `generateThymeleafLayout()` 실행 시:
   - `templates/layout/*.html` 5종 + GNB 컴포넌트 4종 + `EgovWebMvcConfig.java` 생성
   - 결과 메시지에 "Boot 감지 — WebMvcConfigurer 등록 완료" 표기
2. 생성된 Boot 프로젝트가 `./gradlew bootRun`(또는 `@SpringBootTest`)으로 부팅되고, `/` 및 CRUD 목록 URL 요청 시 GNB에 `LETTNMENUINFO` 기반 메뉴가 렌더링됨(데이터 없으면 "홈"만).
3. `generateThymeleafLayout`를 WAR 프로젝트에 실행했을 때 기존 동작(servlet-context.xml patch)이 **회귀 없이** 유지됨.
4. Phase 7 테스트 전부 통과.
5. 문서 5종이 Boot 지원을 반영.
