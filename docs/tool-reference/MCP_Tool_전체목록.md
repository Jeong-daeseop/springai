# MCP Tool 전체 목록

> **2026-08-27 코드 대조로 전체 재작성.** 등록 기준: `McpConfig.java`의 `allToolCallbacks(...)` 파라미터·`toolObjects(...)` 호출(등록되지 않는 legacy 클래스, 예: `CrudPromptBuilderTool`은 제외). 각 Tool 클래스 파일을 직접 Read해 `@Tool` 어노테이션이 붙은 메서드만 셌다.
> 총 **37개 Tool 파일 / 102개 `@Tool` 메서드** — `McpToolDefinitionSnapshotTest`의 `EXPECTED_TOOL_OBJECT_COUNT=37`/`EXPECTED_TOOL_METHOD_COUNT=102`와 정확히 일치 확인.

---

## 1. DB / SQL

### SqlTool (3개)
| 메서드 | 설명 |
|--------|------|
| `executeQuery(sql)` | SELECT 전용 실행 (INSERT/UPDATE/DELETE 차단) |
| `explainQuery(sql)` | SQL EXPLAIN 실행 (쿼리 실행 계획 분석) |
| `getSampleData(database, tableName, limit)` | 테이블 샘플 데이터 조회 |

### SchemaReaderTool (3개)
| 메서드 | 설명 |
|--------|------|
| `getTableList(database)` | DB 내 테이블 전체 목록 조회 |
| `getTableSchema(database, tableName)` | 컬럼 구조 / PK / 타입 조회 |
| `getTableRelations(database, tableName)` | FK 관계 / JOIN 후보 / 자식 테이블 탐지 |

---

## 2. CRUD / 게시판 / 마스터-디테일 소스 생성

> **2026-08-27 정정:** 아래는 원래 `CrudPromptBuilderTool` 한 클래스로 표기돼 있었으나, 그 클래스는
> `@Component`/`@McpToolRisk`가 없어 **MCP에 등록되지 않는 legacy 코드**임을 확인했다. 실제로 각 기능을
> 노출하는 등록된 클래스는 아래처럼 8개로 나뉜다 — 상세 비교는 `화면생성Tool_3종_비교분석.md` 참고.

### CrudGenerationTool (1개)
| 메서드 | 설명 |
|--------|------|
| `buildFullCrudPrompt(database, tableName, domain, packageName, outputPath, llmProvider, egovVersion, viewType, layoutMode, layoutView, breadcrumbView, programFileName, programUrl, programKoreanName, programStorePath, designReferenceId, screenSpecificationId)` | CRUD 전체 소스 생성. `llmProvider=auto`(기본값)면 서버가 직접 파일 저장, `claude`면 프롬프트만 반환. `viewType=thymeleaf`는 `layoutMode=reuse/create` 지원 |

### MasterDetailGenerationTool (1개)
| 메서드 | 설명 |
|--------|------|
| `buildMasterDetailPrompt(database, masterTable, detailTable, domain, packageName, outputPath, viewType, egovVersion, llmProvider, layoutMode, layoutView, breadcrumbView, designReferenceId, screenSpecificationId)` | 1:N 마스터-디테일 CRUD 생성. `llmProvider` 분기는 CRUD와 별개 구현체 |

### BoardGenerationTool (1개)
| 메서드 | 설명 |
|--------|------|
| `buildBoardFeature(database, domain, packageName, outputPath, mainTable, masterTable, useTable, fileTable, fileDetailTable, egovVersion, viewType, layoutMode, layoutView, breadcrumbView, programFileName, programUrl, programKoreanName, programStorePath, defaultBbsId, designReferenceId, screenSpecificationId)` | 게시판(BBS) 기능 세트 생성(목록/상세/등록/수정/논리삭제/조회수증가). `llmProvider` 파라미터 없음 — 항상 결정론적 파이프라인 |

### JoinQueryTool (1개)
| 메서드 | 설명 |
|--------|------|
| `buildJoinSelectPrompt(database, tableName)` | JOIN SELECT 쿼리 + resultMap + VO 추가 필드 생성 |

