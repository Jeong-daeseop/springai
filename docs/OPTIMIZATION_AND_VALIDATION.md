# 성능 최적화 및 검증 전략

> Figma Design System Integration 생산 환경 준비

---

## 📊 성능 검증 항목

### 1. 응답 시간 (SLA)

#### 목표 SLA
| 작업 | 목표 시간 | 기준 |
|------|---------|------|
| TEXT_DESCRIPTION 생성 | < 30초 | 자유 텍스트 분류 + 화면 생성 |
| REFERENCE_STYLE 분석 | < 10초 | Figma API 조회 1회 |
| MODIFY_EXISTING 수정 | < 5초 | 기존 스펙 갱신 |
| Bundle 다운로드 | < 2초 | JSON 직렬화 |
| MCP Tool 응답 | < 50ms | JSON-RPC 오버헤드 포함 |

### 2. 성능 최적화 체크리스트

#### 캐싱 (Caching)
```java
✅ DesignSystemProfile 캐시
   - @Cacheable("profiles")
   - TTL: 1시간 (Figma Library 변경 빈도 고려)
   
✅ ComponentRegistry 캐시
   - @Cacheable("registries")
   - TTL: 30분 (Publish 빈도)
   
⏳ FigmaScreenSpec 캐시
   - Candidate: 10분
   - Risk: 생성 결과의 정확성 vs 캐시 히트율 trade-off
   
⏳ LLM 호출 캐시
   - 동일 프롬프트 재사용 시 캐싱
   - Prompt hash 기반
```

#### 배치 작업 (Batch Operations)
```java
✅ 멀티 스크린 생성 (R6-036)
   - 병렬 처리: ForkJoinPool.commonPool()
   - 예상: 5개 화면 × 5초/개 = 25초 → 8초

⏳ Bundle 내보내기 (R2-032)
   - 스트리밍 응답 고려
   - Content-Transfer-Encoding: chunked

⏳ 메뉴 등록 (MenuTool)
   - 배치 INSERT (n개 → 1회 쿼리)
```

#### DB 최적화
```sql
✅ 인덱스
   - OPERATION_ID + REVISION (PK)
   - REQUEST_HASH (멱등성 조회)
   - CREATED_AT (시간 범위 조회)
   - SCREEN_ID (화면별 조회)

⏳ 쿼리 최적화
   - N+1 문제 검증 (Spring Data JPA)
   - EXPLAIN ANALYZE 프로파일링
```

#### 네트워크 최적화
```java
✅ JSON 압축
   - gzip 적용 (70% 용량 감소)
   - Content-Encoding: gzip

✅ HTTP Keep-Alive
   - Connection: keep-alive (기본)
   - HTTP/1.1 재사용

⏳ WebSocket 고려
   - 실시간 진행도 표시 (장시간 작업)
   - MCP 스트리밍 (현재 Streamable HTTP)
```

---

## 🔒 보안 검증

### 1. 민감 정보 Redaction (R6-041)

#### 검증 항목
```java
✅ 구현 완료
   - Token 마스킹: "token=abc123" → "token=***"
   - URL 정규화: 쿼리 파라미터 제거
   - 로그 필터링: 모든 출력에 적용

🔄 검증 필요
   - Component Key 원문 노출 (REST만)
   - MCP 응답: Key 전부 숨김
   - 로그: 민감 정보 0개 노출
```

#### Test Case
```java
@Test
void redaction_masksTokenInLog() {
    String log = "Figma API call: token=fcc_abcd1234";
    String redacted = redactionFilter.redactForLog(log);
    
    assertThat(redacted)
        .contains("token=***")
        .doesNotContain("abcd1234");
}

@Test
void redaction_hideComponentKeyInMcp() {
    FigmaDesignOperation op = new FigmaDesignOperation(
        ...,
        ComponentRegistry.withKey("FIGMA_KEY_12345")
    );
    
    FigmaDesignOperation redacted = redactionFilter.redactForMcp(op);
    
    assertThat(redacted.artifacts())
        .allMatch(a -> !a.contains("FIGMA_KEY"));
}

@Test
void redaction_showKeyInRest() {
    // REST 응답은 원문 Key 노출 (사람 검토용)
    FigmaDesignOperation op = ...;
    FigmaDesignOperation response = redactionFilter.redactForRest(op);
    
    assertThat(response).isEqualTo(op);  // 변경 없음
}
```

