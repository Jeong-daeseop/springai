# eGovFrame 생성형 AI MCP 서버

Spring Boot 4.1.0-RC1 + Spring AI 2.0.0-RC1 기반 **Model Context Protocol(MCP) 서버**.  
Claude Desktop/Web과 Streamable HTTP 트랜스포트로 연결되어 eGovFrame 4.3/5.0 표준 소스 자동 생성, RAG 기반 문서 검색, 보안 설정 자동화를 제공합니다.

---

## 아키텍처

```
사용자
  │ 대화
  ▼
Claude Desktop
  │ JSON-RPC over Streamable HTTP
  ▼
이 Spring Boot MCP 서버  (Servlet HTTP 서버 — /mcp)
  │
  ├── @Tool 메서드 44개 자동 라우팅
  ├── MCP Resources/Prompts 제공
  ├── Ollama (로컬 LLM)
  ├── Redis VectorStore (RAG)
  └── MySQL (eGovFrame 표준 테이블)
```

---

## 기술 스택

| 구분 | 내용 |
|------|------|
| 프레임워크 | Spring Boot 4.1.0-RC1 + Spring AI 2.0.0-RC1 |
| MCP 트랜스포트 | Streamable HTTP (`/mcp`, `http-only`) |
| LLM | Ollama (로컬 실행, 인터넷 불필요) |
| 벡터 DB | Spring AI VectorStore + Redis |
| 세션 메모리 | Redis (채팅 이력 영속화) |
| DB | MySQL 8.0 — Docker `egov-mysql` |
| Java | 21+ |

---

## 주요 기능

### 1. eGovFrame CRUD 소스 자동 생성
DB 테이블명을 말하면 eGovFrame 5.0 표준 6개 레이어를 자동 생성합니다.

```
테이블명 입력
  → SchemaReaderTool (컬럼/PK/타입 조회)
  → CrudPromptBuilderTool (프롬프트 구성)
  → Claude가 소스 생성
  → CodeSaverTool (파일 저장)
```

**생성 대상 레이어:**

| 레이어 | 패키지 |
|--------|--------|
| Controller | `egovframework.let.{domain}.web` |
| Service (interface) | `egovframework.let.{domain}.service` |
| ServiceImpl | `egovframework.let.{domain}.service.impl` |
| Mapper (@Mapper) | `egovframework.let.{domain}.service.impl` |
| VO | `egovframework.let.{domain}.vo` |
| MyBatis XML | `resources/egovframework/mapper/{domain}` |

### 2. RAG 기반 eGovFrame 문서 검색 채팅
eGovFrame 관련 문서를 벡터 DB에 임베딩해두고 질의 시 관련 문서를 검색해 답변합니다.

- 세션별 대화 이력 유지 (Redis, 멀티턴)
- Ollama 로컬 LLM 스트리밍 응답
- `chat.html` 웹 UI 제공
- `<think>` 태그 파싱으로 추론 과정 분리

### 3. 프로젝트 초기화 / 보안 설정 자동화
- **ProjectInitializrTool** — eGovFrame 표준 폴더 구조 자동 생성
- **SecurityTemplateTool** — Spring Security XML 설정 자동 생성
- **WorkflowGuideTool** — eGovFrame 개발 순서 가이드

---

## MCP Tool 목록 (19개 클래스 / 44개 메서드)

| 분류 | Tool | 설명 |
|------|------|------|
| **스키마/DB** | `SchemaReaderTool` | 테이블 컬럼/PK/타입 정보 조회 |
| | `SqlTool` | SQL 생성 및 실행 지원 |
| **코드 생성** | `CrudPromptBuilderTool` | 테이블 기반 CRUD 프롬프트 빌드 |
| | `CodeTemplateTool` | eGovFrame 레이어별 코드 템플릿 |
| | `CodeSaverTool` | 생성 소스 파일 저장 |
| | `CodeValidatorTool` | 생성 코드 eGovFrame 표준 준수 검증 |
| **프로젝트** | `ProjectInitializrTool` | eGovFrame 프로젝트 구조 생성 |
| | `ProjectScannerTool` | 기존 프로젝트 구조 스캔 |
| | `ProjectHealthTool` | 도메인 완성도 점검 |
| | `OutputPathResolverTool` | 생성 파일 출력 경로 결정 |
| **보안** | `SecurityTemplateTool` | Spring Security 설정 자동 생성 |
| | `AuthTool` | 인증/인가 도구 |
| **RAG/문서** | `RagTool` | VectorStore 기반 문서 검색 |
| **워크플로** | `WorkflowGuideTool` | eGovFrame 개발 워크플로 가이드 |
| **공통** | `EmployeeTool` | 직원 정보 CRUD |
| | `CommonCodeTool` | 공통 코드 조회 |
| | `MenuTool` | 메뉴 구조 조회 |
| | `GenerationHistoryTool` | 코드 생성 이력 조회 |
| | `DateTimeTool` | 현재 시각 조회 |

