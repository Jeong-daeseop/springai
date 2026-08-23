# Figma Design System 통합 구현 목록 (FTC 정부 포털 Design System 확정)

> 문서 버전: 1.3
> 작성일: 2026-08-21
> 관련 문서: [26_FTC_to_KRDS_Design_System_Consolidation_Impact_Analysis.md](./26_FTC_to_KRDS_Design_System_Consolidation_Impact_Analysis.md)(영향 분석), [27_FTC_to_KRDS_Design_System_Consolidation_Implementation_Specification.md](./27_FTC_to_KRDS_Design_System_Consolidation_Implementation_Specification.md)(실행 설계)
> 성격: 구현 체크리스트.
>
> **v1.1 확정**: 통합 대상이 FTC 정부 포털 Design System으로 확정됨에 따라(26/27번 문서 v1.1 참고), v1.0의 R0~R6(22개 항목, KRDS 전환 전제) 전체를 폐기하고 훨씬 작은 범위로 다시 썼다.
>
> **v1.2**: R0/R1/R2-002를 실행·완료했다. R2-001은 Figma에서 직접 조회해 "값이 이미 stale해 보인다"는 간접 증거를 확보했으나 Component Property 패널의 정확한 현재값까지는 확인하지 못해 `[~]`로 남긴다(아래 근거 참고). R3(Figma Desktop 라이브 검증)은 브라우저 자동화로 닿지 않아 여전히 사람 확인 필요.
>
> **v1.3**: R3-002를 로컬 서버 + 실제 MCP `convertPlatform` 호출로 완전히 검증했다(KRDS fileKey가 정확히 차단됨). R3-001도 같은 방식으로 allowlist 게이트 통과까지는 검증했으나, Figma 캔버스에 실제로 그려지는지는 여전히 Figma Desktop이 필요해 `[~]`로 남긴다. 남은 사람 확인 항목은 R3-001의 시각적 확인과 R3-004(Search Panel 마스터 컴포넌트 확인)뿐이다.

---

## 1. 상태 및 우선순위 표기

| 표기 | 의미 |
|---|---|
| `[x]` | 완료, 증적으로 검증됨 |
| `[ ]` | 미구현 |
| `[~]` | 일부 구현되었으나 보완 필요 |
| `[!]` | 선행 결정 또는 외부 조건 때문에 착수 불가 |

| 우선순위 | 의미 |
|---|---|
| P0 | 결정과 실제 설정의 불일치를 없애는 데 직접 필요 |
| P1 | 정합성 확보에 필요 |
| P2 | 정리·문서화 성격 |

---

## 2. R0 — 설정 정합성 확보

- [x] **R0-001 · P0** `.env`의 `FIGMA_ALLOWED_FILE_KEYS`에 FTC fileKey(`mVy5h1UbORVqQoBm8Wr1bT`) 추가 — 완료(2026-08-21)
- [x] **R0-002 · P0**(DEC 성격) `FIGMA_ALLOWED_FILE_KEYS`에서 KRDS fileKey(`6fcm04dwSEH2IUizZfaZCj`) 제거 — R0-004에서 다른 참조가 없음을 확인해 완전 제거로 결정, `.env`에 사유 주석 남김
- [x] **R0-003 · P0** allowlist 변경이 각 환경별 설정에도 반영되는지 확인 — `application-dev/prod/test.yaml`에 이 값을 별도로 override하는 곳 없음(전부 `.env`의 `FIGMA_ALLOWED_FILE_KEYS` 단일 소스) 확인 완료
- [x] **R0-004 · P1** 코드베이스 전체 grep 조사 — `FigmaFileAllowlistValidator`(`app.design-vision.figma.allowed-file-keys` ← `FIGMA_ALLOWED_FILE_KEYS`)가 유일한 소비 경로이며 `FigmaDesignOrchestrationService`에서 실제로 `validateFileKey()`를 호출해 강제하고 있음을 확인. KRDS fileKey(`6fcm04dwSEH2IUizZfaZCj`)는 Java/TypeScript/yaml/json 어디에도 다른 참조가 없음(문서 파일에만 있었음) — allowlist 수정만으로 충분

## 3. R1 — 조직 결정 문서 정정

- [x] **R1-001 · P0**(DEC 성격) `14_DEC02_DEC09_Component_Catalog_Approval_Request.md` 체크포인트 2, DEC-09 확인 항목, 문서 헤더의 "KRDS Figma Library 관례" 문구를 "FTC 정부 포털 Design System Library 관례"로 정정(v1.5). 승인된 값 자체는 이미 FTC 기준으로 맞았으므로 재승인 없이 표기만 정정
- [x] **R1-002 · P1** `12_...md` §4 DEC-01 재확정 경위를 §19 변경 이력(4.24)에 기록 — 완료

