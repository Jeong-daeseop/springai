# 구현 가능한 현실적 MCP + RAG 아키텍처

작성일: 2026-05-21

---

## 전체 아키텍처 다이어그램

```
┌──────────────────────────────────────────────┐
│               Client UI                      │
│----------------------------------------------│
│ Claude Desktop                               │
│ 사내 Web Admin                                │
└──────────────────┬───────────────────────────┘
                   │ MCP(stdio/SSE/HTTP)
                   ▼
┌──────────────────────────────────────────────┐
│          Spring Boot MCP Server              │
│----------------------------------------------│
│ [MCP Layer]                                  │
│  - MCP Controller                            │
│  - Tool Registry                             │
│  - Tool Dispatcher                           │
│                                              │
│ [Tool Layer]                                 │
│  - CrudGenerateTool                          │
│  - RagSearchTool                             │
│  - SqlTool                                   │
│  - GitTool                                   │
│                                              │
│ [Prompt Layer]                               │
│  - PromptBuilder                             │
│  - ContextAssembler                          │
│  - TemplateManager                           │
│                                              │
│ [LLM Layer]                                  │
│  - OllamaClient                              │
│  - OpenAIClient                              │
│  - ClaudeClient                              │
└───────────────┬──────────────────────────────┘
                │
      ┌─────────┴─────────┐
      │                   │
      ▼                   ▼
┌─────────────────┐   ┌──────────────────────┐
│    RAG Engine   │   │    CRUD Generator    │
│-----------------│   │----------------------│
│ EmbeddingService│   │ Controller Template  │
│ ChunkService    │   │ Service Template     │
│ VectorSearch    │   │ DAO Template         │
│ DocumentLoader  │   │ Mapper Template      │
└────────┬────────┘   │ JSP Template         │
         │            └──────────┬───────────┘
         ▼                       │
┌─────────────────┐              │
│    Vector DB    │              │
│-----------------│              │
│ Chroma          │              │
│ PGVector        │              │
└────────┬────────┘              │
         │                       │
         └──────────┬────────────┘
                    ▼
┌──────────────────────────────────────────────┐
│               LLM Runtime                    │
│----------------------------------------------│
│ Ollama                                       │
│ GPT API                                      │
│ Claude API                                   │
└──────────────────────────────────────────────┘
```

---

## 상세 설명

### 1. Client UI 영역

실제 사용자가 질문하는 곳.

| 클라이언트 | 설명 |
|---|---|
| Claude Desktop | MCP stdio/SSE 로컬 연결 |
| ChatGPT | HTTP 기반 MCP 플러그인 |
| 사내 Web UI | 브라우저 기반 관리 화면 |

**예시 요청:**
```
회원관리 CRUD 생성해줘
```

---

### 2. MCP 연결 방식

| 방식 | 설명 | 추천 시점 |
|---|---|---|
| stdio | Claude Desktop 로컬 연결 | 초기 개발 — 가장 단순 |
| SSE | 실시간 스트리밍 | 웹 UI 연동 |
| HTTP | Web 기반 MCP | 다중 클라이언트 |

> 초기에는 **stdio** 가 가장 단순하고 안정적입니다.

---

### 3. Spring Boot MCP Server 상세

#### (1) MCP Layer

MCP 프로토콜 처리 담당.

| 컴포넌트 | 역할 | 예시 |
|---|---|---|
| MCP Controller | MCP 요청 수신 | `@PostMapping("/tools/call")` |
| Tool Registry | 등록된 Tool 목록 관리 | `crud_generate`, `rag_search`, `sql_query` |
| Tool Dispatcher | toolName 기반 Tool 라우팅 | 요청 → 해당 Tool 메서드 호출 |

#### (2) Tool Layer

실제 업무 기능을 담당합니다.

**CrudGenerateTool — 핵심 Tool**
```
입력: { "table": "USER", "framework": "egovframe" }
출력: Controller / Service / DAO / Mapper / JSP
```

**RagSearchTool — 문서 검색**
```
"전자정부 로그인 구조" → 유사 문서 반환
```

**SqlTool — DB Schema 조회**
```sql
DESC TB_USER
```

**GitTool — 기존 프로젝트 검색**
```
기존 회원관리 패턴 검색
```

---

### 4. Prompt Layer — AI 품질 핵심

#### (1) PromptBuilder

