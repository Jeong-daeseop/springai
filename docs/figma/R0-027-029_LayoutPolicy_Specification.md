# R0-027~029: Layout Policy 및 Component Swap 사양

> 설계·계약 결정 문서(작성일 2026-08): designSystemProfileId 계약, Platform Layout 정책, 샘플 fixture

> ⚠️ 이 문서는 **설계·계약 정의**를 다루며, 아래 "승인 상태"의 "✅ 정의 완료"는 코드 구현
> 완료를 의미하지 않는다. 실제 코드 구현 상태는
> `12_Semantic_Figma_Design_System_Implementation_List.md`가 1차 근거이며, 2026-08-17 기준
> R0-027~029는 `[~]`(부분 구현 — `DesignSystemProfile`에 `layoutPolicyVersion`/`layoutPolicy`
> 필드는 있으나 아래 `DefaultLayoutPolicy` record와 Platform 변환·Component Swap 정책 Schema는
> 미구현), R1-015(`DefaultLayoutPolicy`/`PlatformLayoutPolicy`/`ComponentSwapPolicy` 실제 구현)는
> `[ ]`(미착수)다.

## 개요

Figma 화면 생성 시 기본 레이아웃 정책과 플랫폼별 변환 규칙을 정의합니다.

---

## R0-027: designSystemProfileId 계약

### 정의
`designSystemProfileId`는 다음 세 가지를 원자적으로 결합합니다:
- **Token/Variable**: 색상, 타이포그래피, 간격, radius 정의
- **Component Registry**: Publish된 컴포넌트 목록 및 버전
- **Default Layout Policy**: 기본 화면 레이아웃 정책

### 형식
```
{profileId}:{profileVersion}:{registryVersion}
```

예: `krds:1.0:2026.07`

### 검증 규칙
1. 세 버전이 모두 일치해야 함 (mismatch = ERROR)
2. Publish 후 변경 불가 (immutable)
3. Fallback: `designSystemProfileId` 없으면 DEFAULT_PROFILE 사용

---

## R0-028: Platform Layout Policy

### 기본 정책 (v1)

| Platform | Width | Grid | Columns | Navigation | Comment |
|----------|-------|------|---------|------------|---------|
| DESKTOP | 1440px | 12 | 12 | Top GNB | 기본 타겟 |
| TABLET | 768px | 8 | 8 | Side LNB | 중간 해상도 |
| MOBILE | 390px | 4 | 4 | Bottom Tab | 모바일 우선 |

### Component Swap Policy (Desktop → Mobile)

#### Button
```
Desktop: krds.button.large (56px height)
Tablet:  krds.button.medium (48px height)
Mobile:  krds.button.small (40px height)
```

#### TextField
```
Desktop: krds.textField.full (400px width)
Tablet:  krds.textField.full (80% width)
Mobile:  krds.textField.full (100% width)
```

#### Table
```
Desktop: krds.table.standard (모든 컬럼 표시)
Tablet:  krds.table.compact (주요 컬럼만)
Mobile:  krds.table.card (카드형 변환)
```

### Layout Annotation Rules

#### Responsive Breakpoints
```json
{
  "breakpoints": {
    "desktop": { "min": 1024, "maxColumns": 12 },
    "tablet": { "min": 768, "max": 1023, "maxColumns": 8 },
    "mobile": { "min": 0, "max": 767, "maxColumns": 4 }
  }
}
```

#### Grid Settings
```
Desktop: gap=24px, padding=40px
Tablet:  gap=16px, padding=24px
Mobile:  gap=12px, padding=16px
```

#### Navigation Patterns
```
Desktop: Horizontal Top GNB + Left LNB
Tablet:  Collapse LNB, Show Bottom Action Bar
Mobile:  Bottom Tab Navigation (4-5 tabs max)
```

---

## R0-029: 샘플 Fixture 변환

### 1. CRUD List 화면 (Desktop → Mobile)

#### Desktop (1440px / 12 columns)
```
┌─────────────────────────────────────────┐
│  GNB (회사 로고 | 메뉴 | 사용자)         │
├─────────────────────────────────────────┤
│ │ LNB │  사용자 목록                    │
│ │     ├─ [검색] [등록] [삭제]           │
│ │     ├─────────────────────────────────┤
│ │     │ ID │ 이름 │ 이메일 │ 부서 │...│
│ │     ├─────────────────────────────────┤
│ │     │ 1  │ Kim  │ kim@  │ 영업 │...│
│ │     └─────────────────────────────────┘
│ │     [← 1 2 3 4 5 →]                   │
└─────────────────────────────────────────┘
```

