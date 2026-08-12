# KRDS Q&A 6화면 Figma 검증 보고서

- 검증일: 2026-08-11
- 대상: Q&A 목록·등록·상세·답변 목록·답변 상세·답변 등록
- Figma 페이지: `Q&A KRDS 6 Screens v2`
- 계약 상태: `REVIEW_REQUIRED`
- Variant Rule Set 상태: `DRAFT` (`2.0.0-candidate`)

## 1. 결론

원본 스크린샷 6개를 3개의 범용 프레임으로 축약하지 않고, 업무 목적이 서로 다른 6개의 독립 화면으로 재구성했다. 화면 수, Published Instance 해석, 목적별 Variant, 폰트, 기본 레이아웃과 클리핑 검사는 통과했다.

다만 이 결과는 운영 승인 완료가 아니다. Design System Owner의 사람 승인, 픽셀 기반 Visual Regression 기준선, Rollback 재생성 리허설이 남아 있으므로 Registry와 Rule Set은 후보 상태를 유지한다.

## 2. 기존 품질 저하의 직접 원인

기존 `eGovFrame/PageHeader`, `SearchPanel`, `DataTable`, `FormPage` Component Set의 이름만 보고 화면을 조립하면 다음 문제가 발생한다.

1. 일부 Default Variant가 실제 UI를 담지 않은 빈 100×100 수준의 스텁이었다.
2. `FormPage`처럼 화면 구조를 나타내는 역할과, Figma에서 import해야 하는 Published Component가 구분되지 않았다.
3. 등록과 상세가 동일한 기본 Variant를 사용해 입력 가능·읽기 전용 상태가 사라졌다.
4. 목록·답변 목록, 상세·답변 상세가 하나의 범용 프레임으로 합쳐져 6개 업무 흐름이 3개 화면으로 축약됐다.
5. Component Set 첫 Variant 또는 이름 유사도에 의존하면 Library 편집 순서가 결과를 바꾸며, 선택 근거를 추적할 수 없다.

이번 구현에서는 `PAGE/SECTION`을 Auto Layout 구조 역할로, `COMPONENT`를 실제 Published Instance로 구분했다. 따라서 `form.container`, `form.section`, `data.table` 같은 구조 역할에는 억지로 Component Key를 부여하지 않고, Field·Button·Table Cell·Page Header처럼 실제 인스턴스가 필요한 노드만 결정적으로 해석한다.

## 3. 실제 KRDS Published Library Inventory

보안상 Figma 파일 식별자는 문서에 기록하지 않는다. 아래 Key는 공개된 Component/Variant 계약의 식별자이며 Screen Spec과 Registry 후보에 동일하게 반영했다.

| Logical Type | 실제 Library 항목 | 역할 | 선택 Variant | 주요 공개 Property |
|---|---|---|---|---|
| `krds.pageHeader` | Page Header | `page.header` | Static component | `Title#259:4` |
| `krds.searchPanel` | `search__pc` | `search.panel` | medium/default | Label, Hint |
| `krds.tableHeader` | table | `data.table` | title | Title |
| `krds.tableCell` | table | `data.table.cell` | body | Body |
| `krds.pagination` | `pagination__pc` | `data.pagination` | Static component | 없음 |
| `krds.textField` | `text_input` | `field.text` | medium/default·view·disabled | Label, Placeholder |
| `krds.textarea` | `text_area` | `field.textarea` | default·view·disabled | Label, Placeholder |
| `krds.select` | `selectbox` | `field.select` | medium/default·view·disabled | Label, Placeholder |
| `krds.checkbox` | checkbox | `field.checkbox` | off/on/default·disabled | Label |
| `krds.button` | button | action roles | primary·secondary·tertiary, medium/default | Label |

전체 Key, Axis, 허용 값과 Variable 참조는 [`krds-component-registry-v2.json`](../../website-figma-contract/fixtures/qna/krds-component-registry-v2.json)에 저장했다. 빈 Default Variant였던 범용 eGovFrame 래퍼는 Inventory 후보에서 제외했다.

## 4. Figma 6개 화면 결과

