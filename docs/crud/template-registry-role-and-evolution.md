# Template Registry 역할과 단계적 발전 설계

> **작성일:** 2026-07-17  
> **대상:** CRUD·Board·MasterDetail Thymeleaf/FreeMarker 템플릿 선택 구조  
> **관련 컴포넌트:** `CrudTemplateRenderer`, `BoardTemplateRenderer`, `MasterDetailTemplateRenderer`, `CrudOrchestrationService`, `CrudPromptBuilderTool`, `ThymeleafLayoutTool`  
> **관련 문서:** `local-vision-design-reference-integration-review.md`

---

## 1. 결론

Template Registry의 핵심 역할은 다음과 같다.

> 사용 가능한 UI 템플릿을 등록·조회하고, 생성 조건에 맞는 검증된 템플릿을 선택하여 Renderer에 전달하는 중앙 카탈로그다.

Registry는 HTML을 직접 생성하거나 비전 모델을 호출하지 않는다.

```text
사용자 옵션 / DB 스키마 / 비전 분석 결과
                  ↓
              Registry
       사용할 템플릿 세트 결정
                  ↓
       FreeMarker Renderer
                  ↓
       Thymeleaf 생성 파일
```

현재 프로젝트에는 화면 유형별 승인된 Design Template이 사실상 하나씩이므로, 초기 구현은 enum과 Map으로 충분하다. manifest, semantic version, 원격 설치, 복잡한 호환성 행렬을 갖춘 정식 Template Pack Registry는 같은 archetype에 승인된 스타일이 둘 이상 생기는 시점에 도입한다.

---

## 2. Registry가 해결하는 문제

현재 Renderer는 `layerKey → *.ftl` 정적 Map을 사용한다.

```java
LAYER_TEMPLATE_MAP.put("thymeleafList", "thymeleaf-list.html.ftl");
LAYER_TEMPLATE_MAP.put("thymeleafDetail", "thymeleaf-detail.html.ftl");
LAYER_TEMPLATE_MAP.put("thymeleafRegist", "thymeleaf-regist.html.ftl");
LAYER_TEMPLATE_MAP.put("thymeleafUpdt", "thymeleaf-updt.html.ftl");
```

이 구조는 단일 템플릿에서는 충분하지만 다음 요구가 생기면 선택 정책이 여러 Renderer와 OrchestrationService에 흩어질 수 있다.

- 같은 Board 목록에 KRDS 기본형과 기관 특화형이 존재
- 기관마다 서로 다른 GNB/LNB/Footer 사용
- WAR와 Boot에 따라 사용할 수 있는 템플릿이 다름
- eGovFrame 4.3과 5.0의 템플릿 호환성이 다름
- 일부 템플릿 세트에 상세 또는 폼 화면이 없음
- 템플릿이 기대하는 `STATUS`, `ATTACHMENT` 필드가 DB에 없음
- 기존 프로젝트가 업데이트 후에도 동일한 디자인 버전을 유지해야 함

Registry는 다음 결정을 한 곳으로 모은다.

```text
어떤 생성 조건에서
어떤 검증된 FTL 세트를
어떤 fallback 정책으로 사용할 것인가?
```

---

## 3. Registry의 주요 역할

### 3.1 템플릿 등록

Registry는 사용할 수 있는 화면 유형과 실제 FTL 경로를 연결한다.

```text
CRUD_LIST          → crud/thymeleaf-list.html.ftl
CRUD_DETAIL        → crud/thymeleaf-detail.html.ftl
CRUD_FORM          → crud/thymeleaf-regist.html.ftl
BOARD_LIST         → board/thymeleaf-list.html.ftl
BOARD_DETAIL       → board/thymeleaf-detail.html.ftl
BOARD_FORM         → board/thymeleaf-regist.html.ftl
MASTER_DETAIL_LIST → masterdetail/thymeleaf-list.html.ftl
```

초기 단계에는 별도 DB나 manifest 파일이 필요하지 않다.

