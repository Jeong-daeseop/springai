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
