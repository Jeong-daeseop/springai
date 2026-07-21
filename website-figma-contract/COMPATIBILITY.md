# Website → Figma 계약 호환표

검증일: 2026-07-21

| 구성 요소 | 구현 버전 | Document | Package |
|---|---|---|---|
| `springai` | Java 17 / Spring Boot 4.1.0-RC1 | `rendered-design-document-v1` | `figpack-v1` |
| `jsp-design-extractor` | Node.js 26.5.0 / Playwright 1.61.1 / TypeScript 5.9.3 | `rendered-design-document-v1` | `figpack-v1` |
| `jsp-to-figma-plugin` | TypeScript 5.9.3 / Figma typings 1.131.0 | `rendered-design-document-v1` | `figpack-v1` |

Schema SHA-256:

```text
rendered-design-document-v1.schema.json c51f733d86b94709315a2818629b92946c3bf66edf704d50a259aa462ea582f7
figpack-v1.schema.json                   351ba6dec642265f969b5a1928e7f0368182b49e242c238090574545d06917ef
```

`springai`는 빌드 시 중립 Schema를 classpath `contracts/`로 복사한다. Extractor는 로컬 package dependency로 같은
계약 프로젝트를 참조한다. Schema byte가 변경되면 체크섬과 이 표를 함께 갱신하고 모든 계약·E2E 테스트를 다시 실행한다.
Breaking change는 기존 파일을 덮어쓰지 않고 `v2` 계약으로 추가한다.
