# CRUD 소스 생성(auto/claude) — KRDS 원본 자산(`_ds_bundle.css`/`krds.min.js`) 미검증 문제 검토

> 2026-09-02, 코드 실측 기준 작성. 구현 여부는 결정되지 않았으며, 이 문서는 **검토 결과만** 담는다.
> [`CRUD_claude경로_DESIGN_md_Figma참조_반영_영향검토.md`](./CRUD_claude경로_DESIGN_md_Figma참조_반영_영향검토.md)
> 검토 과정에서 "DESIGN.md 커스텀 토큰 미반영"과 "KRDS 기본 자산 자체의 존재 여부 미검증"이 서로
> 다른 층위의 문제라는 게 드러나, 후자만 별도로 검토한다.

---

## 1. 배경 — 두 층위를 구분해야 한다

CRUD 화면의 스타일 반영은 두 층위로 나뉜다.

- **① KRDS 기본 자산**: `krds-btn`/`egov-*` 클래스(템플릿에 하드코딩) + 그 클래스를 실제로 꾸며주는
  `styles.css`/`_ds_bundle.css`/`krds.min.js` 원본 파일. 이게 없으면 화면이 진짜로 깨져 보인다
  (밋밋한 브라우저 기본 스타일).
- **② DESIGN.md 회사 커스텀 토큰**: KRDS 기본값 위에 회사가 오버라이드한 색상 등. 없어도 화면은
  KRDS 기본 스타일(①)로 정상 렌더링되고, 다만 회사 커스터마이징만 못 받는다.

`CRUD_claude경로_DESIGN_md_Figma참조_반영_영향검토.md`는 ②를 다뤘다. 이 문서는 ①이 CRUD 생성
경로(auto/claude)에서 실제로 검증되는지를 확인한다 — 17.1(JSP→Thymeleaf 마이그레이션)에서 이미
같은 종류의 문제를 발견하고 해결안 B로 고친 바 있다(`Thymeleaf_레거시전환_KRDS_반영_검토.md`).

---

## 2. 확인 결과 — auto/claude 모두 완전하지 않다

### 2-1. auto 경로: `styles.css`는 자동 복구되지만 원본 파일은 그대로 미검증

`KrdsStylesConfigurer.java`가 `styles.css`를 patch할 때:

```java
boolean existed = Files.exists(target);
String current = existed ? Files.readString(target, StandardCharsets.UTF_8) : "";
```

`styles.css`가 없으면 빈 문자열에서 시작해 새로 만든다(self-healing) — **`styles.css` 파일 자체가
없어서 깨지는 일은 auto 경로에서 발생하지 않는다.**

다만 이건 `styles.css`에 작은 CSS 변수 오버라이드 블록만 patch하는 것이고, `styles.css`가 내부적으로
`@import`하는 **`_ds_bundle.css`(실제 KRDS 컴포넌트 스타일 본체)와 `krds.min.js` 원본 파일 자체는
새로 만들어주지 않는다.** 이 두 파일은 오직 `initializeProject()`(`FilePlanFactory`)만 배치하며,
`KrdsStylesConfigurer`는 이들의 존재를 검증하지 않는다(`Files.exists` 호출 대상이 `styles.css`
자신뿐, `_ds_bundle.css`/`krds.min.js` 아님).

### 2-2. claude 경로: 안전장치가 아예 없음

`CrudPromptBuilderService.java` 391-392행, `layoutMode=NONE`일 때 프롬프트에 삽입되는 지시:

```
- <head>에 ... th:href="@{/resources/css/styles.css}" link를 직접 포함하세요.
- </body> 직전에 th:src="@{/resources/js/krds.min.js}" script를 직접 포함하세요.
```

**Claude에게 "링크를 넣어라"고 지시만 할 뿐, 그 파일들이 실제로 존재하는지는 서버도 Claude도
검증하지 않는다.** `KrdsStylesConfigurer` 자체가 claude 경로에서 호출되지 않으므로(서버가 CSS를
아예 안 만듦 — `Thymeleaf_레거시전환_KRDS_반영_검토.md`에서 이미 확인된 사실과 동일한 구조),
auto 경로에 있던 최소한의 self-healing(`styles.css` 자동 생성)조차 없다.

