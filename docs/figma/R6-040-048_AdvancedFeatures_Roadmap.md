# R6-040~048: 고급 기능 로드맵

> Figma API 확장, LLM 통합, Redaction, Platform 변환, 테스트

> ⚠️ 이 문서는 2026-08-02 작성된 계획 문서다. 각 항목의 **실제 최신 진행 상태**는
> `12_Semantic_Figma_Design_System_Implementation_List.md` §11.4(R6-032~048)를 1차 근거로
> 삼는다. 이 문서의 "✅"/"🔄" 표기는 계획 수립 시점의 것으로, 이후 12번 문서에서 개별 항목이
> `[~]`(부분 구현)/`[ ]`(미착수)로 갱신됐다.

---

## 개요

R6-032~038은 기존 코드 재사용 기반 설계는 확정됐으나 구현은 부분 완료(`[~]`) 상태이고,
R6-039(callback 등록·보안 검증)만 완료(`[x]`)됐다(12번 문서 §11.4 기준, 2026-07-30 갱신).
이제 다음 단계로 **고급 기능**을 단계적으로 구현한다.

---

## R6-040: Figma REST API 확장

### 목표
기존 단일 Node 조회를 확장하여 복합 쿼리 지원

### 구현 항목

#### 1. Pagination 지원
```java
public record FigmaApiQuery(
    String fileKey,
    String nodeId,
    int page,           // pagination
    int pageSize,       // default: 50
    boolean includeChildren
) {}

// 사용
List<FigmaNode> nodes = figmaApiClient.queryNodesPaginated(query);
```

#### 2. Styles 조회
```java
public FigmaStylesResponse queryStyles(String fileKey) {
    return call(GET, "/v1/files/{fileKey}/styles");
}
```

#### 3. Components 조회
```java
public FigmaComponentsResponse queryComponents(String fileKey) {
    return call(GET, "/v1/files/{fileKey}/components");
}
```

#### 4. Retry/Backoff 정책
```java
public record RetryPolicy(
    int maxRetries,           // default: 3
    long backoffMs,           // default: 1000
    int retryOnStatusCode     // 429 (rate limit)
) {}
```

### 상태: 🔄 구현 계획 (후속 스프린트)

---

## R6-041: Redaction 정책 (민감 정보 보호)

### 목표
MCP 응답 및 로그에서 민감한 Figma 정보 숨기기

### 민감 정보 분류

| 정보 | 노출 대상 | 처리 방식 |
|------|---------|---------|
| **Component Key** | REST만 | MCP/Log: 숨김 |
| **Variable Key** | REST만 | MCP/Log: 숨김 |
| **Style Key** | REST만 | MCP/Log: 숨김 |
| **File Key** | 제한적 | REST: 조회, MCP: 제외 |
| **Access Token** | 절대 금지 | 모든 출력에서 제거 |
| **Node ID** | 조건부 | 디버그 로그 제외 |

### 구현 전략
```java
@Component
public class RedactionFilter {
    
    // MCP 응답 전용 redaction
    public FigmaDesignOperation redactForMcp(FigmaDesignOperation op) {
        return new FigmaDesignOperation(
            op.operationId(),
            op.revision(),
            op.request(),
            redactRequestKeys(op.request()),  // Component/Variable Key 제거
            op.status(),
            ...
        );
    }
    
    // REST 응답: Key 원문 유지 (사람 검토용)
    public FigmaDesignOperation redactForRest(FigmaDesignOperation op) {
        return op;  // 그대로 반환
    }
    
    // 로그 필터: Token/URL 제거
    public String redactForLog(String text) {
        return text.replaceAll("token=.*", "token=***")
                   .replaceAll("key=.*", "key=***");
    }
}
```

### 상태: 🔄 설계 완료, 구현 예정

---

## R6-042: FigmaContextAnalyzer (LLM 통합)

### 목표
Figma 화면 분석을 LLM 구조화 출력으로 자동화

### 입력
```java
public record FigmaContextRequest(
    FigmaScreenSpec screenSpec,
    DesignSystemProfile profile,
    String additionalPrompt
) {}
```

### 출력
```java
public record FigmaContextAnalysis(
    String domain,              // "user", "order", "payment"
    FigmaScreenType screenType, // LIST, FORM, DETAIL
    LayoutPattern layoutPattern,
    List<String> requiredComponents,  // krds.button, krds.textField, ...
    double uncertainty          // 0.0 ~ 1.0
) {}
```

### Spring AI 구조화 출력
```java
@Service
public class FigmaContextAnalyzer {
    
    public FigmaContextAnalysis analyze(FigmaContextRequest request) {
        Prompt prompt = new PromptTemplate("""
            Analyze this Figma screen and identify:
            1. Domain (user, order, product, ...)
            2. Screen type (LIST, FORM, DETAIL)
            3. Layout pattern
            4. Required components
            5. Uncertainty (0-1)
            
            Screen: {screenName}
            Components: {componentNames}
            """).create(Map.of(
            "screenName", request.screenSpec().name(),
            "componentNames", String.join(", ", 
                request.screenSpec().content().children().stream()
                    .map(node -> node.type())
                    .toList())
        ));
        
        return chatModel.call(prompt, FigmaContextAnalysis.class);
    }
}
```

### 상태: 🔄 설계 완료, 구현 예정

---

## R6-043: FigmaStyleExtractor (토큰 추출)