### CrudScreenSourceTool (4개)
| 메서드 | 설명 |
|--------|------|
| `generateCrudList(database, tableName, domain, packageName, outputPath, egovVersion, viewType)` | 단일 테이블 CRUD 목록 화면 1개만 렌더링(파일 저장 없음) |
| `generateCrudDetail(...)` | 상세 화면 1개만 렌더링 |
| `generateCrudRegist(...)` | 등록 화면 1개만 렌더링 |
| `generateCrudUpdt(...)` | 수정 화면 1개만 렌더링 |

### BoardScreenSourceTool (4개)
| 메서드 | 설명 |
|--------|------|
| `generateBoardList(database, domain, packageName, outputPath, mainTable, masterTable, useTable, fileTable, fileDetailTable, ...)` | 게시판 목록 화면 1개만 렌더링 |
| `generateBoardDetail(...)` | 게시판 상세 화면 1개만 렌더링 |
| `generateBoardRegist(...)` | 게시판 등록 화면 1개만 렌더링 |
| `generateBoardUpdt(...)` | 게시판 수정 화면 1개만 렌더링 |

### MasterDetailScreenSourceTool (4개)
| 메서드 | 설명 |
|--------|------|
| `generateMasterList(database, masterTable, detailTable, domain, packageName, outputPath, ...)` | 마스터 목록 화면 1개만 렌더링 |
| `generateMasterDetail(...)` | 마스터 상세 화면 1개만 렌더링 |
| `generateMasterRegist(...)` | 마스터 등록 화면 1개만 렌더링 |
| `generateMasterUpdt(...)` | 마스터 수정 화면 1개만 렌더링 |

### CrudGenerationSnapshotTool (1개)
| 메서드 | 설명 |
|--------|------|
| `adoptCurrentAsBaseline(database, tableName, domain, packageName, outputPath, viewType)` | 5축 파이프라인 Ownership 보호(`V2_PREVIEW` 이상) 켜기 전 이미 생성된 화면을, 재생성 시 비교 기준(Base)이 될 스냅샷으로 등록. 파일은 건드리지 않음 |

---

## 3. Thymeleaf Layout · 코드 저장 · 검증

### ThymeleafLayoutTool (1개)
| 메서드 | 설명 |
|--------|------|
| `generateThymeleafLayout(outputPath, layoutBasePath, overwriteLayout, packageName, menuTableName, programTableName)` | Thymeleaf 공통 layout 5종(default/gnb/lnb/breadcrumb/footer) + GNB 동적 메뉴 컴포넌트 4종(VO/Mapper/MapperXml/Interceptor) + eGovFrame 로고 + Thymeleaf main.html 생성, WAR `servlet-context.xml`/`context-common.xml`에 인터셉터·Mapper 등록 자동 patch. GNB는 `menuTableName`(기본 `LETTNMENUINFO`)+`programTableName`(기본 `LETTNPROGRMLIST`) 기반 동적 렌더링. `packageName`은 `initializeProject()`와 동일값 필수. WAR + Jakarta Servlet(5.0)만 지원 |

### CodeTemplateTool (1개)
| 메서드 | 설명 |
|--------|------|
| `getCodeTemplate(layerKey, placeholders)` | 레이어별 템플릿 단독 반환 (vo/mapper/service/controller/jsp 등) |

### CodeSaverTool (3개)
| 메서드 | 설명 |
|--------|------|
| `generateSource(layerKey, placeholders)` | **폐기 호환용** — `buildFullCrudPrompt(llmProvider="auto")` 전환 안내 반환 |
| `saveGeneratedCode(filePath, code)` | 파일 저장 (Path Traversal 차단) |
| `checkOutputDirectory(baseDir)` | 출력 디렉터리 존재 여부 + 쓰기 권한 확인 |

