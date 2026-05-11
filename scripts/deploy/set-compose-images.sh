#!/usr/bin/env bash
set -euo pipefail

backend_image="${1:-}"
frontend_image="${2:-}"
deploy_dir="${COACH_DEPLOY_DIR:-/opt/coach}"
env_file="${3:-${COACH_ENV_FILE:-${deploy_dir}/docker/.env.deploy}}"

if [ -z "$backend_image" ] || [ -z "$frontend_image" ]; then
  echo "usage: set-compose-images.sh <backend-image> <frontend-image> [env-file]" >&2
  exit 1
fi

if [ ! -f "$env_file" ]; then
  echo "env file not found: $env_file" >&2
  exit 1
fi

tmp_file="$(mktemp)"
grep -v -E '^(BACKEND_IMAGE|FRONTEND_IMAGE)=' "$env_file" > "$tmp_file" || true
{
  printf 'BACKEND_IMAGE=%s\n' "$backend_image"
  printf 'FRONTEND_IMAGE=%s\n' "$frontend_image"
} >> "$tmp_file"

cat "$tmp_file" > "$env_file"
rm -f "$tmp_file"
