# R0-028 옵션 A 구현 계획 (범위 조정판)

> 문서 버전: 1.0
> 작성일: 2026-08-20
> 관련 문서: [24_R0-028_Table_Card_Swap_Logical_Type_Mismatch_Review.md](./24_R0-028_Table_Card_Swap_Logical_Type_Mismatch_Review.md)
> 성격: 24번 문서 5장 옵션 A(근본 수정)를 실제 착수 가능한 범위로 조정한 구현 계획서. **구현 승인 전이며 코드 변경은 포함하지 않는다.**

---

## 1. Context

`docs/figma/24_R0-028_Table_Card_Swap_Logical_Type_Mismatch_Review.md`에서 R0-028(Platform Layout Policy) 재검증 중 발견한 두 버그의 처리 방안을 검토했다. 사용자는 **A안(근본 수정)** 을 선택했으나, 계획 수립 중 추가 조사로 A안의 실제 적용 가능 범위가 좁혀졌다:

- Java `FigmaDesignOrchestrationService.collectLogicalTypes()`(`service/figma/FigmaDesignOrchestrationService.java:340-349`)는 `node.componentResolution()`이 있는 노드(=실제 Published Component 인스턴스)만 수집한다. `krds.dataTable` SECTION(`ListFigmaScreenBuilder.java:76-116`)은 `componentResolution` 없이 만들어지는 구조적 wrapper라 이 메커니즘에 아예 잡히지 않는다.
- 즉 Java의 `applyComponentSwaps()`는 "컴포넌트 1:1 참조 치환"만 가능하고, Table→Card처럼 SECTION의 layoutMode·자식 구조를 바꾸는 **구조적 변환**은 표현할 수 없다.

사용자는 이를 확인한 뒤 **범위를 분리**하기로 결정했다:

- **이번 작업(A안)**: Desktop 하드코딩 padding/gap 제거 + TABLET/MOBILE **버튼 컴포넌트 1:1 스왑**(실제로 R6-038 메커니즘이 표현 가능한 범위)을 정책 주입으로 정식 연결
- **별도(B/C안, 이번 계획 밖)**: Mobile Table→Card는 24번 문서에 기록된 대로 Plugin 로컬 로직(`applyMobileTableCardSwap`)의 문자열만 `"krds.dataTable"`로 고치는 국소 수정으로 처리 — 이 계획에는 포함하지 않는다.

추가로 Explore 조사 결과, R6-038이 저장하는 `FigmaExportBundle`을 Plugin이 실제로 가져와 Apply할 경로가 현재 전혀 없다는 것도 확인했다(`figmaScreenSpecRepository`에 등록되지 않아 기존 다운로드 엔드포인트로 조회 불가, 전용 엔드포인트도 없음, R5-043/R6-047 패턴은 타입 가드에 막혀 재사용 불가). 이 계획은 새 엔드포인트를 만들지 않고 **기존 다운로드 경로에 태우는 최소 배선**으로 이 gap도 함께 닫는다.

---

## 2. 변경 범위

### 2.1 Plugin: `configureWrapper()`를 viewport-aware하게 수정 (Desktop 하드코딩 제거)

파일: `figma-screen-spec-plugin/src/code.ts`

현재 문제 (`configureWrapper()`, 2081-2082행 부근):

```ts
wrapper.itemSpacing = spec.nodeType === "PAGE" ? 40 : 16;
wrapper.paddingTop = wrapper.paddingBottom = spec.nodeType === "PAGE" ? 48 : 0;
wrapper.paddingLeft = wrapper.paddingRight = spec.nodeType === "PAGE" ? 80 : 0;
```

`spec.viewport`를 전혀 보지 않고 PAGE 타입이면 무조건 이 값(잘못된 Desktop 근사치)을 쓴다 — Desktop뿐 아니라 향후 TABLET/MOBILE Bundle을 그릴 때도 같은 값이 적용된다.

