"use client";

import * as React from "react";
import { BellRing, Plus } from "lucide-react";

import { AppShell, PageHeader } from "../components/app-shell";
import { BarChart } from "../components/bar-chart";
import { Button } from "../components/button";
import { DataTable, type Column, type SortState } from "../components/data-table";
import { Notice } from "../components/notice";
import { Panel, PanelBody, PanelHeader } from "../components/panel";
import { Progress } from "../components/progress";
import { StatStrip } from "../components/stat-strip";
import { Badge, StatusBadge } from "../components/status-badge";
import { EmptyState, ErrorState, LoadingRegion, Skeleton } from "../components/states";
import { departmentCompletion, formatDueShort, pendingLearners, type LearnerRow } from "./fixtures";
import { AccountArea, WorkspaceSwitcher, adminNav } from "./shell-presets";

/**
 * Admin "교육 현황" — DESIGN.md §5, adapted from open-design's dashboard template.
 * The template orders summary → chart → table; we put the table of people to act on
 * BEFORE the chart, because product principle 1 (find who needs a nudge within 3 seconds)
 * requires it inside the first viewport. Rows are ordered overdue → due soon.
 */
type ScreenState = "populated" | "loading" | "empty" | "error";

/** Synthetic: people who already completed (they are not in the action table). */
const COMPLETED_COUNT = 45;

const severity: Record<LearnerRow["status"]["kind"], number> = {
  overdue: 0,
  "due-soon": 1,
  "in-progress": 2,
  "not-started": 3,
  completed: 4,
};

function sortRows(rows: LearnerRow[], sort: SortState): LearnerRow[] {
  const copy = [...rows];
  if (!sort) return copy.sort((a, b) => severity[a.status.kind] - severity[b.status.kind]);
  const dir = sort.direction === "asc" ? 1 : -1;
  return copy.sort((a, b) => {
    switch (sort.key) {
      case "dueAt":
        return a.dueAt.localeCompare(b.dueAt) * dir;
      case "progress":
        return (a.progress - b.progress) * dir;
      case "name":
        return a.name.localeCompare(b.name, "ko") * dir;
      default:
        return 0;
    }
  });
}

