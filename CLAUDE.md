# CLAUDE.md

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
빌드 후 Claude Desktop을 재시작하면 MCP 서버가 자동 연결됩니다.
JAR 경로: `build/libs/springai-0.0.1-SNAPSHOT.jar`

Claude Desktop 설정 파일: `~/Library/Application Support/Claude/claude_desktop_config.json`

### 로그 확인
```bash
# Spring Boot 애플리케이션 로그
tail -f /tmp/springai-mcp.log

# Claude Desktop MCP 연결 로그
tail -f ~/Library/Logs/Claude/mcp-server-springai-mcp.log
```

---

## Architecture

**Spring Boot 4.0.6 + Spring AI 2.0.0-M6** 기반 **MCP(Model Context Protocol) Server**.
웹 서버 없이 `stdio` 트랜스포트로 Claude Desktop과 JSON-RPC 통신합니다.

```
Claude Desktop
    │ JSON-RPC over stdio
    ▼
spring-ai-starter-mcp-server  (JSON-RPC dispatcher 자동 처리)
    │ @Tool 어노테이션 메서드 자동 라우팅
    ▼
*Tool 클래스  →  Repository(JdbcTemplate)  →  egov-mysql (Docker)
```

**핵심 설정:**
- `web-application-type: none` — HTTP 서버 비활성화
- `transport: stdio` — Claude Desktop이 JAR을 직접 실행, stdin/stdout 통신
- `banner-mode: off` + `console: ""` — stdout을 JSON-RPC 전용으로 유지 (출력 오염 방지)
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
@Bean
public ToolCallbackProvider myToolCallbacks(MyTool myTool) {
    return MethodToolCallbackProvider.builder().toolObjects(myTool).build();
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

## 현재 등록된 MCP Tool

| Tool 메서드 | 기능 |
|---|---|
| `getCurrentDateTime(timezone)` | IANA 시간대 기준 현재 시각 반환 |
| `celsiusToFahrenheit(celsius)` | 섭씨→화씨 변환 |
| `getEmployeeList(keyword)` | COMTNEMPLYRINFO 목록 조회 (최대 20건) |
| `getEmployee(emplyrId)` | 직원 단건 조회 |
| `createEmployee(...)` | 직원 등록 |
| `updateEmployee(...)` | 직원 수정 |
| `deleteEmployee(emplyrId)` | 직원 삭제 |

---

## 향후 구현 예정 — eGovFrame 5 CRUD 소스 자동 생성

Claude가 DB 테이블명을 받으면 eGovFrame 5.x 표준 CRUD 소스를 자동 생성하는 Tool 2개 추가 예정:

| Tool | 역할 |
|---|---|
| `getTableSchema(database, tableName)` | 컬럼/PK/타입 정보 조회 → Claude가 소스 생성에 활용 |
| `getTableList(database)` | DB 내 테이블 목록 조회 |
| `saveGeneratedCode(filePath, code)` | Claude가 생성한 소스를 파일로 저장 |

**eGovFrame 5.x 생성 대상 레이어:**
```
egovframework.let.{domain}.web        → Egov*Controller.java
egovframework.let.{domain}.service    → Egov*Service.java (interface)
egovframework.let.{domain}.service.impl → Egov*ServiceImpl.java
egovframework.let.{domain}.service.impl → *Mapper.java (@Mapper)
egovframework.let.{domain}.vo         → *VO.java
resources/egovframework/mapper/{domain} → *Mapper.xml
```

**eGovFrame 5.x 스펙:** Spring Framework 6.1.x / Spring Boot 3.2.x / Java 17+ / MyBatis 3.5.x / Jakarta EE