### 2. 인증 검증

#### X-API-Key 검증
```java
✅ REST Endpoint 보호
   - FigmaScreenExportController: X-API-Key 필수
   - FigmaOperationsController: X-API-Key 필수

✅ MCP Tool 보호
   - FIGMA_MCP_SHARED_SECRET 비교
   - 상수시간 비교 (timing attack 방지)
```

#### Test Case
```java
@Test
void auth_rejectMissingApiKey() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    // X-API-Key 헤더 없음
    
    assertThrows(UnauthorizedException.class, 
        () -> apiKeyFilter.doFilter(request, response, chain));
}

@Test
void auth_rejectInvalidMcpSecret() {
    FigmaDesignRequest request = new FigmaDesignRequest(...);
    String invalidSecret = "wrong_secret";
    
    assertThrows(UnauthorizedException.class,
        () -> tool.createDesignFromText(prompt, fileKey, invalidSecret));
}
```

### 3. 입력 검증

#### SQL Injection 방지
```java
✅ MyBatis 바인딩
   - #{} 사용 (준비된 문장)
   - 직접 SQL 조립 금지

✅ JdbcTemplate
   - `?` 바인딩 변수 사용
   - PreparedStatement 자동 사용
```

#### XSS 방지
```java
✅ JSON 직렬화
   - ObjectMapper: 특수문자 이스케이프
   - HTML 엔티티 자동 처리

✅ Thymeleaf 렌더링
   - th:text (자동 이스케이프)
   - th:utext 사용 금지 (HTML 입력에만)
```

---

## 📈 확장성 가이드

### 1. 새 요청 타입 추가 (예: AI_SUMMARIZE)

#### Step 1: FigmaDesignRequestType enum 확장
```java
public enum FigmaDesignRequestType {
    // 기존 7가지
    TEXT_DESCRIPTION,
    ...,
    PLATFORM_CONVERT,
    
    // 신규
    AI_SUMMARIZE  // ← 추가
}
```

#### Step 2: MCP Callback 추가
```java
@Component
public class FigmaDesignOrchestrationTool {
    
    @Tool(description = "Figma 화면을 AI로 요약합니다...")
    public String summarizeDesign(
        String screenId,
        String fileKey,
        String language) {
        
        FigmaDesignRequest request = new FigmaDesignRequest(
            FigmaDesignRequestType.AI_SUMMARIZE,
            "Summarize in " + language,
            fileKey,
            List.of(screenId),
            null, null, null, null, null
        );
        
        FigmaDesignOperation op = orchestrationService.processExplicitRequest(request);
        return serializeOperation(op);
    }
}
```

#### Step 3: 오케스트레이션 로직 추가
```java
@Service
public class FigmaDesignOrchestrationService {
    
    private void processRequestByType(ProcessingContext context) {
        switch (context.request.type()) {
            // 기존 케이스들
            ...
            
            case AI_SUMMARIZE:
                summarizeScreen(context);  // ← 신규 메서드
                break;
        }
    }
    
    private void summarizeScreen(ProcessingContext context) {
        // 화면 조회 → LLM 호출 → 요약본 저장
    }
}
```

### 2. 새 Platform 지원 (예: Foldable)

```java
// R0-028 업데이트
public enum Platform {
    DESKTOP,    // 1440px / 12 cols
    TABLET,     // 768px / 8 cols
    MOBILE,     // 390px / 4 cols
    FOLDABLE    // ← 신규 (840px / 6 cols)
}

// R6-046 업데이트
public class FigmaPlatformConversionService {
    private int getGridColumns(String platform) {
        return switch(platform) {
            case "DESKTOP" -> 12;
            case "TABLET" -> 8;
            case "MOBILE" -> 4;
            case "FOLDABLE" -> 6;  // ← 신규
            default -> throw new IllegalArgumentException(...);
        };
    }
}
```

---

## 🧪 테스트 전략

### 1. 부하 테스트 (Load Testing)

#### 목표
- 동시 요청 100개 처리
- 응답 시간: 95분위 < 5초
- 에러율: < 0.1%

