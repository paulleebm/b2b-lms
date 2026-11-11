# AWS 배포 (Terraform) — 코드와 설계

> **현재 상태:** 코드와 설계만 있다. **실제로 적용(apply)하지 않았고, 시연은 로컬(Docker Compose)에서 한다.**
> AWS 계정이 생기면 아래 순서대로 적용하면 된다. 검증한 것과 못 한 것은 맨 아래에 따로 적었다.

설계 배경은 [docs/03 §2](../../docs/03-architecture.md)(네트워크·보안 그룹·ECS), [docs/02 D4](../../docs/02-tech-decisions.md)(NAT를 뺀 이유), [docs/08](../../docs/08-cost-and-limits.md)(비용)에 있다. 이 문서는 그 설계가 **어떤 코드가 되었는지**를 설명한다.

## 1. 구조

```
infra/terraform/
├─ bootstrap/            계정당 한 번: 상태 저장 버킷, GitHub Actions용 OIDC 역할 2개, Route 53 호스팅 영역
├─ envs/demo/            시연 환경: 아래 모듈을 조립한다
│   ├─ versions.tf       프로바이더(서울 + CloudFront 인증서용 us-east-1), S3 백엔드
│   ├─ main.tf           모듈 조립, 보안 그룹, 비밀값, ECR, ECS 서비스(web·api·worker·keycloak)
│   ├─ mail.tf           SES 도메인 인증(DKIM 3개) + DMARC
│   ├─ backend.hcl.example / demo.tfvars.example
└─ modules/
    ├─ network/          VPC, 퍼블릭·프라이빗 서브넷 ×2 AZ, S3 게이트웨이 엔드포인트 (NAT 없음)
    ├─ edge/             ALB + ACM + Route 53. app.→web, auth.→Keycloak, /admin 차단
    ├─ data/             RDS PostgreSQL 17, ElastiCache Valkey 8 (프라이빗, 암호화, TLS 강제)
    ├─ storage/          미디어 S3 + CloudFront(서명 쿠키, 영상 경로만) + us-east-1 인증서
    ├─ async/            SQS(+DLQ) 2개, MediaConvert 큐·역할, EventBridge 규칙
    ├─ ecs-service/      Fargate 서비스 공통 틀 (web·api·keycloak·마이그레이션 태스크)
    └─ observability/    SNS 메일, ALB 5xx·DLQ 알람, 월 예산 알림
```

이미지 빌드 파일: `backend/Dockerfile`, `frontend/apps/web/Dockerfile`, `infra/keycloak/Dockerfile`.

## 2. 요청이 지나가는 길 (배포 후)

```
브라우저 ─ https://app.도메인 ─ ALB(TLS) ─ web(Next.js BFF) ─ Service Connect ─ api(Spring) ─ RDS / Valkey
         ─ https://auth.도메인 ─ ALB ─ Keycloak ─ RDS(keycloak DB)
         ─ https://media.도메인 ─ CloudFront(서명 쿠키 검사) ─ S3(HLS 조각)
         ─ 업로드: S3로 직접 (presigned PUT / multipart)
```

- **API에는 공개 주소가 없다.** ALB 규칙에 api가 없고, api 보안 그룹은 web 보안 그룹에서 오는 8081만 받는다. 브라우저는 BFF만 알고, 토큰도 BFF 서버에만 있다.
- **데이터 저장소는 인터넷 경로가 아예 없는** 프라이빗 서브넷에 있다(라우팅 테이블에 0.0.0.0/0이 없다).

## 3. 설계 결정 (후보 → 선택 → 이유)

