"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import * as React from "react";
import { Building2, Send, UserRound } from "lucide-react";
import type { AssignmentView, AssignResult, CourseSummary, Department, MemberView } from "@lms/api-client";
import {
  Badge,
  Button,
  DataTable,
  EmptyState,
  Field,
  Input,
  Notice,
  PageHeader,
  Panel,
  PanelBody,
  PanelFooter,
  PanelHeader,
  Select,
  type Column,
} from "@lms/ui";

import { ClientApiError, describeError } from "@/lib/client-api";
import { formatDateTime } from "@/lib/labels";
import { koreanDate } from "@/lib/learning";

/**
 * Assign a published course to departments and/or people. One Idempotency-Key per form
 * submission: a double click or a network retry returns the first result instead of assigning
 * twice; the key is renewed only after a success.
 */
export function AssignmentsClient({
  courses,
  departments,
  members,
  assignments,
}: {
  courses: CourseSummary[];
  departments: Department[];
  members: MemberView[];
  assignments: AssignmentView[];
}) {
  const router = useRouter();
  const [courseId, setCourseId] = React.useState(courses[0]?.id ?? "");
  const [departmentIds, setDepartmentIds] = React.useState<string[]>([]);
  const [memberIds, setMemberIds] = React.useState<string[]>([]);
  const [due, setDue] = React.useState("");
  const [query, setQuery] = React.useState("");
  const [error, setError] = React.useState<string | null>(null);
  const [problem, setProblem] = React.useState<{ title: string; traceId?: string } | null>(null);
  const [result, setResult] = React.useState<string | null>(null);
  const [sending, setSending] = React.useState(false);
  // One key per submission attempt, renewed after a success (state, not a ref: read during render-free handlers).
  const [idempotencyKey, setIdempotencyKey] = React.useState(() => crypto.randomUUID());

  const toggle = (list: string[], id: string) => (list.includes(id) ? list.filter((x) => x !== id) : [...list, id]);
  const visibleMembers = members.filter((m) =>
    `${m.name ?? ""} ${m.email}`.toLowerCase().includes(query.trim().toLowerCase()),
  );

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setResult(null);
    setProblem(null);
    if (!courseId) return setError("배정할 코스를 고르세요.");
    if (departmentIds.length + memberIds.length === 0) return setError("부서나 멤버를 하나 이상 고르세요.");
    setError(null);
    setSending(true);
    try {
      const response = await fetch("/api/v1/assignments", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json, application/problem+json",
          "Idempotency-Key": idempotencyKey,
        },
        body: JSON.stringify({
          courseId,
          departmentIds,
          membershipIds: memberIds,
          // The end of the chosen day in Korea time.
          dueAt: due ? new Date(`${due}T23:59:59+09:00`).toISOString() : null,
        }),
      });
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new ClientApiError(response.status, { status: response.status, ...body });
      }
      const data = (await response.json()) as AssignResult;
      const parts = [`${data.targeted}명에게 배정했습니다`, `새로 ${data.created}명`];
      if (data.existing) parts.push(`이미 듣던 ${data.existing}명은 마감일만 확인`);
      if (data.notActive) parts.push(`가입 전 ${data.notActive}명은 가입하면 배정`);
      setResult(parts.join(" · ") + ".");
      setIdempotencyKey(crypto.randomUUID());
      setDepartmentIds([]);
      setMemberIds([]);
      router.refresh();
    } catch (err) {
      setProblem(describeError(err));
    } finally {
      setSending(false);
    }
  }

  const columns: Column<AssignmentView>[] = [
    { key: "course", header: "코스", cell: (a) => <span className="font-medium text-ink" title={a.courseTitle}>{a.courseTitle}</span> },
    {
      key: "target",
      header: "대상",
      width: "w-[200px]",
      cell: (a) => (
        <span className="inline-flex min-w-0 items-center gap-1.5">
          {a.targetType === "DEPARTMENT" ? (
            <Building2 className="size-3.5 shrink-0 text-muted" aria-label="부서" />
          ) : (
            <UserRound className="size-3.5 shrink-0 text-muted" aria-label="멤버" />
          )}
          <span className="truncate">{a.targetName ?? "-"}</span>
        </span>
      ),
    },
    {
      key: "due",
      header: "마감",
      width: "w-[112px]",
      cell: (a) => <span className="tabular">{a.dueAt ? koreanDate(a.dueAt) : "없음"}</span>,
    },
    {
      key: "progress",
      header: "코스 수료",
      width: "w-[110px]",
      align: "right",
      secondary: true,
      cell: (a) => (
        <span className="tabular">
          {a.courseCompleted}/{a.courseEnrolled}명
        </span>
      ),
    },
    {
      key: "created",
      header: "배정일",
      width: "w-[140px]",
      align: "right",
      secondary: true,
      cell: (a) => <span className="tabular">{formatDateTime(a.createdAt)}</span>,
    },
  ];

  return (
    <>
      <PageHeader
        title="교육 배정"
        description="게시된 코스를 부서나 직원에게 배정합니다. 부서에 배정하면 나중에 그 부서로 오는 사람도 자동으로 배정됩니다."
      />
      <div className="grid gap-6">
        {courses.length === 0 ? (
          <Panel>
            <EmptyState
              icon={Send}
              title="배정할 수 있는 코스가 없습니다"
              description="코스를 게시해야 배정할 수 있습니다."
              action={
                <Button asChild variant="secondary">
                  <Link href="/app/studio">코스 스튜디오로</Link>
                </Button>
              }
            />
          </Panel>
        ) : (
          <Panel>
            <PanelHeader title="새 배정" />
            <form noValidate onSubmit={submit}>
              <PanelBody className="grid gap-5 md:grid-cols-2">
                <Field label="코스">
                  <Select value={courseId} onChange={(e) => setCourseId(e.target.value)}>
                    {courses.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.title}
                      </option>
                    ))}
                  </Select>
                </Field>
                <Field label="마감일" optional hint="그날 자정(한국 시간)까지입니다. 이미 듣는 사람은 더 이른 마감일이 적용됩니다.">
                  <Input type="date" value={due} onChange={(e) => setDue(e.target.value)} />
                </Field>

                <fieldset className="grid content-start gap-2">
                  <legend className="mb-2 text-label font-medium text-ink">부서</legend>
                  {departments.length === 0 ? (
                    <p className="text-body-sm text-muted">
                      부서가 없습니다. <Link href="/app/departments" className="underline">부서 만들기</Link>
                    </p>
                  ) : (
                    departments.map((d) => (
                      <label key={d.id} className="flex items-center gap-2 text-body text-ink">
                        <input
                          type="checkbox"
                          className="size-4 accent-[var(--accent)]"
                          checked={departmentIds.includes(d.id)}
                          onChange={() => setDepartmentIds((prev) => toggle(prev, d.id))}
                        />
                        {d.name}
                      </label>
                    ))
                  )}
                </fieldset>

                <fieldset className="grid content-start gap-2">
                  <legend className="mb-2 text-label font-medium text-ink">
                    멤버 {memberIds.length > 0 ? <span className="text-muted">({memberIds.length}명 선택)</span> : null}
                  </legend>
                  <Input
                    type="search"
                    aria-label="멤버 검색"
                    placeholder="이름이나 이메일로 찾기"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                  />
                  <div className="max-h-56 overflow-y-auto rounded-control border border-line">
                    {visibleMembers.length === 0 ? (
                      <p className="px-3 py-2 text-body-sm text-muted">찾는 멤버가 없습니다.</p>
                    ) : (
                      visibleMembers.map((m) => (
                        <label key={m.id} className="flex items-center gap-2 border-b border-line px-3 py-2 text-body last:border-b-0">
                          <input
                            type="checkbox"
                            className="size-4 accent-[var(--accent)]"
                            checked={memberIds.includes(m.id)}
                            onChange={() => setMemberIds((prev) => toggle(prev, m.id))}
                          />
                          <span className="min-w-0 flex-1 truncate text-ink">{m.name || m.email}</span>
                          {m.status === "INVITED" ? <Badge>가입 전</Badge> : null}
                        </label>
                      ))
                    )}
                  </div>
                </fieldset>
              </PanelBody>
              <PanelFooter className="flex-wrap justify-between">
                <div className="min-w-0 flex-1">
                  {error ? <p className="text-label text-danger">{error}</p> : null}
                  {result ? <Notice tone="success" title={result} className="py-2" /> : null}
                  {problem ? <Notice tone="danger" title={problem.title} className="py-2" /> : null}
                </div>
                <Button type="submit" loading={sending}>
                  <Send aria-hidden="true" />
                  배정하기
                </Button>
              </PanelFooter>
            </form>
          </Panel>
        )}

        <section aria-labelledby="assignments-title" className="grid gap-3">
          <h2 id="assignments-title" className="text-title-sm font-semibold text-ink">
            배정 기록
          </h2>
          <DataTable
            caption="배정 기록"
            columns={columns}
            rows={assignments}
            rowKey={(a) => a.id}
            minWidth="min-w-[40rem]"
            empty={{ title: "아직 배정한 교육이 없습니다", description: "위에서 코스와 대상을 골라 배정하세요." }}
          />
        </section>
      </div>
    </>
  );
}
