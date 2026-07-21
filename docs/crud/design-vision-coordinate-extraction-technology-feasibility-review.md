# 컴포넌트 좌표(bounding box) 추출 — 신규 기술 도입 가능성 조사 문서

> **작성일:** 2026-07-18
> **성격:** 순수 기술 조사 문서. 구현 여부·착수 여부는 결정되지 않았다. 코드는 수정하지 않았다(CLAUDE.md 원칙에 따름).
> **위치:** 이 문서는 `docs/crud/design-vision-component-layout-position-impact-and-changes.md`(이산 버킷 방식, 착수 대상)의 **대체안이 아니라 완전히 별개의 조사**다. "좌표 방식은 지금 이 프로젝트 구조 안에서는 답이 없다"는 이전 결론이, "좌표 방식 자체가 세상에 존재하지 않는다"는 뜻은 아니므로, 별도로 "그럼 좌표 방식은 기술적으로 뭐가 있고 뭘 요구하는가"를 조사해달라는 요청에 따라 작성했다.

---

## 0. 조사 배경

이전 대화에서 다음과 같이 정리됐다:
- 지금 이 프로젝트(`DesignReferenceTool`)가 쓰는 방식 = 채팅형 비전 모델(OpenAI `gpt-4o-mini`/Ollama)에게 이미지를 보여주고 구조화된 JSON(`UiDesignSpec`)으로 답하게 하는 방식(`.entity(UiDesignSpec.class)`)
- 이 방식 **안에서는** 좌표 추출이 신뢰할 수 없어서, 이산 버킷(density/formColumnLayout 등) 방식을 채택함
- 사용자가 "그럼 좌표 방식은 원천적으로 불가능한 거냐"고 재질문 → "아니다, 지금 이 프로젝트 구조 안에서만 답이 없는 것"이라고 답변
- 이번 요청: "일단 구현할지는 다음 문제이고, 좌표 방식 자체를 별도의 신규 기술 도입 검토로 조사해달라"

이 문서는 그 조사 결과다.

---

## 1. 왜 지금 방식(구조화 출력 채팅형 비전 모델)으로는 안 되는지 — 외부 근거로 재확인

기존 대화에서 추론으로만 설명했던 내용을, 실제 검색으로 재확인했다.

- GPT-4o에 (x,y) 좌표 추출을 200회 시도한 결과, **정확한 좌표는 5회뿐이었고 bounding box는 단 한 번도 정확하지 않았다.**
- 원인은 구조적이다: GPT-4o는 고해상도 이미지를 512×512 픽셀 타일로 쪼개 처리하는데, 이 정보가 트랜스포머 레이어를 거치며 세밀한 위치 정보가 손실된다. **정밀한 위치 추정은 애초에 이 모델의 사전학습 목표가 아니다.**
- UI 요소를 대상으로 한 grounding에서는 심지어 최신 MLLM도 위치 관련 설명에서 hallucination을 일으키는 경향이 보고됐다.

→ "지금 이 프로젝트 구조 안에서 좌표 방식이 답이 없다"는 이전 결론은 추측이 아니라 업계에서 실측으로 확인된 현상이다.

---

## 2. 대안 기술 옵션 4가지

### 옵션 A. Anthropic Claude Computer-Use류 grounding 모델 활용

Anthropic의 computer-use 모델은 일반 chat-vision Q&A와 다르게, **화면 기준점(모서리, 이미 알려진 UI 요소)으로부터 픽셀 수를 세도록 특별히 학습**됐다. 이런 특화 학습 덕분에 커서를 클릭할 좌표를 상당히 정확히 반환한다.

**한계**:
- 이건 "지금 클릭해야 할 지점 하나"를 찾는 **상호작용(액션) 용도**로 설계·검증된 것이지, "화면 안의 모든 컴포넌트(테이블/레이블/입력칸/버튼 N개) 위치를 한 번에 구조화해서 전부 뽑아내는" 용도로 검증된 게 아니다. 컴포넌트 개수만큼 반복 질의해야 할 수도 있어 비용·지연시간이 늘어난다.
- 화면 해상도(스크린샷 픽셀 크기)와 모델에 알려주는 `display_width_px`/`display_height_px`가 어긋나면 좌표가 배수로 틀어지는 등, 캡처 이미지의 해상도 처리를 정교하게 맞춰야 하는 실무적 까다로움이 있다.
- 지금 이 프로젝트는 OpenAI/Ollama 2개 provider만 지원한다(`VisionAnalysisClient` 인터페이스, `OpenAiVisionAnalysisClient`/`OllamaVisionAnalysisClient`). Anthropic을 쓰려면 **3번째 provider 구현체를 신규 추가**해야 한다.

