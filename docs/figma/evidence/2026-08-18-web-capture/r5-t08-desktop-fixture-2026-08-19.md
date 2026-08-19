# R5-T08 Figma Desktop Fixture Evidence

- 일시: 2026-08-19 (Asia/Seoul)
- Figma 문서: `eGovFrame`
- 기준 Frame: `qna-list · egov.listPage · 1440px`
- 기준 URL node: `388-1060`
- 실행 Plugin: `eGovFrame FigmaScreenSpec Export`
- 실행 경로: `Desktop Frame 후보 새로고침` → 단일 1440px 후보 선택 → `Tablet/Mobile 검증용 복제본 생성`

## 런타임 결과

Plugin 상태 메시지:

```text
viewport fixture 생성 완료: TABLET:768px, MOBILE:390px
```

생성 정책:

| Fixture | 폭 | Grid | Gap | Padding |
|---|---:|---:|---:|---:|
| Desktop 원본 | 1440px | 12열 | 24px | 40px |
| Tablet 복제본 | 768px | 8열 | 16px | 24px |
| Mobile 복제본 | 390px | 4열 | 12px | 16px |

## 판정

- Desktop 후보 선택·viewport fixture 생성: **PASS**
- 동일 화면에 7종 요청을 순차 Apply하는 R5-T08 전체 시나리오: **잔여**
- Mobile Table→Card 시각 결과: fixture 생성 후 별도 확인 필요
