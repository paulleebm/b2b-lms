import type { BadgeProps } from "@lms/ui";

import type { MembershipStatus, Role } from "@/server/api-types";

export const ROLE_LABELS: Record<Role, string> = {
  ORG_ADMIN: "조직 관리자",
  INSTRUCTOR: "강사",
  LEARNER: "학습자",
};

/** Accepts any string: an unknown role renders as itself instead of breaking the page. */
export function roleLabel(role: string | null | undefined): string {
  return role ? (ROLE_LABELS[role as Role] ?? role) : "-";
}

const STATUS: Record<MembershipStatus, { label: string; tone: BadgeProps["tone"] }> = {
  ACTIVE: { label: "활성", tone: "success" },
  INVITED: { label: "초대됨", tone: "info" },
  DISABLED: { label: "비활성", tone: "neutral" },
};

/** The API may add statuses later; unknown ones render neutrally instead of breaking the page. */
export function statusBadge(status: string): { label: string; tone: BadgeProps["tone"] } {
  return STATUS[status as MembershipStatus] ?? { label: status, tone: "neutral" };
}

const COURSE_STATUS: Record<string, { label: string; tone: BadgeProps["tone"] }> = {
  DRAFT: { label: "초안", tone: "neutral" },
  PUBLISHED: { label: "게시됨", tone: "success" },
  ARCHIVED: { label: "보관됨", tone: "neutral" },
};

export function courseStatus(status: string): { label: string; tone: BadgeProps["tone"] } {
  return COURSE_STATUS[status] ?? { label: status, tone: "neutral" };
}

const dateTime = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul",
  month: "numeric",
  day: "numeric",
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
});

/** "11. 6. 10:42" in Korea time, the same on server and client (no hydration mismatch). */
export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso));
}