### 옵션 B. UI 특화 Object Detection 모델 — Microsoft OmniParser

**Microsoft OmniParser**는 UI 스크린샷을 구조화된 요소로 파싱하는 오픈소스 파이프라인이다:
- **YOLOv8 기반 "interactable region detector"** — 버튼/아이콘 등 클릭 가능한 요소의 bounding box를 탐지하도록, **DOM 트리에서 추출한 6.7만 장의 UI 스크린샷 + bounding box 라벨 데이터셋으로 전용 파인튜닝**됨
- 여기에 아이콘 설명 모델(기능적 의미 파악용, 7천 쌍으로 파인튜닝)과 OCR 모듈(텍스트 추출)을 결합
- 결과물: 원본 스크린샷 위에 구조화된 bounding box를 오버레이해서 반환

**현재 시점에 존재하는 옵션 중 "좌표+UI 시맨틱"을 실제로 목적에 맞게 학습한 가장 실질적인 후보**다.

**한계**:
- 별도의 **GPU 추론 인프라**가 필요하다 — 지금처럼 "OpenAI/Ollama API에 이미지 보내고 JSON 받기" 식의 가벼운 HTTP 호출이 아니라, 모델을 직접 서빙하는 컴포넌트를 새로 세워야 한다.
- Python 생태계 기반 모델이라, 이 프로젝트(Java/Spring Boot)에서 쓰려면 **별도 마이크로서비스로 감싸서 REST로 호출**하는 신규 아키텍처가 필요하다.
- 학습 데이터가 일반적인 웹/앱 UI 위주라, **한국 공공기관 KRDS 스타일 화면에도 잘 맞는지는 검증(POC) 없이 장담할 수 없다.**
- 공공기관 배포 시 이 모델을 어느 서버(내부망 GPU)에서 돌릴지도 별도 검토 대상이다.

### 옵션 C. 문서/레이아웃 분석 서비스 — Azure Document Intelligence Layout 등

Azure Document Intelligence의 Layout 모델은 텍스트 줄/단어, 표, **선택 마크(체크박스 등)**의 bounding box를 실제로 정확히(픽셀 또는 인치 단위) 반환한다. Custom 모델을 학습시키면 폼 필드(텍스트/체크박스/드롭다운)를 정의하고 그 공간적 관계까지 인식시킬 수 있다.

**한계**:
- 원래 **스캔된 문서/양식**을 위해 설계된 서비스라서, "이 사각형이 버튼이다/입력칸이다" 같은 **웹 UI 시맨틱 분류는 지원 범위 밖**이다. 텍스트 블록·표 셀·체크박스 좌표는 주지만, 그걸 "이건 등록 버튼"이라고 분류하려면 별도 로직(색상/모양 휴리스틱, 또는 그 위에 얹는 추가 LLM 분류 단계)이 필요해서 **결국 2단계 하이브리드 파이프라인**이 된다(Document Intelligence로 좌표만 뽑고, 그 위에 다른 모델로 의미 라벨링).
- Azure라는 **세 번째 클라우드 벤더 의존성**이 추가된다.
- 클라우드 서비스이므로 망분리·외부 API 허용 이슈가 OpenAI/Ollama 때와 동일하게 다시 발생한다.

### 옵션 D. 입력을 "정적 이미지"가 아니라 "살아있는 웹페이지"로 바꾸는 방법

이 프로젝트가 이미 쓸 수 있는 브라우저 자동화 도구(Playwright 등)로 실제 페이지를 열어서, 각 DOM 요소의 `boundingBox()`(픽셀 x/y/width/height)를 직접 읽으면 **ML 추론 없이 100% 정확한 좌표를 즉시 얻는다.** 4개 옵션 중 유일하게 "확률적 추정"이 아니라 "확정값"이다.

