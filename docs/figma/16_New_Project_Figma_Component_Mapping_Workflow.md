# 신규 프로젝트 Figma 컴포넌트 매핑 실무 플로우

## 목적

신규 프로젝트에서 `ScreenSpecification` → `FigmaExportBundle` → Figma 적용까지를 안정적으로 진행하기 위한 실무 절차를 정리한다.

핵심 목표는 다음 3가지를 일관되게 맞추는 것이다.

- `logicalType`
- `componentName`
- `componentSetKey`

이 3개가 분리되지 않으면, 번들은 정상처럼 보여도 Figma 적용 단계에서 경고나 fallback이 발생한다.

---

## 1. 용어 정리

### 1-1. `logicalType`

번들/코드에서 사용하는 계약용 식별자다.

예:

- `egov.listPage`
- `egov.searchPanel`
- `krds.button`
- `krds.textField`

이 값은 화면 구조와 조합 규칙을 정의하는 기준이다.

### 1-2. `componentName`

Figma에서 사람이 읽는 컴포넌트 이름이다.

예:

- `List Page`
- `Search Panel`
- `Button`
- `Text Field`

이 값은 라이브러리 문서성과 운영 편의성을 위한 이름이다.

### 1-3. `componentSetKey`

Figma가 내부적으로 사용하는 실제 키다.

이 값은 플러그인이 `importComponentSetByKeyAsync()`로 직접 쓰는 실행용 키다.

중요:

- 사람이 임의로 정하는 값이 아니다.
- 실제 Figma Component Set에서 export해야 한다.
- placeholder 문자열이면 실제 import가 실패한다.

---

## 2. 역할 분리

신규 프로젝트에서는 `egov`와 `krds`의 역할을 먼저 분리한다.

### 2-1. `egov`

상위 패턴 레이어다.

페이지/영역 단위 구조를 담당한다.

예:

- `egov.listPage`
- `egov.pageHeader`
- `egov.searchPanel`
- `egov.dataTable`
- `egov.actionArea`

### 2-2. `krds`

하위 기본 UI 레이어다.

실제 입력/조작/페이징 같은 원자 컴포넌트를 담당한다.

예:

- `krds.button`
- `krds.textField`
- `krds.select`
- `krds.pagination`

### 2-3. 관계

- `egov`는 화면 구조
- `krds`는 화면 부품

즉, `egov.listPage`가 화면 뼈대를 만들고, 그 안에서 `krds.*`가 사용된다.

---

## 3. 실무 플로우

### 3-1. 디자인 시스템 기준을 먼저 고정한다

가장 먼저 해야 할 일은 디자인 시스템 계약을 고정하는 것이다.

이 단계에서 결정하는 것:

- 어떤 논리 타입을 쓸지
- 각 타입이 어느 레이어에 속하는지
- 어떤 화면 패턴이 허용되는지

이때는 아직 Figma 이름을 손보는 단계가 아니다.

### 3-2. Figma 라이브러리 파일에 Component Set을 만든다

Figma에서 실제 `COMPONENT_SET`을 만든다.

예:

- `List Page`
- `Page Header`
- `Search Panel`
- `Data Table`
- `Action Area`
- `Button`
- `Text Field`
- `Select`
- `Pagination`

이 단계의 규칙:

- 반드시 `COMPONENT_SET`이어야 한다.
- `COMPONENT` 단일 노드만 있으면 안 된다.
- publish 가능한 상태여야 한다.

### 3-3. 컴포넌트 이름과 논리 타입을 1:1로 연결한다

예시 매핑:

| logicalType | componentName |
|---|---|
| `egov.listPage` | `List Page` |
| `egov.pageHeader` | `Page Header` |
| `egov.searchPanel` | `Search Panel` |
| `egov.dataTable` | `Data Table` |
| `egov.actionArea` | `Action Area` |
| `krds.button` | `Button` |
| `krds.textField` | `Text Field` |
| `krds.select` | `Select` |
| `krds.pagination` | `Pagination` |

이 단계는 Figma 문서상 이름을 논리 계약과 맞추는 작업이다.

### 3-4. Author Plugin으로 Registry를 export한다

Figma 라이브러리를 기준으로 Component Registry를 export한다.

이때 들어가야 하는 것:

- `componentSetKey`
- `componentName`
- `publishStatus`
- `variants`
- `properties`

핵심은 `componentSetKey`다.

이 값이 실제 Figma key가 아니면 플러그인이 import할 수 없다.

### 3-5. Registry를 승인하고 고정한다

Registry는 단순 목록이 아니라 계약이다.

운영 시에는 다음 상태를 명확히 한다.

- `DRAFT`
- `REVIEW_REQUIRED`
- `APPROVED`
- `PUBLISHED`

실무에서는 최소한 다음 원칙을 지킨다.

- `PUBLISHED` Registry만 화면 번들에서 사용한다.
- 중간 버전은 화면 생성에 쓰지 않는다.
- key 변경은 breaking change로 관리한다.

### 3-6. ScreenSpecification을 만든다

승인된 디자인 시스템 기준으로 화면 구조를 작성한다.

예:

- `user-list`
- `user-detail`
- `user-edit`

이 단계에서 화면은 논리 타입 기준으로만 작성한다.

예:

