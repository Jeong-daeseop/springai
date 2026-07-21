# CLAUDE.md

## 행동 규칙

- 사용자가 명시적으로 수정을 요청하기 전까지 코드를 변경하지 않는다.
- 분석·검토 결과는 문서나 설명으로만 제공하고, 구현은 별도 승인 후 진행한다.

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build (실행 가능한 JAR 생성 — Claude Desktop MCP 연동용)
./gradlew bootJar

# 전체 빌드 + 테스트
./gradlew build

# 테스트 실행
./gradlew test

# 단일 테스트 클래스
./gradlew test --tests "com.krdevops.springai.SpringaiApplicationTests"

# 정리
./gradlew clean
```

### 배포 (Claude Desktop 연동)
서버를 기동한 뒤 Claude Desktop에서 MCP 서버 URL을 `http://localhost:8080` 으로 등록합니다.
JAR 경로: `build/libs/springai-0.0.1-SNAPSHOT.jar`

Claude Desktop 설정 파일: `~/Library/Application Support/Claude/claude_desktop_config.json`

### 로컬 실행 전 사전 조건
```bash
docker start egov-mysql          # MySQL 컨테이너
redis-server                     # Redis (RAG 벡터 스토어 + 채팅 메모리 공용)
ollama run qwen3:8b              # 기본 생성 모델
ollama run qwen3:1.7b            # RAG 쿼리 압축 전용 경량 모델
```

### 로그 확인
```bash
# Spring Boot 애플리케이션 로그
tail -f /tmp/springai-mcp.log
```

---

## Architecture

**Spring Boot 4.1.0-RC1 + Spring AI 2.0.0-RC1** 기반 **MCP(Model Context Protocol) Server**.
`Streamable HTTP` 트랜스포트로 Claude Desktop과 JSON-RPC 통신합니다 (port 8080).

```
Claude Desktop
    │ JSON-RPC over Streamable HTTP (port 8080)
    ▼
spring-ai-starter-mcp-server-webmvc  (JSON-RPC dispatcher 자동 처리)
    │ @Tool 어노테이션 메서드 자동 라우팅
    ▼
*Tool 클래스  →  Service  →  Repository(JdbcTemplate) / Redis / Ollama / OpenAI
                               egov-mysql (Docker)
```

**핵심 설정:**
- `web-application-type: servlet` — Tomcat HTTP 서버 활성화 (SSE 지원)
- `mcp.server.protocol: STREAMABLE` — Streamable HTTP 트랜스포트
- `banner-mode: off` — 불필요한 출력 억제
- 애플리케이션 로그는 `/tmp/springai-mcp.log`에만 기록

**Package root:** `com.krdevops.springai`

---

## MCP Tool 등록 패턴

Tool 추가 시 두 단계가 필요합니다:

**1. Tool 클래스 작성** (`tools/` 패키지)
```java
@Component
@RequiredArgsConstructor
public class MyTool {
    @Tool(description = "설명을 한국어로 상세히 작성 — Claude가 이 설명으로 tool을 선택함")
    public String myMethod(String param) { ... }
}
```

**2. McpConfig에 빈 등록** (`config/McpConfig.java`)
```java
// allToolCallbacks 빈의 toolObjects 목록에 추가
@Bean
public ToolCallbackProvider allToolCallbacks(
        ...,
        MyTool myTool) {
    return MethodToolCallbackProvider.builder()
            .toolObjects(..., myTool)
            .build();
}
```

---

## DB 연결

- **Docker 컨테이너:** `egov-mysql` (mysql:8.0, port 3306)
- **DB:** `ebt` / **User:** `ebt` / **Password:** `ebt01`
- **eGovFrame 주요 테이블:** `LETTNEMPLYRINFO`(직원), `LETTNBBSMASTER`(게시판) 등 다수
- **DB 접근:** `JdbcTemplate` (MyBatis는 Spring Boot 4.x 미지원)

egov-mysql 시작:
```bash
docker start egov-mysql
```