```java
public enum UiArchetype {
    CRUD_LIST,
    CRUD_DETAIL,
    CRUD_FORM,
    BOARD_LIST,
    BOARD_DETAIL,
    BOARD_FORM,
    MASTER_DETAIL_LIST,
    MASTER_DETAIL_DETAIL,
    MASTER_DETAIL_FORM
}
```

```java
public record ArchetypeTemplateSet(
        String list,
        String detail,
        String regist,
        String updt) {
}
```

### 3.2 템플릿 검색

입력 조건을 `UiArchetype`으로 정규화한 뒤 등록된 FTL 세트를 찾는다.

```java
UiArchetype archetype = resolver.resolve(
    featureType,
    pageType,
    designSpec
);

ArchetypeTemplateSet templateSet =
    registry.get(archetype);
```

비전 분석을 사용하는 경우 모델은 실제 파일 경로가 아니라 화면 유형만 반환한다.

```text
비전 분석 결과
  archetype = BOARD_LIST
  layoutShell = GNB_LNB_CONTENT
  density = COMFORTABLE
          ↓
Registry 조회
          ↓
board/thymeleaf-list.html.ftl
```

이 구조는 모델이 존재하지 않는 FTL 경로나 `krds-*` 클래스를 임의로 생성하는 것을 막는다.

### 3.3 호환성 검사

정식 Registry 단계에서는 선택된 템플릿이 현재 프로젝트 조건에서 동작하는지 검사한다.

검사 대상 예시:

- `viewType`: JSP 또는 Thymeleaf
- `featureType`: CRUD, Board, MasterDetail
- `layoutMode`: reuse, create, none
- `egovVersion`: 4.3, 5.0
- packaging: WAR, Boot
- 필수 정적 리소스 존재 여부
- 필수 layout fragment 존재 여부

예를 들어 특정 템플릿이 eGovFrame 5.0 WAR만 지원한다면 다음 요청은 생성 전에 차단한다.

```text
요청: eGovFrame 4.3 + Boot
템플릿: eGovFrame 5.0 + WAR 전용

결과: 호환성 검증 실패
```

런타임 오류보다 생성 전 명시적인 실패가 안전하다.

### 3.4 UI 데이터 계약 검사

화면 템플릿마다 필요한 데이터 역할이 다르다.

일반 CRUD 목록:

```text
필수: ID, 표시 필드
선택: STATUS, CREATED_AT
```

Board 목록:

```text
필수: ID, TITLE
선택: NOTICE, CATEGORY, ATTACHMENT, AUTHOR, CREATED_AT, VIEW_COUNT
```

DB 스키마의 실제 컬럼은 `UiFieldRole`로 변환한다.

```text
NTT_ID             → ID
NTT_SJ             → TITLE
NOTICE_AT          → NOTICE
ATCH_FILE_ID       → ATTACHMENT
FRST_REGIST_PNTTM  → CREATED_AT
```

필수 역할이 부족할 때의 정책:

1. 생성 중단
2. optional 컴포넌트 제거
3. 기본 CRUD archetype으로 fallback
4. 사용자에게 명시적인 `fieldBindings` 요청

사용자가 템플릿이나 archetype을 명시했다면 조용히 fallback하지 않고 실패시키는 편이 안전하다.

### 3.5 템플릿 세트 완전성 검사

CRUD 화면은 일반적으로 여러 파일의 묶음이다.

```text
list
detail
regist
updt
layout
gnb
lnb
breadcrumb
footer
CSS
JS
assets
```

예를 들어 FTC 참조가 목록과 상세만 제공하고 등록·수정 화면은 제공하지 않는다면 다음 정책 중 하나를 선택해야 한다.

```text
정책 A
  list/detail → FTC 구조
  regist/updt → 기본 CRUD 폼

정책 B
  완전한 CRUD 세트가 아니므로 생성 중단
```

