# 섹션 17.1(JSP→Thymeleaf) CSS/JS 자산 미검증 문제 — Readiness 연동 해결 방향 검토

> 2026-09-02, 코드 실측 기준 작성. 구현 여부는 결정되지 않았으며, 이 문서는 **검토 결과만** 담는다.
> [`Thymeleaf_레거시전환_KRDS_반영_검토.md`](./Thymeleaf_레거시전환_KRDS_반영_검토.md)에서 확인한
> "섹션 17.1이 CSS/JS 자산 존재 여부와 무관하게 완주된다"는 문제의 후속 검토다.
>
> **정정(2026-09-02, 후속 조사): 아래 §4의 제안(readiness()를 approve()/apply() 앞에 게이트로 연결)은
> 구조적으로 실행 불가능하다는 게 추가 조사로 드러났다.** `persistValidationReport()`(검증 증적을
> 실제로 남기는 유일한 경로)는 `ThymeleafProjectWorkflowService.revalidate(operationId, ...)` 안에서만
> 호출되는데, 이 메서드는 `status() != APPLIED`면 즉시 예외를 던진다 — 즉 `BINDING`/`BUILD`/`RENDER`
> 증적은 `apply()`가 끝난 뒤에만 생길 수 있다. `apply()` 앞에서 그 증적을 요구하는 건 "적용 전에
> 적용 후에만 생기는 증거를 요구"하는 순환 논리다. 이 문제의 실제 대응 방안(감지가 아니라 해결)은
> [`Thymeleaf_레거시전환_KRDS_반영_검토.md`](./Thymeleaf_레거시전환_KRDS_반영_검토.md) §6-1에
> 재정리했다. 이 문서 §2~§5는 readiness() API 자체의 동작 방식(코드 근거)을 설명하는 부분으로는
> 여전히 유효하며, §4의 "제안"만 재검토 대상이다.

---

## 1. 배경

이전 검토(`Thymeleaf_레거시전환_KRDS_반영_검토.md`)에서 두 가지를 코드로 확인했다.

1. `ThymeleafProjectWorkflowService.preview()`/`approve()`/`apply()`는 CSS/JS 자산(`styles.css`,
   `_ds_bundle.css`, `krds.min.js`) 존재 여부를 전혀 검사하지 않는다 — 기본 게이트(`THYMELEAF_PARSE`/
   `BINDING_VALIDATION`/`ROUTE_PARITY`/`OVERFLOW_CHECK`)는 모두 문자열/구조 수준 검사이고,
   실제 브라우저 렌더를 확인하는 `BROWSER_RENDER`/`VISUAL_PARITY` 게이트는 별도 opt-in 도구
   (`revalidateThymeleafProjectWithBrowserGate`)로만 실행된다.
2. 그 결과 `apply()`까지 아무 에러 없이 완주되고, 대상 프로젝트에 KRDS 자산이 없으면 최악의 경우
   layout 리졸브 실패(500), 그보다 나은 경우도 스타일이 전혀 안 먹은 밋밋한 화면이 배포된다.

이후 섹션 17(Dual Pipeline Common Control Plane)을 검토하던 중, **이 문제를 감지할 수 있는 지렛대가
이미 코드로 존재한다**는 사실을 확인했다.

---

## 2. 이미 존재하는 지렛대 — `ReleaseReadinessEvaluator`

`GenerationControlPlaneService.readiness(operationId, sourceType)` → `ReleaseReadinessEvaluator.evaluate()`:

```java
private static final Set<GateType> REQUIRED = EnumSet.of(BINDING, BUILD, RENDER);
...
boolean ready = applied && failed.isEmpty() && missing.isEmpty();
```

`ThymeleafWorkflowAdapter.map()`에서 `BROWSER_RENDER` 게이트가 `RENDER` 타입으로 매핑되고, `RENDER`는
**필수(REQUIRED) 게이트**다. 즉 `BROWSER_RENDER` 증적이 기록되지 않은 Operation을 `readiness()`로
조회하면 `missing: ["RENDER"]`와 함께 `ready: false`가 정확히 반환된다 — **이 API 하나만으로 CSS/JS
미확인 상태를 이미 식별할 수 있다.**

---

## 3. 남은 gap — `apply()`가 `readiness()`를 참조하지 않음

`ThymeleafProjectWorkflowService.approve()`/`apply()`(코드 확인)는 preview hash 일치·
`validationErrors` 비어있음·DESIGN.md drift·레거시 소스 충돌·파일 쓰기 conflict만 검사한다.
`GenerationControlPlaneService`/`ReleaseReadinessEvaluator`를 호출하는 코드는 어디에도 없다
(grep 확인). **`readiness()`는 "물어보면 알려주는" 사후 조회 API일 뿐, `apply()`를 막는 게이트가
아니다.**

---

## 4. 제안 — `readiness()`를 `approve()`(또는 `apply()`) 앞에 게이트로 연결

새 인프라를 만들 필요 없이, 기존 `GenerationControlPlaneService.readiness()`를
`ThymeleafProjectWorkflowService.approve()` 진입부에서 호출해 `ready=false`면 승인을 거부하는
방식이다. 이렇게 하면 이전 검토에서 제안한 "방안 1(사전조건 검증 게이트)"과 "방안 3(브라우저 게이트
필수화)"을 동시에, 새 코드 최소한으로 달성한다.

