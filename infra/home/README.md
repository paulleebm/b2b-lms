# 홈 배포 운영: Vercel + 미니 PC(Portainer) + Cloudflare

`bymile.site`를 실제로 띄우는 순서입니다. 구성과 선택 이유는 [docs/11](../../docs/11-home-deployment.md)에 있습니다.

```
app.bymile.site   → Vercel (Next.js BFF)
auth.bymile.site  → Cloudflare Tunnel → 미니 PC: keycloak   (/admin은 Access로 소유자만)
api.bymile.site   → Cloudflare Tunnel → 미니 PC: api        (Access 서비스 토큰: Vercel만)
mail.bymile.site  → Cloudflare Tunnel → 미니 PC: mailpit    (Access로 소유자만)
파일              → Cloudflare R2 (브라우저 ↔ R2, presigned URL)
```

| 파일 | 역할 |
|---|---|
| `docker-compose.yml` | Portainer 스택. 이미지만 씁니다(빌드·바인드 마운트 없음) |
| `build-images.sh` | 이미지 4개(api, keycloak, postgres, elasticmq)를 빌드해서 Docker Hub에 푸시 |
| `postgres/`, `elasticmq/` | 역할 생성 스크립트와 큐 설정을 넣은 이미지 (Portainer에서 파일을 마운트하지 않으려고) |
| `rehearsal/` | 같은 스택을 이 PC에서 띄워 보는 설정 (터널 대신 localhost, R2 대신 S3Mock) |
| `../terraform/envs/home` | Cloudflare(DNS·터널·Access·R2), Vercel(프로젝트·도메인·환경변수), Upstash(세션 Redis) |

## 0. 계정 준비 (한 번, 직접)

| 할 일 | 어디서 | 메모 |
|---|---|---|
| Cloudflare 가입, **Zero Trust** 시작(Free 플랜), **R2** 활성화 | dash.cloudflare.com | R2와 Zero Trust는 무료 플랜에서도 결제 수단 등록을 요구할 수 있음. 팀 이름(`xxx.cloudflareaccess.com`)을 정해 둠 |
| Cloudflare API 토큰 | My Profile → API Tokens → Create (Custom) | 권한: Account·Zone Read, Zone·DNS Edit, Account·Cloudflare Tunnel Edit, Account·Access: Apps and Policies Edit, Account·Access: Service Tokens Edit, Account·Workers R2 Storage Edit. 대상: 내 계정, 모든 zone(아직 zone이 없으므로) |
| Vercel 가입 | vercel.com | GitHub로 로그인 → GitHub 앱이 `b2b-lms` 저장소에 접근하도록 허용 |
| Vercel API 토큰 | Account Settings → Tokens | |
| Upstash 가입, Management API 키 | console.upstash.com → Account → Management API | |
| Docker Hub 로그인 | 이 PC에서 `docker login` | 공개 저장소로 올리면 Portainer에 레지스트리 설정이 필요 없음 (이미지에 비밀값 없음) |

토큰은 **파일에 쓰지 않고** 셸 환경변수로만 넘깁니다. 저장소에도, 대화에도 붙여 넣지 않습니다.

## 1. Terraform (이 PC에서)

```bash
cd infra/terraform/envs/home
cp home.tfvars.example home.tfvars      # 계정 ID, 이메일, Docker Hub 이름
export CLOUDFLARE_API_TOKEN=... VERCEL_API_TOKEN=... TF_VAR_upstash_api_key=...
terraform init
terraform apply -var-file=home.tfvars -target=cloudflare_zone.this   # 먼저 zone만
terraform output name_servers
```

1. **가비아에서 네임서버 교체:** My가비아 → 도메인 관리 → `bymile.site` → 네임서버 설정 → 위의 2개 입력. 전파 확인은 `nslookup -type=NS bymile.site`로 하며, 보통 몇 분에서 몇 시간 걸립니다. Cloudflare 대시보드에서 zone이 **Active**가 되면 다음으로 갑니다.
2. 나머지를 적용합니다.
   ```bash
   terraform apply -var-file=home.tfvars
   terraform output -raw stack_env > ../../../home/stack.env    # gitignored, 비밀값 포함
   ```
3. **R2 키 만들기:** R2 → Manage API tokens → Create → *Object Read & Write*, 버킷 `lms-media`만 선택. Access Key ID와 Secret Access Key를 `stack.env`의 마지막 두 줄에 채웁니다. 대시보드에서 만든 이유는, Terraform으로 만들면 Terraform 토큰에 "API 토큰 생성" 권한까지 줘야 하기 때문입니다.

