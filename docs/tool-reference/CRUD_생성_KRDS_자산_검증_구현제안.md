# CRUD 소스 생성 KRDS 원본 자산 검증 — 구현 제안

> [`CRUD_생성_KRDS_자산_미검증_검토.md`](./CRUD_생성_KRDS_자산_미검증_검토.md)에서 확인한 문제(auto는
> `styles.css`만 self-healing, `_ds_bundle.css`/`krds.min.js`는 미검증 / claude는 검증 자체가 없음)에
> 대한 구현 제안이다. **구현 여부는 결정되지 않았으며, 승인 전까지는 이 문서에 따라 코드를 변경하지
> 않는다.**

---

## 1. 배경 및 목적

CRUD auto/claude 생성 경로 모두 KRDS 원본 자산(`_ds_bundle.css`, `krds.min.js`)의 실제 존재 여부를
검증하지 않는다. `initializeProject()`를 거치지 않은 프로젝트에 CRUD 화면 생성 Tool을 단독 호출하면,
클래스명(`krds-btn` 등)은 생성되지만 스타일이 전혀 반영되지 않은 화면이 조용히 완주될 수 있다 —
17.1(JSP→Thymeleaf 마이그레이션)에서 이미 확인·해결한 것과 같은 종류의 문제다
(`Thymeleaf_레거시전환_KRDS_반영_검토.md` 해결안 B).

### 설계 원칙

- **auto와 claude는 다른 방식으로 접근한다**: auto는 서버가 파일을 쓰는 파이프라인이 있어 확실한
  차단이 가능하지만, claude는 저장이 `saveGeneratedCode(filePath, code)`라는 파일 단위 저수준
  Tool로 이뤄져 "프로젝트" 개념이 없다 — 차단이 아니라 안내까지만 현실적으로 가능하다(§4 참고).
- **기존 확장점 재사용**: auto 경로는 이미 있는 `GenerationStageProcessor` 파이프라인에 새 프로세서를
  추가하는 방식으로, `CrudFormColumnCssProcessor`/`CrudTableDensityCssProcessor`와 동일한 패턴을
  따른다. 새 아키텍처를 만들지 않는다.
- **17.1과의 일관성**: `krds-*` 클래스 사용 여부를 먼저 확인하고, 쓰는 경우에만 검증을 발동한다
  (17.1의 `KRDS_CLASS_PATTERN` 정규식 탐지와 동일한 취지 — 무관한 화면까지 막지 않기 위함). 다만
  CRUD 템플릿은 항상 `krds-btn` 등을 하드코딩하므로(6번 다이어그램에서 이미 확인), 이 조건은
  사실상 항상 참이다.

---

## 2. 목표 아키텍처

```
[auto 경로]
CrudGenerationApplicationService
  └─ (기존) CrudFormColumnCssProcessor / CrudTableDensityCssProcessor
  └─ (신규) KrdsAssetVerificationProcessor            [Blueprint ProcessorStep 추가]
       └─ WAR: src/main/webapp/resources/{css/_ds_bundle.css, js/krds.min.js}
          Boot: src/main/resources/static/resources/{...}
          중 하나라도 완비 → 통과
          둘 다 미완비 → ProcessorResult.failed("KRDS_ASSETS_MISSING", ...)
               └─ CodeServiceGenerationExecutor가 이 실패를 기존 방식대로 처리(생성 중단)

[claude 경로]
CrudPromptBuilderService.buildPrompt()
  └─ (신규) outputPath 기준 자산 존재 확인(auto와 동일 판정 로직 재사용)
       └─ 없으면 프롬프트 최상단에 경고 블록 삽입
            "⚠️ KRDS 자산 없음 — initializeProject() 먼저 실행 권장"
       └─ (한계) saveGeneratedCode()는 파일 단위 Tool이라 여기서 차단 불가 — §4 참고
```

---

## 3. auto 경로 설계

