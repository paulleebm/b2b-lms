# syntax=docker/dockerfile:1.7
# Next.js BFF image. Build context = frontend/ (the pnpm workspace):
#   docker build -f apps/web/Dockerfile --build-arg KEYCLOAK_ISSUER=… --build-arg STORAGE_ORIGIN=… .
# The runtime image holds only Next's standalone output (traced server files), not the workspace.

FROM node:22-alpine AS base
RUN corepack enable
WORKDIR /repo

FROM base AS deps
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/web/package.json apps/web/
COPY packages/ui/package.json packages/ui/
COPY packages/api-client/package.json packages/api-client/
RUN --mount=type=cache,id=pnpm,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile --filter web...

FROM deps AS build
COPY . .
# The CSP is computed at build time (next.config.ts): it needs the Keycloak and storage origins.
ARG KEYCLOAK_ISSUER=http://localhost:8080/realms/lms
ARG STORAGE_ORIGIN=http://localhost:9090
ENV KEYCLOAK_ISSUER=$KEYCLOAK_ISSUER STORAGE_ORIGIN=$STORAGE_ORIGIN NEXT_TELEMETRY_DISABLED=1
RUN pnpm --filter web build

FROM node:22-alpine AS runtime
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 PORT=3000 HOSTNAME=0.0.0.0
COPY --from=build --chown=app:app /repo/apps/web/.next/standalone ./
COPY --from=build --chown=app:app /repo/apps/web/.next/static ./apps/web/.next/static
USER app
EXPOSE 3000
CMD ["node", "apps/web/server.js"]
