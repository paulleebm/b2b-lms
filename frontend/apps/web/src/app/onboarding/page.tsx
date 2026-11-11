import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { Badge, Button, Panel, PanelHeader } from "@lms/ui";

import { LogoutButton } from "@/components/logout-button";
import { roleLabel } from "@/lib/labels";
import { loginHref } from "@/lib/return-to";
import { loadMe } from "@/server/me";
import { getSession } from "@/server/session";

import { OnboardingForm } from "./onboarding-form";

export const metadata: Metadata = { title: "회사 등록" };

/** Set on the return trip of the automatic company login, so a failed one cannot loop. */
const RETRIED = "1";

/**
 * Users without a company in their token land here: new users to register one, and members of
 * several companies. Login is one page (realm flow "browser-one-page"), so Keycloak no longer
 * asks members of several companies to pick one; the token then carries none and we ask here.
 * A single active membership needs no question: log in again for it (SSO, no form).
 */
export default async function OnboardingPage({ searchParams }: { searchParams: Promise<{ retried?: string }> }) {
  // proxy.ts only checks that a cookie exists; confirm the session is real.
  const session = await getSession();
  if (!session) redirect(loginHref({ returnTo: "/onboarding" }));

  const result = await loadMe();
  const me = result !== "unauthenticated" && result.ok ? result.me : null;
  if (me?.currentTenant) redirect("/app");

  const active = (me?.memberships ?? []).filter((m) => m.status === "ACTIVE");
  const { retried } = await searchParams;
  if (active.length === 1 && retried !== RETRIED) {
    redirect(loginHref({ org: active[0]!.slug, returnTo: `/onboarding?retried=${RETRIED}` }));
  }

  return (
    <main id="main" className="mx-auto flex min-h-dvh max-w-3xl flex-col items-center justify-center gap-5 px-6 py-12">
      {active.length > 0 ? (
        <Panel className="w-full max-w-lg">
          <PanelHeader title="소속 회사" />
          <ul className="divide-y divide-line border-t border-line">
            {active.map((membership) => (
              <li key={membership.tenantId} className="flex flex-wrap items-center gap-3 px-5 py-3">
                <div className="min-w-0 flex-1">
                  <p className="font-medium break-words text-ink">{membership.name}</p>
                  <p className="text-caption text-muted">{membership.slug}</p>
                </div>
                <Badge>{roleLabel(membership.role)}</Badge>
                <Button asChild size="sm">
                  <a href={loginHref({ org: membership.slug, returnTo: "/app" })}>들어가기</a>
                </Button>
              </li>
            ))}
          </ul>
        </Panel>
      ) : null}
      <OnboardingForm />
      <div className="flex items-center gap-2 text-label text-muted">
        <span>다른 계정으로 로그인하려면</span>
        <LogoutButton variant="ghost" size="sm" />
      </div>
    </main>
  );
}
