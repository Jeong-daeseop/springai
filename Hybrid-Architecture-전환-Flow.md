# Hybrid Architecture 전환 Flow

작성일: 2026-05-15
프로젝트: springai-mcp (Spring Boot MCP Gateway → Hybrid Architecture)

---

## 전체 단계 Overview

```
Phase 1          Phase 2          Phase 3          Phase 4
─────────        ─────────        ─────────        ─────────
Service          transport        OpenAI           Local LLM
레이어 분리  →   stdio→SSE    →   연동          →  연동
(기반 작업)      (HTTP 전환)      (추가)           (추가)

기존 동작        Claude           Claude           Claude
유지             계속 사용        + OpenAI         + OpenAI
                                                   + Local LLM
```

---

## Phase 1 — Service 레이어 분리 (기반 작업)

### 목적
Tool 로직을 Service로 분리해 재사용 기반 마련

### 변경 전/후 구조

```
변경 전                          변경 후
──────────────────               ──────────────────────────────
SchemaReaderTool                 SchemaReaderTool
  └── jdbcTemplate 직접 호출       └── SchemaService.getTableSchema()
                                              │
CodeSaverTool                    CodeSaverTool  SchemaService
  └── Files.writeString() 직접    └── CodeService.save()   │
                                              │         jdbcTemplate
EmployeeTool                     EmployeeTool
  └── EmployeeRepository 직접     └── EmployeeService.getList()
```

### 작업 파일

```
신규 생성
├── service/SchemaService.java        ← SchemaReaderTool 로직 이동
├── service/CodeService.java          ← CodeSaverTool 로직 이동
└── service/EmployeeService.java      ← EmployeeTool 로직 이동

수정
├── tools/SchemaReaderTool.java       ← Service 호출로 교체
├── tools/CodeSaverTool.java          ← Service 호출로 교체
└── tools/EmployeeTool.java           ← Service 호출로 교체
```

### 체크리스트

```
  □ SchemaService 생성 및 SchemaReaderTool 연결
  □ CodeService 생성 및 CodeSaverTool 연결
  □ EmployeeService 생성 및 EmployeeTool 연결
  □ Claude Desktop 기존 동작 검증
```

### 검증 기준
- Claude Desktop 기존 Tool 동작 그대로 유지 확인
- 기존 11개 Tool 정상 응답 확인

---

## Phase 2 — transport stdio → SSE 전환

### 목적
HTTP 기반으로 전환해 모든 LLM이 접근 가능하게

### 변경 전/후 구조

```
변경 전                          변경 후
──────────────────               ──────────────────
Claude Desktop                   Claude Desktop
    │ stdio (JAR 직접실행)             │ HTTP SSE
    ▼                                 ▼
Spring Boot (no web)             Spring Boot (web)
    port: 없음                        port: 8080
```

### 작업 파일

```
수정
├── application.yaml
│     변경 전: web-application-type: none
│     변경 후: web-application-type: servlet
│
│     변경 전: transport: stdio
│     변경 후: transport: sse
│
└── build.gradle
      추가: implementation 'org.springframework.boot:spring-boot-starter-web'
```

### Claude Desktop 설정 변경

```json
// 변경 전 (stdio — JAR 직접 실행)
{
  "mcpServers": {
    "springai-mcp": {
      "command": "java",
      "args": ["-jar", "/Users/jeongdaeseob/workspace-spring-ai/springai/build/libs/springai-0.0.1-SNAPSHOT.jar"]
    }
  }
}

// 변경 후 (SSE — HTTP URL)
{
  "mcpServers": {
    "springai-mcp": {
      "url": "http://localhost:8080/sse"
    }
  }
}
```

### 체크리스트

```
  □ application.yaml 수정 (web-application-type, transport)
  □ build.gradle 수정 (spring-boot-starter-web 추가)
  □ 빌드 및 서버 기동 확인 (port 8080)
  □ Claude Desktop 설정 url 방식으로 변경
  □ SSE 연결 및 기존 Tool 동작 검증
```

### 검증 기준
- http://localhost:8080/sse 접속 확인
- Claude Desktop SSE 재연결 후 Tool 정상 동작 확인

---

## Phase 3 — OpenAI 연동

### 목적
OpenAI가 REST API(Function Calling)로 동일한 Tool 호출

### 변경 후 구조

```
                    Spring Boot Gateway (8080)
                           │
        ┌──────────────────┼──────────────────┐
        │ SSE              │ REST             │
        ▼                  ▼                  ▼
  Claude Desktop     OpenAI GPT          (Phase4)
  (MCP Client)    (Function Calling)    Local LLM
        │                  │
        └──────────────────┘
               │
        [공통 Service Layer]
        SchemaService / CodeService / EmployeeService
               │
           egov-mysql
```

### 작업 파일

```
신규 생성
├── config/SecurityConfig.java          ← API Key 인증
├── controller/ToolApiController.java   ← OpenAI용 REST 엔드포인트
│     POST /api/tools/getTableSchema
│     POST /api/tools/getTableList
│     POST /api/tools/saveGeneratedCode
│     POST /api/tools/getEmployee
│     POST /api/tools/getCodeTemplate
└── service/LlmRouterService.java       ← 라우팅 정책

수정
└── build.gradle
      추가: implementation 'org.springframework.ai:spring-ai-starter-model-openai'
```

### OpenAI Function 등록 예시 (클라이언트 측)

```json
{
  "name": "getTableSchema",
  "description": "MySQL 테이블 컬럼 정보 조회",
  "parameters": {
    "database": "string",
    "tableName": "string"
  },
  "url": "http://gateway:8080/api/tools/getTableSchema"
}
```

