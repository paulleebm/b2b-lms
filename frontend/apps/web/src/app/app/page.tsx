import type { Metadata } from "next";
import { redirect, unstable_rethrow } from "next/navigation";
import { Badge, Button, ErrorState, PageHeader, Panel, PanelBody, PanelHeader } from "@lms/ui";

import { AppFrame } from "@/components/app-frame";
import { LogoutButton } from "@/components/logout-button";
import { roleLabel, statusBadge } from "@/lib/labels";
import { loginHref } from "@/lib/return-to";
import { ApiError, UnauthenticatedError, apiFetch } from "@/server/api";
import type { MeResponse } from "@/server/api-types";

export const metadata: Metadata = { title: "홈" };

type MeResult = { ok: true; me: MeResponse } | { ok: false; title: string; detail?: string; traceId?: string };

async function loadMe(): Promise<MeResult | "unauthenticated"> {
  try {
    return { ok: true, me: await apiFetch<MeResponse>("/v1/me") };
  } catch (error) {
    // Let Next.js control-flow errors (dynamic usage, redirect, notFound) propagate.
    unstable_rethrow(error);
    if (error instanceof UnauthenticatedError) return "unauthenticated";
    if (error instanceof ApiError) {
      // A 401 here means our session exists but the API rejects the token. Show an error
      // instead of redirecting, otherwise Keycloak SSO would loop us straight back.
      const title =
        error.status === 401
          ? "인증 정보를 확인하지 못했습니다"
          : error.status === 403
            ? "이 회사에 접근할 권한이 없습니다"
            : "내 정보를 불러오지 못했습니다";
      return { ok: false, title, detail: error.problem.detail, traceId: error.traceId };
    }
    console.error("[app] GET /v1/me failed:", error);
    return { ok: false, title: "API 서버에 연결할 수 없습니다", detail: "잠시 후 다시 시도해 주세요." };
  }
}

export default async function AppHomePage() {
  const result = await loadMe();
  // redirect() throws, so keep it outside try/catch.
  if (result === "unauthenticated") redirect(loginHref({ returnTo: "/app" }));

  if (!result.ok) {
    return (
      <main id="main" className="mx-auto grid min-h-dvh max-w-xl place-items-center px-6">
        <Panel className="w-full">
          <ErrorState
            title={result.title}
            description={result.detail ?? "잠시 후 다시 시도해 주세요."}
            traceId={result.traceId}
          />
          <div className="flex justify-center gap-2 pb-8">
            <Button asChild>
              <a href={loginHref({ returnTo: "/app" })}>다시 로그인</a>
            </Button>
            <LogoutButton />
          </div>
        </Panel>
      </main>
    );
  }

  const { me } = result;
  if (!me.currentTenant) redirect("/onboarding");

  const tenant = me.currentTenant;
  const canSwitch = me.memberships.length > 1;

  return (
    <AppFrame
      workspace={<span className="truncate px-2 text-body-sm font-semibold text-ink">{tenant.name}</span>}
      account={
        <>
          <span className="hidden text-right md:grid">
            <span className="text-body-sm font-medium text-ink">{me.name ?? me.email}</span>
            <span className="text-caption text-muted">{roleLabel(me.role)}</span>
          </span>
          <LogoutButton variant="ghost" size="sm" />
        </>
      }
    >
      <PageHeader title="홈" description="로그인한 계정과, 지금 어느 회사의 어떤 역할로 일하고 있는지 보여줍니다." />

      <div className="grid gap-6">
        <Panel>
          <PanelHeader title="내 정보" />
          <PanelBody>
            <dl className="grid grid-cols-[7rem_1fr] gap-x-6 gap-y-3 text-body-sm">
              <dt className="text-muted">이름</dt>
              <dd className="break-words text-ink">{me.name ?? "-"}</dd>
              <dt className="text-muted">이메일</dt>
              <dd className="break-all text-ink">{me.email}</dd>
              <dt className="text-muted">현재 회사</dt>
              <dd className="break-words text-ink">
                {tenant.name} <span className="text-muted">({tenant.slug})</span>
              </dd>
              <dt className="text-muted">역할</dt>
              <dd>
                <Badge tone={me.role === "ORG_ADMIN" ? "info" : "neutral"}>{roleLabel(me.role)}</Badge>
              </dd>
            </dl>
          </PanelBody>
        </Panel>

        <Panel>
          <PanelHeader
            title="소속 회사"
            description={
              canSwitch ? "다른 회사로 전환하면 그 회사의 권한으로 다시 로그인합니다." : "소속된 회사 목록입니다."
            }
          />
          <ul className="divide-y divide-line border-t border-line">
            {me.memberships.map((membership) => {
              const status = statusBadge(membership.status);
              const isCurrent = membership.tenantId === tenant.id;
              return (
                <li key={membership.tenantId} className="flex flex-wrap items-center gap-3 px-5 py-3">
                  <div className="min-w-0 flex-1">
                    <p className="font-medium break-words text-ink">{membership.name}</p>
                    <p className="text-caption text-muted">{membership.slug}</p>
                  </div>
                  <Badge>{roleLabel(membership.role)}</Badge>
                  <Badge tone={status.tone}>{status.label}</Badge>
                  {isCurrent ? (
                    <span className="text-label text-muted">현재 회사</span>
                  ) : canSwitch && membership.status === "ACTIVE" ? (
                    <Button asChild size="sm" variant="secondary">
                      <a href={loginHref({ org: membership.slug, returnTo: "/app" })}>전환</a>
                    </Button>
                  ) : null}
                </li>
              );
            })}
          </ul>
        </Panel>
      </div>
    </AppFrame>
  );
}
