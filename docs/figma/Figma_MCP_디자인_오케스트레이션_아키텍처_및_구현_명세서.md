Figma MCP 디자인 오케스트레이션 아키텍처 및 구현 명세서
버전: 1.0.0  |  작성일: 2026-07-30  |  목적: 디자인 시스템 컴포넌트를 활용한 업무화면 자동 생성 시스템
1. 개요
본 문서는 디자인 시스템 컴포넌트를 활용하여 Figma에서 업무화면을 자동 생성하는 MCP(Model Context Protocol) 서버의 아키텍처와 구현 명세를 정의합니다.

[핵심 기능]
• 7가지 디자인 요청 방식 지원 (텍스트 설명, 참조 생성, 수정, 이미지 참조, 멀티 스크린, 컴포넌트 지정, 플랫폼 변환)
• 디자인 시스템 컴포넌트 자동 매칭 및 배치
• Claude API를 활용한 도메인 컨텍스트 분석
• Figma Plugin API를 통한 실시간 디자인 생성

[디자인 시스템 교체 가능성]
본 시스템은 디자인 시스템에 종속되지 않는 구조로 설계되었습니다.
디자인 시스템 설정 파일을 교체하면 다른 디자인 시스템으로 전환할 수 있습니다.
2. 디자인 시스템 설정 (교체 가능 영역)
이 섹션은 디자인 시스템이 변경될 때 수정해야 하는 유일한 영역입니다.

[2.1 설정 파일: config/design-system.json]
• name: "KRDS" | version: "1.0.0"
• figmaFileKey: "6fcm04dwSEH2IUizZfaZCj" | libraryKey: "lk-local"
• colors: primary #0054A6, secondary #505050, background #FFFFFF, error #D32F2F, success #2E7D32
• typography: Pretendard / heading1: 28px 700 / heading2: 22px 700 / body1: 16px 400
• spacing: xs=4, sm=8, md=16, lg=24, xl=32, xxl=48
• radius: sm=4, md=8, lg=12

[2.2 컴포넌트 카탈로그: config/component-catalog.json]
• text_input (StateGroupId:286:25890) - 텍스트 입력 필드 [default, error, disabled, readonly]
• text_area (StateGroupId:343:43306) - 텍스트 영역 [default, error, disabled]
• selectbox (StateGroupId:288:26192) - 드롭다운 선택 상자 [default, open, disabled]
• checkbox (StateGroupId:309:25967) - 체크박스 [unchecked, checked, disabled]
• radio_button (StateGroupId:313:27198) - 라디오 버튼 [unselected, selected, disabled]
• button (StateGroupId:305:2236) - 버튼 [primary, secondary, tertiary, disabled]
• button_text (StateGroupId:300:26102) - 텍스트 버튼 [default, hover, disabled]
• side_navigation (StateGroupId:393:29718) - 좌측 사이드 네비게이션
• footer__pc (SymbolId:340:30856) - 데스크톱 푸터
• footer__mo (StateGroupId:1548:41060) - 모바일 푸터
• logo/KRDS (SymbolId:344:44251) - KRDS 로고

[2.3 화면 유형별 기본 레이아웃: config/default-layouts.json]
• form (입력 폼): text_input, text_area, selectbox, checkbox, radio_button, button, side_navigation, footer__pc
• list (목록): button, selectbox, checkbox, text_input, side_navigation, footer__pc
• detail (상세 조회): button, button_text, side_navigation, footer__pc
• dashboard (대시보드): button, selectbox, side_navigation, footer__pc

[2.4 디자인 시스템 교체 시 수정 파일]
• config/design-system.json → 색상, 타이포그래피, 간격 등 토큰 값 교체
• config/component-catalog.json → 컴포넌트 목록, assetId, variant 교체
• config/default-layouts.json → 화면 유형별 기본 컴포넌트 세트 교체
• .env → FIGMA_FILE_KEY, LIBRARY_KEY 값 교체
3. 시스템 아키텍처
[3.1 전체 구조]

Client Layer
├── 웹 UI (React)
├── CLI (Claude Code)
└── Slack/Teams Bot (Webhook)
        │
        ▼
MCP Orchestration Layer ─ MCP Server (Node.js/TypeScript)
├── Request Router (요청 유형 분류)
├── Context Analyzer (도메인 분석)
├── Component Resolver (컴포넌트 매칭)
├── Design Generator (디자인 생성)
├── Code Converter (코드 변환)
└── Image Processor (이미지 분석)
        │
        ▼
External Services
├── Figma REST API v1/v2
├── Figma Plugin API
└── Claude API (Anthropic)

