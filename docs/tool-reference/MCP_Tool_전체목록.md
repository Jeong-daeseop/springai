# MCP Tool 전체 목록

총 **19개 Tool 파일 / 44개 메서드**

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

## 2. CRUD 소스 생성

### CrudPromptBuilderTool (3개)
| 메서드 | 설명 |
|--------|------|
| `buildFullCrudPrompt(database, tableName, domain, packageName, outputPath, llmProvider, egovVersion)` | 11개 레이어 CRUD 전체 소스 생성 (claude/auto 모드) |
| `buildMasterDetailPrompt(database, masterTable, detailTable, domain, packageName, outputPath)` | 1:N 마스터-디테일 CRUD 생성 |
| `buildJoinSelectPrompt(database, tableName)` | JOIN SELECT 쿼리 + resultMap + VO 추가 필드 생성 |

### CodeTemplateTool (1개)
| 메서드 | 설명 |
|--------|------|
| `getCodeTemplate(layerKey, placeholders)` | 레이어별 템플릿 단독 반환 (vo/mapper/service/controller/jsp 등) |

### CodeSaverTool (3개)
| 메서드 | 설명 |
|--------|------|
| `generateSource(layerKey, placeholders)` | 템플릿 + 플레이스홀더 치환 → 소스 반환 |
| `saveGeneratedCode(filePath, code)` | 파일 저장 (Path Traversal 차단) |
| `getDefaultOutputPath(domain)` | 기본 출력 경로 반환 (`~/Desktop/egov-generated/{domain}`) |

### CodeValidatorTool (2개)
| 메서드 | 설명 |
|--------|------|
| `validateGeneratedCode(filePath)` | 단일 파일 검증 (import, annotation, syntax 체크) |
| `validateGeneratedCodeDirectory(dirPath)` | 디렉터리 내 전체 파일 일괄 검증 |

---

## 3. 프로젝트 초기화

### ProjectInitializrTool (2개)
| 메서드 | 설명 |
|--------|------|
| `initializeProject(projectName, groupId, artifactId, packageName, buildTool, projectType, egovVersion, outputPath)` | eGovFrame 4.3/5.0 WAR/Boot 프로젝트 골격 생성 |
| `getConfigTemplate(configType, packageName, egovVersion, projectType)` | 설정 파일 단독 반환 (contextCommon/applicationYml 등) |

### ProjectScannerTool (1개)
| 메서드 | 설명 |
|--------|------|
| `scanProjectStructure(projectPath)` | 기존 프로젝트 구조 스캔 → PROJECT_CONTEXT 블록 생성 |

### ProjectHealthTool (1개)
| 메서드 | 설명 |
|--------|------|
| `checkProjectHealth(projectPath)` | 프로젝트 구조 / 설정 파일 / 빌드 파일 이상 탐지 |

### OutputPathResolverTool (2개)
| 메서드 | 설명 |
|--------|------|
| `resolveProjectOutputPath(projectContext, domain)` | PROJECT_CONTEXT 기반 outputPath 자동 계산 |
| `checkOutputDirectory(outputPath)` | 경로 존재 여부 + 쓰기 권한 확인 |

---

## 4. 보안 / 메뉴 / 권한

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

## 5. 공통코드 / 직원

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

## 6. RAG / 문서

### RagTool (5개)
| 메서드 | 설명 |
|--------|------|
| `ragSearch(query, topK)` | 벡터 저장소 유사도 검색 |
| `ragIngest(content, metadata)` | 텍스트 직접 인덱싱 |
| `ragIngestUrl(url)` | URL 콘텐츠 크롤링 + 인덱싱 |
| `ragIngestUrls(urls)` | 복수 URL 병렬 인덱싱 (Semaphore 제어) |
| `ragIngestDirectory(dirPath, extension)` | 로컬 디렉터리 파일 일괄 인덱싱 |

---

## 7. 유틸리티 / 워크플로우

### DateTimeTool (2개)
| 메서드 | 설명 |
|--------|------|
| `getCurrentDateTime(timezone)` | IANA 시간대 기준 현재 시각 반환 |
| `celsiusToFahrenheit(celsius)` | 섭씨 → 화씨 변환 |

### GenerationHistoryTool (2개)
| 메서드 | 설명 |
|--------|------|
| `getGenerationHistory(domain, limit)` | 소스 생성 이력 조회 |
| `saveGenerationHistory(domain, tableName, outputPath, fileCount)` | 생성 이력 DB 저장 |

### WorkflowGuideTool (2개)
| 메서드 | 설명 |
|--------|------|
| `suggestNextStep(currentStep, projectContext)` | 현재 단계 기반 다음 단계 가이드 |
| `suggestSecurityMenuAuthWorkflow(projectContext)` | Security → Menu → Auth 전체 워크플로우 안내 |

---

## 전체 워크플로우 (신규 도메인 개발 시)

```
Step 1. initializeProject()
        → eGovFrame 프로젝트 골격 생성 + PROJECT_CONTEXT 블록 획득

Step 2. getSecurityTemplate(setup-all-war-50, ...)
        → Spring Security 설정 파일 일괄 생성

Step 3. getTableList() → getTableSchema() → getTableRelations()
        → 테이블 구조 파악 + 관계 탐지

Step 4. buildFullCrudPrompt(llmProvider=auto)
        → 11개 레이어 소스 자동 생성 + 저장 + 검증

Step 5. getMenuStructure() → generateMenuInsertSql()
        → 메뉴 등록 SQL 생성

Step 6. getProgramList() → generateAuthInsertSql()
        → URL 접근권한 SQL 생성

Step 7. 서버 재기동 → 메뉴 노출 + 접근 제어 확인
```
