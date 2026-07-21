# CrudPromptBuilderTool 기능 및 역할 상세 설명

## 개요

`CrudPromptBuilderTool`은 **eGovFrame 5.x CRUD 전체 소스를 DB 테이블 스키마 기반으로 자동 생성**하는 MCP Tool입니다.
DB 컬럼 조회 → 플레이스홀더 계산 → 11개 레이어 소스 생성까지 한 번에 처리합니다.

---

## 구성 레이어

```
CrudPromptBuilderTool (MCP Tool 진입점)
  ├── CrudPromptBuilderService  — DB 스키마 조회 + 플레이스홀더 계산 + 프롬프트 조립
  ├── MasterDetailService       — 1:N 마스터-디테일 / JOIN SELECT 프롬프트 생성
  ├── CodeService               — 레이어별 템플릿 치환 + 파일 저장 (Path Traversal 차단)
  ├── CodeValidatorService      — 생성된 코드 일괄 검증
  └── GenerationHistoryService  — 생성 이력 DB 저장
```

---

## 기능 1: `buildFullCrudPrompt()` — CRUD 전체 소스 생성

### 파라미터

| 파라미터 | 필수 | 설명 | 예시 |
|----------|------|------|------|
| `database` | ✅ | DB명 | `com` |
| `tableName` | ✅ | 테이블명 | `COMTNEMPLYRINFO` |
| `domain` | ✅ | 도메인명 (대문자 시작) | `Employer` |
| `packageName` | ✅ | 패키지명 | `egovframework.let.emp` |
| `outputPath` | ✅ | 소스 저장 절대경로 | `/Users/me/Desktop/egov-gen/emp` |
| `llmProvider` | ✅ | 생성 주체 (`claude` / `auto`) | `auto` |
| `egovVersion` | ⬜ | `4.3` 또는 `5.0` (기본값 `5.0`) | `5.0` |
| `viewType` | ⬜ | 화면 종류 (`jsp` / `thymeleaf`, 기본값 `jsp`) | `thymeleaf` |
| `layoutMode` | ⬜ | Thymeleaf layout 처리 (`reuse` / `create` / `none`, 기본값 `reuse`) | `reuse` |
| `layoutView` | ⬜ | Thymeleaf layout 경로 | `layout/default` |
| `breadcrumbView` | ⬜ | Thymeleaf breadcrumb 경로 | `layout/breadcrumb` |

### llmProvider 모드 비교

| | `claude` (기본값) | `auto` |
|---|---|---|
| **동작** | 통합 프롬프트를 반환하고 Claude가 Tool을 순서대로 호출 | Tool 내부에서 11개 파일을 직접 생성·저장 |
| **토큰** | 다수의 Tool 호출 소비 | Claude 토큰 97% 절감 |
| **속도** | 상대적으로 느림 | 빠름 (LLM 개입 없음) |
| **유연성** | Claude가 내용 검토·수정 가능 | 결정적(deterministic) 생성 |

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

### 목적
마스터 테이블 상세화면에 디테일 테이블 목록 그리드 탭이 포함된 구조 생성

### 파라미터

| 파라미터 | 설명 | 예시 |
|----------|------|------|
| `database` | DB명 | `com` |
| `masterTable` | 마스터(부모) 테이블 | `COMTNEMPLYRINFO` |
| `detailTable` | 디테일(자식) 테이블 | `COMTNEMPLYRATTRBINFO` |
| `domain` | 마스터 도메인명 | `Employer` |
| `packageName` | 패키지명 | `egovframework.let.emp` |
| `outputPath` | 소스 저장 절대경로 | `/Users/me/Desktop/egov-gen/emp` |
| `viewType` | 화면 종류 (`jsp` / `thymeleaf`) | `thymeleaf` |
| `layoutMode` | Thymeleaf layout 처리 (`reuse` / `create` / `none`) | `reuse` |

### 생성 파일 (총 12개)
- 마스터: VO + Mapper + Service + ServiceImpl + Controller
- 디테일: VO + Mapper
- JSP: List + Detail + Regist + Updt + Detail(디테일 탭 포함)

> `getTableRelations()`에서 자식 테이블이 탐지된 경우 이 Tool 사용

---

## 기능 3: `buildJoinSelectPrompt()` — JOIN SELECT 추가

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

---

## 관련 파일

| 파일 | 역할 |
|------|------|
| `tools/CrudPromptBuilderTool.java` | MCP Tool 진입점 (`@Tool` 어노테이션) |
| `service/CrudPromptBuilderService.java` | DB 조회 + 플레이스홀더 계산 + 프롬프트 조립 |
| `service/MasterDetailService.java` | 1:N 마스터-디테일 / JOIN SELECT 프롬프트 |
| `service/CodeService.java` | 템플릿 치환 + 파일 저장 (Path Traversal 차단) |
| `service/CodeValidatorService.java` | 생성 코드 일괄 검증 |
| `service/GenerationHistoryService.java` | 생성 이력 DB 저장 |
| `service/CommonCodeService.java` | 공통코드 조회 |
| `service/EgovPromptBuilder.java` | 시스템 역할·CRUD 제약조건 프롬프트 블록 |
