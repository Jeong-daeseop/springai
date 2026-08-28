# 픽셀재현(claude 경로) 2차 구현 — 반응형 회귀 검증 구현계획

> [`Figma_픽셀재현_제외범위_2차구현_영향검토.md`](./Figma_픽셀재현_제외범위_2차구현_영향검토.md)에서
> 최우선 순위로 지목된 "트랙 C — 실제 여러 viewport 렌더링 검증"을 실제 구현 가능한 수준까지
> 코드로 확인한 뒤 작성한 구현명세서 + 구현목록이다.
>
> **현재 상태**: 옵션 B로 확정, Phase 0~3 전부 완료. `./gradlew test` 전체 통과(0 failures) 확인 완료.

---

## 1. 배경 및 목적

`Figma_픽셀재현_제외범위_구현계획.md` 트랙 C는 claude 경로 프롬프트에 반응형 가드레일 **문구**만
추가했다. 그러나 Claude가 그 문구를 실제로 지켰는지 확인할 자동화된 방법이 없다는 게 잔여
리스크로 남아 있었고, 영향검토 문서는 이를 "중~높음, 최우선 처리 대상"으로 지목했다.

이번 문서는 그 검증 공백을 메우는 구체적 설계를 다룬다.

### 설계 원칙
- **기존 인프라 재사용**: 신규 스크린샷/픽셀 diff 인프라를 만들지 않는다. `captureWebPageMultiViewport()`가
  이미 만들어내는 `RenderedDesignBundle.breakpointObservations()`를 그대로 소비한다.
- **claude 경로 전용 부가 기능**: 이 Tool은 화면 생성 파이프라인(`buildFullCrudPrompt` 등)에
  자동으로 끼워 넣지 않는다. 생성 완료 후 사람(또는 Claude)이 명시적으로 호출하는 **별도 검증
  Tool**로 분리한다(`FigmaAssetDownloadTool`과 동일한 설계 판단 — 부작용 있는 외부 호출은 프롬프트
  생성과 분리).
- **판정은 신호일 뿐 확정 결론이 아님**: `MOVED`가 실제로 "componentGeometry 가드레일 위반(고정
  좌표 이탈)" 때문인지, 의도된 반응형 재배치(예: 모바일에서 사이드바가 하단으로 이동) 때문인지는
  이 도구가 구분하지 못한다. 최종 판단은 사람이 한다.

---

## 2. 재검토 결과 — 착수 전 확인으로 드러난 사실

착수 전 실제 코드/CSS를 확인한 결과, 원 문서(제외범위 구현계획)의 두 가지 전제가 부정확했다:

| 원 전제 | 실제 확인 결과 |
|---|---|
| "여러 viewport 렌더링 검증"은 인프라 자체가 없어 신규 구현이 필요하다 | `WebCaptureOrchestrationService.captureMultiViewport()` + `MultiViewportComponentMatcher.analyze()`가 이미 Desktop(1440)/Tablet(768)/Mobile(390) 3개 viewport를 캡처하고, `selectorHint`(태그+id+첫 class) 기준으로 `MATCHED_ALL`/`HIDDEN_IN_SOME`/`MOVED`를 판정해 `breakpointObservations`로 반환한다(`MultiViewportComponentMatcher.java`). **캡처·비교 인프라는 이미 완성돼 있다.** |
| SVG 벡터 추출 제외가 "아이콘이 깨진다"는 중간 영향을 준다 | `_ds_bundle.css.tpl`을 직접 grep한 결과 KRDS 아이콘은 전부 `background-image`(인라인 `data:image/svg+xml` 144건 + `ico_*.svg` 외부 파일 다수)로 **CSS에 이미 내장**되어 있다. 표준 KRDS 아이콘은 Figma 벡터 추출이 원래 불필요하고, 이 gap은 KRDS 표준에 없는 **커스텀 벡터 그래픽**에만 국한된다. |

