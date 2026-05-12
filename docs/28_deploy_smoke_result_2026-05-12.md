# 2026-05-12 운영 배포 Smoke 결과

## 범위

#304 운영 배포 smoke의 1차 검증으로, Elastic IP HTTP 기준 배포 파이프라인을 확인했다.

이번 검증은 실제 시연 환경 full rehearsal이 아니다. GitHub OAuth, GitHub API, AI Gateway 실응답, HTTPS 도메인, 운영 OAuth callback 검증은 도메인/HTTPS 연결 후 2차에서 수행한다.

## 1차 환경

- 대상: AWS 단일 EC2 Blue-Green demo 환경
- 접속 기준: Elastic IP HTTP
- 프론트/백엔드 active endpoint: `http://3.39.160.175`
- 배포 방식: GitHub Actions `workflow_dispatch`
- 이미지 registry: GHCR
- 실행 브랜치: `test/deploy-smoke-304`
- 성공 run: https://github.com/Programmers-Intern-Program/INT6-Roadmap-Team06/actions/runs/25692322866

## 준비 작업

- Terraform으로 EC2, Elastic IP, Security Group, IAM role/profile을 생성했다.
- EC2 user data로 Docker, Docker Compose, Nginx 기반을 준비했다.
- GitHub Actions secrets/variables를 HTTP smoke 기준으로 등록했다.
- HTTP smoke용 runtime env는 `prod` profile 기준으로 구성했다.
- OAuth/AI secret 원문은 기록하지 않았다.

## 발생한 문제와 조치

### 1. GitHub Actions secret 인코딩 문제

첫 실행에서 `EC2_HOST` secret 앞에 BOM 문자가 섞여 SSH host 해석이 실패했다.

조치:

- 한 줄 secret은 `gh secret set --body`로 재등록했다.
- private key와 deploy env 파일은 파일 redirection 방식으로 재등록했다.
- deploy env 파일은 ASCII 인코딩으로 정규화했다.

### 2. GitHub Actions runner SSH 접근 차단

초기 Terraform 설정에서 SSH를 로컬 공인 IP `/32`로만 열어 GitHub Actions runner가 EC2에 접속하지 못했다.

조치:

- 1차 HTTP smoke 기준으로 SSH ingress를 임시 `0.0.0.0/0`로 열었다.
- SSH key 기반 접근만 허용한다.
- 2차에서는 도메인/HTTPS 정리와 함께 SSH 접근 범위를 다시 좁히거나 SSM/GitHub Actions IP 반영 방식으로 전환한다.

### 3. Backend readiness race

배포 직후 backend가 아직 boot 중인데 inactive smoke가 먼저 실행되어 `Connection refused`가 발생했다.

조치:

- 배포 workflow에 inactive backend `/actuator/health` `UP` 대기 단계를 추가했다.
- 30회, 5초 간격으로 확인한다.

### 4. Same-origin active endpoint CORS 검사

Nginx active endpoint에서는 프론트와 API가 같은 origin이다. 이 경우 CORS 헤더가 필수는 아닌데 smoke runner가 CORS 헤더를 강제해 실패했다.

조치:

- deploy smoke runner에서 `SMOKE_ORIGIN`과 `API_BASE_URL` origin이 같으면 CORS 헤더를 강제하지 않도록 보정했다.
- direct blue/green port처럼 origin이 다른 경우에는 기존처럼 CORS 헤더를 검증한다.

## 최종 검증 결과

GitHub Actions run `25692322866`에서 다음 단계가 모두 통과했다.

- backend image build/push
- frontend image build/push
- EC2 SSH 연결
- 배포 파일 동기화
- inactive color 계산
- inactive color container 배포
- inactive backend health 대기
- inactive color deploy smoke
- Nginx active color switch
- active endpoint post-switch smoke

로컬에서도 추가 확인했다.

- `scripts/smoke/deploy-smoke.ps1 -AppBaseUrl http://3.39.160.175 -ApiBaseUrl http://3.39.160.175 -Origin http://3.39.160.175` 통과
- direct inactive port smoke 통과
- 브라우저에서 `http://3.39.160.175` 접속 시 로그인 필요 화면 렌더링 확인

## 1차 결론

Elastic IP HTTP 기준으로는 배포 파이프라인이 동작한다.

단, 이 결과는 배포 경로와 Nginx active switch 검증이다. 최종 시연 가능 여부는 도메인 HTTPS, 운영 OAuth callback, 실제 GitHub OAuth, GitHub API, AI Gateway, Redis polling, v1/v2 Coach 흐름을 포함한 2차 full rehearsal에서 판단한다.

## 리소스 정리

1차 Elastic IP HTTP smoke 이후 비용 방지를 위해 Terraform destroy를 실행했다.

- 실행 명령: `terraform destroy -var-file="terraform.tfvars" -auto-approve`
- 삭제 결과: `Destroy complete! Resources: 7 destroyed.`
- 삭제 대상: EC2, Elastic IP, EIP association, Security Group, IAM role, IAM instance profile, SSM policy attachment
- 후속 확인: `terraform state list` 결과 비어 있음
- 후속 확인: `terraform plan -destroy -var-file="terraform.tfvars"` 결과 `No changes. No objects need to be destroyed.`

기존 HTTP smoke IP `3.39.160.175`는 더 이상 유효하지 않다. 내일 도메인/HTTPS 2차 검증 전 새로 `terraform apply`를 수행한 뒤 GitHub Actions secrets/variables를 새 IP 또는 도메인 기준으로 다시 갱신해야 한다.

## 2차 예정

