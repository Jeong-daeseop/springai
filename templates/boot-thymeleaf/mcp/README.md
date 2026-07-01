# FTC 스타일 SpringAI MCP CrudPromptBuilderTool

## 개요

FreeMarker(`.ftl`) 템플릿 + 변수 JSON → **FTC 스타일 Thymeleaf HTML** 자동 생성  
SpringAI MCP Tool 로 등록하여 Claude 또는 다른 LLM 에서 직접 호출합니다.

---

## 전체 파일 구조

```
ftl-thymeleaf/
├── layout/
│   ├── default.html          ← Thymeleaf Layout Dialect 기본 레이아웃
│   ├── gnb.html              ← GNB (상단바 + 헤더 + 메가메뉴)
│   ├── lnb.html              ← 동적 LNB 사이드바
│   ├── breadcrumb.html       ← 브레드크럼
│   └── footer.html           ← 푸터
├── static/resources/css/
│   ├── styles.css            ← 공통 CSS 진입점
│   └── _ds_bundle.css        ← styles.css 내부 import 대상
├── static/resources/js/
│   └── krds.min.js           ← 공통 JS
├── board/
│   ├── thymeleaf-list.html.ftl    ← 게시판 목록
│   ├── thymeleaf-detail.html.ftl  ← 게시판 상세
│   └── thymeleaf-regist.html.ftl  ← 게시판 등록/수정
├── masterdetail/
│   ├── thymeleaf-list.html.ftl    ← 마스터 목록
│   ├── thymeleaf-detail.html.ftl  ← 마스터+1:N 상세
│   └── thymeleaf-regist.html.ftl  ← 마스터 등록/수정
└── mcp/
    ├── mcp-config.json            ← MCP 툴 설정
    ├── board-sample.json          ← 게시판 샘플 변수
    ├── masterdetail-sample.json   ← 마스터-디테일 샘플 변수
    ├── CrudPromptBuilderTool.java ← SpringAI MCP Tool 클래스
    └── README.md                  ← 이 파일
```

---

## 1. egov-boot-web 적용 위치

| 파일 | 복사 위치 |
|---|---|
| `layout/*.html` | `src/main/resources/templates/layout/` |
| `styles.css`, `_ds_bundle.css` | `src/main/resources/static/resources/css/` |
| `krds.min.js` | `src/main/resources/static/resources/js/` |
| `board/*.ftl`, `masterdetail/*.ftl` | `src/main/resources/mcp-templates/` |
| `CrudPromptBuilderTool.java` | `src/main/java/kr/go/egov/mcp/tool/` |
| `mcp-config.json` | `src/main/resources/mcp/` |

정적 리소스 URL 정책:

- 화면 링크 URL은 `/resources/**`를 유지한다.
- BOOT 프로젝트의 실제 파일 저장 위치는 `src/main/resources/static/resources/**`다.
- 따라서 화면에서는 `/resources/css/styles.css`, `/resources/js/krds.min.js`를 사용한다.
- `_ds_bundle.css`는 `styles.css` 내부 `@import` 대상으로만 사용하고 화면에서 직접 링크하지 않는다.

---

## 2. build.gradle 의존성

```groovy
dependencies {
    // Spring AI MCP Server
    implementation 'org.springframework.ai:spring-ai-mcp-server-webmvc-spring-boot-starter'

    // FreeMarker (템플릿 엔진)
    implementation 'org.springframework.boot:spring-boot-starter-freemarker'

    // Thymeleaf + Layout Dialect
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
    implementation 'nz.net.ultraq.thymeleaf:thymeleaf-layout-dialect:3.3.0'

    // Lombok
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
}
```

---

## 3. application.yml 설정

```yaml
spring:
  freemarker:
    template-loader-path: classpath:/mcp-templates/
    suffix: .ftl
    charset: UTF-8
    cache: false   # 개발 시 false, 운영 시 true

  ai:
    mcp:
      server:
        name: crud-prompt-builder
        version: 1.0.0
        enabled: true
```

---

## 4. FreeMarker 설정 Bean (별도 설정 필요 시)

```java
@Configuration
public class FreeMarkerConfig {

    @Bean
    public freemarker.template.Configuration freemarkerConfiguration() throws Exception {
        freemarker.template.Configuration cfg =
            new freemarker.template.Configuration(
                freemarker.template.Configuration.VERSION_2_3_32);
        cfg.setClassForTemplateLoading(this.getClass(), "/mcp-templates");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(
            freemarker.template.TemplateExceptionHandler.RETHROW_HANDLER);
        return cfg;
    }
}
```

---

## 5. Tool 등록 (Spring AI MCP)

```java
@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider crudTools(CrudPromptBuilderTool tool) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(tool)
            .build();
    }
}
```

---

## 6. 실제 MCP Tool 체계

현재 구현은 기능 단위 오케스트레이션 Tool을 기본으로 사용하고, 필요 시 화면별 `generate*` Tool도 함께 제공한다.

