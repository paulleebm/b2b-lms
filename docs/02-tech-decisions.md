# 02. 기술 선택 기록 — 왜 이걸 골랐고, 무엇을 포기했나

> 형식은 현업의 **ADR(Architecture Decision Record)** 을 가볍게 따른다.
> 각 결정은 **후보 → 고른 것 → 이유 → 포기한 것 → 면접 한 줄** 순서다.
> 원칙은 **업계 표준**이다. 표준에서 벗어난 곳은 반드시 ⚠️ 표시와 이유를 적는다.

## 전체 요약

| 영역 | 선택 | 대표 대안 |
|---|---|---|
| 백엔드 언어 | **Java 21 LTS** | Kotlin, TypeScript, Go |
| 프레임워크 | **Spring Boot 4.x** | Spring Boot 3.x, Quarkus, NestJS |
| 빌드 | Gradle (Kotlin DSL) | Maven |
| 저장소 | 모노레포 (Gradle 백엔드 + pnpm 프론트) | 폴리레포 |
| 구조 | 모듈러 모놀리스 + **Spring Modulith** | 마이크로서비스 |
| DB | PostgreSQL (RDS) | MySQL, Aurora |
| 데이터 접근 | Spring Data JPA + JdbcClient(집계) | MyBatis, jOOQ, QueryDSL |
| 마이그레이션 | Flyway | Liquibase, `ddl-auto` |
| 멀티테넌시 | 공유 스키마 + **Hibernate `@TenantId`** + **Postgres RLS** | DB 분리, 스키마 분리 |
| 인증 서버 | **Keycloak** (Organizations) | Auth0, Cognito, WorkOS, 직접 구현 |
| 웹 토큰 처리 | **BFF** (Next.js 서버 + Redis 세션) | 브라우저 토큰 보관(SPA), Spring Cloud Gateway BFF |
| API 인증 | Spring Security OAuth2 Resource Server | 직접 JWT 필터 |
| 인가 | 앱 DB의 멤버십 역할 + `@PreAuthorize` | Keycloak 역할, OPA |
| 에러 형식 | **RFC 9457 Problem Details** | 자체 형식 |
| API 계약 | springdoc-openapi → TS 타입 생성 | 수동 타입, GraphQL |
| 비동기 | **트랜잭셔널 아웃박스**(Modulith 이벤트 레지스트리) + **SQS** | Kafka, RabbitMQ |
| 예약 작업 | `@Scheduled` + ShedLock | EventBridge Scheduler, Quartz |
| 캐시·세션·Rate Limit | Redis (ElastiCache Valkey) + Bucket4j | 인메모리 |
| 파일 | **S3 + CloudFront 서명 URL** | Cloudflare R2 |
| 대용량 업로드 | **S3 Multipart 업로드** | Presigned PUT 한 번, tus |
| 영상 변환·재생 | **MediaConvert → HLS** + CloudFront 서명 쿠키 | ffmpeg 워커, Mux·Kollus, 원본 mp4 |
| PDF | OpenHTMLtoPDF | iText, JasperReports |
| 메일 | Amazon SES | Resend, SendGrid |
| 학습 기록 | **xAPI** 문장 구조 (내부 LRS) | 자체 이벤트 형식 |
| 테스트 | JUnit + Testcontainers + Modulith 검증 + Playwright | H2 메모리 DB |
| 관측성 | **OpenTelemetry** → Grafana Cloud, Sentry, CloudWatch | Datadog |
| 프론트 | Next.js (App Router, 서버 컴포넌트) | Vite SPA |
| UI | Tailwind v4 + 자체 디자인 시스템(`@lms/ui`, Radix 기반) + **DESIGN.md** | shadcn 기본 테마, MUI, Ant Design |
| 디자인 테스트 | Storybook + **Vitest 애드온**(상호작용·axe) + 대비 검사 스크립트, (Should) Chromatic | Ladle |
| 클라우드 | **AWS** (서울 리전) | NCP, GCP, 미니PC |
| IaC | **Terraform** | AWS CDK, CloudFormation, Pulumi |
| 컨테이너 실행 | **ECS Fargate** | EKS, EC2 |
| 배포 | GitHub Actions(OIDC) → ECR → ECS 롤링 + 자동 롤백 | Argo CD, CodePipeline |
| 비밀값 | SSM Parameter Store | Secrets Manager |
| 브랜치 | GitHub Flow + Conventional Commits | Git Flow |

---

## A. 언어와 프레임워크

### A1. 언어 — Java 21 LTS
| 후보 | 장점 | 단점 |
|---|---|---|
| **Java 21** ✅ | 국내 백엔드 표준, 자료·인력 최다, **가상 스레드** | 문법이 장황함 |
| Java 25 (최신 LTS) | 최신 기능 | 라이브러리·운영 사례가 21보다 적음 |
| Kotlin | 간결, null 안전성, 국내 스타트업에서 증가 | Java보다 자료가 적고, JPA와 함께 쓸 때 주의점(open class 등)이 있음 |
| TypeScript (NestJS) | 이미 익숙, 프론트와 타입 공유 | 국내 백엔드 채용 시장의 표준이 아님 |

**이유**
1. **국내 백엔드의 사실상 표준**이다. 면접관이 가장 많이 묻는 질문(JPA, 트랜잭션, Spring Security)에 직접 만든 코드로 답할 수 있다.
2. Java 21은 현재 **가장 널리 운영되는 최신 LTS**다. 라이브러리 호환 문제가 가장 적다.
3. **가상 스레드(Virtual Threads)**: LMS처럼 DB와 외부 호출을 기다리는 시간이 긴 서버에서, 스레드 수 걱정 없이 동기 코드 그대로 동시 처리량을 올린다. Spring Boot 설정 한 줄로 켠다.

**포기한 것:** 이미 익숙한 TypeScript의 속도. → **보완:** 매일 "면접 설명 노트"를 쓰고, Spring의 "마법"(자동 설정, 프록시, 트랜잭션)을 원리부터 정리한다.

> **면접 한 줄:** "국내 표준 스택으로 만들어서, JPA 영속성 컨텍스트, 트랜잭션 경계, Spring Security 필터 체인을 제 코드로 설명할 수 있습니다."

### A2. 프레임워크 — Spring Boot 4.x
| 후보 | 비고 |
|---|---|
| **Spring Boot 4.x** ✅ | 현재 지원되는 최신 메이저 (2025년 11월 GA, Java 17 이상) |
| Spring Boot 3.x | 자료가 가장 많음. 오픈소스 지원 기간이 끝나가는 버전대라 새 프로젝트엔 부적합 |
| Quarkus / Micronaut | 빠른 기동과 적은 메모리. 국내 사례와 인력이 적음 |

**이유:** 새 프로젝트는 **지원 기간이 가장 긴 버전**으로 시작하는 게 원칙이다. 인터넷 자료가 3.x 기준인 경우가 많아서, 버전 차이(패키지 경로, 설정 키)는 공식 마이그레이션 가이드로 확인한다. Day 1에 Spring Initializr의 최신 안정 버전으로 고정한다.

