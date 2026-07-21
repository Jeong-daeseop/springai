# ADR-001 전용 Figma Plugin 출력 경로

- 상태: 승인
- 결정일: 2026-07-21

Release 1 제품 실행 경로는 대화형 Figma MCP가 아니라 전용 `jsp-to-figma-plugin`으로 구현한다.
Figma MCP는 로그인된 AI 세션에 종속되므로 무인 실행, 동일 입력의 결정론적 재생성, package/schema 버전 고정과
실패 시 임시 노드 정리를 애플리케이션 계약으로 보장하기 어렵다. Figma MCP는 개발·검수 비교 도구로만 사용한다.
