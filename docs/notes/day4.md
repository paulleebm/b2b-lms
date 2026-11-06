# Day 4 면접 설명 노트: 아웃박스·큐·수료증·메일·대시보드·감사 로그·영상 변환

> 형식은 Day 1~3과 같다. 각 항목을 무엇을 했는지, 왜 그랬는지, 면접에서 어떻게 말할지 순서로 정리했다.

## 0. 오늘 만든 것 한눈에

```
[API 트랜잭션]  수료 / 배정 / 리마인더 ─ 업무 데이터 + event_publication 행 (같은 커밋)
                        │ 커밋 후
                        ▼
              OutboxRelay ─ SQS(lms-events) 전송 성공 → 발행 완료 표시
                        │                실패 → 행이 남음 → 1분마다 재발행(ShedLock)
                        ▼
[워커]  SqsWorker(롱 폴링, 가상 스레드) ─ processed_message로 중복 거름 ─ 핸들러
          ├ certificate-pdf : Thymeleaf → OpenHTMLtoPDF(Pretendard 내장) → S3 → 수료증 행에 키 기록
          ├ mail-assigned / mail-reminder / mail-completed : JavaMailSender → Mailpit(로컬) / SES(AWS)
          └ 5번 실패 → DLQ (CloudWatch 알람)

[MediaConvert] 작업 완료·실패 ─ EventBridge 규칙 ─ SQS(lms-transcode) ─ 워커 → 영상 READY(HLS) / FAILED
[대시보드] JdbcClient 집계 SQL ─ @Cacheable(회사 ID 키, Redis 60초) ─ 수료·배정 커밋 후 캐시 비움
[감사 로그] 도메인 이벤트 구독 → audit_log (INSERT·SELECT 권한만)
[진위 확인] /verify/{수료번호} ─ verify_certificate() SECURITY DEFINER 함수 ─ 이름 가림("하**김")
```

| 위치 | 파일 | 역할 |
|---|---|---|
| DB | `V6__async_and_audit.sql` | 이벤트 레지스트리, ShedLock, 처리한 메시지, 감사 로그, 진위 확인 함수 |
| 백엔드 | `shared/messaging/*` | 외부로 내보낼 이벤트 표시(`Externalized`), 아웃박스 릴레이, SQS 워커, 재발행 작업 |
| 백엔드 | `learning/internal/Certificate*` | PDF 렌더링, 다운로드, 공개 진위 확인, 누락분 보충 작업 |
| 백엔드 | `learning/internal/Reminder*` | 리마인더(20시간 제한), 매일 09:00 자동 리마인더 |
| 백엔드 | `notification/internal/*` | 메일 3종 핸들러 |
| 백엔드 | `reporting/internal/DashboardService` | 대시보드 읽기 모델, 캐시, 캐시 비우기 |
| 백엔드 | `audit/internal/*` | 감사 기록, 조회 API |
| 백엔드 | `files/internal/MediaConvert*`, `CloudFrontCookies`, `FileJobs` | HLS 작업, 완료 이벤트 처리, 서명 쿠키, 멈춘 작업 정리 |
| 템플릿 | `templates/certificate.html`, `templates/mail/*` | A4 가로 수료증, 메일 레이아웃 |
| 프론트 | `app/app/dashboard`, `app/app/audit`, `app/verify/[serial]` | 교육 현황, 감사 로그, 공개 진위 확인 |
| 프론트 | `app/app/certificates/[enrollmentId]/route.ts` | 수료증 다운로드 (presigned URL로 302) |
| 로컬 | `infra/local/elasticmq.conf` | SQS 대체(ElasticMQ): 큐 2개 + DLQ |
| 배포 | `envs/demo/main.tf` | worker 서비스, MediaConvert·CDN·SQS·SES 환경변수 |

테스트: 백엔드 **80개**(Day 3의 72개 + 비동기 통합 6개 + 영상 단위 2개). 브라우저에서 대시보드 숫자, 행별 리마인더 → Mailpit 도착, 수료증 PDF 다운로드(12.9KB), 진위 확인 페이지, 감사 로그를 직접 확인했다.

---

## 1. 왜 아웃박스인가 (이중 쓰기 문제)