### 3.1 신규 클래스 `KrdsAssetVerificationProcessor`

**위치**: `service/generation/crud/KrdsAssetVerificationProcessor.java` (신규)

`CrudFormColumnCssProcessor`와 동일하게 `GenerationStageProcessor`를 구현한다.

```java
public class KrdsAssetVerificationProcessor implements GenerationStageProcessor {
    private static final String[] WAR_PATHS = {
        "src/main/webapp/resources/css/_ds_bundle.css",
        "src/main/webapp/resources/js/krds.min.js"
    };
    private static final String[] BOOT_PATHS = {
        "src/main/resources/static/resources/css/_ds_bundle.css",
        "src/main/resources/static/resources/js/krds.min.js"
    };

    public ProcessorResult process(GenerationContext context) {
        Path root = context.projectRoot();
        if (allExist(root, WAR_PATHS) || allExist(root, BOOT_PATHS)) {
            return ProcessorResult.success();
        }
        return ProcessorResult.failed("KRDS 원본 자산 없음",
                List.of(new GenerationFailure(ID,
                        "_ds_bundle.css/krds.min.js — initializeProject()를 먼저 실행하세요")));
    }
}
```

(실제 `GenerationContext`/`ProcessorResult`/`GenerationFailure` 시그니처는 기존
`CrudFormColumnCssProcessor` 구현을 그대로 따라 맞춘다 — 위는 의도를 보이기 위한 스케치다.)

### 3.2 Blueprint 등록

**위치**: 기존 `CrudFormColumnCssProcessor`/`CrudTableDensityCssProcessor`가 등록된 Blueprint의
`ProcessorStep` 선언부 — 같은 자리에 `KrdsAssetVerificationProcessor`를 추가한다. 실행 순서는
다른 CSS 처리기보다 **먼저** 두는 것을 권장한다(자산이 없는데 CSS 보강부터 시도할 이유가 없음).

---

## 4. claude 경로 설계와 한계

### 4.1 신규 로직 — 프롬프트 경고 삽입

**위치**: `service/CrudPromptBuilderService.java`

`buildFullCrudPrompt()`가 최종 프롬프트 문자열을 조립하기 직전에, `outputPath` 기준으로 §3.1과
동일한 판정 로직(공유 유틸로 추출 권장 — auto/claude 중복 방지)을 호출해 없으면 프롬프트 최상단에
경고 블록을 삽입한다.

### 4.2 한계 — 반드시 인지해야 할 구조적 제약

`CodeSaverTool.saveGeneratedCode(filePath, code)`는 파일 하나를 독립적으로 저장하는 저수준 Tool이며
`projectRoot` 파라미터가 없다. 즉:

- 프롬프트 경고는 **안내일 뿐 강제 차단이 아니다** — Claude가 경고를 무시하고 코드를 작성해
  `saveGeneratedCode()`로 저장하는 걸 시스템이 막을 방법이 없다.
- `saveGeneratedCode()` 자체에 검증을 추가하려면 `filePath`에서 프로젝트 루트를 역추적해야 하는데,
  파일 하나의 절대경로만으로는 WAR/Boot 구조를 신뢰성 있게 판별하기 어렵고 **오탐(잘못된 경로를
  프로젝트 루트로 오인)** 위험이 있다. 이 대안은 이번 제안 범위에서 제외한다(§6).

---

## 5. 신규/수정 파일 목록

| 파일 | 구분 | 내용 |
|---|---|---|
| `service/generation/crud/KrdsAssetVerificationProcessor.java` | 신규 | auto 경로 검증 프로세서 |
| `service/generation/crud/KrdsAssetPresenceChecker.java`(가칭) | 신규 | WAR/Boot 판정 로직 공유 유틸(auto/claude 재사용) |
| Blueprint `ProcessorStep` 등록부(기존 CSS 프로세서와 동일 파일) | 수정 | 신규 프로세서 등록 |
| `service/CrudPromptBuilderService.java` | 수정 | claude 경로 프롬프트 경고 삽입 |
| 관련 테스트(auto 프로세서 성공/실패, claude 프롬프트 경고 삽입 여부) | 신규 | 회귀 테스트 |