[3.2 데이터 흐름]

사용자 요청
  → [Request Router] 요청 유형 분류 (7가지)
  → [Context Analyzer] 도메인 분석, 화면 구성 도출
  → Figma REST API: 기존 파일/노드 정보 조회
  → [Component Resolver] design-system.json + component-catalog.json 참조, 컴포넌트 매칭 및 검증
  → [Design Generator] Claude API로 디자인 구조 생성
  → [Figma Writer] Plugin API 스크립트 생성 및 실행
  → Figma 캔버스에 결과 반영
4. 7가지 디자인 요청 방식
[4.1 텍스트 설명으로 요청]
MCP Tool: create_design_from_text
입력: prompt (화면 설명), platform
처리: Claude API로 도메인 분석 → 컴포넌트 자동 매칭 → 디자인 생성
Figma API: GET /files (컴포넌트 조회)
예시: "민원신청 화면을 만들어줘"

[4.2 기존 화면을 참조하여 요청]
MCP Tool: create_design_from_reference
입력: prompt, referenceNodeIds[]
처리: 참조 프레임 스타일 추출 → 새 화면에 스타일 적용
Figma API: GET /files/nodes (프레임 분석)
예시: 프레임 선택 → "이 스타일로 처리현황 화면 만들어줘"

[4.3 기존 화면을 수정 요청]
MCP Tool: modify_existing_design
입력: prompt, targetNodeIds[]
처리: 현재 상태 분석 → 수정 계획 수립 → 부분 적용
Figma API: GET /files/nodes (현재 상태)
예시: 프레임 선택 → "주소 입력 필드를 추가해줘"

[4.4 스크린샷/이미지를 참조하여 요청]
MCP Tool: create_design_from_image
입력: prompt, imageNodeIds[]
처리: Claude Vision으로 이미지 분석 → 구조 파악 → 컴포넌트로 재현
Figma API: GET /images (이미지 추출)
예시: 이미지 선택 → "이 화면을 KRDS 스타일로 재현해줘"

[4.5 여러 화면을 한번에 요청 (플로우 단위)]
MCP Tool: create_multi_screen_flow
입력: prompt, screens[{name, description}]
처리: 공통 스타일 설정 → 순차 생성 (화면 간 일관성 유지)
Figma API: GET /files (전체 구조)
예시: "민원 플로우 4개 화면을 만들어줘"

[4.6 컴포넌트를 지정하여 요청]
MCP Tool: create_design_with_components
입력: prompt, componentNames[]
처리: 지정된 컴포넌트 검증 → 해당 컴포넌트만 사용하여 생성
Figma API: GET /files (컴포넌트 검증)
예시: "text_input, button으로 회원가입 폼 만들어줘"

[4.7 플랫폼/반응형 변환 요청]
MCP Tool: convert_platform
입력: sourceNodeIds[], targetPlatform
처리: 소스 분석 → 플랫폼 규칙 적용 → 변환 생성
Figma API: GET /files/nodes (소스 분석)
예시: 데스크톱 화면 선택 → "모바일 버전으로 변환해줘"
5. MCP Server 구현
[5.1 요청 타입 정의 (types/request.ts)]
enum RequestType {
  TEXT_DESCRIPTION = "text_description"
  REFERENCE_STYLE = "reference_style"
  MODIFY_EXISTING = "modify_existing"
  IMAGE_REFERENCE = "image_reference"
  MULTI_SCREEN_FLOW = "multi_screen_flow"
  COMPONENT_SPECIFIED = "component_specified"
  PLATFORM_CONVERT = "platform_convert"
}

interface DesignRequest {
  type: RequestType
  prompt: string
  fileKey: string
  referenceNodeIds?: string[]
  editableNodeIds?: string[]
  imageNodeIds?: string[]
  targetPlatform?: "desktop" | "mobile" | "tablet"
  components?: string[]
  screens?: { name: string; description: string }[]
}

[5.2 요청 라우터 (router/request-router.ts)]
• classify(userMessage) → Claude API로 7가지 유형 분류
• route(request) → 유형별 Handler로 라우팅

[5.3 MCP Tool 정의 (mcp/server.ts)]
MCP Server: "design-system-figma-mcp" v1.0.0

