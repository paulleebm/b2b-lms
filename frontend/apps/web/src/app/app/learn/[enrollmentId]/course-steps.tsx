import { ChevronDown } from "lucide-react";
import type { EnrollmentDetail } from "@lms/api-client";
import { Progress, StepList, type Step } from "@lms/ui";

import { seoulDate } from "@/lib/learning";

/**
 * The course's steps beside a lesson or the quiz (DESIGN.md §4 "학습 단계 목록", layout B):
 * a 300px column on wide screens, one folded line ("2/4 · 제목") above the content below 1024px.
 * Pages lay them out as `fold` above a `lg:grid-cols-[minmax(0,1fr)_300px]` grid holding the
 * content and `aside`.
 */
type Props = { course: EnrollmentDetail; steps: Step[]; done: number; currentId: string };

export function CourseStepsFold({ steps, currentId }: Props) {
  const position = steps.findIndex((s) => s.id === currentId) + 1;
  if (position === 0) return null;
  return (
    <details className="group mb-5 overflow-hidden rounded-panel border border-line bg-surface lg:hidden">
      <summary className="flex min-h-12 cursor-pointer list-none items-center gap-3 px-4 text-body-sm font-semibold text-ink [&::-webkit-details-marker]:hidden">
        <span className="tabular text-muted">
          {position}/{steps.length}
        </span>
        <span className="min-w-0 flex-1 truncate">{steps[position - 1]!.title}</span>
        <span className="sr-only">단계 목록 열기</span>
        <ChevronDown className="size-4 shrink-0 text-muted transition-transform duration-200 group-open:rotate-180" aria-hidden="true" />
      </summary>
      <StepList steps={steps} currentId={currentId} className="border-t border-line" />
    </details>
  );
}

export function CourseStepsAside({ course, steps, done, currentId }: Props) {
  return (
    <aside aria-label="코스 단계" className="hidden overflow-hidden rounded-panel border border-line bg-surface lg:block">
      <div className="grid gap-1 border-b border-line px-5 py-4">
        <p className="font-semibold text-ink">{course.title}</p>
        {course.dueAt ? <p className="text-label text-muted">마감 {seoulDate(course.dueAt).replaceAll("-", ". ")}.</p> : null}
        <Progress value={done} max={steps.length} label={`${course.title} 진도`} valueText={`${done}/${steps.length} 단계`} className="mt-2" />
      </div>
      <StepList steps={steps} currentId={currentId} />
    </aside>
  );
}