---

## 6. 리스크 및 대응

| 리스크 | 영향 | 대응 |
|---|---|---|
| claude 경로는 안내만 가능, 강제 차단 불가 | 사용자가 경고를 무시하면 여전히 깨진 화면 저장 가능 | 의도된 한계로 문서화. 강제 차단이 꼭 필요하면 `saveGeneratedCode()` 검증(오탐 위험 있음)을 별도 승인 후 검토 |
| auto 경로 프로세서 순서가 잘못되면 다른 CSS 처리기가 먼저 실패할 수 있음 | 에러 메시지가 혼란스러울 수 있음 | KRDS 자산 검증을 다른 CSS 프로세서보다 먼저 배치 |
| WAR/Boot 판정 로직을 auto/claude에서 각각 구현하면 중복·불일치 위험 | 유지보수 비용 증가 | 공유 유틸 클래스로 추출(§5) |

---

## 7. 1차 구현 제외 범위

- `saveGeneratedCode()` 저장 시점 검증(오탐 위험으로 별도 검토 필요)
- `styles.css` 자체의 self-healing 로직 변경(이미 auto 경로에 존재, 이번 제안과 무관)
- claude 경로의 강제 차단 메커니즘 신설(구조적으로 어려움, §4.2)

---

## 8. 단계별 구현목록

### Phase 1 — 공유 판정 로직 (필수)

| 순서 | 작업 |
|---|---|
| 1 | `KrdsAssetPresenceChecker`(가칭) 신규 작성 — WAR/Boot 경로 판정, 단위 테스트 |

### Phase 2 — auto 경로 (필수)

| 순서 | 작업 |
|---|---|
| 2 | `KrdsAssetVerificationProcessor` 작성(기존 CSS 프로세서 패턴 재사용) |
| 3 | Blueprint `ProcessorStep`에 등록(다른 CSS 프로세서보다 먼저) |
| 4 | 자산 없음/있음 fixture로 통합 테스트 |

### Phase 3 — claude 경로 (필수)

| 순서 | 작업 |
|---|---|
| 5 | `CrudPromptBuilderService`에 경고 삽입 로직 추가 |
| 6 | 자산 없음/있음 케이스로 프롬프트 출력 테스트 |

### Phase 4 — 검증

| 순서 | 작업 |
|---|---|
| 7 | `./gradlew build` 전체 통과 확인 |

---

## 9. 검증 방법

1. auto 경로: `_ds_bundle.css`/`krds.min.js` 없는 fixture 프로젝트에 CRUD 생성 시도 → 생성 실패,
   명확한 에러 메시지 확인
2. auto 경로: 자산 있는 fixture에서는 기존과 동일하게 정상 생성 확인(회귀 없음)
3. claude 경로: 자산 없는 `outputPath`로 `buildFullCrudPrompt()` 호출 → 반환 프롬프트 최상단에
   경고 블록 포함 확인
4. `./gradlew build` — 전체 테스트 통과 확인

---

## 10. 관련 문서

- [`CRUD_생성_KRDS_자산_미검증_검토.md`](./CRUD_생성_KRDS_자산_미검증_검토.md) — 이 제안의 근거 검토
- [`Thymeleaf_레거시전환_KRDS_반영_검토.md`](./Thymeleaf_레거시전환_KRDS_반영_검토.md) — 17.1의 동일 문제 원 검토 및 해결안 B(구현 완료, 커밋 `65e0ab2`)
- `service/KrdsStylesConfigurer.java` — `styles.css` self-healing 참고 패턴
- `service/generation/crud/CrudFormColumnCssProcessor.java` — auto 경로 프로세서 패턴 참고
