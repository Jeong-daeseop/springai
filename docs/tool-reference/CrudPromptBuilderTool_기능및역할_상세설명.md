# CrudPromptBuilderTool 기능 및 역할 상세 설명

> **⚠ 2026-08-27 중요 정정: `CrudPromptBuilderTool`은 MCP에 등록되지 않는 legacy 클래스입니다.**
> `@Component`/`@Service` 어노테이션이 없고, `McpConfig.allToolCallbacks(...)`에도 등장하지 않으며,
> `@McpToolRisk`도 없어(등록 시 기동 자체가 차단되는 구조) 애초에 등록될 수 없습니다. **실제로 MCP 클라이언트가
> 호출할 수 있는 진입점은 이 문서의 각 "기능"마다 다음과 같이 별개 클래스입니다:**
>
> | 기능 | 이 문서에서의 표기 | 실제 살아있는 클래스 |
> |---|---|---|
> | CRUD 전체 소스 생성 | 기능 1 | **`tools/generation/CrudGenerationTool.java`** |
> | 마스터-디테일 | 기능 2 | `tools/generation/MasterDetailGenerationTool.java` |
> | JOIN SELECT | 기능 3 | `tools/generation/JoinQueryTool.java` |
> | 게시판(BBS) | 기능 4 | `tools/generation/BoardGenerationTool.java` |
> | 단일 화면 미리보기 12개 | 기능 5 | `CrudScreenSourceTool`/`BoardScreenSourceTool`/`MasterDetailScreenSourceTool` |
>
> 파일명은 혼동을 피하려 그대로 유지하지만(다른 문서에서 이 파일을 참조 중), **아래 본문의 "MCP Tool 진입점"이라는
> 표현은 전부 위 표의 실제 클래스를 가리키는 것으로 읽어야 합니다.** `CrudPromptBuilderTool` 자체는 각 기능과
> 동일한 이름의 메서드 사본을 갖고 있지만 전부 호출 불가능한 죽은 코드입니다(삭제는 별도 작업, 아직 보류).
> `CrudGenerationTool`은 `CrudPromptBuilderTool.buildFullCrudPrompt()`와 **완전히 동일한 로직**(같은
> `CrudGenerationMcpFacade`로 위임)이므로, 이 문서의 기능 1 설명은 그대로 유효합니다. 세부 비교는
> [`화면생성Tool_3종_비교분석.md`](./화면생성Tool_3종_비교분석.md)도 함께 참고하세요.

## 개요

이 문서가 다루는 CRUD 소스 생성 로직(기능 1)의 실제 진입점은 `CrudGenerationTool`이며, `@Tool` 메서드는
`buildFullCrudPrompt()` **1개**뿐입니다. 마스터-디테일(기능 2)·게시판(기능 4)·단일 화면 미리보기(기능 5)는
`CrudGenerationTool`의 기능이 아니라 각각 `MasterDetailGenerationTool`/`BoardGenerationTool`/
`~ScreenSourceTool` 계열이라는 **별개의 등록된 클래스**가 담당합니다. 이 문서는 (죽은 코드인)
`CrudPromptBuilderTool` 한 파일에 몰려 있던 서술을 그대로 두되, 각 기능 섹션 첫머리에 실제 소유 클래스를
표시해 혼동을 줄였습니다.

---

## 구성 레이어 (실제 생성자 주입 기준 — `CrudGenerationTool` 기준)

```
CrudGenerationTool (MCP Tool 진입점, tools/generation/CrudGenerationTool.java)
  └── CrudGenerationMcpFacade          — buildFullCrudPrompt() 전용
        └── DispatchCrudGenerationUseCase
              └── CrudGenerationDispatchService — llmProvider(auto/claude) 분기점
                    ├── GenerateCrudProjectUseCase (auto)  → CrudModelFactory / CrudTemplateRenderer / CrudOrchestrationService
                    └── BuildCrudPromptUseCase (claude 등)  → CrudPromptBuilderService (프롬프트 텍스트만 조립)
```