**변경**:

- `configureWrapper(wrapper, spec, screenId, screenVersion, origin)` 시그니처에 `viewport: string` 파라미터 추가
- 파일 상단(기존 `planViewportFixtures()`의 TABLET/MOBILE 상수 근처, `core.ts:704-710` 패턴 참고)에 DESKTOP/TABLET/MOBILE gap·padding 맵을 하나로 정의 (Desktop 24/40, Tablet 16/24, Mobile 12/16 — `PlatformLayoutPolicy.defaultPolicy()`/`valid-platform-layout-policy.json`과 동일 값)
- PAGE 타입일 때 `viewport`로 이 맵에서 값을 찾아 적용. 알 수 없는 viewport는 DESKTOP 값으로 fallback(현행 동작 보존)

**호출 체인 수정**:

- `syncNode(spec, parent, existing, registry, importedComponents, screenId, screenVersion, changes, issues, counts, origin?)` (`code.ts:1616`)에 `viewport: string` 파라미터 추가, 내부의 재귀 호출(`code.ts:1703`)과 `configureWrapper` 호출(`code.ts:1646`)에 그대로 전달
- 최상위 호출부(`code.ts:1204` 부근, `runAtomicApply`의 `populateStaging`)에서 `screen.viewport`(이미 스코프에 있는 `FigmaScreenSpec`)를 `syncNode`에 전달

> **작업 시작 전 확인**: 이 변경은 `docs/figma/24_...md` 검토 이후 별도 세션에서 **이미 부분 착수**되어 현재 워킹 트리에 커밋되지 않은 상태로 존재한다(`figma-screen-spec-plugin/src/code.ts`에 `PAGE_VIEWPORT_POLICY` 맵과 `syncNode`/`configureWrapper`의 `viewport` 파라미터가 이미 추가돼 있음, `git diff` 확인 완료). 실제 착수 시 `git status`/`git diff`로 현재 상태를 먼저 확인하고 중복 작업하지 않는다. 단, 이 미커밋 변경분에도 §2.2 Java 정책 연결과 §2.3 다운로드 경로 배선은 아직 포함돼 있지 않으므로 그 두 부분은 이 계획대로 신규 착수한다.

### 2.2 Java: 버튼 스왑 정책 신설 및 연결

파일: `src/main/java/com/krdevops/springai/service/figma/FigmaPlatformConversionService.java`

`defaultPolicy()`(빈 Swap 규칙, 의도된 안전 기본값)는 그대로 두고, **새 정적 메서드 `approvedPolicy()`** 를 추가한다. viewport 정의는 `defaultPolicy()`와 동일하게 재사용하고, `componentSwaps`에 `website-figma-contract/fixtures/valid-platform-layout-policy.json`에 이미 있는 버튼 축소 규칙 2건만 반영(테이블 규칙은 제외):

```java
List.of(
    new PlatformLayoutPolicy.ComponentSwapRule(
        "krds.button.large", "krds.button.medium", "TABLET", "터치 영역과 8열 grid에 맞춘 축소"),
    new PlatformLayoutPolicy.ComponentSwapRule(
        "krds.button.large", "krds.button.small", "MOBILE", "모바일 터치 영역과 폭에 맞춘 축소")
)
```

`canonicalHash`는 `defaultPolicy()`와 다른 값(예: `"sha256:platform-layout-approved-v1-button-swaps"`)으로 구분.

파일: `src/main/java/com/krdevops/springai/service/figma/FigmaDesignOrchestrationService.java`

`generateFromPlatformConversion()`(263-330행 부근)의 다음 줄:

```java
conversion = platformConversionService.convert(request.targetPlatform(), List.copyOf(logicalTypes));
```

을 3-인자 오버로드로 교체:

```java
conversion = platformConversionService.convert(
        request.targetPlatform(), List.copyOf(logicalTypes), FigmaPlatformConversionService.approvedPolicy());
```