## 4. R2 — 데이터 최신성 확인

- [x] **R2-001 · P1** `component-catalog-v1.json`의 Search Panel 매핑을 FTC 현재 컴포넌트 계약으로 갱신. FTC Search Panel 마스터 `75:98`을 Figma MCP로 조회한 결과 공개 Property는 `Type`(`Simple`/`Advanced`), `Size`(`medium`/`large`/`xlarge`), `State`(`default`/`focus`/`disabled`)이며 Label/Hint 텍스트 Property는 노출되지 않는다. 기존 stale 노드 ID(`"Lable#723:0"`, `"↪️Hint#1275:39"`)를 제거하고 해당 Variant 축으로 교체했다. `search__pc`(`420:10492`)와 `search__mo`(`420:10659`)는 FTC Search Panel의 반응형 구현 항목으로 기록한다.
- [x] **R2-002 · P2** 다른 하드코딩된 FTC 특정 노드 참조가 카탈로그에 더 있는지 전수 확인 — `grep figmaProperty component-catalog-v1.json` 결과 `krds.searchPanel`의 `label`/`placeholder` 2건이 유일함. 다른 6개 컴포넌트(button/textField/select/checkbox/pagination/pageHeader)는 전부 노드 ID 없는 순수 property명만 사용 — 추가로 손볼 곳 없음

## 5. R3 — 라이브 검증 (Figma Desktop, 사람 확인 필요 — 브라우저 자동화 불가)

- [~] **R3-001 · P0** allowlist 수정 후 FTC 파일 기준 요청이 정상 동작하는지 확인 — 로컬 서버(`.env` 로드)에서 실제 MCP `convertPlatform` 호출로 검증 완료: fileKey=`mVy5h1UbORVqQoBm8Wr1bT`(FTC)가 이제 allowlist를 통과해 `status: "ANALYZED"`까지 정상 진행됨. `FigmaFileAllowlistValidatorTest.enforcesTheApprovedFtcFileAndRejectsTheRetiredKrdsFile` 회귀 테스트도 추가했다. 허구의 `screenSpecificationId`라 그 이후 단계에서는 당연히 막힘 — allowlist 게이트 자체가 통과한다는 것만 확인 범위. **Figma 캔버스에 실제로 화면이 그려지는지까지는 Figma Desktop이 필요해 이 부분만 사람 확인으로 남음**
- [x] **R3-002 · P1** KRDS fileKey 요청이 차단되는지 확인 — 같은 방식으로 실제 MCP 호출 검증 완료: fileKey=`6fcm04dwSEH2IUizZfaZCj`(KRDS)가 `isError: true`, `"Figma 파일 접근이 허용되지 않습니다: 6fcm04dwSEH2IUizZfaZCj"`로 정확히 거부됨(`FigmaFileAllowlistValidator`의 `FigmaAllowlistException` 메시지와 일치)
- [x] **R3-003 · P1** Java 전체 테스트(`./gradlew test`) 통과 확인 — 완료, BUILD SUCCESSFUL(allowlist는 `.env`/런타임 설정이라 컴파일된 테스트에 직접 영향 없음을 함께 확인)
- [x] **R3-004 · P1** FTC Search Panel 마스터(`75:98`, `[FTC 고유] Search Panel`)와 반응형 항목(`search__pc` `420:10492`, `search__mo` `420:10659`)을 확인하고, `component-catalog-v1.json` 및 v2의 `krds.searchPanel` 매핑을 `Type`/`Size`/`State` Variant Property로 갱신했다. 기존 stale Label/Hint node 참조는 제거했다.

---

## 6. 폐기된 항목 (v1.0, KRDS 전환 전제 — 더 이상 유효하지 않음)

v1.0의 R1(카탈로그 갱신)·R2(Registry/DB 마이그레이션) 전체와 R3~R6의 다수 항목은 "KRDS로 전환"을 전제로 한 것이라 이번 결정(FTC 확정)과 맞지 않아 폐기한다. 특히 다음은 **하지 않는다**:

- 카탈로그를 KRDS 값(`Type`, `tertiary`, Size 5단계 등)으로 바꾸는 작업
- Registry/DB의 FTC 데이터를 KRDS로 마이그레이션하는 작업
- KRDS의 아이콘 속성(Show left/right icon)을 카탈로그에 추가하는 작업

## 7. 요약

방향이 KRDS가 아니라 FTC로 확정되면서, 실제 필요한 작업은 **"설정을 결정에 맞추는 것"(R0)과 "승인 문서의 라이브러리 이름 오탈자를 정정하는 것"(R1)** 두 가지로 크게 줄었다. 카탈로그·Registry·저장 데이터는 이미 FTC 기준이라 손댈 필요가 없다는 게 이번 방향 확정의 실질적 이점이다.