**한계 — 근본적인 전제 불일치**:
- 지금 `DesignReferenceTool.analyzeDesignReference(referencePath, pageRange, featureType)`은 **PNG/JPEG/PDF 정적 이미지 파일**을 입력으로 받는 도구다. "캡처 화면(스크린샷)을 참고하고 싶다"는 원래 요청과 "살아있는 웹페이지 URL을 달라"는 이 옵션의 전제는 **입력 형태 자체가 다르다.**
- 디자인 참조가 실제로 배포된 타사 웹페이지나 이미 만든 프로토타입 URL이라면 이 옵션이 압도적으로 유리하지만, "PDF로 받은 목업 문서"나 "그림판으로 그린 와이어프레임", "카카오톡으로 전달받은 스크린샷" 같은 경우엔 애초에 적용할 대상이 없다.

### 옵션 E. Figma API/MCP — 디자인 파일 자체에서 구조화 데이터 읽기

Figma 파일은 애초에 **각 레이어(노드)의 정확한 좌표를 데이터로 갖고 있다.** Figma REST API(또는 이 세션에도 이미 연결돼 있는 Figma MCP 서버의 `get_metadata`/`get_design_context` 같은 도구)를 호출하면, 이미지를 "보고 추측"하는 게 아니라 **파일에 저장된 값을 그대로 읽어온다:**

- `absoluteBoundingBox`(x, y, width, height) — 캔버스 기준 절대 좌표, **추정이 아니라 저장된 값**
- `type`(FRAME/TEXT/RECTANGLE/COMPONENT/INSTANCE 등)과 레이어 이름 — 디자이너가 "Button/Primary", "Input/Name", "Table/Row" 처럼 의미 있게 명명해뒀거나 디자인 시스템 컴포넌트를 썼다면, **"이 사각형이 버튼이다"라는 시맨틱 분류가 이미 파일 안에 존재**
- 레이어 계층(부모-자식 중첩)이 곧 그룹핑 구조

**다른 옵션과 비교했을 때의 강점**:
- 옵션 A~C처럼 **모델이 추론하는 게 아니라, 옵션 D(실제 URL의 DOM 읽기)처럼 정확한 저장값을 그대로 읽는 방식**이라 정확도가 100%에 가깝다.
- 옵션 D는 "이미 배포된 살아있는 웹페이지"여야만 적용 가능한데, **Figma는 디자인 목업 단계(아직 코드로 구현되기 전)에도 이미 존재**한다 — "디자인 참조"라는 이 기능의 실제 사용 시나리오(배포 전 목업을 보고 화면을 만들어달라)에 오히려 D보다 더 잘 맞는다.
- 이 세션에도 이미 Figma MCP 서버(`mcp__claude_ai_Figma__get_metadata`, `get_design_context` 등)가 연결돼 있어, OmniParser(옵션 B, GPU 서빙 신규 구축)나 Azure(옵션 C, 신규 클라우드 벤더)에 비해 **새로 붙여야 할 인프라가 훨씬 적다.**

**한계**:
- **입력 전제가 또 다르다**: 지금 `analyzeDesignReference(referencePath, ...)`은 PNG/JPEG/PDF 로컬 파일을 받는 도구다. Figma 방식을 쓰려면 파일이 아니라 **Figma 파일/프레임 URL**을 입력으로 받아야 한다 — 옵션 D와 마찬가지로 입력 계약 자체가 바뀐다.
- **디자인 참조가 실제로 Figma 파일로 존재해야 한다**: 외주 디자이너나 타 부서에서 "이미지로 캡처해서" 또는 "PDF로 내보내서" 전달하는 경우(실무에서 흔함)에는 애초에 원본 Figma 파일에 접근할 수 없어 이 옵션 자체가 성립하지 않는다.
- **레이어 이름 품질에 의존**: 디자이너가 레이어를 "Rectangle 34", "Group 12"처럼 기본값 그대로 두거나 디자인 시스템 컴포넌트를 안 썼다면, 좌표는 정확해도 "이게 버튼인지 레이블인지"는 여전히 모양·색상 기반 추측이 필요해진다.
- Figma도 클라우드 SaaS이므로, 공공기관 배포 시 망분리·외부 API 허용 검토가 동일하게 필요하다.

