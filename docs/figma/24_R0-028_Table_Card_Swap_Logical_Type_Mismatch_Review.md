# R0-028 Mobile Table→Card Component Swap 미작동 원인 검토서

> 문서 버전: 1.4
> 작성일: 2026-08-20
> 관련 문서: [12_Semantic_Figma_Design_System_Implementation_List.md](./12_Semantic_Figma_Design_System_Implementation_List.md) R0-028, [25_R0-028_Option_A_Implementation_Plan_Scope_Adjusted.md](./25_R0-028_Option_A_Implementation_Plan_Scope_Adjusted.md)(구현 계획)
> 성격: 원인 분석·처리 옵션 검토서 + 구현·라이브 검증 기록. 5장 이후는 실제로 구현·검증된 내용이다(8장 참고).
>
> **v1.1 정정**: v1.0의 4장·5장 옵션 A는 "R6-038(PLATFORM_CONVERT → Bundle 생성 경로)이 아직 없어 `applyComponentSwaps()`가 죽은 코드"라고 서술했으나, 이는 Plugin `core.ts`의 오래된 JSDoc 주석만 보고 판단한 오류였다. 실제로는 R6-038이 2026-08-18에 이미 완성·병합되어 있었다(`12_...md` R6-038 항목 `[x]`). 4장·5장을 정확한 코드 확인 결과로 다시 작성했다.
>
> **v1.2 정정**: 5장 옵션 A(범위 조정판, 8.1 참고)를 실제로 구현하고 서버에 붙여 라이브로 돌려본 결과, "버튼 컴포넌트 1:1 스왑" 부분에서 **또 다른 정책-vs-실제 불일치**를 발견했다 — `krds.button.large`/`.medium`/`.small`이라는 컴포넌트는 이 프로젝트 어디에도 존재하지 않는다(8.2 참고).
>
> **v1.3 정정**: v1.2의 8.3에서 "FTC 정부 포털 Design System의 Button은 Style 변형만 있고 Size 개념이 없다"고 썼으나 **틀렸다**. 사용자가 Figma Plugin API로 두 라이브러리(FTC 정부 포털 Design System, KRDS_v1.0.0 Community) 버튼 컴포넌트의 `description`과 `variantGroupProperties`를 직접 읽어 확인한 결과, 둘 다 **Size 변형이 실제로 존재한다**(xlarge/large/medium/small/xsmall). v1.2가 근거로 삼았던 "컴포넌트 설명 문구"는 실제 Component Set의 `description` 필드가 아니라 다른 경로로 붙어온 텍스트였다 — 신뢰도가 낮은 방법이었다. 8.3을 사용자가 직접 확인한 정확한 데이터로 다시 작성했다.
>
> **v1.4 정정**: v1.3의 두 라이브러리 비교표도 부정확했다(사용자가 두 파일 값을 재확인 후 정정). 정확한 값: **FTC 정부 포털 Design System · Button**은 Description이 실제로 있고("정부 포털 CTA 버튼. Primary=주요 행동, Secondary=보조 행동(아웃라인), Ghost=텍스트형 행동." — v1.2가 원래 인용했던 문구가 맞았다), Variant는 Style(Primary/Secondary/Ghost) × **Size(Medium/Small, 2개)** × State(Default/Disabled/Focus) = 18개 조합. **KRDS_v1.0.0 (Community) · button**은 Description이 비어 있고, Type(primary/secondary/tertiary) × Size(xlarge/large/medium/small/xsmall, 5개) × State(default/hover/pressed/disabled/focus) = 75개 조합. 이 프로젝트가 실제로 참조하는 건 FTC 쪽이므로 **Medium/Small 2단계 Size가 실제로 존재**한다 — "Size가 아예 없다"(v1.2)도 "5단계가 있다"(v1.3, 사실은 KRDS 수치를 FTC로 착각한 것)도 둘 다 틀렸다. 8.3을 최종 확인된 표로 다시 작성했다.

---

## 1. 배경

`12_Semantic_Figma_Design_System_Implementation_List.md`의 R0-028(Platform Layout Policy)을 실제 Figma 파일(`eGovFrame`, fileKey `DlDsooAxMZngQpbzBXf0RD`)에서 라이브로 재검증하는 과정에서 다음을 확인했다.