> **상태 파일에 비밀값이 있습니다.** 생성한 비밀번호, 터널 토큰, 서비스 토큰이 `terraform.tfstate`에 들어갑니다. AWS 설계에서는 비밀값을 SSM에 따로 넣어 상태 파일 밖에 뒀지만, 여기서는 Cloudflare가 만들어 주는 토큰을 Vercel 환경변수로 넘겨야 해서 피할 수 없습니다. 그래서 상태는 원격 저장소에 올리지 않고 이 PC에만 둡니다(gitignore). 잃어버리면 `apply`가 비밀값을 새로 만들고, Portainer 스택 값을 다시 넣으면 됩니다. DB 비밀번호는 기존 볼륨에 남아 있으므로, 이때는 `POSTGRES_*`·`LMS_*_PASSWORD`·`KEYCLOAK_DB_PASSWORD` 값을 예전 값으로 유지해야 합니다.

## 2. 이미지 푸시 (이 PC에서)

```bash
IMAGE_PREFIX=docker.io/<도커허브이름> ./infra/home/build-images.sh                      # 미니 PC가 x86(인텔/AMD)
IMAGE_PREFIX=docker.io/<도커허브이름> PLATFORM=linux/arm64 ./infra/home/build-images.sh # 미니 PC가 ARM
```

미니 PC의 CPU는 Portainer → Host → Details에서 확인합니다. 커밋 해시와 `latest` 두 태그로 올라갑니다.

## 3. Portainer (미니 PC)

1. Stacks → Add stack → 이름 `lms` → **Web editor**에 `docker-compose.yml` 내용을 붙여 넣습니다.
2. Environment variables → **Load variables from .env file**로 `stack.env`를 넣습니다.
3. Deploy the stack.

정상 상태:
- `postgres`, `valkey`, `elasticmq`, `mailpit`, `keycloak`, `api`, `worker`, `cloudflared`는 **running** (healthy)
- `seed`는 **exited (0)**: 데모 데이터를 넣고 끝난 상태입니다. 이미 있는 회사는 건너뜁니다.

Cloudflare Zero Trust → Networks → Tunnels에서 `lms-home`이 **Healthy**인지 확인한 뒤, https://app.bymile.site 에서 `learner@demo-elec.test` / `Demo-2026-lms`로 로그인합니다.

## 4. Vercel

`terraform apply`가 프로젝트·도메인·환경변수를 만들고, Vercel은 **`main` 브랜치에 푸시될 때마다** 배포합니다. 첫 배포는 Vercel 대시보드 → Deployments → Redeploy로 시작해도 됩니다. 환경변수(`KEYCLOAK_ISSUER`, `STORAGE_ORIGIN`)는 빌드 시점에 CSP 헤더로 들어가므로, 값을 바꾸면 다시 배포해야 합니다.

## 업데이트와 롤백

| 바꾼 것 | 할 일 |
|---|---|
| 백엔드 코드 | `build-images.sh` → Portainer에서 스택 **Update**("Re-pull image" 켜기) |
| 프론트 코드 | `main`에 머지하면 Vercel이 배포. 롤백은 Vercel → Deployments → 이전 배포 **Promote** |
| 백엔드 롤백 | 스택 변수 `IMAGE_TAG`를 이전 커밋 해시로 바꾸고 Update. DB 마이그레이션은 앞으로만 가므로, 스키마를 바꾼 배포는 되돌리기 전에 확인 |
| 렐름 설정(`lms-realm.json`) | 가져오기는 **렐름이 없을 때만** 실행. 이미 있는 렐름은 관리 콘솔(`auth.bymile.site/admin`, Access 통과 후)에서 바꿈 |

## 리허설: 같은 스택을 이 PC에서

```bash
IMAGE_PREFIX=local LOAD=1 ./infra/home/build-images.sh
docker compose -p lms-rehearsal --env-file infra/home/rehearsal/rehearsal.env \
  -f infra/home/docker-compose.yml -f infra/home/rehearsal/compose.rehearsal.yml up -d
```

- 웹은 `KEYCLOAK_ISSUER=http://localhost:18080/realms/lms`, `API_URL=http://localhost:18081`, `REDIS_URL=redis://localhost:16379`, `KEYCLOAK_CLIENT_SECRET=rehearsal-web-secret`로 `pnpm dev`를 띄웁니다.
- 그다음 Playwright E2E를 그대로 돌립니다(2개 통과 확인함).
- 끝나면 `docker compose -p lms-rehearsal ... down -v`로 지웁니다. 로컬 개발용 `lms` 프로젝트와 볼륨이 분리되어 있습니다.
