import type { Metadata } from "next";
import { Badge, Button, PageHeader, Panel, PanelBody, PanelHeader } from "@lms/ui";

import { roleLabel, statusBadge } from "@/lib/labels";
import { loginHref } from "@/lib/return-to";
import { requireMe } from "@/server/me";

export const metadata: Metadata = { title: "홈" };

export default async function AppHomePage() {
  const me = await requireMe();
  const tenant = me.currentTenant!;
  const canSwitch = me.memberships.length > 1;

  return (
    <>
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
    </>
  );
}