#### Mobile (390px / 4 columns)
```
┌──────────────┐
│ ≡ 사용자 목록 X│
├──────────────┤
│ [검색......] │
├──────────────┤
│ [1] Kim      │
│ kim@...      │
│ ━━━━━━━━━━━━ │
│ [2] Lee      │
│ lee@...      │
│ ━━━━━━━━━━━━ │
├──────────────┤
│ ← 1 2 3 4 → │
├──────────────┤
│ [+등록] [⋮]  │
└──────────────┘
```

### 2. 폼 입력 화면 (Desktop → Mobile)

#### Desktop (2 columns)
```
사용자 등록
┌──────────┬──────────┐
│ 이름     │ 이메일   │
│ [____]   │ [____]   │
├──────────┴──────────┤
│ 부서                 │
│ [________________]   │
├──────────────────────┤
│ [등록]  [취소]       │
└──────────────────────┘
```

#### Mobile (1 column)
```
사용자 등록
┌──────────────┐
│ 이름         │
│ [________]   │
├──────────────┤
│ 이메일       │
│ [________]   │
├──────────────┤
│ 부서         │
│ [________]   │
├──────────────┤
│ [등록]       │
│ [취소]       │
└──────────────┘
```

---

## 구현 범위 및 제약

### v1 지원 범위
- ✅ Basic CRUD (List, Form, Detail)
- ✅ Button, TextField, Select, Table, Pagination
- ✅ Desktop ↔ Mobile 기본 변환
- ✅ GNB/LNB 기본 구조

### v2 후속 항목
- ⏳ Dashboard 레이아웃
- ⏳ Custom Component Swap 정책
- ⏳ Animation/Transition 정의
- ⏳ Dark Mode 지원

---

## 적용 방식

### 1. DesignSystemProfile에 포함
```java
public record DefaultLayoutPolicy(
    String profileId,
    String platform,           // DESKTOP, TABLET, MOBILE
    int width,                 // px
    int gridColumns,           // 12, 8, 4
    Map<String, String> componentSwaps,  // krds.button.large → krds.button.small
    GridSettings gridSettings
) {
}
```

### 2. 화면 생성 시 적용
```java
FigmaScreenSpec spec = screenExportService.export(
    screenSpec,
    designSystemProfile,
    layoutPolicy  // Platform + Component Swap 자동 적용
);
```

### 3. Plugin에서 검증
```typescript
// Figma Plugin: core.ts
const validateLayoutPolicy = (spec: FigmaScreenSpec, profile: DesignSystemProfile) => {
    // Platform별 width 검증
    // Component Swap 가능 여부 확인
    // Grid gap/padding 적용
};
```

---

## 버전 관리

| 버전 | 출시 | 주요 변경 |
|------|------|---------|
| 1.0 | 2026-08 | 기본 CRUD + 3 Platform |
| 1.1 | 2026-09 | Dashboard + Custom Swap |
| 2.0 | 2026-Q4 | Animation + Dark Mode |

---

## 승인 상태 (설계·계약 정의 기준 — 코드 구현 상태 아님)

- **R0-027**: ✅ 계약 정의 완료 (designSystemProfileId 원자성) / 🔄 코드 구현은 `[~]` — `DefaultLayoutPolicy` record 미구현(R1-015)
- **R0-028**: ✅ 정책 정의 완료 (Platform Layout + Component Swap) / 🔄 코드 구현은 `[~]` — Platform 변환 Schema·Component Swap 정책은 I-1/2-A6 범위로 남김
- **R0-029**: ✅ 샘플 Fixture 설계 완성 / 🔄 코드 구현은 `[~]` — `component-catalog-v1.json` 5+5종 기준선만 존재, 11개 예시 컴포넌트 전체 카탈로그는 Design System Library 정식 Publish 이후 진행

**다음 단계**: R1-015(코드 구현), R6-040~048에서 후속 진행 — 최신 진행 상태는 12번 문서 참고