---

## 현재 등록된 MCP Tool (21개)

### CRUD 자동 생성
| Tool 클래스 | 주요 메서드 | 기능 |
|---|---|---|
| `SchemaReaderTool` | `getTableSchema`, `getTableList` | MySQL 테이블 컬럼·PK·타입 조회 |
| `CrudPromptBuilderTool` | `buildCrudPrompt` | DB 스키마 → eGovFrame CRUD 프롬프트 조립 |
| `ThymeleafLayoutTool` | `generateThymeleafLayout` | Thymeleaf 공통 layout 5종 + GNB 동적 메뉴 컴포넌트 4종 생성, servlet-context.xml patch |
| `CodeTemplateTool` | `renderCrudTemplate` | FreeMarker로 Controller/Service/Mapper/VO/JSP 렌더링 |
| `CodeSaverTool` | `saveCode` | 생성된 소스를 지정 경로에 파일 저장 |
| `CodeValidatorTool` | `validateCode` | 생성 코드 구문·규칙 검증 |

### 디자인 참조 분석 (선택)
| Tool 클래스 | 주요 메서드 | 기능 |
|---|---|---|
| `DesignReferenceTool` | `analyzeDesignReference`, `analyzeFigmaReference`, `createScreenSpecification`, `approveScreenSpecification`, `reviseScreenSpecification`, `getScreenSpecification`, `findReusableDesignAnalyses` | 로컬 이미지/PDF 또는 Figma 프레임 → `UiDesignSpec` → `ScreenSpecification` 초안·승인. 결과 ID를 `buildFullCrudPrompt`/`buildMasterDetailPrompt`/`buildBoardFeature`의 `designReferenceId`/`screenSpecificationId`로 전달. 상세: 아래 "DesignReferenceTool 사용법" 참고 |

### 프로젝트 초기화
| Tool 클래스 | 주요 메서드 | 기능 |
|---|---|---|
| `ProjectInitializrTool` | `initializeProject`, `getConfigTemplate` | WAR/Boot × 4.3/5.0 프로젝트 골격 파일 일괄 생성 |

### 보안 설정
| Tool 클래스 | 주요 메서드 | 기능 |
|---|---|---|
| `SecurityTemplateTool` | `getSecurityTemplate` | eGovFrame 4.3/5.0 Spring Security 설정 파일 생성 |

### eGovFrame 운영
| Tool 클래스 | 기능 |
|---|---|
| `EmployeeTool` | COMTNEMPLYRINFO CRUD (직원 관리) |
| `MenuTool` | COMTNMENUINFO 메뉴 등록 |
| `AuthTool` | 인증·권한 설정 |
| `CommonCodeTool` | 공통코드 조회 |
| `SqlTool` | DB 방언별 SQL 생성 |

### 프로젝트 관리·가이드
| Tool 클래스 | 기능 |
|---|---|
| `ProjectScannerTool` | 기존 eGovFrame 프로젝트 구조 스캔 |
| `ProjectHealthTool` | 프로젝트 상태 점검 |
| `WorkflowGuideTool` | 단계별 개발 워크플로우 안내 |
| `OutputPathResolverTool` | 출력 경로 결정 및 검증 |
| `GenerationHistoryTool` | 코드 생성 이력 기록·조회 |

### RAG·유틸
| Tool 클래스 | 기능 |
|---|---|
| `RagTool` | eGovFrame 문서 PDF 인제스트 + 유사도 검색 |
| `DateTimeTool` | IANA 시간대 기준 현재 시각 반환 |

---

## ThymeleafLayoutTool 사용법

`CrudPromptBuilderTool`의 Thymeleaf 생성(`buildFullCrudPrompt`/`buildBoardFeature`/`buildMasterDetailPrompt`)은 `layoutMode=reuse`가 기본값이라 화면 생성 시 layout 파일을 다시 만들지 않습니다. **신규 프로젝트에서는 반드시 `generateThymeleafLayout()`을 먼저 호출**해 layout 5종(`default.html`/`gnb.html`/`lnb.html`/`breadcrumb.html`/`footer.html`)을 준비하세요.