| 순서 | 화면 | Frame 이름 | Node ID | 목적별 핵심 Variant | 결과 |
|---:|---|---|---|---|---|
| 1 | 질문 목록 | `KRDS-QNA-01-목록` | `9190:237` | Search default, Table body, Primary 등록 | 통과 |
| 2 | 질문 등록 | `KRDS-QNA-02-등록` | `9190:238` | Field default, Textarea default, Checkbox editable | 통과 |
| 3 | 질문 상세 | `KRDS-QNA-03-상세` | `9190:239` | Field view, Textarea view, 수정·삭제·목록 | 통과 |
| 4 | 답변 목록 | `KRDS-QNA-04-답변목록` | `9190:240` | Search/Table/Pagination 목록 조합 | 통과 |
| 5 | 답변 상세 | `KRDS-QNA-05-답변상세` | `9190:241` | Read-only Field, 답변 등록·목록 | 통과 |
| 6 | 답변 등록 | `KRDS-QNA-06-답변등록` | `9190:242` | 질문 view, 답변자 default, Select default, Textarea default | 통과 |

## 5. 검증 결과

### 5.1 자동·구조 검증

| Gate | 결과 | 근거 |
|---|---|---|
| 화면 수 | 통과 | 기대 6, 실제 6, 중복 없음 |
| Placeholder | 통과 | 6개 Frame 모두 placeholder `false` |
| Published Instance 해석 | 통과 | 화면별 unresolved instance 0 |
| 목적별 State | 통과 | 등록은 default, 상세는 view/disabled |
| Action 역할 | 통과 | Primary·Secondary·Tertiary가 역할별로 분리됨 |
| 폰트 | 통과 | 전체 `Pretendard GOV` 계열 |
| 계약 Schema | 통과 | Registry, Rule Set, 6개 Screen Spec v2 검증 성공 |

화면별 Published Instance 수는 28, 10, 12, 27, 11, 9개다. 이 수치는 일반 Frame만으로 화면을 흉내 낸 결과가 아니라 실제 Library Instance가 화면 구성에 포함됐음을 확인하는 보조 지표다.

### 5.2 시각 검토

- 6개 Frame 전체를 동일 페이지에서 개별 Screenshot으로 확인했다.
- 질문 상세 화면 높이를 1060px로 조정해 하단 Action 영역의 클리핑을 제거했다.
- 제목, 검색, 본문, 표, Form Field, Action의 시각적 위계가 화면 목적에 맞게 분리됐다.
- Pagination은 Published Instance 연결은 정상이나, 최종 디자인 승인 시 화면 대비 크기와 여백을 한 번 더 검토할 필요가 있다.

### 5.3 아직 완료하지 않은 Gate

| 항목 | 상태 | 완료 조건 |
|---|---|---|
| Design System Owner 승인 | 대기 | Registry `2.1.0`과 Rule Set 후보에 승인 이벤트 연결 |
| 픽셀 Visual Regression | 대기 | 6개 기준 이미지·임계값·Diff Artifact 저장 |
| 접근성 상호작용 | 부분 | Focus, Error, Disabled, 키보드 이동을 Prototype 또는 구현 UI에서 검증 |
| Rollback 리허설 | 대기 | 이전 Snapshot으로 6개 Preview 재생성 후 동일성 확인 |

## 6. 계약 산출물

- 실제 Inventory: [`krds-component-registry-v2.json`](../../website-figma-contract/fixtures/qna/krds-component-registry-v2.json)
- Variant 후보: [`variant-rule-set-krds-v2-candidate.json`](../../website-figma-contract/fixtures/qna/variant-rule-set-krds-v2-candidate.json)
- 6개 Screen Spec: [`fixtures/qna/v2`](../../website-figma-contract/fixtures/qna/v2)
- 화면 Suite: [`qna-screen-suite-v1.json`](../../website-figma-contract/fixtures/qna/qna-screen-suite-v1.json)

## 7. 판정

현재 판정은 **Preview 자동 검증 통과 / 운영 승인 대기**다. 사람 승인 전에는 Rule Set을 `PUBLISHED`로 변경하거나 운영 Export 기본값으로 전환해서는 안 된다.
