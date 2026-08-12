# KRDS Q&A ScreenSpecification 기반 전환 영향평가

## 1. 평가 대상

현재 Q&A 화면 생성 구조를 다음 목표 구조로 전환할 때의 영향을 평가한다.

```text
Q&A ScreenSpecification Fixture
        ↓
공통 Form/List/Detail Builder
        ↓
KRDS Runtime Resolver
        ↓
FigmaScreenSpec 자동 생성
        ↓
DB 저장
        ↓
Bundle
```

현재 Q&A Bootstrap은 완성된 `FigmaScreenSpec JSON`을 읽어 DB에 저장한다. 목표 구조는 사람이 업무 중심 `ScreenSpecification`만 관리하고, Builder와 Runtime Resolver가 Figma 실행 명세를 자동 생성하도록 생성 책임을 변경하는 것이다.

## 2. 종합 평가

| 평가 항목 | 결과 |
|---|---|
| 구조 개선 효과 | 높음 |
| 기존 Plugin 영향 | 낮음 |
| Builder 영향 | 높음 |
| Bootstrap 영향 | 매우 높음 |
| DB 스키마 영향 | 낮음~중간 |
| 기존 Q&A Fixture 영향 | 매우 높음 |
| 테스트 영향 | 높음 |
| 전환 위험 | 중간 |
| 권장 여부 | 권장 |
| 권장 전환 방식 | 병행 운영 후 교체 |

이 작업은 Fixture 파일 형식만 변경하는 단순 변환이 아니다. Q&A 화면의 원본, 생성 책임, 버전 관리, 저장 원자성 및 회귀 테스트 기준을 변경하는 아키텍처 전환이다.

## 3. 현재 구조와 목표 구조

### 3.1 현재 구조

```text
완성된 qna-*.json
→ KrdsQnaFixtureBootstrapService
→ 일부 목록 구조 Java 코드 보완
→ APPROVED 상태 강제 적용
→ FigmaScreenSpecRepository 저장
→ Bundle 조립
```

완성형 Fixture에는 다음 Figma 실행 정보가 포함된다.

- Figma Page·Section·Component 노드
- Semantic Role
- Component Set Key
- Published Variant Key
- Figma Component Property ID
- Variant Property
- Contract Version
- Rule ID
- Context Hash

따라서 KRDS Library나 Registry가 변경되면 Q&A Fixture도 함께 수정해야 한다.

### 3.2 목표 구조

```text
Q&A 업무 명세
→ ScreenSpecification DB 저장
→ 검증 및 승인
→ Page별 Builder 선택
→ 의미 기반 FigmaNodeSpec 생성
→ KRDS Runtime Resolver
→ Component와 Variant 자동 결정
→ FigmaScreenSpec 생성 및 저장
→ Bundle 조립
```

사람은 다음 정보만 관리한다.

- 화면 ID와 이름
- 목록·상세·등록·수정 목적
- Route
- 업무 필드
- 필드 순서
- 검색·표시·필수·수정 가능 여부
- Action
- 화면 배치 정책

Component Key, Variant Key, Rule ID와 Context Hash는 서버가 자동 생성한다.

## 4. 기대 효과

### 4.1 Figma 내부 Key 직접 관리 제거

`componentSetKey`, `variantKey`, Property ID 및 Context Hash가 Q&A 업무 원본에서 제거된다. KRDS Library 변경은 Registry와 Rule Set 갱신으로 흡수할 수 있다.

### 4.2 공통 Builder 품질 향상

현재 Q&A Bootstrap에만 들어 있는 다음 보완을 공통 Builder 또는 Layout Recipe로 승격할 수 있다.

- DataTable Header·Row·Cell 반복 구조
- 실제 업무 컬럼과 컬럼 비율
- SearchPanel 최대 폭
- Pagination 위치
- Action Area 위치
- Page 최대 폭과 Section 간격

