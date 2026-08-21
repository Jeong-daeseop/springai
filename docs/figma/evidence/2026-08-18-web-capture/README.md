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
| Figma REST 원본 Frame export | PASSED | `figma-qna-list-original-frame-2026-08-20.png` (`nodeId=2:20`, 1280×875, SHA-256 `68ab547e…b6a6a0f`) |
| Q&A 원본·semantic Frame export | PASSED | `figma-qna-list-reference-frame-306-2-2026-08-20.png` / `figma-qna-list-semantic-frame-388-1060-2026-08-20.png` |

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

## 2026-08-20 시각 비교 후속

Figma REST API에서 같은 `eGovFrame` 파일의 원본 `Q&A 목록` Frame `306:2`(1200×420)와
semantic `qna-list · egov.listPage` Frame `388:1060`(1440×915)를 각각 export했다.
`FigmaVisualEvidenceComparisonTest`가 두 이미지의 크기 불일치를 실제로 `FAILED`로 기록한다.
현재 원본은 1200×420의 축소 화면이고 semantic 결과는 Desktop 1440×915이므로, 자동 resize 후
비교하지 않는다. 동일 viewport로 원본을 다시 캡처하거나 semantic Frame을 원본 viewport로
재생성한 뒤에만 0.1% pixel Gate를 최종 판정한다.

### 2026-08-20 비교 대상 정정 — 원본 재캡처 vs Plugin 재현 Frame

위 비교(306:2 vs 388:1060)는 **설계 의도와 맞지 않는 짝**이었다.
`FigmaScreenSpec과_figpack_개념_및_역할_비교분석.md` §7.2가 명시하듯 semantic
Frame(`FigmaScreenSpec`/KRDS 재설계 트랙, 388:1060)은 "원본 픽셀을 그대로 복제하지는
않는다" — DB 스키마 기반으로 새로 설계된 화면(가짜 샘플 데이터 포함)이라 원본 JSP
캡처와 애초에 다른 그림이다. viewport 크기를 맞춰도 픽셀 비교가 성립하지 않는다.

R7-015/R7-T04가 실제로 검증해야 하는 것은 `.figpack` 트랙 안에서 "Plugin이 캡처 내용을
얼마나 충실히 Figma 노드로 재현했는가"다. 이를 위해:

1. `captureWebPage` MCP Tool로 `localhost:8081`의 실제 QnA 목록 화면을 viewport
   1440×1200으로 재캡처(`artifactId=36274e54-8900-4c55-8a51-e8ee19990de1`, 노드 69개)
2. `prepareFigmaImport`로 `.figpack` export 후 `jsp-to-figma-plugin`(Figma Desktop,
   Component 생성 옵션 전부 미선택)으로 새 Frame `574:1249` 생성(영역 이탈 경고 4건,
   생성 자체는 성공)
3. Figma REST로 캡처의 `preview.png`(브라우저 원본, 1440×1200)와 `574:1249`
   export(1440×1200)를 `FigmaVisualComparisonService`로 비교

결과: 크기는 일치(1440×1200)하고 **차이율 0.76%**. 여전히 0.1% 임계값은 초과한다.

**진단 정정**: 처음엔 이 차이를 Chromium/Figma 간 폰트 anti-aliasing 차이로 추정했으나,
`qna-list-preview-vs-plugin-diff-2026-08-20.png`의 원본 좌상단 영역(테이블·검색 UI)만
잘라 원본/재현본을 나란히 대조한 결과 **실제 텍스트 재현 누락 버그**로 확인됐다:

- 데이터 테이블의 "질문제목" 컬럼 셀 텍스트가 Plugin 재현 Frame(`574:1249`)에서 두 행 모두
  비어 있음(원본 `preview.png`에는 "질문제목" 텍스트가 존재)
- 검색 영역의 "조회" 버튼 라벨 텍스트도 재현 Frame에서 빠져 있고 빈 파란 사각형만 남음

이 비교(원본 캡처 vs Plugin 재현 Frame)는 실제로 이 결함을 검출해냈으므로 비교 방식
자체는 유효하다고 판단한다.