### LlmRouterService 라우팅 정책 (초안)

```java
case "CODE_GENERATION"  → Claude   // 코드 생성 — 품질 우선
case "CLASSIFICATION"   → OpenAI   // 단순 분류/요약 — 비용 절감
case "SENSITIVE_DATA"   → Ollama   // 민감 데이터 — 보안 (Phase4)
case "SIMPLE_QUERY"     → OpenAI   // 단순 조회 — 비용 절감
default                 → Claude   // 기본값
```

### 체크리스트

```
  □ SecurityConfig API Key 인증 구현
  □ ToolApiController REST 엔드포인트 구현
  □ LlmRouterService 기본 라우팅 구현
  □ OpenAI API Key 환경변수 설정
  □ OpenAI Function Calling 연동 검증
  □ Claude + OpenAI 동시 동작 검증
```

### 검증 기준
- OpenAI → REST → Service → DB 흐름 확인
- Claude(SSE)와 OpenAI(REST) 동일 Tool 결과 비교

---

## Phase 4 — Local LLM 연동 (Ollama)

### 목적
민감 데이터 처리를 사내 Local LLM으로 격리

### 변경 후 구조

```
                    LlmRouterService
                         │
         ┌───────────────┼───────────────┐
         │               │               │
         ▼               ▼               ▼
      Claude           OpenAI        Ollama
   코드 생성          단순 분류      민감 데이터
   고품질 작업        비용 절감      보안 필요
   (외부 API)        (외부 API)     (사내 서버)
```

### 작업 파일

```
신규 생성
└── config/OllamaConfig.java        ← Local LLM 연결 설정
                                       host: localhost
                                       port: 11434 (Ollama 기본)

수정
├── service/LlmRouterService.java   ← Ollama 라우팅 추가
└── build.gradle
      추가: implementation 'org.springframework.ai:spring-ai-starter-model-ollama'
```

### Ollama 설치 및 모델 준비

```bash
# Ollama 설치 (macOS)
brew install ollama

# 모델 다운로드
ollama pull llama3        # 범용
ollama pull codellama     # 코드 생성용
ollama pull mistral       # 경량 분류용

# 서버 실행
ollama serve
```

### 체크리스트

```
  □ Ollama 설치 및 모델 다운로드
  □ OllamaConfig 연결 설정 (localhost:11434)
  □ LlmRouterService Ollama 라우팅 추가
  □ 민감 데이터 분류 기준 정의
  □ 3개 LLM 동시 동작 검증
  □ 라우팅 정책 최종 확정
```

### 검증 기준
- 민감 데이터 요청 → Ollama로만 처리되는지 확인
- 외부 API 호출 없이 사내에서만 처리되는지 확인

---

## 전체 영향 평가 요약

| 파일 | Phase1 | Phase2 | Phase3 | Phase4 |
|---|---|---|---|---|
| application.yaml | 없음 | 수정 | 없음 | 없음 |
| build.gradle | 없음 | 수정 | 수정 | 수정 |
| SpringaiApplication.java | 없음 | 없음 | 없음 | 없음 |
| McpConfig.java | 없음 | 없음 | 없음 | 없음 |
| SchemaReaderTool.java | 수정 | 없음 | 없음 | 없음 |
| CodeSaverTool.java | 수정 | 없음 | 없음 | 없음 |
| EmployeeTool.java | 수정 | 없음 | 없음 | 없음 |
| CodeTemplateTool.java | 없음 | 없음 | 없음 | 없음 |
| EmployeeRepository.java | 없음 | 없음 | 없음 | 없음 |
| SchemaService.java (신규) | 생성 | 없음 | 없음 | 없음 |
| CodeService.java (신규) | 생성 | 없음 | 없음 | 없음 |
| EmployeeService.java (신규) | 생성 | 없음 | 없음 | 없음 |
| SecurityConfig.java (신규) | 없음 | 없음 | 생성 | 없음 |
| ToolApiController.java (신규) | 없음 | 없음 | 생성 | 없음 |
| LlmRouterService.java (신규) | 없음 | 없음 | 생성 | 수정 |
| OllamaConfig.java (신규) | 없음 | 없음 | 없음 | 생성 |

---

## 단계별 우선순위 및 리스크

| Phase | 핵심 작업 | 기존 영향 | 리스크 | 우선순위 |
|---|---|---|---|---|
| 1. Service 분리 | 로직 이동 | 없음 | 낮음 | 즉시 |
| 2. SSE 전환 | transport 변경 | Claude 설정 변경 | 중간 | Phase1 완료 후 |
| 3. OpenAI 연동 | REST API 추가 | 없음 | 중간 | Phase2 완료 후 |
| 4. Local LLM | Ollama 연동 | 없음 | 낮음 | Phase3 완료 후 |

---

## 최종 목표 아키텍처

```
사용자
  │
  ├── Claude Desktop ──── SSE ────────────────────┐
  │                                               │
  ├── OpenAI 앱    ──── REST (Function Calling) ──┤
  │                                               │
  └── Local 앱     ──── REST ─────────────────────┤
                                                  ▼
                              Spring Boot MCP Gateway (8080)
                                       │
                              LlmRouterService
                              (라우팅 정책 제어)
                                       │
                         ┌─────────────┼─────────────┐
                         ▼             ▼             ▼
                      Claude         OpenAI        Ollama
                    (코드생성)      (분류/요약)   (민감데이터)
                                       │
                              [공통 Service Layer]
                         SchemaService / CodeService
                         EmployeeService / CodeTemplateService
                                       │
                                  egov-mysql
```