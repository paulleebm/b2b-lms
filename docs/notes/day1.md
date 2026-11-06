# Day 1 면접 설명 노트 — 인증 연결과 멀티테넌트 격리

> 오늘 만든 코드를 **내 말로 설명할 수 있도록** 정리한 노트다. 각 항목은 "무엇을 → 왜 → 면접에서 이렇게 말한다" 순서다.

## 0. 오늘 만든 것 한눈에

```
브라우저 ──세션 쿠키──▶ Next.js BFF ──Bearer JWT──▶ Spring API ──lms_app 계정──▶ PostgreSQL(RLS)
                        │                            │
                        └──OIDC(PKCE)──▶ Keycloak ◀──┘ (Admin API: 회사 = 조직 생성)
```

| 위치 | 파일 | 역할 |
|---|---|---|
| 인프라 | `infra/local/docker-compose.yml` | Postgres, Valkey, Keycloak, Mailpit |
| 인프라 | `infra/local/postgres/init/01-roles.sql` | DB 계정 3개 (owner / app / keycloak) |
| 인프라 | `infra/keycloak/lms-realm.json` | 렐름 설정을 코드로: 조직 기능, 클라이언트 3개, 토큰 회전 |
| DB | `backend/.../db/migration/V1__tenancy.sql` | 테이블, RLS 정책, 권한, SECURITY DEFINER 함수 |
| 백엔드 | `shared/security/*` | 리소스 서버 설정, 토큰 → 테넌트 필터 |
| 백엔드 | `shared/persistence/*` | 커넥션마다 테넌트를 거는 DataSource, Hibernate 테넌트 리졸버 |
| 백엔드 | `shared/web/*` | RFC 9457 에러, trace id |
| 백엔드 | `tenancy/internal/*` | 사용자 JIT 생성, 멤버십 확인, 회사 등록(사가) |
| 백엔드 | `organization/internal/*` | 부서 (격리 테스트 대상) |
| 테스트 | `backend/src/test/...` | 교차 테넌트, RLS, 커넥션 누수, 정책 누락, 모듈 경계 |

---

## 1. 요청 하나가 지나가는 길 (Spring)

1. **`TraceIdFilter`** — 요청마다 trace id를 만들고 로그(MDC)와 응답 헤더에 넣는다. BFF가 `traceparent`를 보내면 그 값을 쓴다.
2. **Spring Security `BearerTokenAuthenticationFilter`** — `Authorization: Bearer` 토큰을 꺼내 **Keycloak 공개키(JWKS)로 서명**을 검증하고 `iss`, `aud=lms-api`, `exp`를 확인한다. 설정은 `application.yml` 두 줄(`issuer-uri`, `audiences`)뿐이다.
3. **`TenantContextFilter`** (우리 코드) — 검증된 토큰을 받아 `MembershipTenantResolver`에게 묻는다.
   - `sub`로 사용자를 찾고, 없으면 만든다(JIT 프로비저닝).
   - `organization` 클레임의 별칭(alias) = 우리 `tenant.slug` → 테넌트를 찾는다.
   - **그 테넌트 컨텍스트 안에서** 멤버십을 조회한다. ACTIVE가 아니면 403.
   - 권한 `TENANT_MEMBER`, `ROLE_ORG_ADMIN` 등을 붙이고 `TenantContext`에 테넌트·사용자를 넣는다. `finally`에서 반드시 지운다.
4. **URL 규칙** — `/v1/me`, `POST /v1/tenants`는 로그인만, 나머지는 `TENANT_MEMBER` 필요.
5. **컨트롤러 → `@PreAuthorize("hasRole('ORG_ADMIN')")`** → 서비스 `@Transactional`.
6. 트랜잭션이 **커넥션을 꺼내는 순간** `TenantAwareDataSource`가 `set_config('app.tenant_id', ...)`를 건다 → Postgres RLS 적용.
7. Hibernate가 `@TenantId` 필드로 `where tenant_id = ?`를 자동으로 붙인다.
8. 예외는 `GlobalExceptionHandler`가 RFC 9457로 바꾼다. **필터 체인에서 난 401/403도** `HandlerExceptionResolver`로 넘겨서 같은 형식으로 나간다.