**근본 원인 확정 및 수정 (2026-08-20 후속)**: `document.json`을 직접 조사한 결과 원인은
`jsp-to-figma-plugin`(재현 단계)이 아니라 **`jsp-design-extractor`(캡처 단계)**였다.
실제 HTML에서 "질문제목"/"조회"는 `<input type="submit" value="...">` 요소로 구현돼
있는데, `<input>`은 자식 텍스트 노드가 없고 보이는 글자는 `value` 속성에서 나온다.
`jsp-design-extractor/src/server.ts`의 노드 타입 판정은 `tag === "button"`인 경우만
`BUTTON`으로 인식했고(따라서 `<input type=submit>`은 `ELEMENT`로 분류), 텍스트 추출도
`element.textContent`만 사용했다(`<input>`은 항상 빈 문자열) — 그 결과 `document.json`에
텍스트 자체가 애초에 담기지 않았다. Plugin은 없는 데이터를 재현할 수 없었을 뿐이다.

`input[type=submit|button|reset]`을 `BUTTON`으로 인식하고 `.value`를 텍스트로 읽도록
수정했다(`typecheck`/`build`/`lint`/기존 E2E — fixture 4종 결정론적 hash 유지 — 통과).
수정된 Extractor로 동일 QnA 목록을 직접 재캡처해 `document.json`에
`BUTTON input '조회'`, `BUTTON input '질문제목'`(2건)이 정상적으로 담기는 것을 확인했다.

### 2026-08-20 최종 검증 — 수정된 Extractor로 Figma Desktop 재-Import·재-export

수정된 Extractor로 동일 QnA 목록을 재캡처(`artifactId=bae6cbdc-bd35-43ca-ad68-689746510cea`,
`document.json`에 `BUTTON input '조회'`·`BUTTON input '질문제목'`(2건) 정상 포함 확인) →
`jsp-to-figma-plugin`으로 Figma Frame 재생성(`576:1327`) → Figma REST로 재-export →
캡처의 `preview.png`와 `FigmaVisualComparisonService`로 재비교했다.

결과: 크기 일치(1440×1200), **차이율 0.79%**. `조회`/`질문제목` 텍스트는 이제 양쪽 모두
존재하며(재-export 이미지에서 육안 확인), diff 이미지(`qna-list-preview-vs-plugin-diff-2026-08-20-fixed.png`)는
모든 텍스트에 고르게 얇은 테두리만 나타난다 — 이번엔 실제로 Chromium/Figma 간 폰트
렌더링(anti-aliasing) 차이로 판단된다(콘텐츠 누락 아님). 즉 **실제 재현 결함은 수정·검증
완료**됐다.

**정책 결정(2026-08-20 확정)**: cross-renderer 폰트 렌더링 차이에도 0.1% 임계값
(`FigmaVisualComparisonService.DEFAULT_MAX_DIFFERENCE_RATIO`)을 **그대로 유지**하기로
결정했다. 즉 이 비교(캡처 원본 vs Plugin 재현 Frame)는 텍스트가 있는 실제 화면에서
앞으로도 통상 `FAILED`로 기록되는 것이 정상 baseline이다 — 미해결 결함이 아니라
"Chromium 렌더링과 Figma 렌더링은 픽셀 단위로 동일할 수 없다"는 사실을 있는 그대로
기록하는 증적이다. anti-aliasing을 걸러내는 별도 diff 알고리즘을 도입하지 않는 한
이 상태를 유지한다.

**(2026-08-20 정정)** 이전 기록은 `localhost:8081`을 "로컬 개발 데모"로 보고 R7-002의
"외부 운영 로그인 URL" 캡처를 별도 잔여로 남겼다. 사용자 확인 결과 `localhost:8081`은
로컬 데모가 아니라 **이 시스템의 유일한 실체인 실제 eGovFrame 운영 Docker 서버**(가짜
스텁이 아닌 진짜 로그인 폼·업무 로직)이며, 별도로 존재하는 외부 배포 URL은 없다. 즉
"외부"라는 원래 문구는 존재하지 않는 시스템을 요구하는 비현실적 기준이었다 — 위 캡처·
비교 증적이 R7-002가 요구한 운영 로그인 URL 캡처를 실질적으로 충족한다.

최종 증적: `qna-list-1440-capture-preview-2026-08-20-fixed.png`(원본),
`figma-qna-list-plugin-reproduction-frame-576-1327-2026-08-20-fixed.png`(Plugin 재현,
수정 후), `qna-list-preview-vs-plugin-diff-2026-08-20-fixed.png`(diff, 수정 후).
수정 전 증적(`...574-1249...`, 텍스트 누락 상태)은 문제 발견 과정의 기록으로 그대로 보존한다.