1. **Desktop drift**: `qna-list · egov.listPage`(node `388:1060`, 1440px) Frame의 Auto Layout이 gap 40 / padding 80,48로 정책값(gap 24 / padding 40)과 어긋나 있었다. Figma 속성 패널에서 직접 gap→24, padding→40/40으로 수정해 정책과 일치시켰다(2026-08-20, 라이브 수정 완료).
2. **Tablet/Mobile 정책 적용**: `eGovFrame FigmaScreenSpec Export` Plugin(Figma Desktop 앱, `figma-screen-spec-plugin`)의 "Tablet/Mobile 검증용 복제본 생성" 기능을 실행해 `Q&A 목록 · TABLET`(768px, gap16/padding24)과 `Q&A 목록 · MOBILE`(390px, gap12/padding16) Frame이 정책값 그대로 생성됨을 확인했다. 플러그인은 `"viewport fixture 생성 완료: TABLET:768px, MOBILE:390px"` 메시지를 반환했다.
3. **Mobile Table→Card Component Swap 미작동**: 위 Mobile Frame(`390×302.5`, node `576:1489`)을 직접 열어 확대 확인한 결과, 내용물이 여전히 원본 데이터 테이블(번호/질문제목/등록자/진행상태/조회수/등록일 6컬럼)이 그대로 렌더링되어 390px 폭을 초과해 오버플로우하고 있었다. Card 형태로 전환되지 않았다.

3번 현상의 근본 원인을 코드베이스에서 추적한 결과가 이 문서다.

---

## 2. 근본 원인: 두 개의 서로 다른 "테이블" 논리 타입 네이밍 체계

이 프로젝트에는 테이블을 가리키는 두 개의 독립된 네이밍 체계가 **의도적으로** 공존한다. 문제는 이 공존 자체가 아니라, R0-028의 스왑 판정 로직이 둘 중 실제로 쓰이는 쪽을 확인하지 않고 다른 쪽 이름을 하드코딩한 데 있다.

### 2.1 체계 A — Published Component 카탈로그 이름: `"egov.dataTable"`

- 정의 위치: `website-figma-contract/component-catalog-v1.json:106-111`
  ```json
  {
    "logicalType": "egov.dataTable",
    "aliases": ["table", "grid"],
    "replacement": null,
    "figmaProperties": {},
    "codeProperties": {"columns": "table.columns"}
  }
  ```
- `PlatformLayoutPolicy` fixture(`website-figma-contract/fixtures/valid-platform-layout-policy.json`)의 `componentSwaps` 규칙도 이 이름을 그대로 사용한다:
  ```json
  {"fromComponent":"egov.dataTable","toComponent":"egov.dataCard","platform":"MOBILE","reason":"좁은 viewport에서 카드형 데이터 표시"}
  ```
- `figma-screen-spec-plugin/src/core.ts:195-206`의 `requiresPublishedComponent()`가 `nodeType === "COMPONENT"`이고 `type`이 이 카탈로그 집합에 속할 때만 Published Component 참조를 요구한다. 즉 **"egov.dataTable"은 화면 전체가 하나의 Published Component 인스턴스로 조립되는 V2 스킴을 전제로 한 이름**이다.
- 실제 코드베이스 전체를 검색한 결과, `"egov.dataTable"`이 **실제 노드에 `DATA_LOGICAL_TYPE`으로 stamp되는 지점은 단 한 곳도 없다.** 존재하는 참조는 카탈로그 정의, 정책 fixture, V1→V2 카탈로그 컨버터(`ComponentCatalogV1ToV2Converter.java:102,105`, 카탈로그 구조 변환용) 뿐이다. 즉 이 이름은 현재 실행되는 화면 생성 파이프라인에서 **한 번도 실제로 만들어지지 않는 값**이다.

### 2.2 체계 B — 화면 구조 Layout Recipe 타입: `"krds.dataTable"`

- 정의 위치: `src/main/java/.../service/figma/builder/ListFigmaScreenBuilder.java:76-116`
  ```java
  /** 전체 Table은 Published Cell Instance를 조립하는 krds.dataTable Layout Recipe로 표현한다. */
  private FigmaNodeSpec dataTable(...) {
      ...
      idFactory.section(pageId, "table/header"), FigmaNodeSpec.NodeType.SECTION, "krds.dataTable.header", ...
      idFactory.section(pageId, "table"), FigmaNodeSpec.NodeType.SECTION, "krds.dataTable", ...
  }
  ```
