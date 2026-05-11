#!/usr/bin/env bash
set -euo pipefail

color="${1:-}"
deploy_dir="${COACH_DEPLOY_DIR:-/opt/coach}"
env_file="${2:-${COACH_ENV_FILE:-${deploy_dir}/docker/.env.deploy}}"

if [ "$color" != "blue" ] && [ "$color" != "green" ]; then
  echo "color must be blue or green. value=${color}" >&2
  exit 1
fi

read_env_value() {
  local key="$1"
  local default_value="$2"
  local line value

  line="$(grep -E "^${key}=" "$env_file" 2>/dev/null | tail -n 1 || true)"
  if [ -z "$line" ]; then
    printf '%s\n' "$default_value"
    return
  fi

  value="${line#*=}"
  value="${value%\"}"
  value="${value#\"}"
  value="${value%\'}"
  value="${value#\'}"
  printf '%s\n' "$value"
}

if [ "$color" = "blue" ]; then
  frontend_port="$(read_env_value FRONTEND_BLUE_PORT 3001)"
  backend_port="$(read_env_value BACKEND_BLUE_PORT 8081)"
else
  frontend_port="$(read_env_value FRONTEND_GREEN_PORT 3002)"
  backend_port="$(read_env_value BACKEND_GREEN_PORT 8082)"
fi

for port in "$frontend_port" "$backend_port"; do
  if ! [[ "$port" =~ ^[0-9]+$ ]]; then
    echo "port must be numeric. value=${port}" >&2
    exit 1
  fi
done

printf 'frontend_port=%s\n' "$frontend_port"
printf 'backend_port=%s\n' "$backend_port"
