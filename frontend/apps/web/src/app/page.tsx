import { ArrowRight, ClipboardCheck, FileBadge, LockKeyhole } from "lucide-react";
import { Badge, BrandMark, Button, Notice, StatusBadge, type EnrollmentStatus } from "@lms/ui";

import Link from "next/link";

import { loginHref } from "@/lib/return-to";

const ERROR_MESSAGES: Record<string, string> = {
  login_failed: "로그인을 완료하지 못했습니다. 다시 시도해 주세요.",
};

/** Synthetic numbers for the product preview (the frame is labelled "예시 화면"). */
const PREVIEW_STATS = [
  { label: "대상자", value: "128명" },
  { label: "수료", value: "94명" },
  { label: "진행 중", value: "26명" },
  { label: "마감 지남", value: "8명", danger: true },
];
const PREVIEW_DEPARTMENTS = [
  { name: "개발팀", rate: 92 },
  { name: "영업팀", rate: 68 },
  { name: "재무팀", rate: 81 },
  { name: "인사팀", rate: 100 },
];
const PREVIEW_PEOPLE: { name: string; team: string; status: EnrollmentStatus }[] = [
  { name: "박서준", team: "영업팀", status: { kind: "overdue" } },
  { name: "한지민", team: "개발팀", status: { kind: "due-soon", daysLeft: 2 } },
  { name: "이세나", team: "개발팀", status: { kind: "completed" } },
  { name: "정민호", team: "재무팀", status: { kind: "completed" } },
];
const PREVIEW_NAV = ["내 교육", "교육 현황", "교육 배정", "코스 스튜디오", "멤버", "감사 로그"];

const FEATURES = [
  {
    icon: ClipboardCheck,
    title: "교육 배정과 알림",
    body: "부서나 직원에게 필수 교육을 배정하고 마감일을 정합니다. 마감이 다가오면 알림 메일을 보냅니다.",
  },
  {
    icon: FileBadge,
    title: "진도, 퀴즈, 수료증",
    body: "영상과 문서로 학습하고 퀴즈를 통과하면 수료증이 발급됩니다. 수료번호로 누구나 진위를 확인할 수 있습니다.",
  },
  {
    icon: LockKeyhole,
    title: "회사별 데이터 분리",
    body: "회사마다 데이터가 분리되어 다른 회사 정보는 보이지 않습니다. 누가 무엇을 했는지 감사 로그로 남습니다.",
  },
];

const STEPS = [
  { title: "회사 등록", body: "회사 이름과 이메일 도메인을 입력하면 관리자 계정이 만들어집니다." },
  { title: "직원 초대와 교육 배정", body: "직원을 초대하고, 코스를 만들거나 골라 부서에 배정합니다." },
  { title: "현황 확인", body: "대시보드에서 수료율과 마감이 지난 직원을 확인하고 알림을 보냅니다." },
];

/**
 * Landing (Persuade mode, DESIGN.md §1): a common SaaS layout — centered claim with two
 * actions, the product itself as the proof, then features, onboarding steps and a closing CTA.
 * The preview is static markup with synthetic data and says so ("예시 화면").
 */
