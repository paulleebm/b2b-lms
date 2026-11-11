import type { Metadata } from "next";
import type { Department } from "@lms/api-client";
import { ErrorState, PageHeader, Panel } from "@lms/ui";

import { ApiError, apiFetch } from "@/server/api";
import { requireMe } from "@/server/me";

import { DepartmentsClient } from "./departments-client";

export const metadata: Metadata = { title: "부서" };

export default async function DepartmentsPage() {
  const me = await requireMe();
  if (me.role !== "ORG_ADMIN") {
    return (
      <>
        <PageHeader title="부서" />
        <Panel>
          <ErrorState title="조직 관리자만 볼 수 있는 화면입니다" description="부서를 바꿔야 한다면 조직 관리자에게 요청하세요." />
        </Panel>
      </>
    );
  }
  let departments: Department[] | null = null;
  let traceId: string | undefined;
  try {
    departments = await apiFetch<Department[]>("/v1/departments");
  } catch (error) {
    if (!(error instanceof ApiError)) throw error;
    traceId = error.traceId;
  }
  if (!departments) {
    return (
      <>
        <PageHeader title="부서" />
        <Panel>
          <ErrorState title="부서 목록을 불러오지 못했습니다" description="잠시 후 다시 시도해 주세요." traceId={traceId} />
        </Panel>
      </>
    );
  }
  return <DepartmentsClient departments={departments} />;
}