Registry는 세트의 구성 상태를 제공하고, 혼합 사용 여부는 명시적인 정책으로 결정해야 한다.

### 3.6 Fallback 결정

권장 선택 우선순위는 다음과 같다.

```text
사용자가 명시한 archetype/style
        ↓
비전 분석이 추천한 archetype
        ↓
featureType 기반 기본 archetype
        ↓
krds-default
```

비전 분석이 `BOARD_LIST`를 반환했지만 테이블에 제목 필드가 없으면 Registry와 Validator는 다음과 같이 처리할 수 있다.

```text
BOARD_LIST
  → 필수 데이터 계약 실패
  → CRUD_LIST fallback
  → 경고 반환
```

예시 경고:

```text
이미지에서는 게시판 목록형이 탐지됐지만
제목 필드 계약을 충족하지 못했습니다.
기본 CRUD 목록 템플릿으로 생성합니다.
```

---

## 4. Registry와 관련 컴포넌트의 책임 구분

| 컴포넌트 | 책임 |
|---|---|
| `UiArchetypeResolver` | featureType, pageType, DesignReferenceSpec을 archetype으로 변환 |
| `ArchetypeTemplateRegistry` | archetype에 등록된 실제 FTL 세트 조회 |
| `TemplateCompatibilityValidator` | 프로젝트·DB 스키마·필수 필드 호환성 검사 |
| `CrudModelFactory` 계열 | DB 컬럼을 화면 필드 모델로 변환 |
| `CrudTemplateRenderer` 계열 | 선택된 FTL에 모델을 넣어 코드 렌더링 |
| `CodeService` | 생성 결과 파일 저장 |
| `VisionAnalysisClient` | 이미지/PDF에서 구조화된 디자인 명세 추출 |

호출 예시는 다음과 같다.

```java
UiArchetype archetype =
    archetypeResolver.resolve(featureType, designSpec);

ArchetypeTemplateSet templateSet =
    templateRegistry.get(archetype);

compatibilityValidator.validate(
    templateSet,
    generationContext,
    templateModel
);

String html = templateRenderer.render(
    templateSet.list(),
    templateModel
);
```

Registry가 FreeMarker 렌더링까지 수행하면 선택 정책과 생성 로직이 결합된다. 따라서 Registry는 선택·조회에 집중하고 실제 렌더링은 기존 Renderer가 담당해야 한다.

---

## 5. Registry와 비전 분석의 관계

비전 모델은 Registry를 대체하지 않는다.

### 비전 모델의 역할

- 화면이 Board 목록인지 CRUD 목록인지 분류
- GNB/LNB/Footer 구조 탐지
- 검색 폼, 테이블, 카드, 버튼 탐지
- 화면 밀도와 디자인 토큰 후보 추출
- 필요한 semantic field 후보 제안

### Registry의 역할

- 모델의 분류 결과를 실제 FTL 경로로 변환
- 등록된 템플릿만 반환
- 프로젝트 호환성 검사
- 필수 필드 계약 검사
- fallback 정책 적용
- 최종 선택 결과 기록

```text
비전 모델:
“이 이미지는 BOARD_LIST이고 첨부파일과 공지 배지가 있다.”

Registry:
“BOARD_LIST에 등록된 FTL은
 board/thymeleaf-list.html.ftl이다.
 TITLE은 필수이며 NOTICE와 ATTACHMENT는 선택 항목이다.”
```

모델이 다음과 같이 존재하지 않는 파일을 제안하더라도 Registry에 등록되지 않았다면 사용할 수 없다.

```text
templates/krds/modern-board-list-v2.html.ftl
```

---

## 6. 초기 경량 Registry

현재 단계에서는 다음 매핑만 있으면 충분하다.

```text
UiArchetype → list/detail/regist/updt FTL 경로
```

예시 구현:

