import { redirect } from "next/navigation";
import { Button, ErrorState, Panel } from "@lms/ui";

import { AppFrame } from "@/components/app-frame";
import { LogoutButton } from "@/components/logout-button";
import { roleLabel } from "@/lib/labels";
import { loginHref } from "@/lib/return-to";
import { loadMe } from "@/server/me";

/** Every /app page: signed in, acting for a company, inside the role's navigation. */
export default async function AppLayout({ children }: { children: React.ReactNode }) {
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

  return (
    <AppFrame
      role={me.role}
      workspace={<span className="truncate px-2 text-body-sm font-semibold text-ink">{me.currentTenant.name}</span>}
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
      {children}
    </AppFrame>
  );
}
