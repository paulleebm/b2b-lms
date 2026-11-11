# Day 2 면접 설명 노트: 멤버·초대, 코스 스튜디오, 직접 업로드

> Day 1과 같은 형식이다. 항목마다 무엇을, 왜 그렇게 했는지, 면접에서 어떻게 말할지를 적었다.

## 0. 오늘 만든 것 한눈에

```
관리자 ─초대─▶ API ─(1) 계정·조직 가입─▶ Keycloak ─(3) 비밀번호 설정 메일─▶ 직원
                 └─(2) 멤버십 INVITED─▶ PostgreSQL        직원 첫 로그인 → ACTIVE (+ MemberJoined 이벤트)

강사 ─코스 편집(If-Match)─▶ API ─▶ course / course_section / lesson (version = ETag)
강사 ─파일─▶ ① POST /v1/uploads (URL 발급) ② PUT S3 직접 ③ POST complete (API가 HeadObject로 검증) ─▶ 영상이면 변환
```

| 위치 | 파일 | 역할 |
|---|---|---|
| DB | `V2__tenant_scoped_foreign_keys.sql` | 외래키를 `(tenant_id, id)` 복합키로 |
| DB | `V3__catalog.sql` | 코스 → 섹션 → 레슨, 지연 유니크 제약(순서 변경용) |
| DB | `V4__files.sql` | 업로드 파일 기록, 영상 자산 |
| 백엔드 | `tenancy/internal/MemberService` | 멤버 목록·역할·상태, 초대(사가), 초대 취소 |
| 백엔드 | `catalog/internal/*` | 코스 집합체, ETag/If-Match, 게시 검증 |
| 백엔드 | `files/internal/*` | presigned PUT / multipart, 검증, 가짜 변환기 |
| 백엔드 | `shared/web/OpenApiConfig` | OpenAPI 계약을 실제 JSON과 정확히 맞춤 |
| 계약 | `frontend/packages/api-client` | `openapi.json`(테스트가 내보냄) → TS 타입 생성 |
| 프론트 | `app/app/layout.tsx`, `components/app-frame.tsx` | `/v1/me` 한 번, 역할별 메뉴 |
| 프론트 | `app/api/v1/[...path]/route.ts` | 클라이언트 컴포넌트용 BFF 패스스루 (Origin 검사) |
| 프론트 | `app/app/members`, `app/app/studio` | 멤버 관리, 코스 목록·편집기 |
| 프론트 | `lib/upload.ts` | 브라우저 → S3 직접 업로드 (진행률, 파트 재시도, 취소) |

테스트: 백엔드 49개(Day 1의 20개 + 29개), 스토리 테스트 97개(접근성 검사 포함). 브라우저로 초대 → 메일 → 비밀번호 설정 → 첫 로그인 → 코스 작성 → 영상 업로드 → 게시까지 직접 돌려 봤다.

---

## 1. 외래키는 RLS를 우회한다: 복합 외래키 (V2)

- **무엇:** `membership.department_id → department(id)`를 `(tenant_id, department_id) → department(tenant_id, id)`로 바꿨다. 코스·섹션·레슨·파일도 전부 이 방식이다.
- **왜:** PostgreSQL의 외래키 검사는 RLS를 거치지 않는다. A회사 세션에서 B회사 부서 ID를 알아내 넣으면, 단일 외래키는 "그 ID가 존재한다"며 통과시킨다. 복합키로 묶으면 "같은 회사 안에 존재해야" 통과한다.
- **증명:** `RowLevelSecurityIT.foreignKeyCannotPointIntoAnotherTenant`가 앱을 거치지 않는 raw SQL로 이 공격을 하고, `membership_department_fk` 위반으로 막히는 것을 확인한다.
- **면접에서:** "RLS를 켜고 끝내지 않았습니다. 외래키 검사가 RLS 바깥에서 돈다는 걸 확인하고, 참조마다 테넌트를 키에 넣었습니다."

## 2. 초대: 세 단계짜리 사가와 보상 범위

순서는 Keycloak 계정 확보·조직 가입 → DB에 `INVITED` 멤버십 → 비밀번호 설정 메일이다.

| 실패 지점 | 처리 | 이유 |
|---|---|---|
| 1단계 (Keycloak) | 그 주소만 `FAILED` | 아직 아무것도 안 바뀜 |
| 2단계 (DB) | 조직 가입을 되돌림 (보상) | 단, **이번 호출이 가입시킨 경우에만**. 원래 회원이던 사람을 빼면 안 된다 |
| 3단계 (메일) | 되돌리지 않음, `INVITED` + 안내 문구 | 초대는 이미 유효하다. 같은 주소로 다시 초대하면 메일을 다시 보낸다 |