### CodeValidatorTool (5개)
| 메서드 | 설명 |
|--------|------|
| `validateGeneratedCode(filePath)` | 단일 파일 검증 (import, annotation, syntax 체크) |
| `validateGeneratedCodeDirectory(directoryPath)` | 디렉터리 내 전체 파일 일괄 검증 |
| `validateThymeleafRendering(directoryPath)` | 실제 Thymeleaf 엔진 + fixture 모델로 렌더링해 템플릿 구문·렌더링 오류 검사 |
| `auditGeneratedQuality(directoryPath)` | 공통 계약(FreeMarker/Claude Design 잔존 태그, Mapper `${}` 치환, 외부 URL) + 접근성(lang, alt, 버튼 이름) 검사 |
| `validateGeneratedProjectBuild(projectRootPath)` | 생성된 프로젝트를 별도 Maven/Gradle 프로세스로 컴파일 검증(기본 비활성, `EGOV_ALLOW_BUILD_EXECUTION=true` 필요) |

---

## 4. 디자인 참조 분석 (로컬 캡처 / Vision)

### DesignReferenceTool (7개)
| 메서드 | 설명 |
|--------|------|
| `analyzeDesignReference(referencePath, pageRange, featureType)` | PNG/JPEG/이미지형 PDF를 Vision으로 분석 |
| `analyzeFigmaReference(figmaUrl, nodeId, featureType)` | Figma node JSON을 결정론적으로 `UiDesignSpec`에 매핑 |
| `findReusableDesignAnalyses(query, expectedArchetype, expectedFeatureType, topK)` | 현재 실행 계약·화면 유형과 호환되는 분석 후보 검색 |
| `createScreenSpecification(...)` | DB 스키마와 선택적 디자인 분석을 화면명세로 결합 |
| `approveScreenSpecification(id)` | 화면명세 승인 |
| `reviseScreenSpecification(specification)` | 검토 필요 화면명세 수정 |
| `getScreenSpecification(id)` | 최신 화면명세 조회 |

### CaptureWebPageTool (2개)
| 메서드 | 설명 |
|--------|------|
| `captureWebPage(request)` | 허용된 로컬 JSP/Thymeleaf 화면을 Chromium으로 캡처해 Design Artifact 생성 |
| `captureWebPageMultiViewport(request)` | 같은 화면을 Desktop(1440)/Tablet(768)/Mobile(390) 3개 viewport로 캡처하고 `selectorHint` 기준으로 컴포넌트를 대응(MATCHED_ALL/HIDDEN_IN_SOME/MOVED)시킨 `RenderedDesignBundle` 반환 |

### DesignArtifactTool (5개)
| 메서드 | 설명 |
|--------|------|
| `getDesignArtifact(artifactId)` | artifact 메타데이터·요약·경고 조회 |
| `prepareFigmaImport(artifactId)` | Figma Plugin용 `.figpack` 준비 |
| `prepareFigmaBundleImport(bundle)` | `captureWebPageMultiViewport` 결과를 Figma Plugin이 가져올 수 있는 zip(bundle.json + viewport별 .figpack)으로 내보냄 |
| `analyzeCapturedDesign(artifactId, featureType)` | artifact를 결정론적 `UiDesignSpec`으로 변환 |
| `getWebCaptureStatus()` | WEB_CAPTURE와 extractor 준비 상태 점검 |

---

## 5. Figma 연동 (Export / Design System / Orchestration)

> 아래 4개 Tool 클래스의 메서드는 대부분 `figmaMcpSecret` 인증값이 필수이며, Publish·삭제·Key 교체 등
> 되돌리기 어려운 작업은 수행하지 않고 사람의 승인 절차로 넘긴다는 공통 제약을 갖는다.

### FigmaExportTool (2개)
| 메서드 | 설명 |
|--------|------|
| `generateFigmaScreenSpec(figmaMcpSecret, request)` | 승인된 ScreenSpecification에서 FigmaScreenSpec 생성 |
| `validateFigmaScreenSpec(figmaMcpSecret, screenId, version)` | 저장된 FigmaScreenSpec의 필수값·logicalNodeId·지원 Component 문제 검증 |

