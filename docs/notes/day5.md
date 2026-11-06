# Day 5 면접 설명 노트: 시드 데이터, E2E, 도메인·메일 배포 코드, 시연 준비

> 형식은 Day 1~4와 같다. 오늘은 기능보다 **"면접관 앞에서 5분 안에 보여줄 수 있는 상태"**를 만드는 날이었다.

## 0. 오늘 만든 것 한눈에

| 위치 | 파일 | 역할 |
|---|---|---|
| 백엔드 | `tenancy/internal/DemoSeeder.java` | 데모 회사 2개, 직원 24명, 코스 4개, 진도가 제각각인 수강, 수료증 |
| E2E | `frontend/e2e/**` | Playwright: 학습자가 레슨 → 퀴즈 → 수료, 관리자가 대시보드 확인 |
| 웹 | `apps/web/next.config.ts` | 개발 서버 무한 새로고침 수정, openid-client 단일 인스턴스 |
| 배포 | `bootstrap/main.tf` | `bymile.site`(가비아) Route 53 호스팅 영역, 네임서버 출력 |
| 배포 | `envs/demo/mail.tf` | SES 도메인 인증(DKIM 3개), DMARC |
| 배포 | `envs/demo/main.tf` | 구조화 로그(ECS JSON) |
| 문서 | `docs/09-interview-qa.md`, `docs/10-demo-script.md` | 예상 질문과 답, 5분 시연 대본 |

검증: 백엔드 **82개** 통과(한글 이름 순서 단위 테스트 2개 포함), Playwright **2개** 통과(학습 경로 전체가 실제 브라우저·Keycloak·API·DB를 지남), Terraform `validate` 두 스택 통과.

---

## 1. 시드 데이터: 왜 SQL로, 왜 RLS를 지나게

- **데모전자**(20명, 4개 부서: 개발·영업·인사·재무) + **데모물산**(4명). 회사가 둘이어야 "서로 안 보인다"를 화면으로 보여줄 수 있다.
- 수강 상태를 **완료(수료증 포함)·진행 중·미시작·마감 지남**으로 골고루 깔았다. 빈 대시보드는 아무것도 증명하지 못한다.
- Keycloak 계정은 Admin API로 만든다(`provisionUser`). 비밀번호는 로컬 전용 `Demo-2026-lms`(렐름 정책: 10자 이상).
- DB 쓰기는 **회사 컨텍스트 안에서** 한다(`TenantContext.runAs`). 시드도 RLS를 지난다. "시드는 관리자 권한으로 다 넣는다"가 아니라, 앱과 같은 길로 들어간다.
- **순서가 중요했다.** 처음에는 회사 행을 먼저 넣고 계정을 만들었는데, 비밀번호 정책 때문에 계정 생성이 실패하자 "회사가 이미 있음 → 건너뜀"이 되어 다시 돌릴 수 없었다. **계정 먼저(다시 돌려도 같은 계정을 재사용), 조직·회사 행은 마지막**으로 바꿨다. 실패해도 다음 실행을 막는 흔적이 남지 않는다.
- 실행: `./gradlew bootRun --args='--lms.seed=true --server.port=0'` → 끝나면 스스로 종료. 이미 있는 회사는 건너뛴다.

## 2. Playwright E2E: 무엇을, 왜 하나만

- 시나리오: 이메일 우선 로그인(Keycloak 화면 두 단계) → 내 교육 → 남은 레슨 읽기 → 퀴즈 제출 → "통과했습니다" + 수료번호. 그리고 관리자 대시보드.
- **목(mock) 없이** 로컬 스택 전체를 지난다. 단위·통합 테스트가 이미 규칙을 검증하므로, E2E는 "조각이 실제로 이어지는가" 하나만 본다. 많이 만들면 느리고 깨지기 쉽다(테스트 피라미드).
- 시연용 `learner@` 계정은 건드리지 않고 다른 직원 계정을 쓴다. 테스트가 시연 데이터를 소모하지 않게.

