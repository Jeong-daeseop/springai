# DEC-02 · DEC-09 최종 승인 요청 — 컴포넌트 표준 명명 규칙 및 초기 필수 카탈로그

> 문서 버전: 1.5
> 작성일: 2026-07-27
> 승인 대상: `DEC-02`(컴포넌트 표준 명명 규칙), `DEC-09`(초기 필수 Component·Pattern·Page Template 목록)
> 승인자: 조직 KRDS/eGovFrame Figma Library 담당자
> 근거 문서: [12_Semantic_Figma_Design_System_Implementation_List.md](./12_Semantic_Figma_Design_System_Implementation_List.md) §4,
> `website-figma-contract/component-catalog-v1.json`,
> `website-figma-contract/CONTRACT_RULES.md` §6
>
> **v1.5 정정(2026-08-21)**: 체크포인트 2와 DEC-09 확인 항목에서 "실제 검토 대상 라이브러리"를 "KRDS Figma Library"로 표기한 건 오기였다. R0-028 라이브 검증 중 실측한 결과, 실제 Property 값(`Label`/`Style`/`Disabled`, `Primary`/`Secondary`/`Ghost` 등)은 **FTC 정부 포털 Design System**(DEC-01이 확정한 라이브러리) 관례이며 KRDS_v1.0.0 (Community)와는 별개의 값 체계를 쓴다(26/27/28번 문서 참고). 승인 당시 검토·승인된 실제 값 자체는 FTC 기준으로 이미 맞았으므로 **재승인 없이 라이브러리 이름 표기만 정정**한다.

---

## 1. 이 문서로 요청하는 것

`DEC-02`와 `DEC-09`는 같은 산출물(`component-catalog-v1.json`)에 대한 승인이라 하나의
요청으로 묶었다. **기술적으로는 이미 완성돼 JSON Schema 검증과 Spring/Plugin 테스트를
통과한 상태다.** 이 문서에서 필요한 것은 코드 작업이 아니라, 아래 내용이 실제 KRDS/eGovFrame
디자인 언어와 운영 방침에 맞는지에 대한 **사람의 최종 확인**이다.

승인/반려/수정 요청은 §7 서명란에 기록한다. 반려되거나 수정이 필요하면 §6 "변경 시 절차"에
따라 새 카탈로그 버전을 만들고 다시 이 문서 형식으로 재요청한다.

---

## 2. 왜 지금 승인이 필요한가

- 이 카탈로그가 승인돼야 R3(Design System Author Plugin)가 실제 Figma Team Library에
  이 12개 컴포넌트를 정식으로 생성·Publish할 수 있다.
- 승인 전까지는 "기술 기준안"일 뿐이며, R4(Publish Registry 동기화) 이후 단계는 이 카탈로그가
  실제 운영 기준으로 확정됐다는 전제에서 진행된다.
- 승인 없이 계속 진행하면, 나중에 명명 규칙이나 목록이 바뀔 때 이미 Publish된 Figma Component와
  이미 생성된 화면들을 다시 손봐야 하는 비용이 발생한다(Breaking Change 처리, 12번 문서 R4 참고).

---

## 3. 승인 체크포인트

이 문서는 아래 순서로 보면 된다.

| 체크포인트 | 확인 대상 | 승인 기준 | 적용 항목 |
|---|---|---|---|
| 1 | 네임스페이스 구분 | `krds.*` / `egov.*` 분리가 조직의 다른 디자인 시스템 문서와 충돌하지 않음 | `DEC-02` |
| 2 | Figma Property 관례 | `Label`, `Style`, `Disabled` 등 속성명이 실제 FTC 정부 포털 Design System Library 관례와 어긋나지 않음(2026-08-21 정정 — 원문은 "KRDS Figma Library"로 오기됐으나 실제 검토 대상은 FTC 정부 포털 Design System이었음) | `DEC-02` |
| 3 | 별칭 호환성 | 기존 명칭이 누락되지 않았고, 추가가 필요하면 `aliases`에 넣을 수 있음 | `DEC-02` |
| 4 | 필수 목록 충분성 | 12개 필수 항목만으로 1차 범위 `사용자 목록·등록` 화면 생성이 가능함 | `DEC-09` |
| 5 | 선택 항목 범위 | Radio/DatePicker를 선택으로 둔 범위가 의도와 맞음 | `DEC-09` |
| 6 | 제외 범위 명시 | 추가 입력 컴포넌트·Dashboard·DETAIL 필수 승격 제외가 의도와 일치함 | `DEC-09` |

