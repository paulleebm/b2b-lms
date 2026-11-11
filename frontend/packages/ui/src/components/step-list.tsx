import * as React from "react";
import { Check, Lock } from "lucide-react";

import { cn } from "../lib/utils";

/**
 * A learner's course as numbered steps: lessons in order, then the final quiz (DESIGN.md §4
 * "학습 단계 목록", decided 2026-11-10: layout B "목차 옆에"). The current step carries the
 * accent; finished steps a green check; the locked quiz a lock. Shown beside the lesson and as
 * the course page's main list, where the current row also holds the next action.
 */

type StepKind = "VIDEO" | "DOCUMENT" | "TEXT" | "QUIZ";
type StepState = "done" | "todo" | "locked";

type Step = {
  id: string;
  title: string;
  kind: StepKind;
  state: StepState;
  /** Where the step opens. Omitted for a locked step and in stories. */
  href?: string;
  /** Watch progress of an unfinished video, 1–99. */
  percent?: number;
};

type StepListProps = {
  steps: Step[];
  /** The step the learner is on: highlighted, and not a link (it is this page, or `action` opens it). */
  currentId?: string;
  /** Course page: the current row grows a second line and this action (e.g. "이어서 학습하기"). */
  action?: React.ReactNode;
  className?: string;
};

const kindLabel: Record<StepKind, string> = { VIDEO: "영상", DOCUMENT: "문서", TEXT: "텍스트", QUIZ: "퀴즈" };
const stateText: Record<StepState, string> = { done: "완료", todo: "", locked: "잠김" };

function StepMarker({ index, state, current }: { index: number; state: StepState; current: boolean }) {
  const base = "grid size-6 shrink-0 place-items-center rounded-full text-caption font-semibold tabular";
  if (state === "done") {
    return (
      <span className={cn(base, "bg-success text-primary-fg")} aria-hidden="true">
        <Check className="size-3.5 stroke-[2.5]" />
      </span>
    );
  }
  if (state === "locked") {
    return (
      <span className={cn(base, "bg-surface-sunken text-muted")} aria-hidden="true">
        <Lock className="size-3 stroke-[2.25]" />
      </span>
    );
  }
  return (
    <span
      className={cn(base, current ? "bg-surface text-accent ring-2 ring-accent ring-inset" : "bg-surface text-muted ring-1 ring-line ring-inset")}
      aria-hidden="true"
    >
      {index + 1}
    </span>
  );
}

function StepList({ steps, currentId, action, className }: StepListProps) {
  return (
    <ol data-slot="step-list" className={cn("divide-y divide-line", className)}>
      {steps.map((step, index) => {
        const current = step.id === currentId;
        const kind = kindLabel[step.kind];
        const detail = step.percent && step.state !== "done" ? `${kind} · ${step.percent}%` : kind;
        const label = (
          <>
            <StepMarker index={index} state={step.state} current={current} />
            <span className="sr-only">
              {index + 1}단계{current ? ", 현재 단계" : ""}
              {stateText[step.state] ? `, ${stateText[step.state]}` : ""}:{" "}
            </span>
            <span className={cn("min-w-0 flex-1 truncate", current ? "font-semibold text-ink" : "text-ink-2")} title={step.title}>
              {step.title}
            </span>
          </>
        );

        if (current && action) {
          return (
            <li
              key={step.id}
              aria-current="step"
              className="flex flex-wrap items-center gap-x-3 gap-y-3 border-l-3 border-accent bg-accent-soft py-4 pr-5 pl-[17px]"
            >
              <div className="flex min-w-0 flex-1 items-center gap-3">
                <StepMarker index={index} state={step.state} current />
                <div className="grid min-w-0 gap-0.5">
                  <span className="sr-only">{index + 1}단계, 현재 단계: </span>
                  <span className="truncate text-body font-semibold text-ink" title={step.title}>
                    {step.title}
                  </span>
                  <span className="text-label text-muted">
                    {index + 1}단계 · {detail}
                  </span>
                </div>
              </div>
              <div className="w-full sm:w-auto [&>*]:w-full sm:[&>*]:w-auto">{action}</div>
            </li>
          );
        }

        const row = "flex min-h-11 items-center gap-3 border-l-3 py-2.5 pr-5 pl-[17px] text-body-sm";
        const tail = <span className="shrink-0 text-label text-muted tabular">{detail}</span>;
        return (
          <li key={step.id} aria-current={current ? "step" : undefined}>
            {step.href && !current ? (
              <a
                href={step.href}
                className={cn(
                  row,
                  "border-transparent transition-colors duration-150 hover:bg-surface-sunken focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent",
                )}
              >
                {label}
                {tail}
              </a>
            ) : (
              <div className={cn(row, current ? "border-accent bg-accent-soft" : "border-transparent")}>
                {label}
                {tail}
              </div>
            )}
          </li>
        );
      })}
    </ol>
  );
}

export { StepList, type Step, type StepKind, type StepListProps, type StepState };