- `egov.listPage`
- `egov.searchPanel`
- `krds.button`

### 3-7. Figma Export Bundle을 생성한다

Bundle에는 보통 아래가 포함된다.

- `figmaScreenSpec`
- `designSystemProfile`
- `componentRegistry`
- `metadata`

이 Bundle이 플러그인의 기본 입력이 된다.

### 3-8. Figma 플러그인에 적용한다

플러그인은 보통 다음 순서로 컴포넌트를 찾는다.

1. `componentSetKey`로 직접 import
2. 실패하면 현재 Figma 문서에서 `componentName`으로 탐색
3. 그래도 실패하면 fallback 경고

정상 상태는 1번이다.

2번은 보조 경로다.

3번은 최후의 안전장치다.

---

## 4. 신규 프로젝트 표준 운영 순서

실무에서는 아래 순서로 가는 것이 가장 안전하다.

### 단계 A. 디자인 시스템 준비

- `egov` / `krds` 역할 분리
- 논리 타입 목록 확정
- Figma 라이브러리 파일 생성
- Component Set 이름 확정

### 단계 B. Registry 작성

- Figma에서 Component Set export
- 실제 `componentSetKey` 확보
- `publishStatus = CURRENT` 확인
- Registry snapshot 저장

### 단계 C. ScreenSpecification 작성

- 화면 이름/라우트/페이지 정의
- `logicalType` 기준으로 조합
- 검색영역/테이블/액션영역 분리

### 단계 D. Bundle 생성

- 승인된 ScreenSpecification 사용
- 승인된 Profile/Registry snapshot 결합
- `.figma-export-bundle.json` 생성

### 단계 E. Figma 적용

- 플러그인에서 파일 import
- Preview 확인
- Apply 실행
- 경고는 fallback 가능성을 먼저 확인

### 단계 F. 재검증

- Figma에서 컴포넌트 연결 상태 확인
- 이름 불일치 여부 확인
- key mismatch 여부 확인
- 필요 시 Registry 재-export

---

## 5. 실무 체크리스트

### 5-1. Figma 라이브러리 체크

- [ ] `COMPONENT_SET`으로 만들어졌는가
- [ ] 이름이 `List Page`, `Button`처럼 명확한가
- [ ] publish 상태가 `CURRENT`인가
- [ ] 실제 `componentSetKey`가 export 가능한가

### 5-2. Registry 체크

- [ ] `logicalType`과 `componentName`이 대응되는가
- [ ] `componentSetKey`가 placeholder가 아닌가
- [ ] `publishStatus = CURRENT`인가
- [ ] `libraryFileKey`가 design system profile과 일치하는가

### 5-3. ScreenSpecification 체크

- [ ] 승인 상태가 `APPROVED`인가
- [ ] 화면 구조가 `egov` 패턴 기준인가
- [ ] 하위 요소가 `krds` 기본 UI로 구성되는가
- [ ] 불필요한 중복이나 애매한 타입이 없는가

### 5-4. Bundle 체크

- [ ] `figmaScreenSpec` / `designSystemProfile` / `componentRegistry` / `metadata`가 모두 있는가
- [ ] 버전 간 일치가 맞는가
- [ ] `componentSetKey`가 실제 값인가

### 5-5. Figma 적용 체크

- [ ] 현재 Figma 문서에 해당 Component Set이 존재하는가
- [ ] 이름이 번들 기대값과 일치하는가
- [ ] import 실패가 fallback 경고인지, 구조적 누락인지 구분되는가

---

## 6. 자주 나는 실패 원인

### 6-1. `componentSetKey`가 placeholder

증상:

- import 실패
- fallback 경고 발생

원인:

- 샘플 JSON이나 fixture가 실제 export 결과를 대체함

### 6-2. Figma 문서에 Component Set이 없음

증상:

- `COMPONENT_IMPORT_FALLBACK`

원인:

- 현재 열어둔 Figma 파일에 라이브러리 컴포넌트가 없거나
- 이름이 번들과 다름

### 6-3. 이름은 맞는데 publish되지 않음

증상:

- import는 되지만 연결이 불안정하거나 경고가 남음

원인:

- `PUBLISHED` 상태가 아님

### 6-4. `egov` 상위 패턴만 있고 `krds` 하위 요소가 없음

증상:

- 상위 프레임은 보이는데 실제 버튼/입력은 fallback

원인:

- 기본 UI 세트가 라이브러리에 없음

---

## 7. 실무 권장 원칙

- Figma 이름은 사람이 읽는 이름으로 고정한다.
- 번들에는 논리 타입만 쓰지 말고 실제 `componentSetKey`를 함께 넣는다.
- Registry는 화면보다 먼저 승인한다.
- 화면은 Registry를 소비만 한다.
- placeholder key는 테스트용으로만 보고 운영에 쓰지 않는다.

---

## 8. 한 줄 요약

신규 프로젝트에서는:

1. `egov`/`krds` 논리 계약을 먼저 정하고
2. Figma 라이브러리의 `COMPONENT_SET` 이름을 맞추고
3. Author Plugin으로 실제 `componentSetKey`를 export한 뒤
4. 승인된 Registry로 `ScreenSpecification`을 만들고
5. 그 Bundle로 Figma 화면을 적용한다.