판정은 다음처럼 해석한다.

- `승인`: 위 체크포인트가 모두 수용 가능하고, 현재 카탈로그를 운영 기준으로 고정해도 됨
- `조건부 승인`: 명칭/별칭/설명 수준의 수정만 필요하고, 카탈로그의 의미 구조는 유지됨
- `반려`: 필수 목록의 구조나 네임스페이스 정책을 다시 설계해야 함

### 판정 예시

| 상황 | 권장 판정 | 이유 |
|---|---|---|
| `DEC-02`의 네임스페이스와 Property 명칭은 수용 가능하지만, 별칭 1~2개만 보강 필요 | 조건부 승인 | 의미 구조는 유지되고, 사소한 호환성 보완만 있으면 되기 때문 |
| `DEC-02`의 네임스페이스 구분 자체가 조직 표준과 충돌 | 반려 | 명명 체계의 기본축이 흔들려 카탈로그 버전 재설계가 필요하기 때문 |
| `DEC-09`의 12개 목록은 충분하지만, Radio/DatePicker를 필수로 올리자는 의견만 있음 | 보류(승인 대상 외) | 필수 목록 자체는 문제 없지만, 필수/선택 범위 재조정은 별도 논의가 필요한 조정 이슈이기 때문 |
| `DEC-09`의 12개 목록이 1차 화면 생성에 충분 | 승인 | 현재 카탈로그를 운영 기준으로 고정해도 되기 때문 |
| `DEC-09`에서 DETAIL/Dashboard 제외 방침을 바꿔야 함 | 반려 또는 새 DEC 필요 | 범위 정의 자체가 바뀌므로 현재 카탈로그가 아닌 별도 의사결정이 필요하기 때문 |

### 승인 코멘트 초안

아래 문구는 승인자가 서명란과 함께 바로 붙여 넣을 수 있는 예시다.

| 대상 | 판정 | 코멘트 초안 |
|---|---|---|
| `DEC-02` | 승인 | `krds.*` / `egov.*` 구분과 Figma Property 명칭이 조직 기준과 충돌하지 않으며, 별칭도 호환 범위 내에서 정리되어 있어 승인한다.` |
| `DEC-02` | 조건부 승인 | `명명 규칙의 기본 구조는 수용 가능하나, 별칭 또는 일부 속성명은 조직 관례에 맞춰 소폭 조정이 필요하므로 조건부 승인한다.` |
| `DEC-02` | 반려 | `현재 명명 체계는 조직 표준과 충돌하므로, 네임스페이스와 속성 매핑을 재설계한 뒤 재요청이 필요하다.` |
| `DEC-09` | 승인 | `1차 범위의 사용자 목록·등록 화면을 구성하기에 필수 목록이 충분하다고 판단하며, 제외 항목도 현재 범위 정의와 일치하므로 승인한다.` |
| `DEC-09` | 조건부 승인 | `필수 목록은 수용 가능하나, Radio/DatePicker 또는 제외 범위에 대한 문구 조정이 필요하므로 조건부 승인한다.` |
| `DEC-09` | 반려 | `현재 필수 목록 또는 제외 범위 정의만으로는 1차 화면 범위를 안정적으로 보장하기 어려우므로, 목록 재정의 후 재요청이 필요하다.` |

### 승인 책임 분담

| 역할 | 책임 | 확인 포인트 |
|---|---|---|
| 조직 KRDS/eGovFrame Figma Library 담당자 | 최종 승인권자 | `DEC-02`의 명명 규칙과 `DEC-09`의 필수 목록이 실제 운영 표준으로 채택 가능한지 판정 |
| Design System / Figma 운영 담당자 | 기술 검토자 | 카탈로그가 실제 라이브러리 구조와 맞는지, 기존 컴포넌트·별칭·속성과 충돌하지 않는지 확인 |
| Author Plugin / 구현 담당자 | 근거 제공자 | `component-catalog-v1.json`, Schema 검증 결과, 테스트 결과, 변경 영향 범위를 증빙으로 제공 |

### 서명란 작성 예시

아래 예시는 실제 서명란에 들어갈 수 있는 형태다.

| 항목 | 예시 |
|---|---|
| 결정 | `☑ 승인(그대로 진행)` |
| 수정사항(있는 경우) | `별칭 1개 추가` 또는 `문구 수정 없음` |
| 승인자 | `홍길동` |
| 소속/역할 | `KRDS Figma Library 담당자` |
| 승인일 | `2026-07-27` |
| 비고 | `DEC-02/DEC-09 동시 승인` |

| 항목 | 예시 |
|---|---|
| 결정 | `☑ 조건부 승인(아래 수정사항 반영 후 진행)` |
| 수정사항(있는 경우) | `egov.searchPanel 별칭 보강 후 반영` |
| 승인자 | `홍길동` |
| 소속/역할 | `KRDS Figma Library 담당자` |
| 승인일 | `2026-07-27` |
| 비고 | `수정 반영 후 재확인` |

### 승인 요청 메시지 초안

아래 문구는 메일, 메신저, 코멘트 본문에 바로 쓸 수 있는 짧은 버전이다.

```text
DEC-02 / DEC-09 승인 요청드립니다.

