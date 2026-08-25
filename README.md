# SpringAI eGovFrame MCP 서버

Spring Boot와 Spring AI로 구현한 eGovFrame 개발 지원 서버입니다. Claude Desktop/Web 또는 다른 MCP 클라이언트가 Streamable HTTP로 연결하여 eGovFrame 코드 생성, Thymeleaf 화면 생성·검증, Figma 디자인 연동, RAG 문서 검색 기능을 사용할 수 있습니다.

현재 MCP 계약 기준은 **101개 Tool 메서드 / 36개 Tool 객체**입니다(`McpToolDefinitionSnapshotTest` 회귀 기준선). 모든 Tool에는 위험 등급이 지정되어 있으며 MCP 인증은 기본적으로 `REQUIRED`(deny-by-default)입니다.

## 주요 기능

- eGovFrame CRUD·Board·Master-detail 소스 생성
- JSP/화면 분석과 Thymeleaf Skeleton·Responsive 변환
- Figma Screen Specification·Design System·Export Bundle 처리
- 승인 기반 Thymeleaf Project Preview·Apply·Rollback
- 실제 Artifact 기반 디자인 parity 검증
- Ollama·OpenAI 기반 채팅 및 Redis VectorStore RAG 검색
- MySQL 기반 eGovFrame 스키마·공통코드·메뉴 조회
- REST와 MCP 간 Preview/Approve/Apply workflow 연계

## 시스템 구성

```text
MCP/Web Client
      │ Streamable HTTP (/mcp)
      ▼
Spring Boot MCP Adapter
  ├─ X-MCP-Token transport 인증
  ├─ Tool 위험 등급 인가
  ├─ REST Controller
  └─ Chat/SSE UI
      │
      ├─ Application Services / Use Cases
      ├─ Figma·Thymeleaf·Generation Services
      ├─ MySQL Repository (JdbcTemplate)
      ├─ Redis VectorStore / Chat Memory
      ├─ Ollama·OpenAI
      └─ Project·Artifact 저장소
```

## 기술 스택

| 항목 | 버전/구성 |
|---|---|
| Java | 17 toolchain 이상, 개발 권장 21 |
| Spring Boot | 4.1.0-RC1 |
| Spring AI | 2.0.0-RC1 |
| MCP | Streamable HTTP, `/mcp` |
| 데이터베이스 | MySQL 8 (`egov-mysql`) |
| RAG/채팅 메모리 | Redis Stack |
| 로컬 LLM | Ollama (`qwen3:8b`, `qwen3:1.7b`) |
| 임베딩 | ONNX Transformers (`ko-sroberta-multitask`) |

## 빠른 시작

### 사전 요구사항

- JDK 17 이상
- Docker 및 MySQL `egov-mysql` 컨테이너
- Redis Stack
- Ollama와 필요한 모델
- ONNX 임베딩 모델 파일(`model.onnx`, `tokenizer.json`)

### 의존 서비스

```bash
docker start egov-mysql
ollama serve
ollama pull qwen3:8b
ollama pull qwen3:1.7b
```

Redis는 기본적으로 `redis://localhost:6379`를 사용합니다.

### 환경변수

```bash
cp .env.example .env
```

Spring Boot는 `.env` 파일을 자동으로 읽지 않으므로 IDE Run Configuration 또는 셸에 변수를 주입해야 합니다.

```bash
export OPENAI_API_KEY=...
export MCP_SHARED_TOKEN='충분히 긴 무작위 토큰'
export MCP_AUTH_MODE=REQUIRED
export ONNX_MODEL_PATH=/path/to/model.onnx
export ONNX_TOKENIZER_PATH=/path/to/tokenizer.json
```

| 변수 | 기본값 | 설명 |
|---|---|---|
| `MCP_SHARED_TOKEN` | 없음 | MCP 공통 인증 token |
| `MCP_AUTH_MODE` | `REQUIRED` | `AUDIT_ONLY`, `COMPATIBILITY`, `REQUIRED` |
| `MCP_PREVIOUS_SHARED_TOKEN` | 없음 | token 회전 유예용 이전 token |
| `MCP_PREVIOUS_TOKEN_VALID_UNTIL` | 없음 | 이전 token 만료 시각(UTC) |
| `SERVER_ADDRESS` | `127.0.0.1` | 비-loopback이면 token과 `REQUIRED` 필수 |
| `DB_USERNAME`/`DB_PASSWORD` | `ebt`/`ebt01` | MySQL 접속 정보 |
| `REDIS_URI` | `redis://localhost:6379` | Redis 접속 URI |
| `EGOV_OUTPUT_PATH` | 기본 경로 | 생성 파일 출력 경로 |
| `FIGMA_ACCESS_TOKEN` | 없음 | Figma REST API token |

### 실행

```bash
./gradlew bootRun
```

기본 주소는 `http://localhost:8080`, Streamable HTTP MCP endpoint는 `http://localhost:8080/mcp`입니다.