export default async function LandingPage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string | string[] }>;
}) {
  const { error } = await searchParams;
  const errorMessage = typeof error === "string" ? ERROR_MESSAGES[error] : undefined;
  // Plain <a>: these are route handlers that redirect to Keycloak, not pages.
  const signUp = loginHref({ returnTo: "/onboarding" });
  const signIn = loginHref();

  return (
    <div className="min-h-dvh bg-paper">
      <header className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-6">
        <Link href="/" aria-label="LMS 홈">
          <BrandMark />
        </Link>
        <nav aria-label="소개" className="hidden gap-8 text-body-sm text-ink-2 md:flex">
          <a href="#features" className="hover:text-ink">
            기능
          </a>
          <a href="#steps" className="hover:text-ink">
            도입 절차
          </a>
        </nav>
        <div className="flex items-center gap-1">
          <Button asChild variant="ghost" size="sm">
            <a href={signIn}>로그인</a>
          </Button>
          <Button asChild size="sm" className="hidden sm:inline-flex">
            <a href={signUp}>회사 등록하기</a>
          </Button>
        </div>
      </header>

      <main id="main">
        <section className="mx-auto max-w-6xl px-6 pt-14 text-center md:pt-20">
          {errorMessage ? (
            <div className="mx-auto mb-8 max-w-xl text-left">
              <Notice tone="danger" title={errorMessage} />
            </div>
          ) : null}
          <p className="mb-5 text-body-sm font-semibold text-accent">기업 필수 교육 관리</p>
          <h1 className="text-[2.25rem] leading-[1.3] font-bold text-ink md:text-[3.25rem]">
            임직원 필수 교육,
            <br />
            배정부터 수료증까지 <span className="whitespace-nowrap">한 곳에서</span>
          </h1>
          <p className="mx-auto mt-5 max-w-[38rem] text-title-sm font-normal text-ink-2">
            교육을 배정하고, 진행 상황을 확인하고, 수료증을 발급합니다. 관리자는 아직 듣지 않은 직원을 바로 찾을 수
            있습니다.
          </p>
          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <Button asChild size="lg">
              <a href={signUp}>
                회사 등록하기
                <ArrowRight aria-hidden="true" />
              </a>
            </Button>
            <Button asChild size="lg" variant="secondary">
              <a href={signIn}>초대받은 계정으로 로그인</a>
            </Button>
          </div>

          <ProductPreview />
        </section>

        <section id="features" aria-labelledby="features-title" className="mx-auto max-w-6xl scroll-mt-8 px-6 py-24">
          <div className="mb-12 text-center">
            <p className="mb-2 text-body-sm font-semibold text-accent">주요 기능</p>
            <h2 id="features-title" className="text-[1.75rem] leading-[1.35] font-bold text-ink md:text-[2.125rem]">
              교육 담당자가 하던 일을 줄여 줍니다
            </h2>
          </div>
          <ul className="grid gap-5 md:grid-cols-3">
            {FEATURES.map(({ icon: Icon, title, body }) => (
              <li key={title} className="rounded-panel border border-line bg-surface p-7">
                <span className="mb-5 grid size-11 place-items-center rounded-control bg-accent-soft text-accent">
                  <Icon className="size-5" aria-hidden="true" />
                </span>
                <h3 className="mb-2 text-title-sm font-semibold text-ink">{title}</h3>
                <p className="text-body-sm text-muted">{body}</p>
              </li>
            ))}
          </ul>
        </section>

        <section id="steps" aria-labelledby="steps-title" className="scroll-mt-8 border-y border-line bg-surface py-24">
          <div className="mx-auto max-w-6xl px-6">
            <div className="mb-12 text-center">
              <p className="mb-2 text-body-sm font-semibold text-accent">도입 절차</p>
              <h2 id="steps-title" className="text-[1.75rem] leading-[1.35] font-bold text-ink md:text-[2.125rem]">
                세 단계면 시작할 수 있습니다
              </h2>
            </div>
            <ol className="grid gap-10 md:grid-cols-3">
              {STEPS.map((step, i) => (
                <li key={step.title}>
                  <span className="mb-4 grid size-9 place-items-center rounded-full bg-primary text-body-sm font-bold text-primary-fg tabular">
                    {i + 1}
                  </span>
                  <h3 className="mb-2 text-title-sm font-semibold text-ink">{step.title}</h3>
                  <p className="text-body-sm text-muted">{step.body}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section aria-labelledby="cta-title" className="mx-auto max-w-6xl px-6 py-24">
          <div className="flex flex-col items-start justify-between gap-6 rounded-dialog bg-primary px-8 py-10 md:flex-row md:items-center md:px-12">
            <div>
              <h2 id="cta-title" className="text-[1.75rem] leading-[1.35] font-bold text-primary-fg">
                지금 바로 시작해 보세요
              </h2>
              <p className="mt-2 text-body text-shell-muted">회사를 등록하면 관리자 계정이 바로 만들어집니다.</p>
            </div>
            <Button asChild size="lg" variant="secondary">
              <a href={signUp}>
                회사 등록하기
                <ArrowRight aria-hidden="true" />
              </a>
            </Button>
          </div>
        </section>
      </main>

      <footer className="border-t border-line">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-4 px-6 py-8">
          <BrandMark />
          <p className="text-label text-muted">
            로그인 토큰은 브라우저에 남지 않습니다. 서버가 보관하고 브라우저에는 무작위 세션 쿠키만 둡니다.
          </p>
        </div>
      </footer>
    </div>
  );
}

/** The admin dashboard, drawn small: the product is the proof. Decorative for screen readers. */
function ProductPreview() {
  return (
    <figure className="mx-auto mt-16 max-w-5xl text-left">
      <div className="overflow-hidden rounded-dialog border border-line bg-surface shadow-overlay" aria-hidden="true">
        <div className="flex h-9 items-center gap-1.5 border-b border-line bg-surface-sunken px-4">
          <span className="size-2.5 rounded-full bg-line" />
          <span className="size-2.5 rounded-full bg-line" />
          <span className="size-2.5 rounded-full bg-line" />
          <span className="ml-3 rounded-badge border border-line bg-surface px-2.5 py-0.5 text-caption text-muted">
            교육 현황
          </span>
        </div>
        <div className="grid md:grid-cols-[180px_1fr]">
          <div className="hidden bg-shell px-3 py-4 md:block">
            <BrandMark tone="shell" className="mb-5 px-2 [&>span:last-child]:text-body-sm" />
            <ul className="grid gap-0.5 text-label">
              {PREVIEW_NAV.map((item) => (
                <li
                  key={item}
                  className={
                    item === "교육 현황"
                      ? "rounded-control bg-shell-active px-2.5 py-2 text-shell-fg"
                      : "px-2.5 py-2 text-shell-muted"
                  }
                >
                  {item}
                </li>
              ))}
            </ul>
          </div>
          <div className="grid gap-3 bg-paper p-4 md:p-6">
            <p className="text-title-sm font-semibold text-ink">교육 현황</p>
            <div className="grid grid-cols-2 gap-2.5 md:grid-cols-4">
              {PREVIEW_STATS.map((s) => (
                <div key={s.label} className="rounded-panel border border-line bg-surface px-4 py-3">
                  <p className="text-caption text-muted">{s.label}</p>
                  <p className={`text-title font-bold tabular ${s.danger ? "text-danger" : "text-ink"}`}>{s.value}</p>
                </div>
              ))}
            </div>
            <div className="grid gap-2.5 md:grid-cols-[1fr_1.25fr]">
              <div className="rounded-panel border border-line bg-surface p-4">
                <p className="mb-3 text-label font-semibold text-ink">부서별 수료율</p>
                <ul className="grid gap-2.5">
                  {PREVIEW_DEPARTMENTS.map((d) => (
                    <li key={d.name} className="grid grid-cols-[3rem_1fr_2.5rem] items-center gap-2 text-caption text-ink-2">
                      <span>{d.name}</span>
                      <span className="h-2 overflow-hidden rounded-full bg-surface-sunken">
                        <span className="block h-full rounded-full bg-accent" style={{ width: `${d.rate}%` }} />
                      </span>
                      <span className="text-right tabular">{d.rate}%</span>
                    </li>
                  ))}
                </ul>
              </div>
              <div className="rounded-panel border border-line bg-surface p-4">
                <p className="mb-1 text-label font-semibold text-ink">확인이 필요한 직원</p>
                <ul className="divide-y divide-line">
                  {PREVIEW_PEOPLE.map((p) => (
                    <li key={p.name} className="flex items-center justify-between gap-3 py-2">
                      <span className="flex items-center gap-2 text-label text-ink-2">
                        <span className="grid size-6 place-items-center rounded-full bg-surface-sunken text-caption font-semibold">
                          {p.name[0]}
                        </span>
                        {p.name} · {p.team}
                      </span>
                      <StatusBadge status={p.status} />
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          </div>
        </div>
      </div>
      <figcaption className="mt-3 flex items-center justify-center gap-2 text-label text-muted">
        <Badge>예시 화면</Badge>
        관리자 대시보드. 숫자와 이름은 설명을 위한 예시입니다.
      </figcaption>
    </figure>
  );
}