사용자 요청 + RAG 결과 + DB Schema + 회사 규칙을 조합하여 최적의 프롬프트 생성.

```
[사용자 요청]
회원관리 CRUD 생성해줘

[RAG 결과]
기존 회원관리 패턴 문서

[DB Schema]
TB_USER (USER_ID, USER_NM, EMAIL ...)

[회사 규칙]
eGovFrame 표준 레이어 구조 준수
```

#### (2) ContextAssembler

Context 최적화 처리:
- 중복 문서 제거
- 토큰 길이 제한 내 조정
- 중요 문서 우선 배치

#### (3) TemplateManager

Mustache 기반 CRUD 템플릿 관리.
```
controller.mustache
service.mustache
mapper.mustache
```

---

### 5. RAG Engine 상세

#### (1) DocumentLoader

다양한 소스에서 문서 읽기:
- Markdown / PDF
- 소스코드
- API 문서

#### (2) ChunkService

문서를 일정 단위로 분할 (검색 정밀도 향상):
```
1,000 token 단위 분할 + overlap 적용
```

#### (3) EmbeddingService

텍스트 → 벡터 변환.

| 모델 | 특징 |
|---|---|
| nomic-embed-text | Ollama 로컬 실행 가능 |
| BGE-M3 | 한국어 성능 우수 |
| ko-sroberta-multitask | ONNX 로컬 실행 |

#### (4) VectorSearch

코사인 유사도 기반 Top-K 검색 (임계값 필터링 포함).

---

### 6. Vector DB 선택 기준

| DB | 특징 | 추천 상황 |
|---|---|---|
| **Redis Vector Store** | 빠른 속도·간단한 설정 | 기존 Redis 인프라 보유 시 (현재 구현) |
| Chroma | 경량·로컬 실행 | 개발·테스트 환경 |
| PGVector | PostgreSQL 확장·트랜잭션 | 관계형 DB와 통합 필요 시 |

---

### 7. CRUD Generator — Template vs LLM 역할 분리

**Template 중심 (안정적)**
```
Controller.mustache
    ↓ 변수 치환 (${tableName}, ${columns})
    ↓
코드 생성
```

**LLM 역할 — 템플릿 보정**

LLM이 모든 코드를 직접 생성하면:
- 일관성 깨짐
- 품질 흔들림
- 재현 어려움

**권장: Template 중심 + LLM 보조(보정)** 구조가 안정적입니다.

---

### 8. LLM Runtime

| 런타임 | 대표 모델 | 용도 |
|---|---|---|
| Ollama (로컬) | llama3 / qwen / deepseek-coder | 민감 데이터·오프라인 환경 |
| GPT API | GPT-4o / GPT-4o-mini | 고성능 추론·복잡 설계 |
| Claude API | Claude Sonnet / Opus | 장문 컨텍스트·아키텍처 생성 |

---

### 9. 실제 처리 흐름 — CRUD 생성 예시

```
[1] 사용자
    "회원관리 CRUD 생성"
         ↓
[2] MCP Server 수신
         ↓
[3] SqlTool
    → TB_USER 테이블 스키마 조회
         ↓
[4] RagSearchTool
    → 기존 회원관리 패턴 문서 검색
         ↓
[5] PromptBuilder
    → 요청 + Schema + RAG 결과 조합
         ↓
[6] Ollama / GPT 호출
         ↓
[7] CRUD 코드 생성 (Template 치환)
         ↓
[8] Claude Desktop 출력
```

---

### 10. 실제 구현 패키지 구조

```
src/main/java
 ├── mcp
 │    ├── McpController
 │    ├── ToolRegistry
 │    └── ToolDispatcher
 │
 ├── tool
 │    ├── CrudGenerateTool
 │    ├── RagSearchTool
 │    ├── SqlTool
 │    └── GitTool
 │
 ├── rag
 │    ├── EmbeddingService
 │    ├── ChunkService
 │    ├── VectorSearchService
 │    └── DocumentLoader
 │
 ├── prompt
 │    ├── PromptBuilder
 │    ├── ContextAssembler
 │    └── TemplateManager
 │
 ├── llm
 │    ├── OllamaClient
 │    ├── OpenAIClient
 │    └── ClaudeClient
 │
 └── generator
      ├── ControllerGenerator
      ├── ServiceGenerator
      └── MapperGenerator
```

---

### 11. 현실적인 구현 시작 순서