운영·장기 실행은 Gradle과 Java 자식 프로세스를 함께 추적하는 래퍼를 사용합니다.

```bash
./scripts/springai-server.sh start
./scripts/springai-server.sh status
./scripts/springai-server.sh stop
```

운영 기본 포트는 8080입니다. 8082·8083은 테스트 격리 실행에서만 `SERVER_PORT=8082`처럼 명시적으로 사용하고, 운영 프로세스와 섞지 않습니다.

MCP 클라이언트는 모든 MCP 요청에 다음 헤더를 보내야 합니다.

```http
X-MCP-Token: <MCP_SHARED_TOKEN>
```

프로젝트 MCP 클라이언트 별칭은 `springai-mcp`로 통일합니다. 다른 설정에 `egovframe-mcp`가 남아 있으면 동일한 `/mcp` 서버가 중복 표시될 수 있으므로 제거하십시오.

Claude Desktop 설정은 사용 중인 `mcp-remote` 버전의 custom header 전달 방식을 확인해 적용하십시오. token이 없으면 `MCP_TOKEN_MISSING` 또는 `MCP_AUTH_REQUIRED`로 거부됩니다.

## REST와 MCP 보안

- `/api/**`: `X-API-Key` 인증
- `/mcp`, `/mcp/**`, `/sse`, `/sse/**`: 공통 MCP token 인증 및 Tool 위험 등급 인가
- 기본 MCP 모드: `REQUIRED`
- 비-loopback 바인딩: token과 `REQUIRED` 없이는 기동 차단
- 인증 실패 전 Tool delegate가 호출되지 않음
- token·secret·API key·fileKey는 응답과 감사 로그에서 마스킹

자세한 절차는 [WP1 MCP 보안 Runbook](docs/architecture/security/ARCH-WP1-MCP-보안-운영-Runbook.md)을 참고하십시오.

## CRUD 생성 승인 정책(고위험 테이블)

CRUD 자동 생성(`llmProvider=auto`)은 기본적으로 사람의 승인 없이 즉시 파일을 저장합니다. 특정
테이블만 승인된 화면명세(`designReferenceId`/`screenSpecificationId`) 없이는 생성을 차단하고
싶다면 `application.yaml`에 테이블명을 추가하십시오.

```yaml
app:
  crud-generation:
    approval-required-tables:
      - LETTNEMPLYRINFO   # 여기 등록된 테이블은 승인된 화면명세 없이는 auto 생성이 차단됩니다
    approval-required-for-all: false   # true면 viewType·목록과 무관하게 전체 테이블에 강제
```

기본값(빈 목록·`false`)에서는 기존 동작과 완전히 동일합니다. 차단된 시도는 다른 실패와 동일하게
`AI_GENERATION_OPERATION_AUDIT`에 `failureStage=approval-policy`로 남고, `/api/generation-operations/**`
(14.1절)로 조회할 수 있습니다. 배경과 대안 비교는
[CRUD 명시적 승인 단계 도입 타당성 검토](docs/architecture/CRUD_명시적_승인_단계_도입_타당성_검토.md)를
참고하십시오.

## 개발 및 검증

```bash
./gradlew compileJava
./gradlew test
./gradlew test --tests 'com.krdevops.springai.config.mcp.*' --tests 'com.krdevops.springai.config.McpToolDefinitionSnapshotTest'
./gradlew bootJar
./gradlew check
git diff --check
```

CI에서 로컬 ONNX·DB·Redis 의존성을 제외하려면 다음을 사용합니다.

```bash
./gradlew test -Pci
```

## 기준선과 문서

- [통합 사용자 가이드](docs/사용자_가이드.md)
- [권장 목표 아키텍처 구현목록](docs/architecture/SpringAI_권장_목표_아키텍처_구현목록_2026-08-03.md)
- [권장 목표 아키텍처 구현계획서](docs/architecture/SpringAI_권장_목표_아키텍처_구현계획서_2026-08-03.md)
- [WP0 계약·테스트 기준선](docs/architecture/baseline/ARCH-WP0-baseline-2026-08-03.md)
- [WP1 MCP 보안 Runbook](docs/architecture/security/ARCH-WP1-MCP-보안-운영-Runbook.md)
- [전체 아키텍처 재분석](docs/architecture/SpringAI_프로젝트_전체_아키텍처_재분석_2026-08-03.md)
- [5축 파이프라인 Release Gate 운영 Runbook](docs/figma/31_5Axis_Pipeline_Release_Gate_Operations_Runbook.md)
- [5축 벤치마크 기반 구현목록](docs/figma/30_5Axis_Benchmark_Based_Pipeline_Evolution_Implementation_List.md)
- [CRUD 명시적 승인 단계 구현 명세서](docs/architecture/CRUD_명시적_승인_단계_구현_명세서.md) /
  [구현목록](docs/architecture/CRUD_명시적_승인_단계_구현목록.md)

운영 DB·Redis 연결을 실제로 확인하려면 `./scripts/pipeline-live-smoke.sh`를 실행합니다.

