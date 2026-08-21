# Figma Design System 통합 구현 명세서 (FTC 정부 포털 Design System 확정)

> 문서 버전: 1.1
> 작성일: 2026-08-21
> 관련 문서: [26_FTC_to_KRDS_Design_System_Consolidation_Impact_Analysis.md](./26_FTC_to_KRDS_Design_System_Consolidation_Impact_Analysis.md)(영향 분석), [28_FTC_to_KRDS_Design_System_Consolidation_Implementation_List.md](./28_FTC_to_KRDS_Design_System_Consolidation_Implementation_List.md)(체크리스트)
> 성격: 실행 설계서. **구현 승인 전이며 코드 변경은 포함하지 않는다.**
>
> **v1.1 확정**: 조직 결정으로 통합 대상이 **FTC 정부 포털 Design System**으로 확정됨에 따라(26번 문서 v1.1 참고), v1.0의 "KRDS로 전환" 전제로 설계된 Phase 1~8 전체를 폐기하고 훨씬 작은 범위로 다시 썼다. 카탈로그는 이미 FTC 기준이라 손댈 게 없고, 실제 필요한 작업은 설정(allowlist) 수정과 승인 문서 오탈자 정정 두 가지뿐이다.

---

## 1. 목표

Figma 화면 생성 파이프라인이 **FTC 정부 포털 Design System만** 참조하도록 확정하고, 결정과 실제 운영 설정(`.env` allowlist) 사이의 불일치를 제거한다. 카탈로그·Registry·저장 데이터는 이미 FTC 기준이므로 **마이그레이션 대상이 아니다** — 이번 작업은 "전환"이 아니라 "정합성 확보"에 가깝다.

---

## 2. 접근 방식

```
Phase 1  allowlist 수정 (.env FIGMA_ALLOWED_FILE_KEYS)
Phase 2  코드베이스 전체에서 KRDS fileKey 참조 지점 점검
Phase 3  DEC-02/09 승인 문서 오탈자 정정 ("KRDS" → "FTC 정부 포털 Design System")
Phase 4  DEC-01 재확정 기록
Phase 5  FTC 라이브러리 현재 버전과 하드코딩 값(특히 krds.searchPanel 노드 ID) 최신성 확인
Phase 6  라이브 검증
```

Phase 1과 2는 동시에 진행 가능하다. Phase 3(문서 오탈자 정정)은 재승인 절차가 아니라 단순 정정이므로 조직 확인만 받으면 바로 처리할 수 있다.

---

## 3. Phase 1 — allowlist 수정

`.env`(및 각 환경별 `application-*.yaml`에 이 값을 참조하는 곳)의 `FIGMA_ALLOWED_FILE_KEYS`를 다음과 같이 정리한다:

- **추가**: FTC fileKey `mVy5h1UbORVqQoBm8Wr1bT`
- **제거 검토**: KRDS fileKey `6fcm04dwSEH2IUizZfaZCj` — 완전히 제거할지, 과거 참조 이력이 있어 당분간 남겨두되 "미사용" 주석만 달지는 결정 필요(28번 문서 체크리스트 항목).
- 남은 다른 키(`p1QrZlmIgd5i5M0OAf6TsA`, eGovFrame 소비 파일로 추정)는 그대로 유지.

이 변경이 `FigmaFileAllowlistValidator`(또는 이 값을 소비하는 실제 클래스)의 동작에 어떤 영향을 주는지 — 특히 기존에 KRDS fileKey로 이미 발급된 캐시/세션/단기 토큰이 있다면 그 처리 — 확인이 필요하다.

---

## 4. Phase 2 — KRDS fileKey 참조 지점 전수 점검

26번 문서는 allowlist 하나만 확인했다. 코드베이스 전체(Java, TypeScript, 설정 파일, DB 데이터)에서 `6fcm04dwSEH2IUizZfaZCj`를 참조하는 다른 지점이 있는지 `grep`으로 전수 조사한다. 발견되면 각각이 "의도된 참고용 언급"인지 "실수로 열려 있는 KRDS 경로"인지 구분해 후자만 정리한다.

---

## 5. Phase 3 — DEC-02/09 승인 문서 오탈자 정정

`14_DEC02_DEC09_Component_Catalog_Approval_Request.md`의 승인 체크포인트 2(`14_...md:43`) 문구:

> "`Label`/`Style`/`Disabled` 등 속성명이 실제 **KRDS** Figma Library 관례와 어긋나지 않음"

을

> "`Label`/`Style`/`Disabled` 등 속성명이 실제 **FTC 정부 포털 Design System** Library 관례와 어긋나지 않음"

으로 정정한다. **승인된 값(카탈로그의 실제 property명·값) 자체는 바뀌지 않으므로 이건 재승인이 아니라 문서 정정이다** — 다만 조직이 "정정으로 충분한지, 재승인 절차를 다시 밟아야 하는지"는 확인받아야 한다(문서 프로세스 문제이지 기술 문제가 아님).

---

## 6. Phase 4 — DEC-01 재확정 기록

`12_...md` §4 DEC-01은 내용을 바꿀 필요가 없다 — 이미 FTC를 확정한 상태였다. 다만 "왜 지금 다시 이 얘기가 나왔는지"(allowlist가 실제로는 KRDS를 가리키고 있었다는 사실이 발견됐다는 것) 기록을 남기기 위해 §19 변경 이력에 짧게 재확정 사실만 추가한다.

---

## 7. Phase 5 — FTC 라이브러리 최신성 확인

`component-catalog-v1.json`의 `krds.searchPanel` 등 FTC 파일의 특정 노드 ID를 하드코딩한 값들이, FTC 라이브러리가 마지막으로 확인된 이후 업데이트되지 않았는지 확인한다(라이브러리 자체의 버전 드리프트는 이번 통합 결정과 별개로 상시 리스크다).

---

## 8. Phase 6 — 라이브 검증

Figma Desktop에서(사람이 직접, 자동화 불가):
1. allowlist 수정 후 FTC 파일 기준 화면 생성이 정상 동작하는지 확인
2. KRDS fileKey로의 요청이 의도대로 차단되는지(또는 의도적으로 허용 상태로 남겨뒀다면 그 상태가 맞는지) 확인

---

## 9. 범위에서 명시적으로 제외하는 것

- **카탈로그를 KRDS 값으로 바꾸는 작업**: 하지 않는다. FTC가 확정됐으므로 현재 카탈로그가 이미 정답이다.
- **Registry/DB 마이그레이션**: 대상이 없어 불필요.
- **KRDS의 Size 5단계·아이콘 속성 등을 카탈로그에 추가하는 작업**: 하지 않는다. KRDS는 사용하지 않는다.
- **R0-028의 Table→Card/버튼 스왑 이슈**: 24번 문서에서 별도로 다루며 이 결정과 무관.