Tool 1: create_design_from_text → 텍스트 설명 기반 생성
Tool 2: create_design_from_reference → 기존 화면 참조 생성
Tool 3: modify_existing_design → 기존 화면 수정
Tool 4: create_design_from_image → 이미지 참조 생성
Tool 5: create_multi_screen_flow → 멀티 스크린 플로우
Tool 6: create_design_with_components → 컴포넌트 지정 생성
Tool 7: convert_platform → 플랫폼 변환
6. 핵심 서비스 모듈
[6.1 Context Analyzer (컨텍스트 분석기)]
• analyzeForNewDesign(prompt, fileKey) → 도메인 분석, 화면 구성 도출
• analyzeDomain(prompt) → Claude API로 도메인/화면유형/필요 컴포넌트 분석
• deepAnalyze(fileKey, nodeId) → 프레임의 layout, colors, typography, spacing, components, dimensions 추출

[6.2 Component Resolver (컴포넌트 매칭기)]
• findRelevant(fileKey, required[]) → catalog + 로컬 컴포넌트 교차 매칭
• findByName(fileKey, name) → 이름으로 단일 컴포넌트 검색
• getDefaultComponentSet(screenType) → 화면 유형별 기본 컴포넌트 세트 반환
• getPlatformComponents(fileKey, platform) → 플랫폼별 컴포넌트 스왑 적용

[6.3 Style Extractor (스타일 추출기)]
• extract(analyses[]) → colors, typography, spacing, layout 통합 추출
• extractFromNodes(fileKey, nodeIds[]) → 여러 노드에서 공통 스타일 추출
  반환: { colors: {primary, secondary, background, text, accent}, typography: {headingFont, bodyFont, sizes, weights}, spacing: {unit, sectionGap, elementGap, padding}, layout: {maxWidth, columns, hasHeader, hasSidebar, hasFooter} }

[6.4 Figma Writer (파일 쓰기 모듈)]
• writeToFile(fileKey, design, pageId?) → Plugin API 스크립트 생성 및 실행
• applyModifications(fileKey, modifications[]) → 수정사항 스크립트 생성 및 실행
• buildPluginScript(design, pageId?) → 페이지/프레임/노드 생성 스크립트 빌드
7. Figma API 연동
[7.1 Figma REST API 클라이언트 (figma/figma-client.ts)]
Base URL: https://api.figma.com/v1
인증: X-Figma-Token 헤더

주요 메서드:
• getFile(fileKey) → 파일 전체 구조 조회
• getFileNodes(fileKey, nodeIds[]) → 특정 노드 상세 조회
• getLocalComponents(fileKey) → 로컬 컴포넌트 목록 조회
• searchComponents(fileKey, query) → 컴포넌트 검색
• exportNodeImage(fileKey, nodeId, format, scale) → 노드를 이미지로 내보내기
• getFileStyles(fileKey) → 파일 스타일 목록 조회
• getNodeTree(fileKey, nodeId) → 노드 트리 조회

[7.2 사용되는 Figma API 엔드포인트]
• GET /v1/files/:key → 파일 전체 구조 조회
• GET /v1/files/:key/nodes?ids= → 특정 노드 상세 조회
• GET /v1/images/:key?ids= → 노드를 이미지로 내보내기
• GET /v1/files/:key/styles → 파일 스타일 목록 조회
• GET /v1/files/:key/components → 퍼블리시된 컴포넌트 조회
8. 플랫폼 변환 규칙
[플랫폼별 규칙]
Desktop: maxWidth=1440, columns=12, sidebar=visible, spacingScale=1.0, fontScale=1.0
Mobile: maxWidth=390, columns=4, sidebar=bottom_tab, spacingScale=0.75, fontScale=0.875
Tablet: maxWidth=768, columns=8, sidebar=drawer, spacingScale=0.875, fontScale=0.9375

