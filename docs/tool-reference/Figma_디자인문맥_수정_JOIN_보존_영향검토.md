# Figma 디자인 문맥 `revise`·JOIN 보존 영향검토

> 작성일: 2026-09-02
> 발견 배경: 노드별 `PaintSpec` 도입 선행 분석
> 결론: `PaintSpec` 모델을 확장하기 전에 현재 `componentStyles`, `componentGeometry`, `tokens`가 수정·JOIN 처리에서 유실되는 결함을 먼저 고친다.

## 1. 검토 목적

Figma 분석으로 생성된 화면명세에는 다음 디자인 문맥이 저장된다.

- `componentStyles`: 의미 타입별 대표 색상
- `componentGeometry`: 노드별 좌표·스타일·opacity 계층
- `tokens`: 화면 전체 배경색·폰트 참고값

하지만 화면명세 수정과 JOIN 자동 해석 과정에서 `ScreenSpecification`을 다시 만들 때 이 세 필드를 생성자에
전달하지 않아 빈 값으로 초기화된다.

## 2. 직접 근거

### 2.1 화면명세 수정 경로

`ScreenSpecificationService.revise()`는 현재 명세의 디자인 참조 두 필드까지만 전달하고
`componentStyles`, `componentGeometry`, `tokens`는 전달하지 않는다.

- `src/main/java/com/krdevops/springai/service/ScreenSpecificationService.java` L128-180
- 누락 생성자 호출: L171-177

결과적으로 `reviseScreenSpecification()` 호출 뒤 Figma 색상·geometry·token이 사라진다.

### 2.2 JOIN 자동 해석 경로

`ScreenDataBindingResolver.resolve()`도 JOIN이 발견되면 새 명세를 만들면서 같은 세 필드를 누락한다.

- `src/main/java/com/krdevops/springai/service/ScreenDataBindingResolver.java` L49-75

JOIN이 없으면 원본 객체를 그대로 반환하므로 문제가 드러나지 않고, JOIN이 실제 추가되는 경우에만 디자인
문맥이 사라지는 조건부 결함이다.

## 3. 정책 결정

### revise 경로

`reviseScreenSpecification`은 미매핑 필드·JOIN·Action을 수정하는 도구다. 디자인 paint/geometry/token을
수정하는 도구가 아니므로, 제안 객체의 디자인 값이 아니라 **현재 저장된 명세의 디자인 문맥을 불변 보존**한다.

```text
revision.componentStyles  = current.componentStyles
revision.componentGeometry = current.componentGeometry
revision.tokens = current.tokens
```

이 정책은 호출자가 디자인 값을 누락하거나 임의 변조해도 승인된 원본 디자인 문맥을 보호한다.

### JOIN 경로

JOIN 해석은 데이터 바인딩만 바꾸므로 입력 명세의 디자인 문맥을 그대로 전달한다.

```text
resolved.componentStyles  = specification.componentStyles
resolved.componentGeometry = specification.componentGeometry
resolved.tokens = specification.tokens
```

## 4. 영향 범위

| 대상 | 변경 |
|---|---|
| `ScreenSpecificationService.revise()` | 현재 저장본의 디자인 3필드 전달 |
| `ScreenDataBindingResolver.resolve()` | 입력 명세의 디자인 3필드 전달 |
| 서비스 단위 테스트 | revise 보존 및 제안값 변조 무시 검증 |
| resolver 단위 테스트 | JOIN 추가 전후 디자인 문맥 동일성 검증 |
| 모델·MCP schema | 필드 추가/삭제가 없어 변경 없음 |
| formatter·auto 경로 | 변경 없음 |

## 5. 위험과 대응

| 위험 | 대응 |
|---|---|
| 수정 요청이 디자인 값을 바꾸려 해도 반영되지 않음 | Tool 책임을 필드·JOIN·Action 수정으로 제한하고 디자인 수정은 별도 도구로 분리 |
| 향후 필드 추가 때 복사 누락 재발 | `ScreenSpecification`에 명시적 복사 메서드 도입을 후속 검토하되 이번에는 최소 수정 유지 |
| 테스트가 JOIN 없는 조기 반환만 검사 | 실제 JOIN 후보를 구성해 새 명세 생성 분기를 반드시 통과 |
| 기존 MCP baseline 실패와 혼동 | schema diff 없음 확인, baseline 자동 재생성 금지 |

## 6. 결론

현재 디자인 문맥 유실은 이미 구현된 fills/strokes와 opacity 계약의 실효성을 깨뜨리는 선행 결함이다.
변경은 두 생성자 호출과 회귀 테스트에 한정되며 공개 계약을 바꾸지 않는다. 노드별 `PaintSpec` 도입 전에
우선 수정해야 한다.