### A3. 빌드 — Gradle (Kotlin DSL)
- **후보:** **Gradle** ✅, Maven
- **이유:** 국내 Spring 프로젝트의 다수가 Gradle을 쓴다. 증분 빌드와 캐시가 빠르다. Kotlin DSL은 IDE 자동 완성과 타입 검사가 된다.
- **포기한 것:** Maven의 단순함과 예측 가능성. 빌드 스크립트가 "코드"라서 복잡해질 수 있다.

### A4. 저장소 구조 — 다언어 모노레포
```
lms/
├─ backend/              Spring Boot (Gradle) — 실행 모듈 하나, 내부는 Modulith 모듈
├─ frontend/             pnpm 워크스페이스
│  ├─ apps/web/          Next.js (BFF 포함)
│  ├─ packages/ui/       디자인 시스템 + Storybook
│  └─ packages/api-client/  OpenAPI에서 생성한 TS 타입
├─ infra/
│  ├─ terraform/         modules/ + envs/prod, envs/staging
│  ├─ keycloak/          렐름 설정(JSON 내보내기, 코드로 관리)
│  └─ local/             docker-compose (로컬 개발, 백업 시연)
├─ docs/
└─ .github/workflows/    backend.yml, frontend.yml, infra.yml (경로별로 실행)
```
- **이유:** API를 바꾸면 **같은 PR 안에서** 프론트 타입이 바뀌고 CI가 둘 다 검증한다. 인프라 코드도 같이 리뷰된다.
- **포기한 것:** 팀별 저장소 권한 분리. 팀이 커지면 CODEOWNERS로 경로별 리뷰어를 지정한다.

---

## B. 백엔드

### B1. 구조 — 모듈러 모놀리스 + Spring Modulith
| 후보 | 장점 | 단점 |
|---|---|---|
| **모듈러 모놀리스 + Spring Modulith** ✅ | 배포 1개, 트랜잭션 단순, **모듈 경계를 테스트로 강제** | 규모가 커지면 분리 필요 |
| 마이크로서비스 | 독립 배포·확장 | 분산 트랜잭션, 네트워크 장애, 운영 비용 |
| 경계 없는 모놀리스 | 가장 빠름 | 나중에 쪼갤 수 없음 |

**이유**
- 초기 스타트업은 팀이 작고 도메인 경계가 아직 확정되지 않았다. 이 단계의 MSA는 문제보다 비용이 크다.
- **Spring Modulith**는 Spring 공식 프로젝트다. 패키지 단위 모듈(`courses`, `enrollments`…)끼리 **허용되지 않은 의존이 생기면 테스트가 실패**한다. "경계를 지키자"는 약속을 자동으로 검사하는 것이다.
- 모듈 간 통신은 **이벤트**(`EnrollmentCompleted` 등)로 한다. 나중에 모듈을 서비스로 떼어낼 때 이벤트를 메시지 큐로 옮기기만 하면 된다.

> **면접 한 줄:** "MSA는 조직 규모 문제를 푸는 도구라서, 모듈 경계를 Spring Modulith 테스트로 강제하는 모놀리스로 시작했습니다."

### B2. 데이터베이스 — PostgreSQL (Amazon RDS)
| 후보 | 장점 | 단점 |
|---|---|---|
| **PostgreSQL** ✅ | **RLS**, JSONB, 강력한 SQL | 국내에선 MySQL 경험자가 더 많음 |
| MySQL | 국내에서 가장 흔함 | **RLS 없음**, JSON 기능 약함 |
| Aurora PostgreSQL | 고가용성, 자동 확장 | 최소 비용이 RDS보다 높음 |

**이유:** LMS 데이터는 관계형(회사 → 부서 → 멤버 → 수강 → 진도)이고, 대시보드는 조인과 집계의 연속이다. **RLS로 DB가 테넌트 격리를 강제**할 수 있다는 것이 MySQL 대신 고른 결정적 이유다. xAPI 기록과 퀴즈 보기는 JSONB로 저장한다. 운영은 **관리형 RDS**(자동 백업, 특정 시점 복구, 패치)가 표준이다.

### B3. 데이터 접근 — Spring Data JPA + JdbcClient
| 후보 | 장점 | 단점 |
|---|---|---|
| **Spring Data JPA (Hibernate)** ✅ | 국내 Spring 표준, **`@TenantId` 멀티테넌시 지원**, 생산성 | N+1, 영속성 컨텍스트 등 학습 필요 |
| MyBatis | 국내 SI·대기업에서 여전히 많음, SQL 직접 제어 | 반복 SQL, 멀티테넌시를 직접 구현 |
| jOOQ | 타입 안전 SQL | 국내 사례 적음, 상용 DB는 유료 |
| QueryDSL | 국내에서 동적 쿼리 표준처럼 쓰임 | 원본 프로젝트 유지보수가 정체돼 포크(OpenFeign)로 이동 중 |

**이유**
- 일반 CRUD는 JPA로 빠르게 만들고, **대시보드 집계처럼 SQL이 더 명확한 곳은 `JdbcClient`로 직접 SQL**을 쓴다. "ORM으로 다 하려고 하지 않는다"가 현업 감각이다.
- 동적 필터가 많아지면 QueryDSL(포크)을 추가한다. MVP는 Spring Data의 Specification으로 충분하다.
- **N+1 방지:** 목록 조회는 fetch join이나 `@EntityGraph`로 쓰고, 테스트에서 쿼리 수를 검증한다. `open-in-view`는 끈다(뷰 렌더링 중 지연 로딩으로 쿼리가 새는 것을 막는다).

> **면접 포인트:** JPA를 쓰면 거의 반드시 나오는 질문이 N+1과 OSIV다. 이 프로젝트에서 실제로 끈 설정과 그 이유를 말할 수 있어야 한다.

### B4. 마이그레이션 — Flyway
- **후보:** **Flyway** ✅, Liquibase, Hibernate `ddl-auto`
- **이유:** SQL 파일(`V1__init.sql`)을 순서대로 적용하는 가장 단순하고 널리 쓰이는 방식이다. **RLS 정책, DB 계정 권한처럼 JPA가 모르는 SQL**도 같은 곳에서 버전 관리한다. `ddl-auto=update`는 운영에서 절대 쓰지 않는다(의도하지 않은 스키마 변경, 롤백 불가).
- 마이그레이션은 앱 시작 시가 아니라 **배포 파이프라인의 별도 작업**으로 실행한다(→ D7). 앱 계정에는 DDL 권한이 없기 때문이다.