Q&A에서 검증된 개선 사항을 다른 업무 화면에도 적용할 수 있다.

### 4.3 수정 화면 확장 용이

`qna-update`를 추가할 때 완성형 Figma JSON 전체를 작성할 필요가 없다.

```text
Page ID: qna-update
Pattern: crud.update
Route: /qna/{id}/edit
Field Mode: EDITABLE
Actions: UPDATE, DETAIL
```

Form Builder와 Component 계약은 `qna-create`와 공유할 수 있다.

### 4.4 업무 명세와 생성 결과 버전 분리

```text
Q&A ScreenSpecification v3
        ↓
qna-list FigmaScreenSpec v7
qna-create FigmaScreenSpec v3
qna-detail FigmaScreenSpec v3
qna-update FigmaScreenSpec v1
```

어떤 업무 명세 버전으로 어떤 Figma 결과가 만들어졌는지 추적할 수 있다.

## 5. Q&A Fixture 영향

영향 수준은 매우 높다.

현재 Bootstrap은 6개 완성형 JSON 파일을 코드에 고정해 읽는다.

```text
qna-list.json
qna-create.json
qna-detail.json
qna-answer-list.json
qna-answer-detail.json
qna-answer-create.json
```

전환 후 권장 원본은 다음과 같다.

```text
src/main/resources/design/qna/qna-screen-specification.yaml
```

또는 JSON을 유지하되 완성형 `FigmaScreenSpec`이 아니라 업무 중심 `ScreenSpecification` Schema를 사용한다.

기존 완성형 JSON은 즉시 삭제하지 않고 다음과 같은 Snapshot 디렉터리로 이동하는 것이 안전하다.

```text
website-figma-contract/fixtures/qna/snapshots/legacy-v2/
```

기존 Fixture의 역할은 Bootstrap 입력에서 Runtime 결과 비교용 Golden Snapshot으로 바뀐다.

### 5.1 버전 하드코딩 제거

현재 Q&A 목록 보완 코드는 화면 버전을 Java 코드에서 직접 지정한다. 새 구조에서는 다음 원칙으로 변경해야 한다.

```text
동일한 생성 Context Hash
→ 기존 FigmaScreenSpec 재사용

생성 결과 변경
→ 다음 Screen Version 생성
```

## 6. ScreenSpecification 모델 영향

영향 수준은 중간에서 높음이다.

현재 모델은 Page, Field, Action, Layout Density, Form Column Layout, Action Placement 및 SearchPanel Placement를 지원하므로 전면 재작성은 필요하지 않다.

다만 다음 정보의 표현 가능 여부를 확인하고 부족한 계약을 보완해야 한다.

### 6.1 Page별 Route

```text
qna-list           /qna
qna-create         /qna/new
qna-detail         /qna/{id}
qna-update         /qna/{id}/edit
qna-answer-list    /qna/answers
qna-answer-detail  /qna/answers/{id}
qna-answer-create  /qna/answers/new
```

### 6.2 Form Mode

등록과 수정을 명확하게 구분하는 속성이 필요하다.

```text
CREATE
UPDATE
```

두 화면이 모두 Figma `screenType=FORM`으로 변환되므로, Form Mode를 별도 문맥으로 Resolver에 전달해야 한다.

### 6.3 Action 계약

문자열 Action보다 다음 정보를 가진 구조형 계약이 적합하다.

```yaml
- type: UPDATE
  label: 저장
  semanticRole: action.primary
  placement: BOTTOM_RIGHT
  targetPageId: qna-detail
  targetRoute: /qna/{id}
```

권장 속성은 다음과 같다.

- Action Type
- 표시 Label
- Semantic Role
- Placement
- Target Page 또는 Route
- 확인 필요 여부
- 권한 조건

### 6.4 Preview 데이터 분리

현재 목록 샘플 행은 Bootstrap Java 코드에 하드코딩돼 있다. 업무 구조와 Preview 콘텐츠를 분리하는 것이 좋다.