function AdminDashboardScreen({
  state = "populated",
  rows = pendingLearners,
}: {
  state?: ScreenState;
  rows?: LearnerRow[];
}) {
  const [sort, setSort] = React.useState<SortState>(null);
  const [reminded, setReminded] = React.useState<Set<string>>(new Set());
  const [sending, setSending] = React.useState<string | null>(null);
  const [announcement, setAnnouncement] = React.useState("");

  const sorted = React.useMemo(() => sortRows(rows, sort), [rows, sort]);
  // Every figure on the screen is derived from the same records, so the strip, the notice
  // and the table can never disagree (REVIEW.md: no invented or inconsistent numbers).
  const overdue = rows.filter((r) => r.status.kind === "overdue").length;
  const inProgress = rows.filter((r) => r.status.kind === "in-progress" || r.status.kind === "due-soon").length;
  const notStarted = rows.filter((r) => r.status.kind === "not-started").length;
  const completed = COMPLETED_COUNT;
  const total = completed + rows.length;
  const avgProgress = rows.length ? Math.round(rows.reduce((sum, r) => sum + r.progress, 0) / rows.length) : 0;

  const remind = (row: LearnerRow) => {
    setSending(row.id);
    window.setTimeout(() => {
      setReminded((prev) => new Set(prev).add(row.id));
      setSending(null);
      setAnnouncement(`${row.name}님에게 리마인더를 보냈습니다.`);
    }, 600);
  };

  const columns: Column<LearnerRow>[] = [
    {
      key: "name",
      header: "이름",
      sortable: true,
      width: "w-[148px]",
      cell: (r) => (
        <span className="grid min-w-0">
          <span className="truncate font-medium text-ink" title={r.name}>
            {r.name}
          </span>
          <span className="truncate text-caption text-muted @3xl:hidden" title={r.department}>
            {r.department}
          </span>
        </span>
      ),
    },
    {
      key: "department",
      header: "부서",
      secondary: true,
      width: "w-[120px]",
      cell: (r) => <span title={r.department}>{r.department}</span>,
    },
    { key: "course", header: "교육", cell: (r) => <span title={r.course}>{r.course}</span> },
    { key: "status", header: "상태", width: "w-[104px]", cell: (r) => <StatusBadge status={r.status} /> },
    {
      key: "dueAt",
      header: "마감",
      sortable: true,
      align: "right",
      secondary: true,
      width: "w-[80px]",
      cell: (r) => <time dateTime={r.dueAt}>{formatDueShort(r.dueAt)}</time>,
    },
    {
      key: "progress",
      header: "진도",
      sortable: true,
      align: "right",
      secondary: true,
      width: "w-[128px]",
      cell: (r) => <Progress tone="neutral" value={r.progress} label={`${r.name} ${r.course} 진도`} />,
    },
    {
      key: "action",
      header: "동작",
      align: "right",
      width: "w-[132px]",
      cell: (r) =>
        reminded.has(r.id) ? (
          <span className="text-label text-muted">보냄</span>
        ) : (
          <Button
            variant="ghost"
            size="sm"
            loading={sending === r.id}
            onClick={() => remind(r)}
            aria-label={`${r.name}에게 리마인더 보내기`}
          >
            <BellRing aria-hidden="true" />
            리마인더
          </Button>
        ),
    },
  ];

  const loading = state === "loading";

  return (
    <AppShell
      workspace={<WorkspaceSwitcher name="에이크미" />}
      account={<AccountArea name="김하나" role="조직 관리자" />}
      sections={adminNav.map((s) => ({ ...s, items: s.items.map((i) => (i.label === "교육 현황" ? { ...i, count: overdue || undefined } : i)) }))}
    >
      <PageHeader
        title="교육 현황"
        description="11월 필수 교육의 진행 상황입니다. 마감이 지났거나 임박한 사람부터 보여줍니다."
        actions={
          <>
            <Badge tone="neutral">합성 데이터</Badge>
            <Button>
              <Plus aria-hidden="true" />
              교육 배정
            </Button>
          </>
        }
      />

      <p className="sr-only" role="status" aria-live="polite">
        {announcement}
      </p>

      {state === "error" ? (
        <Panel>
          <ErrorState
            title="교육 현황을 불러오지 못했습니다"
            description="서버와 연결이 잠시 끊겼습니다. 입력한 내용은 없으니 다시 시도해 주세요."
            traceId="4bf92f3577b34da6a3ce929d0e0e4736"
            onRetry={() => undefined}
          />
        </Panel>
      ) : state === "empty" ? (
        <Panel>
          <EmptyState
            title="아직 배정한 교육이 없습니다"
            description="부서나 직원에게 교육을 배정하면 여기에서 수료 현황과 마감이 지난 사람을 바로 볼 수 있습니다."
            action={
              <Button>
                <Plus aria-hidden="true" />첫 교육 배정하기
              </Button>
            }
          />
        </Panel>
      ) : (
        <div className="grid gap-6">
          <StatStrip
            loading={loading}
            stats={[
              { label: "대상자", value: total, unit: "명", context: "필수 교육 3개" },
              { label: "수료", value: completed, unit: "명", context: `수료율 ${Math.round((completed / total) * 100)}%` },
              { label: "진행 중", value: inProgress, unit: "명", context: `미시작 ${notStarted}명 별도` },
              {
                label: "마감 지남",
                value: overdue,
                unit: "명",
                context: overdue ? "리마인더 대상" : "없음",
                emphasis: overdue ? "danger" : undefined,
              },
            ]}
          />

          {overdue > 0 && !loading ? (
            <Notice tone="danger" title={`마감이 지난 직원이 ${overdue}명 있습니다`}>
              아래 표에서 리마인더를 보내거나, 교육 배정 화면에서 마감일을 조정할 수 있습니다.
            </Notice>
          ) : null}

          {/* Product principle 1: the people to act on come before the chart, inside the first viewport. */}
          <section aria-labelledby="pending-title" className="grid content-start gap-3">
            <div className="flex items-end justify-between gap-3">
              <h2 id="pending-title" className="text-title-sm font-semibold text-ink">
                처리가 필요한 직원
              </h2>
              <span className="text-label text-muted tabular">
                {rows.length}명 · 평균 진도 {avgProgress}%
              </span>
            </div>
            <DataTable
              caption="처리가 필요한 직원"
              columns={columns}
              rows={sorted}
              rowKey={(r) => r.id}
              sort={sort}
              onSortChange={setSort}
              loading={loading}
              empty={{
                title: "처리할 직원이 없습니다",
                description: "배정된 교육을 모두 마감 안에 수료했습니다.",
              }}
            />
          </section>

          <Panel>
            <PanelHeader title="부서별 수료율" description="필수 교육 3개 합산" />
            <PanelBody>
              {loading ? (
                <LoadingRegion label="부서별 수료율 불러오는 중" className="grid max-w-3xl gap-4 pt-1">
                  {Array.from({ length: 5 }, (_, i) => (
                    <Skeleton key={i} className="h-3" />
                  ))}
                </LoadingRegion>
              ) : (
                <BarChart title="부서별 수료율" data={departmentCompletion} className="max-w-3xl" />
              )}
            </PanelBody>
          </Panel>
        </div>
      )}
    </AppShell>
  );
}

export { AdminDashboardScreen };