```java
@Component
public class ArchetypeTemplateRegistry {

    public ArchetypeTemplateSet resolve(UiArchetype archetype) {
        return switch (archetype) {
            case CRUD_LIST, CRUD_DETAIL, CRUD_FORM ->
                new ArchetypeTemplateSet(
                    "crud/thymeleaf-list.html.ftl",
                    "crud/thymeleaf-detail.html.ftl",
                    "crud/thymeleaf-regist.html.ftl",
                    "crud/thymeleaf-updt.html.ftl"
                );

            case BOARD_LIST, BOARD_DETAIL, BOARD_FORM ->
                new ArchetypeTemplateSet(
                    "board/thymeleaf-list.html.ftl",
                    "board/thymeleaf-detail.html.ftl",
                    "board/thymeleaf-regist.html.ftl",
                    "board/thymeleaf-updt.html.ftl"
                );

            default -> throw new UnsupportedOperationException(
                "지원하지 않는 archetype: " + archetype);
        };
    }
}
```

초기 Registry가 담당하지 않아도 되는 기능:

- 별도 Registry DB
- 외부 Template Pack 설치
- manifest 파일
- semantic version
- 원격 다운로드
- pack dependency
- 동적 plugin loading
- 복잡한 호환성 행렬

현재는 코드·테스트·Git 이력으로 충분히 관리할 수 있다.

---

## 7. 정식 Template Pack Registry 승격 조건

다음 조건 중 하나가 실제로 발생할 때 정식 Registry를 도입한다.

### 7.1 동일 archetype에 여러 승인 스타일 존재

```text
BOARD_LIST
  ├─ krds-default
  ├─ ftc-public
  └─ ministry-compact
```

이때는 archetype만으로 선택할 수 없으므로 `styleId`가 필요하다.

```java
registry.resolve(
    UiArchetype.BOARD_LIST,
    "ftc-public"
);
```

### 7.2 기관별 디자인 분리

```text
공정거래위원회 → ftc-public
고용노동부     → moel-public
지자체         → local-government
```

### 7.3 배포 환경별 호환성 차이

```text
krds-ftc:1.0
  eGovFrame: 5.0
  packaging: WAR
  view: Thymeleaf
```

### 7.4 독립 배포와 롤백 필요

애플리케이션을 다시 배포하지 않고 Template Pack만 설치·업데이트·롤백해야 한다면 pack ID와 버전 관리가 필요하다.

### 7.5 기타 승격 조건

- 프로젝트별 팩 버전 고정 필요
- 라이선스·출처·지원 기능을 런타임에서 검사할 필요
- 외부 조직이 Template Pack을 제작·배포
- Template Pack 간 의존성 관리 필요

---

## 8. 정식 Registry의 manifest 예시

정식 Registry 단계에서는 다음과 같은 manifest를 사용할 수 있다.

```yaml
id: krds-ftc
version: 1.2.0
status: certified

supports:
  archetypes:
    - CRUD_LIST
    - CRUD_DETAIL
    - BOARD_LIST
    - BOARD_DETAIL
    - BOARD_FORM

compatibility:
  egovVersions:
    - "5.0"
  packaging:
    - war
  viewTypes:
    - thymeleaf

templates:
  boardList: board/thymeleaf-list.html.ftl
  boardDetail: board/thymeleaf-detail.html.ftl
  boardRegist: board/thymeleaf-regist.html.ftl
  boardUpdt: board/thymeleaf-updt.html.ftl

requiredFieldRoles:
  boardList:
    - ID
    - TITLE

optionalFieldRoles:
  boardList:
    - NOTICE
    - CATEGORY
    - ATTACHMENT
    - CREATED_AT

assets:
  css:
    - static/resources/css/styles.css
  js:
    - static/resources/js/krds.min.js
```

이 구조는 현재 즉시 구현할 대상이 아니라 다중 스타일과 독립 배포 요구가 생겼을 때의 확장 형태다.

---

## 9. 전체 생성 흐름

### 9.1 디자인 참조가 없는 경우