- 수료 처리 안에서 바로 SQS로 보내면 **DB 커밋과 메시지 전송이 따로 성공하거나 실패한다.** 커밋은 됐는데 전송 전에 죽으면 수료증이 영영 안 만들어지고, 전송은 됐는데 롤백되면 없는 수료에 대한 PDF가 생긴다.
- 그래서 이벤트를 **업무 데이터와 같은 트랜잭션으로 DB 테이블(`event_publication`)에** 적는다. 둘 다 되거나 둘 다 안 된다. 커밋 후 릴레이가 SQS로 보내고, 성공하면 완료 표시를 한다.
- 전송이 실패하면 행이 "미완료"로 남고, `OutboxResubmitJob`이 1분마다 다시 보낸다. 서버가 여러 대여도 **ShedLock**으로 한 대만 돈다.
- **직접 만들지 않았다.** Spring Modulith의 이벤트 레지스트리(JDBC)가 아웃박스 테이블과 재발행 API를 제공한다. 테이블은 Hibernate 자동 생성 대신 Flyway가 만든다(`schema-initialization.enabled=false`).

| 후보 | 선택 여부 | 이유 |
|---|---|---|
| 트랜잭션 안에서 바로 SQS 전송 | ✗ | 이중 쓰기 |
| Debezium CDC (WAL → Kafka) | ✗ | 시연 규모에 Kafka Connect 클러스터는 과함 |
| 직접 만든 outbox 테이블 + 폴러 | ✗ | Modulith가 이미 제공. 바퀴를 다시 만들 이유가 없다 |
| **Modulith 이벤트 레지스트리 + 릴레이** | ✓ | 같은 트랜잭션, 재발행 API, 모듈 경계와 잘 맞음 |

## 2. 최소 한 번 전달과 멱등 소비자

- SQS 표준 큐와 아웃박스 재발행은 둘 다 **"최소 한 번"**이다. 같은 메시지가 두 번 올 수 있다고 가정한다.
- 워커는 처리하기 전에 `insert into processed_message (message_id, handler) … on conflict do nothing`을 한다. 0행이면 이미 처리한 메시지라서 건너뛴다. **이 INSERT와 실제 처리가 한 트랜잭션**이라서, 처리 중 죽으면 기록도 롤백되고 다음 수신 때 다시 처리한다.
- 키가 (메시지 ID, 핸들러)인 이유: 한 이벤트를 수료증·메일 두 핸들러가 각각 처리한다. 하나가 실패해도 다른 하나는 다시 하지 않는다.
- 5번 받고도 실패하면 **DLQ**로 간다(재시도 정책 `maxReceiveCount=5`). DLQ에 메시지가 생기면 CloudWatch 알람이 메일을 보낸다.
- 메시지 봉투에 `tenantId`를 담는다. 워커는 `TenantContext.runAs(tenantId)` 안에서 처리하므로 **워커도 RLS를 지난다.** "워커는 시스템이니 다 본다"가 아니다.

## 3. 수료증 PDF: 왜 OpenHTMLtoPDF인가

| 후보 | 이유 |
|---|---|
| iText 7 | 품질은 최고. 하지만 **AGPL**: 서비스에 쓰면 소스 공개 의무 또는 상용 라이선스 |
| JasperReports | 보고서 도구라 디자인 자유도가 낮고 별도 디자이너 도구가 필요 |
| 헤드리스 Chrome(Puppeteer) | 렌더링은 정확하지만 워커에 브라우저를 띄워야 한다(이미지 크기, 메모리) |
| **Thymeleaf + OpenHTMLtoPDF** | **LGPL**, 순수 Java, HTML/CSS로 디자인, 폰트 내장 |

- 한글이 깨지지 않도록 **Pretendard TTF를 PDF에 내장**한다(OFL 라이선스, 원문 동봉). 시스템 폰트에 기대지 않으므로 컨테이너에서도 같은 결과.
- PDF는 S3 `{tenantId}/certificates/{serial}.pdf`에 저장하고, 다운로드는 **짧은 presigned URL로 302**. 파일이 앱 서버를 지나지 않는다.
- 아직 PDF가 안 만들어졌으면 409 `CERTIFICATE_NOT_READY` → 화면은 "만드는 중입니다, 잠시 후 다시". 실패해서 PDF가 영영 없는 수료증은 **보충 작업**(10분마다)이 찾아서 다시 만든다.
- 테스트는 PDF에서 텍스트를 추출해 이름·코스명·수료번호가 있는지 본다. 자간 때문에 글자 사이에 공백이 끼어 공백을 지우고 비교했다.

## 4. 진위 확인: 로그인 없이, 하지만 RLS는 그대로