| 주제 | 후보 | 선택 | 이유 |
|---|---|---|---|
| 상태 잠금 | DynamoDB 테이블, **S3 네이티브 잠금** | `use_lockfile = true` | Terraform 1.10부터 S3만으로 잠금이 된다. 테이블 하나와 그 권한을 관리할 필요가 없다 |
| CI 인증 | IAM 사용자 액세스 키, **GitHub OIDC** | OIDC 역할 2개 | 저장소에 오래 사는 키가 없다. PR은 읽기 전용(plan), main(production 환경)은 이미지 푸시·ECS 배포만 |
| 인프라 적용 주체 | CI 자동 apply, **사람이 apply** | 사람 | 배포 역할에 IAM·네트워크 권한을 주지 않으려고. PR에서 plan을 보고 사람이 적용한다 |
| 네트워크 | 프라이빗 + NAT, **퍼블릭 + 보안 그룹** | 퍼블릭(시연) | NAT는 켜두기만 해도 월 $40 이상. 보안 그룹이 ALB에서 오는 것만 받으므로 들어오는 경로는 같다. 운영은 프라이빗 + NAT/엔드포인트 (D4) |
| 서비스 간 호출 | 내부 ALB, Cloud Map DNS, **Service Connect** | Service Connect | `http://api:8081`처럼 이름으로 부르고, 재시도·지표가 붙는다. 내부 ALB 비용(월 $20)이 없다 |
| 컴퓨팅 | EKS, EC2, **Fargate** | Fargate (ARM64) | 서버·클러스터 관리가 없다. EKS는 클러스터만 월 $73. Graviton은 x86보다 vCPU당 약 20% 싸다 |
| 비밀값 | 태스크 정의 환경변수, Secrets Manager, **SSM SecureString** | SSM (+ RDS 마스터는 Secrets Manager) | 태스크가 시작할 때 주입되어 태스크 정의·이미지에 남지 않는다. SSM 표준 파라미터는 무료. RDS 마스터 비밀번호는 RDS가 직접 관리·교체하도록 Secrets Manager |
| DB 계정 | 앱이 마스터 계정 사용, **역할 분리** | lms_owner / lms_app / keycloak | 앱(lms_app)은 테이블 소유자가 아니라 RLS를 우회할 수 없다. 마이그레이션은 owner 계정을 가진 **일회성 태스크**만 쓴다 |
| 영상 보호 | 서명 URL, **서명 쿠키** | CloudFront 서명 쿠키 | HLS는 재생 목록 + 수백 개 조각. 조각마다 URL을 서명할 수 없다. 쿠키 하나에 "이 영상 폴더, 2시간" 정책을 담는다 |
| 캐시 | ElastiCache Redis OSS, **Valkey** | Valkey 8 | Redis 호환, 오픈 라이선스, ElastiCache에서 Redis OSS보다 저렴 |
| 이미지 태그 | latest, **커밋 해시 + 불변 태그** | IMMUTABLE | 같은 태그는 항상 같은 이미지라서 롤백이 정확하다 |
| 배포 실패 | 수동 롤백, **회로 차단기** | `deployment_circuit_breaker { rollback = true }` | 새 태스크가 헬스체크를 계속 실패하면 ECS가 이전 버전으로 되돌린다 |
| 큐 소비자 | api 안에서 같이, 별도 이미지, **같은 이미지 + 다른 설정** | worker 서비스 (`WORKER_ENABLED=true`, 인바운드 포트 없음) | 코드는 하나, 부하는 분리. api는 큐를 듣지 않아(`false`) 요청 지연이 PDF 생성에 영향받지 않는다 |
| DNS | 가비아 DNS에 레코드 직접, **Route 53으로 위임** | 가비아 = 등록, Route 53 = 응답 | ACM 검증·ALB·CloudFront 별칭·SES DKIM을 전부 Terraform으로. 호스팅 영역은 `bootstrap`에(지웠다 만들면 네임서버가 바뀐다) |
| 로그 | 텍스트, **JSON(ECS 형식)** | `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` | `traceId`·`tenantId`가 필드라서 Logs Insights에서 정규식 없이 거른다 |

## 4. 적용 순서 (계정이 생기면)

1. **bootstrap** (관리자 권한으로 한 번)
   ```bash
   cd infra/terraform/bootstrap
   terraform init && terraform apply -var state_bucket_name=lms-tfstate-<계정ID>
   ```
2. **도메인 위임 (가비아 → Route 53)**: `terraform output name_servers`의 4개 값을 가비아(My가비아 → 도메인 관리 → `bymile.site` → 네임서버 설정 → 1~4차에 입력)에 넣는다. `nslookup -type=NS bymile.site`가 `awsdns` 주소를 돌려줄 때까지 기다린다(보통 수십 분, 길면 하루). **전파 전에 다음 단계를 적용하면 ACM 인증서 검증이 끝나지 않고 멈춘다.**
3. **백엔드·변수 파일**: `envs/demo/backend.hcl.example`, `demo.tfvars.example`를 복사해 채운다(도메인은 이미 `bymile.site`).
4. **CloudFront 서명 키 쌍**을 로컬에서 만든다 (`openssl`, tfvars 예시 참고). 공개키만 tfvars에 넣는다.
5. **ECR이 먼저 있어야 이미지를 올릴 수 있다**:
   ```bash
   cd ../envs/demo
   terraform init -backend-config=backend.hcl
   terraform apply -var-file=demo.tfvars -target=aws_ecr_repository.this
   ```
   그다음 세 이미지를 `linux/arm64`로 빌드해 `bootstrap` 태그로 푸시한다.
