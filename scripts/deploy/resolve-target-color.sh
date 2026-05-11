#!/usr/bin/env bash
set -euo pipefail

requested="${1:-auto}"
deploy_dir="${COACH_DEPLOY_DIR:-/opt/coach}"
active_file="${COACH_ACTIVE_COLOR_FILE:-${deploy_dir}/ACTIVE_COLOR}"

normalize_color() {
  case "${1:-}" in
    blue|green)
      printf '%s\n' "$1"
      ;;
    *)
      return 1
      ;;
  esac
}

if [ "$requested" = "blue" ] || [ "$requested" = "green" ]; then
  printf '%s\n' "$requested"
  exit 0
fi

if [ "$requested" != "auto" ]; then
  echo "target color must be auto, blue, or green. value=${requested}" >&2
  exit 1
fi

active_color=""
if [ -f "$active_file" ]; then
  active_color="$(tr -d '[:space:]' < "$active_file" || true)"
fi

if ! normalize_color "$active_color" >/dev/null 2>&1; then
  printf '%s\n' "blue"
  exit 0
fi

if [ "$active_color" = "blue" ]; then
  printf '%s\n' "green"
else
  printf '%s\n' "blue"
fi