| Tool 이름 | 설명 |
|---|---|
| `buildFullCrudPrompt` | 단일 테이블 CRUD 전체 생성 |
| `buildMasterDetailPrompt` | 1:N 마스터-디테일 CRUD 전체 생성 |
| `buildBoardFeature` | 게시판(BBS) 기능 세트 전체 생성 |
| `buildJoinSelectPrompt` | JOIN이 필요한 단일 테이블의 Mapper/VO 보강 지시 생성 |
| `generateCrudList` / `generateCrudDetail` / `generateCrudRegist` / `generateCrudUpdt` | 단일 CRUD 화면 1개만 렌더링 반환 |
| `generateBoardList` / `generateBoardDetail` / `generateBoardRegist` / `generateBoardUpdt` | 게시판 화면 1개만 렌더링 반환 |
| `generateMasterList` / `generateMasterDetail` / `generateMasterRegist` | 마스터-디테일 마스터 화면 1개만 렌더링 반환 |

핵심 차이:

- `build*` Tool은 한 번의 호출로 Java, Mapper XML, 화면, 레이아웃까지 파일 세트를 생성한다.
- `generate*` Tool은 파일 저장 없이 화면 1개와 권장 저장 경로만 반환한다.
- `viewType="thymeleaf"`일 때 partial layout 구조를 전제로 화면을 렌더링한다.

---

## 7. 사용 방법

### 방법 A — Claude Desktop / MCP Client 에서 직접 호출

일반 CRUD:

```text
buildFullCrudPrompt 호출:
  database    = "com"
  tableName   = "COMTNEMPLYRINFO"
  domain      = "Employer"
  packageName = "egovframework.let.emp"
  outputPath  = "/path/to/project"
  llmProvider = "auto"
  egovVersion = "5.0"
  viewType    = "thymeleaf"
```

게시판:

```text
buildBoardFeature 호출:
  database        = "com"
  domain          = "Bbs"
  packageName     = "egovframework.let.bbs"
  outputPath      = "/path/to/project"
  mainTable       = "COMTNBBS"
  masterTable     = "COMTNBBSMASTER"
  useTable        = "COMTNBBSUSE"
  fileTable       = "COMTNFILE"
  fileDetailTable = "COMTNFILEDETAIL"
  egovVersion     = "5.0"
  viewType        = "thymeleaf"
```

마스터-디테일:

```text
buildMasterDetailPrompt 호출:
  database    = "com"
  masterTable = "COMTNBBSMASTER"
  detailTable = "COMTNBBSUSE"
  domain      = "BbsMaster"
  packageName = "egovframework.let.bbs"
  outputPath  = "/path/to/project"
  llmProvider = "auto"
  egovVersion = "5.0"
  viewType    = "thymeleaf"
```

### 방법 B — Spring AI ChatClient 에서 자동 호출

```java
@Service
@RequiredArgsConstructor
public class TemplateGenerateService {

    private final ChatClient chatClient;

    public String generate(String prompt) {
        return chatClient.prompt()
            .user(prompt)
            .call()
            .content();
    }
}
```

프롬프트 예시:

```text
COMTNBBSMASTER / COMTNBBSUSE 기준으로
Thymeleaf 마스터-디테일 게시판 관리 기능을 생성해줘.
- domain: BbsMaster
- packageName: egovframework.let.bbs
- outputPath: /path/to/project
- viewType: thymeleaf
- egovVersion: 5.0
```

### 방법 C — REST API 직접 호출 (MCP HTTP Transport)

```bash
curl -X POST http://localhost:8080/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "name": "buildBoardFeature",
    "arguments": {
      "database": "com",
      "domain": "Bbs",
      "packageName": "egovframework.let.bbs",
      "outputPath": "/path/to/project",
      "mainTable": "COMTNBBS",
      "masterTable": "COMTNBBSMASTER",
      "useTable": "COMTNBBSUSE",
      "fileTable": "COMTNFILE",
      "fileDetailTable": "COMTNFILEDETAIL",
      "egovVersion": "5.0",
      "viewType": "thymeleaf"
    }
  }'
```

---

## 8. auto / claude 모드

`buildFullCrudPrompt`, `buildMasterDetailPrompt`는 `llmProvider`에 따라 동작이 갈린다.

- `auto`
  - 서버 내부 오케스트레이션이 실제 파일을 생성·저장한다.
  - 사용자는 생성 결과 요약을 받는다.
- `claude`
  - 스키마와 생성 지시문을 반환한다.
  - 이후 LLM이 직접 소스를 작성하고 저장한다.

`buildBoardFeature`는 현재 `auto` 오케스트레이션 방식으로 동작한다.

---

## 9. Thymeleaf 레이아웃/모델 계약

`viewType="thymeleaf"`일 때 생성 결과는 아래 partial 레이아웃 구조를 사용한다.

- `layout/default.html`
- `layout/gnb.html`
- `layout/lnb.html`
- `layout/breadcrumb.html`
- `layout/footer.html`

Controller는 각 화면 진입 전에 아래 모델 속성을 채워야 한다.

- `lnbTitle`
- `lnbMenus`
- `breadcrumbs`
- `currentMenuId`

권장 구조:

- `lnbMenus`: `menuId`, `label`, `url`를 가진 리스트
- `breadcrumbs`: `label`, `url`를 가진 리스트
- `currentMenuId`: 현재 활성 메뉴 식별자

현재 템플릿은 Controller 내부 `populateLayoutModel(...)` 패턴으로 이 계약을 채우는 방향을 사용한다.

---

## 10. 공통 fieldType 참조

| type | 설명 |
|---|---|
| `text` | 일반 텍스트 입력 |
| `textarea` | 멀티라인 텍스트 |
| `select` | 드롭다운 (options 필수) |
| `radio` | 라디오 버튼 (options 필수) |
| `number` | 숫자 입력 |
| `date` | 날짜 입력 |
