# krds-design-system-author-plugin

`design-system-spec-v1` JSON을 읽어 현재 Figma 파일의 Variable Collection·Component Set·Variant를
**제자리 생성/갱신**하는 로컬 전용 Plugin입니다(11번 문서 R3). Main Component는 삭제·재생성하지 않고
`pluginData`(`designSystemId`/`logicalId`/`contentHash`)로 기존 자산을 찾아 갱신합니다.

```bash
npm install
npm test
npm run typecheck
npm run lint
npm run build
npm run fixture:sample   # dist/sample-design-system-spec.json 생성 — 수동 QA용 샘플
```

Figma Desktop의 **Plugins → Development → Import plugin from manifest**에서 `manifest.json`을 선택합니다.

## 사용 흐름

1. Plugin UI에서 `design-system-spec-v1` 계약을 따르는 JSON 파일을 선택한다. 구조 검증 실패 시 오류 코드,
   JSON Pointer와 상세 원인을 표시하며, 기존 논리 컴포넌트가 있으면 해당 Figma 노드로 이동할 수 있다.
2. 기존 태깅된 Component/Variable과 비교한 변경 미리보기(ADD/UPDATE/NO_CHANGE/BREAKING/DEPRECATE)와
   토큰·속성·Layout·개발 메타데이터의 전/후 값을 확인한다.
3. "적용"을 눌러 실제로 생성·갱신하고, `🔍 KRDS Preview — {designSystemId}` 페이지에서 결과를 확인한다.
4. 검토 상태는 `DRAFT → IN_REVIEW → APPROVED/REJECTED`로 전이한다. 승인·반려 기록 JSON은
   `/api/design-systems/.../reviews` 연계에 사용할 수 있도록 다운로드한다.
5. `APPROVED` 상태이며 사람이 Library를 Publish한 경우에만 Registry JSON을 내보낸다.

## 제약과 후속 작업

- Figma Plugin API는 Team Library Publish를 프로그래밍적으로 수행할 수 없다. Publish는 사람이 Figma UI에서
  직접 해야 하며, 이 Plugin은 Publish 여부와 무관하게 로컬 파일의 Component/Variable만 다룬다(R3-024와 일치).
- Publish 후 `Published Registry JSON 내보내기`를 실행하면 각 Component Set·Variant·Variable·Variable
  Collection의 공개 Key와 `UNPUBLISHED`/`CURRENT`/`CHANGED` 상태를
  `component-registry-v1` 형식으로 내보낸다. Spring의 `ComponentRegistrySyncService`는 모든 필수 자산이
  `CURRENT`일 때만 사람 확인 후 불변 Registry Snapshot을 저장하고 Profile을 `PUBLISHED`로 전환한다.
- Plugin API는 현재 파일의 `fileKey`를 제공하지 않으므로 Registry 내보내기 화면에서 Library `fileKey`를
  입력한다. FTC 테스트 Library의 확인된 값은 `mVy5h1UbORVqQoBm8Wr1bT`이다.
- DEPRECATE로 표시된 항목은 자동 삭제하지 않는다(11번 §2 원칙 #7).
- Auto Layout은 padding/gap/alignment와 min/max width·height를 적용한다. Figma 제약에 따라 값은
  양수여야 하며 min이 max보다 크면 import 전에 거부한다.
- Component Set의 description, documentation link, 코드 컴포넌트·패키지 메타데이터를 함께 갱신한다.
- Figma 권한 오류와 API rate limit 오류는 서로 다른 코드로 표시하며 rate limit만 재시도 가능으로 안내한다.
- 네트워크에 접근하지 않으며, Plugin이 만든 Component/Variable을 실제 Team Library로 만드는 절차는 이
  Plugin 밖(사람의 Publish)에서 이어진다.
