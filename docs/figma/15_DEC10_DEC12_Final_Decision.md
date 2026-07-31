# DEC-10 · DEC-12 최종 결정

> 문서 버전: 1.0  
> 결정일: 2026-07-28  
> 적용 범위: Semantic Figma v1, `figma-screen-spec-plugin`  
> 관련 문서: [12_Semantic_Figma_Design_System_Implementation_List.md](./12_Semantic_Figma_Design_System_Implementation_List.md) §4,
> `website-figma-contract/CONTRACT_RULES.md` §7~§8

---

## 1. 결정 요약

| ID | 최종 결정 | v1 기본 동작 |
|---|---|---|
| DEC-10 | **FILE 우선, REST 선택 기능** | `.figma-export-bundle.json`을 내려받아 Plugin에서 불러온다. REST 직접 조회는 운영 환경이 명시적으로 활성화한 경우에만 사용한다. |
| DEC-12 | **ARCHIVE 단일 정책** | 새 Spec에서 사라진 노드는 삭제하지 않고 `🗄 Removed — {screenId}` Frame으로 이동한다. Plugin의 DELETE·ASK 분기는 v1 범위에서 제공하지 않는다. |

두 결정은 새로운 구현을 요구하는 선택이 아니라 현재 구현을 v1 운영 기준으로 확정하는 결정이다.

---

## 2. DEC-10 — Plugin 입력 연결 방식

### 2.1 확정 내용

1. 기본 입력은 `figma-export-bundle-v1` 계약을 따르는
   `.figma-export-bundle.json` 파일이다.
2. REST 직접 조회는 대량·반복 동기화가 필요한 운영 환경을 위한 선택 기능이다.
3. REST를 활성화하지 않아도 화면 생성·동기화의 모든 핵심 기능을 사용할 수 있어야 한다.
4. REST 장애, 인증 실패 또는 네트워크 차단 시 파일 입력으로 복귀한다.
5. Plugin에는 장기 비밀정보를 저장하지 않는다. REST 사용 시 단기 Bearer Token을 우선하고,
   `X-API-Key`는 제한된 운영·진단 상황에서만 사용한다.

### 2.2 선택 근거

- 파일은 Bundle 자체가 검토·보관 가능한 불변 입력이어서 승인 이력과 재현성이 좋다.
- Figma Plugin의 서버 접근 도메인, CORS, 사내망 연결 상태에 기본 흐름이 종속되지 않는다.
- REST 구현은 이미 존재하므로 반복 작업이 많아질 때 별도 재개발 없이 선택적으로 활성화할 수 있다.
- 운영 서버 도메인은 배포 환경마다 다르므로 결정 문서에 특정 값으로 고정하지 않는다.

### 2.3 REST 활성화 조건

아래 조건을 모두 충족한 환경에서만 REST 직접 조회를 운영 입력으로 노출한다.

- Plugin `manifest.json`의 `networkAccess.allowedDomains`에 실제 HTTPS 서버 도메인 등록
- `app.figma.rest-allowed-origins`에 실제 Figma Plugin origin 등록
- `app.figma.rest-token-secret` 설정 및 단기 토큰 발급·만료 검증
- 401 즉시 중단, 5xx·네트워크 오류 재시도, 파일 fallback 안내 검증

현재 저장소의 `localhost`/`127.0.0.1` 설정은 개발 환경 값이며 운영 도메인 결정 미완료를
의미하지 않는다. 운영 배포 시 환경별 값으로 교체하는 배포 작업이다.

---

## 3. DEC-12 — Removed Node 예외 처리 정책

### 3.1 확정 내용

1. `MERGE`에서 새 Spec에 없는 기존 논리 노드는 항상 Archive한다.
2. `REPLACE`의 기존 Root Frame도 새 화면 생성 전에 Archive한다.
3. Archive 노드는 반투명 처리하고 화면별 `🗄 Removed — {screenId}` Frame 아래에 보관한다.
4. Plugin은 동기화 중 노드를 즉시 삭제하지 않는다.
5. 노드별 확인 대화상자(`ASK`)와 자동 삭제(`DELETE`)는 v1 계약·UI·보고서 enum에 추가하지 않는다.
6. Archive 영구 삭제는 Plugin 동기화와 분리된 사람의 운영 작업이며, Backup·보고서와
   복구 필요성을 확인한 뒤 수행한다.

### 3.2 선택 근거

- Figma 노드에는 Screen Spec이 소유하지 않는 Prototype 연결, Annotation, 사용자 보정이
  남아 있을 수 있어 자동 삭제의 손실 비용이 크다.
- `ASK`를 노드마다 수행하면 대량 동기화가 중단되고 실행 결과가 사용자 선택 순서에 따라 달라진다.
- Archive는 멱등 동기화, 변경 Preview, 사후 복구를 모두 유지한다.
- 현재 `ReconciliationChange.changeType`과 생성 보고서가 `ARCHIVE`를 지원하며 테스트도 같은
  결과를 고정하고 있다.

### 3.3 재검토 조건

다음 요구가 실제 운영 지표로 확인될 때 별도 DEC와 Schema 버전 검토를 거쳐 다시 연다.

- Archive 누적으로 Figma 파일 크기나 성능이 운영 한도를 넘는 경우
- 법적·보안상 특정 노드를 즉시 파기해야 하는 보존 정책이 생긴 경우
- 화면 단위가 아닌 노드 단위 승인 Workflow가 제품 요구사항으로 확정된 경우

그 전에는 DELETE·ASK 미구현을 결함이나 미완료 항목으로 추적하지 않는다.

---

## 4. 완료 근거

| 결정 | 구현 근거 | 검증 근거 |
|---|---|---|
| DEC-10 FILE | Bundle 다운로드 API, Plugin `LOAD_BUNDLE` | Bundle 계약·다운로드·Plugin core 테스트 |
| DEC-10 REST 선택 | Plugin `FETCH_BUNDLE`, 단기 토큰, CORS, 재시도·파일 fallback | `FigmaRestTokenServiceTest`, `FigmaApiSecurityTest`, Plugin 빌드·타입 검사 |
| DEC-12 ARCHIVE | `reconcile()`, `archiveNode()`, `ensureArchiveFrame()` | `reconciliation reuses, moves, adds and archives deterministically` |

따라서 DEC-10과 DEC-12는 구현 보완 대기가 아니라 **결정 완료(`[x]`)**로 관리한다.