| 단계 | 항목 | 이유 |
|---|---|---|
| 1 | Claude Desktop 연결 | MCP 기본 통신 확인 |
| 2 | MCP Tool 호출 | Tool 등록·라우팅 검증 |
| 3 | Ollama 연동 | 로컬 LLM 기본 응답 확인 |
| 4 | CRUD Prompt 생성 | 핵심 기능 조기 검증 |
| 5 | Template 적용 | 코드 품질 안정화 |
| 6 | DB Schema 조회 | SqlTool 연동 |
| 7 | RAG 추가 | 문서 기반 응답 품질 향상 |
| 8 | Vector DB 추가 | 대규모 문서 검색 지원 |

---

### 12. 최종 현실형 추천 구조

```
Claude Desktop
      ↓
Spring Boot MCP
      ↓
Tool Layer
  ├─ CRUD Tool
  ├─ SQL Tool
  └─ RAG Search
      ↓
Prompt Builder
      ↓
Template Engine   ← 핵심: Template 중심
      ↓
Ollama            ← LLM은 보조(보정) 역할
```

## 레이어별 설명

### 1. Client UI

사용자가 MCP Server와 상호작용하는 진입점입니다.

| 클라이언트 | 프로토콜 | 용도 |
|---|---|---|
| Claude Desktop | MCP over SSE | AI 어시스턴트 — Tool 자동 선택·호출 |
| ChatGPT | MCP over HTTP | GPT 기반 Tool 연동 (플러그인 방식) |
| 사내 Web Admin | HTTP REST / SSE | 관리자 UI — RAG 채팅·문서 관리·이력 조회 |

---

### 2. Spring Boot MCP Server

MCP 프로토콜 처리부터 LLM 호출까지 모든 비즈니스 로직을 담당합니다.

#### MCP Layer

| 컴포넌트 | 역할 |
|---|---|
| MCP Controller | JSON-RPC 요청 수신·응답 처리 |
| Tool Registry | 등록된 Tool 목록 관리 (`tools/list` 응답) |
| Tool Dispatcher | `tools/call` 수신 시 적합한 Tool 메서드 라우팅 |

#### Tool Layer

| Tool | 역할 |
|---|---|
| CrudGenerateTool | DB 테이블 기반 eGovFrame CRUD 소스 자동 생성 |
| RagSearchTool | Vector DB 유사 문서 검색 및 임베딩 저장 |
| SqlTool | 자연어 → SQL 변환 및 실행 |
| GitTool | Git 이력 조회·브랜치 관리·커밋 분석 |

#### Prompt Layer

| 컴포넌트 | 역할 |
|---|---|
| PromptBuilder | Tool 결과 + RAG 컨텍스트를 LLM 입력 프롬프트로 조합 |
| ContextAssembler | 검색 문서·대화 이력·시스템 역할을 하나의 컨텍스트로 통합 |
| TemplateManager | 도메인별 소스 생성 템플릿 관리 (Controller·Service·Mapper 등) |

#### LLM Layer

| 클라이언트 | 모델 | 용도 |
|---|---|---|
| OllamaClient | mistral / llama3 등 | 로컬 실행 — 사내 민감 데이터 처리 |
| OpenAIClient | GPT-4o / GPT-4o-mini | 외부 API — 고성능 추론 |
| ClaudeClient | Claude Sonnet / Opus | 외부 API — 장문 컨텍스트·코드 생성 |

---

### 3. RAG Engine

문서를 벡터화하고 유사 문서를 검색하여 LLM 응답 품질을 향상시킵니다.

| 컴포넌트 | 역할 |
|---|---|
| EmbeddingService | 텍스트 → 벡터 변환 (ONNX 로컬 모델 또는 OpenAI Embedding API) |
| ChunkService | 대용량 문서를 일정 크기로 분할 (overlap 포함) — 검색 정밀도 향상 |
| VectorSearch | 코사인 유사도 기반 top-k 문서 검색 (임계값 필터링 포함) |
| DocumentLoader | URL 크롤링·파일 업로드·텍스트 직접 입력 등 다양한 소스 지원 |

**RAG → LLM 실제 흐름:**

```
사용자 질문
    │
    ▼
① Query Compression  — 대화 이력 기반 질문 압축
    │
    ▼
② VectorSearch       — 유사 문서 top-k 검색
    │
    ▼
③ ContextAssembler   — 검색 결과를 문자열로 포맷
    │
    ▼
④ system prompt 주입 — "아래 참고 문서를 기반으로 답변하세요:\n" + context
    │
    ▼
⑤ LLM 호출          — system(역할+RAG) + user(질문)
```