### 오늘 E2E가 찾아낸 문제 3개

| 증상 | 원인 | 해결 |
|---|---|---|
| 로그인 500 `"config" must be an instance of Configuration` | OIDC 설정 객체를 `globalThis`에 캐시하는데, Next가 라우트마다 openid-client를 따로 번들해서 **클래스가 라우트마다 다름** → `instanceof` 실패 | `serverExternalPackages: ["openid-client"]`: 서버에서 패키지를 번들하지 않고 Node가 한 번만 로드 |
| 모든 페이지가 1초마다 새로고침, 내비게이션 `ERR_ABORTED` | Next 16 개발 서버의 MCP 기능이 **로그를 `.next/dev/logs`에 쓰고, 그 쓰기를 파일 감시가 변경으로 보고** 다시 빌드 → 새로고침 → 요청 → 로그 → … | `experimental.mcpServer: false`. 파일 감시기(FileSystemWatcher)로 바뀌는 파일을 직접 찍어서 찾았다 |
| 테스트가 가끔 레슨을 건너뜀 | `isVisible()`은 **기다리지 않는다.** 페이지가 바뀌는 중에 확인해서 "링크 없음"으로 판단 | 화면의 "2/3 레슨" 숫자를, 페이지가 그려진 뒤(`expect(...).toBeVisible()`로 기다림) 읽어서 판단 |

- 면접에서: "E2E는 하나뿐인데 버그 두 개를 잡았습니다. 하나는 번들러가 라이브러리를 라우트마다 복제해서 생긴 `instanceof` 실패였고, 하나는 개발 서버가 자기 로그 파일을 보고 무한히 다시 빌드하던 문제였습니다."

### 스크린샷을 찍다가 찾은 문제: 한글 이름 순서

- README 이미지를 Storybook이 아니라 **실행 중인 앱에서 시드 데이터로** 찍었다(`frontend/e2e/screenshots/readme.spec.ts`, 시연 화면과 같은 그림).
- 찍어 보니 이름이 "하나 김", "도윤 이"로 나왔다. Keycloak의 `name` 클레임은 항상 "이름 성"으로 붙이는데, 한국 이름은 **성 + 이름, 띄어쓰기 없이** 읽는다.
- 로그인할 때마다 토큰으로 이름을 갱신하므로 시드만 고치면 다시 덮어쓰인다. 그래서 **둘 중 하나라도 한글이면 `family_name` + `given_name`으로 직접 조립**하고, 아니면 Keycloak 값을 그대로 쓴다("Jane Doe"). 시드도 같은 함수를 쓴다.
- 면접에서: "글로벌 IdP의 기본값이 한국 사용자에게 맞지 않는 사례였습니다. 이름 순서처럼 사소해 보이는 것이 HR 제품에서는 신뢰 문제라서, 화면으로 직접 확인하는 단계를 둔 게 도움이 됐습니다."

## 3. 도메인: 가비아에서 사고 Route 53이 답한다

- `bymile.site`는 가비아에서 샀다. **등록(소유)은 가비아, DNS 응답은 Route 53**으로 나눈다. ACM 인증서 검증, ALB·CloudFront 별칭, SES DKIM 레코드를 전부 Terraform으로 만들려면 DNS가 AWS에 있어야 한다.
- 호스팅 영역은 `envs/demo`가 아니라 **`bootstrap`에** 두었다. 시연 환경은 면접 후 `destroy`하는데, 호스팅 영역을 지웠다 다시 만들면 **네임서버 4개가 바뀌고**, 가비아에 다시 입력할 때까지 도메인이 죽는다. 그래서 오래 사는 스택 + `prevent_destroy`.
- 순서: `bootstrap apply` → 출력된 `name_servers` 4개를 가비아(My가비아 → 도메인 관리 → 네임서버 설정)에 입력 → 전파 확인(`nslookup -type=NS bymile.site`) → `envs/demo apply`. 전파 전에 apply하면 ACM 검증이 끝나지 않고 멈춘다.
- 비용: 호스팅 영역 월 $0.50 + 쿼리 소량.