- 수료증을 받은 회사(외부 기관, 채용 담당자)는 로그인하지 않고 `/verify/LMS-2026-XXXXXXXX`로 확인한다.
- 문제: 로그인이 없으면 회사 컨텍스트가 없고, RLS 때문에 아무 행도 안 보인다. RLS를 끄면 격리가 깨진다.
- 해결: **`verify_certificate(serial)` 함수 하나만 `SECURITY DEFINER`**로 만들었다. 수료번호가 정확히 일치하는 한 건만, **필요한 열만, 이름은 가려서**("하**김") 돌려준다. 앱 계정은 이 함수 실행 권한만 받는다. 표 전체를 훑을 방법이 없다.
- 수료번호는 Crockford Base32 8자(약 1조 가지) 난수라서 순서대로 넣어 보는 공격이 사실상 불가능하다. 운영이라면 IP별 요청 제한을 붙인다.

## 5. 메일: 배정 안내, 리마인더, 수료 축하

- 로컬은 **Mailpit**(SMTP를 받아 웹 메일함으로 보여주는 도구), AWS는 **SES SMTP 인터페이스**. 코드는 같은 `JavaMailSender`이고 주소·계정만 다르다.
- 리마인더는 **스팸이 되지 않게 20시간에 한 번**: `UPDATE enrollment SET reminded_at = now() WHERE … AND (reminded_at IS NULL OR reminded_at < now() - 20h) RETURNING …`. 버튼을 연달아 눌러도 두 번째는 0건. 조건부 UPDATE라 동시에 눌러도 한 번만 간다.
- 매일 09:00(한국 시간) 마감이 3일 안에 오거나 이미 지난 사람에게 자동 리마인더(ShedLock으로 한 대만). 관리자 버튼은 미수료자 전원 또는 고른 행만.
- 메일 발송은 **요청 스레드에서 하지 않는다.** 이벤트 → 큐 → 워커. SMTP가 느리거나 죽어도 관리자 화면은 바로 응답한다.

## 6. 대시보드: 읽기 모델과 캐시 키 격리

- 화면에 필요한 숫자(이수율, 부서별 수료율, 미이수자 목록)를 **JPA 엔티티를 조립하지 않고 SQL 집계 한 번씩**으로 만든다(`JdbcClient`). N+1이 생길 여지가 없다. RLS는 그대로 적용된다.
- 캐시 키에 **회사 ID를 반드시 넣는다**(`key = tenantId`). 키에 회사가 빠지면 A사 관리자가 B사 숫자를 본다. 캐시는 RLS 바깥이라 DB가 막아 주지 못한다. **멀티테넌트에서 가장 흔한 사고 지점**이라고 설명한다.
- TTL 60초 + **수료·배정이 커밋된 뒤 그 회사 캐시를 비운다.** 테스트: 수료 직후 대시보드에 바로 반영되는지 확인.
- Redis 직렬화 대상(레코드)은 `Serializable`. 테스트에서는 단순 메모리 캐시.

## 7. 감사 로그

- 누가 언제 무엇을 했는지: 초대, 역할 변경, 코스 게시·보관, 배정, 리마인더, 수료. **도메인 이벤트를 구독해서** 남긴다. 각 서비스 코드에 감사 기록 호출을 흩뿌리지 않는다.
- `audit_log`는 앱 계정에 **INSERT·SELECT만** 준다. UPDATE·DELETE 권한이 없으니 앱 버그나 탈취된 앱 계정으로도 기록을 고칠 수 없다(xAPI 학습 기록과 같은 방식).
- RLS 적용: 회사 관리자는 자기 회사 기록만 본다.

## 8. 영상 변환: 왜 HLS로 바꾸고, 왜 폴링이 아니라 이벤트인가

- **왜 변환하나:** 사람들이 올리는 원본은 코덱·비트레이트가 제각각(아이폰 HEVC 4K 등). 어느 브라우저에서나 재생되고(H.264), **네트워크 상태에 따라 화질이 바뀌고(ABR)**, 전송량이 줄어든다.
- MediaConvert 작업: 1080p/720p/480p **QVBR**(장면 복잡도에 맞춰 비트레이트를 쓰는 방식) + 썸네일. 출력은 같은 회사·같은 영상 폴더 아래(`{tenant}/videos/{id}/hls/`) — 서명 쿠키가 이 폴더 단위로 걸리기 때문.
- **완료를 묻지(폴링) 않는다.** MediaConvert가 작업 상태 변화를 EventBridge로 내보내고, 규칙이 그것을 SQS로 보내고, 워커가 받아 영상을 READY/FAILED로 바꾼다. API 호출 비용과 지연이 없다.
- 이벤트가 유실되는 경우를 대비해 **오래 멈춘 변환을 찾는 작업**이 있다(그리고 업로드만 시작하고 끝내지 않은 멀티파트를 정리하는 작업).
- 로컬에서는 가짜 변환기(원본 mp4 그대로 재생). 실제 변환은 `VIDEO_TRANSCODER=mediaconvert`일 때만 켜진다. 테스트는 작업 설정(3화질, 출력 경로)과 EventBridge 이벤트 처리(완료 → READY, 오류 → FAILED)를 검증한다.