### DesignSystemTool (4개)
| 메서드 | 설명 |
|--------|------|
| `validateDesignSystemSpec(figmaMcpSecret, spec)` | DesignSystemSpec의 논리 Component·Property·Pattern 참조 검증 |
| `auditComponentRegistry(figmaMcpSecret, profileId, registryVersion)` | 저장된 ComponentRegistry/DesignSystemProfile의 Publish 상태·버전 드리프트 점검 |
| `preflightComponentRegistry(figmaMcpSecret, profileId, registryVersion, requiredLogicalTypes, expectedLayoutPolicyVersion)` | 화면 생성 전 Registry/Profile 드리프트와 필수 논리 컴포넌트 해석 가능 여부 점검 |
| `previewStyleTokenDiff(figmaMcpSecret, fileKey, profileId)` | 참조 Figma 파일 Styles와 운영 Profile Token 차이를 MATCHED/NEW_CANDIDATE/UNBOUND_IN_PROFILE로 분류(조회 전용) |

### FigmaDesignOrchestrationTool (10개)
| 메서드 | 설명 |
|--------|------|
| `createDesignFromText(figmaMcpSecret, prompt, fileKey)` | 텍스트 설명으로 새 Figma 화면 생성(요청 유형 자동 감지) |
| `createDesignFromReference(...)` | 기존 Figma 화면을 참조해 스타일·레이아웃·컴포넌트가 유사한 새 화면 생성 |
| `modifyExistingDesign(...)` | 기존 화면 수정(지정 노드 ID 범위 내에서만, 미승인 컴포넌트 차단) |
| `createDesignFromImage(...)` | 이미지/스크린샷의 UI/UX 패턴을 분석해 FigmaScreenSpec으로 변환·생성 |
| `createMultiScreenFlow(...)` | 여러 화면을 한 번에 생성 + Navigation 플로우 설정(하나라도 실패하면 전체 거부) |
| `createDesignWithComponents(...)` | 지정 컴포넌트(button/form/table 등)로 화면 생성(승인된 ComponentRegistry 컴포넌트만 허용) |
| `convertPlatform(...)` | 기존 화면을 다른 플랫폼(Desktop↔Tablet↔Mobile)으로 변환, Component Swap 자동 적용 |
| `generateFigmaBundleForOperation(figmaMcpSecret, operationId)` | ANALYZED 상태 Operation에 대해 실제 ScreenSpecification·FigmaExportBundle 생성 |
| `bindFigmaDesignRequestTable(...)` | AWAITING_TABLE_BINDING 상태 Operation에 database/tableName을 채워 분석 재실행 |
| `previewPlatformConversion(...)` | targetPlatform 변환 시 적용될 Grid·Navigation·Component Swap을 미리 계산(조회 전용) |

### FigmaApprovedSpecificationTool (1개)
| 메서드 | 설명 |
|--------|------|
| `createFigmaBundleFromApprovedSpecification(...)` | APPROVED ScreenSpecification을 Figma Bundle Artifact로 생성하고 PREVIEW_READY Operation 반환 |

---

## 6. Thymeleaf 마이그레이션 승인 워크플로우

> CRUD 소스 생성(§2)과는 완전히 별개의 상태기계다 — 레거시 JSP 화면을 Thymeleaf로 변환해 덮어쓰는
> 작업이라 명시적 승인(Preview Hash 일치)이 강제된다. 상세는 아키텍처 다이어그램 14번 항목 참고.

### ThymeleafBindingGenerationTool (1개)
| 메서드 | 설명 |
|--------|------|
| `previewThymeleafBindingGeneration(...)` | 프로젝트의 JSP·Controller·VO를 분석해 Binding Contract 기반 Thymeleaf 생성 미리보기(대상 프로젝트 미변경) |