```text
ScreenSpecification
→ 구조·필드·Action

ScreenPreviewData
→ Figma Preview용 샘플 값
```

## 7. ScreenSpecificationService 영향

영향 수준은 높다.

현재 생성 로직은 실제 DB 테이블 스키마에 강하게 연결되어 있다.

```text
database + tableName
→ DB 컬럼 조회
→ ScreenSpecAssembler
→ DataBindingResolver
→ Validator
→ Repository 저장
```

Q&A Fixture는 이미 구성된 업무 명세이므로 기존 `create()`만으로 Import하기 어렵다.

다음과 같은 신규 진입점이 필요하다.

```java
public ScreenSpecification importSpecification(
        ScreenSpecification specification
)
```

또는 별도 서비스를 둔다.

```text
QnaScreenSpecificationImportService
```

이 서비스의 책임은 다음과 같다.

```text
Fixture 역직렬화
→ DB Schema Binding 검증
→ ScreenSpecValidator
→ 버전 충돌 검증
→ DRAFT 또는 REVIEW_REQUIRED 저장
→ 승인
```

### 7.1 실제 DB Binding 위험

Q&A 명세의 논리 필드와 실제 DB 컬럼이 다를 수 있다.

```text
writer
answerStatus
private
```

질문과 답변이 다른 테이블에 있다면 JOIN DataSource도 필요하다. 따라서 전환 전에 실제 Q&A 스키마와 Field Binding을 확정해야 한다.

## 8. ScreenSpecRepository 영향

영향 수준은 중간이다.

기존 `AI_SCREEN_SPECIFICATION` 테이블을 재사용할 수 있어 신규 테이블은 필수가 아니다.

하지만 현재 같은 ID와 Version이 존재하면 내용을 갱신하는 저장 방식은 불변 버전 원칙과 충돌할 수 있다.

권장 저장 정책은 다음과 같다.

```text
동일 ID·Version 없음
→ 신규 저장

동일 ID·Version + 동일 Hash
→ 멱등 성공

동일 ID·Version + 다른 Hash
→ VERSION_CONFLICT
```

운영 추적 강화를 위해 다음 컬럼 추가를 검토할 수 있다.

```text
SPEC_HASH
SOURCE_TYPE
SOURCE_VERSION
CREATED_BY
APPROVED_AT
```

## 9. Builder 영향

영향 수준은 높다.

### 9.1 List Builder

현재 Q&A Bootstrap에만 있는 기능을 공통 입력 계약으로 옮겨야 한다.

- 업무 컬럼과 순서
- 컬럼별 폭 비율
- Preview 샘플 행
- SearchPanel 최대 폭
- Pagination 위치
- Action Area 위치
- Page Max Width
- Section Gap

Q&A 전용 컬럼 비율을 Builder에 하드코딩하면 안 된다.

```yaml
columns:
  - fieldId: number
    widthPercent: 8
  - fieldId: title
    widthPercent: 32
  - fieldId: writer
    widthPercent: 15
```

### 9.2 Form Builder

등록과 수정의 차이를 처리해야 한다.

| 구분 | qna-create | qna-update |
|---|---|---|
| Form Mode | CREATE | UPDATE |
| 초기값 | 비어 있음 | 기존 데이터 |
| 제목 | 질문 등록 | 질문 수정 |
| Primary Action | 등록 | 저장 |
| 취소 대상 | 목록 | 상세 |
| Pattern | crud.create | crud.update |

### 9.3 Detail Builder

상세 화면은 수정 버튼이 있더라도 필드에 `READ_ONLY` 문맥을 유지해야 한다. 수정 버튼은 편집 화면으로 이동하는 Action이다.

## 10. KRDS Runtime Resolver 영향

영향 수준은 낮음에서 중간이다.

Resolver는 이미 다음 작업을 수행한다.