---

## 5. 고려해야 할 부작용 (구현 전 결정 필요)

### 5-1. 아키텍처 레이어 역전

32번 문서(`Dual_Pipeline_Common_Control_Plane_Impact_Review.md`) §1의 원칙은 "실행기는 분리한다" —
`GenerationControlPlaneService`는 각 실행기(`ThymeleafProjectWorkflowService` 등)를 **읽기 전용으로
투영**하는 하류(downstream) 계층으로 설계됐다(`ThymeleafWorkflowAdapter`가
`ThymeleafOperationStore`를 읽는 방향). `approve()`가 `readiness()`를 호출하게 만들면 이 방향이
역전된다 — 원래 "실행기를 읽기만 하던" 계층이 "실행기의 진행을 좌우하는" 상류(upstream)
의존성이 된다. 컴파일 타임 순환 참조는 아니지만(어댑터가 `ThymeleafProjectWorkflowService`를 직접
참조하지 않으므로), 원래 설계 의도("공통 제어 계층은 실행 로직을 호출하지 않는다")와는 어긋난다.
이 결정을 뒤집으려면 별도 승인이 필요하다.

### 5-2. 기존 운영 데이터에 대한 소급 영향

`BROWSER_RENDER` 게이트는 지금까지 선택적으로만 실행돼 왔다. 이 게이트를 승인 필수 조건으로
바꾸면, 이미 진행 중이거나 과거에 브라우저 게이트 없이 승인된 Operation(실측: `PREVIEW_READY`
1,282건 중 다수가 브라우저 게이트를 거치지 않았을 가능성)의 향후 재승인·재적용 시도가 전부
막히게 된다. 소급 적용 범위(신규 Operation부터만 적용 vs 기존 `PREVIEW_READY` 건도 포함)를
먼저 정해야 한다.

### 5-3. 운영 비용 증가

지금까지 "선택적 정밀 검증" 도구였던 `revalidateThymeleafProjectWithBrowserGate`(Playwright 실제
렌더)가 사실상 **모든 승인의 필수 사전 단계**가 된다. 매 승인마다 브라우저 렌더 비용이 강제로
붙는다는 뜻이며, 이건 게이트 자체의 가치(CSS/JS 문제를 실제로 잡아냄)와 맞바꾸는 비용이다.

### 5-4. 최초 생성 건은 여전히 못 잡을 수 있음

`VISUAL_PARITY`는 baseline 스크린샷과 비교하는 게이트라 최초 생성 시점엔 비교 대상이 없다.
`RENDER`(=`BROWSER_RENDER`, 단순 렌더 성공 여부)만으로는 "렌더는 성공했지만 스타일이 안 먹어서
밋밋하다"는 상태까지 확실히 잡아낼 수 있는지 별도 확인이 필요하다 — 브라우저가 CSS 파일을
찾지 못해도 HTML 자체는 정상 렌더되므로, `BROWSER_RENDER` 게이트의 통과 기준이 무엇인지
(HTML 파싱 성공만 보는지, 실제 스타일 적용 여부까지 확인하는지) 구현 세부를 별도로 확인해야 한다.

---

## 6. 결론

`readiness()` API 차원에서는 CSS/JS 미확인 상태를 이미 정확히 감지할 수 있는 지렛대가 마련돼
있다. `approve()`/`apply()`에 이 체크를 연결하는 것 자체는 코드량이 크지 않지만, §5의 네 가지
부작용(아키텍처 레이어 역전, 기존 운영 데이터 소급 영향, 운영 비용 증가, `BROWSER_RENDER` 게이트의
실제 판정 기준 미확인)이 구현 승인 전에 정리돼야 한다. 특히 §5-1(레이어 역전)은 32번 문서의
핵심 설계 원칙과 직접 충돌하므로, 이 트레이드오프를 명시적으로 받아들이는 별도 의사결정이
필요하다.

---

## 7. 참고 파일 경로

| 파일 | 역할 |
|---|---|
| `service/thymeleaf/ThymeleafProjectWorkflowService.java` | `approve()`/`apply()` — `readiness()` 미참조 확인 |
| `service/controlplane/GenerationControlPlaneService.java` | `readiness(operationId, sourceType)` API |
| `service/controlplane/ReleaseReadinessEvaluator.java` | `REQUIRED = {BINDING, BUILD, RENDER}` 필수 게이트 판정 로직 |
| `service/controlplane/ThymeleafWorkflowAdapter.java` | `BROWSER_RENDER` → `RENDER` 매핑 |
| `service/thymeleaf/ValidationGateExecutor.java` | `BROWSER_RENDER`/`VISUAL_PARITY` 게이트 정의(opt-in 실행) |
| `tools/ThymeleafProjectWorkflowTool.java` | `revalidateThymeleafProjectWithBrowserGate` — 현재 opt-in 진입점 |
| `docs/figma/32_Dual_Pipeline_Common_Control_Plane_Impact_Review.md` §1 | "실행기는 분리한다" 원칙 |
| `docs/tool-reference/Thymeleaf_레거시전환_KRDS_반영_검토.md` | 이 문서의 선행 검토(CSS/JS 미검증 문제 원 발견) |