**GNB(상단 메뉴)는 이제 `COMTNMENUINFO`(`UPPER_MENU_NO=0`) + `COMTNPROGRMLIST` 조인 기반으로 매 요청마다 동적 렌더링됩니다.** `generateThymeleafLayout()`이 layout 5종과 함께 GNB 메뉴 컴포넌트 4종(`GnbMenuVO.java`, `GnbMenuMapper.java`/`.xml`, `EgovGnbMenuInterceptor.java`)을 `{packageName}.cmm.*`에 생성하고, WAR 프로젝트의 `servlet-context.xml`에 인터셉터 등록 블록을 자동 patch합니다(이미 등록되어 있으면 skip).

**`packageName`은 사실상 필수입니다** — `initializeProject()`에 전달했던 packageName과 반드시 동일해야 합니다. 다르면 `EgovGnbMenuInterceptor`가 실제 CRUD 패키지와 어긋난 위치에 생성되어 동작하지 않습니다(생략 시 `egovframework.let.sample` 기본값 + 경고 문구가 반환되지만, 실제 프로젝트에서는 반드시 명시하세요).

**1차 구현 제약**: WAR 프로젝트만 지원(Boot는 `servlet-context.xml` 자체가 없어 인터셉터 등록 불가 — `WebMvcConfigurer` 방식은 후속 과제), Jakarta Servlet(eGovFrame 5.0)만 지원(4.3/javax는 미지원). 메뉴 트리(`COMTNMENUINFO`) 등록은 `MenuTool.generateMenuInsertSql()`로 별도 수동 실행이 필요합니다(자동 INSERT 안 함).

**권장 순서:**
```
1. initializeProject(packageName="egovframework.let.emp", ...)       → 프로젝트 골격 생성
2. generateThymeleafLayout(outputPath, packageName="egovframework.let.emp")
   → layout 5종 + GNB 메뉴 컴포넌트 4종 생성 + servlet-context.xml patch (최초 1회)
3. (선택) analyzeDesignReference(...) → createScreenSpecification(...) → approveScreenSpecification(...)
   → 디자인 목업/스크린샷/PDF가 있을 때만 수행. 상세: 아래 "DesignReferenceTool 사용법"
4. buildFullCrudPrompt(..., viewType="thymeleaf")                    → layoutMode 기본값(reuse)으로 layout 재사용
   디자인 참조를 반영하려면 designReferenceId 또는 screenSpecificationId를 함께 전달
5. (선택) MenuTool.generateMenuInsertSql(...)                        → GNB/LNB에 실제로 표시될 메뉴 등록
```

**예시 프롬프트:**
```
generateThymeleafLayout(outputPath="/Users/user/Desktop/egov-generated/emp", packageName="egovframework.let.emp") 로 Thymeleaf 공통 layout과 GNB 메뉴 컴포넌트 만들어줘
```
```
layoutBasePath="layout/admin" 으로 관리자용 Thymeleaf layout 만들어줘
```
```
기존 layout/GNB 컴포넌트를 최신 버전으로 덮어써줘 (overwriteLayout=true)
```

layout 파일 없이 `viewType="thymeleaf"`로 화면 생성 Tool을 먼저 호출하면 `layoutMode=reuse` 기본값으로 인해 실패하며, `generateThymeleafLayout()` 선행 실행을 안내하는 메시지가 반환됩니다.

전체 예시문 목록: `docs/tool-reference/ThymeleafLayoutTool_기능및역할_상세설명.md`

---

## DesignReferenceTool 사용법 (선택 — 비전 디자인 참조 통합)