> 마스터-디테일/게시판/JOIN/단일 화면 미리보기는 위 구성과 무관한 **별개 클래스·별개 Facade**(각 기능 섹션 참고)이며,
> `CrudGenerationTool`이 이들을 소유하거나 위임하지 않습니다. (과거 버전은 `CrudPromptBuilderTool` 하나가 이 전부를
> 위임하는 것처럼 그렸으나, 그 클래스 자체가 죽은 코드이므로 이 구조도는 실제로 등록된 `CrudGenerationTool` 기준으로
> 다시 그렸습니다.)

---

## 기능 1: `buildFullCrudPrompt()` — CRUD 전체 소스 생성

> **실제 등록된 클래스: `tools/generation/CrudGenerationTool.java`**(`@Component`, MCP 노출). 아래 라인 번호는
> 죽은 코드인 `CrudPromptBuilderTool.java` 기준이나, `CrudGenerationTool.java`(description L19, 메서드 L72)에
> 파라미터·로직이 동일하게 존재한다.

### 파라미터 (총 17개, `CrudPromptBuilderTool.java` L71-84 = `CrudGenerationTool.java` 동일 내용)

| 파라미터 | 필수 | 설명 | 예시 |
|----------|------|------|------|
| `database` | ✅ | DB명 | `com` |
| `tableName` | ✅ | 테이블명 | `LETTNEMPLYRINFO` |
| `domain` | ✅ | 도메인명 (대문자 시작) | `Employer` |
| `packageName` | ✅ | 패키지명 | `egovframework.let.emp` |
| `outputPath` | ✅ | 소스 저장 절대경로 | `/Users/me/Desktop/egov-gen/emp` |
| `llmProvider` | ✅ | 생성 주체 (`auto` / `claude`, **생략 시 `auto`**) | `auto` |
| `egovVersion` | ⬜ | `4.3` 또는 `5.0`(`latest`) (기본값 `5.0`) | `5.0` |
| `viewType` | ⬜ | 화면 종류 (`jsp` / `thymeleaf`, 기본값 `jsp`) | `thymeleaf` |
| `layoutMode` | ⬜ | Thymeleaf layout 처리 (`reuse` / `create`, 기본값 `reuse`) | `reuse` |
| `layoutView` | ⬜ | Thymeleaf layout 경로 (기본값 `layout/default`) | `layout/default` |
| `breadcrumbView` | ⬜ | Thymeleaf breadcrumb 경로 (기본값 `layout/breadcrumb`) | `layout/breadcrumb` |
| `programFileName` | ⬜ | `LETTNPROGRMLIST` 목록 화면 프로그램 파일명. 명시값이 DB 자동조회보다 우선 | |
| `programUrl` | ⬜ | 목록 화면 URL. Controller alias로 사용 | |
| `programKoreanName` | ⬜ | 화면 title/H1/캡션에 쓸 한글명 | |
| `programStorePath` | ⬜ | 프로그램 저장 경로 메타데이터 | |
| `designReferenceId` | ⬜ | `analyzeDesignReference()` 반환 분석 ID | |
| `screenSpecificationId` | ⬜ | `APPROVED` 상태 화면명세 ID. `designReferenceId`보다 우선 | |

> 프로그램 메타데이터(`programFileName` 등) 우선순위: **명시 파라미터 > DB 자동조회(`LETTNPROGRMLIST`/`LETTNMENUINFO`, domain 기준 매칭) > 기존 규칙(packageName+domain) fallback**.
> domain과 일치하는 프로그램이 여러 건이면 자동 선택하지 않고 실패하므로 이때는 `programFileName`을 명시해야 한다.

### llmProvider 모드 비교

> ⚠ **정정**: 이전 버전 문서는 기본값을 `claude`라고 설명했으나, 실제 코드(`CrudGenerationCommand.java` L33-34,
> `CrudGenerationTool.java`/`CrudPromptBuilderTool.java` 동일 description)는 **`llmProvider`를 생략하면 `auto`로 정규화**한다. 아래 표의 "기본값" 표기를 정정.