[컴포넌트 스왑 규칙]
• footer__pc: Desktop 유지 / Mobile → footer__mo / Tablet 유지
• footer__mo: Desktop → footer__pc / Mobile 유지 / Tablet → footer__pc
• side_navigation: Desktop 유지(좌측 고정) / Mobile → 하단 탭 바 / Tablet → 드로어 메뉴
9. 프로젝트 구조
design-system-figma-mcp/
├── package.json
├── tsconfig.json
├── .env                              # 환경변수 (API 키, 파일 키)
├── config/
│   ├── design-system.json            # ⚡ 디자인 시스템 토큰 (교체 가능)
│   ├── component-catalog.json        # ⚡ 컴포넌트 카탈로그 (교체 가능)
│   └── default-layouts.json          # ⚡ 화면 유형별 레이아웃 (교체 가능)
├── src/
│   ├── index.ts                      # MCP 서버 진입점
│   ├── mcp/
│   │   └── server.ts                 # MCP Tool 정의 (7개 Tool)
│   ├── figma/
│   │   ├── figma-client.ts           # Figma REST API 클라이언트
│   │   └── figma-writer.ts           # Plugin API 스크립트 생성
│   ├── services/
│   │   ├── context-analyzer.ts       # 요청 분석 및 컨텍스트 도출
│   │   ├── component-resolver.ts     # 컴포넌트 매칭 (catalog 참조)
│   │   ├── design-generator.ts       # 디자인 생성 (Claude API)
│   │   ├── style-extractor.ts        # 기존 디자인 스타일 추출
│   │   ├── image-analyzer.ts         # 이미지 분석 (Claude Vision)
│   │   └── code-converter.ts         # HTML/CSS/JS 코드 변환
│   ├── rules/
│   │   └── platform-rules.ts         # 플랫폼별 변환 규칙
│   ├── types/
│   │   ├── request.ts                # 요청 타입 정의
│   │   ├── figma.ts                  # Figma 노드 타입
│   │   └── design.ts                 # 디자인 결과 타입
│   └── router/
│       └── request-router.ts         # 요청 유형 분류 및 라우팅
└── tests/
    ├── router.test.ts
    ├── context-analyzer.test.ts
    └── component-resolver.test.ts

⚡ 표시된 파일이 디자인 시스템 교체 시 수정 대상입니다.
10. 환경 설정
[10.1 환경변수 (.env)]
FIGMA_ACCESS_TOKEN=figd_xxxxxxxxxxxxxxxx
ANTHROPIC_API_KEY=sk-ant-xxxxxxxxxxxxxxxx
DEFAULT_FILE_KEY=6fcm04dwSEH2IUizZfaZCj
DESIGN_SYSTEM_CONFIG=config/design-system.json
COMPONENT_CATALOG=config/component-catalog.json
DEFAULT_LAYOUTS=config/default-layouts.json

[10.2 Claude Desktop 설정 (claude_desktop_config.json)]
{
  "mcpServers": {
    "design-system-figma": {
      "command": "node",
      "args": ["./dist/index.js"],
      "env": {
        "FIGMA_ACCESS_TOKEN": "figd_xxxxxxxx",
        "ANTHROPIC_API_KEY": "sk-ant-xxxxxxxx",
        "DEFAULT_FILE_KEY": "6fcm04dwSEH2IUizZfaZCj"
      }
    }
  }
}

[10.3 Claude Code CLI 설정]
claude mcp add design-system-figma node ./dist/index.js --env FIGMA_ACCESS_TOKEN=figd_xxxx --env ANTHROPIC_API_KEY=sk-ant-xxxx
11. API 호출 흐름 / 부록
[11.1 요청 방식별 API 사용 매트릭스]
1. 텍스트 설명 → Figma: GET /files | Claude: 도메인 분석 + 디자인 생성 | Plugin: 노드 생성
2. 기존 참조 → Figma: GET /files/nodes | Claude: 스타일 매칭 + 생성 | Plugin: 노드 생성
3. 기존 수정 → Figma: GET /files/nodes | Claude: 수정 계획 수립 | Plugin: 노드 수정
4. 이미지 참조 → Figma: GET /images | Claude: Vision 분석 + 생성 | Plugin: 노드 생성
5. 멀티 스크린 → Figma: GET /files | Claude: 순차 생성 (일관성) | Plugin: 다중 노드 생성
6. 컴포넌트 지정 → Figma: GET /files | Claude: 제약 기반 생성 | Plugin: 인스턴스 배치
7. 플랫폼 변환 → Figma: GET /files/nodes | Claude: 변환 규칙 적용 | Plugin: 노드 생성

[부록 A: 디자인 시스템 교체 체크리스트]
☐ config/design-system.json — 새 디자인 시스템 토큰 입력
☐ config/component-catalog.json — 새 컴포넌트 목록 및 assetId 매핑
☐ config/default-layouts.json — 화면 유형별 기본 컴포넌트 세트 업데이트
☐ .env — DEFAULT_FILE_KEY 및 Figma 파일 키 업데이트
☐ 플랫폼 변환 규칙의 componentSwaps 업데이트
☐ 테스트 실행으로 컴포넌트 매칭 검증

[부록 B: 지원 화면 유형]
• form (입력 폼) → 헤더 + 사이드바 + 폼 컨텐츠 + 푸터
• list (목록) → 헤더 + 사이드바 + 검색/필터 + 테이블 + 푸터
• detail (상세 조회) → 헤더 + 사이드바 + 상세 컨텐츠 + 액션 바 + 푸터
• dashboard (대시보드) → 헤더 + 사이드바 + 카드/차트 그리드 + 푸터
