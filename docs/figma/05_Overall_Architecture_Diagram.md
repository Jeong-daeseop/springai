# springai 전체 구성 아키텍처 다이어그램

**문서명**: 05_Overall_Architecture_Diagram.md
**버전**: 1.3
**작성일**: 2026-08-17 (최초 작성 2026-07-21)
**상태**: 참고용 개요도 — WEB_CAPTURE 파이프라인 구현 완료 반영
**기준 문서**: `01_JSP_To_Figma_Design_Template_Guide.md`, `02_JSP_Website_Phased_Development_Impact_Assessment.md`, `03_Website_To_Figma_Implementation_Specification.md`, `04_Website_To_Figma_Implementation_List.md`

---

## 1. 목적

본 문서는 `01_JSP_To_Figma_Design_Template_Guide.md`가 제시한 "JSP → Figma" 원래 비전을, 이후 02~04번 문서에서 확정된 실제 구현 설계 및 springai의 기존 MCP 아키텍처와 함께 하나의 그림으로 정리한다. 01번 문서 자체는 여러 항목이 02~04번에서 대체·폐기되었으므로, 이 문서는 01번을 그대로 시각화한 것이 아니라 **01번의 목적(JSP 화면 → Figma 자동 변환)을 현재 확정된 아키텍처 위에 재배치한 것**이다.

> 참고: 이 문서는 01~04번 문서 계열(JSP → Figma "WEB_CAPTURE" 파이프라인)만 다룬다. `ScreenSpecification → FigmaScreenSpec → Bundle → Plugin` 기반의 Semantic Figma Generation Pipeline과 Manual Refinement(속성 단위 Patch 승인·재적용)는 별도 파이프라인이며, 08~17번 문서와
> `docs/architecture/Figma_Manual_Refinement_Mode_운영아키텍처.md`가 그 아키텍처를 다룬다.

`CaptureWebPageTool`/`DesignArtifactTool`/`jsp-design-extractor`/`jsp-to-figma-plugin`은 2026-07-21 최초 작성 시점에는 03/04번 문서의 제안(미구현) 상태였으나, 이후 모두 구현이 완료되어 MCP Tool로 등록됐다(`McpConfig`의 `captureWebPageTool`/`designArtifactTool` 빈). `jsp-design-extractor`는 WP8 Browser Validation Gate(1440/768/390 viewport, axe 접근성, visual diff)까지 포함해 동작하며, `jsp-to-figma-plugin`은 `.figpack` 검증과 Frame/Text 생성을 수행한다. 아래 다이어그램은 이 상태를 반영해 갱신했다.

---

## 2. 전체 구성도

Mermaid 렌더링을 지원하지 않는 뷰어를 위한 정적 미리보기(`mermaid-cli`로 생성한 SVG 스냅샷)이다. 다이어그램 자체를 수정할 때는 아래 Mermaid 소스를 편집하고 `05_Overall_Architecture_Diagram.svg`를 다시 생성해야 한다.

> ⚠️ 2026-08-17 갱신에서는 이 작업 환경에 `mermaid-cli`(`mmdc`)가 설치되어 있지 않아 아래 Mermaid
> 소스만 갱신하고 `05_Overall_Architecture_Diagram.svg`는 아직 재생성하지 못했다. 따라서 현재
> SVG 이미지는 WEB_CAPTURE 구성 요소를 여전히 "제안(미구현)"으로 표시하는 **이전 버전**이다.
> `npx @mermaid-js/mermaid-cli -i 05_Overall_Architecture_Diagram.md -o 05_Overall_Architecture_Diagram.svg`
> (또는 동등한 명령)로 재생성해 이 경고를 제거해야 한다. Mermaid 소스 자체는 최신 상태다.

![springai 전체 구성 아키텍처 다이어그램](./05_Overall_Architecture_Diagram.svg)