첨부 문서에는 컴포넌트 표준 명명 규칙(DEC-02)과 초기 필수 Component·Pattern·Page Template 목록(DEC-09)을
하나의 카탈로그 승인으로 묶어 정리했습니다.

확인 부탁드릴 항목은 다음 3가지입니다.
1. krds.* / egov.* 네임스페이스 구분이 조직 표준과 충돌하지 않는지
2. Figma Property 명칭과 별칭 구성이 FTC 정부 포털 Design System Library 관례에 맞는지
3. 12개 필수 목록과 제외 범위가 1차 사용자 목록·등록 화면 범위에 충분한지

판정은 승인 / 조건부 승인 / 반려 중 하나로 주시면 되고, 조건부 승인의 경우 수정사항만 함께 적어주시면 됩니다.
```

### 승인 코멘트 운용 메모

- `DEC-02`와 `DEC-09`는 분리된 항목이지만 같은 `component-catalog-v1.json`을 보므로 한 번에 승인해도 된다.
- `R0-025`는 별도 의사결정보다 `DEC-09` 승인 결과를 체크리스트에 반영하는 연계 항목으로 본다.
- 별칭 추가나 문구 수정 정도는 조건부 승인으로 처리하고, 네임스페이스 정책 자체가 바뀌면 반려 후 재작성하는 것이 맞다.

---

## 4. DEC-02 — 컴포넌트 표준 명명 규칙

"논리명(코드가 참조하는 이름) ↔ Figma에 실제로 만들어질 이름 ↔ 코드 생성 시 참조하는 속성 경로"를
아래 표로 확정한다. 전체 원본은 `website-figma-contract/component-catalog-v1.json`.

| 논리명(logicalType) | 별칭(호환용) | Figma Property → 값 | 코드 속성 경로 |
|---|---|---|---|
| `krds.button` | `button`, `actionButton` | Label(TEXT), Style(VARIANT: Primary/Secondary), Disabled(BOOLEAN) | `button.text`, `button.class`, `button.disabled` |
| `krds.textField` | `input`, `textInput` | Label(TEXT), Required(BOOLEAN) | `label.text`, `input.required`, `input.value` |
| `krds.select` | `combo`, `dropdown` | Label(TEXT), Value(TEXT) | `label.text`, `option.selected` |
| `krds.checkbox` | `check` | Label(TEXT), Checked(BOOLEAN) | `label.text`, `input.checked` |
| `krds.pagination` | `pager` | Current Page(TEXT) | `pagination.currentPage` |
| `egov.pageHeader` | `pageTitle` | Title(TEXT) | `h1.text` |
| `egov.searchPanel` | `searchForm` | (내부 필드로 구성, 자체 Property 없음) | `form.searchFields` |
| `egov.dataTable` | `table`, `grid` | (내부 필드로 구성) | `table.columns` |
| `egov.formSection` | `form` | (내부 필드로 구성) | `form.fields` |
| `egov.actionArea` | `buttonGroup` | (내부 필드로 구성) | `actions` |
| `egov.listPage` | `listTemplate` | (Page Template) | `template = CRUD_LIST` |
| `egov.formPage` | `formTemplate` | (Page Template) | `template = CRUD_FORM` |

**네임스페이스 규칙**: 원자적 UI 컴포넌트는 `krds.{camelCase}`, eGovFrame 업무 패턴·페이지
골격은 `egov.{camelCase}`. Figma 쪽 표시 이름은 Author Plugin이 `KRDS/Button`,
`eGovFrame/PageHeader`처럼 `{Namespace}/{PascalCase}` 형식으로 생성한다
(`krds-design-system-author-plugin/scripts/create-sample-spec.mjs` 예시 참고).

**별칭(aliases)의 의미**: 과거 다른 이름으로 불리던 컴포넌트를 새 논리명으로 인식시키기 위한
호환 매핑이다. 예를 들어 기존 코드가 `button`이라고 부르던 것은 자동으로 `krds.button`으로
해석된다. 새 컴포넌트를 추가할 때도 이 규칙(네임스페이스 + camelCase + 필요시 별칭)을 따른다.

### 확인해 주실 것

- [x] `krds.*` / `egov.*` 네임스페이스 구분이 조직의 다른 디자인 시스템 문서와 충돌하지 않는지
- [x] Figma Property 이름(`Label`, `Style`, `Disabled` 등)이 실제 FTC 정부 포털 Design System Library의 기존
      관례와 맞는지(이미 Publish된 유사 컴포넌트가 있다면 이름을 거기 맞춰야 할 수 있음. 2026-08-21 정정 — 원문은
      "KRDS Figma Library"로 오기됐으나 실제 검토 대상은 FTC였음)
- [x] 별칭 목록에 빠진 기존 명칭이 있는지(있다면 `aliases`에 추가해야 함)

---

## 5. DEC-09 — 초기 필수 Component·Pattern·Page Template 목록

### 5.1 필수(Required) — 없으면 화면 생성이 FATAL로 실패

1차 범위(§16)의 "사용자 목록·등록 화면"을 만드는 데 반드시 필요한 12개.

**Component 5종**: `krds.button`, `krds.textField`, `krds.select`, `krds.checkbox`, `krds.pagination`

**Pattern·Page Template 7종**: `egov.pageHeader`, `egov.searchPanel`, `egov.dataTable`,
`egov.formSection`, `egov.actionArea`, `egov.listPage`(Page Template), `egov.formPage`(Page Template)

### 5.2 선택(Optional) — 없어도 Preview 단계 fallback만 표시, 정식 생성은 차단하지 않음

| 논리명 | 별칭 | 없을 때 대체 |
|---|---|---|
| `krds.radio` | `radioGroup` | 대체 없음(fallback 표시만) |
| `krds.datePicker` | `date` | **`krds.textField`로 자동 대체**(`replacement` 필드) |

### 5.3 화면유형·레이아웃 패턴 식별자(archetype 호환)

기존 `ScreenSpecification.archetype`/`PageSpec.template` 문자열이 새 분류 체계로 어떻게
해석되는지의 참조표(12번 문서 §5.3과 동일 규칙, 카탈로그에도 이중 기록):

| 구분 | 논리명 | 매핑되는 기존 archetype 문자열 |
|---|---|---|
| Pattern(레이아웃 성격) | `egov.pattern.list` / `.form` / `.detail` / `.masterDetail` | `LIST` / `FORM` / `DETAIL` / `MASTER_DETAIL` |
| Page Template | `egov.listPage` | `CRUD_LIST`, `BOARD_LIST` |
| Page Template | `egov.formPage` | `CRUD_FORM`, `CRUD_REGIST`, `BOARD_FORM` |
| Page Template | `egov.detailPage` | `CRUD_DETAIL`, `BOARD_DETAIL` |

### 5.4 이번 범위에서 의도적으로 제외한 것

- Radio/DatePicker 외의 추가 입력 컴포넌트(파일 업로드, 리치 텍스트 등)
- 상세(DETAIL) 전용 패턴의 필수 승격 — 현재 `egov.detailPage`는 카탈로그에 있지만
  `requiredComponents`가 아니라 Page Template 참조표에만 있어 선택 취급
- Dashboard 계열 컴포넌트 — `layoutPattern=DASHBOARD` 개념은 있지만 실제 컴포넌트는
  아직 카탈로그에 없음(향후 별도 DEC/카탈로그 버전 필요)

### 확인해 주실 것

- [x] 12개 필수 목록이 "사용자 목록·등록" 1차 범위에 실제로 충분한지
- [x] Radio/DatePicker를 선택으로 둔 것이 맞는지(더 넓은 화면에서 필수로 승격해야 하는지)
- [x] 이 12개가 실제 KRDS/eGovFrame Figma Library에 이미 있는지, 아니면 R3 Author Plugin으로
      새로 만들어야 하는지(신규 생성이면 §5의 fallback 정책이 우선 적용됨을 인지)

---

## 6. 미지원·누락 상황에서의 동작(fallback 정책, 참고용)

이미 구현·테스트된 정책이며 변경을 요청하는 대상은 아니지만, 승인 판단에 참고할 수 있도록 첨부한다.

| 상황 | 처리 |
|---|---|
| 필수 Component가 Registry에 없음 | `FATAL` — 화면 생성 자체를 차단 |
| 선택 Component가 Registry에 없음 | `PREVIEW_ONLY` — Preview 단계에서만 fallback 표시, 정식 생성엔 남기지 않음(R5-016) |
| 지원하지 않는 속성이 들어옴 | `PRESERVE_AS_METADATA` — 값을 버리지 않고 메타데이터로 보존(R5-032/033과 동일 원칙) |
| 폐기(Deprecated) 컴포넌트 참조 | `RESOLVE_REPLACEMENT_OR_FAIL` — `replacement`가 있으면 자동 대체, 없으면 실패 |

---

## 7. 승인 이후 / 변경이 필요할 때 절차

1. **승인**: 이 카탈로그(`contractVersion: 1.0.0`)가 운영 기준이 되고, R3 Author Plugin이
   이 목록을 기준으로 실제 Figma Component를 생성·Publish할 수 있게 된다.
2. **컴포넌트 추가·명칭 변경이 필요해지면**: 기존 파일을 직접 고치지 않고 카탈로그를
   `contractVersion: 1.1.0`처럼 새 버전으로 올리고, 이 문서와 같은 형식으로 재승인을 받는다
   (`CONTRACT_RULES.md` §7 호환성 규칙 — 선택 속성 추가는 하위 호환, 필수 속성 추가·의미 변경은
   breaking change로 별도 버전).
3. **폐기가 필요해지면**: 바로 삭제하지 않고 `replacement`(대체 논리명)를 지정해 기존 화면이
   깨지지 않게 한다.

---

## 8. 승인 서명

| 항목 | 내용 |
|---|---|
| 결정 | ☐ 승인(그대로 진행)  ☐ 조건부 승인(아래 수정사항 반영 후 진행)  ☐ 반려 |
| 수정사항(있는 경우) | |
| 승인자 | |
| 소속/역할 | |
| 승인일 | |
| 비고 | |

승인 결과는 [12_Semantic_Figma_Design_System_Implementation_List.md](./12_Semantic_Figma_Design_System_Implementation_List.md)
§4의 `DEC-02`/`DEC-09` 상태를 `[~]` → `[x]`로 갱신하는 근거로 사용한다.

### 승인 완료 메모

- `DEC-02` 승인 완료 반영
- `DEC-09` 승인 완료 반영
- `R0-025` 연동 완료 반영

---

## 9. 변경 이력

| 버전 | 일자 | 변경 내용 |
|---|---|---|
| 1.4 | 2026-07-27 | 사용자 승인 완료에 따라 `DEC-02`/`DEC-09` 반영 메모를 추가하고 승인 문서의 완료 상태를 정리 |
| 1.3 | 2026-07-27 | 승인 책임 분담과 서명란 작성 예시를 추가해 최종 승인·조건부 승인·반려를 바로 기록할 수 있도록 정리 |
| 1.2 | 2026-07-27 | 승인자가 바로 사용할 수 있도록 `승인 코멘트 초안`을 추가하고 문서 작성일을 현재 기준으로 정렬 |
| 1.1 | 2026-07-27 | 승인 판단을 바로 할 수 있도록 `DEC-02`/`DEC-09` 체크포인트 표와 판정 기준을 추가 |
| 1.0 | 2026-07-27 | `component-catalog-v1.json` 기준 DEC-02·DEC-09 최종 승인 요청 문서 최초 작성 |