- 즉 LIST 화면의 표 전체는 `NodeType.SECTION`(Published Component 아님)이고, `type = "krds.dataTable"`이다. 개별 셀은 Published Cell Instance(`krds.tableCell` 등)를 쓰지만, 그 셀들을 감싸는 표 전체 구조는 별도의 "Layout Recipe" 개념으로 명명되어 있다.
- Plugin의 `configureWrapper()`(`figma-screen-spec-plugin/src/code.ts:2062-2069`)가 화면을 실제로 그릴 때 `wrapper.setPluginData(DATA_LOGICAL_TYPE, spec.type)`으로 이 `"krds.dataTable"` 값을 그대로 노드에 stamp한다. **오늘 검증한 `qna-list` Mobile Frame의 표 SECTION 노드에 실제로 찍혀 있는 값이 바로 이것이다.**

### 2.3 스왑 판정 로직의 실수

`figma-screen-spec-plugin/src/code.ts:361-368`:

```ts
function applyMobileTableCardSwap(frame: FrameNode): number {
  ...
  const logicalType = node.getPluginData(DATA_LOGICAL_TYPE);
  if (logicalType === "egov.dataTable" || logicalType === "krds.table") {
    node.setPluginData(DATA_LOGICAL_TYPE, "egov.dataCard");
    ...
  }
}
```

체계 A(`"egov.dataTable"`)와, 코드베이스 어디에도 존재하지 않는 `"krds.table"`만 검사한다. 실제로 노드에 찍히는 체계 B 값(`"krds.dataTable"`)과는 문자열이 정확히 일치하지 않아 **`count`가 항상 0**이 된다. 예외나 경고 없이 조용히 스왑 0건으로 끝나기 때문에, Plugin UI는 `"viewport fixture 생성 완료"`라는 성공 메시지만 보여주고 실패를 알리지 않는다.

---

## 3. 왜 이런 불일치가 생겼나 — 커밋 이력 추적

`git log -S`로 두 문자열의 최초 도입 시점을 추적했다.

| 문자열 | 최초 도입 커밋 | 시점 |
|---|---|---|
| `"krds.dataTable"` (체계 B, 실제 파이프라인 값) | `60ebfe8` "KRDS Role Variant 런타임 생성 파이프라인 구현" | R0-028보다 이전 |
| `applyMobileTableCardSwap()` + `"egov.dataTable"` 검사 (체계 A 참조) | `e8a5854` "feat: complete Figma semantic pipeline validation" | 2026-08-19 |

`e8a5854` 커밋 diff를 확인한 결과, 이 커밋은 **`PlatformLayoutPolicy.java`, `valid-platform-layout-policy.json` fixture, `applyMobileTableCardSwap()`을 모두 같은 세션에서 함께 작성**했다. 즉 작성자는 자신이 그 자리에서 막 만든 `componentSwaps` 정책 fixture의 문구(`"egov.dataTable"→"egov.dataCard"`)를 그대로 Plugin 스왑 조건에 복사해 넣었고, **실제 화면 생성기(`ListFigmaScreenBuilder`, 이보다 훨씬 이전에 존재)가 노드에 어떤 값을 찍는지는 대조 확인하지 않았다.**

두 네이밍 체계 자체의 공존(카탈로그/정책 레벨 vs 화면 구조 레벨)은 설계상 유효하지만, 스왑 판정이 어느 쪽을 봐야 하는지에 대한 검증이 빠진 것이 직접 원인이다.

---

## 4. R6-038은 이미 완성돼 있다 — 그런데도 R0-028 문제를 안 풀어주는 이유

v1.0에서는 `figma-screen-spec-plugin/src/core.ts:712-730`의 `applyComponentSwaps()`(TS, Plugin 쪽 순수함수) JSDoc 주석만 보고 "R6-038이 없어서 죽은 코드"라고 판단했다. 실제로 R6-038(`convert_platform`/`PLATFORM_CONVERT`)을 코드로 직접 확인한 결과는 다르다 — **R6-038은 2026-08-18에 이미 완성·병합**되어 있고 (`12_...md` R6-038 항목 `[x]`, `FigmaDesignOrchestrationService.generateFromPlatformConversion()`), TS `applyComponentSwaps()`와는 별개로 **Java판이 새로 구현되어 실제로 호출되고 있다.**

### 4.1 R6-038이 실제로 하는 일