| | `auto` (기본값) | `claude` |
|---|---|---|
| **동작** | `CrudGenerationDispatchService`가 `GenerateCrudProjectUseCase`로 분기 — Tool 내부에서 11개 파일을 직접 생성·저장 | `BuildCrudPromptUseCase`로 분기 — 통합 프롬프트를 반환하고 Claude가 Tool을 순서대로 호출 |
| **토큰** | Claude 토큰 97% 절감 | 다수의 Tool 호출 소비 |
| **속도** | 빠름 (LLM 개입 없음) | 상대적으로 느림 |
| **유연성** | 결정적(deterministic) 생성 | Claude가 내용 검토·수정 가능 |
| **반환 타입** | `CrudToolResult.Orchestrated` | `CrudToolResult.Prompted` |

---

## 생성 대상 레이어

JSP 기본 생성은 11개 레이어를 만든다. `viewType="thymeleaf"`는 JSP 화면 4개 대신 Thymeleaf 화면 4개를 만들며, `layoutMode="create"`일 때만 layout 레이어 5개를 추가로 생성한다. 기본값인 `layoutMode="reuse"`는 `generateThymeleafLayout()`로 이미 생성한 layout을 재사용한다.

| # | layerKey | 파일명 (domain=Employer) | 저장 경로 |
|---|---|---|---|
| 1 | `vo` | `EmployerVO.java` | `egovframework/let/emp/service/` |
| 2 | `mapper` | `EmployerMapper.java` | `egovframework/let/emp/service/impl/` |
| 3 | `mapperXml` | `EmployerMapper.xml` | `egovframework/let/emp/service/impl/` |
| 4 | `service` | `EmployerService.java` | `egovframework/let/emp/service/` |
| 5 | `serviceImpl` | `EgovEmployerServiceImpl.java` | `egovframework/let/emp/service/impl/` |
| 6 | `controller` | `EgovEmployerController.java` | `egovframework/let/emp/web/` |
| 7 | `controlleradvice` | `EgovEmployerValidationHandler.java` | `egovframework/let/emp/web/` |
| 8 | `jspList` | `EgovEmployerList.jsp` | `jsp/employer/` |
| 9 | `jspDetail` | `EgovEmployerDetail.jsp` | `jsp/employer/` |
| 10 | `jspRegist` | `EgovEmployerRegist.jsp` | `jsp/employer/` |
| 11 | `jspUpdt` | `EgovEmployerUpdt.jsp` | `jsp/employer/` |

> 파일명 규칙: `vo` / `mapper` / `mapperXml` / `service` → `{Domain}접미사`, 나머지 → `Egov{Domain}접미사`

### Thymeleaf 스타일 정책

- Thymeleaf 화면과 layout은 `/resources/css/styles.css`만 직접 링크한다.
- `_ds_bundle.css`는 `styles.css` 내부 `@import` 대상으로만 사용한다.
- 생성 HTML/FTL은 화면별 인라인 `style`을 만들지 않고 `styles.css`의 `egov-*` 공통 클래스를 사용한다.
- `layoutMode="none"` standalone 화면도 `egov-standalone-shell` 등 공통 클래스를 사용한다.

---

## 플레이스홀더 자동 계산

`CrudPromptBuilderService`가 DB 컬럼 정보를 기반으로 아래 값을 자동 계산합니다.

| 플레이스홀더 | 계산 방식 | 예시 |
|---|---|---|
| `{{PACKAGE}}` | 입력값 그대로 | `egovframework.let.emp` |
| `{{DOMAIN}}` | 입력값 그대로 | `Employer` |
| `{{DOMAIN_LC}}` | 첫 글자 소문자 | `employer` |
| `{{TABLE_NAME}}` | 입력값 그대로 | `COMTNEMPLYRINFO` |
| `{{PK_COLUMN}}` | `COLUMN_KEY='PRI'` 탐지 | `EMPLYR_ID` |
| `{{PK_FIELD}}` | PK 컬럼명 camelCase 변환 | `emplyrId` |
| `{{PK_TYPE}}` | DB 타입 → Java 타입 변환 | `String` |
| `{{URL_PREFIX}}` | 패키지에서 자동 추출 | `/emp/employer` |
| `{{DATE}}` | 현재 날짜 | `2026-06-10` |
| `{{VALIDATION_IMPORT}}` | egovVersion 기반 분기 | `jakarta.validation.*` (5.0) / `javax.validation.*` (4.3) |
| `{{VO_FIELDS}}` | 컬럼별 필드 + `@NotBlank` / `@Size` 자동 추가 | |
| `{{MAPPER_COLUMNS}}` | 전체 컬럼 콤마 구분 | `EMPLYR_ID, USER_NM, ...` |
| `{{INSERT_COLUMNS}}` | INSERT용 컬럼 목록 | |
| `{{INSERT_VALUES}}` | MyBatis `#{}` 바인딩 목록 | |
| `{{UPDATE_SET}}` | PK 제외 UPDATE SET 절 | |
| `{{RESULT_MAP_FIELDS}}` | `<id>` / `<result>` 자동 분기 | |
| `{{JSP_LIST_TH}}` | 컬럼 COMMENT 기반 `<th>` 목록 | |
| `{{JSP_LIST_TD}}` | `<c:out>` XSS 처리된 `<td>` 목록 | |
| `{{JSP_DETAIL_ROWS}}` | 상세 화면 `<tr><th><td>` 행 목록 | |
| `{{JSP_FORM_INPUTS}}` | 입력 폼 `<input>` 목록 (PK는 `readonly`) | |

