KRDS 디자인 시스템 — Spring AI MCP 기반 구현 가이드
버전: 1.0.0  |  작성일: 2026-07-30
기술 스택: Java 17 / Spring Boot 4.1.0-RC1 / Spring AI 2.0.0-RC1 / Thymeleaf / MySQL / Redis
목차
1. 아키텍처 개요
2. Spring AI MCP Tool 구현
3. Figma REST API 연동 서비스
4. KRDS 컴포넌트 레지스트리
5. Thymeleaf KRDS 컴포넌트 프래그먼트
6. AI 기반 화면 분석 및 코드 생성
7. RAG 기반 디자인 가이드라인 검색
8. Figma Plugin 연동
9. 보안 설정
10. 데이터베이스 스키마
11. 테스트 전략
12. 프로젝트 디렉토리 구조
13. 설정 파일 (application.yaml)
1. 아키텍처 개요
1.1 전체 시스템 구조
┌──────────────────────────────────────────────────────────────────┐
│                         Client Layer                             │
│  ┌─────────────────┐  ┌──────────────┐  ┌─────────────────────┐  │
│  │ Thymeleaf UI    │  │ Claude Code  │  │ Figma Plugin (TS)   │  │
│  │ (SSE 채팅)      │  │ (MCP Client) │  │ (3종)               │  │
│  └────────┬────────┘  └──────┬───────┘  └──────────┬──────────┘  │
└───────────┼──────────────────┼─────────────────────┼─────────────┘
            │                  │                     │
            ▼                  ▼                     ▼
