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
- **DB:** `com` / **User:** `com` / **Password:** `com01`
- **eGovFrame 주요 테이블:** `COMTNEMPLYRINFO`(직원), `COMTNBBSMASTER`(게시판) 등 다수
- **DB 접근:** `JdbcTemplate` (MyBatis는 Spring Boot 4.x 미지원)

egov-mysql 시작:
```bash
docker start egov-mysql
```

---

## 현재 등록된 MCP Tool (20개)

### CRUD 자동 생성
| Tool 클래스 | 주요 메서드 | 기능 |
|---|---|---|
| `SchemaReaderTool` | `getTableSchema`, `getTableList` | MySQL 테이블 컬럼·PK·타입 조회 |
| `CrudPromptBuilderTool` | `buildCrudPrompt` | DB 스키마 → eGovFrame CRUD 프롬프트 조립 |
| `CodeTemplateTool` | `renderCrudTemplate` | FreeMarker로 Controller/Service/Mapper/VO/JSP 렌더링 |
| `CodeSaverTool` | `saveCode` | 생성된 소스를 지정 경로에 파일 저장 |
| `CodeValidatorTool` | `validateCode` | 생성 코드 구문·규칙 검증 |

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