> **면접:** "인증은 Spring Security 리소스 서버가 표준대로 하고, 저는 그 다음 단계, 즉 '토큰의 조직을 우리 DB의 현재 멤버십으로 확인해 테넌트와 역할을 정하는 필터'를 만들었습니다. 토큰은 로그인 시점의 사실이고 멤버십은 지금의 사실이라서, 비활성화된 멤버는 토큰이 살아 있어도 바로 막힙니다."

---

## 2. 테넌트 격리 — 왜 두 겹인가

| 겹 | 어디서 | 무엇을 막나 |
|---|---|---|
| 앱 | Hibernate `@TenantId` | 리포지토리에 `tenantId` 조건을 안 써도 자동으로 붙는다. 저장할 때 값도 자동으로 채운다 |
| DB | Postgres RLS (`FORCE`) | Hibernate를 우회한 SQL(`JdbcClient`, 실수한 native query)도 막는다 |

### 2.1 설계 문서와 달라진 점 — DataSource 래퍼
- 설계 문서에는 Hibernate의 `MultiTenantConnectionProvider`로 커넥션에 테넌트를 건다고 썼다.
- 구현하면서 **DataSource 자체를 감싸는 방식**(`TenantAwareDataSource`)으로 바꿨다.
- **이유:** `MultiTenantConnectionProvider`는 Hibernate가 꺼내는 커넥션에만 적용된다. `JdbcClient`(대시보드 집계용)는 DataSource에서 직접 커넥션을 꺼내므로 RLS 설정이 빠진다. DataSource를 감싸면 **누가 꺼내든** 같은 규칙이 적용된다. 테스트 `RowLevelSecurityIT`가 `JdbcClient`로 이걸 증명한다.

### 2.2 커넥션 풀 함정 (면접에서 먼저 꺼낼 것)
- 커넥션은 요청이 끝나면 풀로 돌아가 **다른 회사의 요청이 재사용**한다.
- 그래서 커넥션을 돌려줄 때(`close()`) **`RESET app.tenant_id`** 를 실행하는 프록시를 씌웠다.
- 테스트 `ConnectionPoolTenantLeakIT`는 풀 크기를 1로 고정해 **같은 물리 커넥션**을 다시 꺼내도 이전 회사 값이 남지 않음을 증명한다.

### 2.3 `NULLIF` 한 단어의 이유
```sql
SELECT NULLIF(current_setting('app.tenant_id', true), '')::uuid
```
- 한 번 쓰인 사용자 정의 설정은 `RESET` 후 `NULL`이 아니라 **빈 문자열 `''`** 이 된다. `''::uuid`는 에러다.
- `NULLIF`로 빈 문자열을 `NULL`로 바꾸면 → `tenant_id = NULL`은 항상 거짓 → **컨텍스트가 없으면 아무 행도 안 보인다(fail-closed).** `RowLevelSecurityIT.noContextSeesNothing`이 이걸 확인한다.

### 2.4 왜 DB 계정을 나눴나
- Postgres에서 **테이블 소유자와 슈퍼유저는 RLS를 우회**한다. 앱이 소유자로 접속하면 RLS는 장식이다.
- `lms_owner`(마이그레이션), `lms_app`(앱, DML만), `keycloak`(분리). `RlsPolicyCoverageIT`가 "앱 계정은 소유자도 우회 권한자도 아니다"를 검사한다.

### 2.5 "내가 속한 회사 목록"은 어떻게 다른 회사 행을 읽나
- RLS 때문에 앱은 다른 회사의 멤버십을 못 본다. 그런데 로그인 후 "회사 선택" 화면은 내 모든 소속이 필요하다.
- 정책을 느슨하게 하지 않고 **`SECURITY DEFINER` 함수 `user_memberships(user_id)`** 를 만들었다. 함수는 소유자 권한으로 돌고, **지정한 사용자의 행만, 표시용 컬럼만** 돌려준다. `FORCE` 때문에 소유자도 RLS를 받으므로 소유자 전용 읽기 정책(`owner_read`)을 하나 더 뒀다.
- `SET search_path`를 고정한 이유: SECURITY DEFINER 함수에서 search_path를 비워두면, 호출자가 같은 이름의 가짜 테이블을 만들어 끼워 넣는 공격이 가능하다(Postgres 공식 권고).