`FigmaDesignOrchestrationService.generateFromPlatformConversion()` (`service/figma/FigmaDesignOrchestrationService.java:263-330`):

1. `screenSpecificationId`가 가리키는 `APPROVED` 화면명세를 DESKTOP 기준으로 다시 export해 원본 Bundle 생성
2. 그 Bundle 트리에서 실제로 쓰인 논리 타입을 전부 수집(`collectLogicalTypes()`)
3. `FigmaPlatformConversionService.convert(targetPlatform, logicalTypes)`로 타입별 스왑 여부를 판정(`ComponentSwapPolicyResolver.resolve()` 위임)
4. 스왑 대상이 있으면 Java판 `applyComponentSwaps()`(같은 파일 하단, package-private)로 `FigmaNodeSpec` 트리를 재작성
5. `screenId`에 `-{platform}` 접미사를 붙인 **새 `FigmaExportBundle` Artifact**로 저장, `PREVIEW_READY`로 전이

### 4.2 그런데도 R0-028을 못 대체하는 두 가지 이유

**이유 1 — 아키텍처 자체가 다르다.** R6-038은 **Bundle(JSON Artifact) 파일을 새로 생성**하는 서버 사이드 경로다. 이걸 Figma 캔버스에 반영하려면 그 Bundle을 Plugin의 `loadBundleAndPreview()` 흐름으로 별도 Import·Apply해야 한다. 반면 R0-028의 `createViewportFixturesFromSelection()`은 **지금 열려 있는 캔버스 노드를 즉석에서 직접 clone·수정**한다. 이 둘을 갈아끼우려면 R0-028 흐름 자체를 "캔버스 직접 편집"에서 "Bundle 생성 → 재Import"로 바꿔야 하는 아키텍처 전환이 필요하다.

**이유 2 — 기본 Swap 정책이 비어 있다.** `FigmaPlatformConversionService.java:97-114`:
```java
public static PlatformLayoutPolicy defaultPolicy() {
    return new PlatformLayoutPolicy(
        "platform-layout-default-v1",
        List.of(/* DESKTOP/TABLET/MOBILE viewport 3개 */),
        List.of(),   // ← Component Swap 규칙이 빈 리스트
        "sha256:platform-layout-default-v1-no-swaps");
}
```
`convert(targetPlatform, logicalTypes)` 2-인자 오버로드는 **항상 이 빈 정책**을 쓴다. 주석에 명시된 설계 의도: *"조직이 실제 대체 규칙을 승인하기 전까지는 어떤 컴포넌트도 임의로 바꾸지 않는다"* — 의도적인 안전장치다. 즉 `"egov.dataTable"`이든 `"krds.dataTable"`이든 **비교할 규칙 자체가 없어서 이 경로로는 이름이 뭐가 됐든 스왑이 한 건도 안 일어난다.** 3-인자 오버로드 `convert(platform, logicalTypes, policy)`는 존재하지만, 채워진 정책(예: `valid-platform-layout-policy.json`의 `egov.dataTable→egov.dataCard` 규칙)을 실제로 만들어 넘기는 코드는 운영 경로 어디에도 없다 — 이게 R6-038의 진짜 잔여 갭이다.

### 4.3 정정된 결론

이번 버그는 "R6-038이 없어서 급하게 만든 임시방편"이 아니라, **R6-038(Bundle 재생성 경로)과 R0-028(캔버스 직접 편집 경로)이 애초에 서로 다른 목적의 별개 파이프라인**이고, R0-028을 만든 사람이 자신이 같은 세션에서 작성한 정책 fixture 문구를 그대로 옮겨 적으면서 실제 화면 생성기(`ListFigmaScreenBuilder`)가 노드에 찍는 값과 대조 확인을 하지 않은 것이 직접 원인이다. R6-038 쪽도 "완성됐지만 기본 정책이 비어 있어 실질적으로 아무 것도 스왑하지 않는" 별도의 잔여 문제를 안고 있다.

---

## 5. 처리 옵션

