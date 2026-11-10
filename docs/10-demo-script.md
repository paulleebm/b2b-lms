# 10. 시연 대본 (5분) + 아키텍처 1분 + 리허설 체크리스트

> 시연은 **로컬**에서 한다(AWS는 코드만, [14번 답](09-interview-qa.md#14-aws에-정말-배포했나요)). 데모 계정 비밀번호는 모두 `Demo-2026-lms`(로컬 전용).

## 0. 시작 전 (면접 30분 전)

```bash
docker compose -f infra/local/docker-compose.yml up -d
```

```bash
cd backend && ./gradlew bootRun
```

```bash
cd frontend && pnpm --filter web dev
```

**데이터를 처음 상태로 되돌리려면** (리허설 후, E2E를 여러 번 돌린 뒤): DB 볼륨을 지우고 다시 시드한다. Keycloak 렐름도 같은 DB에 있어 함께 초기화된다. Day 1~2에 손으로 만든 ACME 계정도 지워진다.

```bash
docker compose -f infra/local/docker-compose.yml down -v && docker compose -f infra/local/docker-compose.yml up -d
```

```bash
cd backend && ./gradlew bootRun --args='--lms.seed=true --server.port=0'
```

브라우저 창 두 개(일반 창 + 시크릿 창: 다른 사람으로 동시에 로그인), 탭: 앱 `localhost:3000`, Mailpit `localhost:8025`.

| 계정 | 역할 | 쓰는 장면 |
|---|---|---|
| `admin@demo-elec.test` | 데모전자 관리자 | 대시보드, 배정, 리마인더, 감사 로그 |
| `instructor@demo-elec.test` | 데모전자 강사 | 코스 스튜디오, 퀴즈 |
| `learner@demo-elec.test` | 데모전자 학습자 | 학습 → 퀴즈 → 수료 |
| `admin@demo-trade.test` | 데모물산 관리자 | 교차 테넌트 |

---

## 1. 5분 시연

| 시간 | 화면 | 할 일 | 말할 것 (한 문장) |
|---|---|---|---|
| 0:00 | 로그인 | `admin@demo-elec.test` 입력 → **다음** → 비밀번호 | "이메일을 먼저 받는 이유는 회사 도메인이면 그 회사 SSO로 보내기 위해서입니다. 인증은 Keycloak이고, 브라우저에는 토큰 대신 세션 쿠키만 있습니다." |
| 0:30 | **교육 현황** | KPI, 부서별 수료율, 처리가 필요한 직원(마감 지남 포함) | "이 숫자는 SQL 집계 한 번씩이고, 회사 ID를 키로 캐시합니다. 키에 회사가 빠지면 다른 회사 숫자가 보이는 사고가 나요." |
| 1:00 | 교육 현황 | 한 사람 **리마인더** → 다시 누르기(0건) → Mailpit 탭 | "메일은 요청 안에서 보내지 않습니다. 아웃박스 → 큐 → 워커. 하루 한 번만 가도록 DB 조건부 UPDATE로 막았습니다." |
| 1:40 | **교육 배정** | 코스 하나를 영업팀에 배정, 마감일 | "버튼을 두 번 눌러도 배정은 한 번입니다. 멱등 키를 업무 데이터와 같은 트랜잭션으로 저장해요." |
| 2:10 | (시크릿 창) 학습자 | `learner@`로 로그인 → 내 교육 → 이어서 학습하기 → 레슨 **다 읽었어요** | "영상이면 하트비트로 진도를 보내는데, 서버는 실제 경과 시간의 2배까지만 인정합니다. 개발자 도구로 '다 봤다'를 보내도 안 됩니다." |
| 2:50 | 퀴즈 | 일부러 틀림 → 불합격 → 다시 응시 → 통과 → **수료 표시** | "채점은 서버에서. 정답은 브라우저에 내려가지 않습니다. 수료·수료증·이벤트가 한 트랜잭션입니다." |
| 3:30 | 코스 화면 | **수료증 내려받기** (PDF) → 수료번호 복사 → `/verify/수료번호` | "PDF는 워커가 만들고, 진위 확인은 로그인 없이 됩니다. RLS를 끄지 않고 이름을 가린 한 건만 돌려주는 함수 하나만 열었어요." |
| 4:10 | 일반 창 | **감사 로그** | "배정·리마인더·수료가 누가 했는지와 함께 남고, 앱 계정은 이 표를 고칠 권한이 없습니다." |
| 4:30 | 교차 테넌트 | 로그아웃 → `admin@demo-trade.test` → 교육 현황(4명만) | "같은 DB, 같은 테이블인데 데모물산은 데모전자를 전혀 못 봅니다. 앱과 DB 두 겹으로 막고 테스트로 증명합니다." |
| 4:50 | 터미널 | `./gradlew test --tests '*TenantIsolationIT' --tests '*RowLevelSecurityIT'` (미리 돌려둔 결과) | "다른 회사 리소스는 404, 직접 SQL도 0행입니다." |

**시간이 남으면:** 코스 스튜디오에서 퀴즈 편집(두 탭에서 동시에 저장 → 두 번째는 412, ETag/If-Match), 멤버 초대 → Mailpit 초대 메일.

**시간이 부족하면 빼는 순서:** 배정(1:40) → 감사 로그(4:10) → 리마인더 재클릭.

---

## 2. 아키텍처 1분

> "브라우저는 Next.js BFF만 압니다. BFF가 Keycloak과 OIDC로 로그인하고 토큰은 Redis에 둡니다. API는 Spring Boot 모듈러 모놀리스이고, 요청마다 토큰 서명을 검증한 뒤 회사와 역할은 우리 DB의 현재 멤버십으로 결정합니다. 격리는 Hibernate가 회사 조건을 붙이고, PostgreSQL RLS가 최종 방어합니다.
> 느린 일(수료증 PDF, 메일, 영상 변환 완료 처리)은 아웃박스로 SQS에 보내고 워커가 처리합니다. 같은 메시지가 두 번 와도 한 번만 처리하고, 다섯 번 실패하면 DLQ로 갑니다.
> AWS 구성은 ECS Fargate, RDS, ElastiCache, SQS, S3·CloudFront, MediaConvert, SES이고 Terraform으로 썼습니다. 비용 때문에 적용은 하지 않았고, 로컬은 같은 프로토콜의 대체물로 돌립니다."

그림이 필요하면 [README 아키텍처](../README.md#아키텍처)와 [03 아키텍처](03-architecture.md)를 띄운다.

## 3. "가장 어려웠던 결정" 3분

[09 질문 17](09-interview-qa.md#17-가장-어려웠던-결정은): 멤버십을 토큰이 아니라 DB에서 확인한 이유. 순서: **상황**(토큰에 역할이 있다) → **문제**(퇴사·역할 변경이 5분간 반영 안 됨) → **선택지**(토큰 수명 단축 / 토큰 폐기 목록 / DB 확인) → **결정과 대가**(요청마다 조회 1회, 대신 즉시 반영) → **증거**(`MemberManagementIT`: "비활성화된 멤버는 바로 403" — 토큰이 아직 유효해도).

---

## 4. 리허설 체크리스트

- [ ] `docker compose -f infra/local/docker-compose.yml ps`: 6개 모두 running (postgres, valkey, keycloak, mailpit, s3mock, elasticmq)
- [ ] API 로그에 `SQS worker started for queues`(워커가 큐를 듣는 중) — 없으면 수료증·메일이 안 나온다
- [ ] `learner@demo-elec.test`에 **진행 중인 코스가 있다** — 없으면 위 "처음 상태로" 절차
- [ ] Mailpit 메일함 비우기(오래된 메일이 섞이면 헷갈린다)
- [ ] E2E 한 번: `frontend/e2e/node_modules/.bin/playwright test --config frontend/e2e/playwright.config.ts` (시연 계정은 건드리지 않는다)
- [ ] 브라우저 확대 125%, 알림 끄기, 다른 탭 닫기
- [ ] 교차 테넌트 테스트를 미리 한 번 돌려 결과 화면을 터미널에 남겨 두기
- [ ] 네트워크가 끊겨도 된다: 전부 로컬. 단 Docker 이미지가 미리 받아져 있어야 한다