### B5. 멀티테넌시 — 공유 스키마 + Hibernate `@TenantId` + Postgres RLS
| 방식 | 격리 | 운영 비용 | 테넌트 1,000개 |
|---|---|---|---|
| DB를 테넌트마다 | 최고 | 최악 | 운영 불가 |
| 스키마를 테넌트마다 | 높음 | 나쁨 (마이그레이션 N번) | 힘듦 |
| **공유 스키마 + tenant_id** ✅ | 앱 의존 → **RLS로 보강** | 최고 | 문제없음 |

**3중 방어**
1. **식별:** 현재 테넌트는 **검증된 액세스 토큰의 조직 정보 + 우리 DB의 멤버십 확인**으로만 정한다. 요청 바디나 헤더의 tenantId는 믿지 않는다.
2. **앱 레벨 — Hibernate `@TenantId`:** 엔티티의 `tenantId` 필드에 `@TenantId`를 붙이면, Hibernate가 **조회에는 조건을 자동으로 붙이고 저장에는 값을 자동으로 채운다.** 개발자가 WHERE를 깜빡해도 된다. Hibernate 6의 공식 기능이다.
3. **DB 레벨 — RLS:** 커넥션을 풀에서 꺼낼 때 `set_config('app.tenant_id', ...)`를 설정하고, 반납할 때 **반드시 초기화**한다. 이 일은 **DataSource를 감싼 래퍼**가 하므로 JPA를 우회하는 `JdbcClient` 쿼리에도 RLS가 적용된다.

**이전 설계(Prisma)에서 바뀐 점:** 쿼리마다 트랜잭션을 여는 우회 방식(왕복 4번)이 필요 없다. 커넥션을 꺼낼 때 한 번만 설정한다.

**Day 1 구현 중 바꾼 점:** 처음엔 Hibernate의 `MultiTenantConnectionProvider`를 쓰려 했지만, 그 확장 지점은 Hibernate가 꺼내는 커넥션에만 적용돼 `JdbcClient`가 빠진다. 그래서 **DataSource 래퍼(`TenantAwareDataSource`)** 로 바꿨다([notes/day1.md](notes/day1.md) §2.1).

> **면접 한 줄:** "공유 스키마로 운영 비용을 낮추고, 격리 실수는 Hibernate `@TenantId`로 앱에서, RLS로 DB에서 이중으로 막고, 교차 테넌트 테스트로 증명했습니다."

### B6. 인증 서버 — Keycloak (Organizations)
| 후보 | 장점 | 단점 | 비용 |
|---|---|---|---|
| **Keycloak** ✅ | 오픈소스, OIDC·SAML·MFA·**조직(멀티테넌트)·이메일 우선 로그인·조직별 SSO** 기본 제공, 국내 기업에서 많이 씀 | 직접 운영(메모리 많이 씀, 업그레이드) | 0원 (서버 비용만) |
| Auth0 / Okta CIC | 완전 관리형, 기능 풍부 | B2B 조직·SSO 기능이 상위 요금제 | 유료 |
| WorkOS | B2B SSO·SCIM 특화 | SSO 연결당 과금 | 유료 |
| Amazon Cognito | AWS 통합, 저렴 | B2B 조직 모델이 약함, 화면 커스터마이징 제약 | 저렴 |
| Spring Authorization Server | Spring 공식, 완전한 제어 | 로그인 화면·MFA·SSO·조직을 직접 구현 | 0원 |
| 직접 구현 | 학습 효과 | **"Don't roll your own auth"**, SSO 요구에 대응 불가 | 0원 |

**이유**
1. **"인증은 직접 만들지 않는다"** 가 업계 원칙이다. 비밀번호 저장, 잠금 정책, MFA, 세션, 토큰 회전 같은 보안 기능은 검증된 구현을 쓴다.
2. B2B에서 곧 나올 요구(**SSO, MFA, 조직별 로그인 정책**)를 설정으로 해결한다. Keycloak 26부터 정식 기능이 된 **Organizations**가 회사별 멤버, 이메일 도메인, 조직별 IdP 연결, 이메일 우선 로그인을 지원한다.
3. 표준 프로토콜(OIDC)을 쓰므로 나중에 Auth0나 WorkOS로 옮겨도 **우리 API 코드는 거의 그대로**다. API는 "표준 JWT를 검증할 뿐"이기 때문이다.

**포기한 것:** 인증 로직을 직접 구현하며 보여주는 깊이, 그리고 Keycloak 운영 비용(Fargate 컨테이너 1개, 1GB 이상 메모리). → 대신 **토큰 흐름, 조직 매핑, BFF 세션, 리소스 서버 검증**을 정확히 설명하는 것으로 깊이를 보여준다.

**렐름 설정은 코드로 관리한다:** Keycloak 렐름 설정(클라이언트, 토큰 수명, 비밀번호 정책)을 JSON으로 내보내 `infra/keycloak/`에 커밋한다. 관리 화면에서 클릭으로 바꾸고 기록이 남지 않는 것을 막는다.

> **면접 한 줄:** "인증은 직접 만들지 않는다는 원칙에 따라 Keycloak을 썼고, 표준 OIDC라서 관리형 IdP로 바꿔도 API는 그대로입니다."

### B7. 웹의 토큰 처리 — BFF (Backend for Frontend)
| 방식 | 토큰 위치 | XSS 시 피해 | 업계 평가 |
|---|---|---|---|
| 브라우저 localStorage | 브라우저 JS | ❌ 토큰 통째로 탈취 | 비권장 |
| 브라우저 메모리 + Refresh 쿠키 | 브라우저 JS | Access 토큰 노출 | 차선 |
| **BFF** ✅ | **서버(Next.js)** | 토큰 탈취 불가, 세션 쿠키만 존재 | IETF 브라우저 앱 OAuth 가이드의 최우선 권장 |
| Spring Cloud Gateway BFF | 게이트웨이 | 탈취 불가 | Spring 진영 표준. Next.js 서버 렌더링과 조합이 복잡 |

**동작 방식**
1. 브라우저는 `__Host-session` 쿠키(HttpOnly, Secure, SameSite=Lax) **하나만** 가진다. 값은 무작위 세션 ID다.
2. Next.js 서버가 Keycloak과 **Authorization Code + PKCE** 흐름으로 로그인하고, 토큰은 **Redis 세션 저장소**에 보관한다.
3. 서버 컴포넌트와 서버 액션이 Spring API를 호출할 때 세션에서 Access Token을 꺼내 `Authorization: Bearer`로 붙인다.
4. Access Token이 만료되면 Next.js 서버가 Refresh Token으로 갱신한다. **Keycloak의 Refresh Token 회전과 재사용 감지 설정**을 켜서, 탈취된 Refresh Token을 쓰면 세션 전체가 폐기된다.

**왜 BFF인가**
- 브라우저에 토큰이 없으니 XSS가 나도 **토큰을 훔쳐 갈 수 없다.**
- Next.js App Router의 **서버 컴포넌트가 인증된 데이터를 서버에서 바로 렌더링**할 수 있다. 이전 설계("모든 데이터를 클라이언트에서 가져오기")의 어색함이 사라진다.
- 브라우저는 `app.도메인` 하나와만 통신하므로 CORS와 서드파티 쿠키 문제가 없다.

