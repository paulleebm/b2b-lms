import type { Metadata } from "next";
import type { CourseSummary } from "@lms/api-client";
import { ErrorState, PageHeader, Panel } from "@lms/ui";

import { ApiError, apiFetch } from "@/server/api";
import { requireMe } from "@/server/me";

import { StudioList } from "./studio-list";

export const metadata: Metadata = { title: "코스 스튜디오" };

export default async function StudioPage() {
  const me = await requireMe();
  const admin = me.role === "ORG_ADMIN";
  if (!admin && me.role !== "INSTRUCTOR") {
    return (
      <>
        <PageHeader title="코스 스튜디오" />
        <Panel>
          <ErrorState title="강사와 조직 관리자만 볼 수 있는 화면입니다" description="코스를 만들어야 한다면 조직 관리자에게 강사 역할을 요청하세요." />
        </Panel>
      </>
    );
  }
  let courses: CourseSummary[] | null = null;
  let traceId: string | undefined;
  try {
    courses = await apiFetch<CourseSummary[]>("/v1/courses");
  } catch (error) {
    traceId = error instanceof ApiError ? error.traceId : undefined;
  }
  if (courses) return <StudioList courses={courses} admin={admin} />;
  return (
    <>
      <PageHeader title={admin ? "코스 스튜디오" : "내 코스"} />
      <Panel>
        <ErrorState
          title="코스 목록을 불러오지 못했습니다"
          description="잠시 후 다시 시도해 주세요."
          traceId={traceId}
        />
      </Panel>
    </>
  );
}
