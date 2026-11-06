import type { Metadata } from "next";
import type { Dashboard } from "@lms/api-client";
import { AdminDashboard, ErrorState, PageHeader, Panel } from "@lms/ui";

import { ApiError, apiFetch } from "@/server/api";
import { requireMe } from "@/server/me";

import { DashboardClient } from "./dashboard-client";

export const metadata: Metadata = { title: "교육 현황" };

export default async function DashboardPage() {
  const me = await requireMe();
  if (me.role !== "ORG_ADMIN") {
    return (
      <>
        <PageHeader title="교육 현황" />
        <Panel>
          <ErrorState title="조직 관리자만 볼 수 있는 화면입니다" description="교육 현황이 필요하면 조직 관리자에게 요청하세요." />
        </Panel>
      </>
    );
  }
  let dashboard: Dashboard | null = null;
  let traceId: string | undefined;
  try {
    dashboard = await apiFetch<Dashboard>("/v1/dashboard");
  } catch (error) {
    if (!(error instanceof ApiError)) throw error;
    traceId = error.traceId;
  }
  if (!dashboard) return <AdminDashboard state="error" errorTraceId={traceId} rows={[]} assignHref="/app/assignments" />;
  return <DashboardClient dashboard={dashboard} />;
}