```text
Screen Pattern 조회
→ Pattern Validator
→ Variant Rule Set 조회
→ Role별 Component Resolution
→ Component Contract Version 확정
```

큰 구조 변경은 필요하지 않지만 다음 Context가 Rule 평가에 포함되는지 확인해야 한다.

- CREATE와 UPDATE
- EDITABLE과 READ_ONLY
- 필수 여부
- 오류 상태
- Layout Density
- Platform
- Action 종류
- Table Header와 Body Cell

특히 `qna-create`와 `qna-update`가 모두 `FORM`으로 변환되는 경우 `semanticPattern` 또는 `formMode`를 Resolver에 전달해야 한다.

## 11. FigmaScreenExportService 영향

영향 수준은 중간이다.

현재 서비스는 목표 파이프라인의 대부분을 이미 지원한다.

```text
ScreenSpecification 조회
→ Page 선택
→ Builder 선택
→ Resolver 실행
→ 검증
→ FigmaScreenSpec 저장
```

필요한 핵심 보완은 Suite 단위 Batch Export다.

```java
exportSuite(screenSpecificationId, version)
```

반환 결과 예시는 다음과 같다.

```text
qna-list SUCCESS
qna-create SUCCESS
qna-detail SUCCESS
qna-update SUCCESS
qna-answer-list SUCCESS
qna-answer-detail SUCCESS
qna-answer-create SUCCESS
```

### 11.1 Suite 단위 원자성

현재 화면별 Export는 성공 후 개별 저장한다. 여러 화면을 생성할 때 한 화면만 실패하면 부분 저장이 발생할 수 있다.

권장 방식은 다음과 같다.

```text
1. 전체 화면을 메모리에서 생성
2. 전체 Pattern·Resolution·Inventory 검증
3. 하나라도 실패하면 저장하지 않음
4. 모두 성공하면 일괄 저장
```

실패 결과는 Generation Report로만 저장한다.

## 12. Bootstrap 영향

영향 수준은 매우 높다.

현재 실행 흐름은 다음과 같다.

```text
ApplicationRunner
→ KrdsQnaFixtureBootstrapService.bootstrap()
```

새 구조에서는 역할을 세 서비스로 분리하는 것이 안전하다.

```text
KrdsContractBootstrapService
→ Profile·Registry·Rule·Pattern·Inventory 등록

QnaScreenSpecificationBootstrapService
→ Q&A 업무 명세 등록 및 승인

QnaFigmaGenerationBootstrapService
→ 승인된 업무 명세에서 FigmaScreenSpec 생성
```

Runner는 다음 순서를 보장해야 한다.

```text
1. Profile
2. Registry
3. Variant Rule Set
4. Screen Pattern
5. Library Inventory
6. Q&A ScreenSpecification
7. FigmaScreenSpec 생성
8. Bundle Preview 검증
```

서비스를 분리하면 순환 의존 위험과 책임 혼합을 줄일 수 있다.

## 13. Bundle 영향

영향 수준은 낮다.

Bundle 형식은 그대로 유지할 수 있다.

```text
FigmaScreenSpec
+ DesignSystemProfile Snapshot
+ ComponentRegistry Snapshot
+ ExportMetadata
```

Plugin은 최종 Bundle만 사용하므로 원본이 완성형 JSON인지 ScreenSpecification인지 알 필요가 없다.

다만 Builder가 생성하는 Logical ID가 기존 Fixture와 달라질 경우 Plugin MERGE는 기존 노드를 재사용하지 못할 수 있다.

전환 첫 적용에서는 다음 중 하나가 필요하다.

- 기존 Logical ID 규칙 유지
- 기존 화면에 `REPLACE` 적용
- 명시적인 Logical ID Migration Mapping 제공

## 14. Plugin 영향

영향 수준은 낮다.

최종 Bundle 계약이 유지되면 Plugin의 대규모 변경은 필요 없다.

다음 속성이 새 Builder 결과에도 유지되는지 확인해야 한다.

