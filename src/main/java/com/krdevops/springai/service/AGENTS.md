<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-04 | Updated: 2026-06-04 -->

# service

## Purpose
MCP Tool 비즈니스 로직 서비스 레이어. 21개 서비스 클래스가 코드 생성, 스키마 조회,
보안 템플릿, 프로젝트 초기화, SQL 생성, RAG 등 MCP Tool의 핵심 로직을 담당합니다.

## Key Files

| File | Description |
|------|-------------|
| `SecurityTemplateService.java` | eGovFrame 보안 설정 XML 템플릿 생성 |
| `ProjectInitializrService.java` | eGovFrame 프로젝트 초기화 — 디렉토리/파일 구조 생성 |
| `ContextAssembler.java` | LLM 프롬프트용 컨텍스트 조합 — RAG 결과 + 스키마 + 히스토리 |
| `SchemaService.java` | DB 테이블 스키마/컬럼 정보 조회 |
| `CrudPromptBuilderService.java` | eGovFrame CRUD 소스 생성용 프롬프트 빌드 |
| `CodeService.java` | 생성된 코드 저장/관리 |
| `CodeValidatorService.java` | 생성 코드 유효성 검사 |
| `SqlService.java` | SQL 생성 및 실행 지원 |
| `RagService.java` | VectorStore 기반 RAG 문서 검색 |
| `LlmRouterService.java` | LLM 라우팅 — 요청 유형에 따라 적절한 모델 선택 |
| `ProjectScannerService.java` | 기존 프로젝트 구조 스캔 및 분석 |
| `ProjectHealthService.java` | 프로젝트 상태/건강도 검사 |
| `OutputPathResolverService.java` | 생성 파일 출력 경로 결정 로직 |
| `EgovPromptBuilder.java` | eGovFrame 표준 프롬프트 템플릿 빌더 |
| `WorkflowGuideService.java` | eGovFrame 개발 워크플로 가이드 제공 |
| `AuthService.java` | 인증/인가 관련 서비스 |
| `ChunkService.java` | 문서 청킹(chunking) 처리 |
| `CommonCodeService.java` | 공통 코드 조회/관리 |
| `EmployeeService.java` | 직원 정보 비즈니스 로직 |
| `GenerationHistoryService.java` | 코드 생성 이력 관리 |
| `MenuService.java` | 메뉴 구조 조회/관리 |
| `MasterDetailService.java` | 마스터-디테일 관계 처리 |
| `TableRelationService.java` | 테이블 연관관계 분석 |

## For AI Agents

### Working In This Directory
- 서비스 클래스는 단일 책임 원칙 준수 — 하나의 도메인/기능만 담당
- 비즈니스 로직은 이 레이어에만 작성, Tool 클래스는 위임만 수행
- `@Service` 어노테이션 + `@RequiredArgsConstructor` 패턴 사용

### Common Patterns
```java
@Service
@RequiredArgsConstructor
public class MyService {
    private final MyRepository myRepository;
    // 비즈니스 로직
}
```

### Testing Requirements
- 서비스 단위 테스트 시 Repository 목(Mock) 처리
- DB 의존 서비스는 `docker start egov-mysql` 후 통합 테스트

## Dependencies

### Internal
- `mapper/` — DB 접근 Repository
- `chat/service/` — 채팅 관련 서비스 (일부 ContextAssembler에서 참조)

### External
- Spring AI VectorStore
- JdbcTemplate
- Ollama API

<!-- MANUAL: -->
