# Figma PAT 운영·회전 절차 (Release A / P1)

> 적용 범위: 로컬 단일 사용자(P1) 실행 환경  
> 금지 범위: 공유 서버(P2), 사용자별 권한 분리가 필요한 환경(P3)

## 1. 기본 원칙

- PAT는 Figma 파일을 읽는 데 필요한 최소 권한만 부여한다.
- PAT, 전체 Figma URL, 원본 fileKey와 nodeId를 소스·문서·로그에 기록하지 않는다.
- 실제 값은 `.env` 또는 IntelliJ의 로컬 실행 환경변수에만 저장한다.
- `.env`는 Git에 커밋하지 않으며 `.env.example`에는 빈 값만 둔다.
- `FIGMA_ALLOWED_FILE_KEYS`에는 업무상 승인된 파일만 등록한다.
- 애플리케이션은 `SERVER_ADDRESS=127.0.0.1`로 실행한다.

## 2. 최초 설정

1. Figma에서 읽기 전용 용도의 PAT를 새로 발급한다.
2. 만료일은 조직 정책이 허용하는 가장 짧은 운영 주기로 정한다.
3. `.env`에 다음 값을 설정한다.

   ```dotenv
   DESIGN_VISION_FIGMA_ENABLED=true
   FIGMA_ACCESS_TOKEN=<발급한_PAT>
   FIGMA_ALLOWED_FILE_KEYS=<승인된_fileKey[,추가_fileKey]>
   SERVER_ADDRESS=127.0.0.1
   ```

4. 애플리케이션을 재시작한다. 실행 중 환경변수 변경만으로는 적용되지 않는다.
5. 허용 파일의 명시적 frame/node URL로 `analyzeFigmaReference`를 한 번 호출한다.
6. `sourceType=FIGMA`, `fileVersion` 존재, 기대한 `featureType`과 `UiDesignSpec` 생성 여부를 확인한다.

## 3. 정기 회전

만료 전에 다음 순서로 회전한다.

1. 기존 PAT를 폐기하지 않은 상태에서 새 PAT를 발급한다.
2. 새 PAT의 최소 읽기 권한과 만료일을 확인한다.
3. 로컬 `.env` 또는 IntelliJ 실행 구성의 `FIGMA_ACCESS_TOKEN`만 새 값으로 교체한다.
4. 애플리케이션을 재시작한다.
5. allowlist에 있는 대표 frame/node로 MCP smoke test를 실행한다.
6. 성공을 확인한 뒤 기존 PAT를 Figma에서 폐기한다.
7. 회전 일시·담당자·검증 결과만 운영 기록에 남긴다. 토큰 값과 원본 fileKey는 기록하지 않는다.

검증 실패 시 새 PAT의 권한·만료·소유자 파일 접근권을 확인하고, 기존 PAT가 아직 유효하면 환경변수를 되돌려 서비스를 복구한다. 실패한 새 PAT는 폐기하고 다시 발급한다.

## 4. 긴급 폐기

토큰 노출 또는 오용이 의심되면 다음 순서로 처리한다.

1. `DESIGN_VISION_FIGMA_ENABLED=false`로 바꾸고 애플리케이션을 재시작한다.
2. Figma에서 의심 PAT를 즉시 폐기한다.
3. `.env`, IntelliJ 실행 구성, 셸 히스토리, 로그와 공유 문서의 노출 여부를 확인한다.
4. 필요하면 승인된 fileKey 목록도 재검토한다.
5. 새 PAT를 발급하고 §3의 smoke test를 통과한 뒤에만 기능을 다시 활성화한다.

## 5. 운영 점검 체크리스트

- [ ] 실행 주소가 `127.0.0.1`이다.
- [ ] 기능을 사용하지 않을 때 `DESIGN_VISION_FIGMA_ENABLED=false`이다.
- [ ] PAT에 불필요한 쓰기 권한이 없다.
- [ ] PAT 만료일과 다음 회전 예정일을 별도 비밀관리 시스템에 기록했다.
- [ ] allowlist에 승인되지 않은 fileKey가 없다.
- [ ] `.env`와 PAT가 Git 추적 대상이 아니다.
- [ ] 일반 로그에 토큰·전체 URL·원본 fileKey/nodeId가 없다.
- [ ] 대표 frame/node MCP smoke test가 성공한다.

## 6. 프로필 확장 제한

이 절차는 P1 전용이다. 여러 사용자가 같은 서버에 접근하거나 사용자별 Figma 권한을 적용해야 하면 PAT 공유로 확장하지 않는다. MCP 사용자 인증·인가, 사용자별 OAuth, tenant 데이터·캐시·RAG 격리를 구현한 Release B 절차를 별도로 승인해야 한다.
