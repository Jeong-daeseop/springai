# CRUD 생성 — ComponentRegistry 직접 연결(방안 2) 구현명세서 및 구현목록

> [`Figma_ClaudeDesign_DESIGN_md_연계_영향검토.md`](./Figma_ClaudeDesign_DESIGN_md_연계_영향검토.md)와
> 후속 대화에서 비교한 방안 중 **방안 2(DESIGN.md를 거치지 않고 `ComponentRegistry`를
> `CompanyDesignTokenResolver.resolve(profileId, null)`로 직접 연결)**를 CRUD 생성에 반영하는
> 구현명세서다. **구현 여부는 결정되지 않았으며, 승인 전까지는 이 문서에 따라 코드를 변경하지
> 않는다.**

---

## 1. 배경 및 목적

CRUD 소스 생성(auto/claude 둘 다)은 회사 표준 디자인 토큰을 전혀 반영하지 않는다. DESIGN.md를
거치는 방안(방안 1)은 파일 관리·YAML 스키마 검증·"값 vs 이름" 의미 불일치라는 부가 복잡도를
안는 반면, `CompanyDesignTokenResolver.resolve(profileId, appliedDesignRules)`의
`appliedDesignRules`는 **nullable**이라 `resolve(profileId, null)`만으로 이미 DB에 등록된
`ComponentRegistry`(게시된 KRDS Variable 목록)에서 토큰을 뽑아낼 수 있다 — DESIGN.md 파싱
로직(`DesignMdRuleLoader`) 없이 동일한 결과를 더 적은 복잡도로 얻는다.

### 설계 원칙

- **DESIGN.md 완전 우회**: `DesignMdRuleLoader`를 CRUD 경로에 도입하지 않는다. `profileId`만
  넘기고 `appliedDesignRules`는 항상 `null`로 호출한다.
- **선택적 강화, 실패해도 비차단**: `designSystemProfileId`를 안 주면 기존과 동일하게 동작한다.
  값을 줬는데 `resolve()`가 실패해도(예: 프로필 미등록) CRUD 생성 자체를 막지 않고 경고만
  남긴다 — 이 세션에서 반복 확인한 "선택적 디자인 입력은 FATAL 아님" 원칙과 동일하다.
- **1차 범위는 claude 경로만**: auto 경로(`CrudTemplateRenderer`, FreeMarker 정적 렌더링)에 같은
  정보를 반영하려면 템플릿 다수(list/detail/regist/updt × jsp/thymeleaf)를 함께 고쳐야 해서
  범위가 크게 늘어난다. `Figma_fills_strokes_구현계획.md`/`CRUD_생성_KRDS_자산_검증_구현계획.md`와
  같은 패턴으로 claude 경로부터 저비용으로 먼저 반영하고 auto는 §7로 미룬다.

---

## 2. 목표 아키텍처

```
CrudGenerationTool.buildFullCrudPrompt(..., designSystemProfileId)   [파라미터 추가]
    ↓
CrudGenerationMcpFacade.buildFullCrudPrompt(..., designSystemProfileId)   [파라미터 추가]
    ↓
CrudPromptBuilderService.buildFullCrudPrompt(..., designSystemProfileId)   [파라미터 추가]
    ↓ designSystemProfileId != null인 경우만
CompanyDesignTokenResolver.resolve(designSystemProfileId, null)   [신규 호출, DESIGN.md 없이]
    ↓ 성공                                    ↓ 실패(FATAL)
ResolvedDesignTokens                    경고만 남기고 계속 진행
    ↓
프롬프트 최상단에 정보 블록 삽입
"이 프로젝트는 다음 KRDS 표준 CSS 변수를 씁니다: --krds-color-light-primary-60(primary), ..."
```

---

## 3. 데이터/인터페이스 설계 (실제 시그니처 확인 완료)

### 3.1 `ResolvedDesignTokens` (기존, 변경 없음)

```java
public record ResolvedDesignTokens(
        String profileId, String profileVersion, @Nullable String designMdHash,
        Map<String, String> colorTokens, Map<String, String> typographyTokens,
        Map<String, String> spacingTokens, Map<String, String> radiusTokens,
        Map<String, String> layoutTokens,
        Map<String, ComponentPropertyTokens> componentTokens,
        List<GenerationIssue> issues) {
    public boolean hasFatalIssue() { ... }
}
```

`resolve(profileId, null)` 호출 시 `designMdHash`는 `null`이 된다(`appliedDesignRules != null ?
appliedDesignRules.contentHash() : null` — 기존 코드 그대로, 수정 불필요).

