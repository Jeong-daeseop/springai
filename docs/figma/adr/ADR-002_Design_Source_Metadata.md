# ADR-002 DesignSourceMetadata 전환

- 상태: 승인
- 결정일: 2026-07-21

WEB_CAPTURE는 FILE, FIGMA에 이은 세 번째 source이므로 flat nullable 필드를 늘리지 않고 sealed
`DesignSourceMetadata`로 전환한다. 기존 flat 필드는 이전 DB JSON read 호환을 위해 유지하며 신규 로직은
`sourceMetadata.sourceType()`을 사용한다. WEB_CAPTURE는 전용 subtype 없이는 생성할 수 없고 Release 1에서 RAG에 적재하지 않는다.