---

## DB 타입 → Java 타입 변환 규칙

| DB 타입 | Java 타입 |
|---|---|
| `int`, `tinyint`, `smallint`, `mediumint` | `Integer` |
| `bigint` | `Long` |
| `decimal`, `numeric`, `float`, `double` | `java.math.BigDecimal` |
| `datetime`, `timestamp`, `date` | `String` |
| `bit`, `boolean` | `Boolean` |
| 그 외 | `String` |

---

## 공통 코드 자동 탐지

컬럼명이 `_CODE` 또는 `_CD`로 끝나는 경우, `COMTCCMMNCODE`와 매칭하여 공통코드 정보를 프롬프트에 자동 포함합니다.

```
컬럼 EMPLYR_STTUS_CODE → EMPLYR_STTUS (공통코드명)
  - A: 재직
  - B: 휴직
  - C: 퇴직
```

> COMTCCMMNCODE 전체를 1회만 조회하여 Map으로 캐싱 — N+1 쿼리 방지

---

## auto 모드 처리 흐름

```
1. CrudModelFactory — DB 컬럼과 프로그램 메타데이터로 타입 안전 모델 구성
2. CrudTemplateRenderer — FreeMarker 레이어를 결정론적으로 렌더링
3. CrudOrchestrationService — 허용 경로에 파일 저장 및 정적 계약 검사
4. GenerationHistoryRecorder — 생성 이력 저장
```

#### auto 모드 결과 예시
```
=== [auto] eGovFrame 5.x CRUD 소스 생성 완료 ===

DB: com | 테이블: COMTNEMPLYRINFO | 도메인: Employer
출력 경로: /Users/me/Desktop/egov-gen/emp

[생성 파일 목록]
  ✅ EmployerVO.java
  ✅ EmployerMapper.java
  ✅ EmployerMapper.xml
  ✅ EmployerService.java
  ✅ EgovEmployerServiceImpl.java
  ✅ EgovEmployerController.java
  ✅ EgovEmployerValidationHandler.java
  ✅ EgovEmployerList.jsp
  ✅ EgovEmployerDetail.jsp
  ✅ EgovEmployerRegist.jsp
  ✅ EgovEmployerUpdt.jsp

총 11개 성공
```

---

## outputPath 결정 규칙

```
1. 사용자가 경로를 명시한 경우        → 그 경로 그대로 사용
2. 기존 프로젝트 경로를 알려준 경우   → resolveProjectOutputPath() 호출하여 확정
3. 경로 언급 없는 경우                → getDefaultOutputPath(domain) 호출
                                        (기본: ~/Desktop/egov-generated/{domain})

※ 절대로 경로를 임의로 결정하거나 추측하지 말 것
※ 경로 확정 후 "이 경로에 생성합니다: {path}" 사용자에게 먼저 알릴 것
```

---

## 기능 2: `buildMasterDetailPrompt()` — 1:N 마스터-디테일 CRUD