---

## 3. 종합 비교표

| 옵션 | 좌표 정확도 | UI 시맨틱 이해 | 신규 인프라 | 신규 벤더 | 입력 형태 변경 필요 | 망분리 이슈 |
|---|---|---|---|---|---|---|
| A. Claude computer-use grounding | 중간(단일 타겟 특화, 전체 요소 다중 추출은 미검증) | 낮음(액션 타겟팅 용도) | 낮음(API 호출) | 있음(Anthropic 신규 provider) | 없음 | 있음(외부 API) |
| B. OmniParser(자체 호스팅) | 높음(전용 학습됨) | 중간(버튼/아이콘 위주, KRDS 적합성 미검증) | **높음**(GPU 서빙 인프라, Python↔Java 연동) | 없음(오픈소스, 자체 호스팅) | 없음 | 낮음(내부망 가능) |
| C. Azure Document Intelligence | 높음(텍스트/표/체크박스) | **낮음**(버튼 개념 자체가 없어 별도 분류 로직 필요) | 중간(하이브리드 파이프라인 추가 개발) | 있음(Azure 신규 provider) | 없음 | 있음(외부 API) |
| D. 실제 URL + DOM 읽기 | **100%(확정값)** | 없음(좌표만, 의미는 기존 방식과 별도 결합 필요) | 낮음(기존 브라우저 자동화 재사용 가능) | 없음 | **있음**(입력 계약 자체 변경) | 없음(로컬 렌더링) |
| E. Figma API/MCP | **100%(확정값)** | 있음(레이어명·컴포넌트명이 잘 관리된 경우) | **낮음**(이 세션에도 이미 Figma MCP 연결됨) | 있음(Figma, 단 MCP는 이미 연결됨) | **있음**(파일 대신 Figma URL 입력) | 있음(외부 SaaS) |

---

## 4. 결론 및 권고

다섯 옵션 모두 "지금 바로 붙이자"고 할 만큼 결격사유가 없는 옵션은 없지만, **E(Figma)가 등장하면서 우선순위가 바뀐다:**

- **A**: 좌표 grounding 자체는 정확도가 있지만, 원래 용도(단일 클릭 타겟)와 이번에 필요한 용도(화면 전체 다중 컴포넌트 좌표 일괄 추출)가 다르다는 게 검증 안 된 채로 남는다.
- **B(OmniParser)**: 목적 적합성은 높지만, **자체 GPU 인프라 + Python 서비스 신규 구축**이라는 이 프로젝트 기술 스택(Java/Spring, 경량 API 호출 위주) 자체를 바꾸는 수준의 투자가 필요하다.
- **C**: 결국 좌표(Document Intelligence)와 의미(다른 모델) 두 단계를 이어붙여야 해서, 하나의 비전 모델 호출로 끝나는 지금 구조보다 훨씬 복잡해진다.
- **D**: 기술적으로 깔끔하고 정확하지만, "이미 배포된 살아있는 웹페이지"여야 한다는 전제가 "아직 코드로 안 만든 디자인 목업을 참고하고 싶다"는 이 기능의 실제 쓰임새와 어긋난다.
- **E(Figma)**: **D와 같은 수준의 100% 정확한 좌표를 얻으면서도, D와 달리 "아직 구현 전 목업" 단계에서 바로 쓸 수 있다.** 게다가 레이어/컴포넌트 이름이 잘 관리된 파일이라면 시맨틱 분류(옵션 A/B/C가 각자 방식으로 씨름하던 문제)까지 공짜로 딸려온다. 인프라 부담도 가장 적다(이 세션에도 이미 Figma MCP가 연결돼 있을 정도).

**종합하면 E(Figma API/MCP)가 다섯 옵션 중 가장 현실적인 후보다** — 단, "디자인 참조가 실제로 Figma 파일로 존재하고, 레이어가 어느 정도 의미 있게 관리돼 있다"는 전제가 성립할 때에 한해서다. 캡처 화면(PNG/스크린샷)이나 PDF로만 참조를 받는 경우(이 도구의 현재 입력 계약)에는 E도 D도 적용할 수 없고, 그때는 B(OmniParser)가 차선책이 된다.

