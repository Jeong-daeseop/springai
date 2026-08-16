# KRDS Figma Role·Variant 구현 아키텍처 가이드 (요약본)

> 정본(공식 구현 계약): [KRDS Figma Role·Variant 구현명세서](./KRDS_Figma_Role_Variant_구현명세서.md)
> 상세 역할 해설·worked example: [Figma 화면 생성 3계층 역할 가이드](./Figma_화면생성_3계층_역할가이드.md)

> 2026-08-17 개정: 이 문서는 원래 구현명세서 §3(설계 원칙)·§4(도메인 모델)·§6(Resolver 명세)·
> §8(Validation Gate) 등을 독립적으로 다시 서술한 전체 분량 문서였다. 같은 내용이 세 문서에
> 중복 서술되어 있어 한쪽만 갱신되고 다른 쪽이 갱신되지 않는 드리프트가 실제로 발생했다(Q&A
> 화면 수 6→7 개정이 이 문서와 3계층 역할 가이드에는 반영됐지만 정본에는 누락됐던 사례). 이후
> 정본을 구현명세서로 고정하고, 이 문서는 빠르게 훑어볼 요약 + 정본 섹션 링크로 축소했다.
> 세부 내용(도메인 모델 Java 코드, 계약·Schema, Resolver 처리 순서, 각 Gate의 정확한 검증 항목,
> 오류 코드, 저장·버전 정책 등)은 항상 정본을 확인한다.

## 1. 핵심 결론

```text
ScreenSpecification에는 Figma Key를 저장하지 않는다.
Component와 Variant는 서버가 모두 결정한다.
Figma Plugin은 추론하지 않고 검증·적용만 한다.
```

전체 구조는 시각 후보 생성, 업무 명세 승인, 결정적 KRDS 화면 생성의 세 계층으로 구성한다.
전체 흐름 다이어그램과 각 계층의 정확한 입력·출력·강제 규칙은 정본
[§3.1 화면 생성 3계층 아키텍처](./KRDS_Figma_Role_Variant_구현명세서.md#31-화면-생성-3계층-아키텍처)를 참고한다.

## 2. 계층별 책임 요약

| 계층 | 핵심 책임 | 정본 상세 섹션 |
|---|---|---|
| Visual Candidate Generator | 화면의 시각적 후보 생성 (`generate_figma_design`) | §3.1.1 |
| Source Reference | 기존 소스·이미지의 출처와 분석 근거 관리 | §3.1.2 |
| ScreenSpecification | 업무 의미와 화면 요구사항 확정(Source of Truth) | §3.1.3, §4.4 |
| Semantic Builder | 의미 기반 화면 구조 생성 (Component/Variant 미선택) | §7 |
| Screen Pattern Validator | 구조·Slot·순서 검증 | §8 Gate 1 |
| Component Role Resolver | Semantic Role → 정확히 하나의 Logical Component | §6 |
| Variant Rule Resolver | Component Resolution Context → Published Variant Key | §6 |
| Component Contract Preflight | 실제 Figma Library 계약과 Drift 0 검증 | §8 Gate 2 |
| FigmaScreenSpec | 완전히 해결된 실행 명세 | §4, §5 |
| Bundle | 실행에 필요한 버전별 계약 Snapshot 묶음 | §7 |
| Figma Plugin | 실제 Figma Preview·Atomic Apply·후검증 | §7, §8 Gate 4~5 |
| Quality Gate | Layout·접근성·Visual 검증, FATAL/ERROR 시 Apply 차단 | §8, §9 |

## 3. 남은 구현 과제 (2026-08-17 기준)

`qna-update` 추가와 Q&A 화면 수 6→7 개정은 완료되어 목록에서 제거했다(정본 §1.2/§14.4 참고).
남은 항목:

```text
SourceReference 모델·Repository
JSP·HTML·Thymeleaf 정적 분석 서비스
Visual Candidate와 ScreenSpecification 차이 검토 기능
앞단 승인 Gate 저장 모델
Suite 전체 원자적 생성·저장
```

## 4. 최종 요약

```text
시각 아이디어 → generate_figma_design
업무 확정 → ScreenSpecification
결정적 KRDS 변환 → Builder + Runtime Resolver
실행 명세 → FigmaScreenSpec + Bundle
실제 화면 → Plugin Atomic Apply
```
