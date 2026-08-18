# 2026-08-18 캡처·Figma Desktop 운영 증적

## 실행 결과

| 단계 | 결과 | 증적 |
|---|---|---|
| 로그인 세션 생성 후 보호 화면 캡처 | PASSED | `authenticated-qna-list.figpack`, `auth-fixture-result.json` |
| 민감정보 Redaction | PASSED | `auth-fixture-result.json` (`redaction: true`) |
| 비인증 캡처 보안·결정성 회귀 | PASSED | `scripts/web-capture-mcp-e2e.mjs` 실행 결과: 4개 fixture, 외부 리소스 0건, 결정성 Hash 통과 |
| Figma Desktop 첫 Apply | PASSED | `figma-generation-report-qna-list-it-78e93105-v1.json`, `figma-qna-list-apply-2026-08-18.jpeg` |
| Figma Desktop 동일 Bundle MERGE | PASSED | `figma-generation-report-qna-list-it-78e93105-v1.json`, `figma-qna-list-merge-2026-08-18.jpeg` |
| Figma Desktop 7개 Bundle 일괄 MERGE | PASSED | `figma-qna-7screen-batch-2026-08-18.jpeg` |
| 로그인된 실제 eGovFrame Q&A 7화면 캡처 | PASSED | `production-qna-*.jpg` |

## Figma 결과

- 최초 Apply: 신규 36, 재사용 0, Archive 0, Fallback 0
- 동일 Bundle 재적용: 신규 0, 재사용 36, Archive 0, Fallback 0
- `LAYOUT`: PASSED
- `ACCESSIBILITY`: PASSED
- `VISUAL_REGRESSION`: PASSED (두 번째 동일 Bundle MERGE 기준)
- 7개 Bundle(`qna-answer-create`, `qna-answer-detail`, `qna-answer-list`, `qna-create`, `qna-detail`, `qna-list`, `qna-update`) 일괄 검증: 각 Bundle `OK`, 전체 Apply 완료 메시지 확인
- 실제 로그인 세션에서 다음 7개 URL을 캡처했습니다: `/uss/olh/qna/selectQnaList.do`, `/uss/olh/qna/insertQnaView.do`, `/uss/olh/qna/selectQnaDetail.do`, `/uss/olh/qna/updateQnaView.do`, `/uss/olh/qna/selectQnaAnswerList.do`, `/uss/olh/qna/selectQnaAnswerDetail.do`, `/uss/olh/qna/updateQnaAnswerView.do`

## 실행 범위와 제한

인증 fixture는 운영 계정 대신 로컬 테스트 로그인 URL(`127.0.0.1:4331/login`)과 테스트 계정으로 구성했습니다.
추가로 로그인된 실제 eGovFrame Chrome 세션에서 Q&A 7개 URL의 화면 캡처를 별도로 완료했습니다. 캡처 파일에는
운영 자격증명이나 쿠키를 저장하지 않았습니다. 이번 실행에서는 로컬 테스트 Bundle 7개를 Figma Desktop에서 일괄 MERGE했습니다. Plugin이 제공하는
Generation Report는 마지막 화면(`qna-list`) 기준으로 저장되므로, 화면별 독립 Report가 필요하면 각 Bundle을
개별 Apply하고 Report를 별도로 다운로드해야 합니다.