디자인 목업/스크린샷/PDF를 넘겨받아 CRUD·게시판·마스터-디테일 생성에 반영하고 싶을 때만 사용합니다. **기존 순서(`initializeProject` → `generateThymeleafLayout` → `buildFullCrudPrompt` 등)를 대체하지 않으며**, 이 Tool들 호출 직전에 끼워 넣는 선택 단계입니다. 디자인 참조가 없으면 이 섹션 전체를 건너뛰고 기존 방식 그대로 호출하세요.

**동작 조건**: 로컬 파일 분석은 `app.design-vision.provider`가 `openai` 또는 `ollama`일 때 실행됩니다. Figma 분석은 별도 경로이며 `DESIGN_VISION_FIGMA_ENABLED=true`와 `FIGMA_ACCESS_TOKEN`이 모두 필요합니다. 기본값은 Figma 비활성이고 서버는 `127.0.0.1`에 바인딩됩니다. 공공기관 배포 시 망분리·외부 SaaS 허용 여부를 반드시 사전에 확인하세요.

### Figma 참조 분석(P1 로컬 단일 사용자)

```text
analyzeFigmaReference(
  figmaUrl="https://www.figma.com/design/...?...node-id=1-2",
  nodeId=null,
  featureType="crud"
) → analysisId
```

- `/file/...`과 `/design/...` URL만 허용하며 분석할 `node-id`가 필요합니다.
- URL node ID와 별도 `nodeId`를 함께 주면 정규화 후 같아야 합니다.
- 서버 PAT 권한으로 조회하므로 중앙 공유·다중 사용자 배포에는 사용하지 않습니다. 사용자별 권한이 필요한 배포는 OAuth·tenant 격리 구현 이후에만 활성화합니다.
- 레이어와 `absoluteBoundingBox`를 결정론적으로 매핑하며, 회전·클리핑·비가시 레이어는 불확실성으로 반환합니다. 실제 렌더 바운드와의 100% 일치를 보장하지 않습니다.

**⚠️ 레이아웃/GNB 구조 자체는 반영되지 않습니다** — 디자인 참조에서 화면 생성에 반영되는 것은 필드 역할(제목/상태/작성자 등)·액션(검색/등록/삭제)·archetype(CRUD_LIST 등)과 표 밀도(`UiDesignSpec.layout.density`)입니다. GNB/LNB 존재 여부, 콘텐츠 폭, 색상·간격 토큰 등은 생성 layout 구조를 바꾸지 않습니다. 즉 GNB 없는 풀블리드 디자인을 올려도 실제 layout/GNB 구조는 `generateThymeleafLayout()`이 이미 만들어둔 것을 그대로 사용합니다. 표 밀도만 `STANDARD`/`COMPACT`/`COMFORTABLE`로 검증되어 CRUD 목록 wrapper와 `styles.css`에 반영되며, 지원하지 않는 명시값은 즉시 거부됩니다.

### 디자인 참조 CRUD 생성 계약