두 번째 항목은 이번 문서의 구현 대상이 아니지만, 우선순위 재산정 근거로 §7에 반영한다.

---

## 3. 핵심 설계 결정 — 착수 전 확정 필요

`breakpointObservations`는 이미 `captureWebPageMultiViewport()` Tool의 반환값(`RenderedDesignBundle`)에
그대로 들어있다. 즉 **신규 코드 없이도** 오늘 당장 이 Tool을 호출해서 `MOVED`/`HIDDEN_IN_SOME`을
읽을 수 있다. 그래서 두 가지 범위 중 하나를 확정해야 한다:

| | **옵션 A — 문서 가이드만(무코드)** | **옵션 B — 요약 Tool 신설** |
|---|---|---|
| 방식 | `WorkflowGuideTool` 또는 tool-reference 문서에 "claude 경로 생성 후 `captureWebPageMultiViewport()`로 재캡처해 `breakpointObservations`의 `MOVED`를 확인하라"는 절차만 추가 | `captureWebPageMultiViewport()`를 감싸서 이 검증 목적에 맞게 재구성한 요약 리포트를 반환하는 신규 Tool 추가 |
| 개발 규모 | 없음(문서만) | 작음(record 1개 + 순수 함수 서비스 1개 + Tool 1개) |
| 사용자 경험 | Claude가 원본 `RenderedDesignBundle`을 직접 해석해야 함(구조가 검증 목적에 맞춰져 있지 않아 판단 실수 가능) | "위반 의심 N건" 같은 형태로 즉시 해석 가능한 결과 제공 |
| MCP 계약 영향 | 없음 | 신규 Tool 1개 → baseline 재생성, 개수 상수 갱신 필요 |

**이 문서 이후 절은 옵션 B를 기준으로 작성한다** — 개발 규모가 작고(신규 순회/파싱 로직 없이 이미
계산된 `breakpointObservations`를 재분류만 함) 재사용성이 높기 때문이다. 옵션 A만으로 충분하다고
판단되면 Phase 1~4 전체가 불필요해지고 문서화 작업으로 대체된다.

---

## 4. 목표 아키텍처 (옵션 B)

```
(사람 또는 Claude가 명시적으로 호출)
checkResponsiveRegression(CaptureWebPageRequest)          [신규 Tool]
        ↓
WebCaptureOrchestrationService.captureMultiViewport()     [기존, 무변경]
  └─ RenderedDesignBundle(componentMatches, breakpointObservations, warnings)
        ↓
ResponsiveRegressionAnalyzer.analyze(bundle)              [신규 순수 함수]
  └─ breakpointObservations를 Change 타입별로 집계
  └─ MOVED 발생 건은 "가드레일 위반 의심" 목록으로 별도 추출
        ↓
ResponsiveRegressionReport                                [신규 record]
  └─ Tool 반환값 → 사람 또는 Claude가 결과 확인
```

---

## 5. 데이터 모델 설계

### 5.1 `ResponsiveRegressionReport`(신규 record, `model/capture/`)

```java
public record ResponsiveRegressionReport(
        String bundleId,
        int matchedAllCount,
        int hiddenInSomeCount,
        int movedCount,
        List<BreakpointObservation> suspiciousMoves,
        List<CaptureWarning> captureWarnings) {

    public ResponsiveRegressionReport {
        suspiciousMoves = suspiciousMoves == null ? List.of() : List.copyOf(suspiciousMoves);
        captureWarnings = captureWarnings == null ? List.of() : List.copyOf(captureWarnings);
    }
}
```
신규 record이므로 compat 생성자 불필요(기존 호출부 없음).

---

## 6. 핵심 로직 설계

### 6.1 `ResponsiveRegressionAnalyzer`(신규 서비스, 순수 함수)

