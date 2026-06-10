<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# chat/config/rag

## Purpose
RAG 파이프라인 커스텀 컴포넌트 패키지. Spring AI의 RAG 기본 동작을 eGovFrame 도메인에 맞게 확장합니다.

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `transformers/` | 쿼리 변환기 — 검색 전 쿼리 전처리 (see `transformers/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- 새 RAG 컴포넌트(재랭킹, 문서 필터 등) 추가 시 이 패키지 하위에 배치
- 구성된 컴포넌트는 `EgovRagConfig.java`에서 빈으로 등록

<!-- MANUAL: -->
