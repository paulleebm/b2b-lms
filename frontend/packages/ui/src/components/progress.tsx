import * as React from "react";

import { cn } from "../lib/utils";

/**
 * DESIGN.md §4 Progress: 6px bar, square ends (data has edges), number always shown.
 * No rings or sparklines standing in for content (Impeccable craft-floor).
 */
type ProgressProps = Omit<React.ComponentProps<"div">, "children"> & {
  value: number;
  max?: number;
  /** Accessible name, e.g. "정보보안 기초 진도". */
  label: string;
  /** Visible value text; defaults to the percentage. */
  valueText?: string;
  showValue?: boolean;
  /**
   * accent: the screen's main progress (counts toward the 2-accent budget, DESIGN.md §2).
   * neutral: repeated progress in tables, so the accent is not spent on every row.
   */
  tone?: "accent" | "neutral";
};

function Progress({
  value,
  max = 100,
  label,
  valueText,
  showValue = true,
  tone = "accent",
  className,
  ...props
}: ProgressProps) {
  const clamped = Math.min(Math.max(value, 0), max);
  const percent = max === 0 ? 0 : Math.round((clamped / max) * 100);
  const text = valueText ?? `${percent}%`;

  return (
    <div data-slot="progress" className={cn("flex items-center gap-3", className)} {...props}>
      <div
        role="progressbar"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={max}
        aria-valuenow={clamped}
        aria-valuetext={text}
        className="h-1.5 flex-1 overflow-hidden bg-surface-sunken"
      >
        <div
          className={cn(
            "h-full transition-[width] duration-300 ease-standard",
            tone === "accent" ? "bg-accent" : "bg-ink-2",
          )}
          style={{ width: `${percent}%` }}
        />
      </div>
      {showValue ? <span className="min-w-14 shrink-0 text-right text-label whitespace-nowrap text-ink-2 tabular">{text}</span> : null}
    </div>
  );
}

export { Progress, type ProgressProps };