### 2.6 테넌트가 없을 때 Hibernate는?
- `@TenantId`를 쓰면 Hibernate는 세션을 열 때마다 "지금 테넌트가 누구냐"를 묻는다. 그런데 테넌트를 **찾는 과정**(토큰의 조직 → `tenant` 테이블 조회)에는 아직 테넌트가 없다.
- 그래서 리졸버는 테넌트가 없으면 예외 대신 **아무와도 일치하지 않는 값(`0000…`)** 을 돌려준다. 전역 테이블(`tenant`, `app_user`)은 `@TenantId`가 없어서 정상 조회되고, 테넌트 테이블은 0건, 쓰기는 RLS가 거부한다.

---

## 3. 회사 등록 — 두 시스템에 걸친 작업 (사가)

`POST /v1/tenants` → `TenantRegistrationService`
1. 예약어·중복 slug 확인
2. **Keycloak 조직 생성** → 창업자를 조직 멤버로 추가
3. **우리 DB 트랜잭션**: tenant + ORG_ADMIN 멤버십
4. 3이 실패하면 **보상 작업**: Keycloak 조직 삭제

- Keycloak과 Postgres는 하나의 트랜잭션으로 묶을 수 없다. **되돌리기 쉬운 쪽을 먼저** 하고, 실패하면 되돌린다.
- 3에서 **tenantId를 먼저 정하고 그 테넌트 컨텍스트에 들어간 뒤** 트랜잭션을 연다. 커넥션은 트랜잭션이 시작될 때 테넌트에 묶이기 때문이다. 그래야 멤버십 INSERT가 RLS `WITH CHECK`를 통과한다.

> **면접:** "외부 시스템과 DB를 원자적으로 묶을 수 없어서 사가 패턴의 가장 작은 형태로 보상 작업을 넣었고, 보상도 실패하면 정합성 점검 작업이 정리하도록 했습니다."

---

## 4. 작지만 물어볼 만한 것들

| 코드 | 왜 |
|---|---|
| `AbstractEntity implements Persistable` | ID를 직접 생성(UUID v7)하면 Spring Data가 "이미 있는 행"으로 착각해 INSERT 전에 SELECT를 한 번 더 한다(`merge`). `isNew` 플래그로 `persist`를 타게 한다 |
| `UuidV7` 직접 구현 | Java 21 표준 라이브러리에 v7이 없다. 앞 48비트가 밀리초 시각이라 정렬되고 인덱스에 끝으로 붙는다 |
| JIT 사용자 생성의 `DataIntegrityViolationException` 처리 | 같은 사용자의 첫 요청 두 개가 동시에 오면 둘 다 INSERT를 시도한다. 유니크 제약이 하나를 막고, 진 쪽은 다시 조회한다 |
| `spring.jpa.open-in-view: false` | 켜져 있으면 컨트롤러가 응답을 만드는 중에도 지연 로딩 쿼리가 나간다. 쿼리 위치를 서비스 계층으로 한정한다 |
| `ddl-auto: validate` | 스키마는 Flyway만 바꾼다. Hibernate는 엔티티와 스키마가 맞는지만 확인한다 |
| 다른 회사 리소스는 **404** | 403은 "있긴 있다"는 정보를 준다 |
| 테스트에서 `jwt()` 후처리기 | 서명 검증은 Spring Security의 책임이고 그쪽 테스트가 있다. 우리가 증명할 것은 **클레임을 받은 뒤 우리 코드가 하는 일**이다 |
| H2 대신 Testcontainers | H2에는 RLS도 SECURITY DEFINER도 없다. 가장 중요한 테스트가 의미 없어진다 |
| `ModularityTest` | `organization`이 `tenancy.internal`을 직접 부르면 빌드가 깨진다. 모듈 경계를 약속이 아니라 테스트로 지킨다 |

