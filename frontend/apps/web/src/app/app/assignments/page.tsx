import type { Metadata } from "next";
import type { AssignmentView, CourseSummary, Department, MemberView } from "@lms/api-client";
import { ErrorState, PageHeader, Panel } from "@lms/ui";

import { ApiError, apiFetch } from "@/server/api";
import { requireMe } from "@/server/me";

import { AssignmentsClient } from "./assignments-client";

export const metadata: Metadata = { title: "교육 배정" };

export default async function AssignmentsPage() {
  const me = await requireMe();
  if (me.role !== "ORG_ADMIN") {
    return (
      <>
        <PageHeader title="교육 배정" />
        <Panel>
          <ErrorState title="조직 관리자만 볼 수 있는 화면입니다" description="교육 배정이 필요하면 회사의 조직 관리자에게 요청하세요." />
        </Panel>
      </>
    );
  }

  let data: { courses: CourseSummary[]; departments: Department[]; members: MemberView[]; assignments: AssignmentView[] } | null = null;
  let traceId: string | undefined;
  try {
    const [courses, departments, members, assignments] = await Promise.all([
      apiFetch<CourseSummary[]>("/v1/courses"),
      apiFetch<Department[]>("/v1/departments"),
      apiFetch<MemberView[]>("/v1/members"),
      apiFetch<AssignmentView[]>("/v1/assignments"),
    ]);
    data = { courses, departments, members, assignments };
  } catch (error) {
    if (!(error instanceof ApiError)) throw error;
    traceId = error.traceId;
  }

  if (!data) {
    return (
      <>
        <PageHeader title="교육 배정" />
        <Panel>
          <ErrorState title="배정 화면을 불러오지 못했습니다" description="잠시 후 다시 시도해 주세요." traceId={traceId} />
        </Panel>
      </>
    );
  }

  return (
    <AssignmentsClient
      courses={data.courses.filter((c) => c.status === "PUBLISHED")}
      departments={data.departments}
      members={data.members.filter((m) => m.status !== "DISABLED")}
      assignments={data.assignments}
    />
  );
}
