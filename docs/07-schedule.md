# 07. 5일 실행 계획 (Spring + AWS, 범위 축소)

## 원칙과 이유

| 원칙 | 이유 |
|---|---|
| **백엔드 기능과 그 화면을 같은 날** 만든다 | 마지막 날 통합 문제가 한꺼번에 터지는 것을 막는다 |
| **CI는 Day 1, AWS 배포 골격은 Day 2** (Walking Skeleton) | 인프라 문제(도메인, 인증서, Keycloak 주소, 쿠키)는 마지막 날 발견하면 고칠 시간이 없다 |
| **Must를 끝까지 먼저**, Should는 그다음 | 시연은 "핵심 흐름 100%"가 "전부 70%"보다 낫다 |
| **매일 끝에 면접 설명 노트** | Spring이 처음이라 더 중요하다. 설명할 수 없는 코드는 내 코드가 아니다 |
| **기능마다 브랜치 → PR → CI → 머지** | PR 목록 자체가 작업 방식의 증거가 된다 |
| **개발은 로컬, AWS는 배포 확인용** | 개발 중 AWS 비용과 대기 시간을 줄인다 |

---

## 시작 전에 직접 해야 하는 일 (계정·결제는 본인만 가능)

| 언제까지 | 할 일 | 비고 |
|---|---|---|
| Day 1 전 | **JDK 21** 설치(Temurin 등), IntelliJ IDEA(Community 무료) | Spring 개발 표준 IDE |
| Day 1 전 | GitHub 새 공개 저장소 | 공개여야 CodeQL·SonarCloud가 무료 |
| Day 1 전 | **AWS 계정** 생성 → **루트 MFA** → **예산 알림($30, $80, $150)** → IAM Identity Center 사용자 | 2025-07-15 이후 가입 계정은 크레딧 지급. **기존 계정이 있다면 알려주세요**(조건이 다름) |
| **Day 2 오전** | **도메인 구매**(.com 또는 .dev) → Route 53 호스팅 영역 | 다른 곳에서 샀다면 네임서버를 Route 53으로 변경 (반영에 몇 시간) |
| Day 2 오전 | SES에서 도메인 인증 + 데모용 메일 주소 인증 | 샌드박스 상태로 시연 |
| Day 3 전 | Chromatic, Grafana Cloud, Sentry 가입 (모두 무료 구간) | GitHub 계정으로 가입 가능 |

---

## Day 1 — 뼈대, 인증 연결, 멀티테넌트 격리
> 목표: **"A회사 토큰으로 B회사 데이터를 못 본다"를 테스트로 증명한다.**

**공통**
- [ ] 모노레포 구조, `infra/local/docker-compose.yml` (postgres, redis, keycloak, mailpit)
- [ ] Keycloak 렐름 설정: Organizations 켜기, `lms-web`(BFF, confidential) · `lms-api` 클라이언트, Refresh 회전, 렐름 JSON 내보내기
- [ ] GitHub Actions CI 골격 (backend: Gradle build·test, frontend: lint·typecheck·build)

**백엔드**
- [ ] Spring Boot 프로젝트(Initializr), Modulith 패키지 구조, 가상 스레드, `open-in-view: false`
- [ ] Flyway: 전체 스키마 초안, **DB 계정 분리 + RLS 정책**, `verify_certificate` 함수
- [ ] Resource Server 설정, **TenantContextFilter**(조직 → 테넌트, 멤버십 → 권한)
- [ ] Hibernate `@TenantId` + **RLS 커넥션 공급자**(획득 시 설정, 반납 시 reset)
- [ ] ProblemDetail 전역 처리, `/v1/me`(JIT 사용자 생성), `POST /v1/tenants`(Keycloak 조직 생성 + 보상 작업)
- [ ] 테스트: Testcontainers — **교차 테넌트 404**, **RLS 직접 SQL 차단**, **커넥션 반납 후 tenant 초기화**, Modulith 경계 검증

**프론트**
- [ ] Next.js, Tailwind, `packages/ui`(shadcn 기초 컴포넌트) + **Storybook** 설정
- [ ] **BFF 로그인**: `/auth/login`, `/auth/callback`, `/auth/logout`, Redis 세션, 미들웨어 보호
- [ ] 회사 등록 화면, 조직 선택 화면

**📝 면접 노트:** OIDC 인가 코드 + PKCE 흐름, BFF를 고른 이유, Spring Security 필터 체인, `@TenantId` + RLS, 커넥션 풀 함정

---

## Day 2 — 조직, 콘텐츠, AWS 배포 골격
> 목표: **관리자가 직원을 초대하고, 강사가 영상 레슨을 게시한다. 운영 URL에서 로그인된다.**