> **실제 등록된 클래스: `tools/generation/MasterDetailGenerationTool.java`**(§ MCP에 노출되는 유일한 경로).
> `CrudPromptBuilderTool.buildMasterDetailPrompt()`도 같은 `MasterDetailGenerationMcpFacade`로 위임하는
> 동일 로직을 갖고 있지만, 그 클래스 자체가 MCP에 등록되지 않아 호출 불가능하다.

### 목적
마스터 테이블 상세화면에 디테일 테이블 목록 그리드 탭이 포함된 구조 생성

### 파라미터 (총 14개, `MasterDetailGenerationTool.java` L58-64)

| 파라미터 | 필수 | 설명 | 예시 |
|----------|------|------|------|
| `database` | ✅ | DB명 | `com` |
| `masterTable` | ✅ | 마스터(부모) 테이블 | `LETTNEMPLYRINFO` |
| `detailTable` | ✅ | 디테일(자식) 테이블 | `LETTNEMPLYRATTRBINFO` |
| `domain` | ✅ | 마스터 도메인명 | `Employer` |
| `packageName` | ✅ | 패키지명 | `egovframework.let.emp` |
| `outputPath` | ✅ | 소스 저장 절대경로 | `/Users/me/Desktop/egov-gen/emp` |
| `viewType` | ⬜ | 화면 종류 (`jsp` / `thymeleaf`, 기본값 `jsp`) | `thymeleaf` |
| `egovVersion` | ⬜ | `4.3` 또는 `5.0`(`latest`) (기본값 `5.0`) | `5.0` |
| `llmProvider` | ⬜ | 생성 주체 (`auto` / `claude`, **생략 시 `auto`**) | `auto` |
| `layoutMode` | ⬜ | Thymeleaf layout 처리 (`reuse` / `create`, 기본값 `reuse`) | `reuse` |
| `layoutView` | ⬜ | Thymeleaf layout 경로 (기본값 `layout/default`) | |
| `breadcrumbView` | ⬜ | Thymeleaf breadcrumb 경로 (기본값 `layout/breadcrumb`) | |
| `designReferenceId` | ⬜ | `analyzeDesignReference()` 반환 분석 ID | |
| `screenSpecificationId` | ⬜ | 마스터 테이블 기준 `APPROVED` 화면명세 ID | |

> ⚠ **정정**: 이전 버전 문서는 이 기능에 `llmProvider` 파라미터가 없다고 설명했으나, 실제로는 존재하며
> `MasterDetailGenerationDispatchService`가 CRUD와 별개로 자체 `isAuto()` 분기를 수행한다
> (`MasterDetailGenerationCommand.java` L33-34: 생략 시 `auto`로 정규화).

### llmProvider 분기

| | `auto` (기본값) | `claude` |
|---|---|---|
| **동작** | `GenerateMasterDetailProjectUseCase` — Pipeline이 파일을 직접 생성·저장 | `BuildMasterDetailPromptUseCase` — 스키마 정보와 지시만 반환 |
| **반환 타입** | `MasterDetailToolResult.Orchestrated` | `MasterDetailToolResult.Prompted` |

### 생성 파일 (`viewType=jsp` 기준 총 **14개**)
- 공통 백엔드 10개: masterVo / detailVo / masterMapper / detailMapper / masterMapperXml / detailMapperXml / service / serviceImpl / controller / validationHandler
- 마스터 전용 화면 4개: List / Detail / Regist / Updt
- **디테일 테이블은 자체 화면을 만들지 않는다** — VO/Mapper/MapperXml 3파일만 생성되고, 1:N 관계는 마스터 상세 화면(`jsp-detail.jsp.ftl`) 안에 디테일 목록 테이블이 임베드되는 방식으로 표현된다.

> ⚠ **정정**: 이전 버전 문서는 "총 12개(마스터 5 + 디테일 2 + JSP 5)"로 설명했으나, 현재 코드(`MasterDetailLayerDefinition.java`,
> Tool description L141 "총 14개")와 일치하지 않아 수치를 갱신했다.

> `getTableRelations()`에서 자식 테이블이 탐지된 경우 이 Tool 사용

---

## 기능 3: `buildJoinSelectPrompt()` — JOIN SELECT 추가

> **실제 등록된 클래스: `tools/generation/JoinQueryTool.java`**. `CrudPromptBuilderTool`의 동명 메서드는 호출 불가능한 사본이다.

