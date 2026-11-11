import type { Metadata } from "next";
import { notFound } from "next/navigation";
import type { CourseDetail } from "@lms/api-client";
import { ErrorState, PageHeader, Panel } from "@lms/ui";

import { ApiError, apiFetchWithEtag } from "@/server/api";

import { CourseEditor } from "./course-editor";

export const metadata: Metadata = { title: "코스 편집" };

export default async function CourseEditorPage({ params }: { params: Promise<{ courseId: string }> }) {
  const { courseId } = await params;
  if (!/^[0-9a-f-]{36}$/i.test(courseId)) notFound();

  let loaded: { data: CourseDetail; etag: string | null } | null = null;
  let failure: ApiError | null = null;
  try {
    loaded = await apiFetchWithEtag<CourseDetail>(`/v1/courses/${courseId}`);
  } catch (error) {
    if (!(error instanceof ApiError)) throw error;
    failure = error;
  }
  if (loaded) {
    const { data, etag } = loaded;
    return <CourseEditor key={data.id} initial={data} initialEtag={etag ?? `"${data.version}"`} />;
  }
  if (failure?.status === 404) notFound();
  const forbidden = failure?.status === 403;
  return (
    <>
      <PageHeader title="코스 편집" />
      <Panel>
        <ErrorState
          title={forbidden ? "이 코스를 고칠 권한이 없습니다" : "코스를 불러오지 못했습니다"}
          description={
            forbidden ? "강사는 자신이 만든 코스만 고칠 수 있습니다." : "잠시 후 다시 시도해 주세요."
          }
          traceId={failure?.traceId}
        />
      </Panel>
    </>
  );
}
