"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Plus } from "lucide-react";
import type { CourseDetail, CourseSummary } from "@lms/api-client";
import { Badge, Button, DataTable, Notice, PageHeader, type Column } from "@lms/ui";

import { api, describeError } from "@/lib/client-api";
import { courseStatus, formatDateTime } from "@/lib/labels";

export function StudioList({ courses, admin }: { courses: CourseSummary[]; admin: boolean }) {
  const router = useRouter();
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function create() {
    setCreating(true);
    setError(null);
    try {
      const { data } = await api<CourseDetail>("POST", "/v1/courses", { body: { title: "제목 없는 코스" } });
      router.push(`/app/studio/${data.id}`);
    } catch (e) {
      setError(describeError(e).title);
      setCreating(false);
    }
  }

  const columns: Column<CourseSummary>[] = [
    {
      key: "title",
      header: "코스",
      cell: (c) => (
        <Link href={`/app/studio/${c.id}`} className="font-medium text-ink underline-offset-3 hover:underline" title={c.title}>
          {c.title}
        </Link>
      ),
    },
    {
      key: "status",
      header: "상태",
      width: "w-[96px]",
      cell: (c) => {
        const s = courseStatus(c.status);
        return <Badge tone={s.tone}>{s.label}</Badge>;
      },
    },
    {
      key: "outline",
      header: "구성",
      width: "w-[132px]",
      secondary: true,
      cell: (c) => (
        <span className="tabular">
          섹션 {c.sectionCount} · 레슨 {c.lessonCount}
        </span>
      ),
    },
    ...(admin
      ? [{ key: "owner", header: "만든 사람", width: "w-[140px]", secondary: true, cell: (c: CourseSummary) => c.ownerName ?? "-" }]
      : []),
    {
      key: "updated",
      header: "마지막 수정",
      width: "w-[148px]",
      align: "right",
      secondary: true,
      cell: (c) => <span className="tabular">{formatDateTime(c.updatedAt)}</span>,
    },
  ];

  return (
    <>
      <PageHeader
        title={admin ? "코스 스튜디오" : "내 코스"}
        description={admin ? "회사의 모든 코스를 만들고 고칩니다." : "내가 만든 코스를 고치고 게시합니다."}
        actions={
          <Button onClick={create} loading={creating}>
            <Plus aria-hidden="true" />새 코스
          </Button>
        }
      />
      <div className="grid gap-4">
        {error ? <Notice tone="danger" title={error} /> : null}
        <DataTable
          caption="코스 목록"
          columns={columns}
          rows={courses}
          rowKey={(c) => c.id}
          minWidth="min-w-[36rem]"
          empty={{
            title: "아직 코스가 없습니다",
            description: "새 코스를 만들고 섹션과 레슨을 채운 뒤 게시하면 직원에게 배정할 수 있습니다.",
            action: (
              <Button variant="secondary" size="sm" onClick={create} loading={creating}>
                새 코스 만들기
              </Button>
            ),
          }}
        />
      </div>
    </>
  );
}
