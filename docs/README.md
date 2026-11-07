# 기획 문서

B2B 멀티테넌트 사내교육 LMS의 기획·설계 문서다. 모든 결정에는 **이유**와 **검토한 대안**을 함께 적었다.

**스택 요약:** Java 21 · Spring Boot 4 · Spring Modulith · JPA + PostgreSQL(RLS) · Keycloak · Next.js(BFF) · Storybook · AWS(ECS Fargate, RDS, ElastiCache, SQS, S3/CloudFront, SES, MediaConvert) · Terraform · GitHub Actions · OpenTelemetry · xAPI

| 문서 | 내용 |
|---|---|
| [01-product.md](01-product.md) | 무엇을, 누구를 위해 만드나: 멤버십 모델, 이메일 우선 로그인, 시나리오, LMS 표준(xAPI·SCORM), MoSCoW 범위 |
| [02-tech-decisions.md](02-tech-decisions.md) | 기술 선택 기록(ADR): 후보, 선택 이유, 포기한 것 |
| [03-architecture.md](03-architecture.md) | AWS 구성도, 네트워크·보안 그룹, 요청 생애주기, 멀티테넌트 격리, Modulith 모듈, API |
| [04-data-model.md](04-data-model.md) | ERD와 테이블 설계 이유, 동시성 제어 |
| [05-service-flows.md](05-service-flows.md) | 기능별 시퀀스 다이어그램과 엣지 케이스 |
| [06-security.md](06-security.md) | 위협 모델(OWASP Top 10), 계층별 보안 장치, 예상 질문 |
| [07-schedule.md](07-schedule.md) | 5일 실행 계획, 범위 축소 순서, 위험 평가 |
| [08-cost-and-limits.md](08-cost-and-limits.md) | AWS 비용, 시연으로는 무리인 것, 표준에서 벗어난 곳 |
| [09-interview-qa.md](09-interview-qa.md) | 예상 질문과 답 18문항 |
| [10-demo-script.md](10-demo-script.md) | 5분 시연 대본, 아키텍처 1분, 리허설 체크리스트 |
| [11-home-deployment.md](11-home-deployment.md) | 실제 배포: Vercel + 미니 PC(Docker) + Cloudflare. 선택 이유, 대안, AWS 설계와의 대응 |
| [notes/](notes/) | 일별 면접 설명 노트 (Day 1~5) |
| [../DESIGN.md](../DESIGN.md) | 디자인 계약서 "인장과 이수증": 색·서체·컴포넌트·상태·접근성 규칙과 이유 |
| [../PRODUCT.md](../PRODUCT.md) | 디자인 도구가 읽는 제품 요약 |
| [design/REVIEW.md](design/REVIEW.md) | 디자인 검토 기준 (CI 검사 → 체크리스트 → 5개 관점 점수) |
| [design/reviews/](design/reviews/) | 검토 기록 |
| [design/open-design/](design/open-design/) | open-design에서 가져온 craft 규칙·대시보드·검토 템플릿 (출처·라이선스 포함) |

> 다이어그램은 Mermaid로 작성했다. GitHub에서 바로 렌더링된다.
