import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ArrowLeft, ClipboardCheck, Download, PlayCircle } from "lucide-react";
import type { EnrollmentDetail } from "@lms/api-client";
import {
  Badge,
  Button,
  CompletionMark,
  ErrorState,
  Notice,
  PageHeader,
  Panel,
  Progress,
  StepList,
} from "@lms/ui";

import { QUIZ_STEP, courseSteps, koreanDate, seoulDate } from "@/lib/learning";
import { formatDateTime } from "@/lib/labels";
import { ApiError, apiFetch } from "@/server/api";

export const metadata: Metadata = { title: "코스" };

/**
 * One course for a learner (DESIGN.md §4 "학습 단계 목록", layout B): the steps in order with
 * the current one highlighted and its action on the same row, so "which course, which step"
 * is answered by one list. The final quiz is the last step.
 */
export default async function CoursePage({
  params,
  searchParams,
}: {
  params: Promise<{ enrollmentId: string }>;
  searchParams: Promise<{ certificate?: string }>;
}) {
  const { enrollmentId } = await params;
  const { certificate } = await searchParams;
  if (!/^[0-9a-f-]{36}$/i.test(enrollmentId)) notFound();

  let course: EnrollmentDetail | null = null;
  let failure: ApiError | null = null;
  try {
    course = await apiFetch<EnrollmentDetail>(`/v1/my/enrollments/${enrollmentId}`);
  } catch (error) {
    if (!(error instanceof ApiError)) throw error;
    failure = error;
  }
  if (failure?.status === 404) notFound();
  if (!course) {
    return (
      <>
        <PageHeader title="코스" />
        <Panel>
          <ErrorState title="코스를 불러오지 못했습니다" description="잠시 후 다시 시도해 주세요." traceId={failure?.traceId} />
        </Panel>
      </>
    );
  }

  const base = `/app/learn/${course.id}`;
  const completed = course.status === "COMPLETED";
  const quiz = course.quiz;
  const { steps, done } = courseSteps(course);
  const quizIsNext = !course.nextLesson && !!quiz && quiz.unlocked && !quiz.passed;
  const currentId = completed ? undefined : (course.nextLesson?.id ?? (quizIsNext ? QUIZ_STEP : undefined));

  const action = completed ? undefined : course.nextLesson ? (
    <Button asChild size="lg">
      <Link href={`${base}/lessons/${course.nextLesson.id}`}>
        <PlayCircle aria-hidden="true" />
        {course.lessonsDone === 0 ? "학습 시작하기" : "이어서 학습하기"}
      </Link>
    </Button>
  ) : quizIsNext ? (
    <Button asChild size="lg">
      <Link href={`${base}/quiz`}>
        <ClipboardCheck aria-hidden="true" />
        {quiz.openAttemptId ? "이어서 응시" : "퀴즈 보기"}
      </Link>
    </Button>
  ) : undefined;

  return (
    <div className="mx-auto max-w-[880px]">
      <Link href="/app" className="mb-2 inline-flex items-center gap-1 text-label text-muted hover:text-ink">
        <ArrowLeft className="size-3.5" aria-hidden="true" />내 교육
      </Link>
      <PageHeader
        title={course.title}
        className="mb-5"
        description={
          completed ? undefined : (
            <span className="inline-flex flex-wrap items-center gap-2">
              {course.dueAt ? `마감 ${seoulDate(course.dueAt).replaceAll("-", ". ")}.` : "마감 없음"}
              {course.overdue ? <Badge tone="danger">마감 지남</Badge> : null}
            </span>
          )
        }
      />

      <div className="grid gap-6">
        {completed ? (
          <Panel aria-labelledby="done-title">
            <div className="flex flex-col items-center gap-4 px-6 py-10 text-center">
              <CompletionMark size="lg" completedAt={course.completedAt ? koreanDate(course.completedAt) : undefined} />
              <div className="grid gap-1">
                <h2 id="done-title" className="text-title font-semibold text-ink">
                  수료했습니다
                </h2>
                <p className="text-body-sm text-muted">
                  {course.completedAt ? `${formatDateTime(course.completedAt)} 수료` : null}
                  {course.certificateSerial ? <span className="tabular"> · 수료번호 {course.certificateSerial}</span> : null}
                </p>
              </div>
              {certificate === "pending" ? (
                <Notice tone="info" title="수료증을 만드는 중입니다">잠시 후 다시 눌러 주세요. 보통 몇 초 걸립니다.</Notice>
              ) : null}
              {course.certificateSerial ? (
                <Button asChild variant="secondary">
                  <a href={`/app/certificates/${course.id}`}>
                    <Download aria-hidden="true" />
                    수료증 내려받기 (PDF)
                  </a>
                </Button>
              ) : null}
            </div>
          </Panel>
        ) : null}

        <Panel className="overflow-hidden">
          <div className="border-b border-line px-5 py-4">
            <Progress value={done} max={steps.length} label={`${course.title} 진도`} valueText={`${done}/${steps.length} 단계`} />
          </div>
          <StepList steps={steps} currentId={currentId} action={action} />
        </Panel>
      </div>
    </div>
  );
}
