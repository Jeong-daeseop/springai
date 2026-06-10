# Hybrid Architecture 전환 영향 평가

작성일: 2026-05-15
프로젝트: springai-mcp (Spring Boot MCP Gateway → Hybrid Architecture)

---

## 현재 vs 목표 구조

```
현재 (MCP Gateway)                목표 (Hybrid Architecture)
──────────────────────            ──────────────────────────────
Claude Desktop                    Claude  ← 오케스트레이터
    │ stdio                           │
    ▼                             OpenAI / Local LLM
Spring Boot MCP                       │
    │                             Spring Boot Gateway
    └── Tools → egov-mysql             │ HTTP (SSE/REST)
                                       └── Tools → egov-mysql
```

---

## 현재 프로젝트 파일 목록

```
src/main/java/com/krdevops/springai/
├── SpringaiApplication.java
├── config/
│   └── McpConfig.java
├── mapper/
│   └── EmployeeRepository.java
├── tools/
│   ├── CodeSaverTool.java
│   ├── CodeTemplateTool.java
│   ├── DateTimeTool.java
│   ├── EmployeeTool.java
│   └── SchemaReaderTool.java
└── vo/
    └── EmployeeVO.java

src/main/resources/
└── application.yaml

build.gradle
```

---

## 파일별 상세 영향 평가

### 🔴 HIGH — 전면 변경 필요

#### application.yaml
- **현재 설정**
  ```yaml
  spring:
    main:
      web-application-type: none
      banner-mode: off
    ai:
      mcp:
        server:
          transport: stdio
  ```
- **변경 후 설정**
  ```yaml
  spring:
    main:
      web-application-type: servlet   # 변경
      banner-mode: off
    ai:
      mcp:
        server:
          transport: sse              # 변경
  ```
- **영향:** Claude Desktop 설정 파일도 함께 변경 필요
- **공수:** 낮음 (설정 2줄 변경)

---

#### build.gradle
- **현재 의존성**
  ```groovy
  implementation 'org.springframework.ai:spring-ai-starter-mcp-server'
  implementation 'org.springframework.boot:spring-boot-starter-jdbc'
  runtimeOnly 'com.mysql:mysql-connector-j'
  ```
- **추가 의존성**
  ```groovy
  // Phase 2 — SSE 전환
  implementation 'org.springframework.boot:spring-boot-starter-web'

  // Phase 3 — OpenAI 연동
  implementation 'org.springframework.ai:spring-ai-starter-model-openai'

  // Phase 4 — Local LLM 연동
  implementation 'org.springframework.ai:spring-ai-starter-model-ollama'
  ```
- **영향:** 의존성 추가만, 기존 의존성 제거 없음
- **공수:** 낮음

---

### 🟡 MEDIUM — 구조 추가 필요

#### SchemaReaderTool.java
- **현재:** JdbcTemplate 직접 호출
  ```java
  @Tool
  public String getTableSchema(String database, String tableName) {
      jdbcTemplate.queryForList(...);  // 직접 호출
  }
  ```
- **변경 후:** SchemaService 위임
  ```java
  @Tool
  public String getTableSchema(String database, String tableName) {
      return schemaService.getTableSchema(database, tableName);  // 위임
  }
  ```
- **영향:** Tool 동작 변경 없음, 내부 구조만 변경
- **공수:** 낮음

---

#### CodeSaverTool.java
- **현재:** Files.writeString() 직접 호출
- **변경 후:** CodeService 위임
- **영향:** Tool 동작 변경 없음
- **공수:** 낮음

---

#### EmployeeTool.java
- **현재:** EmployeeRepository 직접 주입
- **변경 후:** EmployeeService 위임
- **영향:** Tool 동작 변경 없음
- **공수:** 낮음

---

### 🟢 LOW — 재사용 가능 (변경 없음)

#### SpringaiApplication.java
- **영향:** 없음
- **이유:** 진입점 변경 불필요

#### McpConfig.java
- **영향:** 없음
- **이유:** SSE 전환 시 Spring AI가 자동 처리

#### CodeTemplateTool.java
- **영향:** 없음
- **이유:** 템플릿 내용은 LLM 종류와 무관

#### DateTimeTool.java
- **영향:** 없음
- **이유:** 외부 의존 없는 순수 로직

#### EmployeeRepository.java
- **영향:** 없음
- **이유:** DB 접근 로직 변경 없음

#### EmployeeVO.java
- **영향:** 없음
- **이유:** 데이터 구조 변경 없음

---

## 신규 추가 파일 (5개)

### service/SchemaService.java (Phase 1)
```
역할: SchemaReaderTool의 DB 조회 로직 분리
      MCP Tool과 REST API가 공통으로 사용
내용: getTableList(), getTableSchema()
의존: JdbcTemplate
```

### service/CodeService.java (Phase 1)
```
역할: CodeSaverTool의 파일 저장 로직 분리
내용: saveGeneratedCode(), checkOutputDirectory()
의존: Java NIO (Files)
```

### service/EmployeeService.java (Phase 1)
```
역할: EmployeeTool의 CRUD 로직 분리
내용: getList(), getOne(), create(), update(), delete()
의존: EmployeeRepository
```

### config/SecurityConfig.java (Phase 3)
```
역할: REST API 호출 시 API Key 인증
내용: /api/tools/** 경로 인증 필터
의존: Spring Security
```

