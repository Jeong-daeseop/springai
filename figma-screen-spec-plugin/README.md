# eGovFrame FigmaScreenSpec Export Plugin

Spring에서 내려받은 `.figma-export-bundle.json`을 Published FTC/KRDS Component Instance 기반
Figma 화면으로 생성·동기화한다.

입력은 DEC-10에 따라 **파일을 기본값**으로 사용한다. 서버 REST 직접 조회는 운영 환경이
허용 도메인·CORS·단기 토큰을 설정한 경우에만 사용하는 선택 기능이며, 실패하면 파일 입력으로
복귀한다.

## 동작

1. Bundle의 Screen/Profile/Registry/Metadata 버전 일치 여부를 검증한다.
2. `krds.*`, `egov.*` 논리 타입에 대응하는 Registry 항목과 `CURRENT` 상태를 검사한다.
3. `logicalNodeId`로 기존 Wrapper/Instance를 찾아 Preview diff를 만든다.
4. `MERGE`는 기존 논리 노드를 재사용하고 신규 노드만 만든다.
5. `REPLACE`는 기존 화면을 Removed 영역으로 Archive한 뒤 다시 만든다.
6. Spec에서 사라진 노드는 삭제하지 않고 `🗄 Removed — {screenId}`로 이동한다.
7. Registry의 `componentSetKey`로 Published Component Set을 import하고 Variant·Property를 적용한다.
8. 기존 Frame을 선택해 Migration Preview를 계산하고, 모호한 매핑이 없을 때만 전체 Backup 복제 후 `logicalNodeId` 부여와 Published Instance 교체를 수행한다.

DEC-12에 따라 v1 Plugin은 Removed Node의 즉시 삭제(`DELETE`)나 노드별 확인(`ASK`)을
제공하지 않는다. Archive 영구 삭제는 Backup과 생성 보고서를 확인한 뒤 사람이 별도로 수행한다.

## Legacy Frame Migration

1. 승인된 `.figma-export-bundle.json`을 불러온다.
2. 마이그레이션할 기존 Root Frame 하나를 선택한다.
3. `선택한 기존 Frame Migration Preview`를 실행한다.
4. `MANUAL_REVIEW`가 있으면 Frame 이름 또는 구조를 사람이 정리한 뒤 Preview를 다시 실행한다.
5. Preview가 적용 가능할 때 `백업 후 Migration 적용`을 누른다.
6. Plugin은 선택 Root를 숨김 Backup으로 복제한 뒤 `logicalNodeId`를 부여하고, 매핑된 로컬 Instance를 Published Instance로 교체한다.
7. 실패하면 화면에 표시된 `backupNodeId`의 Backup Frame으로 되돌린다.

Migration은 자동으로 원본 Frame을 삭제하지 않는다. Backup을 사람이 검증한 후에만 정리한다.
8. 사용자가 Instance Property를 변경한 경우 이전 Plugin 관리값과 비교해 Override를 보존한다.
9. 결과를 `figma-generation-report` 형태의 JSON으로 내려받을 수 있다.

## 개발

```bash
npm install
npm test
npm run typecheck
npm run lint
npm run build
```

Figma Desktop의 `Plugins > Development > Import plugin from manifest...`에서 `manifest.json`을 선택한다.

정식 MERGE/REPLACE는 `APPROVED` ScreenSpecification과 모든 필수 Component가 `CURRENT`인
Registry에서만 허용한다. 필수 Component가 없을 때 일반 Frame으로 조용히 대체하지 않는다.