### 목적
단일 테이블에 JOIN이 필요한 경우 SELECT 쿼리·resultMap·VO 추가 필드 자동 생성

### 파라미터

| 파라미터 | 설명 | 예시 |
|----------|------|------|
| `database` | DB명 | `com` |
| `tableName` | JOIN 추가 대상 테이블 | `COMTNEMPLYRINFO` |

### 반환값
- JOIN SELECT 쿼리 초안
- resultMap 추가 항목
- VO 추가 필드 목록

> `getTableRelations()`에서 공통코드·부서 등 JOIN 후보 컬럼이 탐지된 경우 사용

---

## 기능 4: `buildBoardFeature()` — 게시판(BBS) 기능 세트 생성

> **실제 등록된 클래스: `tools/generation/BoardGenerationTool.java`**(`@Component`, `@McpToolRisk(FILE_WRITE)` L18).
> `CrudPromptBuilderTool.buildBoardFeature()`도 같은 `BoardGenerationMcpFacade`로 위임하는 동일 로직을 갖고
> 있지만, 그 클래스 자체가 MCP에 등록되지 않아 호출 불가능하다.

### 목적
게시판 목록/상세/등록/수정 + 논리삭제 + 조회수 증가 + 마스터 이름 조회를 한 번에 생성. `initializeProject()`와는 별도 단계이며,
사용자가 게시판(BBS) 생성을 명시했을 때만 호출한다.

### 파라미터 (총 21개, `BoardGenerationTool.java` L56-65)

| 파라미터 | 필수 | 설명 | 기본값 |
|----------|------|------|--------|
| `database` | ✅ | DB명 | |
| `domain` | ✅ | 도메인명 PascalCase | `Bbs` |
| `packageName` | ✅ | 패키지명 | `egovframework.let.bbs` |
| `outputPath` | ✅ | 소스 저장 절대경로 | |
| `mainTable` | ⬜ | 게시글 테이블 | `LETTNBBS` |
| `masterTable` | ⬜ | 게시판 마스터 테이블 | `LETTNBBSMASTER` |
| `useTable` | ⬜ | 게시판 사용/권한 테이블(생략 가능) | `LETTNBBSUSE` |
| `fileTable` | ⬜ | 첨부파일 묶음 테이블(생략 가능) | `LETTNFILE` |
| `fileDetailTable` | ⬜ | 첨부파일 상세 테이블(생략 가능) | `LETTNFILEDETAIL` |
| `egovVersion` | ⬜ | eGovFrame 버전 | `5.0` |
| `viewType` | ⬜ | `jsp` / `thymeleaf` | `jsp` |
| `layoutMode` | ⬜ | Thymeleaf layout 처리 (`reuse` / `create`) | `reuse` |
| `layoutView` | ⬜ | layout 경로 | `layout/default` |
| `breadcrumbView` | ⬜ | breadcrumb 경로 | `layout/breadcrumb` |
| `programFileName` | ⬜ | 프로그램 파일명(명시값이 DB 자동조회보다 우선) | |
| `programUrl` | ⬜ | `LETTNPROGRMLIST` URL — query는 `bbsId`, path는 Controller alias | |
| `programKoreanName` | ⬜ | 화면 title/H1/caption 한글명 | |
| `programStorePath` | ⬜ | 프로그램 저장 경로 메타데이터 | |
| `defaultBbsId` | ⬜ | 요청 bbsId가 없을 때만 사용할 게시판 ID(마스터 테이블에서 검증) | |
| `designReferenceId` | ⬜ | `analyzeDesignReference()` 반환 분석 ID | |
| `screenSpecificationId` | ⬜ | 게시글 주 테이블 기준 `APPROVED` 화면명세 ID | |

> ⚠ **테이블명은 하드코딩이 아니다** — `mainTable`~`fileDetailTable`은 전부 `@Nullable`이며, 사용자가 값을 넘기면
> `BoardTableSetResolver`가 그 값을 그대로 채택해 `INFORMATION_SCHEMA.COLUMNS`를 조회한다. 미지정 시에만
> 위 표의 `LETTN*` 이름으로 대체된다. 고정된 것은 테이블 "이름"이 아니라 "게시글/마스터/사용권한/파일/파일상세"라는
> **5개 역할 슬롯 구조**다.

