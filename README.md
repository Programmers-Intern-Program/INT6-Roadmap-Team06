# INT6-Roadmap-Team06

AI 개발자 성장 코치 서비스입니다. 사용자의 프로필, GitHub 활동, 진단 결과를 바탕으로 주차별 학습 로드맵과 진도 관리를 제공합니다.

## 로컬 실행 준비

루트에서 `.env.example`을 복사해 개인 `.env`를 만듭니다.

```powershell
Copy-Item .env.example .env
```

`.env`에는 실제 GitHub OAuth secret, AI API key, JWT secret 같은 개인 값을 넣습니다. `.env`는 Git에 올리지 않습니다.

## DB/Redis 실행

```powershell
docker compose -f docker/docker-compose.yml up -d
```

기본 로컬 값은 다음과 같습니다.

- PostgreSQL: `localhost:5433`, database `coachdb`, user/password `coach`
- Redis: `localhost:6380`

## 백엔드 실행

```powershell
cd backend
.\gradlew.bat bootRun
```

기본 profile은 `local`입니다. GitHub 로그인 OAuth까지 확인하려면 `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`을 `.env`에 채우고 `local,oauth` profile로 실행합니다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local,oauth"
```

GitHub OAuth App은 목적별로 분리합니다.

- 로그인 OAuth App callback URL: `http://localhost:8080/login/oauth2/code/github`
- 저장소 연결 OAuth App callback URL: `http://localhost:3000/github/callback`

저장소 연결 OAuth App을 따로 만들었다면 `.env`에 `GITHUB_CONNECTION_CLIENT_ID`, `GITHUB_CONNECTION_CLIENT_SECRET`도 채웁니다. 값이 없으면 기존 `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`을 fallback으로 사용합니다.

## 프론트엔드 실행

```powershell
cd frontend
npm install
npm run dev
```

기본 주소는 `http://localhost:3000`입니다.

프론트에서 백엔드 API를 호출하려면 `frontend/.env.local`에 API base URL을 설정합니다. 값이 없으면 같은 origin의 상대 경로로 요청합니다.

```powershell
Set-Content .env.local "NEXT_PUBLIC_API_BASE_URL=http://localhost:8080"
```

`/github`에서 저장소 연결을 확인하려면 `frontend/.env.local`에 저장소 연결 OAuth App client id와 callback URL을 추가합니다.

```powershell
Add-Content .env.local "NEXT_PUBLIC_GITHUB_CONNECTION_CLIENT_ID=your-github-connection-oauth-client-id"
Add-Content .env.local "NEXT_PUBLIC_GITHUB_CONNECTION_REDIRECT_URI=http://localhost:3000/github/callback"
```

프론트 변경 검증은 다음 명령으로 확인합니다.

```powershell
cd frontend
npm ci
npm run lint
npm run build
```

## 테스트

백엔드 테스트는 실행 범위와 Docker 필요 여부가 다릅니다.

```powershell
cd backend
.\gradlew.bat test
```

- `test`: 단위 테스트와 Docker가 필요 없는 빠른 테스트를 실행합니다. `integration` 태그는 제외됩니다.
- `prIntegrationTest`: PR 병합 전에 확인할 `pr-gate` 태그 테스트를 실행합니다. 현재 CI의 PR 검증에 포함됩니다.
- `prVerification`: `test`와 `prIntegrationTest`를 함께 실행합니다. 백엔드 PR 본문에는 기본적으로 이 명령 결과를 적습니다.
- `integrationTest`: `integration` 태그 테스트를 실행합니다. Testcontainers가 PostgreSQL/Redis 컨테이너를 띄우므로 Docker Desktop이 켜져 있어야 합니다.
- `fullVerification`: `test`, `integrationTest`, JaCoCo 리포트 생성을 함께 실행합니다. `dev`/`main` push CI의 전체 백엔드 검증 기준입니다.

PR 검증 기준:

```powershell
cd backend
.\gradlew.bat prVerification
```

Docker/Testcontainers까지 포함해 전체 통합 테스트를 확인하려면 Docker Desktop을 먼저 실행한 뒤 다음 중 하나를 사용합니다.

```powershell
cd backend
.\gradlew.bat integrationTest
.\gradlew.bat fullVerification
```

Docker가 꺼져 있거나 Docker CLI에 접근할 수 없으면 `integrationTest`는 시작 전에 실패합니다. 이 경우 Docker Desktop을 켠 뒤 같은 명령을 다시 실행합니다.