> RAG 검색 결과는 LLM에 직접 전달되는 것이 아니라
> `system` 메시지 안에 문자열로 삽입되어 전달됩니다.

---

### 4. Vector DB

| DB | 특징 | 적합한 상황 |
|---|---|---|
| **Redis Vector Store** | 빠른 응답·간단한 설정 | 중소규모·기존 Redis 인프라 보유 시 |
| Chroma | 경량·로컬 실행 가능 | 개발·테스트 환경 |
| PGVector | PostgreSQL 확장·트랜잭션 지원 | 관계형 DB와 통합 필요 시 |

---

### 5. CRUD Generator

eGovFrame 표준 레이어 소스를 DB 스키마 기반으로 자동 생성합니다.

| 템플릿 | 생성 파일 |
|---|---|
| Controller Template | `Egov*Controller.java` — Spring MVC 요청 처리 |
| Service Template | `Egov*Service.java` (interface) + `*ServiceImpl.java` |
| DAO Template | `*Mapper.java` (@Mapper 인터페이스) |
| Mapper Template | `*Mapper.xml` — MyBatis SQL 정의 |
| JSP Template | List / Detail / Regist / Update 4개 화면 |

---

### 6. LLM Runtime

| 런타임 | 실행 방식 | 특징 |
|---|---|---|
| Ollama | 로컬 서버 (HTTP API) | 인터넷 미연결 환경·민감 데이터 처리 |
| GPT API | OpenAI REST API | 고성능·최신 모델·토큰 비용 발생 |
| Claude API | Anthropic REST API | 장문 컨텍스트(200K)·코드 생성 강점 |

---

## 현재 구현 상태 (2026-05-21 기준)

| 레이어 | 항목 | 상태 |
|---|---|---|
| Client | Claude Desktop | ✅ 구현 완료 |
| Client | 사내 Web Admin | 🔶 기본 UI (채팅·문서 업로드) |
| Client | ChatGPT 연동 | ❌ 미구현 |
| MCP Layer | Tool Registry / Dispatcher | ✅ 구현 완료 (17개 Tool) |
| Tool Layer | CrudGenerateTool | ✅ 구현 완료 |
| Tool Layer | RagSearchTool | ✅ 구현 완료 |
| Tool Layer | SqlTool | ❌ 미구현 |
| Tool Layer | GitTool | ❌ 미구현 |
| Prompt Layer | TemplateManager | ✅ 구현 완료 |
| Prompt Layer | ContextAssembler (RAG 전용) | 🔶 부분 구현 |
| Prompt Layer | 공통 PromptBuilder | ❌ 미구현 |
| LLM Layer | OllamaClient | ✅ 구현 완료 |
| LLM Layer | OpenAIClient | ✅ 구현 완료 |
| LLM Layer | ClaudeClient | ❌ 미구현 |
| RAG Engine | EmbeddingService (ONNX 로컬) | ✅ 구현 완료 |
| RAG Engine | VectorSearch | ✅ 구현 완료 |
| RAG Engine | DocumentLoader | ✅ 구현 완료 |
| RAG Engine | ChunkService | ❌ 미구현 |
| Vector DB | Redis Vector Store | ✅ 구현 완료 |
| Vector DB | Chroma / PGVector | ❌ 미사용 |
| CRUD Generator | 5개 레이어 템플릿 | ✅ 구현 완료 |

**전체 완성도: 약 65%**

---

## Gap 분석 — 미구현 항목 우선순위

| 우선순위 | 항목 | 이유 |
|---|---|---|
| P1 | ChunkService | 대용량 문서 RAG 정밀도 핵심 |
| P2 | 공통 PromptBuilder | Tool별 프롬프트 중복 제거 |
| P3 | SqlTool | 자연어 DB 조회 수요 높음 |
| P4 | ClaudeClient | 장문 코드 생성 품질 향상 |
| P5 | GitTool | CI/CD 연계 자동화 |
| P6 | ChatGPT 연동 | 다중 클라이언트 지원 |

---

## 점검 및 구현 영향 평가

### 1. Client UI — 연결 방식 점검

