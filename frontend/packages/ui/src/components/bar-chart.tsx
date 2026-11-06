import * as React from "react";
import { ChartBar, Table2 } from "lucide-react";

import { cn } from "../lib/utils";
import { Button } from "./button";

/**
 * Horizontal bars for "completion rate by department" (DESIGN.md §5).
 * Plain HTML/CSS instead of a chart library: two charts on the whole product don't justify
 * a dependency, and native elements are easier to make accessible.
 * - Every bar shows its number; overdue share is a second segment in the danger colour.
 * - "표로 보기" shows the same data as a real table (WCAG: charts need a text alternative).
 */
type BarDatum = {
  label: string;
  /** Completed / total, as a share of 100. */
  value: number;
  /** Overdue share of 100 (drawn after the value segment). */
  overdue?: number;
  /** Extra text after the number, e.g. "38/52명". */
  detail?: string;
};

function BarChart({
  title,
  data,
  unit = "%",
  className,
}: {
  /** Accessible name of the chart and caption of its table view. */
  title: string;
  data: BarDatum[];
  unit?: string;
  className?: string;
}) {
  const [asTable, setAsTable] = React.useState(false);
  const tableId = React.useId();

  return (
    <figure data-slot="bar-chart" className={cn("grid gap-3", className)}>
      <div className="flex items-center justify-between gap-3">
        <figcaption className="text-label text-muted">
          <span className="mr-3 inline-flex items-center gap-1.5">
            <span className="inline-block size-2.5 bg-accent" aria-hidden="true" />
            수료
          </span>
          <span className="inline-flex items-center gap-1.5">
            <span className="inline-block size-2.5 bg-danger" aria-hidden="true" />
            마감 지남
          </span>
        </figcaption>
        <Button
          variant="ghost"
          size="sm"
          aria-pressed={asTable}
          aria-controls={tableId}
          onClick={() => setAsTable((v) => !v)}
        >
          {asTable ? <ChartBar aria-hidden="true" /> : <Table2 aria-hidden="true" />}
          {asTable ? "차트로 보기" : "표로 보기"}
        </Button>
      </div>

      {asTable ? (
        <table id={tableId} className="w-full border-collapse text-body-sm">
          <caption className="sr-only">{title}</caption>
          <thead>
            <tr className="border-b border-line text-label text-ink-2">
              <th scope="col" className="py-2 text-left font-medium">
                부서
              </th>
              <th scope="col" className="py-2 text-right font-medium">
                수료율
              </th>
              <th scope="col" className="py-2 text-right font-medium">
                마감 지남
              </th>
            </tr>
          </thead>
          <tbody>
            {data.map((d) => (
              <tr key={d.label} className="border-b border-line last:border-b-0">
                <th scope="row" className="py-2 text-left font-normal text-ink">
                  {d.label}
                </th>
                <td className="py-2 text-right tabular">
                  {d.value}
                  {unit}
                  {d.detail ? <span className="ml-1 text-muted">({d.detail})</span> : null}
                </td>
                <td className="py-2 text-right tabular">
                  {d.overdue ?? 0}
                  {unit}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <ul id={tableId} aria-label={title} className="grid gap-3">
          {data.map((d) => (
            <li key={d.label} className="grid grid-cols-[minmax(5rem,9rem)_1fr_auto] items-center gap-3">
              <span className="truncate text-body-sm text-ink" title={d.label}>
                {d.label}
              </span>
              <span className="flex h-2.5 bg-surface-sunken" aria-hidden="true">
                <span className="h-full bg-accent" style={{ width: `${d.value}%` }} />
                {d.overdue ? <span className="h-full bg-danger" style={{ width: `${d.overdue}%` }} /> : null}
              </span>
              <span className="w-24 text-right text-body-sm tabular text-ink">
                <span className="sr-only">{d.label} 수료율 </span>
                {d.value}
                {unit}
                {d.detail ? <span className="ml-1 text-caption text-muted">{d.detail}</span> : null}
                {d.overdue ? <span className="sr-only">, 마감 지남 {d.overdue}{unit}</span> : null}
              </span>
            </li>
          ))}
        </ul>
      )}
    </figure>
  );
}

export { BarChart, type BarDatum };
