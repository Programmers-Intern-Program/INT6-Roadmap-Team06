# Docker Deploy Notes

이 디렉터리는 시연용 단일 EC2 Blue-Green 배포에서 사용할 실행 기준을 둔다.

## 환경 파일

```powershell
Copy-Item docker/.env.deploy.example docker/.env.deploy
```

`docker/.env.deploy`에는 운영 도메인, OAuth client id/secret, JWT secret, AI Gateway key를 채운다. 이 파일은 Git에 올리지 않는다.

## 이미지 빌드

```powershell
docker build -t coach-backend:local ./backend

docker build `
  --build-arg NEXT_PUBLIC_API_BASE_URL=http://localhost:8081 `
  --build-arg NEXT_PUBLIC_GITHUB_CONNECTION_CLIENT_ID=dummy `
  --build-arg NEXT_PUBLIC_GITHUB_CONNECTION_REDIRECT_URI=http://localhost:3001/github/callback `
  -t coach-frontend:local ./frontend
```

`NEXT_PUBLIC_*` 값은 frontend build 시점에 client bundle에 포함된다. OAuth secret, JWT secret, AI Gateway key는 image에 넣지 않고 runtime env로만 주입한다.

## Blue-Green 실행 기준

```powershell
docker compose --env-file docker/.env.deploy -f docker/docker-compose.deploy.yml up -d postgres redis
docker compose --env-file docker/.env.deploy -f docker/docker-compose.deploy.yml up -d backend-blue frontend-blue
```

기본 포트는 다음과 같다.

- nginx active endpoint: `80`
- backend blue: `8081`
- backend green: `8082`
- frontend blue: `3001`
- frontend green: `3002`

inactive color 배포, smoke 검증, active switch는 #303 GitHub Actions CD workflow에서 수행한다. Nginx active switch는 `/opt/coach/ACTIVE_COLOR` 기준으로 현재 active color를 기록하고, `/api`, `/actuator`, `/oauth2`, `/login/oauth2`는 backend active color로, 나머지 요청은 frontend active color로 proxy한다.

## GHCR 기반 CD

#303 CD workflow는 backend/frontend image를 GHCR에 push하고 EC2에서 pull한다.

필요한 GitHub Secrets:

- `EC2_HOST`
- `EC2_USER` (없으면 `ubuntu`)
- `EC2_SSH_PRIVATE_KEY`
- `DEPLOY_ENV_FILE`

필요한 GitHub Variables:

- `APP_BASE_URL`
- `API_BASE_URL`
- `NEXT_PUBLIC_API_BASE_URL`
- `NEXT_PUBLIC_GITHUB_CONNECTION_CLIENT_ID`
- `NEXT_PUBLIC_GITHUB_CONNECTION_REDIRECT_URI`

GHCR package 권한이 막히면 같은 workflow 구조에서 image 전달 방식만 `docker save`/`scp`/`docker load`로 바꾸는 것을 fallback으로 둔다.

## Smoke

```powershell
.\scripts\smoke\deploy-smoke.ps1 `
  -AppBaseUrl "http://localhost:3001" `
  -ApiBaseUrl "http://localhost:8081" `
  -Origin "http://localhost"
```

active switch 이후에는 Nginx endpoint를 대상으로 확인한다.

```powershell
.\scripts\smoke\deploy-smoke.ps1 `
  -AppBaseUrl "http://localhost" `
  -ApiBaseUrl "http://localhost" `
  -Origin "http://localhost"
```
