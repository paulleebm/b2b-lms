import * as React from "react";

import { cn } from "../lib/utils";

/**
 * Product logo (DESIGN.md §4): an "L" whose foot turns into a check — the product name and
 * "completed" in one stroke — in a rounded square, next to the product name.
 * `paper` on light pages (landing), `shell` on the dark navigation rail.
 * The same drawing is the favicon (apps/web/src/app/icon.svg); keep the two in sync.
 */
const MARK_PATH = "M11 8v14.5h4.5L24 12";

function BrandMark({
  name = "LMS",
  tone = "paper",
  className,
}: {
  name?: string;
  tone?: "paper" | "shell";
  className?: string;
}) {
  const paper = tone === "paper";
  return (
    <span data-slot="brand-mark" className={cn("inline-flex items-center gap-2", className)}>
      <svg viewBox="0 0 32 32" className="size-7 shrink-0" aria-hidden="true" focusable="false">
        <rect width="32" height="32" rx="8" className={paper ? "fill-primary" : "fill-shell-fg"} />
        <path
          d={MARK_PATH}
          fill="none"
          strokeWidth={3}
          strokeLinecap="round"
          strokeLinejoin="round"
          className={paper ? "stroke-primary-fg" : "stroke-shell"}
        />
      </svg>
      <span className={cn("text-body font-bold", paper ? "text-ink" : "text-shell-fg")}>{name}</span>
    </span>
  );
}

export { BrandMark };
