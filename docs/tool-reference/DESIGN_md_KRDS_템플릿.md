# DESIGN.md — KRDS Design System 템플릿

> 이 파일은 `springai` 자체가 아니라, 이 도구로 생성하는 **eGovFrame 프로젝트의 루트**에 두는
> `DESIGN.md`의 템플릿이다. `DesignMdRuleLoader`(R6-055)가 이 형식 그대로 파싱한다 —
> 형식·제약은 `DesignMdRuleLoader.java` 코드 기준으로 검증했다.

## 사용 방법

1. 아래 "복사해서 쓰는 DESIGN.md" 블록 전체를 대상 eGovFrame 프로젝트 루트의 `DESIGN.md` 파일로
   저장한다.
2. 색상/타이포그래피/radius 값은 **실제 hex나 px 값이 아니라 CSS 변수 이름**을 넣는다 —
   `CompanyDesignTokenResolver`가 이 값을 그대로 CSS 변수 참조로 취급하기 때문이다(값과 변수 이름을
   구분하지 않는 현재 구현의 제약). 아래 템플릿의 변수명은 전부 `_ds_bundle.css.tpl`에 실제
   정의된 KRDS 변수를 grep으로 확인해 넣은 것이다.
3. `screenSpecificationId`/`designReferenceId` 없이 `viewType="thymeleaf"`로 화면을 생성해도
   무방하다 — DESIGN.md 반영은 17.1(JSP→Thymeleaf 마이그레이션, `designSystemProfileId` 파라미터)
   경로에서만 소비된다. **CRUD 소스 생성(auto/claude)은 이 파일을 읽지 않는다**(이 세션에서 grep
   0건으로 확인된 사실 — `CRUD_claude경로_DESIGN_md_Figma참조_반영_영향검토.md` 참고).

---

## 복사해서 쓰는 DESIGN.md

```markdown
---
schemaVersion: "1.0"
colors:
  primary: "--krds-color-light-primary-60"
  primaryHover: "--krds-color-light-primary-70"
  secondary: "--krds-color-light-secondary-60"
  textDefault: "--krds-color-light-gray-90"
  textMuted: "--krds-color-light-gray-60"
  background: "--krds-color-light-gray-0"
  border: "--krds-color-light-gray-20"
typography:
  bodyFont: "--krds-font-family-base"
radius:
  button: "--krds-button--radius-medium"
  input: "--krds-input--radius-medium"
spacing:
  medium: "16px"
  large: "24px"
layout:
  contentMaxWidth: "1180px"
voice:
  tone: "공공기관 안내문 — 정중하고 간결한 존댓말"
forbidden:
  - pattern: "임의 색상 하드코딩 금지, 위 colors 카테고리 변수만 사용"
---

# 프로젝트 KRDS 커스텀 규칙

이 프로젝트는 KRDS(전자정부 디자인 시스템) 기본 팔레트를 그대로 따른다. 위 frontmatter는
`DesignMdRuleLoader`가 파싱해 Thymeleaf 마이그레이션 화면 생성 시 CSS 변수 참조 provenance로
남긴다. 실제 색상값은 이 파일이 아니라 `_ds_bundle.css`(KRDS 원본 자산)가 정의한다.

## 참고
- 색상/폰트/radius 값은 모두 CSS 변수 **이름**이며, 실제 값이 아니다.
- `route`/`httpMethod`/`field`/`voField`/`dbSource`/`validation`/`csrf`/`authority`/`permission`
  키는 절대 추가하지 말 것 — 업무 계약 침범으로 간주되어 생성이 즉시 실패한다.
```

---

## 참고 — 코드로 확인한 제약 사항

| 항목 | 제약 | 근거 |
|---|---|---|
| 지원 카테고리 | `typography`/`colors`/`spacing`/`radius`/`layout`/`components`/`voice`/`forbidden` 8개만 인식 | `DesignMdRuleLoader.SUPPORTED_CATEGORIES` |
| `schemaVersion` | `"1.0"`만 지원, 다르면 즉시 실패(FATAL) | `SUPPORTED_SCHEMA_VERSION` |
| 금지 키 | `route`/`httpMethod`/`field`/`voField`/`dbSource`/`validation`/`csrf`/`authority`/`permission`(대소문자 무시, 중첩 구조까지 재귀 검사) — 있으면 즉시 실패 | `FORBIDDEN_KEYWORDS`, `checkForbiddenKeywords()` |
| 파일 없음 | 경고만 남기고 기본값으로 계속 진행(실패 아님) | `DESIGN_MD_NOT_FOUND` 처리 분기 |
| 반영 범위 | 17.1(Thymeleaf 마이그레이션)에서만 소비, CRUD 생성(auto/claude)은 미반영 | `CRUD_claude경로_DESIGN_md_Figma참조_반영_영향검토.md` |
| 실제 반영 방식 | 값(CSS 변수 이름)만 HTML 주석 provenance로 남김 — 실제 색상값 주입 아님 | `BindingComposer.java` JavaDoc |

## 관련 문서

- `service/thymeleaf/DesignMdRuleLoader.java` — 이 형식의 원본 구현
- `service/thymeleaf/CompanyDesignTokenResolver.java` — 파싱된 규칙을 CSS 변수로 매핑
- `CRUD_claude경로_DESIGN_md_Figma참조_반영_영향검토.md` — CRUD 경로 미반영 검토