---

## 빠른 시작

### 사전 요구사항

- Java 21+
- Docker (`egov-mysql` 컨테이너)
- Ollama (`ollama serve` 실행 중)
- Claude Desktop

### 1. Docker 컨테이너 시작

```bash
docker start egov-mysql
```

DB 정보: `ebt` / `ebt` / `ebt01` (db / user / password)

### 2. Ollama 실행

```bash
ollama serve
ollama pull <모델명>   # 예: ollama pull mistral
```

### 3. 환경변수 설정

#### 필수 환경변수

| 변수명 | 설명 | 예시 |
|--------|------|------|
| `APP_API_KEY` | `/api/tools/**` 엔드포인트 인증 키 | `my-secret-key-123` |
| `OPENAI_API_KEY` | OpenAI API 키 | `sk-proj-...` |

> **주의:** 두 환경변수 모두 미설정 시 애플리케이션 기동이 실패합니다.

#### 설정 방법

**방법 1 — 셸 환경변수**

```bash
export APP_API_KEY=my-secret-key-123
export OPENAI_API_KEY=sk-proj-...
```

**방법 2 — `.env` 파일 (로컬 개발 권장)**

```bash
# 프로젝트 루트에 .env 파일 생성 (.gitignore에 포함되어 있음)
cp .env.example .env
# .env 파일을 열어 값 입력
```

`.env.example`:
```
APP_API_KEY=local-dev-key
OPENAI_API_KEY=sk-proj-여기에_실제_키_입력
OLLAMA_BASE_URL=http://localhost:11434
REDIS_URI=redis://localhost:6379
EGOV_OUTPUT_PATH=/Users/yourname/Desktop/egov-generated
```

**방법 3 — IDE Run Configuration**

IntelliJ IDEA: `Run > Edit Configurations > Environment variables`에 추가

#### 선택 환경변수

| 변수명 | 기본값 | 설명 |
|--------|--------|------|
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama 서버 주소 |
| `OLLAMA_MODEL` | `qwen3:8b` | 기본 Ollama 모델 |
| `REDIS_URI` | `redis://localhost:6379` | Redis 연결 URI |
| `EGOV_OUTPUT_PATH` | `~/Desktop/egov-generated` | 소스 생성 기본 경로 |
| `ONNX_MODEL_PATH` | `~/models/ko-sroberta/model.onnx` | 임베딩 모델 경로 |

---

### 4. 빌드

```bash
./gradlew bootJar
# 결과: build/libs/springai-0.0.1-SNAPSHOT.jar
```

### 5. Claude Desktop 연동

MCP 서버 애플리케이션을 먼저 실행합니다.

```bash
./gradlew bootRun
```

`~/Library/Application Support/Claude/claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "springai-mcp": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-remote@0.1.38",
        "http://localhost:8080/mcp",
        "--allow-http",
        "--transport",
        "http-only"
      ]
    }
  }
}
```

애플리케이션을 먼저 실행한 뒤 Claude Desktop을 재시작하면 MCP 도구가 자동 연결됩니다.

---

## 개발 명령

```bash
# 전체 빌드 + 테스트
./gradlew build

# 테스트만
./gradlew test

# 실행 가능한 JAR 빌드
./gradlew bootJar

# 정리
./gradlew clean
```

---

## 로그 확인

```bash
# 애플리케이션 로그
tail -f /tmp/springai-mcp.log

# Claude Desktop MCP 연결 로그
tail -f ~/Library/Logs/Claude/mcp-server-springai-mcp.log
```

---

## 패키지 구조

```
com.krdevops.springai
├── config/          # 글로벌 설정 (McpConfig, VectorStoreConfig, ...)
├── controller/      # HTTP API (RagController, ToolApiController)
├── mapper/          # JdbcTemplate Repository
├── service/         # 비즈니스 로직 서비스 21종
├── tools/           # MCP Tool 구현체 19개 클래스 / 44개 메서드
├── vo/              # Value Object
└── chat/            # 채팅 서브 도메인
    ├── config/      # 채팅 설정 (RAG, Redis, ChatMemory)
    ├── controller/  # 채팅 컨트롤러 5종
    ├── service/     # 채팅 서비스 인터페이스 + 구현체
    ├── repository/  # Redis 채팅 메모리
    └── util/        # 응답 정제, 프롬프트 템플릿
```
