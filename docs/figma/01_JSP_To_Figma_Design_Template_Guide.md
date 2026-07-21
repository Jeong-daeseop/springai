# JSP → Figma Design Template 자동 생성 구현 가이드

**문서명** : 01_JSP_To_Figma_Design_Template_Guide.md

**버전** : 1.1

**작성일** : 2026-07-21

**작성자** : Spring AI MCP Project

**관련 문서** : 이 문서는 초기 구상안이며 02~04번 문서에서 상당 부분 대체되었다. 현재 확정된 전체 아키텍처는 `05_Overall_Architecture_Diagram.md` 참고.

---

# 1. 문서 목적

본 문서는 기존 JSP 기반 화면을 분석하여 Figma Design Template를 자동 생성하는 방법을 정의한다.

본 프로젝트의 목표는 다음과 같다.

```
JSP

↓

실행 화면(Rendering)

↓

HTML + CSS + Layout 분석

↓

Design JSON 생성

↓

Figma Design 생성

↓

React / Thymeleaf 코드 생성
```

---

# 2. 목표

자동으로 생성 가능한 항목

- Figma Frame
- Auto Layout
- Component
- Style
- Variable
- Design System
- Component Library

---

# 3. 전체 아키텍처

```
                JSP

                 │

                 ▼

         Tomcat Rendering

                 │

                 ▼

        Playwright Browser

                 │

        ┌────────┴────────┐

        ▼                 ▼

     DOM 분석         CSS 분석

        │                 │

        └────────┬────────┘

                 ▼

         Layout Analyzer

                 ▼

      Component Recognizer

                 ▼

         Design JSON 생성

                 ▼

          Figma Plugin

                 ▼

      Figma Design Template
```

---

# 4. 처리 절차

## STEP 1

JSP 실행

```
sample.jsp

↓

http://localhost:8080/sample.do
```

---

## STEP 2

Playwright 실행

페이지 Rendering

```
Browser

↓

HTML

↓

CSS

↓

DOM
```

---

## STEP 3

DOM 분석

추출 항목

- Header
- Footer
- Form
- Table
- Input
- Button
- Image
- Select
- Checkbox
- Radio
- Modal
- Tab

---

## STEP 4

CSS 분석

수집 항목

- Width
- Height
- Margin
- Padding
- Border
- Radius
- Background
- Font
- Shadow

---

## STEP 5

Layout 분석

Bounding Box

```
x

y

width

height
```

Layout 종류

- Flex
- Grid
- Block
- Inline
- Absolute

---

## STEP 6

Component 분석

HTML

↓

UI Component

예)

```
button

↓

Primary Button
```

```
table

↓

Data Grid
```

```
input

↓

TextField
```

---

## STEP 7

Pattern 분석

자동 인식

- Search Panel
- Toolbar
- List
- Form
- Detail
- Popup
- Dashboard

---

## STEP 8

Design JSON 생성

예

```json
{
  "pageType":"LIST",
  "components":[
      {
          "type":"SearchPanel"
      },
      {
          "type":"DataGrid"
      }
  ]
}
```

---

## STEP 9

Figma Plugin

JSON

↓

Frame

↓

Auto Layout

↓

Component

↓

Text

↓

Image

---

## STEP 10

Design Template 생성

```
Header

Search

Toolbar

Grid

Paging

Footer
```

---

# 5. 기술 스택

## Backend

- Spring Boot
- Spring AI
- Playwright
- Jsoup
- Jackson

---

## Frontend

- TypeScript
- Figma Plugin API

---

## AI

- GPT
- Claude
- Gemini

---

# 6. Spring AI MCP Tool 구성

```
RenderJspTool

↓

DomAnalyzerTool

↓

CssAnalyzerTool

↓

LayoutAnalyzerTool

↓

ComponentRecognizerTool

↓

DesignJsonGeneratorTool

↓

FigmaExportTool
```

---

# 7. MCP Tool 목록

| Tool | 설명 |
|------|------|
| RenderJspTool | JSP 실행 |
| DomAnalyzerTool | DOM 분석 |
| CssAnalyzerTool | CSS 분석 |
| LayoutAnalyzerTool | Layout 분석 |
| ComponentRecognizerTool | Component 추출 |
| PatternAnalyzerTool | 화면 패턴 분석 |
| DesignJsonGeneratorTool | Design JSON 생성 |
| FigmaExportTool | Figma Export |

---

# 8. 자동 생성 가능한 화면

- List
- Register
- Update
- Detail
- Popup
- Dashboard
- Login

---

# 9. 자동 생성 가능한 Component

- Header
- Footer
- Sidebar
- Search Panel
- Toolbar
- Button
- Input
- Select
- Checkbox
- Radio
- DatePicker
- DataGrid
- Modal
- Tabs
- Accordion
- Breadcrumb
- Pagination

---

# 10. 산출물

생성되는 결과물

```
JSP

↓

Design JSON

↓

Figma Design

↓

Component Library

↓

Design System

↓

React

↓

Thymeleaf
```

---

# 11. 향후 개발 계획

- Bootstrap 자동 분석
- Tailwind 자동 분석
- KRDS Component 자동 인식
- Design Token 생성
- Variable 생성
- Figma Component Library 생성
- React Component 생성
- Thymeleaf Template 생성

---

# 12. 기대 효과

- JSP → Figma 자동 변환
- Design System 자동 구축
- UI Component 재사용
- React 코드 생성
- Thymeleaf 코드 생성
- Spring AI MCP 기반 자동 UI 분석 플랫폼 구축

---

# 추천 문서 구조

- 이 문서를 시작으로 아래와 같이 문서를 구성하면 하나의 설계서(Software Design Document)가 됩니다.
- docs/
  └── figma/
      ├── 01_JSP_To_Figma_Design_Template_Guide.md      ← 전체 개요(현재 문서)
      ├── 02_Playwright_Rendering_Architecture.md       ← Playwright 렌더링
      ├── 03_DOM_CSS_Analyzer.md                        ← DOM/CSS 분석
      ├── 04_Layout_Component_Analyzer.md               ← 레이아웃/컴포넌트 분석
      ├── 05_Design_JSON_Specification.md               ← Design JSON 규격
      ├── 06_Figma_Plugin_Development_Guide.md          ← Figma Plugin 구현
      ├── 07_Spring_AI_MCP_Tool_Design.md               ← MCP Tool 설계
      ├── 08_React_Thymeleaf_Code_Generator.md          ← 코드 생성
      ├── 09_End_To_End_Pipeline.md                     ← 전체 파이프라인
      └── examples/
          ├── employee-list.json
          ├── dashboard.json
          └── popup.json

- 이 구조라면 향후 Spring AI MCP 기반 JSP → Figma → React/Thymeleaf 자동 생성 플랫폼의 공식 설계 문서로 사용하기에 적합합니다.

---
# 문서 변경 이력

| 버전 | 작성일 | 변경 내용 |
|-------|---------|-----------|
| 1.1 | 2026-07-21 | `05_Overall_Architecture_Diagram.md` 링크 추가 |
| 1.0 | 2026-07-20 | 최초 작성 |
