# KRDS Q&A 6화면 운영전환 승인 체크리스트

- 대상 Registry: `krds / 2.1.0`
- 대상 Rule Set: `krds-role-variant / 2.0.0-candidate`
- 대상 화면: Q&A 6개 Desktop 화면
- 현재 상태: Preview 검증 완료, 사람 승인 대기

## 1. 승인 전 필수 확인

### A. Library 계약

- [x] 필수 Logical Type 10종이 Registry에 존재한다.
- [x] Component와 Variant Key가 실제 Published Library에서 import된다.
- [x] Property 이름과 Variant Axis가 실제 Library와 일치한다.
- [x] 빈 Default Variant인 범용 래퍼를 생성 대상으로 사용하지 않는다.
- [ ] Design System Owner가 Registry `2.1.0`을 승인했다.

### B. 화면 완전성

- [x] 목록·등록·상세·답변 목록·답변 상세·답변 등록이 각각 독립 Frame이다.
- [x] 실제 Frame 수가 정확히 6개다.
- [x] 6개 Screen Spec의 ID가 중복되지 않는다.
- [x] 등록과 상세의 Field State가 default와 view로 구분된다.
- [x] Primary·Secondary·Destructive Action이 역할별 Variant로 분리된다.

### C. 품질 Gate

- [x] unresolved Published Instance가 0건이다.
- [x] placeholder가 0건이다.
- [x] 육안 검토에서 Overflow와 Clipping이 발견되지 않았다.
- [x] 계약 테스트가 Registry, Rule Set, Screen Spec 6개를 검증한다.
- [ ] 6개 화면의 픽셀 Visual Regression 기준선과 임계값이 승인됐다.
- [ ] Focus, Error, Disabled, Read-only 상태의 접근성 검증이 완료됐다.

### D. 운영 안전성

- [x] Rule Set은 승인 전 `DRAFT` 상태다.
- [x] Screen Spec은 승인 전 `REVIEW_REQUIRED` 상태다.
- [ ] 승인자, 승인 시각, 승인 대상 Hash가 기록됐다.
- [ ] 이전 Registry·Rule Set Snapshot으로 Rollback Preview 재생성에 성공했다.
- [ ] 모니터링 지표와 장애 대응 담당자가 지정됐다.

## 2. 승인 기록

| 역할 | 이름 | 판정 | 일시 | 근거/의견 |
|---|---|---|---|---|
| Design System Owner |  | 대기 |  |  |
| Product Designer |  | 대기 |  |  |
| Frontend/Plugin Owner |  | 대기 |  |  |
| MCP/Backend Owner |  | 대기 |  |  |

승인은 Registry 버전, Rule Set 버전, 6개 Screen Spec의 Git commit 또는 Artifact Hash를 함께 기록해야 유효하다.

## 3. Publish 절차

1. 계약·Java·Plugin 전체 테스트 결과를 승인 기록에 첨부한다.
2. Product Designer가 6개 Frame을 원본 6개 스크린샷과 화면별로 대조한다.
3. Design System Owner가 Registry의 Component Key, Property, Lifecycle을 승인한다.
4. Rule Set을 `DRAFT`에서 `PUBLISHED`로 변경하는 별도 변경을 만든다.
5. Screen Spec 상태를 `REVIEW_REQUIRED`에서 `APPROVED`로 변경한다.
6. Preview를 동일 입력으로 다시 생성하고 Frame 수, unresolved instance, fallback 수를 확인한다.
7. 운영 기본 버전을 새 Registry·Rule Set으로 전환한다.

사람 승인 없이 4~7단계를 자동 실행하지 않는다.

## 4. Rollback 절차

다음 중 하나가 발생하면 즉시 이전 Snapshot으로 되돌린다.

- 필수 Component import 실패
- Property 또는 Variant Axis Drift
- 6개 중 화면 누락·중복
- fallback 또는 placeholder 발생
- 승인된 기준선을 넘는 Visual Diff

Rollback 순서는 다음과 같다.

1. 신규 Rule Set의 운영 선택을 중단한다.
2. 직전 `PUBLISHED` Registry·Rule Set·Pattern 버전을 활성화한다.
3. 실패한 Preview와 Generation Report를 보존한다.
4. 이전 Snapshot으로 Q&A 6개 Preview를 재생성한다.
5. 화면 수 6, unresolved 0, fallback 0을 확인한다.
6. 원인과 영향 화면을 기록한 뒤 재승인 전까지 후보 버전을 `DRAFT`로 유지한다.

Figma에서 이번 Preview 페이지 자체를 되돌려야 할 때는 파일 Version History 또는 페이지 단위 복제를 사용한다. 검증 증거가 남아야 하므로 승인 없이 페이지나 Frame을 삭제하지 않는다.

## 5. 운영 전환 판정

현재는 **전환 불가**다. A~D의 미완료 항목과 네 역할의 승인 기록이 모두 채워진 뒤에만 `PUBLISHED` 전환이 가능하다.
