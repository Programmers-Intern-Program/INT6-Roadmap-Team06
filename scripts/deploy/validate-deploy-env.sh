#!/usr/bin/env bash
set -euo pipefail

env_file="${1:-docker/.env.deploy}"

if [ ! -f "$env_file" ]; then
  echo "::error::Deploy env file not found: $env_file" >&2
  exit 1
fi

declare -A values=()

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

strip_quotes() {
  local value="$1"
  if [[ "$value" == \"*\" && "$value" == *\" ]]; then
    value="${value:1:${#value}-2}"
  elif [[ "$value" == \'*\' && "$value" == *\' ]]; then
    value="${value:1:${#value}-2}"
  fi
  printf '%s' "$value"
}

while IFS= read -r raw_line || [ -n "$raw_line" ]; do
  line="${raw_line%$'\r'}"
  line="${line#$'\ufeff'}"
  line="$(trim "$line")"

  if [ -z "$line" ] || [[ "$line" == \#* ]] || [[ "$line" != *=* ]]; then
    continue
  fi

  key="$(trim "${line%%=*}")"
  value="$(trim "${line#*=}")"
  value="$(strip_quotes "$value")"

  if [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
    values["$key"]="$value"
  fi
done < "$env_file"

required_keys=(
  POSTGRES_DB
  POSTGRES_USER
  POSTGRES_PASSWORD
  DATABASE_URL
  DATABASE_USERNAME
  DATABASE_PASSWORD
  REDIS_HOST
  REDIS_PORT
  CORS_ALLOWED_ORIGINS
  FRONTEND_URL
  JWT_SECRET
  GITHUB_CLIENT_ID
  GITHUB_CLIENT_SECRET
  GITHUB_CONNECTION_CLIENT_ID
  GITHUB_CONNECTION_CLIENT_SECRET
  AI_GATEWAY_API_KEY
  AI_GATEWAY_BASE_URL
  AI_GATEWAY_MODEL
)

defaulted_keys=(
  AI_GATEWAY_TIMEOUT_CONNECT
  AI_GATEWAY_TIMEOUT_READ
  API_PROXY_CONNECT_TIMEOUT
  API_PROXY_SEND_TIMEOUT
  API_PROXY_READ_TIMEOUT
)

is_placeholder() {
  local value="$1"
  local normalized
  normalized="$(printf '%s' "$value" | tr '[:upper:]' '[:lower:]')"

  [ -z "$normalized" ] ||
    [[ "$normalized" == *change-me* ]] ||
    [[ "$normalized" == your-* ]] ||
    [[ "$normalized" == *your-production* ]] ||
    [[ "$normalized" == dummy ]] ||
    [[ "$normalized" == example ]] ||
    [[ "$normalized" == *example.com* ]] ||
    [[ "$normalized" == *replace-me* ]] ||
    [[ "$normalized" == *paste* ]] ||
    [[ "$normalized" == todo ]]
}

error_count=0

for key in "${required_keys[@]}"; do
  if [[ ! -v "values[$key]" ]]; then
    echo "::error::${key} is required in $env_file" >&2
    error_count=$((error_count + 1))
    continue
  fi

  if is_placeholder "${values[$key]}"; then
    echo "::error::${key} must be set to a non-placeholder value" >&2
    error_count=$((error_count + 1))
  fi
done

for key in "${defaulted_keys[@]}"; do
  if [[ ! -v "values[$key]" ]]; then
    echo "::warning::${key} is not set in $env_file; repository default will be used" >&2
    continue
  fi

  if is_placeholder "${values[$key]}"; then
    echo "::error::${key} must be set to a non-placeholder value or omitted to use the repository default" >&2
    error_count=$((error_count + 1))
  fi
done

if [ "$error_count" -gt 0 ]; then
  echo "::error::Deploy env validation failed with ${error_count} problem(s)" >&2
  exit 1
fi

echo "Deploy env validation passed (${#required_keys[@]} required keys checked)."
