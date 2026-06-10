<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# service

## Purpose
비즈니스 로직 서비스 레이어 패키지. MCP Tool과 REST Controller가 위임하는 핵심 로직이 여기에 위치합니다.
23개 서비스 클래스가 eGovFrame CRUD 생성, RAG, LLM 라우팅, 보안 템플릿, SQL 실행 등을 담당합니다.

## Key Files

| File | Description |
|------|-------------|
| `LlmRouterService.java` | LLM 라우팅 — taskType/모델명 기준 OpenAI·Ollama `ChatClient` 선택 및 호출 |
| `RagService.java` | RAG 핵심 — 텍스트/URL/Java 디렉터리 임베딩, 유사 검색, 컨텍스트 빌드 (SSRF 방지 포함) |
| `ChunkService.java` | 긴 텍스트를 청크로 분할 — Vector Store 저장 전처리 |
| `SchemaService.java` | DB 테이블 목록·스키마 조회 (JdbcTemplate → `information_schema`) |
| `CodeService.java` | 생성된 소스 코드를 파일로 저장 |
| `EmployeeService.java` | 직원 CRUD 비즈니스 로직 |
| `EgovPromptBuilder.java` | eGovFrame 5.x CRUD 소스 생성용 프롬프트 조립 |
| `ContextAssembler.java` | 스키마 + RAG 컨텍스트 + 생성 이력을 하나의 프롬프트 컨텍스트로 조합 |
| `CrudPromptBuilderService.java` | CRUD 전체 프롬프트 빌드 — 단일/마스터-디테일/조인 SELECT 패턴 지원 |
| `MasterDetailService.java` | 마스터-디테일 관계 테이블 CRUD 프롬프트 생성 |
| `SecurityTemplateService.java` | eGovFrame 보안 설정 템플릿(Spring Security XML, SecurityConfig) 생성 |
| `AuthService.java` | 인증 관련 SQL INSERT 생성 (권한, 메뉴 접근 설정) |
| `MenuService.java` | eGovFrame 메뉴 구조 조회 및 INSERT SQL 생성 |
| `CommonCodeService.java` | eGovFrame 공통코드 조회·검색 |
| `SqlService.java` | 임의 SQL 실행 및 쿼리 설명 (AI 기반) |
| `TableRelationService.java` | FK 기반 테이블 관계 분석 |
| `ProjectScannerService.java` | 기존 프로젝트 구조 스캔 — 패키지·파일 목록 추출 |
| `ProjectHealthService.java` | 프로젝트 건강 상태 점검 — 설정 유효성, DB 연결 확인 |
| `ProjectInitializrService.java` | 신규 eGovFrame 프로젝트 디렉터리 구조 초기화 |
| `GenerationHistoryService.java` | 코드 생성 이력 저장·조회 |
| `OutputPathResolverService.java` | 생성 코드 출력 경로 결정 (`EGOV_OUTPUT_PATH` 환경변수 기준) |
| `CodeValidatorService.java` | 생성된 Java/XML 코드 유효성 검사 |
| `WorkflowGuideService.java` | eGovFrame CRUD 생성 워크플로우 단계별 안내 |

## For AI Agents

### Working In This Directory
- Tool 클래스는 서비스를 단순 위임(delegate)하는 얇은 래퍼 — 비즈니스 로직은 이 패키지에 작성
- `LlmRouterService.route(modelOrTaskType)` — 모델명(`gpt-*`, `o1*`, `o3*`) 또는 taskType으로 라우팅
- `CODE_GENERATION` taskType은 `LlmRouterService.chat()`에서 예외 발생 → MCP SSE 사용 권고
- SSRF 방지: `RagService.fetchHtml()`은 루프백·사설 IP 차단

### Common Patterns
```java
// LLM 라우팅 패턴
ChatClient client = llmRouterService.route("qwen3:8b");   // Ollama
ChatClient client = llmRouterService.route("gpt-4o-mini"); // OpenAI
String answer = llmRouterService.chat("SIMPLE_QUERY", prompt);
```

## Dependencies

### Internal
- `mapper/` — DB 접근 (EmployeeRepository, GenerationHistoryRepository)
- `config/AppProperties.java` — 문서 경로, API 키

### External
- Spring AI `VectorStore`, `ChatClient`
- Spring JDBC `JdbcTemplate`
- Redis `RedisTemplate`

<!-- MANUAL: -->
