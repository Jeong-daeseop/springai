# Figma Design System 통합 영향 분석서 (FTC 정부 포털 Design System 확정)

> 문서 버전: 1.1
> 작성일: 2026-08-21
> 관련 문서: [12_Semantic_Figma_Design_System_Implementation_List.md](./12_Semantic_Figma_Design_System_Implementation_List.md) §4 DEC-01, [14_DEC02_DEC09_Component_Catalog_Approval_Request.md](./14_DEC02_DEC09_Component_Catalog_Approval_Request.md), [24_R0-028_Table_Card_Swap_Logical_Type_Mismatch_Review.md](./24_R0-028_Table_Card_Swap_Logical_Type_Mismatch_Review.md)
> 후속 문서: [27_FTC_to_KRDS_Design_System_Consolidation_Implementation_Specification.md](./27_FTC_to_KRDS_Design_System_Consolidation_Implementation_Specification.md), [28_FTC_to_KRDS_Design_System_Consolidation_Implementation_List.md](./28_FTC_to_KRDS_Design_System_Consolidation_Implementation_List.md)
> 성격: 영향 분석서. **구현 승인 전이며 코드·설정 변경은 포함하지 않는다.**
>
> **v1.1 확정**: v1.0은 "FTC → KRDS 통합"(KRDS로 일원화) 방향을 전제로 작성됐다. 이후 조직 결정으로 **정반대 방향, 즉 통합 대상은 FTC 정부 포털 Design System으로 확정**됐다 — 이는 `12_...md` §4 DEC-01이 원래 확정했던 방향과 같다(DEC-01 자체는 바뀌지 않는다). 이 방향 전환에 맞춰 영향 분석 전체를 다시 썼다. v1.0에서 조사한 두 라이브러리 구조 차이 자체(3장)는 사실관계로서 유효하므로 그대로 남기되, "그래서 무엇을 해야 하는가"라는 결론은 정반대로 바뀐다 — **카탈로그를 KRDS에 맞춰 바꿀 필요가 없고(이미 FTC 기준으로 맞게 돼 있음), 대신 KRDS 쪽으로 새 오염이 들어오지 않도록 정리하는 게 목표다.**

---

## 1. 배경 및 목적

이 프로젝트는 Figma 디자인 시스템 라이브러리를 두 개 참조할 수 있는 상태였다:

- **FTC 정부 포털 Design System**(fileKey `mVy5h1UbORVqQoBm8Wr1bT`) — `12_...md` §4 DEC-01이 이미 확정한 라이브러리. 실제 화면(예: `eGovFrame` 파일의 `qna-list`, node `388:1123`)이 참조하는 Published Component가 이 라이브러리 소속이다. `component-catalog-v1.json`의 property 이름·값(`Style`, `Primary`/`Secondary`/`Ghost` 등)도 전부 FTC 실제 구조를 그대로 반영한다.
- **KRDS_v1.0.0 (Community)** — 별도의 커뮤니티 라이브러리. 화면 생성 파이프라인에서 실제로 쓰인 적이 없다.

조직은 이번에 **"통합된 프레임은 FTC 정부 포털 DS를 사용한다"**고 확정했다. 이 문서는 그 결정을 실행하는 데 필요한 영향(주로 "KRDS가 실수로 섞여 들어가지 않게 정리해야 할 지점")을 정리한다.

---

## 2. 가장 중요한 발견: 설정(`.env`)이 결정과 어긋나 있다 — 고쳐야 할 것은 여기다

`DEC-01`(`12_...md:124`)은 FTC를 이미 확정했다. 그런데 실제 운영 게이트인 `.env`의 `FIGMA_ALLOWED_FILE_KEYS`를 확인한 결과:

```
FIGMA_ALLOWED_FILE_KEYS=6fcm04dwSEH2IUizZfaZCj,p1QrZlmIgd5i5M0OAf6TsA
```

- 이 두 키 중 **어느 것도 FTC의 fileKey(`mVy5h1UbORVqQoBm8Wr1bT`)가 아니다.**
- `6fcm04dwSEH2IUizZfaZCj`는 같은 `.env`의 `FIGMA_RELEASE_A_URL` 값에서 이미 `"KRDS_v1.0.0--Community-"`로 확인된다.

**결론: 지금 "어느 Figma 파일에 대한 요청을 허용할지" 판단하는 실제 보안 게이트(allowlist)엔 결정된 FTC가 빠져 있고, 대신 쓰지 않기로 한 KRDS가 들어가 있다.** 이게 이번 통합에서 실질적으로 고쳐야 하는 핵심 항목이다 — FTC fileKey를 allowlist에 추가하고, 쓰지 않기로 한 KRDS fileKey는 제거(또는 명시적으로 비활성 상태로 문서화)하는 것.

---

## 3. 두 라이브러리 구조 차이 (사실관계 — v1.0에서 조사, 참고용으로 유지)

R0-028 작업 중 Figma 라이브 조회로 실측한 결과다. **이 표는 "KRDS로 맞춰야 할 목표"가 아니라 "FTC와 KRDS가 얼마나 다른 별개 시스템인지"를 보여주는 참고 자료**로만 유지한다 — KRDS 쪽으로 카탈로그를 바꿀 계획은 없다.

| | FTC 정부 포털 Design System · Button (채택됨) | KRDS_v1.0.0 (Community) · button (미사용) |
|---|---|---|
| Description | "정부 포털 CTA 버튼. Primary=주요 행동, Secondary=보조 행동(아웃라인), Ghost=텍스트형 행동." | 비어 있음 |
| Variant 1 | Style — Primary / Secondary / Ghost | Type — primary / secondary / tertiary |
| Variant 2 (Size) | Size — Medium / Small (2개) | Size — xlarge / large / medium / small / xsmall (5개) |
| Variant 3 | State — Default / Disabled / Focus | State — default / hover / pressed / disabled / focus |
| 총 조합 | 18개 (3×2×3) | 75개 (3×5×5) |

