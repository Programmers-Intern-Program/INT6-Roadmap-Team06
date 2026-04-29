# Coach Frontend (auth smoke-test)

Next.js 15 (App Router, TypeScript) 기반 최소 프론트. 백엔드의 OAuth 로그인 →
JWT 쿠키 → `/me` / `/refresh` / `/logout` 흐름을 손으로 검증하기 위한 용도다.

## 셋업

```bash
cd frontend
npm install
cp .env.local.example .env.local   # 필요 시 NEXT_PUBLIC_API_BASE_URL 수정
npm run dev
```

`http://localhost:3000` 에서 동작. 백엔드는 `http://localhost:8080` 에서 `GITHUB_CLIENT_ID` /
`GITHUB_CLIENT_SECRET` 환경변수와 함께 띄워야 한다 (둘 다 미설정이면 OAuth2 빈이 등록되지
않아 `/oauth2/authorization/github` 가 404).

## 화면

| 경로 | 용도 |
|------|------|
| `/` | "GitHub으로 로그인" 버튼 — `/oauth2/authorization/github?redirectUrl=...` 로 풀-페이지 이동 |
| `/me` | `/api/v1/auth/me` 조회. 토큰 갱신/로그아웃 버튼 포함 |
| `/login?error=...` | OAuth 실패 핸들러가 리다이렉트하는 위치 |

## 검증 체크리스트

- [ ] `/` → "GitHub으로 로그인" → GitHub 동의 화면 → 콜백 후 `/me` 에 사용자 정보 표시
- [ ] DevTools → Application → Cookies → `accessToken`, `refreshToken` HttpOnly + SameSite=Lax 확인
- [ ] `/me` 에서 "토큰 갱신" 클릭 → 204 + 새 쿠키 (Network 탭에서 Set-Cookie 확인)
- [ ] "로그아웃" → `/` 로 이동 + 쿠키 사라짐
- [ ] GitHub 동의 화면에서 "Cancel" → `/login?error=...` 로 도착
- [ ] `?redirectUrl=http://localhost:3000/me` 가 콜백 후에도 보존되는지 (OAuth2State 라운드트립)

## 알려진 한계

- 인터셉터/자동 refresh 미구현. 401 발생 시 사용자가 수동으로 "토큰 갱신" 또는 재로그인.
  실제 슬라이스에서 axios/fetch 래퍼에 인터셉터를 붙일 예정.
- 스타일은 데모용 최소 CSS. 디자인 시스템은 별도 도입 예정.