### controller/ToolApiController.java (Phase 3)
```
역할: OpenAI Function Calling용 REST 엔드포인트
내용:
  POST /api/tools/getTableList
  POST /api/tools/getTableSchema
  POST /api/tools/saveGeneratedCode
  POST /api/tools/getCodeTemplate
  POST /api/tools/getEmployee
의존: SchemaService, CodeService, EmployeeService
```

### service/LlmRouterService.java (Phase 3~4)
```
역할: 작업 유형에 따라 최적 LLM 선택
내용:
  CODE_GENERATION  → Claude  (품질 우선)
  CLASSIFICATION   → OpenAI  (비용 절감)
  SENSITIVE_DATA   → Ollama  (보안/사내)
  SIMPLE_QUERY     → OpenAI  (비용 절감)
  default          → Claude
의존: ChatClient (Claude/OpenAI/Ollama)
```

### config/OllamaConfig.java (Phase 4)
```
역할: Local LLM 연결 설정
내용: host=localhost, port=11434
의존: spring-ai-starter-model-ollama
```

---

## Claude Desktop 설정 변경

```json
// 변경 전 (Phase 1~2 이전 — stdio)
{
  "mcpServers": {
    "springai-mcp": {
      "command": "java",
      "args": [
        "-jar",
        "/Users/jeongdaeseob/workspace-spring-ai/springai/build/libs/springai-0.0.1-SNAPSHOT.jar"
      ]
    }
  }
}

// 변경 후 (Phase 2 이후 — SSE)
{
  "mcpServers": {
    "springai-mcp": {
      "url": "http://localhost:8080/sse"
    }
  }
}
```

---

## 전체 파일 영향 매트릭스

| 파일 | 구분 | Phase1 | Phase2 | Phase3 | Phase4 | 비고 |
|---|---|---|---|---|---|---|
| application.yaml | 기존 | 없음 | 수정 | 없음 | 없음 | transport, web-type 변경 |
| build.gradle | 기존 | 없음 | 수정 | 수정 | 수정 | 의존성 추가 |
| SpringaiApplication.java | 기존 | 없음 | 없음 | 없음 | 없음 | 변경 없음 |
| McpConfig.java | 기존 | 없음 | 없음 | 없음 | 없음 | 변경 없음 |
| SchemaReaderTool.java | 기존 | 수정 | 없음 | 없음 | 없음 | Service 위임 |
| CodeSaverTool.java | 기존 | 수정 | 없음 | 없음 | 없음 | Service 위임 |
| EmployeeTool.java | 기존 | 수정 | 없음 | 없음 | 없음 | Service 위임 |
| CodeTemplateTool.java | 기존 | 없음 | 없음 | 없음 | 없음 | 변경 없음 |
| DateTimeTool.java | 기존 | 없음 | 없음 | 없음 | 없음 | 변경 없음 |
| EmployeeRepository.java | 기존 | 없음 | 없음 | 없음 | 없음 | 변경 없음 |
| EmployeeVO.java | 기존 | 없음 | 없음 | 없음 | 없음 | 변경 없음 |
| SchemaService.java | 신규 | 생성 | 없음 | 없음 | 없음 | |
| CodeService.java | 신규 | 생성 | 없음 | 없음 | 없음 | |
| EmployeeService.java | 신규 | 생성 | 없음 | 없음 | 없음 | |
| SecurityConfig.java | 신규 | 없음 | 없음 | 생성 | 없음 | |
| ToolApiController.java | 신규 | 없음 | 없음 | 생성 | 없음 | |
| LlmRouterService.java | 신규 | 없음 | 없음 | 생성 | 수정 | |
| OllamaConfig.java | 신규 | 없음 | 없음 | 없음 | 생성 | |

---

## 리스크 평가

| Phase | 리스크 | 내용 | 대응 방안 |
|---|---|---|---|
| Phase 1 | 낮음 | Tool 동작 변경 없음 | 단위 테스트로 검증 |
| Phase 2 | 중간 | Claude Desktop 재설정 필요 | SSE 전환 전 stdio 백업 보관 |
| Phase 3 | 중간 | OpenAI API 비용 발생 | 라우팅 정책으로 호출 최소화 |
| Phase 4 | 낮음 | Ollama 모델 용량 큼 (수 GB) | 경량 모델(mistral) 우선 적용 |

---

## 공수 산정

| Phase | 작업 내용 | 예상 공수 |
|---|---|---|
| Phase 1 | Service 레이어 분리 (3개 파일) | 0.5일 |
| Phase 2 | SSE 전환 + Claude Desktop 재설정 | 0.5일 |
| Phase 3 | REST API + 인증 + OpenAI 연동 | 2일 |
| Phase 4 | Ollama 설치 + Local LLM 연동 | 1일 |
| **합계** | | **4일** |

---

## 기존 코드 재사용률

| 구분 | 파일 수 | 비율 |
|---|---|---|
| 변경 없음 (완전 재사용) | 7개 | 64% |
| 수정 (부분 변경) | 5개 | 45% |
| 신규 추가 | 7개 | - |

> 기존 비즈니스 로직(Tool 내용, DB 접근, 템플릿)은 100% 재사용 가능

---

## 결론

1. **기존 코드 손상 없음** — Tool 로직, DB 접근, 템플릿 전부 재사용
2. **단계적 전환 가능** — Phase별 독립 진행, 이전 단계 롤백 가능
3. **가장 큰 작업** — LlmRouterService 라우팅 정책 설계 (비즈니스 결정 필요)
4. **총 예상 공수** — 4일