### 2.3 Java: PLATFORM_CONVERT 산출물을 기존 다운로드 경로로 조회 가능하게

파일: `src/main/java/com/krdevops/springai/service/figma/FigmaScreenExportService.java`

`export()`가 하던 `figmaScreenSpecRepository.save(finalSpec)`(212행 부근)와 동일한 저장을 PLATFORM_CONVERT 경로도 탈 수 있도록, 얇은 public 메서드를 추가한다(레이어링 유지 — Orchestration이 다른 서비스의 repository를 직접 주입받지 않고 `FigmaScreenExportService`를 통해서만 접근):

```java
/** R6-038: PLATFORM_CONVERT 등 export() 흐름을 타지 않는 경로가 만든 FigmaScreenSpec을
 * 기존 /screens/{screenId}/download 로 조회 가능하도록 등록한다. */
public void registerConvertedSpec(FigmaScreenSpec spec) {
    figmaScreenSpecRepository.save(spec);
}
```

파일: `src/main/java/com/krdevops/springai/service/figma/FigmaDesignOrchestrationService.java`

`generateFromPlatformConversion()`에서 `artifactService.saveFigmaExportBundle(convertedBundle)` 호출 직전 또는 직후에:

```java
screenExportService.registerConvertedSpec(convertedSpec);
```

추가. 이러면 `screenId`가 `{원본}-{platform}`(예: `spec-emp-list-screen-tablet`) 형태로 이미 등록되므로, Plugin이 **이미 갖고 있는** "서버에서 조회"(`#fetchBundle` → `FETCH_BUNDLE` → `GET /api/figma/screens/{screenId}/download`, `code.ts:817-855`) 버튼에 그 screenId를 입력하는 것만으로 즉시 조회·Apply 가능해진다. 새 REST 엔드포인트나 새 Plugin UI는 만들지 않는다.

---

## 3. 명시적으로 하지 않는 것

- Mobile Table→Card 구조 변환 — 24번 문서에 기록된 대로 별도 처리(Plugin 로컬 `applyMobileTableCardSwap`의 `"krds.dataTable"` 문자열 수정), 이번 계획에 포함 안 함
- `collectLogicalTypes`/`applyComponentSwaps`를 구조 변환까지 확장하는 일 — 별도 신규 기능으로 분리, 이번엔 안 함
- Bundle 자동 재Import(폴링/webhook) UX 신설 — 기존 수동 "서버에서 조회" 버튼 재사용으로 대체, 새 UX 안 만듦

---

## 4. 변경 파일 요약

| 파일 | 변경 |
|---|---|
| `figma-screen-spec-plugin/src/code.ts` | `configureWrapper`/`syncNode`에 viewport 파라미터 추가, DESKTOP/TABLET/MOBILE gap·padding 맵 도입 (일부 미커밋 상태로 이미 착수됨, §2.1 참고) |
| `src/main/java/.../service/figma/FigmaPlatformConversionService.java` | `approvedPolicy()` 신설 |
| `src/main/java/.../service/figma/FigmaDesignOrchestrationService.java` | `generateFromPlatformConversion()`이 `approvedPolicy()` 사용 + `screenExportService.registerConvertedSpec()` 호출 |
| `src/main/java/.../service/figma/FigmaScreenExportService.java` | `registerConvertedSpec()` 신설 |

---

## 5. 다음 단계

이 문서는 계획이며 코드 변경을 포함하지 않는다. 아래 확인 후 착수한다.

- [ ] §2.1 Plugin viewport-aware 변경분의 미커밋 상태를 `git status`/`git diff`로 재확인 후 이어서 작업할지, 새로 작성할지 결정
- [ ] §2.2/§2.3 Java 변경 착수 승인
- [ ] 착수 시 `12_Semantic_Figma_Design_System_Implementation_List.md`의 R0-028 항목을 이 문서를 근거로 갱신