| 옵션 | 내용 | 범위 | 장단점 |
|---|---|---|---|
| **A. 근본 수정 (Bundle 경로로 전환)** | R0-028의 Tablet/Mobile 생성 자체를 `createViewportFixturesFromSelection()`(캔버스 직접 편집) 대신 R6-038(PLATFORM_CONVERT → Bundle 생성 → Plugin 재Import) 경로로 바꾼다. 추가로 ① 채워진 `PlatformLayoutPolicy`(componentSwaps 포함)를 생성해 `convert(platform, types, policy)` 3-인자 오버로드에 주입하는 코드, ② Bundle을 Plugin이 자동으로 재Import하는 UX(R5-043과 유사)까지 함께 구현해야 함 | R0-028+R6-038 잔여를 동시에 건드리는 아키텍처 전환. 조직의 Swap 규칙 승인 절차도 선행 필요(DEC 성격) | 설계 의도에 완전히 부합, Registry-aware 안전성 확보. 단 범위가 매우 크고 별도 기획·승인이 필요 |
| **B. 최소 수정** | `applyMobileTableCardSwap()`의 판정 문자열을 실제 파이프라인이 stamp하는 값 `"krds.dataTable"`로 교체 (`"egov.dataTable"`/`"krds.table"`은 향후 대비 안전망으로 병기 가능) | `code.ts` 함수 1개, 5줄 내외 | 국소적이고 즉시 검증 가능. 다만 "서버가 판정해야 한다"는 원래 설계 의도와는 여전히 어긋난 임시방편이며, Registry에 `egov.dataCard`가 실제 Publish돼 있는지 확인 없이 무조건 값만 바꿔치기함. R6-038과는 독립적으로 남는 별개 코드 경로임을 감수해야 함 |
| **C. 절충 (권장)** | 지금은 B로 버그를 국소 수정하되, 함수 주석에 "이 로컬 판정은 R0-028 캔버스 직접 편집 전용 임시 로직이며, R6-038이 Swap 정책 주입(4.2 이유 2)까지 완성되고 Bundle 재Import UX가 갖춰지면 A안으로 대체 검토"를 명시해 두 파이프라인이 왜 중복 존재하는지 코드 레벨에서 추적 가능하게 남김 | B와 동일 + 주석 2~3줄 | 당장 R0-028 동작을 정상화하면서, "왜 같은 스왑 로직이 Java·TS 두 군데 따로 있는가"를 다음 작업자가 다시 헤매지 않도록 문서화 |

## 6. 부수 확인: Desktop drift도 같은 성격의 문제

R0-028의 또 다른 잔여였던 Desktop gap/padding drift(gap 40/padding 80,48 vs 정책 24/40)의 원인도 함께 확인됐다. `configureWrapper()`(`code.ts:2081-2082`)가 `PlatformLayoutPolicy`를 전혀 참조하지 않고 PAGE 타입 노드의 padding/gap을 하드코딩하고 있다:

```ts
wrapper.itemSpacing = spec.nodeType === "PAGE" ? 40 : 16;
wrapper.paddingTop = wrapper.paddingBottom = spec.nodeType === "PAGE" ? 48 : 0;
wrapper.paddingLeft = wrapper.paddingRight = spec.nodeType === "PAGE" ? 80 : 0;
```

오늘 라이브로 고친 것은 이번에 생성된 화면 인스턴스 하나뿐이며, 이 코드가 그대로 있는 한 **향후 새로 생성되는 모든 화면에서 같은 drift가 재발한다.** 이 문제는 이 문서의 스코프(Table→Card 스왑)와 별개이므로 원인만 기록하고, 처리 여부는 별도로 결정한다.

---

## 7. 다음 단계 (v1.1 시점, 이후 8장에서 실행됨)

- [x] 5장 옵션 A/B/C 중 채택안 결정 → **A안(범위 조정판)** 채택. Table→Card 구조 변환은 Java 스왑 메커니즘이 표현 불가능함이 계획 단계에서 확인되어 범위에서 제외, B/C안(Plugin 로컬 `"krds.dataTable"` 문자열 수정)으로 별도 처리하기로 함 — [25번 문서](./25_R0-028_Option_A_Implementation_Plan_Scope_Adjusted.md)
- [x] 6장 Desktop drift 하드코딩 문제를 이번 작업에 포함 → viewport-aware로 수정, 8.1 참고
- [ ] B/C안(`applyMobileTableCardSwap`의 `"krds.dataTable"` 문자열 수정)은 아직 미실행 — 별도 진행 필요
- [ ] 승인 시 `12_Semantic_Figma_Design_System_Implementation_List.md`의 R0-028 항목을 이 문서를 근거로 갱신 → 미실행

---

## 8. 구현 및 라이브 검증 (2026-08-20)

