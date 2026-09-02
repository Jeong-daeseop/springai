# Figma v2 `VisualStyle` 확장 구현계획

> 상태: 설계 완료 — 구현 대기

## 1. 구현 순서

1. v2 전용 `VisualStyle`, `VisualPaint`, `GradientStop`, `GradientHandle` 모델 추가
2. `SemanticNode`에 `@Nullable VisualStyle` 추가 및 구형 생성자 유지
3. `FigmaUiDesignSpecV2Mapper`에서 fills/strokes/style 추출
4. unsupported paint type과 image metadata를 uncertainty/assessment에 반영
5. v2 JSON round-trip 및 legacy JSON 테스트 추가
6. v2→v1 projection이 기존 결과를 유지하는지 회귀 테스트
7. MCP snapshot diff가 optional visualStyle로 제한되는지 확인
8. 전체 테스트 실행

## 2. 완료 조건

- v2 노드별 fills/strokes 순서 보존
- node opacity와 paint opacity 분리
- gradient/image 메타데이터 손실 없음
- 기존 v2 생성자·JSON·projection 호환
- MCP schema의 신규 필드는 optional
- 전체 테스트 통과

## 3. 중단 조건

기존 v2 JSON 역직렬화가 깨지거나, `visualStyle`이 MCP schema에서 required로 생성되면 구현을 중단하고 계약을 재검토한다.
