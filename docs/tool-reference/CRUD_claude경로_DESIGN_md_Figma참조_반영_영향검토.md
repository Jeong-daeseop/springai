# CRUD 소스 생성기(claude)에 DESIGN.md·Figma 참조 메타데이터 반영 — 영향검토

> 2026-09-02, 코드 실측 기준 작성. 구현 여부는 결정되지 않았으며, 이 문서는 **검토 결과만** 담는다.
> 아키텍처 다이어그램 아티팩트 17.2절(CRUD 소스 생성기 상세)에서 확인한 "claude 경로도 DESIGN.md·
> KRDS를 반영하지 않는다"는 사실에 대해, 17.1(JSP→Thymeleaf 마이그레이션)에만 있는 반영 메커니즘을
> CRUD claude 경로에도 추가할지 검토해달라는 요청에 대한 답이다.

---

## 1. 현재 상태 재확인

CRUD claude 경로에는 이미 **한 종류의 디자인 입력**이 존재한다 — `screenSpecificationId`를 주면
`ScreenSpecificationPromptFormatter.format()`이 `componentStyles`/`tokens`/`componentGeometry`
(Figma 1단계 원시 RGBA 값, `FigmaDesignSpecMapper` 산출물)를 프롬프트에 넣어준다.

이 문서가 다루는 건 이것과 **다른 두 번째 경로**다 — 17.1에만 있는 `DesignMdRuleLoader`(DESIGN.md
파싱)→`CompanyDesignTokenResolver`(→`VariableRegistryEntry`, 게시된 Figma Variable 이름) 경로를
CRUD claude에도 추가하는 것.

재확인 결과, 이 두 번째 경로는 CRUD claude 경로 어디에도 없다(grep 0건, 코드로 확인됨):

```
ScreenSpecificationService.java / ScreenSpecAssembler.java
  → DesignSystemQueryService/ComponentRegistry/DesignMdRuleLoader/CompanyDesignTokenResolver: 0건

CrudPromptBuilderService.java
  → designSystemProfileId/DesignMdRuleLoader/CompanyDesignTokenResolver/resolvedDesignTokens/AppliedDesignRules: 0건
```

`ScreenSpecificationPromptFormatter.java`에 "krds"라는 문자열이 등장하긴 하지만, 이건 "생성 코드에
`krds-*`/`egov-*` 클래스 구조를 유지하라"는 **하드코딩된 안내 문구** 한 줄일 뿐, DESIGN.md 파일이나
게시된 Figma Variable 이름을 동적으로 읽어서 반영하는 게 아니다.

---

## 2. 기술적 실현 가능성 — 예상보다 높음

- `CrudPromptBuilderService`가 이미 `ScreenSpecificationPromptFormatter`를 필드로 갖고 쓴다 — 17.1의
  `BindingComposer`가 `resolvedDesignTokens`를 받아 HTML 주석으로 남기는 것과 같은 패턴을, 이
  포맷터에 텍스트 블록으로 추가하면 된다. **신규 인프라 불필요, 기존 확장점 재사용.**
- `buildFullCrudPrompt`가 이미 `outputPath`(대상 프로젝트 경로)를 파라미터로 받고 있어,
  `DesignMdRuleLoader.load(outputPath)`를 호출할 전제조건(프로젝트 경로)이 이미 갖춰져 있다.
- 오히려 **CRUD 생성이 17.1보다 유리한 조건**이다: `CrudGenerationTool`의 JavaDoc에 "생성 화면은
  `initializeProject()`가 만든 `/resources/css/styles.css`와 `/resources/js/krds.min.js`를
  사용합니다"라고 명시돼 있어, CRUD 생성은 애초에 "이 도구 체계로 초기화된 프로젝트"를 전제로
  설계돼 있다. 17.1(정의상 순수 레거시 프로젝트 대상, DESIGN.md 존재 불확실)과 달리 DESIGN.md가
  실제로 존재할 가능성이 훨씬 높다.

---