`layoutMode=reuse`(기본값)일 때도 기존 layout이 `/resources/css/styles.css`를 이미 링크하고 있다고
전제할 뿐, 그 파일이나 `_ds_bundle.css`/`krds.min.js`의 실제 존재 여부는 검증하지 않는다.

### 2-3. 종합

| | `styles.css` | `_ds_bundle.css`/`krds.min.js` |
|---|---|---|
| CRUD auto | ✅ 없으면 자동 생성 | ❌ 미검증 |
| CRUD claude | ❌ 미검증(생성 자체를 안 함) | ❌ 미검증 |
| 17.1(수정 전) | ❌ 미검증 | ❌ 미검증 |
| 17.1(해결안 B 적용 후) | `krds-*` 클래스 사용 시 3종 모두 검증 → 없으면 `approve()` 차단 | (동일 검증에 포함) |

CRUD auto는 17.1(수정 전)보다 한 단계 낫지만(`styles.css`만은 self-healing), `_ds_bundle.css`/
`krds.min.js`까지 포함한 완전한 검증은 CRUD 경로 어디에도 없다. Files.exists 기반 명시적 검증
코드는 CRUD 생성 경로 전체(`service/generation/crud/`)에서 **0건**이다(grep 확인).

---

## 3. 실질적 위험

`initializeProject()`를 거치지 않고 CRUD 화면 생성 Tool을 단독 호출하는 시나리오(예: 이미 존재하는
프로젝트에 이 도구로 화면만 추가하려는 경우)에서:

- **auto 경로**: `styles.css`는 생기지만 그 안의 `@import url(_ds_bundle.css)`가 가리키는 파일이
  없어 브라우저가 그 규칙을 무시 — 결과적으로 KRDS 컴포넌트 스타일(`.krds-btn`의 배경색·테두리 등)
  없이 화면이 렌더링된다.
- **claude 경로**: 위 문제에 더해, `styles.css` 자체도 없을 수 있다 — 최악의 경우 완전히 꾸밈없는
  기본 HTML로 렌더링된다.

이건 17.1에서 이미 확인한 "화면이 깨져 보인다" 위험과 동일한 종류이며, CRUD 경로에서는 아직
방어선이 없다.

---

## 4. 결론

CRUD 생성 경로(auto/claude 둘 다)에 17.1과 동일한 KRDS 원본 자산 미검증 문제가 남아 있다. auto는
`styles.css` self-healing 덕에 부분적으로 완화돼 있지만, `_ds_bundle.css`/`krds.min.js`까지 포함한
완전한 검증은 없다. claude는 그 부분 완화조차 없다.

대응 방향은 17.1에서 이미 검증한 해결안 B(안내형 실패 — `Thymeleaf_레거시전환_KRDS_반영_검토.md`
§6 참고)를 CRUD 생성 경로에도 유사하게 적용하는 것이 가장 저비용일 것으로 보이나, 구체 설계는
이 문서 범위 밖이며 별도 검토·승인이 필요하다.

---

## 5. 참고 파일 경로

| 파일 | 역할 |
|---|---|
| `service/KrdsStylesConfigurer.java` | `styles.css` patch — 파일 없으면 자동 생성(self-healing), `_ds_bundle.css`/`krds.min.js` 존재는 미검증 |
| `service/CrudPromptBuilderService.java` (391-392행) | claude 경로 — `layoutMode=NONE` 시 CSS/JS 링크 삽입을 프롬프트로 지시만 함, 실제 존재 검증 없음 |
| `service/generation/crud/CrudFormColumnCssProcessor.java`, `CrudTableDensityCssProcessor.java` | `KrdsStylesConfigurer` 호출처(auto 경로 전용) |
| `service/initializr/FilePlanFactory.java` | `_ds_bundle.css`/`krds.min.js`/`styles.css` 원본 배치 — CRUD 생성과는 별개의 프로젝트 초기화 단계 |
| `tools/generation/CrudGenerationTool.java` | JavaDoc상 "`initializeProject()`가 만든 자산을 사용" 전제만 명시, 런타임 검증 없음 |
| `Thymeleaf_레거시전환_KRDS_반영_검토.md` | 17.1의 동일 문제 원 검토 + 해결안 B(안내형 실패) |
| `CRUD_claude경로_DESIGN_md_Figma참조_반영_영향검토.md` | 이 문서와 구분되는 ②(DESIGN.md 커스텀 토큰) 층위 검토 |