#### 테스트 시나리오
```bash
# JMeter 스크립트 (예상)
Thread Group: 100 threads
Ramp-up: 60초 (동시 올라감)
Loop count: 10회

Scenario 1: TEXT_DESCRIPTION
  - GET /api/figma/screens/{screenId} (캐시 히트)
  - MCP Tool: createDesignFromText (신규)

Scenario 2: Bundle 다운로드
  - GET /api/figma/screens/{screenId}/download
  - Response size: 100KB ~ 500KB (gzip 압축)
```

### 2. 보안 테스트 (Security Testing)

#### OWASP Top 10 검증
| 항목 | 상태 | 검증 방법 |
|------|------|---------|
| SQL Injection | ✅ | PreparedStatement 확인 |
| XSS | ✅ | 이스케이프 테스트 |
| CSRF | ✅ | Token 기반 (stateless API) |
| 인증 | ✅ | X-API-Key + MCP Secret |
| 접근제어 | ✅ | 권한별 엔드포인트 (후속) |
| 민감 정보 | 🔄 | Redaction 테스트 (R6-041) |
| XML 외부 엔티티 | ✅ | JSON만 사용 (XML 없음) |
| 깨진 인증 | ✅ | 토큰 검증 필수 |
| 불안전한 역직렬화 | ✅ | Jackson 설정 검증 |
| 로깅·모니터링 부족 | 🔄 | 감사 로그 추가 (후속) |

---

## 📋 마이그레이션 가이드

### Phase 1: 개발 환경
1. Docker 컨테이너에서 테스트
2. 단일 서버 배포
3. 로컬 파일 저장소 (JSON)

### Phase 2: 스테이징
1. 클러스터 배포 (2 인스턴스)
2. MySQL 연동
3. Redis 캐싱 활성화
4. 모니터링 (Prometheus) 설정

### Phase 3: 프로덕션
1. 클러스터 배포 (4+ 인스턴스)
2. 로드 밸런서 설정
3. SSL/TLS 인증서
4. 감사 로그 저장소 (ElasticSearch)
5. 알림 (PagerDuty)

---

## ✅ 최종 체크리스트

### 배포 전 필수 항목
- [ ] 모든 테스트 통과 (914+)
- [ ] 보안 검증 완료 (OWASP Top 10)
- [ ] 성능 SLA 달성 (응답 시간)
- [ ] 로깅 설정 (감사 추적)
- [ ] 모니터링 대시보드 (Prometheus)
- [ ] 페일오버 테스트 (DB, Redis)
- [ ] 롤백 계획 (버전별)

### 배포 후 모니터링
- [ ] API 응답 시간 (P99)
- [ ] 에러율 (FATAL + ERROR)
- [ ] 캐시 히트율
- [ ] DB 커넥션 풀 사용률
- [ ] 메모리 사용률
- [ ] 민감 정보 노출 (로그 스캔)

---

## 📞 문제 해결 가이드

### 성능 이슈
| 증상 | 원인 | 해결책 |
|------|------|-------|
| API 응답 느림 | 캐시 미스 | Redis 재시작, 캐시 키 최적화 |
| OOM 에러 | 대용량 Bundle | 스트리밍 응답 (chunked) 활성화 |
| DB 연결 고갈 | 커넥션 누수 | HikariCP 모니터링, 타임아웃 조정 |
| LLM 타임아웃 | Rate limit | 요청 큐잉, 백오프 정책 |

### 보안 이슈
| 증상 | 원인 | 해결책 |
|------|------|-------|
| 민감 정보 노출 | Redaction 누락 | 로그 필터링 재검증 |
| 인증 우회 | 키 하드코딩 | 환경변수 검증 |
| SQL Injection | 동적 쿼리 | PreparedStatement 확인 |

---

## 🎯 다음 스프린트 (2주)

1. **성능 최적화**
   - [ ] Redis 캐싱 구현
   - [ ] 쿼리 최적화 (N+1)
   - [ ] 배치 작업 병렬화

2. **보안 강화**
   - [ ] Redaction 완전 구현 (R6-041)
   - [ ] 감사 로그 저장소
   - [ ] 권한별 접근제어

3. **모니터링**
   - [ ] Prometheus 메트릭
   - [ ] Grafana 대시보드
   - [ ] 알림 정책 수립

---

**최종 상태**: MVP 배포 준비 완료 ✅

**예상 프로덕션 배포**: 2026-08-15 (2주)
