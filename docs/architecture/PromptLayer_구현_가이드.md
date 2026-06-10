# Prompt Layer 구현 가이드

작성일: 2026-05-21

---

## 전체 관계 다이어그램

```
                ┌──────────────────┐
                │ TemplateManager  │
                └────────┬─────────┘
                         │
                         ▼
┌─────────────┐   ┌───────────────┐
│ RAG Result  │──▶│PromptBuilder  │
└─────────────┘   └──────┬────────┘
                          │
┌─────────────┐           ▼
│ DB Schema   │──▶ContextAssembler
└─────────────┘           │
                          ▼
                     Final Prompt
                          │
                          ▼
                         LLM
```

---

## 컴포넌트별 역할

### TemplateManager

CRUD 소스 생성의 뼈대를 담당합니다.
도메인·레이어별 코드 템플릿을 관리하며, 플레이스홀더를 치환하여 실제 코드를 생성합니다.

```
controller.mustache  →  ${domain}Controller.java
service.mustache     →  ${domain}Service.java
mapper.mustache      →  ${domain}Mapper.xml
```

**현재 구현:** `CodeService` (Java 문자열 플레이스홀더 치환 방식)

---

### PromptBuilder

사용자 요청 + RAG 결과를 받아 LLM에게 전달할 프롬프트 초안을 생성합니다.

```
입력:
  - 사용자 요청  ("회원관리 CRUD 생성해줘")
  - RAG Result   (기존 패턴 참조 문서)
  - Template     (TemplateManager에서 선택된 템플릿)

출력:
  - LLM 입력용 프롬프트 초안
```

**현재 구현:** Tool별 개별 조립 (공통 PromptBuilder 미분리)

---

### ContextAssembler

PromptBuilder 초안 + DB Schema를 통합하여 최종 프롬프트를 완성합니다.

```
입력:
  - PromptBuilder 초안
  - DB Schema     (테이블 컬럼·PK·타입 정보)

처리:
  - 중복 내용 제거
  - 토큰 길이 제한 내 조정
  - 중요 컨텍스트 우선 배치

출력:
  - Final Prompt → LLM 호출
```

**현재 구현:** `buildRagContext()` — RAG 전용 부분 구현

---

## 현실적인 구현 순서

eGovFrame CRUD 자동생성 목적 기준 권장 순서입니다.

### 1단계 — TemplateManager 안정화

**가장 먼저 안정화해야 할 컴포넌트.**

전체 CRUD 생성 품질의 기반이 되며, 이후 PromptBuilder·ContextAssembler의 출력 품질도 TemplateManager 완성도에 의존합니다.

```
목표: 10개 레이어 템플릿 품질 확보
  - Controller / Service / ServiceImpl
  - Mapper(interface) / MapperXml
  - VO / jspList / jspDetail / jspRegist / jspUpdt
```

체크포인트:
- [ ] 플레이스홀더 치환 정확성 검증
- [ ] eGovFrame 표준 준수 여부 확인
- [ ] 생성된 코드 컴파일·실행 검증

---

### 2단계 — PromptBuilder 구현

공통 PromptBuilder를 분리하여 Tool별 중복 코드를 제거합니다.

```
목표: 공통 프롬프트 조립 레이어 구현
  - 사용자 요청 파싱
  - RAG Result 주입
  - Template 선택 로직
```

체크포인트:
- [ ] 프롬프트 일관성 검증
- [ ] 레이어별 프롬프트 템플릿 분리
- [ ] Tool별 PromptBuilder 통합

---

### 3단계 — Ollama 연결

로컬 LLM으로 기본 응답 품질을 검증합니다.

```
목표: PromptBuilder 출력 → Ollama → 코드 생성 검증
  - 생성 코드 품질 확인
  - 프롬프트 반복 튜닝
  - 응답 속도·안정성 확인
```

체크포인트:
- [ ] 기본 CRUD 생성 성공 확인
- [ ] 프롬프트 품질 문제 식별 및 수정
- [ ] 생성 코드 eGovFrame 표준 준수 여부

---

### 4단계 — DB Schema Tool (SqlTool)

자동 스키마 조회로 수동 입력 의존도를 제거합니다.

```
목표: 테이블명 입력 → 스키마 자동 조회 → ContextAssembler 주입
  - INFORMATION_SCHEMA 조회
  - 컬럼·PK·타입·NULL 여부 파싱
  - ContextAssembler 연동
```

체크포인트:
- [ ] 스키마 조회 정확성
- [ ] 복합 PK·FK 처리
- [ ] ContextAssembler 주입 형식 정의

---

### 5단계 — RAG 연동

기존 패턴 문서를 검색하여 프롬프트 품질을 향상시킵니다.

```
목표: 유사 패턴 문서 검색 → PromptBuilder 주입
  - eGovFrame 표준 문서 임베딩
  - 기존 생성 이력 RAG 저장
  - 유사도 임계값 튜닝
```

체크포인트:
- [ ] RAG 검색 결과 관련성 확인
- [ ] 프롬프트 품질 향상 여부 측정
- [ ] 토큰 초과 방지 (길이 제한 조정)

---

### 6단계 — ContextAssembler 고도화

전체 컨텍스트를 통합·최적화하는 레이어를 완성합니다.

```
목표: RAG + Schema + 사용자 요청 통합 최적화
  - 중복 컨텍스트 제거
  - 토큰 예산 관리
  - 중요도 기반 컨텍스트 우선순위
  - 멀티 Tool 결과 통합
```

체크포인트:
- [ ] 최종 프롬프트 토큰 수 모니터링
- [ ] LLM 응답 품질 비교 (고도화 전·후)
- [ ] 다양한 도메인·테이블 적용 검증

---

## 단계별 의존 관계

```
[1] TemplateManager
       │ 템플릿 제공
       ▼
[2] PromptBuilder ◀── RAG Result (5단계 이후)
       │ 프롬프트 초안
       ▼
[3] Ollama 연결 (검증)
       │
       ▼
[4] DB Schema Tool ──▶ [6] ContextAssembler
       │                        │
       └──────────────────────▶ Final Prompt
                                        │
                                        ▼
                                       LLM
```

---

## eGovFrame CRUD 자동생성 적용 포인트

| 단계 | Prompt Layer 역할 | 현재 구현 상태 |
|---|---|---|
| 테이블 선택 | SqlTool → ContextAssembler 스키마 주입 | ❌ SqlTool 미구현 |
| 패턴 참조 | RagSearchTool → PromptBuilder RAG 주입 | ✅ 부분 구현 |
| 코드 생성 | TemplateManager → 플레이스홀더 치환 | ✅ 구현 완료 |
| 품질 보정 | LLM → 생성 코드 검토·수정 | ✅ 구현 완료 |
| 컨텍스트 통합 | ContextAssembler → 전체 조합 | 🔶 RAG 전용만 구현 |

---

## 핵심 원칙

> **TemplateManager 가 가장 먼저 안정화되어야 전체 품질이 좋아집니다.**

- Template 중심으로 코드 일관성을 확보한 뒤 LLM을 보조 역할로 활용
- LLM이 모든 코드를 직접 생성하게 하면 일관성·재현성이 깨짐
- ContextAssembler 고도화는 TemplateManager·PromptBuilder 안정화 이후에 진행
