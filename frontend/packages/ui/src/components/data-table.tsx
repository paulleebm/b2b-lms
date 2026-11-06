import * as React from "react";
import { ArrowDown, ArrowUp, ArrowUpDown } from "lucide-react";

import { cn } from "../lib/utils";
import { EmptyState, ErrorState, LoadingRegion, Skeleton } from "./states";

/**
 * DESIGN.md §4 Tables — the protagonist of admin screens.
 * Five states built in (craft/state-coverage): loading skeleton rows, empty, error, data, edge
 * (long text truncates with a title; numbers stay right-aligned and tabular).
 * Sorting is controlled by the caller so it can be done on the server for large sets.
 */

type Align = "left" | "right" | "center";

type Column<T> = {
  key: string;
  header: string;
  /** Cell content. Return plain text where possible. */
  cell: (row: T) => React.ReactNode;
  align?: Align;
  sortable?: boolean;
  /** Tailwind width class, e.g. "w-40". */
  width?: string;
  /**
   * Hide when the TABLE (not the viewport) is narrower than 48rem — a container query, because
   * the same table sits full-width on one screen and in a half column on another (DESIGN.md §8).
   */
  secondary?: boolean;
};

type SortState = { key: string; direction: "asc" | "desc" } | null;

type DataTableProps<T> = {
  /** Accessible table name; rendered as a visually hidden caption. */
  caption: string;
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  sort?: SortState;
  onSortChange?: (sort: SortState) => void;
  loading?: boolean;
  error?: { title: string; description: string; traceId?: string; onRetry?: () => void };
  empty?: { title: string; description: string; action?: React.ReactNode };
  /** Highlight a row (e.g. the selected one). */
  isRowSelected?: (row: T) => boolean;
  /**
   * Below this width the table scrolls sideways with the first column pinned, instead of
   * squeezing columns to nothing (DESIGN.md §8).
   */
  minWidth?: string;
  className?: string;
};

const alignClass: Record<Align, string> = {
  left: "text-left",
  right: "text-right tabular",
  center: "text-center",
};

function nextSort(current: SortState, key: string): SortState {
  if (!current || current.key !== key) return { key, direction: "asc" };
  if (current.direction === "asc") return { key, direction: "desc" };
  return null;
}

function DataTable<T>({
  caption,
  columns,
  rows,
  rowKey,
  sort = null,
  onSortChange,
  loading = false,
  error,
  empty,
  isRowSelected,
  minWidth = "min-w-[34rem]",
  className,
}: DataTableProps<T>) {
  const colSpan = columns.length;

  const head = (
    <thead className="bg-surface-sunken">
      <tr>
        {columns.map((col, colIndex) => {
          const active = sort?.key === col.key;
          const ariaSort = active ? (sort!.direction === "asc" ? "ascending" : "descending") : col.sortable ? "none" : undefined;
          const SortIcon = !active ? ArrowUpDown : sort!.direction === "asc" ? ArrowUp : ArrowDown;
          return (
            <th
              key={col.key}
              scope="col"
              aria-sort={ariaSort}
              className={cn(
                "h-10 border-b border-line px-3 text-label font-medium whitespace-nowrap text-ink-2",
                colIndex === 0 && "sticky left-0 z-10 bg-surface-sunken shadow-[inset_-1px_0_0_var(--line)]",
                alignClass[col.align ?? "left"],
                col.width,
                col.secondary && "hidden @3xl:table-cell",
              )}
            >
              {col.sortable && onSortChange ? (
                <button
                  type="button"
                  onClick={() => onSortChange(nextSort(sort, col.key))}
                  className={cn(
                    "-mx-1 inline-flex items-center gap-1 rounded-badge px-1 hover:text-ink",
                    col.align === "right" && "flex-row-reverse",
                  )}
                >
                  {col.header}
                  <SortIcon
                    className={cn("size-3.5 stroke-[1.75]", active ? "text-ink" : "text-muted")}
                    aria-hidden="true"
                  />
                </button>
              ) : (
                col.header
              )}
            </th>
          );
        })}
      </tr>
    </thead>
  );

  let body: React.ReactNode;
  if (loading) {
    body = (
      <tbody>
        {Array.from({ length: 5 }, (_, i) => (
          <tr key={i} className="border-b border-line last:border-b-0">
            {columns.map((col) => (
              <td key={col.key} className={cn("h-11 px-3", col.secondary && "hidden @3xl:table-cell")}>
                <Skeleton className={cn("h-3.5", col.align === "right" ? "ml-auto w-10" : "w-3/4")} />
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    );
  } else if (error) {
    body = (
      <tbody>
        <tr>
          <td colSpan={colSpan}>
            <ErrorState
              title={error.title}
              description={error.description}
              traceId={error.traceId}
              onRetry={error.onRetry}
            />
          </td>
        </tr>
      </tbody>
    );
  } else if (rows.length === 0) {
    body = (
      <tbody>
        <tr>
          <td colSpan={colSpan}>
            <EmptyState
              title={empty?.title ?? "표시할 항목이 없습니다"}
              description={empty?.description ?? "조건을 바꾸거나 새 항목을 추가해 보세요."}
              action={empty?.action}
            />
          </td>
        </tr>
      </tbody>
    );
  } else {
    body = (
      <tbody>
        {rows.map((row) => {
          const selected = isRowSelected?.(row) ?? false;
          return (
            <tr
              key={rowKey(row)}
              aria-selected={isRowSelected ? selected : undefined}
              className={cn(
                "border-b border-line transition-colors duration-150 last:border-b-0",
                selected ? "bg-accent-soft" : "bg-surface hover:bg-surface-sunken",
              )}
            >
              {columns.map((col, colIndex) => (
                <td
                  key={col.key}
                  className={cn(
                    "h-11 max-w-0 truncate px-3 text-body-sm text-ink-2 [&:has([data-slot=badge])]:overflow-visible",
                    colIndex === 0 && "sticky left-0 z-10 bg-inherit shadow-[inset_-1px_0_0_var(--line)]",
                    alignClass[col.align ?? "left"],
                    col.width,
                    col.secondary && "hidden @3xl:table-cell",
                  )}
                >
                  {col.cell(row)}
                </td>
              ))}
            </tr>
          );
        })}
      </tbody>
    );
  }

  const table = (
    <table className={cn("w-full table-fixed border-collapse", minWidth)}>
      <caption className="sr-only">{caption}</caption>
      {head}
      {body}
    </table>
  );

  return (
    <div
      data-slot="data-table"
      className={cn("@container overflow-x-auto rounded-panel border border-line bg-surface", className)}
    >
      {loading ? <LoadingRegion label={`${caption} 불러오는 중`}>{table}</LoadingRegion> : table}
    </div>
  );
}

export { DataTable, type Column, type DataTableProps, type SortState };
