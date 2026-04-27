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

기본 profile은 `local`입니다. GitHub OAuth까지 확인하려면 `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`을 `.env`에 채우고 `local,oauth` profile로 실행합니다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local,oauth"
```

## 테스트

```powershell
cd backend
.\gradlew.bat test
```