| 항목 | 다이어그램 | 현재 구현 | 영향 |
|---|---|---|---|
| Claude Desktop | MCP stdio/SSE/HTTP | SSE (port 8080) | ✅ 정상 동작 |
| 사내 Web Admin | HTTP | 기본 채팅·문서 UI | 🔶 관리자 기능 미흡 |
| MCP 트랜스포트 | stdio 권장 | SSE 사용 중 | ⚠️ 서버 별도 기동 필요 |

**영향 평가:**
- SSE 방식은 Spring Boot 서버가 항상 실행 중이어야 하므로, 서버 미기동 시 Claude Desktop 연결 불가 (`ECONNREFUSED`)
- stdio 방식으로 전환 시 Claude Desktop이 JAR을 직접 실행 — 서버 별도 기동 불필요

---

### 2. MCP Layer — Tool 등록 구조 점검

| 항목 | 다이어그램 | 현재 구현 | 영향 |
|---|---|---|---|
| MCP Controller | `@PostMapping("/tools/call")` 수동 구현 | Spring AI MCP 자동 처리 | ✅ 더 단순한 구현 |
| Tool Registry | 수동 등록 | `McpConfig` + `ToolCallbackProvider` | ✅ 17개 Tool 자동 등록 |
| Tool Dispatcher | 수동 라우팅 | `@Tool` 어노테이션 자동 라우팅 | ✅ 추가 코드 불필요 |

**영향 평가:**
- Spring AI MCP가 Controller·Dispatcher를 자동 처리하므로 다이어그램의 수동 구현 부분은 불필요
- 신규 Tool 추가 시 `@Tool` 메서드 작성 + `McpConfig` 빈 등록 2단계만 필요

---

### 3. Tool Layer — 미구현 Tool 영향 평가

| Tool | 현재 상태 | 미구현 시 영향 | 구현 난이도 |
|---|---|---|---|
| CrudGenerateTool | ✅ 구현 완료 | — | — |
| RagSearchTool | ✅ 구현 완료 | — | — |
| SqlTool | ❌ 미구현 | 자연어 DB 조회 불가 → Claude가 스키마 수동 입력 요구 | 중 |
| GitTool | ❌ 미구현 | 기존 코드 패턴 참조 불가 → RAG 문서 의존도 증가 | 중 |

**SqlTool 미구현 실제 영향:**
```
현재: 사용자가 테이블 구조를 직접 설명해야 함
구현 후: "TB_USER 테이블 CRUD 만들어줘" → SqlTool이 자동으로 스키마 조회
```

**GitTool 미구현 실제 영향:**
```
현재: 기존 프로젝트 패턴은 RAG 문서로만 참조 가능
구현 후: Git 이력에서 유사 구현 코드 직접 검색 가능
```

---

### 4. Prompt Layer — 품질 핵심 점검

| 항목 | 현재 구현 | 문제점 | 개선 방향 |
|---|---|---|---|
| PromptBuilder | Tool별 개별 조립 | 프롬프트 일관성 없음·중복 코드 | 공통 PromptBuilder 레이어 분리 |
| ContextAssembler | RAG 전용만 존재 | Tool 결과 통합 조립 불가 | Tool 결과 + RAG + Schema 통합 |
| TemplateManager | CodeService로 구현 | Mustache 미사용·Java 문자열 직접 관리 | Mustache/Thymeleaf 템플릿 엔진 도입 검토 |

**영향 평가:**
- 공통 PromptBuilder 부재 → Tool 추가 시마다 프롬프트 조립 코드 중복 작성
- TemplateManager를 Mustache로 전환 시 템플릿 수정이 코드 재컴파일 없이 가능

---

### 5. RAG Engine — ChunkService 미구현 영향

**현재 문제:**
```
문서 전체를 하나의 Document로 저장
    → 긴 문서일수록 검색 정밀도 저하
    → 관련 없는 내용이 함께 검색됨
    → 토큰 낭비로 LLM 컨텍스트 초과 위험
```

**ChunkService 구현 후:**
```
1,000 token 단위 분할 + 200 token overlap
    → 관련 청크만 정밀 검색
    → 토큰 효율 향상
    → RAG 응답 품질 직접적 향상
```

**영향 등급: 높음** — eGovFrame 표준 문서(수백 페이지)나 소스코드 RAG 시 필수

---

### 6. Vector DB — Redis 유지 vs 전환 검토