- `componentMaxWidth`
- `columnWidthPercent`
- Action Area `BOTTOM_RIGHT`
- qna-update Editable Variant
- Screen Version 변경 시 Visual Baseline 재생성

## 15. 화면 수 영향

현재 Q&A는 6개 화면이다. 질문 수정 화면 `qna-update`를 추가하면 최소 7개가 된다.

```text
qna-list
qna-create
qna-detail
qna-update
qna-answer-list
qna-answer-detail
qna-answer-create
```

답변 수정 화면까지 추가하면 `qna-answer-update`를 포함해 총 8개가 된다.

영향 대상은 다음과 같다.

- Screen Suite Manifest
- Bootstrap 결과의 Screen ID 목록
- 필수 Frame 개수 검증
- Runtime Resolver 회귀 테스트
- Plugin Bundle Preview 테스트
- 문서의 “Q&A 6 Screens” 명칭
- Design System Profile 표시 이름
- Figma Page 이름

현재 `qna-detail`에 UPDATE Action이 있으므로 최소 7개 구성이 가장 일관된다.

## 16. DB 데이터 마이그레이션

기존 Q&A FigmaScreenSpec을 삭제할 필요는 없다.

```text
기존 Fixture 기반 화면
→ 과거 Screen Version 유지

새 ScreenSpecification 기반 화면
→ 다음 Screen Version으로 저장
```

관계는 다음과 같이 추적한다.

```text
AI_SCREEN_SPECIFICATION
qna-suite v3
        ↓
FigmaScreenSpec Repository
qna-list v7
qna-create v3
qna-detail v3
qna-update v1
```

`FigmaScreenSpec`에 이미 Screen Specification ID와 Version이 있으므로 기본 생성 출처 추적은 가능하다.

## 17. 실패 및 Rollback 영향

다음 실패를 고려해야 한다.

- Q&A 업무 명세 역직렬화 실패
- 실제 DB 컬럼 Binding 실패
- Screen Pattern 불일치
- Variant Rule 미해결
- Registry Entry 누락
- Inventory Drift
- Component Contract Version 불일치
- 여러 화면 중 일부만 생성 실패
- 동일 버전 내용 충돌

권장 원자성 정책은 다음과 같다.

```text
전체 화면 생성 성공
→ ScreenSpecification과 FigmaScreenSpec 확정

하나라도 실패
→ 새 FigmaScreenSpec을 저장하지 않음
→ 실패 Generation Report만 저장
```

Suite 전체 원자성을 확보하기 위한 `prepare → validate all → commit` 분리가 중요하다.

## 18. 테스트 영향

영향 수준은 높다.

### 18.1 기존 테스트 역할 변경

완성형 Q&A JSON Schema 테스트는 Bootstrap 입력 검증에서 Runtime 결과 Snapshot 비교 테스트로 역할을 변경한다.

### 18.2 신규 테스트

#### ScreenSpecification Fixture 계약

- Q&A Suite ID와 Version
- 필수 Page 7개
- Page별 Pattern
- Field ID와 순서
- Action 종류
- Route
- CREATE·UPDATE·DETAIL Mode

#### Builder 계약

- 목록 Header·Row·Cell
- 등록 Editable Form
- 상세 ReadOnly Detail
- 수정 Editable Update Form
- SearchPanel 최대 폭
- Table 컬럼 비율
- Action Area 위치

#### Resolver 회귀

- 모든 COMPONENT에 Resolution 존재
- unresolved 0
- fallback 0
- Rule ID 일치
- Variant Key 일치
- Context Hash 결정성

#### DB 통합

- ScreenSpecification 저장
- 승인 상태
- Page별 FigmaScreenSpec 저장
- 정확한 Profile·Registry 버전
- 동일 실행 멱등성
- 동일 버전 다른 내용 충돌

#### Plugin Bundle

- 모든 Bundle Preview 통과
- Frame 수 7
- Apply FATAL 0
- Atomic Rollback 검증
- Logical ID 중복 0

