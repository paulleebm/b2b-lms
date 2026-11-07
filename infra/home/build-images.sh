#!/usr/bin/env bash
# Build the four images the home stack runs and push them to Docker Hub (or load them locally).
#
#   IMAGE_PREFIX=docker.io/<user> ./infra/home/build-images.sh            # push, linux/amd64
#   IMAGE_PREFIX=docker.io/<user> PLATFORM=linux/arm64 ./infra/home/build-images.sh
#   IMAGE_PREFIX=local LOAD=1 ./infra/home/build-images.sh                # this machine only (rehearsal)
#
# Each image is tagged with the git commit and with "latest". Portainer pulls ${IMAGE_TAG:-latest};
# pinning IMAGE_TAG to a commit in the stack makes a rollback "change one variable, redeploy".
# The images hold no secrets: every password and client secret comes from the stack's environment.
set -euo pipefail

: "${IMAGE_PREFIX:?set IMAGE_PREFIX, e.g. docker.io/yourname}"
PLATFORM="${PLATFORM:-linux/amd64}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SHA="$(git -C "$ROOT" rev-parse --short HEAD)"

if [[ "${LOAD:-0}" == "1" ]]; then
  OUTPUT=(--load)
else
  OUTPUT=(--push)
fi

build() { # name context [dockerfile]
  local name="$1" context="$2" file="${3:-$2/Dockerfile}"
  echo "==> ${IMAGE_PREFIX}/${name}:${SHA} (${PLATFORM})"
  docker buildx build --platform "$PLATFORM" "${OUTPUT[@]}" \
    -t "${IMAGE_PREFIX}/${name}:${SHA}" -t "${IMAGE_PREFIX}/${name}:latest" \
    -f "$file" "$context"
}

build lms-api       "$ROOT/backend"
build lms-keycloak  "$ROOT/infra/keycloak"
build lms-postgres  "$ROOT/infra/home/postgres"
build lms-elasticmq "$ROOT/infra" "$ROOT/infra/home/elasticmq/Dockerfile"

echo "Done. In Portainer: IMAGE_PREFIX=${IMAGE_PREFIX}, IMAGE_TAG=${SHA} (or latest), then Update the stack with \"Re-pull image\"."