```mermaid
flowchart TB
    CD["Claude Desktop\n(MCP Client)"]

    subgraph SpringAI["springai — Spring Boot 4.x MCP Server (port 8080)"]
        direction TB

        subgraph ToolLayer["MCP Tool 계층"]
            T1["CRUD 생성 Tool군\nSchemaReaderTool / CrudPromptBuilderTool\nCodeTemplateTool / CodeSaverTool 등"]
            T2["DesignReferenceTool\nFILE·FIGMA 분석 (기존 구현)"]
            T3["CaptureWebPageTool\nDesignArtifactTool\nWEB_CAPTURE (구현 완료)"]
        end

        subgraph ServiceLayer["Service 계층"]
            S1["CrudOrchestrationService\nBoardOrchestrationService"]
            S2["DesignReferenceAnalysisService\ncheckExecutionContract / saveOrGet"]
            S3["WebCaptureOrchestrationService\nWebCaptureUrlValidator / WebCaptureDeploymentGuard"]
            S4["WebCaptureProjectionPolicy\n→ SafeDesignProjection"]
        end

        subgraph ConvergeLayer["공통 수렴 모델 (기존 + 확장)"]
            M1["DesignAnalysisResult\nsourceType: FILE·FIGMA·WEB_CAPTURE"]
            M2["UiDesignSpec"]
            M3["ScreenSpecification"]
        end

        subgraph CodeGen["기존 코드 생성 파이프라인"]
            G1["CrudModelFactory / BoardModelFactory"]
            G2["CodeTemplateTool (FreeMarker)\n→ CodeSaverTool"]
        end
    end

    subgraph Extractor["jsp-design-extractor — Node.js/TS/Playwright\n(별도 프로세스, 구현 완료)"]
        direction TB
        E1["Playwright 브라우저 렌더링\n(01번 STEP1~2에 대응)"]
        E2["DOM·computed CSS·좌표 수집\n(01번 STEP3~4에 대응)"]
        E3["Layout Analyzer / Component Recognizer\n(01번 STEP5~7에 대응)"]
        E4["RenderedDesignDocument 생성\n(01번 'Design JSON'을 대체)"]
        E1 --> E2 --> E3 --> E4
    end

    subgraph Plugin["jsp-to-figma-plugin — Figma Plugin API\n(별도 프로젝트, 구현 완료)"]
        direction TB
        P1[".figpack 검증\n(schema/hash/manifest)"]
        P2["Frame · Auto Layout · Component 생성\n(01번 STEP9~10에 대응)"]
        P1 --> P2
    end

    subgraph External["외부 시스템"]
        DB[("egov-mysql\nLETTNEMPLYRINFO 등")]
        Redis[("Redis\nRAG 벡터 스토어 + 채팅 메모리")]
        LLM["Ollama(qwen3) / OpenAI\nVision LLM — FILE/FIGMA 분석용"]
        FigmaAPI["Figma REST API\n(기존, DesignReferenceTool 전용)"]
        FigmaApp["Figma Desktop App"]
        JSPApp["Tomcat 실행 중인 JSP/eGovFrame 화면"]
    end

    Output[["생성된 JSP/Thymeleaf 소스 코드"]]

    CD -->|"JSON-RPC / Streamable HTTP"| ToolLayer
    T1 --> S1
    T2 --> S2
    T3 --> S3

    S1 --> DB
    S1 --> Redis
    S2 --> LLM
    S2 --> FigmaAPI
    S2 --> M1

    S3 -->|"loopback HTTP\nX-Extractor-Key"| E1
    JSPApp --> E1
    E4 -->|".figpack"| S3
    E4 -->|".figpack (로컬 파일)"| P1
    S3 --> S4
    S4 --> M1

    M1 --> M2 --> M3
    M3 --> G1 --> G2 --> Output

    P2 --> FigmaApp

    classDef existing fill:#1f6feb22,stroke:#1f6feb,color:#1f6feb;
    classDef proposed fill:#d2992222,stroke:#d29922,stroke-dasharray: 4 3,color:#9a6700;
    classDef external fill:#8b949e22,stroke:#8b949e,color:#57606a;

    class T1,S1,G1,G2,T2,S2,T3,S3,S4,Extractor,Plugin,E1,E2,E3,E4,P1,P2 existing;
    class DB,Redis,LLM,FigmaAPI,FigmaApp,JSPApp external;
```

**범례**

