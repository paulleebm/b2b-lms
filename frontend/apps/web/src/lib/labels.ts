import type { BadgeProps } from "@lms/ui";

import type { MembershipStatus, Role } from "@/server/api-types";

export const ROLE_LABELS: Record<Role, string> = {
  ORG_ADMIN: "조직 관리자",
  INSTRUCTOR: "강사",
  LEARNER: "학습자",
};

export function roleLabel(role: Role | null | undefined): string {
  return role ? ROLE_LABELS[role] : "-";
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