### 3.2 파라미터 체인 확장

**호출 체인**(확인됨): `CrudGenerationTool.buildFullCrudPrompt()` →
`CrudGenerationMcpFacade.buildFullCrudPrompt()` → `CrudPromptBuilderService.buildFullCrudPrompt()`.

세 메서드 모두 마지막에 `@Nullable String designSystemProfileId` 파라미터를 추가한다(기존
`designReferenceId`/`screenSpecificationId` 뒤 자리). 각 계층은 그대로 다음 계층에 전달만 한다.

```java
// CrudGenerationTool.java
public String buildFullCrudPrompt(String database, String tableName, String domain, String packageName,
                                  String outputPath, String llmProvider, @Nullable String egovVersion,
                                  @Nullable String viewType, @Nullable String layoutMode,
                                  @Nullable String layoutView, @Nullable String breadcrumbView,
                                  @Nullable String programFileName, @Nullable String programUrl,
                                  @Nullable String programKoreanName, @Nullable String programStorePath,
                                  @Nullable String designReferenceId, @Nullable String screenSpecificationId,
                                  @Nullable String designSystemProfileId) {   // ← 신규
    return facade.buildFullCrudPrompt(database, tableName, domain, packageName, outputPath, llmProvider,
            egovVersion, viewType, layoutMode, layoutView, breadcrumbView, programFileName, programUrl,
            programKoreanName, programStorePath, designReferenceId, screenSpecificationId,
            designSystemProfileId);
}
```

기존 시그니처를 호출하는 곳(테스트 포함)이 깨지지 않도록, 새 파라미터를 받지 않는 기존
오버로드는 그대로 남기고 `designSystemProfileId=null`로 위임하는 컴패트 오버로드를 추가한다
(이 레포의 기존 관례 — `UiDesignSpec`/`ScreenSpecification`의 compat 생성자 패턴과 동일).

### 3.3 `CrudPromptBuilderService` 내부 로직

**위치**: `service/CrudPromptBuilderService.java`, 기존 `KRDS_ASSET_WARNING` 삽입부(§4 CRUD
KRDS 자산 검증 구현과 같은 자리, `sb` 조립 직후) 바로 다음.

```java
private static final String DESIGN_TOKEN_INFO_PREFIX = "ℹ️ 이 프로젝트는 다음 KRDS 표준 CSS 변수를 씁니다: ";

// StringBuilder sb 조립부에 추가
if (designSystemProfileId != null && !designSystemProfileId.isBlank()) {
    ThymeleafGenerationStageResult<ResolvedDesignTokens> tokenResult =
            companyDesignTokenResolver.resolve(designSystemProfileId, null);
    if (tokenResult.successful() && !tokenResult.value().colorTokens().isEmpty()) {
        String tokenSummary = tokenResult.value().colorTokens().entrySet().stream()
                .map(e -> e.getValue() + "(" + e.getKey() + ")")
                .collect(Collectors.joining(", "));
        sb.append(DESIGN_TOKEN_INFO_PREFIX).append(tokenSummary).append("\n\n");
    }
    // 실패해도 조용히 건너뜀(FATAL 아님) — resolve() 실패 사유는 tokenResult.value()가 없을 때 issues에 담김
}
```

`CompanyDesignTokenResolver`를 `CrudPromptBuilderService`의 생성자 의존성으로 신규 주입한다
(`@RequiredArgsConstructor`이므로 필드 추가만 하면 됨).

---

## 4. 신규/수정 파일 목록

| 파일 | 변경 유형 | 내용 |
|---|---|---|
| `tools/generation/CrudGenerationTool.java` | 수정 | `designSystemProfileId` 파라미터 + compat 오버로드(§3.2) |
| `service/generation/mcp/CrudGenerationMcpFacade.java` | 수정 | 동일 파라미터 전달만 |
| `service/CrudPromptBuilderService.java` | 수정 | `CompanyDesignTokenResolver` 의존성 추가, §3.3 로직 |
| 관련 테스트 | 신규/수정 | §7 |

---

## 5. 리스크 및 대응

