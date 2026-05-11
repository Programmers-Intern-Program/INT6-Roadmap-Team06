# Demo Blue-Green Terraform

시연용 단일 EC2 Blue-Green 배포 인프라를 만드는 Terraform 구성이다. #302의 `docker/docker-compose.deploy.yml`을 EC2에서 실행할 수 있도록 서버, 보안 그룹, Elastic IP, IAM instance profile, Docker 설치 user data를 준비한다.

## 범위

포함:

- EC2 instance
- Security Group
- Elastic IP
- IAM role / instance profile
- Ubuntu 24.04 LTS AMI 조회
- Docker, Docker Compose plugin 설치 user data
- `/opt/coach` 배포 디렉터리 준비

제외:

- RDS / ElastiCache
- ALB target group 기반 Blue-Green
- CloudFront / S3 정적 배포
- Route53 record 생성
- GitHub Actions CD 자동화

## 준비

```powershell
cd infra/terraform/demo-bluegreen
Copy-Item terraform.tfvars.example terraform.tfvars
```

`terraform.tfvars`에는 실제 값을 넣는다. 이 파일은 Git에 올리지 않는다.

확인할 값:

- `key_name`: AWS에 이미 생성된 EC2 key pair 이름
- `allowed_ssh_cidr`: 가능하면 내 IP `/32`
- `allowed_http_cidr`: 시연 접근을 허용할 CIDR
- `vpc_id`, `subnet_id`: 기본 VPC를 쓰지 않을 때만 지정

## 명령

```powershell
terraform init
terraform fmt -check
terraform validate
terraform plan -var-file="terraform.tfvars"
```

실제 리소스 생성은 AWS 계정, 비용, 도메인, secret 주입 방식이 확정된 뒤 실행한다.

```powershell
terraform apply -var-file="terraform.tfvars"
```

## 출력

`terraform apply` 후 다음 값을 확인한다.

- `elastic_ip`
- `ssh_host`
- `frontend_blue_url`
- `frontend_green_url`
- `backend_blue_url`
- `backend_green_url`
- `deploy_directory`

기본 포트는 #302 deploy compose와 맞춘다.

- frontend blue: `3001`
- frontend green: `3002`
- backend blue: `8081`
- backend green: `8082`

## OAuth Callback

도메인을 연결한 뒤 GitHub OAuth App callback URL을 운영 도메인 기준으로 등록한다.

- 로그인 OAuth App: `https://API_DOMAIN/login/oauth2/code/github`
- 저장소 연결 OAuth App: `https://APP_DOMAIN/github/callback`

Elastic IP만 사용하는 1차 검증에서는 GitHub OAuth callback이 HTTPS 도메인을 요구하는지 먼저 확인한다. HTTPS 도메인이 필요하면 Route53, reverse proxy, TLS 설정을 #303 또는 후속 배포 작업에서 붙인다.

## 배포 파일

user data는 EC2에 `/opt/coach`와 하위 디렉터리를 만든다. #303 CD workflow에서는 이 위치에 다음 파일을 배치하는 흐름으로 잡는다.

- `docker/docker-compose.deploy.yml`
- `docker/.env.deploy`
- `scripts/smoke/deploy-smoke.ps1`

secret은 image에 bake하지 않고 `docker/.env.deploy` 또는 GitHub Actions secret에서 runtime env로만 주입한다.

## 금지

다음 파일과 값은 repo에 커밋하지 않는다.

- `terraform.tfvars`
- `*.tfstate`
- AWS access key / secret key
- OAuth client secret
- JWT secret
- AI Gateway API key
- 실제 production `.env.deploy`
