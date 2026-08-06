# jsp-design-extractor

Playwright/Chromium으로 허용된 로컬 JSP 화면을 캡처해 `figpack-v1`을 반환합니다.

```bash
npm install
npx playwright install chromium
npm run build
EXTRACTOR_API_KEY=change-me \
EXTRACTOR_ALLOWED_ORIGINS=http://127.0.0.1:8080,http://localhost:8080 \
npm start
```

서버는 `127.0.0.1:4319`에만 바인딩됩니다. `X-Extractor-Key`가 없거나 일치하지 않으면 요청을 거부합니다.
`npm test`는 실제 Chromium으로 고정 fixture를 캡처하고 `.figpack`, content hash와 input value 미수집을 검증합니다.

## WP8 Browser Validation Gate

브라우저 Gate는 완성된 HTML 문자열 또는 loopback URL 중 하나를 입력으로 받아 Chromium에서
1440×1200, 768×1024, 390×844 화면을 검증합니다. 각 viewport마다 실제 수평 overflow,
JavaScript/resource 오류, axe 접근성, PNG visual diff를 실행합니다.

```bash
npm run test:browser-gate
npm run browser-gate -- --request /absolute/path/request.json
```

요청 예시:

```json
{
  "screenId": "employee-list",
  "url": "http://127.0.0.1:8080/emp/employerList.do",
  "artifactDirectory": "/tmp/wp8-artifacts",
  "baselineDirectory": "/project/visual-baselines",
  "maskSelectors": ["[data-dynamic]"],
  "readySelector": "main",
  "maxDifferenceRatio": 0.001,
  "timeoutMillis": 30000
}
```

외부 URL과 외부 resource 요청은 차단됩니다. 기준 이미지는
`<screenId>-desktop.png`, `<screenId>-tablet.png`, `<screenId>-mobile.png` 이름으로 준비합니다.
기준 이미지가 없으면 현재 캡처를 자동 승인하지 않고 `BASELINE_MISSING`으로 차단합니다. 결과 JSON,
현재 screenshot 및 diff PNG는 `artifactDirectory`에 저장됩니다.

Java Adapter는 기본적으로 저장소 루트의 `jsp-design-extractor/scripts/browser-gate.mjs`를 실행합니다.
배포 경로가 다르면 `WP8_BROWSER_GATE_RUNNER`, Node 실행 파일이 다르면 `WP8_NODE_BINARY`, 전체 프로세스
timeout을 바꾸려면 `WP8_BROWSER_GATE_TIMEOUT_SECONDS`를 지정합니다.
