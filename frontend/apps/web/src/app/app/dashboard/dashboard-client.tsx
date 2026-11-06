"use client";

import { useRouter } from "next/navigation";
import type { Dashboard, ReminderResult } from "@lms/api-client";
import { AdminDashboard, type LearnerRow } from "@lms/ui";

import { api } from "@/lib/client-api";
import { formatDateTime } from "@/lib/labels";
import { seoulDate } from "@/lib/learning";

const DUE_SOON_MS = 3 * 24 * 60 * 60 * 1000;
const REMIND_WINDOW_MS = 20 * 60 * 60 * 1000;   // the server throttles to one reminder per ~day

/** API rows → the design system's rows; status is derived the same way as the learner's badge. */
function toRow(p: Dashboard["pending"][number], now: number): LearnerRow {
  const due = p.dueAt ? new Date(p.dueAt).getTime() : null;
  const status: LearnerRow["status"] = p.overdue
    ? { kind: "overdue" }
    : due !== null && due - now <= DUE_SOON_MS
      ? { kind: "due-soon", daysLeft: Math.max(0, Math.ceil((due - now) / (24 * 60 * 60 * 1000))) }
      : p.status === "IN_PROGRESS"
        ? { kind: "in-progress", percent: p.lessonsTotal ? Math.round((p.lessonsDone / p.lessonsTotal) * 100) : 0 }
        : { kind: "not-started" };
  return {
    id: p.enrollmentId,
    name: p.memberName,
    department: p.departmentName ?? "부서 없음",
    course: p.courseTitle,
    status,
    dueAt: p.dueAt ? seoulDate(p.dueAt) : "",
    progress: p.lessonsTotal ? Math.round((p.lessonsDone / p.lessonsTotal) * 100) : 0,
  };
}

export function DashboardClient({ dashboard }: { dashboard: Dashboard }) {
  const router = useRouter();
  const now = new Date(dashboard.generatedAt).getTime();
  const { kpi } = dashboard;

  const rows = dashboard.pending.map((p) => toRow(p, now));
  const remindedIds = dashboard.pending
    .filter((p) => p.remindedAt && now - new Date(p.remindedAt).getTime() < REMIND_WINDOW_MS)
    .map((p) => p.enrollmentId);

  return (
    <AdminDashboard
      state={kpi.enrolled === 0 ? "empty" : "populated"}
      rows={rows}
      stats={{
        total: kpi.enrolled,
        completed: kpi.completed,
        inProgress: kpi.inProgress,
        notStarted: kpi.notStarted,
        overdue: kpi.overdue,
        completionRate: kpi.completionRate,
        courseCount: dashboard.courses.length,
      }}
      departments={dashboard.departments
        .filter((d) => d.enrolled > 0)
        .map((d) => ({
          label: d.name,
          value: d.completionRate,
          overdue: d.enrolled ? Math.round((d.overdue / d.enrolled) * 100) : 0,
          detail: `${d.completed}/${d.enrolled}`,
        }))}
      remindedIds={remindedIds}
      assignHref="/app/assignments"
      description={`활성 멤버 ${kpi.activeMembers}명의 필수 교육 진행 상황입니다. ${formatDateTime(dashboard.generatedAt)} 기준 (최대 1분 전 집계).`}
      onRemind={async (enrollmentId) => {
        await api<ReminderResult>("POST", "/v1/reminders", { body: { enrollmentIds: [enrollmentId] } });
      }}
      onRemindAll={async () => {
        const { data } = await api<ReminderResult>("POST", "/v1/reminders", { body: {} });
        router.refresh();
        return data.queued > 0
          ? `${data.queued}명에게 리마인더를 보냈습니다. 오늘 이미 받은 사람은 건너뜁니다.`
          : "보낼 사람이 없습니다. 오늘 이미 모두에게 보냈습니다.";
      }}
    />
  );
}
