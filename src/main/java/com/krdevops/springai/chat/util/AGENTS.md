<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-08 | Updated: 2026-06-08 -->

# chat/util

## Purpose
채팅 모듈 전용 유틸리티 클래스 패키지. LLM 응답 후처리, 문서 중복 방지, JSON 프롬프트 관리를 담당합니다.

## Key Files

| File | Description |
|------|-------------|
| `EgovThinkTagOutputConverter.java` | Ollama `<think>...</think>` 태그 제거 — qwen3 모델 thinking 모드 출력 정리 |
| `EgovResponseCleanerUtil.java` | LLM 응답에서 불필요한 마크다운 아티팩트·중복 공백 제거 |
| `EgovDocumentHashUtil.java` | 문서 해시 생성 — 동일 파일 재임베딩 방지 (SHA-256 기반) |
| `EgovJsonPromptTemplates.java` | 채팅 시스템 프롬프트 상수 모음 — eGovFrame 도메인 특화 JSON 응답 유도 템플릿 |

## For AI Agents

### Working In This Directory
- `EgovThinkTagOutputConverter`: qwen3:8b 모델의 `<think>` 블록은 사용자에게 노출하지 않음
- `EgovDocumentHashUtil`: 문서 업로드 시 해시 비교 → 변경된 파일만 재임베딩
- 새 후처리 로직은 `EgovResponseCleanerUtil`에 추가하거나 별도 Util 클래스 작성

## Dependencies

### Internal
- `chat/service/impl/EgovSessionAwareChatServiceImpl.java` — 응답 후처리에 사용
- `chat/service/impl/EgovDocumentServiceImpl.java` — 문서 해시 체크에 사용

<!-- MANUAL: -->
