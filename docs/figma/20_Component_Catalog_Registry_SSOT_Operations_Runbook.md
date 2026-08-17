# Catalog·Component Registry SSOT 운영 Runbook

## 1. 목적

Catalog v2와 Registry v3 전환 기간 동안 Legacy Reader와 Resolved Registry의 결과를 안전하게 비교하고, 운영 승인 후 Legacy Reader를 제거하기 위한 절차를 정의한다.

## 2. 관찰·이중 읽기

1. `POST /api/design-systems/registries/observe-v3`로 두 결과를 비교하고 Report ID를 보관한다.
2. `POST /api/design-systems/registries/dual-read-v3`로 이중 읽기를 실행한다.
3. 전환 기간의 선택값은 `selectedSource=LEGACY`여야 한다.
4. 차이는 `AI_COMPONENT_REGISTRY_RESOLUTION_REPORT`에 저장한다.
5. `COMPONENT_KEY_CHANGED`, `VARIANT_KEY_CHANGED`, `LOGICAL_TYPE_MISSING`은 P0 차이로 분류한다.

## 3. Legacy Reader 제거 조건

아래 조건을 모두 충족하기 전에는 Legacy Reader를 제거하지 않는다.

- 최소 14일 연속 `dual-read-v3` 관찰 완료
- 운영 대상 Profile·Registry 전체에서 비교 Report `identical=true`
- P0 차이 0건, P1 차이 0건
- 전체 Registry v3 일괄 검증 API가 `valid=true`
- 7개 기준 화면의 Figma Desktop E2E와 Generation Report 보관 완료
- 운영자 승인 기록과 Rollback 대상 Snapshot 확인 완료
- `APP_FIGMA_SSOT_BUNDLE_ENABLED=true` 전환 후 7일간 오류·Fallback 증가 없음

## 4. 중단·Rollback 기준

- 동일 Profile에서 P0 차이가 1건 이상 발생하면 SSOT 전환을 중단한다.
- 필수 Binding 누락, Content Hash 불일치, Quality Gate 실패가 발생하면 즉시 Legacy 선택값으로 복귀한다.
- Rollback은 `confirmed=true`와 actor를 요구하는 `rollback-v3` 경로만 사용한다.
- 최소 최근 3개 승인 Snapshot과 Generation Report를 보존한다.

## 5. 운영 체크리스트

- [ ] Catalog/Registry 버전·Hash 기록
- [ ] 관찰 Report ID 기록
- [ ] 이중 읽기 선택 소스가 `LEGACY`인지 확인
- [ ] 전체 Snapshot 검증 결과 보관
- [ ] 7개 화면 E2E 증적 보관
- [x] 운영자 승인자·시각·Comment 보관
- [x] Rollback 대상 버전과 복귀 결과 보관

## 6. 제거 후 검증

Legacy Reader 제거 후에도 Catalog v2, Registry v3 Schema/Contract Test, 전체 Snapshot 일괄 검증, Rollback 경로는 유지한다. 제거 작업은 별도 변경 승인과 배포 후 Smoke Test를 거친다.

## 7. 최종 검증 기록 (2026-08-17)

- `./gradlew test --console=plain`: `BUILD SUCCESSFUL`
- Plugin `npm run typecheck`: 통과
- Plugin `npm test`: 49/49 통과
- Plugin `npm run build`: 통과
- 7개 화면 Generation Report와 승인·Rollback 증적: `docs/figma/evidence/`, `KRDS_QNA_7화면_운영검증보고서_2026-08-16.md`