**백엔드**
- [ ] 부서, 멤버(역할·부서 변경, 비활성화 → 캐시 삭제), 초대(Keycloak 조직 초대 + INVITED 멤버십)
- [ ] 코스·섹션·레슨 CRUD, `@Version` 낙관적 락, 게시 검증, 소유권 검사
- [ ] 파일: 작은 파일은 Presigned PUT, **영상은 S3 Multipart 업로드**(시작·조각 URL·완료·취소), HeadObject 재검증, S3 CORS(`ETag` 노출), 미완료 업로드 수명 주기 규칙
- [ ] `video_asset` + `VideoTranscoder` 인터페이스 — 오늘은 **가짜 구현**(즉시 READY, 원본 mp4를 재생 주소로)으로 게시 흐름을 먼저 완성
- [ ] springdoc → `packages/api-client` 타입 생성 + CI 불일치 검사

**프론트**
- [ ] 역할별 레이아웃, 관리자: 멤버·부서·초대 화면
- [ ] 강사 스튜디오: 코스 편집기, **분할 업로드 컴포넌트**(조각 병렬 전송, 진행률, 실패 조각 재시도, 취소) + 상태별 스토리(업로드 중·변환 중·실패·완료)

**AWS 배포 골격 (Terraform)**
- [ ] 상태 저장용 S3 버킷, `modules/network`, `rds`, `elasticache`, `ecs-service`, `alb`, `s3-cloudfront`
- [ ] Route 53 + ACM, ECR, SSM 파라미터, GitHub OIDC 역할
- [ ] Dockerfile(api, web, keycloak 최적화 빌드), 첫 배포
- [ ] ✅ **`app.도메인`에서 회사 등록 → 로그인 → 새로고침해도 세션 유지**

**📝 면접 노트:** Terraform 상태 관리, 보안 그룹 설계, NAT를 뺀 이유, Presigned URL과 Multipart 업로드, 낙관적 락

---

## Day 3 — 학습의 핵심: 배정, 진도, xAPI, 퀴즈, 수료
> 목표: **시나리오 5~7이 끝까지 돈다.**

**백엔드**
- [ ] 배정: 부서·개인, `ON CONFLICT` 수강 생성(더 이른 마감일), 신규 입사자 동기화, 멱등 키
- [ ] 레슨 조회(수강 확인 + **CloudFront 서명 쿠키**), 진도 하트비트 + **시청 시간 인정 규칙**(영상 길이는 `video_asset`에서만)
- [ ] **xAPI 기록**(`records` 모듈): launched, progressed(25% 단위), completed, attempted, passed/failed
- [ ] 퀴즈: 문항 편집, 응시 조건, 서버 채점, 조건부 UPDATE, **수료 + certificate 행 + 이벤트를 한 트랜잭션**
- [ ] 단위 테스트: 인정 시간 계산, 채점, 수료 판정 (경계값)

**프론트**
- [ ] 학습자 홈(서버 컴포넌트), 코스 상세, **영상 플레이어**(hls.js로 HLS 재생, mp4도 지원, 화질 표시, 이어보기, 하트비트), 문서 뷰어
- [ ] 퀴즈 응시·결과, 강사 퀴즈 편집기, 배정 화면

**📝 면접 노트:** 배정과 수강을 분리한 이유, DB 제약으로 동시성 해결, xAPI를 쓴 이유, 부정 시청 방지의 한계, JPA N+1을 어디서 어떻게 막았는지

---

## Day 4 — 관리자 가치와 비동기: 대시보드, 수료증, 감사 로그
> 목표: **돈 내는 사람(HR)이 보는 화면과, 비동기 처리의 표준 패턴을 완성한다.**

**백엔드**
- [x] 대시보드: `JdbcClient` 집계 SQL, Redis 캐시(tenantId 키)
- [x] **아웃박스 → SQS → worker**: Modulith 이벤트 레지스트리, SQS 발행, worker 프로필, DLQ
- [x] 수료증: Thymeleaf + OpenHTMLtoPDF(한글 폰트), S3 저장, 다운로드, 공개 진위 확인
- [x] **영상 변환 (Should)**: MediaConvert 작업 템플릿(HLS 1080p·720p·480p, 썸네일) + 전용 IAM 역할을 Terraform으로, `VideoUploaded` → SQS → 워커 `CreateJob`, **EventBridge 규칙 → SQS → 완료·실패 처리**, 오래 걸리는 작업 점검 예약 작업
- [x] 메일(SES / 로컬 Mailpit): 배정 알림, 리마인더
- [x] 감사 로그(이벤트 구독), 예약 작업(`@Scheduled` + ShedLock)

