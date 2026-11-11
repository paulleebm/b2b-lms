import type { Metadata } from "next";
import type { MyEnrollment } from "@lms/api-client";
import { Badge, Button, LearnerHome, Panel, PanelHeader } from "@lms/ui";

import { roleLabel, statusBadge } from "@/lib/labels";
import { toLearnerCourse } from "@/lib/learning";
import { loginHref } from "@/lib/return-to";
import { ApiError, apiFetch } from "@/server/api";
import { requireMe } from "@/server/me";

export const metadata: Metadata = { title: "내 교육" };

/**
 * Everyone's home is "내 교육": admins and instructors take required courses too.
 * Open courses first (the API sorts by due date), then completed ones with a check mark.
 */
export default async function AppHomePage() {
  const me = await requireMe();
  const tenant = me.currentTenant!;

  let enrollments: MyEnrollment[] | null = null;
  try {
    enrollments = await apiFetch<MyEnrollment[]>("/v1/my/enrollments");
  } catch (error) {
    if (!(error instanceof ApiError)) throw error;
  }

  const open = (enrollments ?? []).filter((e) => e.status !== "COMPLETED").map(toLearnerCourse);
  const completed = (enrollments ?? []).filter((e) => e.status === "COMPLETED").map(toLearnerCourse);
  const canSwitch = me.memberships.length > 1;

  const switcher = canSwitch ? (
    <Panel>
      <PanelHeader title="소속 회사" description="다른 회사로 전환하면 그 회사의 권한으로 다시 로그인합니다." />
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
              ) : membership.status === "ACTIVE" ? (
                <Button asChild size="sm" variant="secondary">
                  <a href={loginHref({ org: membership.slug, returnTo: "/app" })}>전환</a>
                </Button>
              ) : null}
            </li>
          );
        })}
      </ul>
    </Panel>
  ) : null;

  return (
    <LearnerHome
      state={enrollments === null ? "error" : open.length + completed.length === 0 ? "empty" : "populated"}
      courses={open}
      completed={completed}
      footer={switcher}
    />
  );
}
