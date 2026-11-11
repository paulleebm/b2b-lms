#!/usr/bin/env bash
# Build the images the home stack runs and push them to Docker Hub (or load them locally).
#
#   IMAGE_PREFIX=docker.io/paulleebm ./infra/home/build-images.sh        # push, linux/amd64
#   IMAGE_PREFIX=docker.io/paulleebm PLATFORM=linux/arm64 ./infra/home/build-images.sh
#   IMAGE_PREFIX=local LOAD=1 PUBLIC_SCHEME=http DOMAIN=localhost ./infra/home/build-images.sh   # rehearsal
#
# Each image is tagged with the git commit and with "latest". Portainer pulls ${IMAGE_TAG:-latest};
# pinning IMAGE_TAG to a commit in the stack makes a rollback "change one variable, redeploy".
# The images hold no secrets: every password, key and client secret comes from the stack's env.
#
# One exception to "same image everywhere": the web image's Content-Security-Policy is computed
# at build time (next.config.ts), so it is built for one set of public names (DOMAIN below).
set -euo pipefail

: "${IMAGE_PREFIX:?set IMAGE_PREFIX, e.g. docker.io/yourname}"
PLATFORM="${PLATFORM:-linux/amd64}"
PUBLIC_SCHEME="${PUBLIC_SCHEME:-https}"
DOMAIN="${DOMAIN:-bymile.site}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SHA="$(git -C "$ROOT" rev-parse --short HEAD)"

if [[ "${LOAD:-0}" == "1" ]]; then
  OUTPUT=(--load)
else
  OUTPUT=(--push)
fi

build() { # name context dockerfile [extra buildx args...]
  local name="$1" context="$2" file="$3"
  shift 3
  echo "==> ${IMAGE_PREFIX}/${name}:${SHA} (${PLATFORM})"
  docker buildx build --platform "$PLATFORM" "${OUTPUT[@]}" \
    -t "${IMAGE_PREFIX}/${name}:${SHA}" -t "${IMAGE_PREFIX}/${name}:latest" \
    -f "$file" "$@" "$context"
}

build lms-api          "$ROOT/backend"                "$ROOT/backend/Dockerfile"
build lms-web          "$ROOT/frontend"               "$ROOT/frontend/apps/web/Dockerfile" \
  --build-arg "KEYCLOAK_ISSUER=${PUBLIC_SCHEME}://auth.${DOMAIN}/realms/lms" \
  --build-arg "STORAGE_ORIGIN=${PUBLIC_SCHEME}://files.${DOMAIN}"
build lms-keycloak     "$ROOT/infra/keycloak"         "$ROOT/infra/keycloak/Dockerfile"
build lms-postgres     "$ROOT/infra/home/postgres"    "$ROOT/infra/home/postgres/Dockerfile"
build lms-elasticmq    "$ROOT/infra"                  "$ROOT/infra/home/elasticmq/Dockerfile"
build lms-garage       "$ROOT/infra/home/garage"      "$ROOT/infra/home/garage/Dockerfile"
build lms-storage-init "$ROOT/infra/home/storage-init" "$ROOT/infra/home/storage-init/Dockerfile"
build lms-caddy        "$ROOT/infra/home/caddy"       "$ROOT/infra/home/caddy/Dockerfile"

echo "Done. In Portainer: IMAGE_TAG=${SHA} (or latest), then Update the stack with \"Re-pull image\"."