**왜 세션을 쿠키에 직접 암호화해 넣지 않고 Redis에 두나:** Keycloak 토큰 묶음(Access + Refresh + ID)은 수 KB라서 쿠키 한 개 크기 한도(약 4KB)를 넘기 쉽다. 또 서버 저장소에 두면 **관리자가 특정 사용자 세션을 즉시 끊을 수 있다.**

**OIDC 클라이언트 라이브러리:** `openid-client`(OpenID 인증을 받은 Node 라이브러리). 프레임워크에 묶인 인증 라이브러리보다 동작이 투명해서 흐름을 설명하기 좋다.

**포기한 것:** 요청이 브라우저 → Next.js → Spring으로 한 단계 늘어난다. 같은 VPC 안이라 지연은 수 ms다(→ D5).

### B8. API 인증 — Spring Security OAuth2 Resource Server
- **이유:** Spring API는 **"Keycloak이 서명한 JWT인가"만 검증**한다. Keycloak의 공개키 목록(JWKS)을 받아 서명, 만료, 발급자(`iss`), 대상(`aud`)을 확인한다. 공개키는 캐시하고 키 교체도 자동으로 따라간다.
- 직접 만든 JWT 필터 대신 Spring Security 표준 설정 몇 줄로 끝난다. 브라우저(BFF 경유)와 외부 시스템(Client Credentials 토큰) 호출이 **같은 방식으로 인증**된다.

### B9. 인가 — 앱 DB의 멤버십 역할 + `@PreAuthorize`
| 후보 | 비고 |
|---|---|
| Keycloak 역할(role)을 토큰에 넣기 | 역할 변경이 Keycloak 동기화를 요구함. 회사별 역할 표현이 복잡 |
| Keycloak Authorization Services | 강력하지만 복잡, 디버깅 어려움 |
| **앱 DB Membership.role + `@PreAuthorize`** ✅ | 역할은 회사별 **업무 데이터**. 관리자가 우리 화면에서 바꾸면 즉시 반영 |
| OPA / Cedar | 정책 엔진. 규칙이 수십 개가 될 때 |

**이유:** **"인증은 IdP, 인가는 애플리케이션"** 은 흔한 분리 방식이다. 토큰은 "누구인지(`sub`)와 어느 조직으로 로그인했는지"만 담고, 그 조직에서의 역할은 요청마다 우리 DB(Redis 캐시 60초)에서 확인한다. 그래서 역할 변경이나 멤버 비활성화가 **토큰 만료를 기다리지 않고** 반영된다(캐시는 변경 시 즉시 삭제). 리소스 단위 규칙("강사는 자기 코스만")은 서비스 계층에서 검사한다.

### B10. 입력 검증 — Jakarta Bean Validation
- `@NotBlank`, `@Size`, `@Email` 등 표준 애너테이션으로 요청 DTO(Java `record`)를 검증한다.
- **요청 DTO와 엔티티를 분리**한다. 엔티티를 그대로 받으면 `role` 같은 필드를 몰래 끼워 넣는 공격(Mass Assignment)에 뚫린다. 응답도 응답 DTO로만 반환한다.

### B11. 에러 형식 — RFC 9457 Problem Details
```json
{
  "type": "https://app.도메인/problems/enrollment-not-found",
  "title": "Enrollment not found",
  "status": 404,
  "detail": "수강 정보를 찾을 수 없습니다.",
  "instance": "/v1/enrollments/0193...",
  "code": "ENROLLMENT_NOT_FOUND",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
}
```
- **이유:** HTTP API 에러 형식의 **IETF 표준**이다. Spring이 `ProblemDetail`로 기본 지원한다. 표준 형식이면 외부 연동사가 따로 학습하지 않아도 된다.
- `code`(앱 고유 에러 코드)와 `traceId`(분산 추적 ID)는 표준이 허용하는 확장 필드로 넣는다. 사용자가 문의하면 `traceId`로 로그와 트레이스를 바로 찾는다.

### B12. API 계약 — springdoc-openapi → TS 타입 생성
- **후보:** **OpenAPI** ✅, GraphQL, tRPC(TS 전용이라 불가)
- **이유:** B2B 고객사는 HR 시스템과 API로 연동하길 원한다. 언어 중립 REST + OpenAPI가 맞다. 컨트롤러와 DTO에서 OpenAPI 문서가 자동 생성되고, 프론트는 `openapi-typescript`로 타입을 생성한다. CI에서 생성 결과가 커밋된 파일과 다르면 실패시켜 **계약 불일치를 PR에서 잡는다.**
- springdoc의 Spring Boot 4 호환 버전은 Day 1에 확인한다.

### B13. 비동기 처리 — 트랜잭셔널 아웃박스 + Amazon SQS
| 후보 | 장점 | 단점 |
|---|---|---|
| **아웃박스(Modulith 이벤트 레지스트리) + SQS** ✅ | DB 커밋과 메시지 발행의 일관성 보장, SQS는 완전 관리형·저렴 | 순서 보장이 기본이 아님(FIFO 큐는 별도) |
| Kafka (MSK) | 이벤트 스트리밍 표준, 재처리 | 최소 비용이 높고 운영이 무거움. 이 규모엔 과함 |
| RabbitMQ (Amazon MQ) | 라우팅 유연 | 브로커 상시 비용 |
| Redis 큐 | 이미 Redis가 있음 | 내구성이 SQS보다 약함 |

**왜 아웃박스인가 (가장 중요한 이유)**
"DB에 수료 기록 저장"과 "수료증 생성 메시지 발행"은 **원자적으로 묶을 수 없다.** 커밋 후 발행하면 그 사이에 서버가 죽었을 때 메시지가 사라지고, 커밋 전에 발행하면 롤백됐는데 메시지만 나간다.
→ **같은 트랜잭션으로 "보낼 이벤트"를 DB 테이블에 쓰고, 커밋 후 별도로 SQS에 발행**한다. 발행이 실패하면 재시도한다. Spring Modulith의 **이벤트 발행 레지스트리**가 바로 이 아웃박스 패턴을 공식 지원한다.

**큐로 보내는 작업:** 수료증 PDF 생성, 메일 발송(초대, 배정, 리마인더), 대규모 배정의 수강 생성.

**소비자는 같은 Spring 앱의 `worker` 실행 모드**다(같은 이미지, 다른 프로필). PDF가 CPU를 써도 API 응답에 영향이 없고, 따로 늘릴 수 있다.

**멱등성:** SQS는 "최소 한 번 전달"이라 같은 메시지가 두 번 올 수 있다. 소비자는 "이미 처리했는가"를 먼저 확인한다. 실패가 반복된 메시지는 **DLQ(Dead Letter Queue)** 로 옮겨 알림을 보낸다.