- 🔵 실선 파랑: 이미 구현된 컴포넌트 (CRUD 생성 파이프라인, `DesignReferenceTool`의 FILE/FIGMA 분석 경로, `WEB_CAPTURE` 파이프라인 전체)
- ⚪ 회색: springai 외부 시스템

2026-08-17 기준 01~04번 문서 범위의 컴포넌트는 모두 구현이 완료되어 "제안(미구현)"으로 표시할
대상이 더 이상 없다. 점선 주황 클래스(`proposed`)는 향후 새로운 미구현 제안이 추가될 때
재사용할 수 있도록 Mermaid `classDef` 정의 자체는 남겨뒀다.

---

## 3. 01번 문서 대비 무엇이 바뀌었는가

01번 문서의 원래 10단계 파이프라인(JSP 실행 → Playwright → DOM/CSS 분석 → Layout 분석 → Component 분석 → Pattern 분석 → Design JSON → Figma Plugin → Design Template 생성)은 **개념적으로는 위 다이어그램에 그대로 살아있다.** 다만 02~04번 문서의 검토 과정에서 다음과 같이 구체화·수정되었다.

| 01번 원안 | 현재 확정 설계 | 사유 |
|---|---|---|
| `RenderJspTool` 등 springai 안에 Playwright 직접 내장 | `jsp-design-extractor`를 별도 Node.js/TS 프로세스로 분리, springai는 loopback HTTP로만 호출 | springai는 Java/Spring 기반이라 Chromium을 직접 구동하지 않는 편이 배포·격리에 유리 (02번 §5.1, 03번 원칙 #2~3) |
| Design JSON | `RenderedDesignDocument` (schema `rendered-design-document-v1`) | 좌표·스타일·자산까지 포함하는 정밀 모델과, 코드 생성용 의미 모델(`UiDesignSpec`)을 분리 (02번 §8.1) |
| GPT/Claude/Gemini 멀티 AI 스택으로 Design JSON 생성 | Release 1은 결정론적 규칙 기반, LLM을 필수 경로에서 제외 | 동일 입력의 재현성·버전 고정이 필요하기 때문 (03번 원칙 #7) |
| Figma 출력 후 React 코드까지 자동 생성 | React 코드 생성 제외, 기존 JSP/Thymeleaf 코드 생성 파이프라인(`CrudModelFactory` 등)과 연결 | springai가 이미 보유한 eGovFrame 코드 생성 자산을 재사용하는 것이 더 현실적 (03번 §4.2 제외 항목) |
| Figma Plugin이 무엇으로 만들어지는지 불명확 | 전용 `jsp-to-figma-plugin` TypeScript 프로젝트로 명시, Figma MCP(`use_figma`)는 개발 보조용으로만 사용하고 제품 실행 경로에서 제외 | 무인 재현성·결정론 확보 (03번 §4.3) |
| 보안/개인정보 처리 언급 없음 | URL allowlist, loopback 강제, `WebCaptureProjectionPolicy`를 통한 PII 구조적 배제 | 로컬 개발 도구가 아니라 실제 배포 가능한 기능으로 설계되었기 때문 (03번 §11) |

---

## 4. 문서 변경 이력

| 버전 | 작성일 | 변경 내용 |
|---|---|---|
| 1.3 | 2026-08-17 | WEB_CAPTURE 파이프라인(`CaptureWebPageTool`/`DesignArtifactTool`/`jsp-design-extractor`/`jsp-to-figma-plugin`) 구현 완료 반영 — Mermaid 소스에서 `proposed` 클래스 제거, §1에 Semantic Figma Generation Pipeline과의 범위 구분 안내 추가. SVG는 `mmdc` 부재로 미재생성(§2 경고 참고) |
| 1.2 | 2026-07-21 | `05_Overall_Architecture_Diagram.svg` 정적 미리보기 첨부 및 §2에 이미지 링크 추가 |
| 1.1 | 2026-07-21 | `mermaid-cli` 렌더링 검증 완료(exit code 0, SVG 정상 생성) 확인 |
| 1.0 | 2026-07-21 | 01~04번 문서 통합 개요도 최초 작성 |
