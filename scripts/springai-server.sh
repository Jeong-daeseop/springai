#!/usr/bin/env bash
set -euo pipefail

# 운영 기본 포트. MCP 클라이언트(.codex/config.toml)와 일치해야 한다.
SERVER_PORT="${SERVER_PORT:-8080}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="${ROOT_DIR}/build/run"
PID_FILE="${RUN_DIR}/springai-server.pid"
LOG_FILE="${RUN_DIR}/springai-server.log"

mkdir -p "${RUN_DIR}"

descendants() {
  local parent="$1"
  local child
  for child in $(pgrep -P "${parent}" 2>/dev/null || true); do
    descendants "${child}"
    printf '%s\n' "${child}"
  done
}

stop_tree() {
  local root_pid="$1"
  local child
  if ! kill -0 "${root_pid}" 2>/dev/null; then
    return 0
  fi
  for child in $(descendants "${root_pid}"); do
    kill -TERM "${child}" 2>/dev/null || true
  done
  kill -TERM "${root_pid}" 2>/dev/null || true
}

read_pid() {
  [[ -f "${PID_FILE}" ]] && tr -d '[:space:]' < "${PID_FILE}" || true
}

start() {
  local existing_pid
  existing_pid="$(read_pid)"
  if [[ -n "${existing_pid}" ]] && kill -0 "${existing_pid}" 2>/dev/null; then
    echo "SpringAI가 이미 실행 중입니다. PID=${existing_pid}, PORT=${SERVER_PORT}"
    exit 0
  fi
  rm -f "${PID_FILE}"

  if lsof -nP -iTCP:"${SERVER_PORT}" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "포트 ${SERVER_PORT}가 이미 사용 중입니다. lsof -nP -iTCP:${SERVER_PORT} -sTCP:LISTEN으로 확인하세요." >&2
    exit 1
  fi

  # stdin까지 분리하지 않으면 bootRun의 Gradle/Java 자식이 실행 세션의
  # 종료 신호를 물려받아 Tomcat이 내려갈 수 있다.
  nohup "${BASH_SOURCE[0]}" run </dev/null >"${LOG_FILE}" 2>&1 &
  local launcher_pid="$!"
  disown "${launcher_pid}" 2>/dev/null || true
  echo "${launcher_pid}" > "${PID_FILE}"
  echo "SpringAI 시작 요청: PID=$(cat "${PID_FILE}"), PORT=${SERVER_PORT}"
}

run() {
  local gradle_pid=""
  cleanup() {
    if [[ -n "${gradle_pid:-}" ]]; then
      stop_tree "${gradle_pid}"
      wait "${gradle_pid}" 2>/dev/null || true
    fi
    rm -f "${PID_FILE}"
  }
  trap cleanup INT TERM EXIT

  cd "${ROOT_DIR}"
  # 운영 서버는 프로젝트의 .env에 정의된 API Key/DB/Redis 설정을 함께 사용한다.
  # shellcheck disable=SC1091
  if [[ -f "${ROOT_DIR}/.env" ]]; then
    set -a
    source "${ROOT_DIR}/.env"
    set +a
  fi
  SERVER_PORT="${SERVER_PORT}" \
    SPRING_DEVTOOLS_RESTART_ENABLED=false \
    ./gradlew bootRun --console=plain &
  gradle_pid="$!"
  wait "${gradle_pid}"
}

stop() {
  local pid
  pid="$(read_pid)"
  if [[ -z "${pid}" ]]; then
    echo "관리 PID 파일이 없습니다. 실행 중인 SpringAI를 변경하지 않았습니다."
    exit 0
  fi
  if kill -0 "${pid}" 2>/dev/null; then
    stop_tree "${pid}"
    echo "SpringAI 종료 신호를 전달했습니다. PID=${pid}"
  else
    rm -f "${PID_FILE}"
    echo "stale PID 파일을 정리했습니다. PID=${pid}"
  fi
}

status() {
  local pid
  pid="$(read_pid)"
  if [[ -n "${pid}" ]] && kill -0 "${pid}" 2>/dev/null; then
    echo "RUNNING PID=${pid} PORT=${SERVER_PORT}"
    lsof -nP -iTCP:"${SERVER_PORT}" -sTCP:LISTEN || true
  else
    echo "STOPPED PORT=${SERVER_PORT}"
  fi
}

case "${1:-status}" in
  start) start ;;
  run) run ;;
  stop) stop ;;
  restart) stop || true; sleep 1; start ;;
  status) status ;;
  *) echo "사용법: $0 {start|run|stop|restart|status}" >&2; exit 2 ;;
esac