### llmProvider 파라미터 없음

이 Tool은 `llmProvider`를 받지 않는다. `BoardGenerationCommand` 주석: "llmProvider 분기가 없다 — 항상 결정론적
오케스트레이션". `GenerateBoardProjectUseCase → BoardProjectGenerationService`는 분기 없이 항상
`BoardGenerationPipelineService`만 실행하며, 반환값도 `BoardOrchestrationResult` 한 가지 형태로 고정된다
(CRUD/마스터-디테일의 `Orchestrated`/`Prompted` 분기와 다름).

### 생성 파일 수
- `jsp`: 화면 4개 포함 12개 파일
- `thymeleaf`: 화면 4개 포함 12개 파일(`layoutMode="create"` 시 17개)

---

## 기능 5: 단일 화면 미리보기 Tool 12개 — `ScreenSourceMcpFacade` 경유

> **실제 등록된 클래스: `CrudScreenSourceTool`/`BoardScreenSourceTool`/`MasterDetailScreenSourceTool`**
> (모두 `McpConfig`에 등록됨). `CrudPromptBuilderTool`의 동명 메서드 12개는 호출 불가능한 사본이다.
> 기존 README의 "화면별 MCP Tool" 패턴과의 호환을 위해 추가된 세분 Tool이며(`generateBoardList`
> description 참고), **전체 레이어 세트를 저장하지 않고 화면 1개만 렌더링해 반환**한다는 공통점을 가진다.
> 파일 저장 없음 — 미세 조정·확인용.

| 그룹 | Tool | 대상 |
|---|---|---|
| CRUD | `generateCrudList` / `generateCrudDetail` / `generateCrudRegist` / `generateCrudUpdt` | 단일 테이블 화면 1개 |
| 게시판 | `generateBoardList` / `generateBoardDetail` / `generateBoardRegist` / `generateBoardUpdt` | 게시판 화면 1개(테이블 역할 슬롯 5개 파라미터 동일) |
| 마스터-디테일 | `generateMasterList` / `generateMasterDetail` / `generateMasterRegist` / `generateMasterUpdt` | 마스터 화면 1개(`masterTable`/`detailTable` 파라미터) |

공통 특징:
- CRUD 그룹 파라미터: `database`, `tableName`, `domain`, `packageName`, `outputPath`, `egovVersion`(선택), `viewType`(선택)
- 게시판 그룹 파라미터: `buildBoardFeature`와 동일한 5개 테이블 슬롯 + 프로그램 메타데이터 선택값
- 마스터-디테일 그룹 파라미터: `masterTable`/`detailTable` 둘 다 필수
- 전부 `ScreenSourceMcpFacade`(`generateCrudScreenSource` / `generateBoardScreenSource` / `generateMasterDetailScreenSource`)로 위임되며, `ScreenType`(LIST/DETAIL/REGIST/UPDT) enum으로 화면 종류만 구분한다.

---

## 전체 CRUD 생성 워크플로우

```
Step 1. getTableList(database)
        → 테이블 목록 확인

Step 2. getTableSchema(database, tableName)
        → 컬럼 구조 파악

Step 3. getTableRelations(database, tableName)
        → 자식 테이블(1:N) 또는 JOIN 후보 컬럼 탐지
        → 자식 테이블 있음: buildMasterDetailPrompt() 사용
        → JOIN 후보 있음: buildJoinSelectPrompt() 병행
        → 단순 CRUD: buildFullCrudPrompt() 사용

Step 4. (viewType=thymeleaf + layoutMode=reuse인 경우) generateThymeleafLayout(...)
        → 공통 layout과 GNB 메뉴 컴포넌트 최초 1회 생성

Step 5. buildFullCrudPrompt(... llmProvider="auto")
        → 11개 파일 자동 생성 + 저장 + 검증 + 이력 저장

Step 6. MenuTool.generateMenuInsertSql()
        → 생성된 URL로 메뉴 등록

Step 7. AuthTool.generateAuthInsertSql()
        → URL 접근 권한 등록
```