| 기준 | Redis Vector Store | Chroma | PGVector |
|---|---|---|---|
| 현재 사용 | ✅ | ❌ | ❌ |
| 설정 복잡도 | 낮음 | 매우 낮음 | 중간 |
| 한국어 검색 성능 | 보통 | 보통 | 보통 |
| 대용량 문서 | 메모리 제약 | 디스크 저장 | 디스크 저장 |
| 트랜잭션 지원 | ❌ | ❌ | ✅ |
| 전환 필요성 | 소규모 운영 시 유지 | 개발 환경 대안 | 대용량 운영 시 검토 |

**영향 평가:** 현재 규모(eGovFrame 문서 수백 건)에서 Redis 유지 적합. 문서 수만 건 이상 시 PGVector 전환 검토.

---

### 7. CRUD Generator — Template vs LLM 역할 현황

**현재 구현 방식 (Template 중심 ✅ 올바른 방향):**
```
generateSource(layer, valuesJson)
    → Java 문자열 플레이스홀더 치환
    → 파일 저장
```

**LLM 역할 범위 (현재):**
- 테이블 분석 → 플레이스홀더 값 결정
- 생성된 코드 보정·설명

**영향 평가:** 현재 구조가 상세 설명의 "Template 중심 + LLM 보조" 권장 구조와 일치. 추가 변경 불필요.

---

### 8. LLM Runtime — 모델 라우팅 전략 점검

| 상황 | 현재 처리 | 권장 처리 |
|---|---|---|
| 민감 데이터 포함 | 수동으로 Ollama 선택 | 자동 감지 → Ollama 라우팅 |
| 복잡한 코드 생성 | GPT-4o-mini 사용 | GPT-4o 또는 Claude 사용 |
| 단순 질의 응답 | GPT-4o-mini | Ollama (비용 절감) |
| 장문 컨텍스트 | GPT-4o-mini (128K) | Claude (200K) |

**영향 평가:** ClaudeClient 미구현으로 장문 컨텍스트(코드 전체 파일 분석 등) 처리 한계 존재. GPT-4o-mini(128K)로 커버 가능하나 비용 증가.

---

### 9. 실제 처리 흐름 — 현재 구현과의 차이

| 흐름 단계 | 다이어그램 | 현재 구현 | 차이 |
|---|---|---|---|
| 1. 사용자 요청 | Claude Desktop | Claude Desktop + Web UI | ✅ |
| 2. MCP 수신 | MCP Server | Spring AI MCP 자동 처리 | ✅ |
| 3. SqlTool | 스키마 자동 조회 | ❌ 미구현 → 수동 입력 | ⚠️ |
| 4. RagSearchTool | 패턴 문서 검색 | ✅ 구현 완료 | ✅ |
| 5. PromptBuilder | 통합 조립 | Tool별 개별 조립 | 🔶 |
| 6. LLM 호출 | Ollama / GPT | Ollama + GPT-4o-mini | ✅ |
| 7. CRUD 생성 | Template 치환 | ✅ 구현 완료 | ✅ |
| 8. 출력 | Claude Desktop | Claude Desktop + Web UI | ✅ |

---

### 10. 종합 구현 영향 평가 요약

| 레이어 | 현재 완성도 | 미구현 시 실사용 영향 | 권장 조치 |
|---|---|---|---|
| Client UI | 🔶 70% | SSE 서버 미기동 시 연결 불가 | stdio 전환 또는 자동 재시작 설정 |
| MCP Layer | ✅ 95% | 없음 | 유지 |
| Tool Layer | 🔶 50% | SqlTool 없어 수동 스키마 입력 | SqlTool 우선 구현 |
| Prompt Layer | 🔶 40% | 프롬프트 품질 편차 | 공통 PromptBuilder 분리 |
| RAG Engine | 🔶 75% | 장문서 검색 정밀도 저하 | ChunkService 구현 |
| Vector DB | ✅ 90% | 대용량 시 메모리 제약 | 현 규모 유지, 추후 PGVector 검토 |
| CRUD Generator | ✅ 95% | 없음 | 유지 |
| LLM Runtime | 🔶 70% | 장문 컨텍스트 처리 한계 | ClaudeClient 추가 검토 |

**즉시 구현 권장 (실사용 영향 큰 순서):**

```
1순위: ChunkService       — RAG 품질 직접 영향
2순위: SqlTool            — CRUD 자동화 완성도
3순위: 공통 PromptBuilder — 코드 유지보수성
```