### B14. 예약 작업 — `@Scheduled` + ShedLock
- **후보:** **`@Scheduled` + ShedLock** ✅, EventBridge Scheduler, Quartz
- **이유:** 서버가 2대가 되면 `@Scheduled`가 **두 번 실행**된다. ShedLock이 DB 락으로 한 대만 실행되게 한다. 코드와 함께 관리되고 로컬에서도 똑같이 돈다. EventBridge Scheduler는 작업이 많아지거나 서비스가 쪼개질 때 검토한다.

### B15. Redis (ElastiCache Valkey) + Bucket4j
- **용도:** ① BFF 세션 저장소 ② 멤버십 역할 캐시 ③ 대시보드 캐시 ④ Rate Limit(Bucket4j)
- **이유:** 이 네 가지는 **여러 서버가 공유해야 하는 상태**다. 서버 메모리에 두면 서버가 2대가 되는 순간 세션이 끊기고 Rate Limit이 2배로 풀린다.
- **Valkey를 고른 이유:** Redis의 라이선스 변경 이후 리눅스 재단이 이어받은 오픈소스 포크다. ElastiCache에서 Redis 엔진보다 저렴하고 명령어가 호환된다.

### B16. 파일 — S3 + CloudFront 서명 URL
- **후보:** **S3 + CloudFront** ✅, Cloudflare R2(이전 선택)
- **바꾼 이유:** AWS 표준 구성으로 통일하면 **IAM 권한, 암호화, 로그를 한 체계로** 관리한다. ECS 태스크 역할(Task Role)로 접근하므로 **액세스 키가 아예 없다.**
- **업로드:** 문서·이미지(50MB 이하)는 Presigned PUT 한 번, **영상은 Multipart 업로드**(→ B16-1). 완료 보고 시 서버가 `HeadObject`로 재검증.
- **다운로드:** 문서·수료증은 CloudFront 서명 URL(10분).
- **영상 재생:** HLS + **CloudFront 서명 쿠키**(→ B16-2).
- S3 버킷은 **퍼블릭 접근 전면 차단**, CloudFront만 읽을 수 있다(OAC).
- **포기한 것:** R2의 무료 다운로드 트래픽. 시연 규모에서는 CloudFront 무료 구간 안이다.

### B16-1. 대용량 업로드 — S3 Multipart 업로드
| 후보 | 장점 | 단점 |
|---|---|---|
| Presigned PUT 한 번 | 가장 단순 | 1GB를 한 번에 보내다 끊기면 **처음부터 다시**. 큰 파일일수록 실패 확률이 높다 |
| **Multipart 업로드** ✅ | 조각(예: 10MB)으로 나눠 **병렬 전송**, 실패한 조각만 재전송, 이어 올리기 가능 | 시작 → 조각별 URL 발급 → 완료의 3단계 API 필요 |
| tus 같은 재개 가능 업로드 프로토콜 | 표준화된 재개 업로드 | 별도 서버 필요, S3 직접 업로드의 장점이 사라짐 |

**이유:** AWS도 100MB 이상 파일에는 Multipart를 권장한다. 강의 영상은 수백 MB에서 수 GB다.
- 서버가 `CreateMultipartUpload`로 시작하고, 조각 번호마다 **Presigned URL**을 발급한다. 브라우저는 조각을 S3로 직접 보내고, 각 응답의 `ETag`를 모아 완료를 요청한다. 서버가 `CompleteMultipartUpload`로 합친다.
- S3 CORS 설정에서 **`ETag` 헤더를 노출**해야 브라우저가 값을 읽을 수 있다(흔히 막히는 지점).
- 중간에 버려진 업로드의 조각은 보이지 않지만 **저장 요금이 계속 나온다.** S3 수명 주기 규칙 **"미완료 Multipart 업로드 1일 후 중단"** 을 반드시 건다.

### B16-2. 영상 변환 — AWS Elemental MediaConvert → HLS
**왜 변환이 필요한가 (mp4는 이미 압축돼 있는데도)**
1. **재생 호환성:** 아이폰 영상은 HEVC 코덱의 `.mov`인 경우가 많아 일부 브라우저·회사 PC에서 재생되지 않는다. 모두가 재생할 수 있는 **H.264 + AAC**로 통일한다.
2. **트래픽과 버퍼링:** 4K 고화질 원본을 그대로 주면 학습자 전원이 그 용량을 받는다. 회사 와이파이에서 수십 명이 동시에 보면 버퍼링이 생기고 전송 비용도 커진다.
3. **적응형 스트리밍(ABR):** 영상을 여러 화질(1080p, 720p, 480p)의 짧은 조각(6초)으로 나눠두면, 플레이어가 네트워크 속도에 맞춰 화질을 자동으로 바꾼다. 업계 표준 형식이 **HLS**다.
4. **믿을 수 있는 영상 길이:** 진도 계산에 쓰는 영상 길이를 강사 입력이나 브라우저 보고가 아니라 **변환 결과에서 서버가 직접** 얻는다.

| 후보 | 장점 | 단점 | 비용 |
|---|---|---|---|
| **MediaConvert** ✅ | 관리형(서버 관리 없음), 작업 템플릿 하나로 여러 화질 HLS + 썸네일, 완료·실패 이벤트를 EventBridge로 발행, Terraform으로 정의 | AWS 종속, 변환 원리가 감춰짐 | 출력 영상 분당 과금 |
| ffmpeg 워커 (Fargate) | 가장 저렴, 원리를 직접 보여줌 | CPU·메모리·디스크를 많이 쓰고, 긴 작업의 실패·재시도·진행률을 직접 관리 | 컨테이너 실행 시간 |
| 영상 플랫폼 (Mux, Kollus 등) | 업로드부터 재생·DRM·분석까지 완성형, 국내 교육 업체가 많이 씀 | 유료 계약, 역량 증명이 어려움 | 계약 |
| 브라우저에서 압축 (ffmpeg.wasm) | 서버 비용 0원 | 대용량에서 느리고 불안정, 사용자 PC 성능에 의존 → **표준 아님** | 0원 |

**이유:** 영상 변환은 **우리 회사의 핵심 경쟁력이 아니라 인프라**다. 관리형 서비스에 맡기고, 우리는 **"업로드 → 변환 요청 → 완료 이벤트 처리 → 재생 권한"이라는 흐름과 실패 처리**를 설계한다. 이미 만든 **아웃박스 → SQS → 워커** 구조를 그대로 재사용한다.

**변환 설정 (작업 템플릿)**
| 항목 | 값 | 이유 |
|---|---|---|
| 비디오 코덱 | H.264, QVBR(품질 기준 가변 비트레이트) | 가장 넓은 호환성. QVBR은 장면 복잡도에 따라 비트레이트를 조절해 같은 화질에 용량이 작다 |
| 화질 단계 | 1080p · 720p · 480p (**원본보다 큰 화질은 만들지 않음**) | 강의 화면(슬라이드, 코드)은 글자가 보여야 해서 1080p를 최상위로 둔다 |
| 오디오 | AAC 128kbps | 표준 |
| 조각 길이 | 6초 | 화질 전환 반응성과 요청 수의 균형 |
| 썸네일 | 프레임 캡처(JPEG) | 코스 목록·플레이어 미리보기 |

