# Semantic Figma 계약 운영 규칙

검증 기준일: 2026-07-28

이 문서는 `website-figma-contract`의 JSON Schema만으로 표현하기 어려운 교차 문서 일관성,
식별자 수명주기, 변경 정책과 컴포넌트 카탈로그 운영 규칙을 정의한다.

## 1. 식별자와 버전

| 값 | 형식 | 수명주기 |
|---|---|---|
| `screenId`, `designSystemId`, `profileId` | 소문자 영숫자로 시작, 소문자 영숫자와 `-_.` 사용, 1~64자 | 논리 자산이 유지되는 동안 불변 |
| `screenSpecificationId` | 위 형식과 동일하며 UUID 허용 | 원본 ScreenSpecification과 동일 |
| `screenVersion`, `designSystemVersion`, `profileVersion` | 1 이상의 정수 | 내용 변경 시 단조 증가 |
| `registryVersion` | 영숫자로 시작, 영숫자와 `-_.` 사용, 1~64자 | Figma Library Publish 단위로 신규 발급 |
| `schemaVersion` | 현재 `1` | breaking change 시 `v2` Schema를 추가 |

`registryVersion`은 Library 표시 버전과 동일할 필요는 없지만, 하나의 Published Library 상태를
유일하게 가리켜야 한다. 기존 버전에 다른 Component Key 집합을 덮어쓰지 않는다.

## 2. logicalNodeId

`logicalNodeId`는 `{pageId}/{section}/{fieldId}` 구조이며 각 세그먼트는 영문자 또는 숫자로 시작하고
영문자·숫자·`._:-`만 포함한다. `/`는 세그먼트 구분자다. `section`은 `table/row`처럼 계층 경로를
받을 수 있지만 빈 세그먼트, 선행·후행 `/`, 세그먼트 안의 기타 문자는 허용하지 않는다.

- 같은 업무 의미의 노드는 화면 버전이 바뀌어도 ID를 보존한다.
- 표시명, 순서, 스타일 변경은 ID 변경 사유가 아니다.
- 업무 의미가 달라진 신규 노드만 새 ID를 발급한다.
- 한 화면 트리에서 중복 ID는 `DUPLICATE_LOGICAL_NODE_ID` 오류로 생성을 중단한다.
- 삭제된 ID를 같은 화면 버전 계열의 다른 의미에 재사용하지 않는다.
- 반복 행은 데이터 PK가 아닌 반복 템플릿 ID를 사용한다. 실제 행은 Figma 생성기가 별도 인스턴스로 관리한다.

## 3. 변경 정책

| 정책 | 의미 | 기존 노드가 있을 때 |
|---|---|---|
| `CREATE` | 신규 화면/노드 생성 전용 | 충돌 오류 |
| `MERGE` | 기본 갱신 정책 | 동일 `logicalNodeId`를 재사용하고 Screen 소유 속성만 갱신 |
| `REPLACE` | 명시적으로 전체 재생성 | 사용자 Preview 승인 후 교체, 제거 노드는 Archive |
| `SKIP` | 해당 노드 변경 제외 | 현재 Figma 상태 유지 |

사용자 직접 수정 속성은 `USER_OVERRIDE`, 컴포넌트 외형은 `DESIGN_SYSTEM`, 업무 값과 구조는
`SCREEN_SPEC` 소유로 취급한다. `MERGE`는 다른 소유자의 값을 덮어쓰지 않는다.

## 4. 화면유형 매핑과 실패 정책

`screenType`은 `PageSpec.template`의 `_LIST`, `_FORM`/`_REGIST`, `_DETAIL` 접미사를 우선 사용하고,
값이 없을 때만 `ScreenSpecification.archetype`에 같은 규칙을 적용한다.

`layoutPattern`은 별도로 `MASTER_DETAIL`, `DASHBOARD`, 그 외 `STANDARD`로 판정한다.
따라서 `MASTER_DETAIL`처럼 두 판정에 관련되는 문자열도 서로 충돌하지 않는다.

