---
schemaVersion: "1.0"
typography:
  heading:
    fontSize: "20px"
    fontWeight: "700"
  body:
    fontSize: "14px"
    fontWeight: "400"
colors:
  primary: "krds.color.primary.60"
  secondary: "krds.color.secondary.50"
spacing:
  unit: "8px"
  small: "4px"
  large: "16px"
radius:
  default: "8px"
  button: "4px"
layout:
  grid: "12"
  maxWidth: "1200px"
components:
  button:
    variant: "krds-btn"
    size: "medium"
  table:
    density: "STANDARD"
voice:
  tone: "formal"
  language: "ko-KR"
forbidden:
  - pattern: "inline style"
    reason: "토큰 외 하드코딩 금지"
---

# 프로젝트 디자인 규칙

이 파일은 eGovFrame JSP→Thymeleaf 변환 시 적용할 설계 규칙을 정의합니다.

## 소개

YAML frontmatter 위의 규칙들은 자동으로 파싱되어 Thymeleaf 생성기에 적용됩니다.

## 운영 규칙

- 모든 색상은 KRDS 토큰을 참조해야 합니다.
- 간격은 8px 단위를 기본으로 유지합니다.
- 버튼은 항상 krds-btn 클래스를 사용합니다.