**프론트**
- [x] 대시보드(KPI, 부서별 차트, 미이수자 표, 리마인더 버튼), 내 수료증, 진위 확인 페이지
- [x] **Storybook 보강**: 상태별 스토리·인터랙션 테스트 (98개). MSW·Chromatic은 미룸 (화면 조립 컴포넌트가 props만 받아 목이 필요 없음, Chromatic은 CI 이후)

**📝 면접 노트:** 아웃박스 패턴, 최소 한 번 전달과 멱등성, DLQ, 캐시 키 격리, iText 라이선스 이슈, 영상을 변환하는 이유(호환성·ABR·트래픽), 폴링 대신 이벤트, HLS에 서명 쿠키를 쓰는 이유

---

## Day 5 — 운영 품질과 발표 준비
> 목표: **면접관이 링크 하나로 모든 걸 확인할 수 있는 상태.**

**DevOps**
- [ ] CD 완성: main 머지 → Trivy → ECR → Flyway 태스크 → ECS 롤링 + 회로 차단기 — **미룸**(저장소 토큰 `workflow` 권한). OIDC 역할·회로 차단기·마이그레이션 태스크는 Terraform에 있음
- [ ] **롤백 리허설** — 미룸 (AWS 적용 안 함)
- [~] CloudWatch 알람(5xx, DLQ, 예산)과 JSON 로그(ECS 형식)는 코드에 있음. OpenTelemetry·Sentry는 미룸
- [ ] CodeQL, Dependabot, SonarCloud 연결 — 미룸 (CI와 함께)
- [ ] 노트북 백업 시연 모드(`docker compose --profile full up`) — 미룸 (컨테이너 안 Keycloak 발급자 주소 문제). API·웹은 호스트에서 실행

**데이터·문서**
- [x] 시드: 데모 회사 2개(교차 테넌트 시연), 계정 3종, 코스 3개, 다양한 진도의 직원 20명
- [x] README: 아키텍처 그림, 기술 선택 요약(→ docs), **비용 표**, 데모 계정, 로컬 실행법
- [x] `docs/09-interview-qa.md`: 예상 질문과 답

**리허설**
- [x] 5분 시연 대본: [10-demo-script.md](10-demo-script.md) — 로그인(이메일 우선) → 초대 → 영상 업로드·변환 → 코스 게시 → 배정 → 학습 → 퀴즈 → 수료증 → 대시보드 → 교차 테넌트 테스트 실행
- [x] 1분 아키텍처 설명, "가장 어려웠던 결정" 3분 이야기 (같은 문서)

**Should가 남아 있으면 이 순서로:** 관리자 OTP → SSO 시연(두 번째 렐름) → Back-Channel Logout → Playwright E2E 1개 (**E2E는 했다**: 2개)

---

## 일정이 밀리면 이 순서로 줄인다

1. Should 전부 (SSO 시연, OTP, Back-Channel Logout, Playwright)
2. Grafana Cloud·Sentry 연동 → CloudWatch 로그만 유지
3. 영상 변환 → **후퇴안**: H.264 mp4만 허용, 원본을 서명 URL로 재생, 길이는 워커가 파일 헤더에서 추출 (Multipart 업로드는 유지)
4. 수료증 PDF → 수료 상태 표시만 (아웃박스 → SQS 흐름은 **메일 알림으로 유지**)
5. 리마인더 예약 작업
6. 감사 로그 화면 (기록은 유지)

**절대 줄이지 않는 것:** 멀티테넌트 격리와 그 테스트, BFF 로그인, 배정 → 학습 → xAPI → 수료 흐름, Terraform 배포와 CI/CD.

## 솔직한 위험 평가

| 위험 | 가능성 | 대응 |
|---|---|---|
| Spring·JPA 학습 시간 초과 | **높음** | Day 1에 인증·격리만 확실히. 막히면 바로 범위 축소 순서를 적용 |
| Keycloak Organizations 설정에서 막힘 | 중간 | Day 1 오전에 가장 먼저 검증. 안 되면 렐름 단일 + 그룹 방식으로 대체 |
| MediaConvert·EventBridge 연결에서 막힘 | 중간 | 가짜 변환기로 나머지 흐름은 완성해 둔 상태라 영향이 작다. 막히면 후퇴안(H.264 mp4만 허용) |
| AWS 첫 배포가 오래 걸림 | 중간 | Day 2에 골격만 먼저. 안 되면 Day 5까지 로컬 시연으로 대체 가능 |
| 크레딧 조건이 예상과 다름 | 낮음~중간 | 예산 알림, 시연 전후로 `terraform destroy` |