지원 접미사가 없는 자유 문자열은 임의의 화면유형으로 바꾸지 않는다.
`UNSUPPORTED_SCREEN_TYPE` 오류를 반환하고 사용자 또는 상위 호출자가 화면유형을 명시해야 한다.

## 5. Bundle 교차 문서 일관성

JSON Schema 검증 후 다음 값의 동일성을 의미 검증한다.

- Screen의 `profileId/profileVersion/registryVersion`
- Profile의 `id/version/registryVersion`
- Registry의 `profileId/profileVersion/registryVersion`
- Bundle metadata의 Screen/Profile/Registry 버전

불일치는 `*_MISMATCH` 오류로 import를 중단한다. 실제 Published Library의 Component Key가 Registry와
다르면 `COMPONENT_KEY_MISMATCH`로 처리하고 Registry 동기화 전에는 화면을 생성하지 않는다.

## 6. 컴포넌트 카탈로그

`component-catalog-v1.json`이 논리 컴포넌트, Figma Property, 코드 속성, fallback의 기준이다.

- `requiredComponents`: 1차 생성에 반드시 존재해야 하는 KRDS/eGovFrame 컴포넌트
- `optionalComponents`: 없어도 fallback 가능한 확장 컴포넌트
- `patterns`: 여러 컴포넌트의 의미 조합
- `pageTemplates`: 화면 골격 템플릿
- `aliases`: 논리명 변경 호환성
- `replacement`: 폐기 컴포넌트의 대체 논리명

지원하지 않는 선택 속성은 카탈로그의 `fallback`에 따라 기본값 사용, 노드 생략 또는 경고를 적용한다.
필수 컴포넌트가 카탈로그나 Published Registry에 없으면 임의 프레임으로 대체하지 않고 오류를 반환한다.
카탈로그의 초기 목록은 기술 기준선이며, 조직 Library 담당자의 Preview 승인 후 운영 기준으로 확정한다.

## 7. Plugin 입력 정책

Semantic Figma v1의 기본 입력은 `figma-export-bundle-v1` 계약을 따르는
`.figma-export-bundle.json` 파일이다. REST 직접 조회는 운영 환경에서 명시적으로 활성화하는
선택 기능이며, REST를 사용하지 않아도 모든 핵심 생성·동기화 기능이 동작해야 한다.

- REST 사용 시 단기 Bearer Token을 우선한다.
- Plugin에 장기 비밀정보를 저장하지 않는다.
- 허용 서버 도메인과 CORS origin은 배포 환경별 설정으로 관리한다.
- REST 실패 시 동일 Bundle의 파일 입력으로 복귀할 수 있어야 한다.

## 8. Removed Node 정책

새 Spec에서 사라진 기존 논리 노드는 삭제하지 않고 화면별
`🗄 Removed — {screenId}` Frame으로 Archive한다. `REPLACE` 대상의 기존 Root도 같은 정책을 따른다.

- v1 Plugin은 자동 `DELETE`와 노드별 `ASK` 분기를 제공하지 않는다.
- Archive 영구 삭제는 동기화와 분리된 사람의 운영 작업이다.
- 삭제 전 Backup, 생성 보고서와 복구 필요성을 확인한다.
- DELETE·ASK 요구가 생기면 보고서 enum과 호환성 영향을 포함해 별도 결정으로 재검토한다.

## 9. 호환성

- v1 소비자는 v1 Schema와 카탈로그만 지원한다.
- 선택 속성 추가는 하위 호환 변경으로 허용한다.
- 필수 속성 추가, enum 제거/이름 변경, 의미 변경은 breaking change이며 v2 파일로 추가한다.
- producer는 자신이 생성한 `schemaVersion`을 명시하고 consumer는 미지원 버전을 명확히 거부한다.
- Schema 변경 시 계약 fixture, Spring 테스트, Extractor 테스트, Plugin 테스트를 모두 통과해야 한다.