---

## 테스트 예시문

### 기본 CRUD (auto 모드)
```
COMTNEMPLYRINFO 테이블로 Employer 도메인 CRUD 소스 자동 생성해줘
database=com, tableName=COMTNEMPLYRINFO, domain=Employer
packageName=egovframework.let.emp, llmProvider=auto
outputPath=/Users/me/Desktop/egov-gen/emp
```

### 기본 CRUD (claude 모드 — 프롬프트 반환)
```
COMTNEMPLYRINFO 테이블로 Employer CRUD 생성 프롬프트 만들어줘
database=com, domain=Employer, packageName=egovframework.let.emp, llmProvider=claude
```

### 마스터-디테일
```
COMTNEMPLYRINFO(마스터) + COMTNEMPLYRATTRBINFO(디테일) 1:N CRUD 생성해줘
```

### JOIN SELECT
```
COMTNEMPLYRINFO 테이블에 공통코드 JOIN SELECT 쿼리 생성해줘
```

### 게시판(BBS)
```
LETTNBBS 기본 테이블로 Bbs 게시판 기능 생성해줘
database=com, domain=Bbs, packageName=egovframework.let.bbs
outputPath=/Users/me/Desktop/egov-gen/bbs
```

---

## 관련 파일

| 파일 | 역할 |
|------|------|
| `tools/generation/CrudGenerationTool.java` | **실제 등록된** CRUD MCP Tool 진입점 (`@Component`, `buildFullCrudPrompt` 1개) |
| `tools/generation/MasterDetailGenerationTool.java` | 마스터-디테일 전용 진입점 (같은 Facade 공유) |
| `tools/generation/BoardGenerationTool.java` | 게시판 전용 진입점 (같은 Facade 공유) |
| `tools/generation/JoinQueryTool.java` | JOIN SELECT 전용 진입점 |
| `tools/generation/CrudScreenSourceTool.java` 등 3종 | 단일 화면 미리보기 12개의 실제 등록 진입점 |
| `tools/CrudPromptBuilderTool.java` | ⚠ **MCP 미등록 legacy 코드** — 위 5개 클래스의 메서드 사본 17개를 갖고 있으나 전부 호출 불가 |
| `service/generation/mcp/CrudGenerationMcpFacade.java` | `buildFullCrudPrompt` → Command 변환 → Dispatch 호출 → 결과 포맷 |
| `service/generation/mcp/MasterDetailGenerationMcpFacade.java` | `buildMasterDetailPrompt` 동일 역할 |
| `service/generation/mcp/BoardGenerationMcpFacade.java` | `buildBoardFeature` 동일 역할 |
| `service/generation/mcp/ScreenSourceMcpFacade.java` | 단일 화면 미리보기 12개 Tool의 공통 위임 대상 |
| `service/generation/crud/CrudGenerationDispatchService.java` | CRUD `llmProvider`(auto/claude) 분기의 유일한 지점 |
| `service/generation/masterdetail/MasterDetailGenerationDispatchService.java` | 마스터-디테일 `llmProvider` 분기(CRUD와 별개 구현체) |
| `service/CrudPromptBuilderService.java` | `claude` 경로에서 DB 조회 + 플레이스홀더 계산 + 프롬프트 조립 |
| `service/CrudModelFactory.java` / `service/CrudTemplateRenderer.java` / `service/CrudOrchestrationService.java` | `auto` 경로 — 결정론적 모델 구성 → FreeMarker 렌더링 → 파일 저장 |
| `service/MasterDetailService.java` | `buildJoinSelectPrompt()` 전용 |
| `service/BoardTableSetResolver.java` | 게시판 테이블 역할 슬롯 → 실제 테이블명 해석(명시값 우선, 미지정 시 `LETTN*`) |
| `service/CommonCodeService.java` | 공통코드 조회 |
| `service/EgovPromptBuilder.java` | 시스템 역할·CRUD 제약조건 프롬프트 블록 |

> 세 Tool(CRUD/마스터-디테일/게시판) 간 구조 차이의 전체 비교는 [`화면생성Tool_3종_비교분석.md`](./화면생성Tool_3종_비교분석.md) 참고.