### Spring의 "마법" = 프록시
- `@Transactional`, `@PreAuthorize`는 Spring이 객체를 감싼 **프록시**가 처리한다.
- **같은 클래스 안에서 자기 메서드를 부르면 프록시를 거치지 않아 적용되지 않는다(self-invocation).** 그래서 `DepartmentController`(권한)와 `DepartmentService`(트랜잭션)를 나눴고, 여러 단계가 필요한 곳은 `TransactionTemplate`으로 경계를 코드에 명시했다.

---

## 5. Keycloak 설정에서 짚을 것 (`lms-realm.json`)

| 설정 | 의미 |
|---|---|
| `organizationsEnabled: true` | 회사 = Keycloak 조직. 이메일 우선 로그인, 조직별 SSO의 기반 |
| `lms-web`: confidential + PKCE S256 | BFF만 토큰을 교환할 수 있다. 인가 코드가 탈취돼도 `code_verifier` 없이는 못 쓴다 |
| `audience-lms-api` 매퍼 | 토큰의 `aud`에 `lms-api`를 넣는다. API는 자기 앞으로 발급된 토큰만 받는다 |
| `lms-admin` 서비스 계정 | Spring이 조직을 만들 때 쓰는 기계용 계정(client credentials), 필요한 관리 역할만 |
| `revokeRefreshToken: true`, `refreshTokenMaxReuse: 0` | Refresh Token 회전 + 재사용 감지 |
| `bruteForceProtected`, `passwordPolicy` | 무차별 대입 잠금, 비밀번호 정책 |
| `accessTokenLifespan: 300` | Access Token 5분 |

---

## 6. BFF 로그인 (Next.js) — 코드 위치와 설명 포인트

| 파일 | 하는 일 | 면접 포인트 |
|---|---|---|
| `app/auth/login/route.ts` | `state`, `nonce`, PKCE `code_verifier` 생성 → Redis `login:<state>`(5분) → Keycloak으로 이동 | 브라우저에는 S256 **challenge**만 간다. verifier는 서버에만 있다 |
| `app/auth/callback/route.ts` | `GETDEL`로 state를 **한 번만** 꺼냄 → 코드 교환(PKCE + client secret) → ID 토큰 검증 → **새 세션 ID** 발급 | 재생 공격 방지(1회용 state), 세션 고정 공격 방지(로그인마다 새 ID) |
| `server/session.ts` | Redis 세션, 만료 30초 전 갱신, **갱신 분산 락**(`SET NX EX`, 소유자만 해제하는 Lua) | 회전 방식에서 동시 갱신 = 재사용 감지 → 정상 사용자 로그아웃. 락으로 한 번만 갱신 |
| `app/auth/logout/route.ts` | POST 전용 + Origin 검사 → Redis 삭제 → Keycloak 로그아웃(RP-Initiated) | `<img src="/auth/logout">` 같은 CSRF 로그아웃 차단 |
| `proxy.ts` | 쿠키 **존재만** 확인해 `/auth/login`으로 보냄 | Next.js 16에서 `middleware.ts` → `proxy.ts`로 이름이 바뀌었다. 진짜 검증은 서버의 `getSession()` |
| `lib/return-to.ts` | 로그인 후 돌아갈 주소는 같은 사이트의 상대 경로만 | 오픈 리디렉트 방지(`//evil.com`, `/\evil.com`) |
| `app/onboarding/actions.ts` | 서버 액션 → `POST /v1/tenants` → `organization:<slug>`로 재로그인 | 새 조직이 토큰에 담기려면 토큰을 다시 받아야 한다 |

**실제로 확인한 흐름 (2026-11-06, 로컬):** 회원가입(Keycloak) → 콜백 → 세션 → 회사 등록 → Keycloak 조직 `acme` 생성 → 재로그인 → `/app`에 "에이크미 / 관리자" 표시. psql로 `lms_app` 계정에서 회사 컨텍스트 없이 `membership`을 조회하면 **0건**(RLS)이었다.

---

## 7. 오늘 미룬 것 (내일 이후)

- 멤버십 조회 Redis 캐시(60초) — 지금은 요청마다 DB 조회
- 초대(INVITED → ACTIVE), 부서 수정·삭제, 멤버 관리 — Day 2
- springdoc → 프론트 타입 생성 — Day 2
- Testcontainers로 Keycloak까지 띄우는 E2E — Day 5 (Playwright)
