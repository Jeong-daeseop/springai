# Figma 디자인 문맥 `revise`·JOIN 보존 구현명세서 및 구현목록

> 기반 문서: [`Figma_디자인문맥_수정_JOIN_보존_영향검토.md`](./Figma_디자인문맥_수정_JOIN_보존_영향검토.md)
> 상태: 구현 완료(2026-09-02)
> 범위: 기존 디자인 3필드의 불변/pass-through 보존
> 제외: 모델·MCP 변경, PaintSpec 도입, 디자인 수정 기능

## 1. 요구사항

1. `revise()`는 현재 저장된 `componentStyles`, `componentGeometry`, `tokens`를 새 revision에 보존한다.
2. revise 요청에 다른 디자인 값이 들어와도 이를 채택하지 않는다.
3. `ScreenDataBindingResolver.resolve()`가 JOIN 명세를 새로 만들 때 입력 디자인 3필드를 보존한다.
4. JOIN이 없는 기존 조기 반환 동작은 유지한다.
5. 상태·버전·페이지·dataSources 등 기존 수정/해석 동작은 바꾸지 않는다.
6. 공개 모델, MCP schema, formatter, auto 템플릿은 변경하지 않는다.

## 2. 구현명세

### 2.1 revise 보존

`ScreenSpecificationService.revise()`의 생성자 마지막 인자에 현재 저장본의 값을 전달한다.

```java
current.uiDesignSpecReference(),
current.designSystemSnapshotReference(),
current.componentStyles(),
current.componentGeometry(),
current.tokens()
```

`proposed`의 디자인 필드를 사용하지 않는다. 이 도구의 수정 허용 범위가 미매핑 필드·JOIN·Action이기 때문이다.

### 2.2 JOIN 보존

`ScreenDataBindingResolver.resolve()`의 생성자 마지막 인자에 입력 명세의 값을 전달한다.

```java
specification.uiDesignSpecReference(),
specification.designSystemSnapshotReference(),
specification.componentStyles(),
specification.componentGeometry(),
specification.tokens()
```

## 3. 수정 파일

| 파일 | 작업 |
|---|---|
| `src/main/java/com/krdevops/springai/service/ScreenSpecificationService.java` | revise 시 현재 디자인 문맥 보존 |
| `src/main/java/com/krdevops/springai/service/ScreenDataBindingResolver.java` | JOIN 해석 시 입력 디자인 문맥 보존 |
| `src/test/java/com/krdevops/springai/service/ScreenSpecificationServiceTest.java` | revise 회귀 테스트 |
| `src/test/java/com/krdevops/springai/service/ScreenDataBindingResolverTest.java` | JOIN 생성 분기 회귀 테스트 |

## 4. 구현목록

1. 기존 service/resolver 테스트 fixture와 mock 구성을 확인한다.
2. revise 생성자 호출에 현재 디자인 3필드를 추가한다.
3. JOIN resolver 생성자 호출에 입력 디자인 3필드를 추가한다.
4. revise가 현재 디자인 값을 동일 객체 내용으로 보존하는 테스트를 추가한다.
5. proposed 디자인 값이 달라도 현재 저장값을 유지하는 테스트를 추가한다.
6. 실제 JOIN이 추가되는 resolver 테스트에서 세 필드가 동일하게 유지되는지 검증한다.
7. JOIN 없는 경우 원본 인스턴스를 반환하는 기존 동작을 검증한다.
8. 서비스·resolver·formatter 관련 테스트를 실행한다.
9. MCP baseline diff 없음과 전체 테스트 결과를 기록한다.
10. 문서 상태를 구현 완료로 갱신한다.

## 5. 인수 조건

- [x] revise 후 `componentStyles`가 현재 저장본과 동일하다.
- [x] revise 후 `componentGeometry`가 현재 저장본과 동일하다.
- [x] revise 후 `tokens`가 현재 저장본과 동일하다.
- [x] proposed에 다른 디자인 값을 넣어도 현재 저장본 값이 유지된다.
- [x] JOIN이 추가된 명세에서 디자인 3필드가 입력과 동일하다.
- [x] JOIN이 없으면 기존처럼 입력 명세 인스턴스를 그대로 반환한다.
- [x] revision version 증가와 DRAFT→검증 상태 흐름이 유지된다.
- [x] JOIN dataSources/pages 변환 동작이 유지된다.
- [x] 공개 모델 및 MCP baseline diff가 없다.
- [x] 기존 fills/strokes·opacity formatter 테스트가 통과한다.

## 6. 검증 명령

```bash
./gradlew test --tests "com.krdevops.springai.service.ScreenSpecificationServiceTest" \
  --tests "com.krdevops.springai.service.ScreenDataBindingResolverTest" \
  --tests "com.krdevops.springai.service.ScreenSpecificationPromptFormatterTest" --console=plain
git diff -- src/test/resources/mcp/tool-definitions-baseline.json
./gradlew test --console=plain
```

## 7. 후속 단계

이 보존 결함을 수정한 뒤 노드별 `PaintSpec` 모델 설계로 복귀한다. 그 단계에서는 기존
`componentStyles`/`NodeGeometry.backgroundColor` 호환 유지, `NodeGeometry.fills/strokes` 순서 보존,
MCP optional schema 전략을 별도 합의한다.

## 8. 구현 및 검증 결과

- `revise()`가 현재 저장본의 디자인 3필드를 보존하고 proposed 디자인값을 무시하도록 수정했다.
- 실제 JOIN이 추가되는 resolver 경로가 입력 디자인 3필드를 그대로 전달하도록 수정했다.
- JOIN 없음 원본 반환, JOIN_COLUMN 변환, proposed 변조 방지 회귀 테스트를 추가했다.
- 서비스·resolver·formatter 관련 테스트 13개: 통과
- 전체 테스트: 1,983개 중 기존 `McpToolDefinitionSnapshotTest` baseline 불일치 1건만 실패, 14개 skipped
- 공개 모델 및 MCP baseline: 변경 없음
- 아키텍처 검증: 승인
