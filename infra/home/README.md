# 홈 배포 운영: 미니 PC(Portainer) 한 대에 전부

`bymile.site`를 실제로 띄우는 순서입니다. 구성과 선택 이유는 [docs/11](../../docs/11-home-deployment.md)에 있습니다.

```
app.bymile.site    → 공유기 80·443 → 미니 PC: caddy → web (Next.js BFF)
auth.bymile.site   →                               → keycloak  (/admin·master 렐름은 집 안에서만)
files.bymile.site  →                               → garage    (S3 API, presigned URL)
inbox.bymile.site  →                               → mailpit   (집 안에서만)
API·DB·큐·캐시     → 공개 주소 없음 (Docker 내부망)
```

| 파일 | 역할 |
|---|---|
| `docker-compose.yml` | Portainer 스택. 이미지만 씁니다(빌드·바인드 마운트 없음) |
| `gen-stack-env.sh` | 스택 변수 파일 `stack.env`를 비밀값과 함께 생성 (gitignored) |
| `build-images.sh` | 이미지 8개를 빌드해서 Docker Hub(`docker.io/paulleebm`)에 푸시 |
| `caddy/` | 유일한 입구. Let's Encrypt 인증서 자동 발급·갱신, 관리 화면 차단, 파일 CORS |
| `garage/`, `storage-init/` | S3 호환 저장소와, 노드 배치·접근 키·버킷을 만드는 1회성 작업 |
| `postgres/`, `elasticmq/` | 역할 생성 스크립트와 큐 설정을 넣은 이미지 (Portainer에서 파일을 마운트하지 않으려고) |
| `rehearsal/` | 같은 스택을 이 PC에서 띄워 보는 설정 (HTTP + `*.localhost`) |

## 0. 네트워크 준비 (한 번, 직접)

1. **미니 PC 내부 IP 고정:** 공유기 관리 화면 → DHCP 서버 설정 → 미니 PC MAC 주소에 IP 예약(예: `192.168.0.10`).
2. **포트포워딩:** 공유기에서 외부 TCP 80, TCP 443, UDP 443(HTTP/3)을 미니 PC 같은 포트로 넘깁니다.
3. **공인 IP 확인:** 공유기 관리 화면 첫 페이지의 외부 IP. 브라우저에서 "내 IP"를 검색해도 됩니다.
4. **가비아 DNS:** My가비아 → 도메인 관리 → `bymile.site` → DNS 설정에 A 레코드 4개를 넣습니다.

   | 호스트 | 타입 | 값 |
   |---|---|---|
   | `app` | A | 공인 IP |
   | `auth` | A | 공인 IP |
   | `files` | A | 공인 IP |
   | `inbox` | A | 공인 IP |

   확인: `nslookup app.bymile.site`가 공인 IP를 돌려주면 됩니다.

> **가정용 회선은 IP가 바뀔 수 있습니다.** 자주 바뀌지는 않지만, 바뀌면 사이트가 멈춥니다. 공유기가 DDNS를 지원하면(ipTIME은 `xxx.iptime.org`) A 레코드 대신 그 이름을 가리키는 **CNAME**으로 넣어 두면 IP가 바뀌어도 따라갑니다.

## 1. 스택 변수 만들기 (이 PC에서)

```bash
./infra/home/gen-stack-env.sh
```

- `infra/home/stack.env`가 만들어집니다. DB 비밀번호, Keycloak 클라이언트 비밀값, Garage 키가 들어 있습니다.
- **저장소에 올라가지 않고(gitignore), 이 파일이 유일한 사본입니다.** 비밀번호 관리자 같은 곳에 보관합니다.
- 이미 파일이 있으면 덮어쓰지 않습니다. DB 볼륨이 옛 비밀번호를 기억하고 있어서, 새로 만들면 스택이 자기 데이터에 접속하지 못합니다.

## 2. 이미지 푸시 (이 PC에서)

```bash
IMAGE_PREFIX=docker.io/paulleebm ./infra/home/build-images.sh
```

