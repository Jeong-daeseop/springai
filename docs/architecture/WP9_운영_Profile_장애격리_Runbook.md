# WP9 운영 Profile·장애 격리 Runbook

작성일: 2026-08-06  
대상: `ARCH-WP9`

## 1. Profile 책임

| Profile | Template/static | credential | 용도 |
|---|---|---|---|
| `dev` (기본) | source directory `file:` | 로컬 DB/Redis fallback 허용 | IDE hot reload와 로컬 개발 |
| `test` | jar와 동일한 `classpath:` | 격리 테스트용 fallback 허용 | 자동 테스트에서 패키징 경로 조기 검증 |
| `prod` | `classpath:/templates/`, `classpath:/static/` | DB URL/user/password, Redis URI 필수 | bootJar 배포 |

`EXTERNAL_TEMPLATE_DIRECTORY`가 설정되면 해당 절대 경로의 실제 디렉터리만 order 0 resolver로
우선 탐색한다. 값이 없거나 template이 없으면 jar classpath resolver가 처리한다. 상대 경로와
심볼릭 링크 디렉터리는 기동 시 거부한다.

## 2. Timeout matrix

| Adapter | connect | read/idle | total/first token | retry | circuit/bulkhead |
|---|---:|---:|---:|---|---|
| MySQL | 30s (`Hikari`) | query 30s | - | 없음 | connection circuit, indexing 2 |
| Redis | 3s | 5s | - | 없음 | Redis operation circuit, indexing 2 |
| OpenAI | 5s 정책값 | idle 45s | first token 30s, API total 2m, SSE total 5m | 생성 요청 없음 | adapter circuit, chat 8 |
| Ollama | 3s 정책값 | idle 60s | first token 60s, SSE total 5m | 생성 요청 없음 | adapter circuit, chat 8 |
| Figma | 5s | - | request total 30s | GET의 429/5xx/transport만 최대 3회 | adapter circuit, capture 4 |
| Extractor | 3s | - | request total 60s | POST capture 없음 | adapter circuit, capture 4 |

기본 circuit은 연속 실패 5회에 열리고 30초 뒤 단일 HALF_OPEN probe를 허용한다. Bulkhead는
대기하지 않고 포화 호출을 즉시 거부한다. 값은 `app.resilience`와 각 Adapter property의 환경
변수로 조정한다.

## 3. Executor와 queue

| 기능 | executor | 동시 실행 | queue | 포화 정책 |
|---|---|---:|---:|---|
| Capture | `captureExecutor` | 4 | 32 | abort |
| Indexing | `documentProcessingExecutor` | 2 | 50 | abort |
| Chat | `chatExecutor` | 8 | 128 | abort |
| Vision | `visionExecutor` | 4 | 16 | abort |

Chat의 blocking query compression과 reactive subscription은 `chatExecutor`를 사용한다. 문서
처리는 `documentProcessingExecutor`, 외부 Adapter는 같은 기능군의 semaphore bulkhead를 함께
사용해 다른 기능의 thread/permit을 소비하지 않는다.

## 4. Health와 장애 대응

- `/actuator/health/liveness`: JVM/application 생존 상태만 포함한다. 외부 시스템 장애로 재시작하지 않는다.
- `/actuator/health/readiness`: application readiness와 외부 Adapter circuit 상태를 포함한다.
- health 요청 자체는 외부 시스템을 호출하지 않아 probe가 장애를 증폭하지 않는다.
- 특정 Adapter circuit이 열려도 다른 Adapter circuit과 `/` 같은 무관 endpoint는 계속 동작한다.

CI의 `prodBootJarSmoke`는 임시 working directory에서 prod bootJar를 실행하고 UI, 정적 리소스,
두 health probe를 검증한다. `ExternalCallGuardTest`는 장애 주입으로 Adapter별 circuit 격리를,
`FigmaApiClientTest`는 로컬 HTTP 장애(429/5xx/timeout)와 GET retry 상한을 검증한다.
`Wp9FailureIsolationIntegrationTest`는 Figma circuit을 강제로 연 뒤에도 무관한 `/` UI endpoint가
정상 응답하는지 검증한다.

## 5. Worker 분리 판단 기준

아래 중 하나가 15분 이상 지속되고 executor/bulkhead 조정만으로 해소되지 않으면 해당 기능을 별도
Worker로 분리하는 ADR을 작성한다.

| metric | 경고 | 분리 검토 |
|---|---:|---:|
| queue 사용률 | 70% | 85% 이상 지속 |
| rejected / submitted | 0.1% | 1% 이상 |
| active / max concurrency | 80% | 95% 이상 지속 |
| 처리 latency p95 / timeout | 60% | 80% 이상 |
| circuit OPEN 횟수 | 시간당 1회 | 시간당 3회 이상 |
| Chat 중 Capture/Indexing 영향 | p95 10% 증가 | p95 25% 증가 |

분리 우선순위는 `Indexing`(CPU/Redis batch), `Capture/Vision`(긴 외부 I/O), `Chat` 순서다. 분리
후에도 operation/artifact 계약과 idempotency key는 동일하게 유지해야 한다. 실제 metric 발행과
dashboard/alert는 `ARCH-WP10` 범위다.

## 6. 구현 증적과 재검증

| 검증 | 명령/테스트 | 2026-08-06 결과 |
|---|---|---|
| 전체 CI 회귀 + 패키지 실행 | `./gradlew test prodBootJarSmoke -Pci --console=plain` | PASS |
| 임의 cwd UI | `prodBootJarSmoke`의 `/`, `/js/marked.min.js` | PASS |
| probe 분리 | `prodBootJarSmoke`의 liveness/readiness | PASS |
| profile/민감 기본값 | `Wp9ProfileContractTest` | PASS |
| 외부 template 경로 정책 | `ExternalTemplateResolverConfigTest` | PASS |
| circuit/bulkhead | `ExternalCallGuardTest` | PASS |
| 장애 전파 차단 | `Wp9FailureIsolationIntegrationTest` | PASS |
| Figma retry/timeout | `FigmaApiClientTest` | PASS |

재검증은 기본적으로 위 Gradle 명령 하나로 수행한다. CI에서도 `clean test bootJar -Pci` 이후
`prodBootJarSmoke -Pci`를 별도 단계로 실행해 source checkout에 우연히 의존하는 패키지 오류를
차단한다.
