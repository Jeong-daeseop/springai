# R0-028 Figma Desktop 시각 검증

- 대상 파일: `eGovFrame` (`DlDsooAxMZngQpbzBXf0RD`)
- Desktop Frame: `388:1060`, `1440×821`
- Mobile Frame: `576:1489`, `390×302.5`

## 결과

- Desktop: Q&A 목록, 검색 패널, 6열 테이블, 페이지네이션이 정상적으로 캔버스에 렌더링됨.
- Mobile: **FAIL**. 외곽 Frame은 390px이나 내부 콘텐츠와 테이블 폭이 730px이며 6열 Table이 그대로 유지된다. Table→Card 변환 결과가 아니다.

증적 PNG:

- `qna-list-desktop-388-1060.png`
- `qna-list-mobile-576-1489.png`

이 결과는 R0-028을 완료로 승격하지 않고, 실제 Figma 화면의 논리 메타데이터가 `krds.dataTable`로 stamp된 신규 생성 경로와 기존 레거시 `ELEMENT:n*` 화면을 분리해야 함을 확인한다.
