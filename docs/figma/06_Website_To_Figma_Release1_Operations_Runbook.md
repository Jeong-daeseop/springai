# Website → Figma Release 1 운영 절차

## 1. 신뢰 경계

Release 1은 로컬 단일 사용자와 비인증 JSP 화면만 지원한다. `springai`와 extractor 모두 loopback에 바인딩하며
운영 URL, 로그인 세션, SPA 및 다중 viewport를 활성화하지 않는다.

## 2. 기동

```bash
cd jsp-design-extractor
npm install
npx playwright install chromium
npm run build
EXTRACTOR_API_KEY=<별도-secret> \
EXTRACTOR_ALLOWED_ORIGINS=http://127.0.0.1:8080,http://localhost:8080 \
EXTRACTOR_LOG_FILE=/tmp/jsp-design-extractor.jsonl \
npm start
```

`springai`에는 서로 다른 `WEB_CAPTURE_EXTRACTOR_API_KEY`와 `WEB_CAPTURE_DOCUMENT_KEY_SECRET`을 설정하고
`WEB_CAPTURE_ENABLED=true`, `SERVER_ADDRESS=127.0.0.1`로 기동한다.
현재 결정론 매퍼 계약은 `rendered-design-mapper-v2`이다. `v1`의 form layout 값과 cache를 재사용하지 않는다.

## 3. 상태 및 장애 확인

`getWebCaptureStatus()`가 `READY`인지 먼저 확인한다. `DISABLED`면 설정을 확인하고, `ERROR`면 extractor health,
Chromium 설치 여부와 artifact 경로 쓰기 권한을 확인한다. 로그에는 전체 URL, query, Cookie, 인증 헤더나 DOM 원문을 남기지 않는다.
`EXTRACTOR_LOG_FILE`을 설정하면 extractor는 캡처 ID, origin hash, 건수, 크기, 오류 코드만 JSONL로 기록한다.
로그 파일의 상위 디렉터리는 기동 계정이 쓸 수 있어야 하며 운영자가 회전·보존 정책을 적용한다.

## 4. Secret 회전

1. 새 extractor key와 document HMAC secret을 각각 생성한다.
2. extractor를 새 key로 재기동한다.
3. `springai`의 두 환경변수를 갱신하고 재기동한다.
4. `getWebCaptureStatus()`로 확인한 뒤 이전 secret을 폐기한다.

두 secret은 같은 값을 사용하지 않는다. 회전 중 실패한 capture는 다시 실행하며 기존 artifact를 덮어쓰지 않는다.

## 5. Artifact 보존과 정리

artifact는 기본 24시간 보존한다. 새 artifact 저장 시 만료된 UUID 디렉터리를 정리한다. symbolic link, 임시 작업 디렉터리와
UUID가 아닌 운영자 파일은 자동 삭제하지 않는다. 긴급 수동 삭제는 기능을 중지한 뒤 정확한 artifact UUID 디렉터리만 대상으로 수행한다.

## 6. Figma import

`prepareFigmaImport()`가 반환한 `.figpack`을 Figma Desktop 개발 Plugin에서 선택한다. preview의 화면명·노드·경고를
확인한 뒤 생성한다. 높은 confidence의 Component 후보와 공통 Paint/Text Style은 사용자가 체크한 경우에만 생성한다.
같은 `documentKey + contentHash`는 중복 생성하지 않으며 Plugin 실패 시 이번 실행의 임시 Frame만 제거한다.

### 6.1 Figma Desktop 개발 Plugin 호환성 주의사항

- Figma main sandbox에는 브라우저의 Web Crypto `crypto.subtle`이 없을 수 있으므로 package hash는 번들에 포함된
  `@noble/hashes` SHA-256 구현으로 검증한다.
- macOS 파일 선택기에서 사용자 정의 `.figpack` 확장자가 비활성화되지 않도록 file input의 `accept` 속성을 강제하지 않는다.
  확장자가 아니라 ZIP entry, manifest, schema와 SHA-256 검증을 신뢰 경계로 사용한다.
- ZIP 라이브러리가 반환하는 `assets/` 같은 directory entry는 manifest의 파일 목록 비교에서 제외한다. 실제 file entry만
  entry 수, 경로와 hash 검증 대상에 포함한다.

### 6.2 2026-07-21 실제 검증 기준값

`build/figma-e2e`의 목록·상세·등록·수정 `.figpack`을 Figma Desktop 개발 Plugin으로 가져온 결과 preview는
각각 29·21·13·13개 노드와 경고 0개를 표시했다. 사용자 승인 후 네 화면 모두 1440×1200 최상위 Frame으로 생성됐다.
목록 Frame에는 image/SVG, Auto Layout, `TABLE/n14`·`BUTTON/n17` Component와 공통 Paint/Text Style이 생성됐다.
같은 목록 파일을 다시 생성하면
`같은 documentKey/contentHash Frame이 이미 존재합니다`로 거부되고 기존 Frame 한 개만 유지된다.
생성 단계에서 실패하는 계약 유효·SVG 변환 실패 fixture도 검증했으며, 오류 후 `IMPORTING` 임시 Frame과 실패 화면
Frame이 모두 남지 않았다. fixture는 `jsp-to-figma-plugin`의 `npm run fixture:cleanup`으로 다시 생성할 수 있다.

## 7. 실제 MCP 통합 회귀 검증

`springai`의 `bootJar`와 extractor 빌드가 준비된 로컬 개발 환경에서 다음 명령을 실행한다.

```bash
node scripts/web-capture-mcp-e2e.mjs
```

실행기는 임시 JSP fixture 서버, extractor와 Spring Boot를 각각 loopback 포트에 기동한다. MCP JSON-RPC로 상태 조회,
캡처, artifact 조회, Figma export, 결정론 분석과 `createScreenSpecification`을 호출하고 같은 분석을 두 번 실행해 cache 재사용을 검증한다.
파일 artifact와 프로세스는 종료 시 정리하지만, 분석 cache는 설정된 개발 DB의 `AI_DESIGN_ANALYSIS`에 보존될 수 있다.
Figma Desktop 수동 검수 파일이 필요하면 `--output-dir=build/figma-e2e`를 지정한다. 목록 흐름의 전체 분석과
화면명세 검증에 더해 목록·상세·등록·수정 `.figpack` 네 개가 보존된다.

## 8. 보안 회귀 검증

`jsp-design-extractor`에서 `npm test`를 실행하면 실제 Chromium으로 다음 항목을 검증한다.

- 다른 origin redirect 거부와 userinfo URL 거부, query 값 마스킹
- 허용하지 않은 외부 resource, Service Worker와 WebSocket의 서버 미도달
- popup 즉시 종료와 download 삭제 격리
- 민감 selector 적용 전후 `preview.png` SHA-256 차이를 통한 screenshot mask 확인
- API key, profile, viewport, origin 및 구조화 로그의 secret 비노출

Spring 테스트는 ZIP slip, 압축 해제 크기 초과, manifest hash 위변조, 대소문자 entry 충돌과 artifact path traversal을
각각 거부하는지 확인한다. 이 보안 검증이 실패한 package나 capture는 Figma import 대상으로 전달하지 않는다.