**포기한 것:** ffmpeg를 직접 다루며 보여줄 수 있는 깊이, 그리고 분당 비용. → 면접에서는 "규모가 커져 비용이 문제가 되면 ffmpeg 워커(Fargate Spot)로 옮길 수 있게 **변환을 `VideoTranscoder` 인터페이스 뒤에 뒀다**"고 설명한다.

**⚠️ 일정이 밀릴 때의 후퇴안:** 업로드를 **H.264 mp4만** 받도록 제한하고, 원본을 서명 URL로 그대로 재생한다(점진적 다운로드). 이때 영상 길이는 업로드 완료 시 워커가 파일 헤더를 읽어 구한다. 08 문서에 표준 이탈로 기록한다.

### B17. PDF — OpenHTMLtoPDF
| 후보 | 비고 |
|---|---|
| **OpenHTMLtoPDF** ✅ | HTML/CSS 템플릿 → PDF. 내부 엔진은 Apache PDFBox. LGPL |
| iText | 강력하지만 **AGPL**(소스 공개 의무) 또는 상용 라이선스 → 회사 제품에 쓰기 어려움 |
| JasperReports | 보고서 특화, 무거움 |
| 헤드리스 Chrome | 결과가 예쁘지만 컨테이너가 무거움 |

**이유:** 수료증 디자인을 **HTML 템플릿(Thymeleaf)** 으로 관리해 디자이너가 수정하기 쉽다. 한글은 Noto Sans KR을 임베드한다. **라이선스를 검토해서 iText를 뺐다**는 점이 면접 포인트다.

### B18. 메일 — Amazon SES
- **이유:** AWS 표준 구성에 포함되고 가장 저렴하다. 도메인 인증(SPF, DKIM, DMARC)으로 스팸함을 피한다. Keycloak의 메일(초대, 비밀번호 재설정)도 SES SMTP로 보낸다.
- **시연 제약:** 새 SES 계정은 **샌드박스**라서 미리 인증한 주소로만 보낼 수 있다. 데모 계정 메일 주소를 인증해 두면 시연엔 충분하다. 운영 전환에는 AWS 심사가 필요하다.
- 로컬 개발은 **Mailpit**(가짜 메일함)을 쓴다. `MailSender` 인터페이스 뒤에서 구현을 바꿔 끼운다.

### B19. 학습 기록 — xAPI 문장 구조 (내부 LRS)
```json
{
  "actor":  { "account": { "homePage": "https://app.도메인", "name": "<userId>" } },
  "verb":   { "id": "http://adlnet.gov/expapi/verbs/completed", "display": { "ko-KR": "완료함" } },
  "object": { "id": "https://app.도메인/xapi/lessons/<lessonId>", "objectType": "Activity" },
  "result": { "completion": true, "duration": "PT12M30S" },
  "context": { "registration": "<enrollmentId>", "extensions": { "https://app.도메인/xapi/tenant": "<tenantId>" } },
  "timestamp": "2026-11-10T09:12:00+09:00"
}
```
- **이유:** 자체 형식으로 "레슨 완료 로그"를 만들면 우리만 읽을 수 있다. **xAPI 형식으로 남기면 외부 LRS, BI 도구, 고객사 시스템으로 그대로 내보낼 수 있다.** 이후 cmi5나 SCORM 도입의 기반이 된다.
- 빠른 화면 조회용 **현재 상태**(LessonProgress, Enrollment)와 **변경 불가능한 기록**(LearningRecord, xAPI)을 분리한다. 상태는 덮어쓰고, 기록은 추가만 한다.
- 기록하는 동사: `launched`, `progressed`, `completed`, `attempted`, `passed`, `failed`.

### B20. 테스트
| 종류 | 도구 | 무엇을 |
|---|---|---|
| 단위 | JUnit + AssertJ | 채점, 수료 판정, 시청 시간 인정 계산 |
| 통합 | **Testcontainers**(진짜 PostgreSQL, Redis) + MockMvc | API, **RLS 교차 테넌트 차단**, 트랜잭션 |
| 모듈 경계 | Spring Modulith 검증 테스트 | 허용되지 않은 모듈 의존 |
| E2E | Playwright | 로그인 → 학습 → 수료 핵심 흐름 1개 |
| 프론트 컴포넌트 | Storybook + Vitest + Chromatic | 상태별 UI, 인터랙션, 접근성, 시각 회귀 |

- **H2 같은 메모리 DB를 쓰지 않는 이유:** H2에는 RLS가 없고 SQL 문법도 다르다. 운영과 다른 DB로 테스트하면 **가장 중요한 격리 테스트가 의미가 없다.**
- API 통합 테스트는 Keycloak 대신 **테스트용 JWT를 직접 서명**해 넣는다(Spring Security 테스트 지원). Keycloak 연동 자체는 E2E에서 확인한다.

### B21. 관측성 — OpenTelemetry
| 신호 | 도구 | 비용 |
|---|---|---|
| 로그 | JSON 로그 → **CloudWatch Logs** (ECS 기본 연동) | 소량 거의 0원 |
| 트레이스·메트릭 | **OpenTelemetry** → **Grafana Cloud** | 무료 구간 |
| 에러 | **Sentry** (Spring, Next.js) | 무료 구간 |
| 알림 | CloudWatch 알람(ALB 5xx, ECS 태스크 비정상) → SNS → 메일 | 거의 0원 |
| 비용 | **AWS Budgets 알림** | 0원 |

- **OpenTelemetry를 고른 이유:** 업체 중립 표준이다. Datadog(유료)이나 다른 백엔드로 바꿔도 **코드는 그대로 두고 전송 설정만** 바꾼다.
- 모든 로그와 트레이스에 `traceId`, `tenantId`, `userId`가 붙는다. 비밀번호, 토큰 필드는 마스킹한다.

---

## C. 프론트엔드

### C1. 프레임워크 — Next.js (App Router, 서버 컴포넌트)
- **후보:** **Next.js** ✅, Vite + React SPA, React Router v7
- **이유:** BFF 구조라서 **서버 컴포넌트가 인증된 데이터를 서버에서 바로 렌더링**한다. App Router의 설계 방향과 일치한다. 국내 프론트엔드 채용의 표준이다. 공개 페이지(랜딩, 수료증 진위 확인)는 정적 생성으로 빠르게 제공한다.
- **데이터 흐름 원칙:** 읽기는 서버 컴포넌트, 쓰기는 **서버 액션 또는 라우트 핸들러**(→ Spring API). 실시간에 가까운 상호작용(영상 진도 하트비트, 업로드 진행률)만 클라이언트 컴포넌트에서 BFF 라우트를 호출한다.

