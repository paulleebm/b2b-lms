import { Badge, Button, Notice, SealMark, StatusBadge } from "@lms/ui";

import { loginHref } from "@/lib/return-to";

const ERROR_MESSAGES: Record<string, string> = {
  login_failed: "로그인을 완료하지 못했습니다. 다시 시도해 주세요.",
};

/** Synthetic roster for the illustration (labelled "예시 화면" on the page). */
const ROSTER = [
  { name: "이세나", team: "개발팀", status: { kind: "completed", completedAt: "2026. 11. 3." } as const },
  { name: "한지민", team: "개발팀", status: { kind: "due-soon", daysLeft: 2 } as const },
  { name: "박서준", team: "영업팀", status: { kind: "overdue" } as const },
  { name: "정민호", team: "재무팀", status: { kind: "completed", completedAt: "2026. 11. 5." } as const },
];

/**
 * Landing (Persuade mode, DESIGN.md §1): one claim, one proof. The proof is the product's own
 * record — who is done, who is late — with the seal that marks completion. No eyebrow, no
 * three identical feature cards, no invented metrics (Impeccable craft-floor, anti-ai-slop).
 */
export default async function LandingPage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string | string[] }>;
}) {
  const { error } = await searchParams;
  const errorMessage = typeof error === "string" ? ERROR_MESSAGES[error] : undefined;

  return (
    <div className="min-h-dvh bg-paper">
      <header className="mx-auto flex h-16 max-w-6xl items-center justify-between px-6">
        <span className="flex items-center gap-2 text-body-sm font-semibold text-ink">
          <span className="grid size-6 place-items-center rounded-seal border-[1.5px] border-ink text-[10px] font-extrabold">
            學
          </span>
          LMS
        </span>
        <Button asChild variant="ghost" size="sm">
          <a href={loginHref()}>로그인</a>
        </Button>
      </header>

      <main id="main" className="mx-auto grid max-w-6xl gap-12 px-6 pt-10 pb-20 lg:grid-cols-[1.05fr_1fr] lg:items-center lg:pt-20">
        <section className="grid content-start gap-6">
          {errorMessage ? <Notice tone="danger" title={errorMessage} /> : null}
          <h1 className="text-[2.5rem] leading-[1.3] font-bold text-ink md:text-[3rem]">
            필수 교육은
            <br />
            수료 기록으로 끝납니다
          </h1>
          <p className="max-w-[34rem] text-body text-ink-2">
            회사가 직원에게 교육을 배정하고, 누가 들었고 누가 아직인지 한 화면에서 확인합니다. 수료는 진위를 확인할 수
            있는 수료증과 함께 회사의 기록으로 남습니다.
          </p>
          <div className="flex flex-wrap gap-3">
            {/* Plain <a>: these are route handlers that redirect to Keycloak, not pages. */}
            <Button asChild size="lg">
              <a href={loginHref({ returnTo: "/onboarding" })}>회사 등록하기</a>
            </Button>
            <Button asChild size="lg" variant="secondary">
              <a href={loginHref()}>초대받은 계정으로 로그인</a>
            </Button>
          </div>
          <p className="text-label text-muted">
            로그인 토큰은 브라우저에 남지 않습니다. 서버가 보관하고 브라우저에는 무작위 세션 쿠키만 둡니다.
          </p>
        </section>

        <figure className="grid gap-3" aria-labelledby="roster-caption">
          <div className="rounded-panel border border-line bg-surface">
            <div className="flex items-center justify-between border-b border-line px-5 py-3.5">
              <div>
                <p className="text-body-sm font-semibold text-ink">정보보안 기초</p>
                <p className="text-caption text-muted">마감 11월 8일 · 대상 4명</p>
              </div>
              <Badge>예시 화면</Badge>
            </div>
            <ul className="divide-y divide-line">
              {ROSTER.map((r) => (
                <li key={r.name} className="flex items-center gap-3 px-5 py-3">
                  <span className="grid w-10 shrink-0 place-items-center">
                    {r.status.kind === "completed" ? (
                      <SealMark size="md" completedAt={r.status.completedAt} />
                    ) : (
                      <span className="size-[40px]" aria-hidden="true" />
                    )}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block text-body-sm font-medium text-ink">{r.name}</span>
                    <span className="block text-caption text-muted">{r.team}</span>
                  </span>
                  <StatusBadge status={r.status} withSeal={false} />
                </li>
              ))}
            </ul>
          </div>
          <figcaption id="roster-caption" className="text-label text-muted">
            수료한 사람에게만 인장이 찍힙니다. 관리자는 마감이 지난 사람부터 봅니다.
          </figcaption>
        </figure>
      </main>
    </div>
  );
}