- Docker Desktop에서 `paulleebm`으로 로그인되어 있어야 합니다.
- 미니 PC가 x86(인텔/AMD)이라 `linux/amd64`로 빌드합니다. ARM 장비라면 `PLATFORM=linux/arm64`를 붙입니다.
- 커밋 해시와 `latest` 두 태그로 올라갑니다: `lms-{api,web,keycloak,postgres,elasticmq,garage,storage-init,caddy}`.
- 저장소는 공개입니다. 이미지에 비밀값이 없으므로 Portainer에 레지스트리 로그인이 필요 없습니다.
- 웹 이미지의 CSP 헤더는 빌드할 때 정해지므로 `auth.bymile.site`, `files.bymile.site` 기준으로 빌드됩니다. 도메인을 바꾸면 `DOMAIN=...`을 붙여 다시 빌드합니다.

## 3. Portainer (미니 PC)

1. Stacks → Add stack → 이름 `lms` → **Web editor**에 `docker-compose.yml` 내용을 붙여 넣습니다.
2. Environment variables → **Load variables from .env file**로 `stack.env`를 넣습니다.
3. Deploy the stack.

정상 상태:
- `caddy`, `postgres`, `valkey`, `elasticmq`, `garage`, `mailpit`, `keycloak`, `api`, `worker`, `web`은 **running**
- `storage-init`, `seed`는 **exited (0)**: 저장소 준비와 데모 데이터 넣기를 마치고 끝난 상태입니다.
- `caddy` 로그에 `certificate obtained successfully`가 도메인 4개만큼 보입니다. 안 보이면 0단계(포트포워딩, DNS)를 다시 확인합니다.

그다음 https://app.bymile.site 에서 `learner@demo-elec.test` / `Demo-2026-lms`로 로그인합니다.

**휴대폰 LTE(집 밖 네트워크)로 한 번 확인합니다:** https://auth.bymile.site/admin 과 https://inbox.bymile.site 가 **404**여야 합니다. Caddy는 요청의 출발지 IP로 집 안인지 판단하는데, Docker가 출발지 IP를 바꿔 전달하는 환경이면(예: IPv6로 들어온 요청) 바깥 요청도 집 안처럼 보일 수 있기 때문입니다. 그래서 DNS에는 IPv4(A 레코드)만 넣습니다.

**관리 화면 (집 안 네트워크에서만):**
- Keycloak 관리 콘솔: https://auth.bymile.site/admin, 사용자 `admin`, 비밀번호는 `stack.env`의 `KEYCLOAK_ADMIN_PASSWORD`
- 데모 메일함: https://inbox.bymile.site

## 업데이트와 롤백

| 바꾼 것 | 할 일 |
|---|---|
| 코드 (백엔드·프론트 모두) | `build-images.sh` → Portainer에서 스택 **Update**("Re-pull image" 켜기) |
| 롤백 | 스택 변수 `IMAGE_TAG`를 이전 커밋 해시로 바꾸고 Update. DB 마이그레이션은 앞으로만 가므로, 스키마를 바꾼 배포는 되돌리기 전에 확인 |
| 렐름 설정(`lms-realm.json`) | 가져오기는 **렐름이 없을 때만** 실행. 이미 있는 렐름은 관리 콘솔에서 바꿈 |

**데이터는 볼륨에 있습니다:** `pgdata`(DB), `garage_meta`·`garage_data`(파일), `caddy_data`(인증서), `valkeydata`(세션), `maildata`. 스택을 지울 때 볼륨까지 지우지 않도록 주의합니다. 정기 백업은 아직 없습니다([docs/11](../../docs/11-home-deployment.md#4-한계와-다음-단계)).

## 리허설: 같은 스택을 이 PC에서

```bash
IMAGE_PREFIX=local LOAD=1 PUBLIC_SCHEME=http DOMAIN=localhost ./infra/home/build-images.sh
docker compose -p lms-rehearsal --env-file infra/home/rehearsal/rehearsal.env \
  -f infra/home/docker-compose.yml -f infra/home/rehearsal/compose.rehearsal.yml up -d
```

- 브라우저에서 http://app.localhost 로 엽니다. 브라우저는 `*.localhost`를 자기 PC로 연결합니다.
- 실제 스택과 다른 점은 HTTP라는 것(인증서 없음)과 `*.localhost` 이름뿐입니다.
- Playwright E2E: `E2E_BASE_URL=http://app.localhost`로 실행합니다.
- 끝나면 `docker compose -p lms-rehearsal ... down -v`로 지웁니다. 로컬 개발용 `lms` 프로젝트와 볼륨이 분리되어 있습니다.
