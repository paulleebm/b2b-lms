import * as React from "react";
import { CloudOff, type LucideIcon } from "lucide-react";

import { cn } from "../lib/utils";
import { Button } from "./button";

/**
 * The non-populated states every data surface must render (craft/state-coverage, DESIGN.md §9).
 */

/** Layout-shaped placeholder. Use for waits over 300ms; never a spinner in the middle of content. */
function Skeleton({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="skeleton"
      aria-hidden="true"
      className={cn(
        "rounded-badge bg-[linear-gradient(90deg,var(--surface-sunken)_25%,#e7e8e3_37%,var(--surface-sunken)_63%)] bg-[length:400%_100%] animate-shimmer",
        className,
      )}
      {...props}
    />
  );
}

/** Wraps skeletons so assistive tech hears one "loading" message instead of nothing. */
function LoadingRegion({
  label,
  slow = false,
  children,
  className,
}: {
  label: string;
  /** Show after ~15s: "평소보다 오래 걸리고 있어요". */
  slow?: boolean;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div data-slot="loading-region" role="status" aria-live="polite" aria-busy="true" className={className}>
      <span className="sr-only">{label}</span>
      {children}
      {slow ? <p className="mt-3 text-label text-muted">평소보다 오래 걸리고 있어요. 잠시만 기다려 주세요.</p> : null}
    </div>
  );
}

/** Headline + why it is empty + the next action. Never just "데이터가 없습니다". */
function EmptyState({
  icon: Icon,
  title,
  description,
  action,
  className,
}: {
  icon?: LucideIcon;
  title: string;
  description: React.ReactNode;
  action?: React.ReactNode;
  className?: string;
}) {
  return (
    <div
      data-slot="empty-state"
      className={cn("mx-auto flex max-w-md flex-col items-center gap-2 px-6 py-10 text-center", className)}
    >
      {Icon ? <Icon className="mb-1 size-6 stroke-[1.5] text-muted" aria-hidden="true" /> : null}
      <p className="text-title-sm font-semibold text-ink">{title}</p>
      <p className="text-body-sm text-muted">{description}</p>
      {action ? <div className="mt-3">{action}</div> : null}
    </div>
  );
}

/** What happened → why (if known) → what to do. Shows the trace id as a support code. */
function ErrorState({
  title,
  description,
  onRetry,
  retrying = false,
  traceId,
  className,
}: {
  title: string;
  description: React.ReactNode;
  onRetry?: () => void;
  retrying?: boolean;
  traceId?: string;
  className?: string;
}) {
  return (
    <div
      data-slot="error-state"
      role="alert"
      className={cn("mx-auto flex max-w-md flex-col items-center gap-2 px-6 py-10 text-center", className)}
    >
      <CloudOff className="mb-1 size-6 stroke-[1.5] text-danger" aria-hidden="true" />
      <p className="text-title-sm font-semibold text-ink">{title}</p>
      <p className="text-body-sm text-muted">{description}</p>
      {onRetry ? (
        <Button variant="secondary" size="sm" className="mt-3" onClick={onRetry} loading={retrying}>
          다시 시도
        </Button>
      ) : null}
      {traceId ? (
        <p className="mt-2 text-caption text-muted">
          문의 코드 <code className="font-mono tabular select-all">{traceId}</code>
        </p>
      ) : null}
    </div>
  );
}

export { EmptyState, ErrorState, LoadingRegion, Skeleton };
