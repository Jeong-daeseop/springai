# SecurityTemplateTool webXmlFilter 5.0 정책 검토

> 작성일: 2026-06-10
> 대상: `SecurityTemplateTool_webXmlFilter_5_0_정책.md`
> 검토 관점: 정책 타당성, 구현 위치 선택, 테스트 계획 보완, 누락 케이스

---

## 1. 검토 요약

문서의 분석과 결론에 동의한다.

| 항목 | 판정 |
|---|---|
| 문제 진단 | 정확 |
| 안 2 (5.0 지원 확장) 미권장 근거 | 타당 |
| 구현 위치: Renderer | 적절 |
| 권장 정책: 4.3 전용 제한 | 동의 |
| 테스트 계획 9-2 (Service 메시지) | 보완 필요 |
| Factory expand() 버전 체크 언급 | 누락 |

---

## 2. 문제 진단 평가

다음 연결 고리를 정확히 짚었다.

```
web.xml.fragment → targetBeanName=egovSpringSecurityLoginFilter
                → 5.0 context-security.xml에 해당 Bean 없음
                → NoSuchBeanDefinitionException
```

"겉보기로는 생성되지만 런타임에서 깨진다"는 위험을 명확히 표현한 점이 핵심이다.

---

## 3. 안 2 미권장 근거 평가

5.0은 `EgovSecurityConfiguration`이 `SecurityFilterChain`을 자동 구성하는 구조이다.
여기에 커스텀 필터 체인을 끼워 넣으려면 다음을 모두 검증해야 한다.

```text
1. egovSpringSecurityLoginFilter Bean 5.0 등록 방식
2. SecurityFilterChain 중복 또는 순서 충돌 여부
3. CSRF / loginProcessUrl / logoutUrl 동작
4. 5.0 통합 테스트
```

현재 단계에서 이 복잡도를 감수할 이유가 없다. 안 1이 맞다.

---

## 4. 구현 위치 — Renderer가 맞다

8-1에서 Renderer를 선택한 이유를 구체적으로 확인한다.

```
문자열 반환 경로: renderSingle() → renderer.render()
파일 저장 경로:  plan() → toPlan() → renderer.render()
```

두 경로가 모두 `renderer.render()`를 거친다.
Renderer에서 한 번만 막으면 양쪽 경로 모두 적용된다.

Factory에서 막으면 `renderSingle()` 경로에서 별도로 처리해야 하므로
누락 가능성이 생긴다. Renderer 단일 지점이 더 안전하다.

---

## 5. 테스트 계획 보완 — [필수]

### 5-1. 9-2 Service 테스트의 문제

문서의 Service 테스트 검증 내용이다.

```java
assertThat(result).contains("지원하지 않는 securityType");
assertThat(result).contains("webXmlFilter");
```

`IllegalArgumentException`을 Service가 잡아서 `unsupported()` 메시지로 감싸면,
Renderer에서 던진 **버전 불일치 안내 메시지가 사라진다.**

사용자 입장에서 "왜 안 되는지, 대신 뭘 써야 하는지"를 알 수 없다.

### 5-2. 권장 메시지 노출 방식

최소한 다음 내용이 사용자에게 전달되어야 한다.

```text
webXmlFilter는 eGovFrame 4.3 WAR XML Security 전용입니다.
5.0에서는 setup-war-50을 사용하세요.
```

구현 선택지는 두 가지다.

| 방식 | 설명 |
|---|---|
| Service가 예외 메시지를 그대로 반환 | `IllegalArgumentException.getMessage()`를 직접 반환 |
| Renderer 예외를 Service가 가공 없이 전달 | `unsupported()` 대신 버전 불일치 전용 처리 분기 |

`unsupported()` 메시지는 "지원하지 않는 securityType"이라는 일반 메시지이므로,
버전 불일치처럼 **이유가 명확한 경우**는 그 메시지를 그대로 노출하는 방식이 낫다.

### 5-3. 수정된 Service 테스트 제안

```java
@Test
void getSecurityTemplate_webXmlFilter_50_returnsVersionMismatchMessage() {
    String result = service.getSecurityTemplate(
        "webXmlFilter",
        "egovframework.let.emp",
        "5.0",
        null,
        "war"
    );

    assertThat(result).contains("4.3");
    assertThat(result).contains("setup-war-50");
}
```

---

## 6. 누락 케이스 — Factory expand() 버전 체크

문서에서 다루지 않은 케이스가 있다.

현재 `expand()`에서 단일 타입은 `default -> List.of(lower)` 경로로 빠진다.

```java
default -> List.of(lower);
```

`webxmlfilter`를 단일 타입으로 호출하면 Factory에서는 버전 검증 없이 통과하고,
Renderer에서만 막힌다.

Renderer가 막아주므로 결과적으로는 안전하지만,
설계상 의도를 코드에 명시하는 것이 좋다.

선택지:

| 방식 | 설명 |
|---|---|
| 현행 유지 | Renderer 단일 지점에서 막음 (현재 구조상 안전) |
| Factory에도 추가 | `toPlan()` 또는 `expand()` default 분기에서 버전 체크 |

Renderer가 단일 책임을 갖는 현행 구조가 더 단순하다.
단, 이 설계 의도를 Factory 주석 또는 Renderer 코드에 명시해두면 이후 유지보수에서 혼선을 줄일 수 있다.

---

## 7. 구현 전 결정 사항

정책 방향과 구현 위치는 확정됐다. 구현 전에 한 가지만 결정하면 된다.

> **Service가 `IllegalArgumentException`을 잡을 때 버전 불일치 메시지를 사용자에게 그대로 노출할 것인가?**

권장 답변: **예. 버전 불일치 메시지를 그대로 노출한다.**

이유:
- `unsupported()` 일반 메시지는 이유를 설명하지 않는다.
- 버전 불일치는 사용자가 정정할 수 있는 명확한 원인이다.
- Claude가 Tool 결과를 보고 올바른 대안(`setup-war-50`)으로 재호출할 수 있다.

---

## 8. 구현 순서 (결정 후)

```text
1. SecurityTemplateRenderer — webxmlfilter + 5.0 → IllegalArgumentException
2. SecurityTemplateRendererIntegrationTest — webXmlFilter_withEgov50_throwsUnsupported
3. SecurityTemplateService — 버전 불일치 메시지 노출 처리
4. SecurityTemplateServiceTest — getSecurityTemplate_webXmlFilter_50_returnsVersionMismatchMessage
5. SecurityTemplateTool.java description — webXmlFilter 4.3 전용 명시
```
