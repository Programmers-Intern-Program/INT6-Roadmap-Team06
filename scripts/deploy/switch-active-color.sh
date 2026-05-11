#!/usr/bin/env bash
set -euo pipefail

target_color="${1:-}"
deploy_dir="${COACH_DEPLOY_DIR:-/opt/coach}"
env_file="${COACH_ENV_FILE:-${deploy_dir}/docker/.env.deploy}"
active_file="${COACH_ACTIVE_COLOR_FILE:-${deploy_dir}/ACTIVE_COLOR}"

if [ "$target_color" != "blue" ] && [ "$target_color" != "green" ]; then
  echo "target color must be blue or green. value=${target_color}" >&2
  exit 1
fi

if [ "$(id -u)" -ne 0 ]; then
  exec sudo \
    COACH_DEPLOY_DIR="$deploy_dir" \
    COACH_ENV_FILE="$env_file" \
    COACH_ACTIVE_COLOR_FILE="$active_file" \
    "$0" "$target_color"
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
eval "$("$script_dir/color-ports.sh" "$target_color" "$env_file")"

nginx_config="/etc/nginx/sites-available/coach.conf"
nginx_enabled="/etc/nginx/sites-enabled/coach.conf"

cat > "$nginx_config" <<NGINX
server {
    listen 80 default_server;
    server_name _;

    client_max_body_size 20m;

    proxy_http_version 1.1;
    proxy_set_header Host \$host;
    proxy_set_header X-Real-IP \$remote_addr;
    proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto \$scheme;
    proxy_set_header X-Forwarded-Host \$host;
    proxy_set_header X-Forwarded-Port \$server_port;

    location = /api {
        proxy_pass http://127.0.0.1:${backend_port};
    }

    location /api/ {
        proxy_pass http://127.0.0.1:${backend_port};
    }

    location /actuator/ {
        proxy_pass http://127.0.0.1:${backend_port};
    }

    location /oauth2/ {
        proxy_pass http://127.0.0.1:${backend_port};
    }

    location /login/oauth2/ {
        proxy_pass http://127.0.0.1:${backend_port};
    }

    location / {
        proxy_pass http://127.0.0.1:${frontend_port};
    }
}
NGINX

rm -f /etc/nginx/sites-enabled/default
ln -sfn "$nginx_config" "$nginx_enabled"

nginx -t
systemctl reload nginx || systemctl restart nginx

printf '%s\n' "$target_color" > "$active_file"
chmod 0644 "$active_file"

echo "active color switched to ${target_color}"