## 3. 필요한 변경 범위

1. `CrudGenerationTool.buildFullCrudPrompt()`에 `designSystemProfileId`(선택, `@Nullable`) 파라미터
   신규 추가
2. `CrudPromptBuilderService`가 `CompanyDesignTokenResolver.resolve(profileId, appliedDesignRules)`
   호출(17.1과 동일 패턴 — 실패해도 FATAL 아님, 토큰 없이 계속)
3. `ScreenSpecificationPromptFormatter.format()`에 `resolvedDesignTokens` 파라미터 및 출력 블록 추가

---

## 4. 위험

- 이전 검토(`Figma_Variable_Style_이름_반영_검토.md`)의 위험이 부분 재적용된다 — 다만 여기서
  다루는 `VariableRegistryEntry`는 사람이 Figma UI에서 명시적으로 게시(Publish)한 이름이라, 그때
  다룬 "원시 `boundVariables`" 위험보다는 신뢰도가 상대적으로 높다.
- claude 경로는 Claude가 실제로 코드를 작성하는 경로라, 이름을 실제 코드에 반영할 수 있는
  "실사용 단계" 위험은 여전히 있다. 다만 17.1과 동일하게 "값이 아니라 변수 이름만" 전달하는
  방식을 그대로 따르면, Claude가 실제 색상값을 지어내는 게 아니라 이미 `_ds_bundle.css`에 정의된
  변수를 참조하는 정도로 유도할 수 있어 위험이 완화된다.
- **auto 경로와의 비대칭**: auto 경로는 여전히 DESIGN.md 미반영 상태로 남으므로, "같은 CRUD인데
  claude로 만들면 반영되고 auto로 만들면 안 되는" 불일치가 새로 생긴다 — 문서화·안내가 필요하다.
- `KrdsStylesConfigurer`의 하드코딩된 `CRUD_CSS`와의 정합성은 17.1과 동일한 트레이드오프다.

---

## 5. 결론

기술적으로는 기존 패턴 재사용만으로 저비용 구현이 가능하고, CRUD 생성 특유의 조건(항상
`initializeProject()` 이후 프로젝트를 대상으로 함)이 오히려 17.1보다 유리하다. 다만 auto/claude
반영 비대칭성과 이름 참조 위험은 구현 전에 정리가 필요하다.

---

## 6. 참고 파일 경로

| 파일 | 역할 |
|---|---|
| `tools/generation/CrudGenerationTool.java` | `buildFullCrudPrompt()` — `outputPath`/`screenSpecificationId` 이미 보유, `designSystemProfileId` 없음 |
| `service/CrudPromptBuilderService.java` | claude 경로 프롬프트 조립 — `ScreenSpecificationPromptFormatter` 이미 사용, DESIGN.md 관련 참조 0건 |
| `service/ScreenSpecificationPromptFormatter.java` | claude 경로 프롬프트 포맷터 — "krds-*" 문자열은 하드코딩 안내 문구뿐 |
| `service/thymeleaf/DesignMdRuleLoader.java` | DESIGN.md 파싱 — 현재 `service/thymeleaf/` 3개 파일에서만 참조(17.1 전용) |
| `service/thymeleaf/CompanyDesignTokenResolver.java` | `VariableRegistryEntry`(게시된 Figma Variable 이름) 해석 — 현재 17.1 전용 |
| `service/thymeleaf/BindingComposer.java` | 17.1에서 `resolvedDesignTokens`를 HTML 주석 provenance로만 반영하는 참고 패턴 |
| `service/KrdsStylesConfigurer.java` | CRUD auto 경로의 하드코딩 CSS — 이번 검토와 별개, 정합성 트레이드오프만 공유 |
| `docs/tool-reference/Figma_Variable_Style_이름_반영_검토.md` | 이름 참조 위험의 선행 검토 |
| `docs/tool-reference/Thymeleaf_레거시전환_KRDS_반영_검토.md` | 17.1 DESIGN.md 반영의 원 검토 |