┌──────────────────────────────────────────────────────────────────┐
│                Spring Boot 4.1.0-RC1 Application                 │
│                                                                  │
│  ┌─────────────┐  ┌──────────────────────────────────────────┐   │
│  │ Spring MVC   │  │ Spring AI MCP Server (WebMVC)           │   │
│  │ Controllers  │  │ /mcp/** (Streamable HTTP / JSON-RPC)     │   │
│  │ + Thymeleaf  │  │                                          │   │
│  └──────┬───────┘  │  ┌────────────────────────────────────┐  │   │
│         │          │  │ @Tool Methods (7개 디자인 요청)      │  │   │
│         │          │  │ → MethodToolCallbackProvider 등록    │  │   │
│         │          │  └────────────────────────────────────┘  │   │
│         │          └──────────────────────┬───────────────────┘   │
│         │                                 │                      │
│  ┌──────┴─────────────────────────────────┴──────────────────┐   │
│  │                    Service Layer                           │   │
│  │  ┌──────────────┐  ┌─────────────┐  ┌──────────────────┐  │   │
│  │  │ FigmaApi     │  │ KrdsComponent│  │ ScreenDesign    │  │   │
│  │  │ Service      │  │ Registry     │  │ Generator       │  │   │
│  │  └──────────────┘  └─────────────┘  └──────────────────┘  │   │
│  │  ┌──────────────┐  ┌─────────────┐  ┌──────────────────┐  │   │
│  │  │ CodeGen      │  │ RagDesign   │  │ ImageAnalysis   │  │   │
│  │  │ Service      │  │ Service     │  │ Service         │  │   │
│  │  └──────────────┘  └─────────────┘  └──────────────────┘  │   │
│  └────────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐   │
│  │                    Data Layer                              │   │
│  │  ┌──────────┐  ┌───────────────┐  ┌─────────────────────┐ │   │
│  │  │ MySQL    │  │ Redis         │  │ Figma REST API      │ │   │
│  │  │ (JDBC    │  │ (Vector Store │  │ (api.figma.com)     │ │   │
│  │  │ Template)│  │ + Chat Memory)│  │                     │ │   │
│  │  └──────────┘  └───────────────┘  └─────────────────────┘ │   │
│  └────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
1.2 데이터 흐름
사용자 디자인 요청 (MCP Client 또는 Thymeleaf UI)
    │
    ▼
[MCP Server /mcp/**] ─── JSON-RPC 수신 → @Tool 메서드 라우팅
    │
    ├── [FigmaApiService] ─── Figma REST API 조회 (노드, 컴포넌트, 이미지)
    ├── [KrdsComponentRegistry] ─── ComponentRegistry JSON 계약 기반 컴포넌트 매칭
    ├── [RagDesignService] ─── Redis Vector Store에서 KRDS 디자인 가이드라인 검색
    ├── [ChatModel] ─── gpt-4o-mini 또는 qwen3:8b로 화면 구성 분석
    ▼
[ScreenDesignGenerator] ─── FigmaScreenSpec JSON 생성
    │
    ├── [CodeGenService] ─── Thymeleaf + CSS 코드 생성 (FreeMarker 템플릿)
    ├── [Figma Plugin] ─── Published Component Instance 기반 생성
    │                       logicalNodeId 기반 MERGE/REPLACE
    ▼
결과 반환 (FigmaScreenSpec JSON + Thymeleaf 코드)
2. Spring AI MCP Tool 구현
2.1 MCP Tool 설정
// config/McpToolConfig.java
@Configuration
public class McpToolConfig {
    @Bean
    public MethodToolCallbackProvider designToolCallbackProvider(
            DesignToolService designToolService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(designToolService)
                .build();
    }
}
2.2 7개 디자인 요청 @Tool 메서드
// service/tool/DesignToolService.java
@Service
public class DesignToolService {

    private final FigmaApiService figmaApiService;
    private final KrdsComponentRegistry componentRegistry;
    private final ScreenDesignGenerator screenDesignGenerator;
    private final CodeGenService codeGenService;
    private final RagDesignService ragDesignService;
    private final ImageAnalysisService imageAnalysisService;
    private final FigmaSecretValidator secretValidator;

    // ── Tool 1: 텍스트 설명으로 디자인 생성 ──
    @Tool(description = "텍스트 설명을 기반으로 KRDS 스타일의 업무화면을 생성합니다")
    public DesignResult createDesignFromText(
            @ToolParam(description = "Figma 공유 비밀키") String sharedSecret,
            @ToolParam(description = "화면 설명") String prompt,
            @ToolParam(description = "Figma 파일 키") String fileKey,
            @ToolParam(description = "플랫폼 (desktop|mobile)") String platform) {
        secretValidator.validate(sharedSecret);
        List<Document> guidelines = ragDesignService.searchGuidelines(prompt);
        ScreenAnalysis analysis = screenDesignGenerator.analyzeScreen(prompt, guidelines);
        List<ComponentMapping> components = componentRegistry.resolveComponents(
                analysis.getScreenType(), analysis.getRequiredComponents());
        FigmaScreenSpec spec = screenDesignGenerator.generateSpec(analysis, components, platform);
        String thymeleafCode = codeGenService.generateThymeleaf(spec);
        return DesignResult.builder()
                .screenSpec(spec).thymeleafCode(thymeleafCode).components(components).build();
    }

    // ── Tool 2: 기존 화면 참조하여 생성 ──
    @Tool(description = "기존 Figma 화면의 스타일을 참조하여 새로운 화면을 생성합니다")
    public DesignResult createDesignFromReference(
            @ToolParam(description = "Figma 공유 비밀키") String sharedSecret,
            @ToolParam(description = "생성할 화면 설명") String prompt,
            @ToolParam(description = "Figma 파일 키") String fileKey,
            @ToolParam(description = "참조할 노드 ID 목록 (쉼표 구분)") String referenceNodeIds) {
        secretValidator.validate(sharedSecret);
        List<String> nodeIds = List.of(referenceNodeIds.split(","));
        List<FigmaNode> referenceNodes = figmaApiService.getNodes(fileKey, nodeIds);
        DesignStyle extractedStyle = screenDesignGenerator.extractStyle(referenceNodes);
        ScreenAnalysis analysis = screenDesignGenerator.analyzeScreen(prompt, extractedStyle);
        // ... 스타일 적용 후 Spec 및 Thymeleaf 코드 생성
    }

    // ── Tool 3: 기존 화면 수정 ──
    @Tool(description = "기존 Figma 화면을 부분적으로 수정합니다")
    public DesignResult modifyExistingDesign(...) {
        // logicalNodeId 기반 수정 계획 수립
        ModificationPlan plan = screenDesignGenerator.planModification(prompt, currentNodes);
        // MERGE/REPLACE 방식으로 변경사항 생성
        FigmaScreenSpec deltaSpec = screenDesignGenerator.generateDeltaSpec(plan);
    }

    // ── Tool 4: 이미지 참조하여 생성 ──
    @Tool(description = "스크린샷이나 이미지를 분석하여 KRDS 스타일로 재현합니다")
    public DesignResult createDesignFromImage(...) {
        // Vision 모델(gpt-4o-mini)로 이미지 분석 → KRDS 컴포넌트 매칭
    }

    // ── Tool 5: 멀티 스크린 플로우 생성 ──
    @Tool(description = "여러 화면을 한 번에 생성합니다 (플로우 단위)")
    public FlowDesignResult createMultiScreenFlow(...) {
        // 순차 생성 (이전 화면 참조하여 일관성 유지)
    }

    // ── Tool 6: 컴포넌트 지정 생성 ──
    @Tool(description = "특정 KRDS 컴포넌트를 사용하여 화면을 생성합니다")
    public DesignResult createDesignWithComponents(...) {
        // 지정된 컴포넌트 검증 후 화면 구성
    }

    // ── Tool 7: 플랫폼 변환 ──
    @Tool(description = "기존 화면을 다른 플랫폼 버전으로 변환합니다")
    public DesignResult convertPlatform(...) {
        // PlatformRules 기반 컴포넌트 스왑 (footer__pc ↔ footer__mo)
    }
}
2.3 반환 타입 정의
@Data @Builder
public class DesignResult {
    private FigmaScreenSpec screenSpec;
    private String thymeleafCode;
    private List<ComponentMapping> components;
    private DesignStyle referenceStyle;
    private ModificationPlan modificationPlan;
    private ImageAnalysisResult imageAnalysis;
    private PlatformRules platformRules;
    private String warning;
}

@Data @Builder
public class FlowDesignResult {
    private List<DesignResult> screens;
    private DesignStyle sharedStyle;
}
3. Figma REST API 연동 서비스
// service/figma/FigmaApiService.java
@Service
public class FigmaApiService {
    private final RestClient restClient;

    public FigmaApiService(RestClient.Builder builder, FigmaTokenProvider tokenProvider) {
        this.restClient = builder
                .baseUrl("https://api.figma.com/v1")
                .defaultHeader("X-Figma-Token", tokenProvider.getToken())
                .build();
    }

    public FigmaFile getFile(String fileKey) {
        return restClient.get().uri("/files/{fileKey}", fileKey).retrieve().body(FigmaFile.class);
    }

    public Map<String, FigmaNode> getNodes(String fileKey, List<String> nodeIds) {
        String ids = String.join(",", nodeIds);
        return restClient.get().uri("/files/{fileKey}/nodes?ids={ids}", fileKey, ids)
                .retrieve().body(FigmaNodesResponse.class).getNodes();
    }

    public List<byte[]> exportImages(String fileKey, List<String> nodeIds) {
        String ids = String.join(",", nodeIds);
        FigmaImagesResponse response = restClient.get()
                .uri("/images/{fileKey}?ids={ids}&format=png&scale=2", fileKey, ids)
                .retrieve().body(FigmaImagesResponse.class);
        return response.getImages().values().stream().map(this::downloadImage).toList();
    }
}

// security/FigmaTokenProvider.java — HMAC 토큰 인증
@Component
public class FigmaTokenProvider {
    @Value("${figma.api.access-token}") private String accessToken;
    public String getToken() { return accessToken; }

    public String generateHmacToken(String payload) {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(
                accessToken.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        return Base64.getEncoder().encodeToString(
                mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
4. KRDS 컴포넌트 레지스트리
4.1 ComponentRegistry JSON 계약
// src/main/resources/registry/krds-component-registry.json
{
  "designSystem": "KRDS",  "version": "1.0.0",
  "figmaFileKey": "6fcm04dwSEH2IUizZfaZCj",
  "components": {
    "text_input":  { "assetId": "StateGroupId:286:25890", "category": "input",
                     "thymeleafFragment": "fragments/text-input :: textInput",
                     "cssClass": "krds-input", "egovTag": "<form:input>" },
    "text_area":   { "assetId": "StateGroupId:343:43306", "category": "input",
                     "thymeleafFragment": "fragments/textarea :: textarea",
                     "cssClass": "krds-textarea", "egovTag": "<form:textarea>" },
    "selectbox":   { "assetId": "StateGroupId:288:26192", "category": "input",
                     "thymeleafFragment": "fragments/selectbox :: selectbox",
                     "cssClass": "krds-select", "egovTag": "<form:select>" },
    "checkbox":    { "assetId": "StateGroupId:309:25967", "category": "selection",
                     "thymeleafFragment": "fragments/checkbox :: checkbox",
                     "cssClass": "krds-checkbox", "egovTag": "<form:checkbox>" },
    "radio_button":{ "assetId": "StateGroupId:313:27198", "category": "selection",
                     "thymeleafFragment": "fragments/radio :: radio",
                     "cssClass": "krds-radio", "egovTag": "<form:radiobutton>" },
    "button":      { "assetId": "StateGroupId:305:2236", "category": "action",
                     "thymeleafFragment": "fragments/button :: button",
                     "cssClass": "krds-btn", "egovTag": "<input type='button'>" },
    "side_navigation": { "assetId": "StateGroupId:393:29718", "category": "navigation",
                     "thymeleafFragment": "layout/sidebar :: sidebar" },
    "footer__pc":  { "assetId": "SymbolId:340:30856", "category": "layout",
                     "thymeleafFragment": "layout/footer :: footer" },
    "footer__mo":  { "assetId": "StateGroupId:1548:41060", "category": "layout",
                     "thymeleafFragment": "layout/footer-mobile :: footerMobile" }
  },
  "screenTypes": {
    "form":      { "defaultComponents": ["text_input","text_area","selectbox","checkbox",
                   "radio_button","button","side_navigation","footer__pc"] },
    "list":      { "defaultComponents": ["button","selectbox","checkbox","text_input",
                   "side_navigation","footer__pc"] },
    "detail":    { "defaultComponents": ["button","side_navigation","footer__pc"] },
    "dashboard": { "defaultComponents": ["button","selectbox","side_navigation","footer__pc"] }
  },
  "platformRules": {
    "desktop": { "maxWidth": 1440, "componentSwaps": { "footer__mo": "footer__pc" } },
    "mobile":  { "maxWidth": 390,  "componentSwaps": { "footer__pc": "footer__mo" } },
    "tablet":  { "maxWidth": 768,  "componentSwaps": {} }
  }
}
5. Thymeleaf KRDS 컴포넌트 프래그먼트
5.1 기본 레이아웃 (layout/default.html)
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout">
<head>
  <link rel="stylesheet" th:href="@{/css/krds-tokens.css}">
  <link rel="stylesheet" th:href="@{/css/krds-components.css}">
  <link rel="stylesheet" th:href="@{/css/krds-layout.css}">
</head>
<body>
  <div class="krds-page">
    <div th:replace="~{layout/header :: header}"></div>
    <div class="krds-content-area">
      <div th:replace="~{layout/sidebar :: sidebar(activeMenu=${activeMenu})}"></div>
      <main class="krds-main">
        <div layout:fragment="content"></div>
      </main>
    </div>
    <div th:replace="~{layout/footer :: footer}"></div>
  </div>
</body>
</html>
5.2 컴포넌트 프래그먼트 예시
<!-- fragments/text-input.html -->
<th:block th:fragment="textInput(id, label, required, type, placeholder, value, error)">
  <div class="krds-form-group">
    <label th:for="${id}" class="krds-form-label"
           th:classappend="${required} ? 'krds-form-label--required'" th:text="${label}">Label</label>
    <input th:id="${id}" th:name="${id}" th:type="${type ?: 'text'}" class="krds-input"
           th:classappend="${error} ? 'krds-input--error'"
           th:placeholder="${placeholder}" th:value="${value}" th:required="${required}">
    <span th:if="${error}" class="krds-form-message krds-form-message--error" th:text="${error}"></span>
  </div>
</th:block>

<!-- fragments/button.html -->
<th:block th:fragment="button(text, variant, type, disabled)">
  <button th:text="${text}" th:type="${type ?: 'button'}" class="krds-btn"
          th:classappend="'krds-btn--' + ${variant}" th:disabled="${disabled}"></button>
</th:block>

<!-- fragments/selectbox.html -->
<th:block th:fragment="selectbox(id, label, required, options, selectedValue)">
  <div class="krds-form-group">
    <label th:for="${id}" class="krds-form-label" th:text="${label}">Label</label>
    <select th:id="${id}" th:name="${id}" class="krds-select" th:required="${required}">
      <option value="">선택하세요</option>
      <option th:each="opt : ${options}" th:value="${opt.value}"
              th:text="${opt.label}" th:selected="${opt.value == selectedValue}"></option>
    </select>
  </div>
</th:block>
6. AI 기반 화면 분석 및 코드 생성
6.1 ScreenDesignGenerator
@Service
public class ScreenDesignGenerator {
    private final ChatModel chatModel;   // gpt-4o-mini 또는 qwen3:8b

    public ScreenAnalysis analyzeScreen(String prompt, List<Document> guidelines) {
        String systemPrompt = """
            당신은 한국 공공서비스 업무 도메인 전문가입니다.
            KRDS 기반으로 화면 구성을 분석하세요.
            사용 가능한 컴포넌트: text_input, text_area, selectbox, checkbox,
            radio_button, button, side_navigation, footer__pc
            화면 유형: form, list, detail, dashboard 중 선택
            JSON 형식으로 반환: { screenType, title, sections[{title, fields[]}], actions[] }
            """;
        ChatResponse response = chatModel.call(new Prompt(List.of(
                new SystemMessage(systemPrompt), new UserMessage(prompt))));
        return objectMapper.readValue(response.getResult().getOutput().getText(), ScreenAnalysis.class);
    }

    public FigmaScreenSpec generateSpec(ScreenAnalysis analysis,
            List<ComponentMapping> components, String platform) {
        return FigmaScreenSpec.builder()
                .screenType(analysis.getScreenType())
                .title(analysis.getTitle())
                .platform(platform)
                .sections(/* 각 섹션의 필드에 logicalNodeId + assetId 매핑 */)
                .actions(analysis.getActions())
                .components(components)
                .build();
    }
}
6.2 CodeGenService (FreeMarker 기반)
@Service
public class CodeGenService {
    private final Configuration freeMarkerConfig;