| 리스크 | 영향 | 대응 |
|---|---|---|
| `ComponentRegistry`/`DesignSystemProfile`이 DB에 미등록 상태(사람이 Figma UI로 Publish 안 함) | `resolve()`가 `DESIGN_SYSTEM_PROFILE_NOT_FOUND`로 실패 | 비차단 설계(§3.3)로 이미 흡수됨 — CRUD 생성은 정상 진행, 토큰 정보만 프롬프트에서 빠짐 |
| Claude가 프롬프트의 변수 이름을 실제 값처럼 오인해 임의 색상 추론 | 잘못된 색상이 코드에 반영될 수 있음 | 안내문에 "변수 이름"임을 명시(`DESIGN_TOKEN_INFO_PREFIX` 문구), 17.1의 provenance 패턴과 동일하게 "이름만, 값 아님" 원칙 유지 |
| 파라미터 체인 3계층 모두 수정 필요 | 변경 범위가 여러 파일에 걸침 | 각 계층은 단순 전달만 하므로 로직 복잡도는 낮음, compat 오버로드로 기존 호출부 회귀 없음 |

---

## 6. 검증 방법 (§8과 함께 참고)

1. `ComponentRegistry`에 등록된 fixture profileId로 `buildFullCrudPrompt(..., designSystemProfileId="krds")` 호출 → 반환 프롬프트에 `DESIGN_TOKEN_INFO_PREFIX` 블록과 실제 변수 이름 포함 확인
2. 미등록 profileId로 호출 → 생성 자체는 정상 완료, 토큰 블록만 없음 확인(비차단 검증)
3. `designSystemProfileId` 생략(기존 호출) → 기존 테스트 스위트 회귀 없음 확인
4. `./gradlew build` 전체 통과 확인

---

## 7. 1차 구현 제외 범위 (2차 이후)

- **auto 경로 반영**: `CrudTemplateRenderer`/`CodeServiceGenerationExecutor` 경로에 같은 정보를
  HTML 주석 provenance로 남기려면 `CrudTemplateModel`에 필드를 추가하고 `list/detail/regist/updt
  × jsp/thymeleaf` 템플릿을 전부 수정해야 한다 — 범위가 커서 별도 문서로 분리한다.
- `ComponentPropertyTokens`(컴포넌트별 속성 매핑) 활용: 이번 범위는 `colorTokens`만 프롬프트에
  반영한다. `typographyTokens`/`radiusTokens`/`componentTokens` 확장은 효과 확인 후 추가한다.
- DB에 `ComponentRegistry`가 없을 때 자동으로 채워주는 기능: 이건 완전히 별개 주제(Figma UI
  Publish → `ComponentRegistrySyncService` 흐름)이며 이번 범위 밖이다.

---

## 8. 단계별 구현목록

### Phase 1 — 파라미터 체인 (필수)

| 순서 | 작업 |
|---|---|
| 1 | `CrudGenerationTool.buildFullCrudPrompt()`에 `designSystemProfileId` 추가 + compat 오버로드 |
| 2 | `CrudGenerationMcpFacade.buildFullCrudPrompt()`에 동일 파라미터 전달 로직 추가 |
| 3 | `CrudPromptBuilderService`에 `CompanyDesignTokenResolver` 의존성 주입 |

### Phase 2 — 토큰 반영 로직 (필수)

| 순서 | 작업 |
|---|---|
| 4 | `CrudPromptBuilderService.buildFullCrudPrompt()`에 §3.3 로직 추가 |
| 5 | `resolve()` 실패 시 비차단 처리 확인(단위 테스트) |

### Phase 3 — 테스트 (필수)

| 순서 | 작업 |
|---|---|
| 6 | 등록된 profileId fixture로 프롬프트에 토큰 블록 포함 확인 |
| 7 | 미등록 profileId로 비차단 확인 |
| 8 | `designSystemProfileId` 생략 시 기존 동작 회귀 없음 확인 |

### Phase 4 — 검증

| 순서 | 작업 |
|---|---|
| 9 | `./gradlew build` 전체 통과 확인 |

---

## 9. 관련 문서

- [`Figma_ClaudeDesign_DESIGN_md_연계_영향검토.md`](./Figma_ClaudeDesign_DESIGN_md_연계_영향검토.md) — 방안 비교의 원 검토
- [`CRUD_claude경로_DESIGN_md_Figma참조_반영_영향검토.md`](./CRUD_claude경로_DESIGN_md_Figma참조_반영_영향검토.md) — 방안 1(DESIGN.md 경유)의 원 검토, 이번 방안 2와의 대조군
- `service/thymeleaf/CompanyDesignTokenResolver.java` — `resolve()` 원본 구현
- `service/designsystem/DesignSystemQueryService.java` — `findLatestProfile`/`findLatestRegistry`
- `service/thymeleaf/BindingComposer.java` — 17.1의 "이름만, 값 아님" provenance 패턴 참고