- `createScreenSpecification()`은 `listColumns`/`detailColumns`를 선택 인자로 받으며 Tool → `ScreenSpecificationService` → `ScreenSpecAssembler`까지 그대로 전달합니다. 명시 컬럼은 `EXPLICIT`, 디자인 분석에서 선택한 컬럼은 `DESIGN_REFERENCE`, 기본 선택은 `DEFAULT`로 `PageSpec.selectionSource`에 기록합니다.
- 화면 표시 컬럼은 복합 PK를 항상 포함하며 PK 포함 최대 6개입니다. 6개를 넘으면 조용히 자르지 않고 예외를 반환합니다. 등록·수정 및 VO/Mapper 스키마 필드는 전체 물리 컬럼을 유지합니다.
- `schemaBindings`는 항상 물리 `COLUMN` source를 사용합니다. 디자인 힌트의 `COMMON_CODE` 판정은 표시 후보에만 사용하며 등록·수정/스키마 계약으로 누수시키지 않습니다.
- Thymeleaf CRUD는 `ScreenSubsetMode.LIST_AND_DETAIL`, JSP CRUD는 `LIST_ONLY`를 사용합니다. JSP에서 detail subset이 요청되면 표준 상세 필드를 유지하고 호환성 경고를 반환합니다.
- Master-detail 생성은 master와 detail 모두 실제 `viewType`을 전달하되 `ScreenSubsetMode.NONE`을 사용합니다. 단일 CRUD와 master-detail의 모든 운영 생성·미리보기 경로는 하위 호환 오버로드가 아니라 `viewType`/`subsetMode`가 명시된 `CrudModelFactory.fromSchema()` 호출을 사용합니다.
- 상세 표시 영역에서는 `SensitiveFieldPolicy`의 토큰 단위 정확 일치로 민감 필드를 제외합니다. `JSP_DETAIL_ROWS`와 Thymeleaf 상세 표시에는 적용하지만 VO/Mapper/등록·수정 폼의 스키마 필드는 제거하지 않습니다. 기존 목록용 `isSensitiveListField()` 동작은 유지합니다.
- 표 밀도는 `ScreenSpecification` 생성·binding resolve·validation 과정에서 보존되며 `reviseScreenSpecification()`으로 변경할 수 없습니다. revision은 현재 명세의 density를 유지합니다.
- 비표준 density(`COMPACT`/`COMFORTABLE`) 생성 시 `TableDensityCssContract`의 marker 계약으로 `styles.css`를 멱등 보강합니다. CSS 경로 또는 marker가 손상되어 안전하게 보강할 수 없으면 다른 파일 저장이나 런타임 설정 변경 전에 즉시 실패합니다.
- record를 재생성하는 코드에서는 `PageSpec.selectionSource`와 `ScreenSpecification.layoutDensity`를 반드시 명시적으로 전달해야 합니다. 신규 필드 누락으로 `DEFAULT`/`STANDARD`로 조용히 초기화되는 회귀를 만들지 마세요.

**흐름은 DB 매핑 난이도에 따라 두 갈래로 갈라집니다:**

1. **단순 케이스** (표준 단일 테이블, JOIN·공통코드·미매핑 없음) — 2단계로 끝남
   ```
   analyzeDesignReference(referencePath) → designAnalysisId
   buildFullCrudPrompt(..., designReferenceId=designAnalysisId)
   ```
   `buildFullCrudPrompt` 내부에서 화면명세가 즉시 `APPROVED`로 생성되어 그대로 진행됩니다.

2. **REVIEW_REQUIRED 케이스** (JOIN·공통코드·미매핑 필드·파일 처리 등 애매한 경우) — 사람 확인 필요
   ```
   analyzeDesignReference(referencePath) → designAnalysisId
   createScreenSpecification(database, tableName, screenName, featureType, designAnalysisId,
                             listColumns=null, detailColumns=null) → REVIEW_REQUIRED + 이슈 목록
   (필요시) reviseScreenSpecification(spec) → 매핑 수정
   approveScreenSpecification(screenSpecificationId) → APPROVED
   buildFullCrudPrompt(..., screenSpecificationId=screenSpecificationId)
   ```
   `screenSpecificationId`가 `APPROVED`가 아닌 채로 전달되면 `buildFullCrudPrompt`/`buildMasterDetailPrompt`가 예외를 던지며 생성이 중단됩니다(파일 저장 없음). `designReferenceId`와 `screenSpecificationId`를 동시에 전달하면 `screenSpecificationId`가 우선합니다.

`findReusableDesignAnalyses(query, expectedArchetype, topK)`로 과거 분석 결과를 후보로 검색할 수 있지만, 결과는 자동 채택되지 않으며 사람이 확인 후 `designAnalysisId`로 직접 선택해야 합니다.

상세 설계와 보안 검토(임의 파일 읽기 차단, 프롬프트 인젝션 방어, 망분리 배포 게이트 등)는 `docs/crud/local-vision-design-reference-integration-review.md`를, Tool별 테스트 현황은 `docs/crud/design-vision-tool-test-priority-detail.md`를 참고하세요.
