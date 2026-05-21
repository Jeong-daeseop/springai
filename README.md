# eGovFrame 생성형 AI MCP 서버

Spring Boot + Spring AI 기반 **Model Context Protocol(MCP) 서버**.
Claude Desktop과 연동하여 eGovFrame 프로젝트 골격 생성, CRUD 소스 자동 생성, RAG 기반 문서 검색 등을 제공합니다.

---

## 기술 스택

| 구분 | 내용 |
|---|---|
| 프레임워크 | Spring Boot 4.0.6 + Spring AI 2.0.0-M6 |
| MCP 트랜스포트 | SSE (Server-Sent Events) |
| 외부 LLM | OpenAI GPT-4o-mini |
| 로컬 LLM | Ollama mistral (민감 데이터 처리용) |
| 임베딩 모델 | ko-sroberta-multitask (ONNX, 로컬 실행) |
| 벡터 DB | Redis Vector Store |
| DB | MySQL 8.0 (eGovFrame 표준 테이블) |
| Java | 17 |

---

## 주요 기능

### eGovFrame 프로젝트 생성 (ProjectInitializr)
- `war` / `boot` × `maven` / `gradle` × `4.3` / `5.0` 조합으로 프로젝트 골격 즉시 생성
- eGovFrame 4.3 (Spring 5.3 / Java 11 / javax) ↔ 5.0 (Spring 6.2 / Java 17 / Jakarta EE) 완전 분기
- Capability Matrix 설계 — 버전별 분기를 8개 독립 메서드로 제어

### CRUD 소스 자동 생성
- DB 테이블 스키마 기반 eGovFrame 표준 레이어 생성
- Controller / Service / ServiceImpl / Mapper / MapperXML / VO / JSP(4종) 10개 레이어

### RAG 기반 문서 검색
- eGovFrame 공식 문서 임베딩 + Redis 벡터 검색
- 유사도 임계값 0.70, top-k 3

### Spring Security 템플릿
- eGovFrame 4.x (`WebSecurityConfigurerAdapter`) / 5.x (`SecurityFilterChain`) 자동 분기

---

## 빠른 시작

### 사전 요구사항

- Java 17+
- Docker (MySQL, Redis)
- Claude Desktop

### 환경 변수

```bash
export OPENAI_API_KEY=sk-...
export APP_API_KEY=your-api-key       # MCP 서버 인증 키
export EGOV_OUTPUT_PATH=~/Desktop/egov-generated
```

### 빌드 및 실행

```bash
# Docker 컨테이너 시작 (MySQL + Redis)
docker start egov-mysql egov-redis

# JAR 빌드
./gradlew bootJar

# 실행
java -jar build/libs/springai-0.0.1-SNAPSHOT.jar
```

### Claude Desktop 연동

`~/Library/Application Support/Claude/claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "springai-mcp": {
      "url": "http://localhost:8080/sse",
      "headers": {
        "X-API-Key": "your-api-key"
      }
    }
  }
}
```

---

## MCP Tool 목록

| 분류 | Tool | 설명 |
|---|---|---|
| 프로젝트 생성 | `initializeProject` | eGovFrame 4.3/5.0 프로젝트 골격 생성 |
| 프로젝트 생성 | `getConfigTemplate` | 설정 파일 템플릿 반환 |
| CRUD | `buildFullCrudPrompt` | 테이블 기반 CRUD 소스 생성 프롬프트 |
| CRUD | `getCodeTemplate` | 레이어별 소스 템플릿 반환 |
| CRUD | `saveGeneratedCode` | 생성 소스 파일 저장 |
| DB | `getTableSchema` | 테이블 컬럼/PK 정보 조회 |
| DB | `getTableList` | DB 테이블 목록 조회 |
| 검증 | `validateGeneratedCodeDirectory` | eGovFrame 표준 준수 검증 |
| 검증 | `checkProjectHealth` | 도메인 완성도 점검 |
| Security | `getSecurityTemplate` | Spring Security 설정 템플릿 반환 |
| RAG | `searchDocument` | eGovFrame 문서 벡터 검색 |
| 이력 | `getGenerationHistory` | CRUD 생성 이력 조회 |

---

## eGovFrame 버전별 생성 내용

| projectType | egovVersion | Spring | Java | Servlet | MyBatis-Spring |
|---|---|---|---|---|---|
| `war` + `4.3` | 4.3.0 | 5.3.37 | 11 | javax 4.0 | 2.1.2 |
| `war` + `5.0` | 5.0.0 | 6.2.11 | 17 | Jakarta EE 10 | 3.0.3 |
| `boot` + `4.3` | 4.3.0 | 5.3.37 (Boot 2.7.18) | 11 | — | starter 2.3.2 |
| `boot` + `5.0` | 5.0.0 | 6.2.11 (Boot 3.5.6) | 17 | — | starter 3.0.3 |

---

## 로그

```bash
# 애플리케이션 로그
tail -f logs/springai-mcp.log

# Claude Desktop MCP 연결 로그
tail -f ~/Library/Logs/Claude/mcp-server-springai-mcp.log
```
