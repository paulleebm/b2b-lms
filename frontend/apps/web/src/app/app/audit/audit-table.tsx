"use client";

import type { AuditEntry } from "@lms/api-client";
import { Badge, DataTable, type Column } from "@lms/ui";

import { formatDateTime, roleLabel } from "@/lib/labels";

/** Machine action codes → what a person reads. Unknown codes show as they are. */
const ACTIONS: Record<string, string> = {
  MEMBERS_INVITED: "멤버 초대",
  MEMBER_ROLE_CHANGED: "역할 변경",
  MEMBER_STATUS_CHANGED: "상태 변경",
  MEMBER_DEPARTMENT_CHANGED: "부서 변경",
  COURSE_PUBLISHED: "코스 게시",
  COURSE_DRAFT: "게시 취소",
  COURSE_ARCHIVED: "코스 보관",
  COURSE_ASSIGNED: "교육 배정",
  REMINDERS_SENT: "리마인더 발송",
  COURSE_COMPLETED: "수료",
};

const STATUS: Record<string, string> = { ACTIVE: "활성", INVITED: "초대됨", DISABLED: "비활성" };

function describe(e: AuditEntry): string {
  const m = e.metadata as Record<string, unknown>;
  switch (e.action) {
    case "MEMBERS_INVITED":
      return `${m.count}명 · ${roleLabel(String(m.role))}`;
    case "MEMBER_ROLE_CHANGED":
      return `${roleLabel(String(m.from))} → ${roleLabel(String(m.to))}`;
    case "MEMBER_STATUS_CHANGED":
      return `${STATUS[String(m.from)] ?? m.from} → ${STATUS[String(m.to)] ?? m.to}`;
    case "COURSE_ASSIGNED":
      return `${m.title} · 새 수강 ${m.newEnrollments}건`;
    case "REMINDERS_SENT":
      return `${m.count}건`;
    case "COURSE_COMPLETED":
      return `${m.title} · ${m.certificate}`;
    default:
      return typeof m.title === "string" ? m.title : "";
  }
}

export function AuditTable({ entries }: { entries: AuditEntry[] }) {
  const columns: Column<AuditEntry>[] = [
    { key: "at", header: "시각", width: "w-[132px]", cell: (e) => <span className="tabular">{formatDateTime(e.at)}</span> },
    { key: "actor", header: "행위자", width: "w-[140px]", cell: (e) => e.actorName ?? <span className="text-muted">시스템</span> },
    { key: "action", header: "동작", width: "w-[120px]", cell: (e) => <Badge>{ACTIONS[e.action] ?? e.action}</Badge> },
    { key: "detail", header: "내용", cell: (e) => <span title={describe(e)}>{describe(e)}</span> },
    {
      key: "trace",
      header: "추적 ID",
      width: "w-[120px]",
      secondary: true,
      cell: (e) => (e.traceId ? <code className="text-caption text-muted">{e.traceId.slice(0, 12)}</code> : null),
    },
  ];
  return (
    <DataTable
      caption="감사 로그"
      columns={columns}
      rows={entries}
      rowKey={(e) => e.id}
      minWidth="min-w-[44rem]"
      empty={{ title: "아직 기록이 없습니다", description: "멤버를 초대하거나 코스를 게시하면 여기에 남습니다." }}
    />
  );
}