- **실제로 겪은 버그:** 처음엔 메일 발송까지 한 `try`에 넣었다. 메일이 실패하자(Keycloak에 redirect URI 미등록) 이미 커밋된 초대를 두고 조직 가입만 되돌려서, 화면은 "실패"인데 목록엔 "초대됨"이 남았다. **보상은 되돌릴 수 있는 단계까지만** 거는 게 원칙이다. 회귀 테스트로 `emailFailureKeepsTheInvitation`를 추가했다.
- **활성화:** 초대받은 사람이 처음 API를 부르면 `update ... where status = 'INVITED'`(조건부 업데이트)로 정확히 한 번만 `ACTIVE`가 되고 `MemberJoined` 이벤트가 나간다. Day 3의 "신입 자동 배정"이 이 이벤트를 받는다.
- **비밀번호가 이미 있는 계정**(다른 회사 소속)에는 비밀번호 설정 메일을 보내지 않는다. 보내면 남의 비밀번호를 강제로 재설정시키게 된다.
- **Keycloak 특성:** 메일 링크로 비밀번호를 정해도 로그인 세션은 생기지 않는다(관리자가 시킨 작업이라서). 그래서 링크 끝을 `/auth/login`으로 두었고, 한 번 더 로그인한다.

## 3. "관리자 0명" 막기: 비관적 잠금

- 검사("관리자가 2명 이상인가") 후 변경은 전형적인 check-then-act 경쟁이다. 관리자 둘이 동시에 서로를 강등하면 둘 다 검사를 통과한다.
- `lockActiveByRole`이 활성 관리자 행을 `SELECT ... FOR UPDATE`(id 순서 고정, 교착 방지)로 잡고 센다. 나중 요청은 먼저 요청의 커밋을 기다린 뒤 1명을 보고 `409 LAST_ADMIN`을 받는다.
- 자기 자신의 관리자 권한은 못 내린다(`CANNOT_DEMOTE_SELF`). 동시성 테스트는 두 스레드로 실제 경쟁을 만들고 "정확히 하나만 성공, 남은 관리자 1명"을 확인한다.
- **왜 낙관적 잠금이 아닌가:** 바뀌는 행(상대방)과 지켜야 할 불변식(관리자 수)이 다른 행에 걸쳐 있다. 행 버전으로는 이걸 못 지킨다.

## 4. 코스 편집 동시성: HTTP 표준 그대로 (ETag / If-Match)

- `course.version`(`@Version`)을 `ETag: "3"`으로 내보내고, 모든 쓰기에 `If-Match`를 요구한다. 없으면 `428`, 다르면 `412`다. 각각 RFC 9110, RFC 6585에 있는 방식이다.
- 섹션·레슨을 바꿔도 코스의 `touch()`로 버전이 오른다. 코스가 집합체 루트이기 때문이다.
- **두 겹:** If-Match 비교(대부분의 경우) + 같은 순간 두 요청이 같은 버전으로 들어오면 Hibernate의 `where version = ?`가 막는다(`409 CONCURRENT_UPDATE`).
- **응답은 항상 코스 전체 + 새 ETag:** 편집기는 받은 걸 그대로 화면에 반영하고 다음 요청에 그 ETag를 쓴다. 클라이언트가 버전을 계산하지 않는다.
- **순서 변경:** `PUT /outline`에 전체 순서를 보낸다. `(course_id, position)` 유니크 제약을 `DEFERRABLE INITIALLY DEFERRED`로 둬서, 위치를 서로 바꾸는 중간 상태를 허용하고 커밋 때 한 번 검사한다. 요청이 기존 섹션·레슨 목록과 다르면(그사이 누가 추가함) `409 OUTLINE_MISMATCH`다.
- **게시 규칙:** 섹션 1개 이상, 빈 섹션 없음, 영상은 변환 완료(`READY`), 문서는 업로드 검증 완료, 텍스트는 본문 있음. 어기면 `422`와 함께 `errors: [{code, message, sectionId, lessonId}]`가 오고, 편집기는 이걸 눌러서 해당 레슨으로 가는 목록으로 보여 준다. 게시된 코스는 목차가 잠기고, 고치려면 게시를 취소한다.

## 5. 업로드: 바이트는 API를 지나지 않는다

| 단계 | 누가 | 무엇을 검사하나 |
|---|---|---|
| init | API | 용도별 허용 형식(파일명이 아니라 선언된 MIME), 크기 상한(영상 5GB, 문서 50MB) |
| PUT | 브라우저 → S3 | 서명에 Content-Type·Content-Length가 들어가 다른 파일을 못 올림 |
| complete | API | `HeadObject`로 실제 크기·형식 대조. 다르면 객체 삭제 + `ABORTED` |

