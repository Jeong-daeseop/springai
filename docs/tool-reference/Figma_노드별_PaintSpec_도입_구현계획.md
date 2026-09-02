# Figma 노드별 `PaintSpec` 도입 구현명세서 및 구현목록

> 기반 문서: [`Figma_노드별_PaintSpec_도입_영향검토.md`](./Figma_노드별_PaintSpec_도입_영향검토.md)
> 상태: Phase 3 진행 — 공용 formatter를 통한 전체 생성 경로 연결 검증 완료
> 범위: 노드별 paint 순서·메타데이터 보존, SOLID 원시 색상 지원
> 제외: 신규 IMAGE API 연결, v2 Design IR 확장, auto 렌더링

## 1. 요구사항

1. 각 `NodeGeometry`가 fills/strokes 배열을 원래 순서로 보존한다.
2. SOLID는 `type`, `visible`, `opacity`, 원시 RGBA를 보존한다.
3. 미지원 paint도 최소 type/visible/opacity를 보존한다.
4. 기존 대표 색상과 기존 생성자·JSON을 호환 유지한다.
5. 스타일이 다른 반복 형제는 축약하지 않는다.
6. revise·JOIN·검증·저장/조회 후 paint 배열이 유지된다.
7. MCP schema 변경은 optional fills/strokes와 PaintSpec 정의로 제한한다.

## 2. Phase 0 — 기존 MCP baseline drift 해소 (완료)

PaintSpec 코드 변경 전에 수행한다.

1. 저장 baseline과 현재 런타임 Tool schema를 정규화해 도구별 diff를 생성했다.
2. 변경은 `validateGeneratedCode`, `validateGeneratedCodeDirectory` 설명 2건으로 한정됨을 확인했다.
3. `CodeValidatorTool`의 실제 `@Tool` 설명과 대조해 기존 Thymeleaf 검증 기능 확장에 따른 승인 변경으로 분류했다.
4. 승인 변경만 baseline에 반영했다.
5. `McpToolDefinitionSnapshotTest` 단독 실행이 통과했다.

검증 근거:

- baseline diff는 두 Tool의 설명만 변경되며 Tool 이름·입력 schema·개수는 변경되지 않았다.
- `./gradlew test --tests "com.krdevops.springai.config.McpToolDefinitionSnapshotTest" --console=plain` 성공.

중단 조건:

- 의도를 확인할 수 없는 계약 변경이 하나라도 있으면 baseline을 갱신하지 않고 사용자 판단을 요청한다.

## 3. 데이터 모델

### 3.1 `PaintSpec`

`UiDesignSpec` 내부 record로 추가한다.

```java
public record PaintSpec(
        String type,
        boolean visible,
        double opacity,
        @Nullable String color) {
    public PaintSpec {
        type = normalizePaintType(type);
        opacity = clamp(opacity);
    }
}
```

`color`는 SOLID의 원시 color alpha까지만 포함한다. paint opacity와 node opacity는 별도 필드로 유지한다.

### 3.2 `NodeGeometry`

canonical 생성자에 optional `fills`, `strokes`를 추가하고 compact constructor에서 불변 리스트로 정규화한다.
현재 13인자 생성자 시그니처는 compat 생성자로 유지해 기존 10개 호출부를 변경하지 않는다.

## 4. 매퍼

`FigmaDesignSpecMapper.buildGeometryTree()`에서 다음을 수행한다.

1. `paintSpecs(node.path("fills"), uncertainties, nodeId, "fills")`
2. `paintSpecs(node.path("strokes"), uncertainties, nodeId, "strokes")`
3. 배열 순서 유지
4. 최대 16개 제한
5. 알려지지 않은 type은 `UNKNOWN`으로 보존하고 uncertainty 추가
6. 기존 `firstSolidPaint()` 결과도 그대로 유지

원시 SOLID RGBA 변환은 paint opacity를 곱하지 않는 별도 헬퍼를 사용한다.

## 5. 반복 축약

`isSameShape()`에 다음 동등성 조건을 추가한다.

```text
fills, strokes, opacity, cornerRadius, textStyle
```

따라서 크기·이름이 같더라도 paint가 다른 노드는 대표 1개로 축약하지 않는다.

## 6. 프롬프트 계약

geometry 안내를 다음 우선순위로 갱신한다.

```text
fills/strokes 배열
→ geometry backgroundColor/borderColor
→ componentStyles 대표 색상
```

