"use client";

import * as React from "react";
import { Award, Download, PlayCircle } from "lucide-react";

import { AppShell, PageHeader } from "../components/app-shell";
import { Button } from "../components/button";
import { Panel, PanelBody, PanelHeader } from "../components/panel";
import { Progress } from "../components/progress";
import { SealMark } from "../components/seal-mark";
import { Badge, StatusBadge } from "../components/status-badge";
import { EmptyState, ErrorState, LoadingRegion, Skeleton } from "../components/states";
import { completedCourses, formatDue, learnerCourses, type LearnerCourse } from "./fixtures";
import { AccountArea, WorkspaceSwitcher, learnerNav } from "./shell-presets";

/**
 * Learner "내 교육" — product principle 2: one next task at the top (Hick's law),
 * then the rest. Completed courses carry the seal; a course completed just now plays
 * the stamp once (the only authored motion in the product).
 */
type ScreenState = "populated" | "loading" | "empty" | "error" | "just-completed";

function NextUp({ course }: { course: LearnerCourse }) {
  return (
    <Panel aria-labelledby="next-title" className="overflow-hidden">
      <div className="grid gap-5 p-6 md:grid-cols-[1fr_auto] md:items-end">
        <div className="grid gap-3">
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge status={course.status} />
            <span className="text-label text-muted">마감 {formatDue(course.dueAt)}</span>
          </div>
          <div className="grid gap-1">
            <h2 id="next-title" className="text-title font-semibold text-ink">
              {course.title}
            </h2>
            <p className="text-body text-ink-2">다음 레슨: {course.nextLesson}</p>
          </div>
          <Progress
            value={course.lessonsDone}
            max={course.lessonsTotal}
            label={`${course.title} 진도`}
            valueText={`${course.lessonsDone}/${course.lessonsTotal} 레슨`}
            className="max-w-md"
          />
        </div>
        <Button size="lg" className="w-full md:w-auto">
          <PlayCircle aria-hidden="true" />
          이어서 학습하기
        </Button>
      </div>
    </Panel>
  );
}

function CourseRow({ course }: { course: LearnerCourse }) {
  return (
    <li className="grid grid-cols-[1fr_auto] items-center gap-x-4 gap-y-2 px-5 py-4 md:grid-cols-[minmax(0,1fr)_14rem_auto]">
      <div className="grid min-w-0 gap-1">
        <p className="truncate text-body font-medium text-ink">{course.title}</p>
        <p className="text-label text-muted">마감 {formatDue(course.dueAt)}</p>
      </div>
      <Progress
        value={course.lessonsDone}
        max={course.lessonsTotal}
        label={`${course.title} 진도`}
        valueText={`${course.lessonsDone}/${course.lessonsTotal}`}
        tone="neutral"
        className="col-span-2 row-start-2 md:col-span-1 md:row-start-auto"
      />
      <StatusBadge status={course.status} className="col-start-2 row-start-1 md:col-start-auto md:row-start-auto" />
    </li>
  );
}

function CompletedRow({ course, stamp = false }: { course: LearnerCourse; stamp?: boolean }) {
  return (
    <li className="flex items-center gap-4 px-5 py-4">
      <SealMark size="md" stamp={stamp} completedAt={course.completedAt} />
      <div className="grid min-w-0 flex-1 gap-0.5">
        <p className="truncate text-body font-medium text-ink">{course.title}</p>
        <p className="text-label text-muted">{course.completedAt} 수료</p>
      </div>
      <Button variant="secondary" size="sm" aria-label={`${course.title} 수료증 내려받기`}>
        <Download aria-hidden="true" />
        수료증
      </Button>
    </li>
  );
}

function JustCompleted({ course }: { course: LearnerCourse }) {
  return (
    <Panel aria-labelledby="done-title">
      <div className="flex flex-col items-center gap-4 px-6 py-10 text-center" role="status">
        <SealMark size="lg" stamp completedAt={course.completedAt} />
        <div className="grid gap-1">
          <h2 id="done-title" className="text-title font-semibold text-ink">
            수료했습니다
          </h2>
          <p className="text-body font-medium text-ink-2">{course.title}</p>
          <p className="text-body-sm text-muted">
            수료증이 발급되었습니다. 회사의 교육 기록에도 {course.completedAt}자로 남았습니다.
          </p>
        </div>
        <div className="flex flex-wrap justify-center gap-2">
          <Button>
            <Award aria-hidden="true" />
            수료증 보기
          </Button>
          <Button variant="secondary">다음 교육으로</Button>
        </div>
      </div>
    </Panel>
  );
}

function LearnerHomeScreen({
  state = "populated",
  courses = learnerCourses,
  completed = completedCourses,
}: {
  state?: ScreenState;
  courses?: LearnerCourse[];
  completed?: LearnerCourse[];
}) {
  const [next, ...rest] = courses;

  let content: React.ReactNode;
  if (state === "loading") {
    content = (
      <LoadingRegion label="내 교육 불러오는 중" className="grid gap-6">
        <Panel className="grid gap-4 p-6">
          <Skeleton className="h-5 w-32" />
          <Skeleton className="h-6 w-2/3" />
          <Skeleton className="h-2 w-full max-w-md" />
        </Panel>
        <Panel className="grid gap-5 p-5">
          <Skeleton className="h-4 w-1/2" />
          <Skeleton className="h-4 w-2/5" />
        </Panel>
      </LoadingRegion>
    );
  } else if (state === "error") {
    content = (
      <Panel>
        <ErrorState
          title="내 교육 목록을 불러오지 못했습니다"
          description="네트워크 연결을 확인한 뒤 다시 시도해 주세요. 학습 기록은 안전하게 저장되어 있습니다."
          onRetry={() => undefined}
        />
      </Panel>
    );
  } else if (state === "empty" || !next) {
    content = (
      <Panel>
        <EmptyState
          icon={Award}
          title="배정된 교육이 없습니다"
          description="회사에서 필수 교육을 배정하면 여기에 표시되고, 메일로도 알려드립니다."
        />
      </Panel>
    );
  } else {
    content = (
      <div className="grid gap-8">
        {state === "just-completed" && completed[0] ? <JustCompleted course={completed[0]} /> : <NextUp course={next} />}

        {rest.length > 0 ? (
          <Panel>
            <PanelHeader title="진행 중인 교육" description={`${rest.length}개`} />
            <ul className="divide-y divide-line border-t border-line">
              {rest.map((c) => (
                <CourseRow key={c.id} course={c} />
              ))}
            </ul>
          </Panel>
        ) : null}

        {completed.length > 0 ? (
          <Panel>
            <PanelHeader title="수료한 교육" description="수료증은 언제든 다시 내려받을 수 있습니다" />
            <PanelBody className="px-0 pb-0">
              <ul className="divide-y divide-line border-t border-line">
                {completed.map((c) => (
                  <CompletedRow key={c.id} course={c} />
                ))}
              </ul>
            </PanelBody>
          </Panel>
        ) : null}
      </div>
    );
  }

  return (
    <AppShell
      workspace={<WorkspaceSwitcher name="에이크미" />}
      account={<AccountArea name="이세나" role="학습자" />}
      sections={learnerNav}
    >
      <div className="mx-auto max-w-[880px]">
        <PageHeader
          title="내 교육"
          description="회사가 배정한 필수 교육입니다. 마감이 가까운 것부터 보여줍니다."
          actions={<Badge tone="neutral">합성 데이터</Badge>}
        />
        {content}
      </div>
    </AppShell>
  );
}

export { LearnerHomeScreen };