어느 옵션이든 이번에 진행 중인 이산 버킷 방식(`design-vision-component-layout-position-impact-and-changes.md`)과는 **비교가 안 될 만큼 큰 규모의 별개 프로젝트**로 취급해야 한다 — 최소한 입력 계약(파일 → URL/Figma 링크) 자체의 변경이 필요하고, 옵션에 따라 신규 인프라·신규 벤더 망분리 재검토까지 필요하기 때문이다.

---

## 5. 만약 실제로 진행하기로 한다면 — 선행 필요 사항 (승인 필요)

- **B를 검토한다면**: OmniParser를 실제 eGovFrame/KRDS 캡처 화면 몇 장에 돌려보는 **POC**를 먼저 진행해, 한국 공공기관 화면에서도 실사용 가능한 정확도가 나오는지 확인. GPU 서빙 인프라를 어디에 둘지(사내 서버/클라우드 GPU) 결정.
- **D를 검토한다면**: `analyzeDesignReference()`에 URL 입력 경로를 추가할지(기존 파일 경로 입력과 병행 지원할지, 대체할지) 결정. URL 기반일 때만 좌표가 정확하고 파일 기반일 때는 여전히 이산 버킷 방식을 써야 하므로, **하나의 도구 안에 두 가지 신뢰도 수준이 공존**하게 되는 UX/계약을 어떻게 설명할지도 함께 결정 필요.
- **E(Figma)를 검토한다면**: `analyzeDesignReference()`에 Figma 파일/프레임 URL 입력 경로를 추가할지 결정(D와 마찬가지로 파일 입력과 병행/대체 여부 결정 필요). 이 세션에 연결된 Figma MCP 도구(`get_metadata`/`get_design_context` 등)로 실제 사내 Figma 파일 몇 개를 대상으로 **좌표·레이어명 품질이 실사용 가능한 수준인지 POC** 먼저 진행. 디자이너들이 실제로 레이어/컴포넌트 이름을 얼마나 일관되게 관리하는지(조직의 Figma 사용 관행)를 먼저 확인해야 시맨틱 분류 품질을 가늠할 수 있음.
- 어느 쪽이든: 공공기관 배포 시나리오에서 신규 인프라/신규 외부 API의 망분리·보안 검토를 별도로 진행.

이 문서는 조사 결과일 뿐이며, 위 옵션 중 어느 것도 착수가 결정되지 않았다.

---

## 참고 자료

- [Getting GPT Vision To Return Coordinates - OpenAI Developer Community](https://community.openai.com/t/getting-gpt-vision-to-return-coordinates/671669)
- [Why GPT Vision Struggles with Bounding Boxes (and How We Fixed It) - Medium](https://medium.com/@silverskytechnology/why-gpt-vision-struggles-with-bounding-boxes-and-how-we-fixed-it-1b5d3db5914b)
- [Navigating the Digital World as Humans Do: Universal Visual Grounding for GUI Agents (arXiv)](https://arxiv.org/html/2410.05243v1)
- [Microsoft AI Releases OmniParser Model on HuggingFace - MarkTechPost](https://www.marktechpost.com/2024/10/24/microsoft-ai-releases-omniparser-model-on-huggingface-a-compact-screen-parsing-module-that-can-convert-ui-screenshots-into-structured-elements/)
- [OmniParser for Pure Vision Based GUI Agent (arXiv)](https://arxiv.org/html/2408.00203v1)
- [OmniParser official page](https://microsoft.github.io/OmniParser/)
- [Best practices for computer and browser use with Claude - Anthropic](https://claude.com/blog/best-practices-for-computer-and-browser-use-with-claude)
- [Claude Computer Use API: Desktop Automation Guide](https://www.digitalapplied.com/blog/anthropic-computer-use-api-guide)
- [What is the Document Intelligence layout model? - Microsoft Learn](https://learn.microsoft.com/en-us/azure/ai-services/document-intelligence/prebuilt/layout?view=doc-intel-4.0.0)