    // FigmaScreenSpec → Thymeleaf HTML 코드 생성
    public String generateThymeleaf(FigmaScreenSpec spec) {
        Template template = freeMarkerConfig.getTemplate("thymeleaf-page.ftl");
        StringWriter writer = new StringWriter();
        template.process(Map.of("spec", spec), writer);
        return writer.toString();
    }

    // FigmaScreenSpec → Controller Java 코드 생성
    public String generateController(FigmaScreenSpec spec) { /* spring-controller.ftl 사용 */ }

    // FigmaScreenSpec → CSS 코드 생성
    public String generateCss(FigmaScreenSpec spec) { /* krds-page-css.ftl 사용 */ }
}
7. RAG 기반 디자인 가이드라인 검색
@Service
public class RagDesignService {
    private final VectorStore vectorStore;  // Redis Vector Store
    private final EmbeddingModel embeddingModel;  // ONNX ko-sroberta-multitask

    // KRDS 디자인 가이드라인 PDF 적재
    public void loadDesignGuidelines(Resource pdfResource) {
        PdfDocumentReader reader = new PdfDocumentReader(pdfResource);
        List<Document> documents = reader.read();
        documents.forEach(doc -> {
            doc.getMetadata().put("source", "krds-design-guidelines");
            doc.getMetadata().put("version", "1.0.0");
        });
        vectorStore.add(documents);
    }