```text
buildFullCrudPrompt(featureType=board)
        ↓
featureType → BOARD archetype
        ↓
Registry에서 Board FTL 세트 조회
        ↓
DB 스키마로 BoardTemplateModel 생성
        ↓
FreeMarker 렌더링
        ↓
파일 저장·검증
```

### 9.2 디자인 참조가 있는 경우

```text
analyzeDesignReference(image)
        ↓
UiDesignSpec(archetype=BOARD_LIST)
        ↓
buildFullCrudPrompt(designReferenceId=...)
        ↓
Registry에서 Board FTL 세트 조회
        ↓
DB 필드와 semantic role 결합
        ↓
호환성 검사
        ↓
FreeMarker 렌더링
```

### 9.3 다중 스타일이 생긴 이후

```text
UiDesignSpec
  archetype = BOARD_LIST
  styleHint = FTC_PUBLIC
        ↓
Template Pack Registry
        ↓
krds-ftc:1.2.0 선택
        ↓
호환성·필수 필드 검사
        ↓
Renderer
```

---

## 10. Registry가 하지 말아야 할 일

Registry는 다음 책임을 가져서는 안 된다.

- PDF·이미지 읽기
- 비전 모델 호출
- DB 스키마 조회
- 컬럼명 의미 추론
- FreeMarker 렌더링
- 생성 파일 저장
- Controller·Service·Mapper 생성
- 외부 에셋 다운로드
- 모델 출력 HTML/JS 실행
- RAG 유사 화면 검색

Registry는 등록된 선택지와 조건을 관리하는 계층으로 제한한다.

---

## 11. SpringAI 최소 적용안

권장 최소 구성:

```text
model/ui/
  UiArchetype.java
  ArchetypeTemplateSet.java

service/
  ArchetypeTemplateRegistry.java
  UiArchetypeResolver.java
```

각 컴포넌트의 책임:

```text
UiArchetypeResolver
  featureType/designSpec → UiArchetype

ArchetypeTemplateRegistry
  UiArchetype → 실제 FTL 경로

CrudTemplateRenderer 계열
  FTL 경로 + TemplateModel → HTML
```

현재는 `CrudTemplateRenderer`, `BoardTemplateRenderer`, `MasterDetailTemplateRenderer`가 도메인별로 분리돼 있다. 따라서 처음부터 신규 Registry 계층을 크게 만들 필요는 없다. 기존 Renderer의 정적 Map을 명시적인 archetype Map으로 정리하는 것으로 시작할 수 있다.

초기 완료 기준:

- 모든 화면 archetype이 하나의 승인된 FTL 세트로 연결됨
- 미등록 archetype은 명시적으로 실패
- 기존 CRUD/Board/MasterDetail 생성 결과 회귀 없음
- 비전 분석이 없어도 현재 featureType만으로 정상 선택
- 비전 분석이 있는 경우 실제 파일 경로가 아니라 archetype만 전달

---

## 12. 최종 권고

Registry라는 이름의 큰 시스템을 먼저 만드는 것이 목적이 아니다. 현재 필요한 것은 다음 결정을 코드 한 곳에 모으는 것이다.

> 어떤 생성 조건에서 어떤 검증된 FTL 세트를 사용할 것인가?

현 단계에서는 다음 구조가 적절하다.

```text
featureType 또는 DesignReferenceSpec
              ↓
         UiArchetype
              ↓
     단순 Java Map/enum Registry
              ↓
       기존 FreeMarker Renderer
```

다음 상황이 발생하기 전에는 정식 Template Pack Registry를 만들지 않는다.

- 같은 archetype에 승인된 스타일이 2개 이상 존재
- 기관별 템플릿 선택 필요
- 애플리케이션과 독립적인 팩 배포·롤백 필요
- eGovFrame·packaging별 호환성 관리 필요

즉, 지금은 단순 Map이면 충분하며 실제 다중 스타일 요구가 생긴 시점에 manifest·버전·호환성 관리가 있는 정식 Registry로 승격하는 것이 적절하다.