### ThymeleafProjectWorkflowTool (6개)
| 메서드 | 설명 |
|--------|------|
| `previewThymeleafProject(...)` | Thymeleaf 생성 파일의 Preview + diff 기준 hash 생성(변경 없음) |
| `approveThymeleafProject(sharedSecret, operationId, previewHash)` | Preview hash가 일치하는 Operation을 명시적으로 승인 |
| `applyThymeleafProject(sharedSecret, operationId)` | 승인된 Operation을 source revision 재검증 후 원자 적용 |
| `revalidateThymeleafProject(sharedSecret, operationId)` | 적용된 파일의 검증 Gate 재실행 |
| `revalidateThymeleafProjectWithBrowserGate(...)` | 정적 Gate + 실제 브라우저 렌더 검증 |
| `getThymeleafProjectReport(sharedSecret, operationId)` | Operation의 현재 상태·Preview hash 조회 |

### ThymeleafBaselineApprovalTool (1개)
| 메서드 | 설명 |
|--------|------|
| `approveThymeleafBaseline(...)` | 적용된 Thymeleaf 화면을 지금 렌더해 시각 비교 baseline으로 승인 |

---

## 7. 프로젝트 초기화

### ProjectInitializrTool (2개)
| 메서드 | 설명 |
|--------|------|
| `initializeProject(projectName, groupId, artifactId, packageName, buildTool, projectType, egovVersion, outputPath)` | eGovFrame 4.3/5.0 WAR/Boot 프로젝트 골격 생성 |
| `getConfigTemplate(configType, packageName)` | 설정 파일 단독 반환 (contextCommon/applicationYml 등) |

### ProjectScannerTool (1개)
| 메서드 | 설명 |
|--------|------|
| `scanProjectStructure(projectRootPath)` | 기존 프로젝트 구조 스캔 → PROJECT_CONTEXT 블록 생성 |

### ProjectHealthTool (1개)
| 메서드 | 설명 |
|--------|------|
| `checkProjectHealth(projectRootPath, domain)` | 프로젝트 구조 / 설정 파일 / 빌드 파일 이상 탐지 |

### OutputPathResolverTool (2개)
| 메서드 | 설명 |
|--------|------|
| `getDefaultOutputPath(domain)` | 기본 출력 경로 반환 (`~/Desktop/egov-generated/{domain}`) |
| `resolveProjectOutputPath(projectRootPath, packageName, domain)` | PROJECT_CONTEXT 기반 outputPath 자동 계산 |

---

## 8. 보안 / 메뉴 / 권한

### SecurityTemplateTool (1개)
| 메서드 | 설명 |
|--------|------|
| `getSecurityTemplate(securityType, packageName, egovVersion, outputPath, projectType)` | Spring Security 설정 파일 생성 (단일 타입 또는 setup-* 조합 키워드) |

**주요 조합 키워드:**

| 키워드 | 파일 수 | 대상 |
|--------|---------|------|
| `setup-all-war-43-xml` | 10개 | eGovFrame 4.3 WAR XML 방식 |
| `setup-all-war-43-java` | 12개 | eGovFrame 4.3 WAR Java Config 방식 |
| `setup-all-war-50` | 11개 | eGovFrame 5.0 WAR |
| `setup-filters` | 4개 | loginFilter + logoutFilter + loginPolicyFilter + sessionMapping |

### MenuTool (2개)
| 메서드 | 설명 |
|--------|------|
| `getMenuStructure(menuNo)` | COMTNMENUINFO 메뉴 트리 조회 + 권장 MENU_NO/MENU_ORDR 계산 |
| `generateMenuInsertSql(upperMenuNo, urlPrefix, menuNm, progrmFileNm)` | COMTNPROGRMLIST + COMTNMENUINFO INSERT SQL 생성 |

### AuthTool (2개)
| 메서드 | 설명 |
|--------|------|
| `getProgramList(keyword)` | COMTNPROGRMLIST 중복 확인 (LIKE 검색) |
| `generateAuthInsertSql(urlPrefix, programNm, domain)` | COMTNROLEINFO + COMTNAUTHORROLERELATE INSERT SQL 생성 |

---

## 9. 공통코드 / 직원