- PaintSpec.color alpha에는 color.a만 포함
- PaintSpec.opacity와 누적 node opacity는 각각 한 번만 적용
- 기존 backgroundColor/borderColor alpha에는 color.a × paint.opacity가 이미 반영됨
- gradient/image는 메타데이터만 보존됐으며 아직 CSS 변환 대상이 아님

## 7. 수정 파일

| 파일 | 작업 |
|---|---|
| `model/design/UiDesignSpec.java` | PaintSpec, NodeGeometry fills/strokes, compat 생성자 |
| `service/FigmaDesignSpecMapper.java` | 순서형 paint 파싱, 제한·uncertainty, 축약 조건 |
| `service/ScreenSpecificationPromptFormatter.java` | 우선순위·alpha 계약 갱신 |
| 관련 model/mapper/formatter/service/repository 테스트 | 호환·보존·round-trip 회귀 |
| `tool-definitions-baseline.json` | Phase 0 green 이후 PaintSpec 한정 diff 검토·갱신 |

## 8. 구현목록

1. Phase 0 baseline drift를 해소한다.
2. `PaintSpec`과 NodeGeometry optional lists를 추가한다.
3. 구형 생성자와 과거 JSON 호환 테스트를 추가한다.
4. 순서형 paint 파서와 16개 제한을 구현한다.
5. 복수 SOLID·비가시·gradient/image·UNKNOWN fixture를 추가한다.
6. 반복 형제 스타일 동등성을 강화한다.
7. formatter 우선순위와 alpha 계약을 갱신한다.
8. revise·JOIN·withValidation·withDesignContext 보존을 검증한다.
9. DB repository JSON round-trip을 검증한다.
10. CRUD·Board·Master-detail 공용 formatter 소비를 검증한다.
11. MCP schema diff가 예상 범위만 포함하는지 검토 후 baseline을 갱신한다.
12. 전체 테스트와 실제 Figma fixture 검증 결과를 기록한다.

## 9. 인수 조건

- [x] Phase 0 전후 snapshot 테스트가 green이다.
- [x] 복수 fills/strokes가 입력 순서대로 보존된다.
- [x] color.a와 paint opacity가 별도 값으로 보존된다.
- [x] gradient/image-only 노드가 paint 없음과 구분된다.
- [ ] 기존 backgroundColor/borderColor 결과가 바뀌지 않는다.
- [ ] 구형 NodeGeometry 생성자와 과거 JSON을 정상 처리한다.
- [ ] paint가 다른 반복 형제를 축약하지 않는다.
- [ ] revise·JOIN·검증·디자인 참조 갱신 후 paint가 유지된다.
- [x] DB 저장/재조회 후 paint 순서와 값이 동일하다.
- [x] formatter가 신규→호환→대표 fallback 순서를 명시한다.
- [x] MCP diff가 optional fills/strokes와 PaintSpec 정의로 제한된다.
- [x] gradient stop과 handle position을 순서대로 보존한다.
- [x] IMAGE paint의 `imageRef`와 `scaleMode`를 보존한다.
- [x] IMAGE 노드가 기존 `imageNodeIds → FigmaAssetDownloadService` 경로로 전달된다.
- [x] gradient stop/handle 메타데이터가 CSS gradient 값으로 변환된다.
- [x] 생성 프롬프트에 노드별 `gradientCssHints`가 포함된다.
- [x] CRUD·Master-detail·Board 생성 경로가 공용 formatter의 gradient 힌트를 사용한다.
- [ ] auto 생성 경로의 파일 결과가 변하지 않는다.

## 10. 검증

```bash
./gradlew test --tests "com.krdevops.springai.config.McpToolDefinitionSnapshotTest" --console=plain
./gradlew test --tests "com.krdevops.springai.service.FigmaDesignSpecMapperTest" \
  --tests "com.krdevops.springai.service.ScreenSpecificationPromptFormatterTest" \
  --tests "com.krdevops.springai.service.ScreenSpecificationServiceTest" \
  --tests "com.krdevops.springai.service.ScreenDataBindingResolverTest" --console=plain
./gradlew test --console=plain
```

## 11. 후속 단계

1. gradient stop과 transform 모델 및 CSS 변환
2. IMAGE fill과 기존 asset 다운로드 경로 연결
3. v2 Design IR의 노드별 visual style 확장
