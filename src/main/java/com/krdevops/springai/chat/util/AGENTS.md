<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-06-04 | Updated: 2026-06-04 -->

# chat/util

## Purpose
채팅 기능 유틸리티 클래스 패키지. 문서 해시 계산, 프롬프트 템플릿, LLM 응답 정제,
Think 태그 파싱 등 채팅 파이프라인 전반에서 사용되는 도우미 클래스를 포함합니다.

## Key Files

| File | Description |
|------|-------------|
| `EgovDocumentHashUtil.java` | 문서 중복 감지용 SHA-256 해시 계산 유틸 |
| `EgovJsonPromptTemplates.java` | JSON 구조 응답을 유도하는 프롬프트 템플릿 상수 모음 |
| `EgovResponseCleanerUtil.java` | LLM 응답에서 불필요한 마크다운, 코드 펜스, 공백 제거 |
| `EgovThinkTagOutputConverter.java` | `<think>...</think>` 태그를 파싱하여 추론 과정과 최종 답변을 분리 |

## For AI Agents

### Working In This Directory
- `EgovThinkTagOutputConverter.java`는 Ollama의 CoT(Chain of Thought) 모델 응답 처리용
- `EgovJsonPromptTemplates.java` 수정 시 이를 참조하는 서비스 클래스의 파싱 로직과 호환성 확인

### Common Patterns
- 유틸 클래스는 `static` 메서드 또는 `@Component` 싱글톤으로 구현
- 상태 없는(stateless) 순수 함수 형태 권장

## Dependencies

### Internal
- `chat/service/impl/EgovSessionAwareChatServiceImpl.java` — 응답 정제 유틸 사용
- `chat/service/impl/EgovDocumentServiceImpl.java` — 문서 해시 유틸 사용

<!-- MANUAL: -->
