# Figma v2 `VisualStyle` 확장 영향검토

## 1. 현재 상태

`UiDesignSpecV2.SemanticNode`는 geometry·layoutConstraints·의미 추론을 보존하지만 Figma paint를 보존하지 않는다.
v1 `PaintSpec`을 직접 삽입하면 두 IR의 책임과 MCP 계약이 결합된다.

## 2. 권장 방향

v2 전용 선택적 `VisualStyle`을 `SemanticNode`에 추가한다.

- `opacity`: 노드 로컬 opacity
- `fills`, `strokes`: 순서형 v2 paint 목록
- `borderRadius`: 선택적 모서리 반경
- `text`: 선택적 폰트·크기·굵기 정보
- paint는 `type`, `visible`, `opacity`, `color`, gradient 상세, image 메타데이터를 보존

기존 `SemanticNode` 생성자와 과거 v2 JSON은 호환 생성자·선택적 필드로 유지한다.

## 3. 영향

| 영역 | 영향 |
|---|---|
| v2 모델 | 선택적 `visualStyle`과 하위 paint 타입 추가 |
| v2 Figma mapper | 원본 node의 fills/strokes/style 추출 |
| v2→v1 projection | 기존처럼 명시적 projection에서만 대표 색상·geometry를 선택 |
| MCP schema | `SemanticNode.visualStyle`가 optional로 추가 |
| persistence | JSON round-trip 및 구형 payload 호환 필요 |
| renderability | gradient/image를 보존하되 지원 수준은 assessment에 기록 |

## 4. 금지 사항

- v2 schemaVersion을 조용히 바꾸지 않는다.
- v1 `PaintSpec` record를 v2 public contract에 직접 노출하지 않는다.
- unsupported paint를 빈 목록으로 바꿔 원본 존재를 잃지 않는다.

## 5. 구현 검증 결과

v2 MCP baseline과 `SemanticNode` 생성자 호출부를 분리 검토했다. `visualStyle`은 선택적 필드로 추가되었고,
v1 projection 결과·기존 생성자·JSON round-trip 회귀 테스트를 통과했다.