### 8.1 구현 내용 (A안, 범위 조정판)

[25번 문서](./25_R0-028_Option_A_Implementation_Plan_Scope_Adjusted.md) 계획대로 다음을 구현·테스트 완료했다:

1. **Desktop drift 제거**: `figma-screen-spec-plugin/src/code.ts`의 `configureWrapper()`/`syncNode()`가 `FigmaScreenSpec.viewport`를 받아 DESKTOP(24/40/1440)·TABLET(16/24/768)·MOBILE(12/16/390) 값을 정책 맵(`PAGE_VIEWPORT_POLICY`)에서 선택하도록 수정. 기존엔 viewport와 무관하게 PAGE 타입이면 항상 gap40/padding80,48/width1440(Desktop 근사치)이 적용됐다.
2. **버튼 컴포넌트 1:1 스왑 정책**: `FigmaPlatformConversionService.approvedPolicy()` 신설(TABLET/MOBILE 버튼 축소 규칙 2건), `FigmaDesignOrchestrationService.generateFromPlatformConversion()`이 이를 사용하도록 변경.
3. **PLATFORM_CONVERT 결과 조회 배선**: `FigmaScreenExportService.registerConvertedSpec()` 신설, `generateFromPlatformConversion()`에서 호출해 변환 결과가 기존 `/api/figma/screens/{screenId}/download`로 조회되도록 함(신규 엔드포인트·신규 Plugin UI 없이 기존 "서버에서 조회" 버튼 재사용).

Plugin `typecheck`/`build`/`lint`/`npm test`(60건), Java 신규 테스트 4건, Java 전체 회귀(`./gradlew test`) 전부 통과 확인.

### 8.2 라이브 검증에서 발견한 두 번째 정책-vs-실제 불일치

로컬 서버(`./gradlew bootRun`, `.env` 로드)를 띄우고 `convertPlatform`→`generateFigmaBundleForOperation` MCP 흐름을 실제 APPROVED 화면명세(`3816d01e-...`, LETTNQAINFO 기반 `list` 화면)로 MOBILE 변환 실행:

- **8.1의 3번(REST 조회 배선)은 정상 작동 확인**: 결과가 `screenId=list-mobile`로 저장되고 `GET /api/figma/screens/list-mobile/download`로 실제 조회됨(`viewport: "MOBILE"`).
- **버튼 스왑은 발동하지 않음**(`issues: []`, swapTargets 0건). 다운로드된 트리를 확인하니 실제 버튼 노드의 logicalType은 `"krds.button"`(사이즈 접미사 없음) 하나뿐이었다. `src/main/java/.../builder/BuilderSupport.java:53`에서 버튼은 항상 `"krds.button"`로만 생성되며, `component-catalog-v1.json`에도 `krds.button.large`/`.medium`/`.small` 같은 logicalType은 등록돼 있지 않다(전체 15개 logicalType 확인).

즉 `approvedPolicy()`에 넣은 버튼 스왑 규칙(`krds.button.large→medium/small`, `website-figma-contract/fixtures/valid-platform-layout-policy.json`에서 그대로 가져온 예시)도 **2·3장에서 지적한 것과 동일한 종류의 문제** — 정책 fixture의 예시 문구를 실제 파이프라인 산출물과 대조하지 않고 그대로 사용한 것이다. 이번엔 Java 단위 테스트가 합성 데이터(가짜 `"krds.button.large"` 문자열)로만 작성돼 있어 테스트는 통과했지만 실제 화면에는 절대 적용되지 않는다.

### 8.3 곁가지 확인: "KRDS 디자인 시스템이 잘못 만들어진 것 아닌가?" — 아니다, Size는 실제로 있다 (v1.4 최종 확인)

8.2를 논의하는 과정에서 "사이즈 개념 자체가 없다는 게 KRDS 디자인 시스템을 잘못 만든 것 아니냐"는 질문이 나왔다. Figma MCP(`search_design_system`/`get_design_context`)로 확인했던 v1.2의 첫 결론은 방법 자체가 부정확해 틀렸다 — 실제 Component Set의 `description` 필드가 아니라 다른 경로로 붙어온 텍스트를 근거로 삼았기 때문이다. 이후 v1.3에서 한 번 더 고쳤지만 그 표도 부정확했다(두 라이브러리 수치가 뒤섞임). 아래는 사용자가 **Figma Plugin API로 두 라이브러리의 실제 `componentSet.description`과 `variantGroupProperties`를 직접 읽어** 재확인한 최종 값이다(가장 신뢰할 수 있는 방법 — Figma가 컴포넌트에 실제로 들고 있는 원본 메타데이터를 그대로 읽는 것).