## 4. 메일 발신 도메인 (SES)

- `no-reply@bymile.site`로 보내려면 SES가 이 도메인을 확인해야 한다. **Easy DKIM**: SES가 준 토큰 3개로 CNAME 3개를 만든다. 메일마다 DKIM 서명이 붙어 스팸함으로 덜 간다.
- **DMARC `p=none`**: 받는 쪽에 "DKIM이 맞는지 보고만 해 달라". 시연 중 설정 실수로 메일이 사라지는 일을 막는다. 운영이라면 보고서를 보고 `quarantine` → `reject`로 올린다.
- **SES 샌드박스**: 새 계정은 확인된 주소로만, 하루 200통. 시연 상대 주소를 확인하거나 프로덕션 접근을 신청한다(코드로 할 수 없어 문서로 남김).
- SMTP 자격 증명은 Terraform 바깥에서 SSM에 넣는다(상태 파일에 비밀을 남기지 않는 같은 원칙).

## 5. 관측성: 오늘 한 것과 미룬 것

- **한 것:** 로그 한 줄마다 `traceId`, `tenantId`(MDC). AWS에서는 `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`로 **JSON 로그**를 내보낸다. CloudWatch Logs Insights에서 `filter tenantId = "…"`로 한 회사의 요청만 모아 볼 수 있다. 에러 응답(RFC 9457)에도 `traceId`가 있어서 사용자가 보낸 화면 캡처에서 바로 로그를 찾는다.
- **한 것:** CloudWatch 알람(ALB 5xx, DLQ에 메시지), 월 예산 알림 — Day 3 Terraform.
- **미룬 것:** OpenTelemetry 수집기 → Grafana Cloud, Sentry. 이유: 계정·토큰이 필요하고 시연은 로컬. 붙이는 방법은 [09 질문 7](../09-interview-qa.md#7-관측성은)에 적었다.

## 6. 일부러 미룬 것 (면접에서 먼저 말한다)

| 항목 | 상태 | 이유 |
|---|---|---|
| CI/CD (GitHub Actions) | 미룸 | 저장소 토큰에 `workflow` 권한이 없어 올릴 수 없음. OIDC 역할 2개(plan / deploy)는 Terraform에 이미 있음 |
| 실제 AWS 적용 | 미룸 | 코드와 `validate`까지. 시연은 로컬. 적용 순서는 [Terraform README](../../infra/terraform/README.md) |
| 롤백 리허설 | 미룸 | 배포 환경이 필요. 회로 차단기(`rollback = true`)는 코드에 있음 |
| `docker compose --profile full` | 미룸 | 컨테이너 안에서 Keycloak 발급자 주소(`localhost:8080`)가 달라지는 문제를 풀어야 함. 지금은 API·웹을 호스트에서 실행 |
| Chromatic, SonarCloud | 미룸 | CI가 먼저 |

## 7. 면접에서 이렇게 말한다 (1분)

> "마지막 날에는 기능을 더 넣지 않고 시연할 수 있는 상태를 만들었습니다. 회사 두 개와 직원 스물네 명을 시드로 넣었는데, 시드도 앱과 똑같이 회사 컨텍스트 안에서 RLS를 지나게 했습니다. 그리고 로그인부터 수료까지 실제 Keycloak과 DB를 지나는 Playwright 테스트를 하나 만들었는데, 이게 로그인 500 에러와 개발 서버 무한 새로고침, 두 가지 버그를 찾아줬습니다. 배포 쪽은 가비아에서 산 도메인을 Route 53으로 위임하는 부분과 SES 도메인 인증까지 Terraform으로 썼고, 호스팅 영역은 지웠다 만들면 네임서버가 바뀌니까 오래 사는 bootstrap 스택에 따로 뒀습니다."