### CommonCodeTool (2개)
| 메서드 | 설명 |
|--------|------|
| `getCommonCode(codeId)` | COMTCCMMNCODE 공통코드 단건 조회 |
| `searchCommonCode(keyword)` | 공통코드 키워드 검색 |

### EmployeeTool (5개)
| 메서드 | 설명 |
|--------|------|
| `getEmployeeList(keyword)` | COMTNEMPLYRINFO 목록 조회 (최대 20건) |
| `getEmployee(emplyrId)` | 직원 단건 조회 |
| `createEmployee(...)` | 직원 등록 (BCrypt 비밀번호 해싱) |
| `updateEmployee(...)` | 직원 정보 수정 |
| `deleteEmployee(emplyrId)` | 직원 삭제 |

---

## 10. RAG / 문서

### RagTool (5개)
| 메서드 | 설명 |
|--------|------|
| `ragSearch(query, topK)` | 벡터 저장소 유사도 검색 |
| `ragIngest(docId, content, type)` | 텍스트 직접 인덱싱 |
| `ragIngestUrl(url, docId)` | URL 콘텐츠 크롤링 + 인덱싱 |
| `ragIngestUrls(urls)` | 복수 URL 병렬 인덱싱 (Semaphore 제어) |
| `ragIngestDirectory(directoryPath)` | 로컬 디렉터리 파일 일괄 인덱싱 |

---

## 11. 유틸리티 / 워크플로우

### DateTimeTool (2개)
| 메서드 | 설명 |
|--------|------|
| `getCurrentDateTime(timezone)` | IANA 시간대 기준 현재 시각 반환 |
| `celsiusToFahrenheit(celsius)` | 섭씨 → 화씨 변환 |

### GenerationHistoryTool (2개)
| 메서드 | 설명 |
|--------|------|
| `getGenerationHistory(keyword)` | 소스 생성 이력 조회 |
| `saveGenerationHistory(tableName, domain, packageName, ...)` | 생성 이력 DB 저장 |

### WorkflowGuideTool (3개)
| 메서드 | 설명 |
|--------|------|
| `suggestNextStep(currentContext)` | 현재 단계 기반 다음 단계 가이드 |
| `suggestProjectSetupCrudWorkflow(currentContext)` | `initializeProject()` 실행 후 DB 설정→스키마 조회→CRUD 프롬프트 생성→코드 저장→빌드 검증 순서로 다음 단계 안내(빈 문자열 입력 시 9단계 전체 안내) |
| `suggestSecurityMenuAuthWorkflow(currentContext)` | Security → Menu → Auth 전체 워크플로우 안내 |

---

## 전체 워크플로우 (신규 도메인 개발 시)

```
Step 1. initializeProject()
        → eGovFrame 프로젝트 골격 생성 + PROJECT_CONTEXT 블록 획득

Step 2. getSecurityTemplate(setup-all-war-50, ...)
        → Spring Security 설정 파일 일괄 생성

Step 3. getTableList() → getTableSchema() → getTableRelations()
        → 테이블 구조 파악 + 관계 탐지

Step 4. (viewType=thymeleaf인 경우) generateThymeleafLayout(outputPath, packageName=...)
        → Thymeleaf 공통 layout 5종 + GNB 동적 메뉴 컴포넌트 4종 생성 (최초 1회, layoutMode=reuse 기본값 화면 생성 전 선행)

Step 5. buildFullCrudPrompt(llmProvider=auto)  — CrudGenerationTool
        → 화면/Java/Mapper 소스 자동 생성 + 저장 + 검증
        → Thymeleaf 화면은 styles.css의 egov-* 공통 클래스를 사용하고 인라인 style을 생성하지 않음

Step 6. getMenuStructure() → generateMenuInsertSql()
        → 메뉴 등록 SQL 생성

Step 7. getProgramList() → generateAuthInsertSql()
        → URL 접근권한 SQL 생성

Step 8. 서버 재기동 → 메뉴 노출 + 접근 제어 확인
```