## 9. HLS에 서명 쿠키를 쓰는 이유

- HLS는 재생 목록(.m3u8) + 수백 개의 조각(.ts). **조각마다 서명 URL을 만들 수 없다**(재생 목록 안의 주소를 다 고쳐야 한다).
- CloudFront 서명 쿠키 3개(Policy, Signature, Key-Pair-Id)에 **"이 영상 폴더(`…/videos/{id}/*`), 2시간"** 정책을 담는다. 브라우저가 그 폴더의 모든 요청에 쿠키를 붙인다.
- 쿠키는 BFF가 레슨을 열 때 `Set-Cookie`로 내려준다. 도메인 `.bymile.site`로 걸어 `app.`이 심고 `media.`가 받는다(같은 상위 도메인이어야 하는 이유).
- 개인키는 Terraform에 넣지 않고 SSM SecureString에 손으로 넣는다(상태 파일에 비밀이 남지 않게).

## 10. 예약 작업과 ShedLock

| 작업 | 주기 | 하는 일 |
|---|---|---|
| 아웃박스 재발행 | 1분 | 1분 넘게 미완료인 이벤트를 다시 보냄 |
| 수료증 보충 | 10분 | PDF가 없는 수료증을 다시 렌더링 |
| 자동 리마인더 | 매일 09:00 KST | 마감이 3일 안에 오거나 지난 사람에게 (20시간 제한 재사용) |
| 멈춘 변환 점검 | 30분 | 너무 오래 PROCESSING인 영상을 FAILED로 |
| 버려진 업로드 정리 | 1시간 | 끝나지 않은 멀티파트 업로드 중단 |

- 서버가 2대면 `@Scheduled`가 2번 돈다. **ShedLock**(JDBC, DB 시각 기준)으로 한 대만 실행. 서버마다 시계가 달라도 DB 시각을 쓰므로 잠금 만료가 어긋나지 않는다.

## 11. 오늘 부딪힌 문제

| 문제 | 원인 | 해결 |
|---|---|---|
| 대시보드 스토리 테스트가 멈춤 | `remindedIds = []` 기본값이 렌더링마다 새 배열 → `useEffect` 의존성이 매번 바뀌어 무한 렌더링 | 배열 대신 내용 문자열(`join(",")`)을 의존성으로. **실제 화면도 같은 문제가 있었을 것**이라 테스트가 잡아 준 버그 |
| PDF 텍스트 비교 실패 | 자간 때문에 추출된 글자 사이에 공백 | 공백을 지우고 비교. Java 문자열 `"\s+"`가 정규식이 아니라 공백 하나로 해석되는 실수도 같이 고침(`"\\s+"`) |
| Docker가 멈춤 | 이미지 빌드와 Testcontainers를 동시에 돌려 VM 메모리 부족 → 이후 소켓 파일이 깨져 재시작 불가 | 깨진 소켓 폴더 이름만 바꾸고 재시작(공장 초기화는 DB 볼륨이 지워져 하지 않음). 이후 무거운 작업은 순서대로 |

## 12. 면접에서 이렇게 말한다 (1분)

> "수료하면 수료증 PDF와 메일이 나가야 하는데, 이걸 요청 안에서 하면 느리고, 큐로 바로 보내면 DB 커밋과 메시지 전송이 따로 놀아요. 그래서 이벤트를 업무 데이터와 같은 트랜잭션으로 아웃박스 테이블에 적고, 커밋 후에 SQS로 보냅니다. 전송이 실패하면 1분마다 다시 보내고요. 그러면 같은 메시지가 두 번 갈 수 있으니까 워커는 처리한 메시지 ID를 같은 트랜잭션으로 기록해서 두 번째는 건너뜁니다. 다섯 번 실패하면 DLQ로 가고 알람이 옵니다. 워커도 메시지에 담긴 회사 ID로 컨텍스트를 세워서 RLS를 그대로 지나갑니다."