```java
public final class ResponsiveRegressionAnalyzer {
    private ResponsiveRegressionAnalyzer() {}

    public static ResponsiveRegressionReport analyze(RenderedDesignBundle bundle) {
        List<BreakpointObservation> moved = bundle.breakpointObservations().stream()
                .filter(o -> o.change() == BreakpointObservation.Change.MOVED)
                .toList();
        long hiddenCount = bundle.breakpointObservations().stream()
                .filter(o -> o.change() == BreakpointObservation.Change.HIDDEN).count();
        long matchedAllCount = bundle.componentMatches().stream()
                .filter(m -> m.status() == ComponentMatch.Status.MATCHED_ALL).count();

        return new ResponsiveRegressionReport(
                bundle.bundleId(), (int) matchedAllCount, (int) hiddenCount, moved.size(),
                moved, bundle.warnings());
    }
}
```

`MultiViewportComponentMatcher`가 이미 만든 `Result`를 재순회할 필요 없이 `RenderedDesignBundle`
필드만 재분류하므로 신규 순회 로직이 없다 — §2에서 확인한 대로 무거운 신규 로직이 아니다.

### 6.2 `ResponsiveRegressionTool`(신규 Tool)

```java
@Component
public class ResponsiveRegressionTool {
    private final WebCaptureOrchestrationService service;

    @McpToolRisk(McpToolRiskLevel.EXTERNAL)
    @Tool(description = """
            claude 경로로 생성된 화면이 componentGeometry 반응형 가드레일 지시를 실제로
            지켰는지 확인합니다. captureWebPageMultiViewport와 동일하게 Desktop/Tablet/Mobile
            3개 viewport를 캡처하고, 컴포넌트가 브레이크포인트 사이에서 재배치(MOVED)됐는지
            집계해 반환합니다. MOVED는 가드레일 위반(좌표를 인라인/고정폭으로 옮김) 의심 신호일
            뿐이며, 의도된 반응형 재배치일 수도 있으므로 최종 판단은 사람이 해야 합니다.
            """)
    public ResponsiveRegressionReport checkResponsiveRegression(CaptureWebPageRequest request) {
        RenderedDesignBundle bundle = service.captureMultiViewport(request);
        return ResponsiveRegressionAnalyzer.analyze(bundle);
    }
}
```

`McpConfig`의 `allToolCallbacks` 빈에 `ResponsiveRegressionTool` 파라미터 + `toolObjects(...)` 추가.

---

## 7. 신규/수정 파일 목록

| 파일 | 변경 유형 | 내용 |
|---|---|---|
| `model/capture/ResponsiveRegressionReport.java` | 신규 | §5.1 record |
| `service/ResponsiveRegressionAnalyzer.java` | 신규 | §6.1 순수 함수 |
| `tools/ResponsiveRegressionTool.java` | 신규 | §6.2 Tool |
| `config/McpConfig.java` | 수정 | `ResponsiveRegressionTool` 빈 등록 |
| `src/test/resources/mcp/tool-definitions-baseline.json` | 재생성 | 신규 Tool 1개 추가로 인한 baseline 갱신 |
| `docs/tool-reference/Figma_fills_strokes_구현계획.md`류 문서(참고용) | 없음 | 이번 작업은 별도 문서로 완결, 기존 문서 수정 없음 |

---

## 8. 리스크 및 대응

| 리스크 | 영향 | 대응 |
|---|---|---|
| `MOVED`가 가드레일 위반이 아니라 의도된 반응형 재배치일 수 있음(오탐) | 사람이 잘못된 결론을 낼 위험 | Tool 설명과 리포트 문서에 "의심 신호일 뿐, 최종 판단은 사람" 명시(§1 설계 원칙) |
| `captureWebPageMultiViewport()`와 동일하게 서버가 미리 실행 중이어야 함 | 기존 제약 승계 | 신규 문제 아님, 기존 Tool 설명 그대로 승계 |
| MCP 계약 변경(신규 Tool 1개) | `McpToolDefinitionSnapshotTest`/`McpToolRiskAnnotationCoverageTest` 하드코딩 개수 불일치 | baseline 재생성 + 두 카운트 상수 모두 갱신(기존 반복된 절차, `McpToolRiskAnnotationCoverageTest`도 반드시 함께 확인) |
| `MultiViewportComponentMatcher`의 선언된 한계(같은 DOM, CSS만 다른 반응형에서만 정확) | adaptive 설계(viewport별 DOM 구조 자체가 다른 경우) 오탐/누락 가능 | 기존 `ComponentMatch` Javadoc에 이미 명시된 한계 승계, 신규 리스크 아님 |

