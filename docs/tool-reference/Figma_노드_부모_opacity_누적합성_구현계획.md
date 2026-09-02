# Figma 노드·부모 `opacity` 누적 합성 구현명세서 및 구현목록

> 기반 문서: [`Figma_노드_부모_opacity_누적합성_영향검토.md`](./Figma_노드_부모_opacity_누적합성_영향검토.md)
> 상태: 구현 완료(2026-09-02)
> 범위: claude 프롬프트의 opacity 의미·누적·우선순위 계약 보강
> 제외: 모델/MCP 변경, 대표 RGBA 선합성, auto 렌더링 변경

## 1. 요구사항 요약

1. paint RGBA alpha는 이미 `color.a × paint.opacity`를 포함한다고 명시한다.
2. `NodeGeometry.opacity`는 로컬 노드 opacity이며 null은 `1.0`이라고 명시한다.
3. 부모부터 현재 노드까지 opacity를 곱해 누적값을 계산하도록 명시한다.
4. 최종 시각 alpha는 paint alpha와 누적 node opacity를 한 번만 결합하도록 명시한다.
5. 실제 노드에는 geometry 스타일을 우선하고 componentStyles는 타입 fallback으로 사용한다.
6. 모델·MCP baseline·auto 경로를 변경하지 않는다.

## 2. 출력 계약

`ScreenSpecificationPromptFormatter`의 `componentGeometry` 안내에 다음 규칙을 포함한다.

```text
[opacity 적용 규칙]
- componentStyles/geometry의 rgba alpha에는 color.a × paint.opacity가 이미 반영되어 있습니다.
- geometry.opacity는 해당 노드의 로컬 opacity이며 null은 1.0입니다.
- cumulativeNodeOpacity = ancestor opacity × ... × current node opacity
- effectivePaintAlpha = rgba alpha × cumulativeNodeOpacity
- paint/node opacity를 rgba와 CSS opacity 양쪽에 중복 적용하지 마세요.
- 실제 노드는 componentGeometry 값을 우선하고 componentStyles는 geometry 스타일이 없을 때만 fallback으로 사용하세요.
```

예시:

```text
rgba alpha=0.5, 부모 opacity=0.8, 현재 노드 opacity=0.5
effectivePaintAlpha=0.5×0.8×0.5=0.2
```

## 3. 수정 파일

| 파일 | 변경 내용 |
|---|---|
| `src/main/java/com/krdevops/springai/service/ScreenSpecificationPromptFormatter.java` | geometry 블록 앞 opacity 계약 추가 |
| `src/test/java/com/krdevops/springai/service/ScreenSpecificationPromptFormatterTest.java` | 계약 문구·공식·우선순위 회귀 테스트 추가 |

수정하지 않는 파일:

- `FigmaDesignSpecMapper.java`
- `UiDesignSpec.java`
- `ScreenSpecification.java`
- `ScreenSpecAssembler.java`
- `src/test/resources/mcp/tool-definitions-baseline.json`

## 4. 구현 단계

1. formatter의 geometry 출력 블록에 opacity 규칙을 고정 문구로 추가한다.
2. geometry가 비어 있으면 opacity 규칙도 출력하지 않는다.
3. geometry가 있으면 local/null/cumulative/effective/중복 금지/geometry 우선 규칙을 모두 출력한다.
4. 기존 geometry JSON 직렬화와 page 출력 순서는 유지한다.
5. formatter 단위 테스트를 추가한다.
6. 관련 매퍼·Assembler·formatter 테스트를 실행한다.
7. MCP baseline diff가 없음을 확인한다.
8. 전체 테스트 결과를 기록하고 문서를 구현 완료로 갱신한다.

## 5. 인수 조건

- [x] geometry가 있으면 `rgba alpha = color.a × paint.opacity` 의미가 출력된다.
- [x] geometry opacity가 로컬값이고 null은 `1.0`이라는 규칙이 출력된다.
- [x] 누적 node opacity 공식이 출력된다.
- [x] effective paint alpha 공식이 출력된다.
- [x] opacity 중복 적용 금지가 출력된다.
- [x] 실제 노드에는 geometry 우선, componentStyles fallback 규칙이 출력된다.
- [x] geometry가 비어 있으면 opacity 규칙이 출력되지 않는다.
- [x] geometry JSON 내용과 기존 프롬프트 구조가 유지된다.
- [x] 공개 모델과 MCP baseline에 diff가 없다.
- [x] auto 경로 코드가 변경되지 않는다.

## 6. 검증 명령

```bash
./gradlew test --tests "com.krdevops.springai.service.ScreenSpecificationPromptFormatterTest" --console=plain
./gradlew test --tests "com.krdevops.springai.service.FigmaDesignSpecMapperTest" \
  --tests "com.krdevops.springai.service.ScreenSpecAssemblerTest" \
  --tests "com.krdevops.springai.service.ScreenSpecificationPromptFormatterTest" --console=plain
git diff -- src/test/resources/mcp/tool-definitions-baseline.json
```

## 7. 후속 단계

1. 노드별 `PaintSpec` 모델과 의미 타입 fallback 분리
2. 그라데이션 stop/transform 보존 및 CSS 변환
3. IMAGE fill과 기존 asset 다운로드 경로 연결

## 8. 구현 및 검증 결과

- geometry가 있을 때만 opacity 적용 규칙을 출력하도록 formatter를 보강했다.
- paint alpha 선반영, 로컬/null opacity, 누적/effective 공식, 중복 적용 금지, geometry 우선순위를 명시했다.
- geometry가 없을 때 규칙이 출력되지 않는 부정 테스트를 포함해 formatter 테스트를 보강했다.
- 매퍼·Assembler·formatter 관련 테스트 49개: 통과
- 전체 테스트: 1,980개 중 기존 `McpToolDefinitionSnapshotTest` baseline 불일치 1건만 실패, 14개 skipped
- 공개 모델, MCP baseline, auto 경로: 변경 없음
- 아키텍처 검증: 승인(`CLEAR`)