- **객체 키:** `tenants/{회사}/files/{id}/source`. 사용자가 보낸 파일명은 키에 절대 넣지 않는다(경로 조작, 인코딩 문제 차단). 파일명은 DB에만 두고 다운로드 때 `Content-Disposition`으로 돌려준다.
- **100MB 초과는 multipart:** 16MB 파트, 동시 3개, 파트마다 2번 재시도. 파트 URL은 20개씩 진행에 맞춰 받는다(15분 만료 전에 쓰도록).
- **남이 시작한 업로드는 같은 회사 사람이라도 완료·취소 불가**(404). 업로드한 멤버십 ID와 비교한다.
- **영상 변환:** complete가 커밋된 **뒤에** 변환을 시작한다(`@TransactionalEventListener`). 롤백된 영상을 변환하는 일이 없게 하려는 것이다. 로컬은 가짜 변환기가 3초 뒤 `READY`를 주고, AWS는 MediaConvert → EventBridge → SQS로 같은 `VideoStateUpdater`를 부른다. 완료 알림은 중복될 수 있으므로 `markReady`는 멱등이다.
- **로컬 S3: Adobe S3Mock.** LocalStack은 2026년 3월부터 인증 토큰이 필요하고, MinIO 커뮤니티판은 배포가 중단됐다. S3Mock은 **서명을 검증하지 않는다.** 그래서 "서명이 다른 파일을 막는다"는 실제 S3에서만 증명되고, 테스트는 우리 쪽 검증(HeadObject 대조)을 증명한다. 한계를 숨기지 않고 말할 것.
- **CSP:** 브라우저가 S3로 직접 보내므로 `connect-src`에 저장소 origin을 추가했다(`STORAGE_ORIGIN`).

## 6. API 계약 → 프론트 타입

- `OpenApiContractIT`가 `/v3/api-docs`를 `frontend/packages/api-client/openapi.json`으로 내보내고(키 정렬, 서버 주소 제거), `openapi-typescript`가 `schema.d.ts`를 만든다. 계약 파일을 커밋하므로 **API 변경이 PR에서 diff로 보인다.**
- **정확도 문제 두 가지를 직접 고쳤다.**
  1. Java record는 모든 필드를 직렬화하는데 springdoc은 전부 선택값으로 표시한다 → 응답 스키마는 전부 `required`로 바꿨다.
  2. null이 될 수 있는 필드가 `string`으로 나온다 → `@ApiNullable`을 붙이면 OpenAPI 3.1의 `type: [string, null]`(참조면 `oneOf`)로 바뀌어 TS에서 `string | null`이 된다. JSpecify `@Nullable`은 TYPE_USE 애너테이션이라 springdoc이 못 읽는다는 것을 확인하고 선언용 애너테이션을 따로 만들었다.

## 7. 프론트 구조에서 짚을 것

- **`/app` 레이아웃이 `/v1/me`를 한 번** 부르고(React `cache`), 페이지는 `requireMe()`로 같은 결과를 쓴다. 메뉴는 역할별로 다르다(관리자: 멤버·코스 스튜디오, 강사: 내 코스). 메뉴를 숨기는 건 편의일 뿐이고 권한 검사는 API가 매번 한다.
- **BFF 패스스루** `/api/v1/*`: 클라이언트 컴포넌트가 쓰는 길이다. 토큰은 서버에만 있다. CSRF는 SameSite=Lax 쿠키에 더해 **변경 요청의 `Origin`이 우리 앱인지**로 막는다. 경로 조각은 영문·숫자·`-_`만 허용한다(`..` 차단).
- **화면 재사용:** 디자인 단계에서 만든 화면(`AdminMembersScreen`)은 가짜 앱 셸을 품고 있어서, 내용만 `AdminMembers`로 분리해 실제 앱에서 쓰고 스토리는 셸을 씌운 버전을 그대로 쓴다. 초대 콜백이 Promise를 돌려주면 실제 서버 결과("2명 초대, 1명 메일 재발송")가 폼 아래에 뜬다.
- **편집기는 화면을 다시 짰다.** 디자인 화면은 "전체 저장" 모델이었는데 API는 "변경 하나 = 요청 하나 + ETag"다. 같은 부품(`CourseOutline`, `FileUpload`, `Notice`)으로 API 모델에 맞게 조립했다. 412가 나면 입력은 화면에 남기고 "새로고침" 버튼을 띄운다.

## 8. 오늘 미룬 것

- AWS Terraform 골격: AWS 계정과 도메인이 필요하다. 계정이 준비되면 진행한다.
- CI 워크플로: GitHub 토큰에 `workflow` 권한이 필요하다(`gh auth refresh -s workflow`).
- 멤버십 Redis 캐시(60초): 지금은 요청마다 DB 조회. 역할 변경 시 캐시 무효화와 같이 넣는다.
- 버려진 업로드 정리 작업(`PENDING` 하루 경과) + S3 수명 주기 규칙
- 레슨 삭제 시 파일 정리: 지금은 레슨만 지우고, 고아 파일은 위 정리 작업이 맡는다.
- 부서 관리 화면: API(만들기·이름 바꾸기·삭제, 사용 중이면 409)는 있고 화면은 Day 3에 붙인다.
