# website-figma-contract

Website → Figma 실행 프로젝트가 공유하는 기술 중립 계약입니다. `rendered-design-document-v1`과
`figpack-v1`은 breaking change 시 새 버전을 만들며 Java/TypeScript 구현은 버전을 고정해 사용합니다.

- `npm test`: JSON Schema 유효·거부 fixture와 체크섬 검증
- `COMPATIBILITY.md`: 실행 프로젝트별 계약 버전과 승인 체크섬