    // 디자인 가이드라인 검색
    public List<Document> searchGuidelines(String query) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query).topK(5).similarityThreshold(0.7)
                .filterExpression("source == 'krds-design-guidelines'").build();
        return vectorStore.similaritySearch(searchRequest);
    }
}

# Redis Vector Store 설정 (application.yaml)
spring.ai.vectorstore.redis:
  index: krds-design-rag
  prefix: "krds:"
  initialize-schema: true
spring.ai.embedding.transformer.onnx:
  modelUri: classpath:models/ko-sroberta-multitask.onnx
8. Figma Plugin 연동
8.1 기존 3종 플러그인과의 연동
┌─────────────────────────────────────────────────────────────┐
│                  Figma Plugin 연동 구조                      │
│                                                             │
│  (1) figma-screen-spec-plugin                               │
│     - FigmaScreenSpec JSON을 읽어 Figma 캔버스에 화면 생성   │
│     - Published Component Instance 기반 생성                │
│     - logicalNodeId 기반 MERGE/REPLACE                      │
│                                                             │
│  (2) krds-design-system-author-plugin                       │
│     - DesignSystemProfile JSON 기반 디자인 시스템 관리       │
│     - ComponentRegistry JSON 읽기/쓰기                      │
│                                                             │
│  (3) jsp-to-figma-plugin                                    │
│     - .figpack 기반 JSP 웹 캡처 Import                      │
│     - 기존 화면 → Figma 변환                                │
│                                                             │
│  Spring Boot MCP Server                                     │
│     - @Tool 메서드 → FigmaScreenSpec JSON 생성              │
│     - /api/figma/spec POST → Plugin이 폴링                 │
│     - HMAC 토큰 인증                                        │
└─────────────────────────────────────────────────────────────┘
8.2 FigmaScreenSpec → Plugin 전달 API
@RestController
@RequestMapping("/api/figma")
public class FigmaSpecController {
    // MCP Tool이 생성한 Spec을 저장
    @PostMapping("/spec")
    public ResponseEntity<Map<String, String>> saveSpec(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestBody FigmaScreenSpec spec) {
        String specId = specRepository.save(spec);
        return ResponseEntity.ok(Map.of("specId", specId));
    }

