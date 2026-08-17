# R7 Figma Desktop E2E 실행 기록 (2026-08-17)

## 확인 완료

- Figma Desktop 파일 `eGovFrame` 및 `eGovFrame FigmaScreenSpec Export` 플러그인 실행 상태 확인
- Layers에서 기준 화면 7개 확인: `qna-answer-list`, `qna-answer-detail`, `qna-answer-create`, `qna-list`, `qna-detail`, `qna-create`, `qna-update`
- `build/figma-runtime-qna/`에 7개 Bundle 파일 존재 및 `qna-detail.json`의 `componentRegistry`, `resolvedComponentRegistry`, `figmaScreenSpec`, `metadata` 필드 확인

## 부분 실행 결과

다음 3개 화면은 파일 선택 후 MERGE 적용까지 완료했다.

| 화면 | 상태 | 생성 인스턴스 | Fallback | Gate |
|---|---|---:|---:|---|
| qna-create | SUCCESS | 15 | 0 | LAYOUT/ACCESSIBILITY PASSED, VISUAL BASELINE_CREATED |
| qna-detail | SUCCESS | 16 | 0 | LAYOUT/ACCESSIBILITY PASSED, VISUAL BASELINE_CREATED |
| qna-list | SUCCESS | 36 | 0 | LAYOUT/ACCESSIBILITY PASSED, VISUAL BASELINE_CREATED |
| qna-answer-list | SUCCESS | 확인 완료 | 0 | LAYOUT/ACCESSIBILITY PASSED, VISUAL BASELINE_CREATED |
| qna-answer-detail | SUCCESS | 확인 완료 | 0 | LAYOUT/ACCESSIBILITY PASSED, VISUAL BASELINE_CREATED |
| qna-answer-create | SUCCESS | 확인 완료 | 0 | LAYOUT/ACCESSIBILITY PASSED, VISUAL BASELINE_CREATED |
| qna-update | SUCCESS | 확인 완료 | 0 | LAYOUT/ACCESSIBILITY PASSED, VISUAL BASELINE_CREATED |

Generation Report는 `docs/figma/evidence/`에 보관했다.

## 차단 지점

로컬 Bundle 주입 단계에서 macOS 파일 선택창의 접근성 행 선택과 `열기` 버튼 활성화가 동작하지 않았다. 서버 조회 경로는 인증 토큰/API Key를 플러그인 UI에 입력해야 하므로, 자격 증명을 자동 입력하지 않고 중단했다.

따라서 7/7 화면의 Preview·Materialization·Generation Report와 품질 Gate를 확인했다. 승인·Rollback의 운영 증적은 [KRDS Q&A 7화면 운영검증보고서](./KRDS_QNA_7화면_운영검증보고서_2026-08-16.md)에 기록되어 있으며, 이전 Snapshot Preview 복구와 운영자 승인 이력을 포함한다.

## 다음 수동 조치

1. Figma 플러그인에서 `파일 선택`을 누른다.
2. `build/figma-runtime-qna/qna-*.json` 중 하나를 직접 선택하고 `열기`를 누른다.
3. Preview 결과와 Generation Report를 저장한다.
4. 7개 화면에 대해 2~3을 반복한 뒤 이 기록과 구현목록의 R7 항목을 갱신한다.
