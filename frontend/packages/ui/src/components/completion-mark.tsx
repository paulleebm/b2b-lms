import * as React from "react";
import { Check } from "lucide-react";

import { cn } from "../lib/utils";

/**
 * Completion mark (DESIGN.md §4): a check in a soft green circle, the common "done" sign.
 * Used where a course is completed or a certificate is issued — course rows, the completion
 * moment, the public verification page. Small rows use StatusBadge's "수료" chip instead.
 */
type CompletionMarkProps = Omit<React.ComponentProps<"span">, "children"> & {
  size?: "md" | "lg";
  /** Completion date read to assistive tech, e.g. "2026. 11. 6." */
  completedAt?: string;
};

const sizes = {
  md: { box: "size-10", icon: "size-5" },
  lg: { box: "size-16", icon: "size-8" },
} as const;

function CompletionMark({ size = "md", completedAt, className, ...props }: CompletionMarkProps) {
  const s = sizes[size];
  return (
    <span
      data-slot="completion-mark"
      role="img"
      aria-label={completedAt ? `수료 (${completedAt})` : "수료"}
      className={cn("inline-grid shrink-0 place-items-center rounded-full bg-success-soft text-success", s.box, className)}
      {...props}
    >
      <Check className={cn(s.icon, "stroke-[2.5]")} aria-hidden="true" />
    </span>
  );
}

export { CompletionMark, type CompletionMarkProps };