    // Figma Plugin이 Spec을 조회 (HMAC 인증)
    @GetMapping("/spec/{specId}")
    public ResponseEntity<FigmaScreenSpec> getSpec(
            @PathVariable String specId,
            @RequestHeader("X-Figma-Token") String hmacToken) {
        secretValidator.validateHmac(hmacToken, specId);
        return specRepository.findById(specId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
8.3 FigmaScreenSpec JSON 계약 예시
{
  "$schema": "figma-screen-spec-v1",
  "screenType": "form",
  "title": "민원신청",
  "platform": "desktop",
  "layout": { "template": "layout/default", "maxWidth": 1440, "sidebarWidth": 240 },
  "breadcrumb": ["홈", "민원안내 및 신청", "민원신청"],
  "sections": [{
    "title": "1. 신청인 정보",
    "fields": [
      { "logicalNodeId": "field-text_input-name", "component": "text_input",
        "assetId": "StateGroupId:286:25890", "label": "이름",
        "required": true, "mergeStrategy": "MERGE" },
      { "logicalNodeId": "field-text_input-phone", "component": "text_input",
        "assetId": "StateGroupId:286:25890", "label": "연락처",
        "required": true, "type": "tel", "mergeStrategy": "MERGE" }
    ]
  }],
  "actions": [
    { "label": "취소", "variant": "secondary" },
    { "label": "신청하기", "variant": "primary", "type": "submit" }
  ]
}
9. 보안 설정
@Configuration @EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.ignoringRequestMatchers("/mcp/**", "/api/**"))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/mcp/**").permitAll()   // MCP는 Tool 내부에서 인증
                .requestMatchers("/api/**").authenticated()
                .requestMatchers("/css/**", "/js/**").permitAll()
                .anyRequest().permitAll())
            .addFilterBefore(new ApiKeyAuthFilter(apiKey), UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}

@Component
public class FigmaSecretValidator {
    @Value("${figma.shared-secret}") private String sharedSecret;
    @Value("${figma.allowed-file-keys}") private List<String> allowedFileKeys;

    public void validate(String providedSecret) {
        if (!sharedSecret.equals(providedSecret))
            throw new SecurityException("Invalid Figma shared secret");
    }
    public void validateFileKey(String fileKey) {
        if (!allowedFileKeys.contains(fileKey))
            throw new SecurityException("File key not in allowed list");
    }
}
10. 데이터베이스 스키마 (MySQL)
-- 디자인 요청 이력
CREATE TABLE design_request (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_type    VARCHAR(50) NOT NULL COMMENT 'text_description|reference_style|...',
    prompt          TEXT NOT NULL,
    file_key        VARCHAR(100) NOT NULL,
    platform        VARCHAR(20) DEFAULT 'desktop',
    screen_type     VARCHAR(20) COMMENT 'form|list|detail|dashboard',
    status          VARCHAR(20) DEFAULT 'pending',
    spec_json       JSON COMMENT 'FigmaScreenSpec JSON',
    thymeleaf_code  TEXT COMMENT '생성된 Thymeleaf 코드',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMP NULL,
    INDEX idx_file_key (file_key), INDEX idx_status (status)
);

-- KRDS 컴포넌트 사용 통계
CREATE TABLE component_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    component_name VARCHAR(100) NOT NULL,
    asset_id VARCHAR(100),
    variant_used VARCHAR(50),
    FOREIGN KEY (request_id) REFERENCES design_request(id)
);

-- 생성된 화면 Spec 저장 (Figma Plugin 조회용)
CREATE TABLE figma_screen_spec (
    id VARCHAR(36) PRIMARY KEY COMMENT 'UUID',
    file_key VARCHAR(100) NOT NULL,
    spec_json JSON NOT NULL,
    status VARCHAR(20) DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    applied_at TIMESTAMP NULL
);
11. 테스트 전략
// JSON 계약 테스트
@SpringBootTest
class ComponentRegistryContractTest {
    @Autowired private KrdsComponentRegistry registry;

    @Test void registryShouldHaveAllRequiredComponents() {
        List<String> required = List.of("text_input", "text_area", "selectbox",
                "checkbox", "radio_button", "button", "side_navigation", "footer__pc");
        for (String name : required) {
            ComponentResolution r = registry.resolveByNames("test", List.of(name));
            assertThat(r.getMissing()).isEmpty();
            assertThat(r.getResolved().get(0).getAssetId()).isNotNull();
        }
    }
}

// MCP Tool 통합 테스트
@SpringBootTest(webEnvironment = RANDOM_PORT)
class DesignToolIntegrationTest {
    @Test void createDesignFromTextShouldReturnValidSpec() {
        DesignResult result = designToolService.createDesignFromText(
                sharedSecret, "민원신청 화면을 만들어줘", "6fcm04dwSEH2IUizZfaZCj", "desktop");
        assertThat(result.getScreenSpec()).isNotNull();
        assertThat(result.getScreenSpec().getScreenType()).isEqualTo("form");
        assertThat(result.getThymeleafCode()).contains("th:replace");
    }

    @Test void invalidSharedSecretShouldThrow() {
        assertThrows(SecurityException.class, () ->
                designToolService.createDesignFromText("wrong", "test", "fk", "desktop"));
    }
}
12. 프로젝트 디렉토리 구조
src/main/
├── java/kr/go/krds/
│   ├── KrdsApplication.java
│   ├── config/
│   │   ├── McpToolConfig.java            ← MCP Tool 등록
│   │   ├── SecurityConfig.java           ← Spring Security
│   │   ├── RedisConfig.java              ← Redis Vector Store
│   │   └── WebConfig.java
│   ├── controller/
│   │   ├── PageController.java           ← Thymeleaf 페이지 라우팅
│   │   ├── FigmaSpecController.java      ← Figma Plugin Spec API
│   │   └── ChatController.java           ← SSE 채팅 응답
│   ├── service/
│   │   ├── tool/DesignToolService.java   ← 7개 @Tool 메서드
│   │   ├── figma/FigmaApiService.java    ← Figma REST API 클라이언트
│   │   ├── registry/KrdsComponentRegistry.java
│   │   ├── design/ScreenDesignGenerator.java
│   │   ├── codegen/CodeGenService.java   ← FreeMarker 기반 코드 생성
│   │   └── rag/RagDesignService.java     ← KRDS 가이드라인 RAG 검색
│   ├── domain/ (DesignResult, FigmaScreenSpec, ScreenAnalysis, ...)
│   ├── repository/ (JdbcTemplate 기반)
│   └── security/ (FigmaSecretValidator, ApiKeyAuthFilter)
├── resources/
│   ├── application.yaml
│   ├── registry/krds-component-registry.json  ← 디자인 시스템 교체 시 수정
│   ├── schema/figma-screen-spec-v1.json
│   ├── codegen-templates/ (thymeleaf-page.ftl, spring-controller.ftl, krds-page-css.ftl)
│   ├── static/css/ (krds-tokens.css, krds-components.css, krds-layout.css)
│   ├── templates/
│   │   ├── layout/ (default.html, header.html, sidebar.html, footer.html)
│   │   ├── fragments/ (text-input.html, button.html, selectbox.html, ...)
│   │   └── pages/
│   └── models/ (ko-sroberta-multitask.onnx)
13. 설정 파일 (application.yaml)
server:
  port: 8080

spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat.options.model: gpt-4o-mini
    ollama:
      base-url: http://localhost:11434
      chat.model: qwen3:8b
    mcp.server:
      name: krds-design-mcp
      version: 1.0.0
      type: SYNC
    vectorstore.redis:
      index: krds-design-rag
      prefix: "krds:"
      initialize-schema: true
    embedding.transformer.onnx:
      modelUri: classpath:models/ko-sroberta-multitask.onnx

  datasource:
    url: jdbc:mysql://localhost:3306/krds_design
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

  data.redis:
    host: localhost
    port: 6379

  thymeleaf:
    prefix: classpath:/templates/
    suffix: .html
    cache: false

figma:
  api:
    base-url: https://api.figma.com/v1
    access-token: ${FIGMA_ACCESS_TOKEN}
  shared-secret: ${FIGMA_SHARED_SECRET}
  allowed-file-keys:
    - 6fcm04dwSEH2IUizZfaZCj

krds:
  registry: classpath:registry/krds-component-registry.json
  design-system-version: 1.0.0
부록: Gradle 의존성
dependencies {
    implementation 'org.springframework.ai:spring-ai-starter-mcp-server-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
    implementation 'nz.net.ultraq.thymeleaf:thymeleaf-layout-dialect'
    implementation 'org.freemarker:freemarker:2.3.33'
    implementation 'com.networknt:json-schema-validator:1.5.6'
    implementation 'org.springframework.ai:spring-ai-redis-store-spring-boot-starter'
    implementation 'org.springframework.ai:spring-ai-transformers-spring-boot-starter'
    implementation 'com.mysql:mysql-connector-j'
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'
    implementation 'org.springframework.ai:spring-ai-pdf-document-reader'
    implementation 'org.apache.pdfbox:pdfbox:3.0.7'
    implementation 'org.springframework.boot:spring-boot-starter-security'
}