기준선 커밋은 `a09ccd2`(WP0), `83f90f5`(WP1), `b2409d2`(문서)입니다.

## 디렉터리 구조

Java 소스는 `src/main/java/com/krdevops/springai` 아래 775개 파일로 구성됩니다.

```text
src/main/java/com/krdevops/springai
├── config/         # Spring Security, 애플리케이션 설정
│   ├── mcp/        # MCP Tool 위험 등급·인가·민감정보 마스킹(McpAuthorizingToolCallback 등)
│   └── observability/
├── controller/     # REST API
├── chat/           # 채팅 UI, RAG, SSE
│   ├── config/rag/transformers/  # ONNX 임베딩 설정
│   ├── context/ controller/ dto/ repository/ response/ service/ util/
├── exception/      # 전역 예외 처리
├── mapper/         # JdbcTemplate Repository
├── model/          # 요청·결과·계약 모델(9개 최상위 + 하위 패키지)
│   ├── artifact/ board/ capture/ contract/ crud/ masterdetail/
│   ├── design/role/ designsystem/ operation/ parity/ thymeleaf/ write/
│   └── figma/      # contract/ hybrid/ ops/ refinement/ request/
├── policy/         # 입력·보안 정책
├── service/        # Application Service와 도메인 처리(86개 최상위 + 하위 패키지)
│   ├── artifact/ auth/ common/ contract/ designsystem/ menu/
│   ├── observability/ operation/ parity/ resilience/ sql/ thymeleaf/ workflow/ write/
│   ├── figma/builder/
│   ├── initializr/template/
│   ├── security/template/
│   └── generation/ # api/ board/ crud/ layout/ masterdetail/ mcp/ model/ pipeline/processor/ source/
├── tools/          # MCP Tool 구현(McpConfig에 등록)
│   └── generation/ # Board/Crud/MasterDetail Generation·ScreenSource·JoinQuery Tool
├── util/
└── vo/

src/main/resources/
├── db/migration/   # Flyway
├── model/          # ONNX 임베딩 모델 배치 경로
├── static/js/
└── templates/      # Thymeleaf (board/ crud/ egov/ legacy-thymeleaf/ masterdetail/ security/)

docs/               # 아키텍처·구현계획·Figma 통합·운영 문서
templates/          # eGovFrame FreeMarker 생성 템플릿(boot-thymeleaf/, war-thymeleaf/)
prompts/            # Tool 프롬프트 템플릿(crud/menu/security/system-prompt 등)
scripts/            # springai-server.sh 등 운영 스크립트
```

### Figma 연동 서브 프로젝트 (독립 Node.js 프로젝트)

`DesignReferenceTool`/`FigmaDesignOrchestrationTool` 기반 Figma↔Thymeleaf 파이프라인은 Spring Boot 단일 앱이 아니라, 계약(JSON Schema)으로 연결된 별도 Node.js 프로젝트 여러 개로 구성됩니다. 각 프로젝트의 `README.md`에 상세 사용법이 있습니다.

| 디렉터리 | 역할 |
|---|---|
| `website-figma-contract/` | Website→Figma 파이프라인이 공유하는 기술 중립 계약(JSON Schema, `component-catalog-v1/v2.json`). `figmaContractTest` Gradle task로 검증 |
| `figma-screen-spec-plugin/` | Spring이 내려준 `.figma-export-bundle.json`을 Published FTC/KRDS Component Instance 기반 Figma 화면으로 생성·동기화하는 Figma Plugin. `figmaRuntimeBundlePluginTest`/`figmaRefinementPluginTest`로 검증 |
| `jsp-design-extractor/` | Playwright/Chromium으로 로컬 화면(JSP/Thymeleaf)을 캡처해 `figpack-v1`을 생성. `browserGateTest`(Playwright 1440/768/390, axe, visual diff)로 검증 |
| `jsp-to-figma-plugin/` | `figpack-v1`을 검증하고 현재 Figma 파일에 Frame/Text 구조를 생성하는 로컬 전용 Plugin |
| `krds-design-system-author-plugin/` | `design-system-spec-v1` JSON으로 Figma Variable Collection·Component Set·Variant를 제자리 생성/갱신하는 로컬 전용 Plugin |
| `component-contracts/` | Primitive/Semantic 디자인 토큰 정의(`tokens/primitive/`, `tokens/semantic/`) |
| `figma-capture/` | 캡처된 참조 화면 스크린샷 자산 |

이 서브 프로젝트들은 `build.gradle`의 `check` 태스크(`figmaContractTest`, `qnaRuntimeResolverTest`, `figmaRuntimeBundlePluginTest`, `figmaRefinementPluginTest`)와 별도 등록 task(`browserGateTest`)로 Java 빌드 파이프라인에 연결됩니다.

## 로그

```bash
tail -f /tmp/springai-mcp.log
tail -f ~/Library/Logs/Claude/mcp-server-springai-mcp.log
```

로그에 인증 token이나 API secret을 출력하지 마십시오.