### C2. UI — Tailwind CSS v4 + 자체 디자인 시스템 (`@lms/ui`) + DESIGN.md
- **이유:** 컴포넌트 소스를 **우리 저장소에 소유**하므로 `packages/ui`가 진짜 디자인 시스템이 된다. Radix 기반이라 키보드·스크린리더 접근성이 기본이다. 디자인 토큰은 CSS 변수로 관리한다.
- **디자인 방향은 [DESIGN.md](../DESIGN.md)가 정한다** (2026-11-06 확정, 2026-11-10 보편적인 디자인으로 변경). shadcn의 기본 테마(Tailwind 기본 인디고 강조색)는 AI가 만든 화면의 대표 징후라 버렸고, shadcn은 "컴포넌트 소스를 복사해 소유한다"는 방식만 따른다.
- 디자인 규칙은 open-design의 craft 규칙(상태 5종, 폼 검증, 접근성, 한글 조판)과 Impeccable·Anthropic frontend-design 스킬의 금지 목록을 출처와 함께 가져왔다([docs/design/](design/)).
- **포기한 것:** MUI·Ant Design의 "다 만들어진 관리자 화면" 속도.

### C3. 디자인 테스트 — Storybook + Vitest 애드온 + 대비 검사
- **Storybook:** 컴포넌트와 화면의 모든 상태(로딩, 빈 상태, 오류, 정상, 극단값)를 앱과 분리해 확인한다. 화면 조립 스토리(`Screens/*`)는 API가 생기기 전 설계 검토 자료다.
- **Storybook Vitest 애드온:** 모든 스토리를 **실제 Chromium에서 테스트로** 돌린다. `play` 함수(클릭·키보드·aria 확인)와 **axe 접근성 검사**(`a11y.test: "error"` — 위반이 있으면 실패)가 CI에서 실행된다.
  - 실제로 잡은 버그: `tailwind-merge`가 우리 글자 크기 토큰(`text-body-sm`)을 색으로 오인해 주 버튼 글자색을 지워 버렸다(대비 1.15:1). axe 테스트가 잡았고 `extendTailwindMerge`로 고쳤다.
- **대비 검사 스크립트**(`check:contrast`): `styles.css`의 토큰 값을 직접 읽어 DESIGN.md가 쓰는 28개 색 짝을 WCAG 2.2 AA로 검사한다. 문서와 코드가 어긋날 수 없다.
- **Chromatic(Should):** PR마다 Storybook 배포 + 스크린샷 비교(시각 회귀). 무료 구간 안에서 쓰며 계정 연결이 필요해 Day 4에 붙인다.

### C4. 클라이언트 상태 — TanStack Query (필요한 곳만)
- 서버 컴포넌트가 읽기를 맡으므로 TanStack Query는 **클라이언트 상호작용 화면**(코스 편집기, 퀴즈 응시, 진도)에서만 쓴다. 전역 상태 라이브러리는 쓰지 않는다.

### C5. 폼 — React Hook Form + zod
- 비제어 방식이라 필드가 많은 코스 편집기에서 리렌더링이 적다. zod 스키마는 **서버 액션에서 한 번 더** 검증한다(클라이언트 검증은 UX, 진짜 검증은 서버).

### C6. 차트 — HTML/CSS 직접 구현 (Recharts에서 변경)
- 제품 전체의 차트가 "부서별 수료율 가로 막대" 정도라 차트 라이브러리 의존성을 둘 이유가 없다. 원래 계획은 Recharts였지만, 디자인 작업에서 직접 구현으로 바꿨다.
- **이유:** 네이티브 요소라 접근성 처리(값 텍스트, "표로 보기" 대체 표)가 쉽고, 토큰을 그대로 쓴다. 번들도 가볍다.
- 차트가 늘어나면(추세선, 기간 비교) 그때 라이브러리를 검토한다.

---

## D. 인프라와 DevOps

### D1. 클라우드 — AWS (서울 리전)
| 후보 | 비고 |
|---|---|
| **AWS** ✅ | 국내 스타트업에서 가장 많이 씀, 자료·인력 최다 |
| NCP (네이버 클라우드) | 공공·금융 고객이 있으면 유리(공공 클라우드 보안 인증) |
| GCP / Azure | 특정 생태계(데이터, MS 365) 고객에 유리 |
| 미니PC (이전 선택) | 0원이지만 업계 표준 아님 |

**이유:** 국내 스타트업 표준이고, 이번에 쓰는 관리형 서비스(RDS, ElastiCache, SQS, SES, CloudFront)가 한 곳에 모여 있다. **공공기관 고객을 노리는 회사라면 NCP 이야기가 나올 수 있다**는 점을 면접 전에 기억해 둔다.

### D2. IaC — Terraform
| 후보 | 비고 |
|---|---|
| **Terraform** ✅ | 업계 IaC 표준, 멀티 클라우드, 채용 수요 최다 |
| AWS CDK | 프로그래밍 언어로 작성, AWS 전용 |
| CloudFormation | AWS 기본, 장황함 |
| Pulumi | 프로그래밍 언어, 사례 적음 |

**이유:** 인프라를 **코드로 리뷰하고 버전 관리**한다. 클릭으로 만든 인프라는 재현할 수 없다. `terraform plan`으로 바뀔 내용을 미리 보고, `terraform destroy`로 면접 후 한 번에 정리해 **과금을 끊는다.**
- **상태 파일:** S3 버킷(버전 관리, 암호화)에 저장하고 잠금을 건다. 로컬에 두면 잃어버리거나 동시 수정 충돌이 난다.
- **구조:** `modules/`(network, ecs-service, rds, …) + `envs/prod`, `envs/staging`. 스테이징은 정의만 하고 비용 때문에 평소엔 띄우지 않는다.

### D3. 컨테이너 실행 — ECS Fargate
| 후보 | 장점 | 단점 |
|---|---|---|
| **ECS Fargate** ✅ | 서버 관리 없음, AWS 네이티브, 학습 곡선 낮음 | AWS 종속 |
| EKS (쿠버네티스) | 업계 표준 오케스트레이션, 이식성 | 클러스터 비용(월 약 $73), 학습·운영 부담 → **이 규모엔 과함** |
| EC2 + Docker Compose | 가장 저렴 | 서버 패치, 무중단 배포, 확장을 직접 |
| Lambda | 사용량 과금 | Spring·Keycloak 같은 상시 서버에 부적합 |

**이유:** 초기 스타트업에서 쿠버네티스는 "문제보다 비용이 큰" 경우가 많다. ECS로 시작하고, 서비스가 수십 개가 되면 EKS를 검토한다. 컨테이너 이미지 기반이라 이전은 어렵지 않다.

**서비스 구성:** `web`(Next.js), `api`(Spring), `worker`(Spring, worker 프로필), `keycloak`.

