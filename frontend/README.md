# LMS 프론트엔드

pnpm 워크스페이스: `apps/web`(Next.js 16 App Router, BFF)과 `packages/ui`(shadcn/ui 방식 디자인 시스템 + Storybook).

## 실행

```bash
corepack enable                       # package.json의 packageManager(pnpm 10)를 사용
pnpm install
cp apps/web/.env.example apps/web/.env.local   # 값은 시작 시 zod로 검증
pnpm dev                              # http://localhost:3000 (Keycloak·Redis·API는 infra/ docker compose)
pnpm storybook                        # http://localhost:6006
pnpm typecheck && pnpm lint && pnpm build && pnpm build-storybook
```

## BFF 인증 흐름 (docs/02 B7, docs/05 §1·§4)

1. `GET /auth/login[?org=별칭]` — state·nonce·PKCE verifier를 만들어 Redis `login:<state>`(5분)에 두고 Keycloak으로 302. scope는 `openid profile email organization`(조직 지정 시 `organization:<별칭>`).
2. `GET /auth/callback` — `login:<state>`를 꺼내며 삭제(1회용) → code + verifier + client secret으로 토큰 교환, ID 토큰 서명·nonce 검증 → **새** 세션 ID(32바이트) 발급, Redis `session:<id>`에 토큰 저장 → `__Host-session` 쿠키(HttpOnly, Secure, SameSite=Lax)만 브라우저로.
3. 서버 컴포넌트·서버 액션은 `apiFetch()`로 Spring API 호출. 만료 30초 전이면 `refresh-lock:<id>`(SET NX EX 10) 락을 잡은 요청만 갱신하고, 나머지는 최대 2초 기다렸다 세션을 다시 읽는다(Refresh Token 회전 시 재사용 오탐 방지).
4. `POST /auth/logout` — Redis 세션·쿠키 삭제 후 Keycloak end-session(`id_token_hint`)으로 303.
5. `src/proxy.ts`(Next 16의 middleware)는 `/app/**`, `/onboarding`에서 쿠키 **존재만** 확인한다. 진짜 검증은 서버의 `getSession()`.

토큰·Redis·Keycloak 코드는 모두 `apps/web/src/server/`(`import "server-only"`)에 있어 클라이언트 번들에 들어갈 수 없다.
