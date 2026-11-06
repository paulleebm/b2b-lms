import * as React from "react";

import { cn } from "../lib/utils";
import { Skeleton } from "./states";

/**
 * DESIGN.md §5 dashboard summary. One strip split by rules — not four identical cards
 * (card-grid cliché) and not the hero-metric template. Every number carries its context.
 */
type Stat = {
  label: string;
  value: number | string;
  /** Unit after the number, e.g. "명", "%". */
  unit?: string;
  /** Comparison or context, e.g. "지난주 대비 +6명". */
  context?: string;
  /** Marks the one figure that needs action (e.g. overdue). */
  emphasis?: "danger";
};

function StatStrip({ stats, loading = false, className }: { stats: Stat[]; loading?: boolean; className?: string }) {
  return (
    <dl
      data-slot="stat-strip"
      aria-busy={loading || undefined}
      className={cn(
        "grid grid-cols-2 overflow-hidden rounded-panel border border-line bg-surface md:grid-flow-col md:auto-cols-fr md:grid-cols-none",
        className,
      )}
    >
      {stats.map((s, i) => (
        <div
          key={s.label}
          className={cn(
            "grid content-start gap-1 border-line px-5 py-4",
            i > 0 && "md:border-l",
            i % 2 === 1 && "border-l md:border-l",
            i >= 2 && "border-t md:border-t-0",
          )}
        >
          <dt className="text-label font-medium text-muted">{s.label}</dt>
          <dd className="flex items-baseline gap-1">
            {loading ? (
              <Skeleton className="h-8 w-16" />
            ) : (
              <>
                <span
                  className={cn(
                    "text-headline font-bold tabular",
                    s.emphasis === "danger" ? "text-danger" : "text-ink",
                  )}
                >
                  {typeof s.value === "number" ? s.value.toLocaleString("ko-KR") : s.value}
                </span>
                {s.unit ? <span className="text-body-sm text-ink-2">{s.unit}</span> : null}
              </>
            )}
          </dd>
          {s.context && !loading ? <dd className="text-caption text-muted">{s.context}</dd> : null}
        </div>
      ))}
    </dl>
  );
}

export { StatStrip, type Stat };