### D4. 네트워크 — 퍼블릭/프라이빗 서브넷 분리, NAT 없음 ⚠️
- **표준 구성:** 앱 컨테이너를 프라이빗 서브넷에 두고, 외부 호출은 NAT Gateway를 거친다.
- **우리 구성:** DB·Redis는 **프라이빗 서브넷**(인터넷 경로 없음), ECS 태스크는 **퍼블릭 서브넷 + 보안 그룹으로 ALB에서 오는 트래픽만 허용**한다.
- ⚠️ **표준에서 벗어난 이유:** NAT Gateway는 켜두기만 해도 월 약 $40 이상이다(서울, 데이터 처리 비용 별도). 시연 기간 비용에서 가장 큰 단일 항목이 된다. 보안 그룹이 인바운드를 ALB로만 제한하므로 외부에서 들어올 수 있는 경로는 같다. **운영이라면 프라이빗 서브넷 + NAT(또는 VPC 엔드포인트)** 로 바꾼다고 문서에 명시한다.

### D5. 프론트 호스팅 — ECS (같은 VPC)
- **후보:** Vercel(이전 선택), AWS Amplify, **ECS** ✅
- **바꾼 이유:** BFF가 **Redis 세션 저장소(VPC 내부)** 와 **Spring API**에 접근해야 한다. Vercel에서는 VPC 안의 ElastiCache에 직접 접근할 수 없어서 외부 Redis를 따로 써야 하고, 매 요청이 클라우드 사이를 오간다. 같은 VPC면 내부 통신(ECS Service Connect)으로 처리되고 인프라가 Terraform 하나로 묶인다.
- **포기한 것:** Vercel의 PR별 미리보기 배포. → 프론트 UI 리뷰는 **Chromatic의 PR별 Storybook**으로 대체한다.

### D6. 도메인, 인증서, 진입점
| 주소 | 대상 |
|---|---|
| `app.도메인` | ALB → `web` (Next.js) |
| `auth.도메인` | ALB → `keycloak` |
| `api.도메인` | ALB → `api` (외부 연동용, Bearer 토큰 필수) |
| `cdn.도메인` | CloudFront → S3 (서명 URL·서명 쿠키로만 접근) |

- **ALB 하나에 호스트 이름 기반 라우팅**으로 세 서비스를 받는다. ALB는 고정 비용이 있어서 여러 개 만들지 않는다.
- 인증서는 **ACM**(무료)으로 발급하고 자동 갱신한다. DNS는 **Route 53**을 쓴다.
- 도메인은 `.com`이나 `.dev`를 권장한다(`.xyz` 같은 일부 저가 도메인은 회사 방화벽·메일 필터에 막힐 수 있다).

### D7. 배포 — GitHub Actions(OIDC) → ECR → ECS
```
PR:    빌드 · 테스트(Testcontainers) · 정적 분석 · OpenAPI 불일치 검사 · Storybook/Chromatic
main:  이미지 빌드 → Trivy 취약점 검사 → ECR 푸시(:커밋해시)
       → Flyway 마이그레이션(일회성 ECS 태스크, lms_owner 계정)
       → ECS 서비스 롤링 배포 (배포 회로 차단기 + 자동 롤백)
```
- **OIDC:** GitHub Actions가 AWS의 단기 자격 증명을 그때그때 받는다. **저장소에 AWS 액세스 키를 넣지 않는다.** 역할은 ECR 푸시, ECS 배포만 할 수 있게 최소 권한으로 만든다.
- **배포 회로 차단기:** 새 버전이 헬스체크를 통과하지 못하면 ECS가 **자동으로 이전 버전으로 롤백**한다.
- **이미지 태그는 커밋 해시:** `latest`를 쓰지 않는다. 무엇이 배포됐는지 정확히 알 수 있고, 롤백할 버전이 명확하다.
- **마이그레이션은 하위 호환으로** 쓴다(컬럼 추가 → 배포 → 옛 컬럼 삭제는 다음 배포). 롤링 배포 중에는 옛 버전과 새 버전이 동시에 돌기 때문이다.
- **Argo CD를 안 쓴 이유:** 쿠버네티스용 GitOps 도구라 ECS에는 맞지 않는다.

### D8. 비밀값 — SSM Parameter Store (SecureString)
- **후보:** **SSM Parameter Store** ✅(표준 등급 무료), Secrets Manager(비밀값당 월 $0.4, 자동 교체)
- **이유:** ECS 태스크 정의가 시작 시 값을 주입한다. 코드와 이미지에는 비밀값이 없다. DB 비밀번호 **자동 교체**가 필요해지면 Secrets Manager로 옮긴다(RDS 연동 지원).

### D9. 이미지 빌드 — Dockerfile (멀티 스테이지)
- **후보:** **Dockerfile** ✅, Jib, Cloud Native Buildpacks(`bootBuildImage`)
- **이유:** 무엇이 들어가는지 **눈으로 보이고 설명하기 쉽다.** Spring Boot의 계층형 JAR로 의존성 층과 코드 층을 분리해 캐시 효율을 높인다. **non-root 사용자로 실행**하고 최소 런타임 이미지(JRE)를 쓴다.

### D10. 코드 품질과 보안 검사
| 도구 | 무엇 | 비용 |
|---|---|---|
| Spotless + Checkstyle | Java 포맷, 규칙 | 0원 |
| ESLint + Prettier | TS 포맷, 규칙 | 0원 |
| **CodeQL** | 정적 보안 분석 | 공개 저장소 0원 |
| **Dependabot** | 의존성 취약점·업데이트 PR | 0원 |
| **Trivy** | 컨테이너 이미지 취약점 | 0원 |
| SonarCloud | 코드 품질, 커버리지 | 공개 저장소 0원 |

### D11. 브랜치 전략 — GitHub Flow + Conventional Commits
- main은 항상 배포 가능하다. 기능마다 브랜치 → PR → CI 통과 → 머지. Git Flow는 정해진 릴리스 주기가 있는 패키지 소프트웨어에 맞다.
- 커밋: `feat(enrollment): add xAPI completed statement`.

### D12. 로컬 개발 환경 — Docker Compose
| 컨테이너 | 용도 |
|---|---|
| postgres, redis | 운영과 같은 엔진 |
| keycloak | 렐름 설정을 `infra/keycloak/`에서 자동 가져오기 |
| mailpit | 메일 확인 |

- S3, SQS는 **개발용 AWS 리소스**를 쓰거나 로컬 에뮬레이터를 쓴다. 에뮬레이터(LocalStack 등)는 최근 라이선스 정책을 Day 1에 확인하고 결정한다. 자동화 테스트에서는 저장소·큐를 인터페이스 뒤의 가짜 구현으로 바꿔 끼운다.
- 같은 Compose 파일에 `--profile full`을 주면 api, worker, web까지 모두 띄운다. **AWS 장애나 과금 문제가 생겨도 노트북으로 시연**할 수 있다.