### 목표
Design System Profile에 영향을 주지 않으면서 Style 후보 추출

### 기능
```java
@Service
public class FigmaStyleExtractor {
    
    // 공통 Color 후보 추출
    public Map<String, Set<String>> extractColors(FigmaScreenSpec spec) {
        return spec.content().children().stream()
            .filter(node -> node.properties().containsKey("fillColor"))
            .collect(Collectors.groupingBy(
                node -> node.properties().get("fillColor"),
                Collectors.mapping(FigmaNodeSpec::logicalNodeId, Collectors.toSet())
            ));
    }
    
    // 공통 Typography 후보
    public Map<String, Set<String>> extractTypography(FigmaScreenSpec spec) {
        // fontSize, fontWeight, lineHeight 그룹화
    }
    
    // 공통 Spacing 후보 추출
    public Map<String, Set<Integer>> extractSpacing(FigmaScreenSpec spec) {
        // gap, padding, margin 값 수집
    }
}
```

### 상태: 🔄 설계 완료, 구현 예정

---

## R6-044~048: 기타 확장 및 통합

### R6-044: ComponentRegistryResolver 확장
- ✅ 재사용 기반이 되는 기존 `ComponentRegistryResolver`는 구현 완료
- 🔄 확장 대상(Allowlist 제약, 승인 상태 검증)은 미구현 — 12번 문서 R6-044 `[~]`

### R6-045: DesignReferenceAnalysisService 확장
- ✅ 재사용 기반이 되는 기존 PNG/JPEG/PDF Vision 분석은 구현 완료
- 🔄 확장 대상(Figma 이미지 export 분석, 불확실성 반환)은 미구현 — 12번 문서 R6-045 `[~]`

### R6-046: FigmaPlatformConversionService
```java
@Service
public class FigmaPlatformConversionService {
    
    public FigmaScreenSpec convertToPlatform(
        FigmaScreenSpec sourceSpec,
        String targetPlatform) {
        
        // Desktop 1440/12 → Mobile 390/4
        // Tablet 768/8
        // Component Swap 적용
        
        return sourceSpec.withNextRevision(
            applySizePolicy(sourceSpec, targetPlatform),
            applyComponentSwaps(sourceSpec, targetPlatform),
            applyNavigationPattern(sourceSpec, targetPlatform)
        );
    }
}
```

### R6-047: Tool 응답 형식화
- operationId, artifactId 포함
- PREVIEW_READY / APPLY_REQUIRED 상태 분리
- APPLIED는 Plugin 보고 후에만

### R6-048: Transport-neutral Facade
```java
@Service
public class FigmaDesignFacade {
    
    // REST/MCP 공용 interface
    public FigmaDesignOperationResponse process(
        FigmaDesignRequest request,
        TransportType transport) {
        
        FigmaDesignOperation op = orchestrationService.process(request);
        
        return switch(transport) {
            case REST -> redaction.redactForRest(op);
            case MCP -> redaction.redactForMcp(op);
        };
    }
}
```

### 상태: 🔄 설계 완료, 구현 예정

---

## 구현 우선순위 및 일정

| ID | 기능 | 난이도 | 기대 시간 | 상태 |
|----|------|--------|----------|------|
| R6-040 | Figma API 확장 | 중 | 4시간 | 🔄 예정 |
| R6-041 | Redaction | 중 | 3시간 | 🔄 예정 |
| R6-042 | ContextAnalyzer | 중 | 4시간 | 🔄 예정 |
| R6-043 | StyleExtractor | 하 | 2시간 | 🔄 예정 |
| R6-044 | Resolver 확장 | 하 | 1시간 | 🔄 예정 |
| R6-045 | Analysis 확장 | 중 | 2시간 | 🔄 예정 |
| R6-046 | Platform변환 | 상 | 6시간 | 🔄 예정 |
| R6-047 | 응답형식화 | 하 | 1시간 | 🔄 예정 |
| R6-048 | Facade | 중 | 3시간 | 🔄 예정 |

**총 예상**: 약 26시간 (여러 팀 병렬 가능)

---

## v1 완성 범위 (MVP)

### 필수 (P0)
- ✅ R6-032~039: 기본 7가지 Callback + MCP 등록
- 🔄 R6-040: Figma API 기본 확장
- 🔄 R6-041: Redaction (Token/Key만)
- 🔄 R6-042: ContextAnalyzer (기본)

### 선택 (P1)
- 🔄 R6-043~045: 추출기 확장
- 🔄 R6-046: Platform 변환
- 🔄 R6-047~048: 통합 facade

---

## 외부 의존성

- **Spring AI**: ChatModel (LLM 호출)
- **Figma REST API**: Pagination, Styles, Components
- **Design System Profile**: Token/Variable definitions

---

## 다음 단계

1. 🔄 R0-027~029 (설계·계약 정의는 확정, `DefaultLayoutPolicy` 등 코드 구현은 R1-015로 후속 — 12번 문서 R0-027~029 `[~]`, R1-015 `[ ]`)
2. 🔄 R6-040~048 (이번 스프린트: 우선순위별 진행)
3. 📋 최종 정리 및 문서화
4. 📋 추가 검증 (성능, 보안) — 아직 미착수

---

**마지막 업데이트**: 2026-08-02 (최초 작성). 2026-08-17: 상태 표기를 12번 문서 §11.4 실제
진행 상태에 맞춰 정정(상단 안내 참고), 오타 수정.
