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

- backend blue: `8081`
- backend green: `8082`
- frontend blue: `3001`
- frontend green: `3002`

inactive color 배포, smoke 검증, active switch는 후속 #303 GitHub Actions CD workflow에서 붙인다.

## Smoke

```powershell
.\scripts\smoke\deploy-smoke.ps1 `
  -AppBaseUrl "http://localhost:3001" `
  -ApiBaseUrl "http://localhost:8081"
```