`component-catalog-v1.json`의 `krds.button` `figmaProperties`(`figmaProperty: "Style"`, 값 `primary`/`secondary`)는 **이미 FTC 구조와 정확히 일치한다.** 즉 이 부분은 v1.0에서 "불일치"로 지적했지만, 통합 방향이 FTC로 확정된 지금은 **오히려 이미 올바른 상태**다. 수정이 필요 없다.

---

## 4. 실제 영향 범위 (FTC 확정 기준으로 재정리)

### 4.1 설정 — 유일한 실질 수정 대상

`.env`(또는 운영 환경 설정)의 `FIGMA_ALLOWED_FILE_KEYS`에 FTC fileKey(`mVy5h1UbORVqQoBm8Wr1bT`)를 추가하고, KRDS fileKey(`6fcm04dwSEH2IUizZfaZCj`)는 이번 결정에 따라 제거하거나 "미사용" 명시가 필요하다.

### 4.2 카탈로그 — 변경 불필요

`component-catalog-v1.json`은 이미 FTC 값을 반영하고 있어 **변경할 필요가 없다.** `krds.searchPanel`의 하드코딩된 노드 ID(`"Lable#723:0"` 등)도 FTC 파일 기준이므로 그대로 유효하다 — 다만 이 값이 여전히 실제 FTC 파일의 노드와 일치하는지 최신 상태 확인은 별개로 필요하다(라이브러리 자체가 업데이트됐을 수 있음).

### 4.3 데이터/Registry — 마이그레이션 불필요

기존 저장된 `.figma-export-bundle.json` 등의 `componentSetKey`와 `"Size=Medium, Style=Primary, State=Default"` 같은 variant 키는 이미 FTC 기준이다. **v1.0에서 우려했던 마이그레이션은 필요 없다** — 애초에 옮겨갈 대상(KRDS)이 아니었기 때문이다.

### 4.4 조직 결정 문서

- **DEC-01**: 재작성 불필요. 이번 결정으로 **재확정(reaffirm)** 됐을 뿐이다. `12_...md`에 확정 사실만 짧게 덧붙이면 된다.
- **DEC-02·DEC-09**(`14_DEC02_DEC09_Component_Catalog_Approval_Request.md`): 승인 체크포인트 2(`14_...md:43`)의 문구가 "`Label`/`Style`/`Disabled` 등 속성명이 실제 **KRDS** Figma Library 관례와 어긋나지 않음"이라고 돼 있는데, 실제로 이 속성명들은 KRDS가 아니라 **FTC** 라이브러리 관례다. 이건 방향이 바뀌어서 생긴 문제가 아니라 **원래부터 있던 단순 오탈자/오기**(어느 라이브러리를 가리키는지 이름을 잘못 씀)다. 값 자체(승인된 property명·값)는 이미 맞으므로 **재승인은 불필요하고, 문구의 "KRDS"를 "FTC 정부 포털 Design System"으로 정정**하면 된다.
- **DEC-06**(Registry 버전 정책): 영향 없음 — 새 Publish나 라이브러리 교체가 없으므로 신규 `registryVersion` 발급 이슈도 없다.

### 4.5 코드 레벨 — FTC 참조는 정상, KRDS 참조만 점검

v1.0 §3.4에서 나열했던 FTC fileKey/문자열 참조들(`.figma-export-bundle.json`, 각종 문서, `ComponentRegistrySyncServiceTest.java` 등)은 **전부 정상이며 변경 불필요**하다. 대신 코드베이스에서 **KRDS fileKey(`6fcm04dwSEH2IUizZfaZCj`)를 실제로 허용/참조하는 곳**이 있는지 점검해, 이번 결정과 어긋나게 KRDS 경로가 열려 있지 않은지 확인해야 한다(4.1의 allowlist가 대표적 사례).

### 4.6 Figma 밖 범위 — 이미 올바름

`docs/crud/template-registry-role-and-evolution.md`의 `styleHint = FTC_PUBLIC` 같은 Thymeleaf 쪽 네이밍은 **오히려 이미 이번 결정과 일치한다.** 정리할 필요 없음.

### 4.7 테스트

`ComponentRegistrySyncServiceTest.java`/`VariantRuleResolverTest.java`가 FTC 값(`Style=Primary` 등)을 하드코딩한 것도 **정상이며 그대로 유지**한다.

---

## 5. 리스크 및 결정 필요 사항

1. **allowlist 수정**이 유일한 실질 코드/설정 변경이다 — 4.1 참고.
2. **DEC-02/09 승인 문서의 "KRDS"→"FTC" 오탈자 정정**이 필요하다(4.4) — 재승인 절차가 아니라 단순 문서 정정으로 처리 가능한지 조직 확인 필요.
3. `krds.searchPanel` 등 하드코딩된 노드 ID가 **FTC 라이브러리의 현재 버전과 여전히 일치하는지**(라이브러리 자체가 마지막 확인 이후 업데이트됐을 가능성) 별도 확인 필요.
4. 코드베이스 전체에서 KRDS fileKey를 참조하는 다른 지점이 4.1 외에 더 있는지 전수 점검 필요(이 문서는 allowlist 하나만 확인했다).

---

## 6. 다음 단계

실행 설계는 [27번 문서](./27_FTC_to_KRDS_Design_System_Consolidation_Implementation_Specification.md), 체크리스트는 [28번 문서](./28_FTC_to_KRDS_Design_System_Consolidation_Implementation_List.md)를 이 결정에 맞춰 함께 정정했다.
