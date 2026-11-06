import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { CircleAlert, CircleCheck, Info, TriangleAlert, type LucideIcon } from "lucide-react";

import { cn } from "../lib/utils";

/**
 * Inline notice (DESIGN.md §2 Semantic, §4). Tinted ground + icon + text — no coloured
 * left border (anti-ai-slop). Errors answer: what happened → why → what to do.
 */
const noticeVariants = cva("flex gap-3 rounded-panel px-4 py-3 text-body-sm", {
  variants: {
    tone: {
      info: "bg-info-soft text-info",
      success: "bg-success-soft text-success",
      warning: "bg-warning-soft text-warning",
      danger: "bg-danger-soft text-danger",
    },
  },
  defaultVariants: { tone: "info" },
});

const icons: Record<NonNullable<NoticeProps["tone"]>, LucideIcon> = {
  info: Info,
  success: CircleCheck,
  warning: TriangleAlert,
  danger: CircleAlert,
};

type NoticeProps = Omit<React.ComponentProps<"div">, "title"> &
  VariantProps<typeof noticeVariants> & {
    title: React.ReactNode;
    action?: React.ReactNode;
  };

function Notice({ className, tone = "info", title, action, children, ...props }: NoticeProps) {
  const Icon = icons[tone ?? "info"];
  // Errors interrupt (assertive); everything else is announced politely.
  const role = tone === "danger" ? "alert" : "status";
  return (
    <div data-slot="notice" role={role} className={cn(noticeVariants({ tone }), className)} {...props}>
      <Icon className="mt-0.5 size-4 shrink-0 stroke-[1.75]" aria-hidden="true" />
      <div className="grid min-w-0 flex-1 gap-0.5">
        <p className="font-semibold">{title}</p>
        {children ? <div className="text-ink-2">{children}</div> : null}
      </div>
      {action ? <div className="shrink-0 self-center">{action}</div> : null}
    </div>
  );
}

export { Notice, noticeVariants, type NoticeProps };
