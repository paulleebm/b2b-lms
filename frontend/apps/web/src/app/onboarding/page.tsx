import type { Metadata } from "next";
import { redirect } from "next/navigation";

import { LogoutButton } from "@/components/logout-button";
import { loginHref } from "@/lib/return-to";
import { getSession } from "@/server/session";

import { OnboardingForm } from "./onboarding-form";

export const metadata: Metadata = { title: "회사 등록" };

export default async function OnboardingPage() {
  // proxy.ts only checks that a cookie exists; confirm the session is real.
  const session = await getSession();
  if (!session) redirect(loginHref({ returnTo: "/onboarding" }));

  return (
    <main id="main" className="mx-auto flex min-h-dvh max-w-3xl flex-col items-center justify-center gap-5 px-6 py-12">
      <OnboardingForm />
      <div className="flex items-center gap-2 text-label text-muted">
        <span>다른 계정으로 로그인하려면</span>
        <LogoutButton variant="ghost" size="sm" />
      </div>
    </main>
  );
}
