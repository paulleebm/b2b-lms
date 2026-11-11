import type { Metadata } from "next";
import type { Department, MemberView } from "@lms/api-client";
import { ErrorState, PageHeader, Panel } from "@lms/ui";

import { ApiError, apiFetch } from "@/server/api";
import { requireMe } from "@/server/me";

import { MembersClient } from "./members-client";

export const metadata: Metadata = { title: "멤버" };

export default async function MembersPage() {
  const me = await requireMe();
  if (me.role !== "ORG_ADMIN") {
    return (
      <>
        <PageHeader title="멤버" />
        <Panel>
          <ErrorState title="조직 관리자만 볼 수 있는 화면입니다" description="역할이 필요하면 회사의 조직 관리자에게 요청하세요." />
        </Panel>
      </>
    );
  }

  let data: { members: MemberView[]; departments: Department[] } | { traceId?: string };
  try {
    const [members, departments] = await Promise.all([
      apiFetch<MemberView[]>("/v1/members"),
      apiFetch<Department[]>("/v1/departments"),
    ]);
    data = { members, departments };
  } catch (error) {
    data = { traceId: error instanceof ApiError ? error.traceId : undefined };
  }

  if ("members" in data) {
    return <MembersClient members={data.members} departments={data.departments} selfUserId={me.userId} />;
  }
  const { traceId } = data;
  return (
    <>
      <PageHeader title="멤버" />
      <Panel>
        <ErrorState title="멤버 목록을 불러오지 못했습니다" description="잠시 후 다시 시도해 주세요." traceId={traceId} />
      </Panel>
    </>
  );
}
