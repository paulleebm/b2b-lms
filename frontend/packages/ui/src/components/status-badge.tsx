import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { CircleCheck, CircleDashed, CircleDot, Clock3, TriangleAlert, type LucideIcon } from "lucide-react";

import { cn } from "../lib/utils";

/** Generic small chip (DESIGN.md §4 Badges). Always text; colour is a second channel. */
const badgeVariants = cva(
  "inline-flex h-[22px] items-center gap-1 rounded-badge px-1.5 text-label font-medium whitespace-nowrap tabular",
  {
    variants: {
      tone: {
        neutral: "bg-surface-sunken text-ink-2",
        info: "bg-info-soft text-info",
        warning: "bg-warning-soft text-warning",
        danger: "bg-danger-soft text-danger",
        success: "bg-success-soft text-success",
      },
    },
    defaultVariants: { tone: "neutral" },
  },
);

type BadgeProps = React.ComponentProps<"span"> & VariantProps<typeof badgeVariants>;

function Badge({ className, tone, ...props }: BadgeProps) {
  return <span data-slot="badge" className={cn(badgeVariants({ tone }), className)} {...props} />;
}

/**
 * The enrollment status vocabulary, identical on every screen (DESIGN.md §2).
 * `overdue` and `due-soon` are derived from the due date, never stored (docs/04 §2.5).
 */
type EnrollmentStatus =
  | { kind: "not-started" }
  | { kind: "in-progress"; percent: number }
  | { kind: "due-soon"; daysLeft: number }
  | { kind: "overdue" }
  | { kind: "completed"; completedAt?: string };

const statusIcon: Record<EnrollmentStatus["kind"], LucideIcon> = {
  "not-started": CircleDashed,
  "in-progress": CircleDot,
  "due-soon": Clock3,
  overdue: TriangleAlert,
  completed: CircleCheck,
};

function StatusBadge({ status, className }: { status: EnrollmentStatus; className?: string }) {
  const Icon = statusIcon[status.kind];
  const view = {
    "not-started": { tone: "neutral" as const, text: "미시작" },
    "in-progress": { tone: "info" as const, text: `진행 중 ${"percent" in status ? status.percent : 0}%` },
    "due-soon": { tone: "warning" as const, text: `마감 D-${"daysLeft" in status ? status.daysLeft : 0}` },
    overdue: { tone: "danger" as const, text: "마감 지남" },
    completed: { tone: "success" as const, text: "수료" },
  }[status.kind];

  return (
    <Badge tone={view.tone} className={className}>
      <Icon className="size-3 stroke-[2]" aria-hidden="true" />
      {view.text}
    </Badge>
  );
}

export { Badge, badgeVariants, StatusBadge, type BadgeProps, type EnrollmentStatus };