- 도메인 A record 또는 Route53 연결
- HTTPS/TLS 설정
- 운영 GitHub OAuth App callback 등록
  - 로그인: `https://API_DOMAIN/login/oauth2/code/github`
  - 저장소 연결: `https://APP_DOMAIN/github/callback`
- GitHub Actions variables를 HTTPS 도메인 기준으로 갱신
- `DEPLOY_ENV_FILE`에 운영 OAuth/AI Gateway 값을 반영
- SSH ingress 임시 공개 설정 정리
- #304 기준 운영 도메인 full rehearsal 수행

## 2차 진행 기록

### 2차-1. HTTPS 준비 변경

운영 도메인 full rehearsal을 위해 단일 EC2 demo 구성에 HTTPS 진입 경로를 추가했다.

- Terraform Security Group에 Nginx HTTPS `443/tcp` ingress를 추가했다.
- `switch-active-color.sh`가 `APP_DOMAIN`과 Let’s Encrypt 인증서 파일을 감지하면 443 HTTPS server block을 함께 생성하도록 보강했다.
- 인증서가 없으면 기존처럼 HTTP active endpoint만 생성한다.
- HTTP endpoint는 GitHub Actions deploy smoke와 인증서 갱신 경로를 위해 유지한다.

검증:

- `terraform fmt -check` 통과
- `terraform validate` 통과
- `bash -n scripts/deploy/switch-active-color.sh` 통과

### 2차-2. Terraform 재생성

비어 있던 local state 기준으로 Terraform apply를 다시 실행했다.

- 실행 명령: `terraform apply -var-file="terraform.tfvars" -auto-approve`
- 생성 결과: `Apply complete! Resources: 7 added, 0 changed, 0 destroyed.`
- 새 Elastic IP: `13.209.241.224`
- public HTTP URL: `http://13.209.241.224:80`
- SSH target: `ubuntu@13.209.241.224`
- EC2 instance: `i-0af2f6410b5817824`

기존 HTTP smoke IP `3.39.160.175`는 계속 무효다.

### 2차-3. GitHub Actions CD HTTP gate

GitHub Actions secret/variable을 새 Elastic IP 기준으로 갱신한 뒤 CD workflow를 수동 실행했다.

- 실행 ref: `test/deploy-domain-smoke-304`
- 성공 run: https://github.com/Programmers-Intern-Program/INT6-Roadmap-Team06/actions/runs/25722638629

성공 run에서 다음 단계가 모두 통과했다.

- backend image build/push
- frontend image build/push
- EC2 SSH 연결
- 배포 파일 동기화
- inactive color 계산
- inactive color container 배포
- inactive backend health 대기
- inactive color deploy smoke
- Nginx active color switch
- active endpoint post-switch smoke

로컬에서도 추가 확인했다.

- `powershell -ExecutionPolicy Bypass -File .\scripts\smoke\deploy-smoke.ps1 -AppBaseUrl http://13.209.241.224 -ApiBaseUrl http://13.209.241.224 -Origin http://13.209.241.224` 통과
- EC2 active color는 `green`
- `backend-green`, `frontend-green`, `postgres`, `redis` 모두 healthy
- 현재 Nginx는 인증서가 없어 HTTP server block만 생성된 상태
- EC2에 `certbot`, `python3-certbot-nginx` 설치 완료
- `certbot --version` 결과: `certbot 2.9.0`

### 2차 중 발생한 문제와 조치

#### 1. 이전 IP 기준 CORS 설정

첫 번째 2차 CD run `25721786607`은 inactive smoke CORS preflight에서 실패했다.

원인:

- `DEPLOY_ENV_FILE`이 이전 IP 기준 값을 포함하고 있어 direct backend port smoke의 `Origin: http://13.209.241.224`를 허용하지 않았다.

조치:

- HTTP gate용 `DEPLOY_ENV_FILE`을 새 Elastic IP 기준으로 재등록했다.
- 이 env는 도메인 full rehearsal용 secret이 아니라 HTTP deploy smoke용 placeholder다.

#### 2. Postgres volume password mismatch

두 번째 2차 CD run `25722111113`은 inactive backend health 대기에서 timeout 됐다.

원인:

- 첫 run에서 생성된 Postgres volume은 이전 DB password로 초기화됐다.
- 이후 새 smoke env의 DB password와 달라져 backend Flyway 초기화가 `FATAL: password authentication failed for user "coach"`로 실패했다.

조치:

- active switch 전이고 사용자 데이터가 없는 smoke 환경이므로 EC2에서 compose stack을 `down -v`로 정리했다.
- 세 번째 run에서 Postgres/Redis volume을 새 env 기준으로 다시 생성했고 CD가 통과했다.

### 2차 남은 항목

아직 도메인 full rehearsal은 완료되지 않았다. 다음 값이 필요하다.

- 실제 단일 도메인 `DOMAIN`
- DNSZI A record를 `13.209.241.224`로 연결
- 도메인용 GitHub OAuth App 2개
  - 로그인 callback: `https://DOMAIN/login/oauth2/code/github`
  - 저장소 연결 callback: `https://DOMAIN/github/callback`
- 도메인용 `DEPLOY_ENV_FILE`
  - `APP_DOMAIN=DOMAIN`
  - `FRONTEND_URL=https://DOMAIN`
  - `CORS_ALLOWED_ORIGINS=https://DOMAIN,http://13.209.241.224`
  - 실제 OAuth client id/secret
  - 실제 AI Gateway key
  - 운영용 JWT secret

도메인 값이 준비되면 GitHub Actions variables를 HTTPS 도메인 기준으로 다시 갱신하고, Certbot 발급 후 HTTPS smoke와 full rehearsal을 이어서 수행한다.