---

## 9. 이번 범위에서 제외

- **옵션 A(문서 가이드만)** — §3에서 옵션 B로 확정할 경우 불필요
- **트랙 B(SVG 벡터 추출)** — §2 재검토로 표준 KRDS 아이콘엔 영향 없음이 확인되어 우선순위를
  최하위로 재조정. 커스텀 벡터 그래픽 케이스만 남는데 발생 빈도가 낮아 이번 범위에서 다루지 않음
- **트랙 A(구조 일치도 좌표 비교), 이미지 압축, 트랙 D(퍼지 dedup)** — 영향검토 문서의 결론 그대로
  우선순위 낮음 유지, 이번 범위 아님
- `MOVED` 원인을 자동으로 "의도된 재배치 vs 가드레일 위반"으로 분류하는 고도화 — 1차는 단순 집계만

---

## 10. 단계별 구현목록

### Phase 0 — 설계 결정 확정 (필수, 코드 작업 아님)
| 순서 | 작업 | 상태 |
|---|---|---|
| 1 | §3 옵션 A/B 중 확정 | 완료 — 옵션 B로 확정 |

### Phase 1 — 데이터 모델·로직
| 순서 | 작업 | 상태 |
|---|---|---|
| 2 | `ResponsiveRegressionReport` record 신설 | 완료 |
| 3 | `ResponsiveRegressionAnalyzer.analyze()` 구현 | 완료 |

### Phase 2 — Tool·계약
| 순서 | 작업 | 상태 |
|---|---|---|
| 4 | `ResponsiveRegressionTool` 신규 + `McpConfig` 등록 | 완료 |
| 5 | `tool-definitions-baseline.json` 삭제 → `McpToolDefinitionSnapshotTest` 재실행으로 재생성 | 완료(개수 상수 104→105, 39→40) |
| 6 | `McpToolRiskAnnotationCoverageTest`의 하드코딩된 `scannedMethods` 상수 갱신 | 완료(104→105) |

### Phase 3 — 테스트
| 순서 | 작업 | 상태 |
|---|---|---|
| 7 | `ResponsiveRegressionAnalyzerTest`: MOVED 없음/일부 있음/HIDDEN 혼재 케이스 | 완료(3건) |
| 8 | `ResponsiveRegressionToolTest`: `captureMultiViewport()` 호출 위임 확인 | 완료(1건) |
| 9 | `./gradlew test` 전체 통과 확인 | 완료(0 failures) |

---

## 11. 검증 방법

1. `./gradlew build` — 전체 테스트 통과 확인
2. `llmProvider=claude`로 화면 생성 → 실제 배포 → `checkResponsiveRegression()` 호출 → 정상
   반응형이면 `movedCount=0` 확인
3. 의도적으로 좌표를 고정 px로 하드코딩한 화면으로 동일 호출 → `movedCount>0` 및
   `suspiciousMoves`에 해당 `selectorHint`가 잡히는지 확인

---

## 12. 관련 문서

- [Figma_픽셀재현_제외범위_2차구현_영향검토.md](./Figma_픽셀재현_제외범위_2차구현_영향검토.md) — 이 구현계획의 우선순위 근거가 된 영향검토
- [Figma_픽셀재현_제외범위_구현계획.md](./Figma_픽셀재현_제외범위_구현계획.md) — 트랙 C의 반응형 가드레일 문구가 처음 추가된 문서