| | FTC 정부 포털 Design System · Button | KRDS_v1.0.0 (Community) · button |
|---|---|---|
| Description | "정부 포털 CTA 버튼. Primary=주요 행동, Secondary=보조 행동(아웃라인), Ghost=텍스트형 행동." | 비어 있음 |
| Variant 1 | Style — Primary / Secondary / Ghost | Type — primary / secondary / tertiary |
| Variant 2 (Size) | **Size — Medium / Small (2개)** | Size — xlarge / large / medium / small / xsmall (5개) |
| Variant 3 | State — Default / Disabled / Focus | State — default / hover / pressed / disabled / focus |
| 총 조합 | 18개 (3×2×3) | 75개 (3×5×5) |

이 프로젝트의 실제 화면(`388:1123` 등)이 참조하는 건 **FTC 정부 포털 Design System**이므로, 실제로 쓰이는 Size 값은 **Medium/Small 2단계**다. "Size가 아예 없다"(v1.2)도 틀렸고 "5단계가 있다"(v1.3, KRDS 수치를 FTC로 착각)도 틀렸다 — 실제로는 **Medium/Small 2단계 Size가 존재**한다.

**그럼 정확한 문제는 무엇인가.** 여기서 구분해야 할 게 있다 — Figma에는 컴포넌트를 바꾸는 방법이 두 가지다.

1. **Variant Property 변경**: 같은 컴포넌트 인스턴스에서 속성값만 바꾼다(예: `instance.setProperties({ Size: "small" })`). 컴포넌트 자체(`componentSetKey`)는 그대로다.
2. **Component Swap**: 인스턴스가 참조하는 컴포넌트 자체를 완전히 다른 컴포넌트로 교체한다(`componentId`/`componentSetKey`를 다른 값으로 바꿈).

이 프로젝트가 R0-028/R6-038에 구현한 메커니즘(`ComponentSwapPolicyResolver`, `PlatformLayoutPolicy.componentSwaps`, Java·TS `applyComponentSwaps()`)은 전부 **2번(Component Swap)** 만 할 수 있다 — `fromComponent`/`toComponent`로 서로 다른 두 `logicalType`(=서로 다른 컴포넌트)을 지정하는 구조이기 때문이다. 그런데 버튼 Size는 **1번(Variant Property)** 이다 — `krds.button`이라는 컴포넌트 하나 안의 속성값 차이일 뿐, `krds.button.small`이라는 별도 컴포넌트가 존재하는 게 아니다.

**정확한 결론**: KRDS/FTC 디자인 시스템은 잘못 만들어지지 않았다 — Size 변형은 정상적으로 잘 갖춰져 있다. 문제는 **이 프로젝트의 "Component Swap" 메커니즘이 애초에 Size 같은 Variant Property 변경을 다루도록 설계되지 않았다**는 것이다. "플랫폼에 따라 컴포넌트의 Variant Property를 자동으로 바꿔주는 기능" 자체가 이 코드베이스에 아직 없다 — 이건 `approvedPolicy()` 문자열을 고쳐서 될 일이 아니라, Component Swap과는 별개의 새 기능이 필요한 문제다.

### 8.4 잔여 작업

- [ ] `FigmaPlatformConversionService.approvedPolicy()`의 컴포넌트 스왑 규칙을 비운다(실제로 대응하는 컴포넌트 쌍이 없으므로) — 정책 주입 배선(`convert()` 3-인자 호출, `registerConvertedSpec()`)은 유지
- [ ] Desktop drift 수정(8.1-1)의 시각적 확인은 아직 Figma Desktop에서 직접 못 함 — Plugin으로 신규 화면을 실제 생성해 gap/padding이 24/40으로 나오는지 확인 필요(브라우저 자동화로는 Figma Desktop을 조작할 수 없어 사용자 확인 필요)
- [ ] B/C안(Table→Card, `"krds.dataTable"` 문자열 수정)은 여전히 미실행
- [ ] 이 모든 결과를 `12_Semantic_Figma_Design_System_Implementation_List.md`의 R0-028 항목에 반영
