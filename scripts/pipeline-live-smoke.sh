#!/usr/bin/env bash
set -euo pipefail

# 운영과 동일한 환경변수로 DB·Redis live smoke를 실행한다.
if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

export PIPELINE_INFRA_SMOKE_LIVE=true
exec ./gradlew pipelineInfrastructureLiveSmoke --console=plain