## 19. 예상 변경 영역

| 영역 | 예상 영향 |
|---|---|
| Q&A 완성형 Fixture | Bootstrap 입력에서 Snapshot으로 전환 |
| Q&A Screen Suite Manifest | 6개에서 7개 가능 |
| ScreenSpecification Fixture | 신규 |
| ScreenSpecification Import 서비스 | 신규 |
| Bootstrap Orchestration | 대폭 변경 |
| List Builder | 컬럼·Preview·Layout 정책 입력 지원 |
| Form Builder | CREATE/UPDATE Mode 보완 |
| Detail Builder | ReadOnly 계약 확인 |
| Runtime Resolver | Form Mode Context 보완 |
| Figma Export Service | Prepare/Batch Commit 검토 |
| Repository | 불변 저장 강화 |
| 계약 테스트 | 대폭 변경 |
| Runtime 테스트 | 7개 화면으로 확장 |
| Plugin | 경미한 보완 또는 변경 없음 |
| API | 기존 API 대부분 재사용 가능 |

## 20. 권장 전환 전략

Big Bang 방식으로 기존 Fixture Bootstrap을 즉시 제거하는 것은 권장하지 않는다.

### 20.1 1단계: 기존 Bootstrap 유지

```text
기존 완성형 Fixture
→ 기존 Q&A 화면 계속 제공

새 Q&A ScreenSpecification
→ 별도 ID와 Version으로 저장
```

### 20.2 2단계: Shadow Export

새 업무 명세로 임시 화면을 생성한다.

```text
runtime-qna-list
runtime-qna-create
runtime-qna-detail
runtime-qna-update
...
```

### 20.3 3단계: 동등성 Gate

다음 조건을 모두 통과해야 한다.

```text
필수 Role 일치
필드·Action 일치
Screen Pattern 통과
Component Resolution 100%
fallback 0
Plugin Preview 통과
Visual Gate 통과
```

### 20.4 4단계: Source of Truth 전환

동등성 Gate 통과 후 다음과 같이 전환한다.

```text
Q&A ScreenSpecification
→ Bootstrap Source of Truth

기존 Figma JSON
→ 회귀 Snapshot
```

실제 `qna-*` Screen ID로 다음 버전을 생성하고 기존 완성형 JSON Import를 비활성화한다.

## 21. 필수 선행 과제

안전한 전환을 위해 다음 세 가지를 우선 해결해야 한다.

1. `ScreenSpecificationService`에 DB Schema 자동 생성이 아닌 승인된 Fixture Import 경로 추가
2. Q&A 전용 목록·Form 표현을 공통 Builder 입력 계약으로 승격
3. 여러 화면을 모두 검증한 후 저장하는 Suite 단위 원자성 확보

추가로 다음 항목을 확정해야 한다.

- 실제 Q&A DB Schema 및 Field Binding
- qna-update 포함 여부
- 답변 수정 화면 포함 여부
- Logical ID 호환 정책
- Preview 샘플 데이터의 저장 위치
- ScreenSpecification 불변 버전 정책

## 22. 최종 판단

제안 구조로의 전환을 권장한다. 가장 큰 장점은 Q&A 화면이 KRDS Component Key에 종속된 완성형 JSON에서 벗어나 다른 업무 화면과 동일한 생성 파이프라인을 사용한다는 점이다.

영향 수준은 다음과 같다.

```text
Plugin·Bundle API: 낮음
Resolver: 중간
ScreenSpecification 모델: 중간
Builder·Bootstrap·테스트: 높음
데이터 손실 위험: 낮음
호환성 위험: 중간
장기 유지보수 개선 효과: 매우 높음
```

기존 Fixture를 즉시 제거하지 않고 Shadow Export와 동등성 Gate를 거쳐 단계적으로 Source of Truth를 전환하는 것이 가장 안전하다.
