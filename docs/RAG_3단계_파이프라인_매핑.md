# RAG 3단계 파이프라인 — 프로젝트 매핑

> 작성일: 2026-06-04  
> 대상: Spring AI 2.0.0-M6 / Spring Boot 4.0.6

---

## RAG 개요

**RAG(Retrieval-Augmented Generation)** 는 검색(Retrieval)과 생성(Generation) 두 기능의 결합입니다.  
LLM이 학습하지 않은 도메인 지식(eGovFrame 5.0 교재)을 외부 DB에서 실시간으로 꺼내 LLM에게 제공함으로써 정확도를 높입니다.

---

## 3단계 파이프라인 매핑

### 🟢 1단계 — 질문 (Query)

> 사용자가 질문을 입력하면, AI 시스템이 이 질의의 의미를 분석합니다.

| 개념 | 구현체 | 파일 |
|------|--------|------|
| 질문 입력 | 웹 UI에서 텍스트 입력 → HTTP POST | `chat.html` → `EgovDocumentController` |
| 의미 분석 | 멀티턴 불완전 질문을 독립 문장으로 재작성 | `EgovCompressionQueryTransformer` |

**예시:**
```
사용자: "그거 어떻게 구현해?"   (이전 대화 맥락: ServiceImpl 작성 중)
           ↓ EgovCompressionQueryTransformer (Ollama LLM 호출 #1)
재작성: "eGovFrame ServiceImpl 구현 방법은?"
```

> 히스토리가 없는 첫 질문은 재작성 없이 원본 그대로 사용

---

### 🔵 2단계 — 검색 및 추출 (Retrieve)

> 질문과 관련된 내용을 사전에 구축해 둔 벡터 DB에서 찾아냅니다.

| 개념 | 구현체 | 파일 |
|------|--------|------|
| 외부 데이터베이스 | Redis VectorStore (54개 문서 사전 저장) | `VectorStoreConfig` |
| 텍스트 청크 분할 | 700자 단위 / 100자 overlap | `ChunkService` |
| 의미 임베딩 | `ko-sroberta-multitask` ONNX → 768차원 벡터 | `TransformersEmbeddingModel` |
| 유사도 매칭 | 코사인 유사도, 임계값 0.60 이상만 선택 | `EgovRagConfig` |
| top-k 선택 | 최대 5개 청크 반환 | `EgovRagConfig` |
| 문서 타입 필터 | `type == 'document'` (소스코드·이력 제외) | `EgovRagConfig` |
| 실행 주체 | `QuestionAnswerAdvisor` (Spring AI 내장) | `EgovSessionAwareChatServiceImpl` |

**검색 흐름:**
```
재작성된 질문
    ↓ ko-sroberta-multitask 임베딩
[0.82, 0.14, -0.33, ... 768차원 벡터]
    ↓ Redis 코사인 유사도 계산
    ↓ type == 'document' 필터 적용
유사도 0.60 이상 청크 최대 5개 선택
```

**사전 임베딩 구축 흐름 (서버 시작 / 재인덱싱 시):**
```
PDF/MD 파일 54개
    → EgovDocumentServiceImpl  (변경 감지 — 해시 비교)
    → ChunkService             (700자 분할)
    → ko-sroberta-multitask    (벡터 변환)
    → Redis VectorStore 저장
       metadata: { type, docId, chunkIdx, total }
```

---

### 🔴 3단계 — 답변 생성 (Generate)

> 검색된 문맥(Context)과 사용자의 질문을 LLM에게 함께 전달하여 자연어 답변을 생성합니다.

| 개념 | 구현체 | 파일 |
|------|--------|------|
| 시스템 프롬프트 | "eGovFrame 전문가, 참고 문서 기반 답변" 지시 | `EgovPromptBuilder.ragSystemPrompt()` |
| 문맥(Context) 주입 | 검색된 청크 5개를 user message에 자동 추가 | `QuestionAnswerAdvisor` |
| 대화 이력 추가 | 세션별 최근 8개 메시지 Redis에서 로드 | `MessageChatMemoryAdvisor` |
| LLM | Ollama `qwen2.5-coder:7b` (로컬 실행) | `application.yaml` |
| 응답 방식 | SSE 스트리밍 (실시간 토큰 단위 출력) | `EgovSessionAwareChatServiceImpl` |

**LLM이 받는 최종 입력 구조:**
```
┌─ 시스템 프롬프트 ─────────────────────────────────────┐
│  당신은 eGovFrame 5.x 전문 AI 어시스턴트입니다.        │
│  검색된 참고 문서를 바탕으로 답변하세요.               │
└──────────────────────────────────────────────────────┘
┌─ User Message ───────────────────────────────────────┐
│  [대화 이력 최근 8개]                                 │
│                                                      │
│  [검색된 교재 청크 최대 5개]                          │
│    - 교재 3차시 p.15: ServiceImpl 작성법...           │
│    - eGovFrame 가이드: @Service 어노테이션...         │
│                                                      │
│  [현재 질문]                                         │
│    eGovFrame ServiceImpl 구현 방법은?                │
└──────────────────────────────────────────────────────┘
                    ↓ Ollama qwen2.5-coder:7b
┌─ 스트리밍 응답 ───────────────────────────────────────┐
│  eGovFrame ServiceImpl은 EgovAbstractServiceImpl을   │
│  상속받아 구현합니다. 교재 기준으로...                │
└──────────────────────────────────────────────────────┘
```

---

## 전체 파이프라인 요약

```
🟢 1단계 질문              🔵 2단계 검색                🔴 3단계 생성
──────────────            ──────────────────           ──────────────────
chat.html                 Redis VectorStore            EgovPromptBuilder
      ↓                   (54개 문서 사전 저장)                ↓
EgovCompression   →       ko-sroberta 임베딩    →      QuestionAnswerAdvisor
QueryTransformer          유사도 0.60 필터             (청크 → user message)
(질문 재작성)              top-5 청크 선택                      ↓
                                                   MessageChatMemoryAdvisor
                                                   (이력 8개 추가)
                                                           ↓
                                                   Ollama qwen2.5-coder:7b
                                                   (SSE 스트리밍 답변)
```

---

## RAG 유무 비교

| 질문 예시 | RAG 없음 | RAG 있음 |
|----------|----------|----------|
| "eGovFrame 로그인 처리 방법" | LLM 일반 지식으로 추측 | 교재 내 실제 구현 기반 답변 |
| "EgovUserDetailsHelper 사용법" | 틀린 답변 가능성 높음 | 교재에서 검색 후 정확한 답변 |
| 할루시네이션 가능성 | 높음 | 낮음 (실제 문서 기반) |

---

## 현재 한계 및 개선 예정 항목

| 항목 | 현황 | 개선 방향 |
|------|------|----------|
| 청크 단절 | 700자 고정 분할 — 의미 단위 중간 끊김 가능 | PDF 페이지 단위, MD `##` 섹션 단위 분리 청킹 |
| 출처 미표시 | 어떤 교재 몇 페이지인지 사용자에게 미표시 | `RETRIEVED_DOCUMENTS` 메타데이터 활용 |
| 구 청크 누적 | 파일 수정 시 이전 청크 미삭제 | 재임베딩 전 기존 청크 삭제 로직 추가 |
| 검색 품질 | 짧은 질문의 임베딩 벡터 품질 낮음 | HyDE — 가상 답변 생성 후 임베딩 |
