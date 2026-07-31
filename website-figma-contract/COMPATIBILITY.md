# Website → Figma 계약 호환표

검증일: 2026-07-27 (Semantic Figma 공통 `$defs`, Bundle 교차 계약, Component Catalog 추가)

| 구성 요소 | 구현 버전 | Document | Package |
|---|---|---|---|
| `springai` | Java 17 / Spring Boot 4.1.0-RC1 | `rendered-design-document-v1` | `figpack-v1` |
| `jsp-design-extractor` | Node.js 26.5.0 / Playwright 1.61.1 / TypeScript 5.9.3 | `rendered-design-document-v1` | `figpack-v1` |
| `jsp-to-figma-plugin` | TypeScript 5.9.3 / Figma typings 1.131.0 | `rendered-design-document-v1` | `figpack-v1` |
| `figma-screen-spec-plugin` | TypeScript / Figma Plugin API | `figma-screen-spec-v1` | `figma-export-bundle-v1` |

Schema SHA-256:

```text
rendered-design-document-v1.schema.json 83ea58779e416889ef95805781880a0a1e3cf692a1f7dfd7de00e14fcdc3a5da
figpack-v1.schema.json                   9dc0e501ac56682cbec5d49310b40d2b63d96bac028f7467d8d1b141963e939d
```

`WebsiteFigmaContractCrossValidationTest`(springai)가 `springai` classpath schema와 이 계약 프로젝트 원본의 checksum이
매 빌드마다 일치하는지 자동 검증한다.

`springai`는 빌드 시 중립 Schema를 classpath `contracts/`로 복사한다. Extractor는 로컬 package dependency로 같은
계약 프로젝트를 참조한다. Schema byte가 변경되면 체크섬과 이 표를 함께 갱신하고 모든 계약·E2E 테스트를 다시 실행한다.
Breaking change는 기존 파일을 덮어쓰지 않고 `v2` 계약으로 추가한다.
식별자, 변경 정책, Bundle 버전 일치와 Component Catalog 규칙은
[`CONTRACT_RULES.md`](./CONTRACT_RULES.md)를 따른다.
