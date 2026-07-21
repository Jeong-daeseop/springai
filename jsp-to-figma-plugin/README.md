# jsp-to-figma-plugin

`figpack-v1`을 검증하고 현재 Figma 파일에 Frame/Text 구조를 생성하는 로컬 전용 Plugin입니다.

```bash
npm install
npm run typecheck
npm run build
```

Figma Desktop의 **Plugins → Development → Import plugin from manifest**에서 `manifest.json`을 선택합니다.
Plugin은 네트워크에 접근하지 않으며, 실패할 경우 이번 실행에서 생성한 임시 최상위 Frame만 제거합니다.