6. **전체 적용**: `terraform apply -var-file=demo.tfvars`
7. **적용 뒤 손으로 하는 일** (Terraform에 두지 않은 이유: 비밀값이 상태 파일에 남지 않게, 또는 DB 안의 일이라서)
   - CloudFront 개인키: `aws ssm put-parameter --name /lms/demo/cloudfront-private-key --type SecureString --overwrite --value file://cf-private.pem`
   - **SES**: 콘솔에서 SMTP 자격 증명을 만들어 `/lms/demo/ses-smtp-username`, `-password`에 넣는다. 새 계정은 **샌드박스**(확인된 주소로만, 하루 200통)라서 시연 받는 주소를 확인하거나 프로덕션 접근을 신청한다. 도메인 인증(DKIM)은 `mail.tf`가 레코드를 만들어 자동으로 끝난다.
   - **DB 역할 만들기**: RDS 마스터 계정으로 `lms_owner`, `lms_app`, `keycloak` 역할과 `lms`, `keycloak` DB를 만든다. 비밀번호는 SSM `/lms/demo/db-*` 값. SQL은 로컬용 `infra/local/postgres/init/01-roles.sql`과 같고 비밀번호만 다르다.
   - **Keycloak 렐름**: 로컬 `lms-realm.json`은 localhost 주소와 개발용 시크릿을 담고 있어 그대로 올리지 않는다. 주소를 `app.도메인`으로, 클라이언트 시크릿을 SSM 값으로 바꾼 렐름을 가져온다(운영이라면 keycloak-config-cli로 코드화).
8. **마이그레이션 → 배포**: `aws ecs run-task --task-definition lms-demo-migrate ...`가 성공하면 서비스를 새 이미지로 롤링 배포한다. CI가 붙으면 이 순서를 워크플로가 한다.
9. **면접이 끝나면** `terraform destroy -var-file=demo.tfvars`. RDS 삭제 보호는 시연 기본값에서 꺼져 있다. `bootstrap`(상태 버킷, 호스팅 영역)은 남긴다: 다시 올릴 때 가비아 설정을 바꿀 필요가 없다.

## 5. 로컬과 AWS의 차이

| | 로컬 (지금 시연) | AWS (이 코드) |
|---|---|---|
| 객체 저장소 | S3Mock (서명 검증 안 함) | S3 (서명·만료·Content-Length 강제) |
| 영상 변환 | 가짜 변환기 (원본 mp4를 그대로 재생) | MediaConvert → HLS 3화질, 완료는 EventBridge → SQS → worker |
| 영상 재생 | presigned GET (mp4) | CloudFront + 서명 쿠키 (HLS) |
| 메일 | Mailpit | SES (SMTP, DKIM 서명, 발신 `no-reply@bymile.site`) |
| 큐 | ElasticMQ | SQS (+DLQ, 5번 실패 시 이동) |
| 큐 소비자 | API 프로세스 안의 워커 | worker 서비스 (같은 이미지, `WORKER_ENABLED=true`) |
| 비밀값 | `*_dev` 개발값 | SSM SecureString, RDS 마스터는 Secrets Manager |
| 마이그레이션 | API가 시작할 때 Flyway | 일회성 ECS 태스크 (`--lms.migrate-only=true`) |
| TLS | 없음 (localhost) | ALB·CloudFront에서 TLS 1.2+, RDS `rds.force_ssl`, Valkey 전송 암호화 |

## 6. 비용

[docs/08](../../docs/08-cost-and-limits.md)의 표와 같은 구성이다(서울, 24시간 가동, 월 약 $140~150, worker 포함). 여기에 Route 53 호스팅 영역 월 $0.50, 도메인(가비아) 연 비용이 따로 든다. 예산 알림(80% 예측, 100% 실제)이 `observability` 모듈에 있다.

## 7. 검증한 것과 못 한 것

**검증함**
- `terraform fmt -recursive`, `terraform init -backend=false && terraform validate` — `bootstrap`, `envs/demo` 모두 통과 (Terraform 1.17.1, AWS 프로바이더 6.72, Docker 이미지 `hashicorp/terraform`으로 실행)
- 세 Dockerfile 로컬 빌드 (결과는 `docs/notes/day3.md`)

**못 함 (AWS 계정이 필요)**
- `terraform plan`/`apply`: 실제 리소스 생성, ACM 검증, Route 53 레코드, 가비아 네임서버 위임 확인, SES DKIM 검증
- IAM 정책이 실제로 충분한지(부족하면 런타임에 AccessDenied로 드러난다) — 적용 후 각 서비스 기능을 한 번씩 돌려 봐야 한다
- CloudFront 서명 쿠키가 실제 CloudFront에서 통과하는지 (발급 코드와 정책 형식은 단위 테스트로 검증, 실제 키 쌍·배포가 필요)
- MediaConvert 작업이 실제로 성공하는지 (작업 설정과 완료 이벤트 처리는 테스트로 검증)
