"use client";

import Link from "next/link";
import * as React from "react";
import { ArrowLeft, CircleCheck, CircleX, RotateCcw, Send, Timer } from "lucide-react";
import type { AttemptView, EnrollmentDetail, SubmitResult } from "@lms/api-client";
import {
  Badge,
  Button,
  ErrorState,
  LoadingRegion,
  Notice,
  PageHeader,
  Panel,
  PanelBody,
  PanelFooter,
  PanelHeader,
  CompletionMark,
  Skeleton,
  cn,
} from "@lms/ui";

import { api, describeError } from "@/lib/client-api";
import { QUIZ_STEP, clock, courseSteps } from "@/lib/learning";

import { CourseStepsAside, CourseStepsFold } from "../course-steps";

type Problem = { title: string; detail?: string; traceId?: string };

/**
 * Starting is idempotent on the server (an open attempt is resumed), so a reload or a second tab
 * continues the same attempt. The answer key never reaches the browser; grading is server-side.
 * The quiz is the course's last step, so the step list sits beside it as on a lesson.
 * @param course the outline; null when it could not be loaded (the quiz still works)
 */
export function QuizRunner({ enrollmentId, course }: { enrollmentId: string; course: EnrollmentDetail | null }) {
  const [attempt, setAttempt] = React.useState<AttemptView | null>(null);
  const [answers, setAnswers] = React.useState<Record<string, string[]>>({});
  const [result, setResult] = React.useState<SubmitResult | null>(null);
  const [problem, setProblem] = React.useState<Problem | null>(null);
  const [submitting, setSubmitting] = React.useState(false);
  const [missing, setMissing] = React.useState<string[]>([]);
  const started = React.useRef(false);
  const base = `/app/learn/${enrollmentId}`;

  const start = React.useCallback(async () => {
    setProblem(null);
    setResult(null);
    setAnswers({});
    setMissing([]);
    try {
      const { data } = await api<AttemptView>("POST", `/v1/my/enrollments/${enrollmentId}/quiz/attempts`);
      setAttempt(data);
    } catch (error) {
      setProblem(describeError(error));
    }
  }, [enrollmentId]);

  React.useEffect(() => {
    if (started.current) return;
    started.current = true;
    void start();
  }, [start]);

  const remaining = useCountdown(attempt?.deadlineAt ?? null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!attempt) return;
    const unanswered = attempt.questions.filter((q) => !answers[q.id]?.length).map((q) => q.id);
    setMissing(unanswered);
    if (unanswered.length > 0 && remaining !== 0) {
      document.getElementById(`q-${unanswered[0]}`)?.focus();
      return;
    }
    setSubmitting(true);
    try {
      const { data } = await api<SubmitResult>(
        "POST",
        `/v1/my/enrollments/${enrollmentId}/quiz/attempts/${attempt.id}/submit`,
        { body: { answers } },
      );
      setResult(data);
      window.scrollTo({ top: 0, behavior: "smooth" });
    } catch (error) {
      setProblem(describeError(error));
    } finally {
      setSubmitting(false);
    }
  }

  // Passing shows at once in the outline (the server's copy is a request old).
  const view =
    course && result?.passed && course.quiz ? { ...course, quiz: { ...course.quiz, passed: true } } : course;
  const outline = view ? courseSteps(view) : null;

  const header = (
    <>
      <Link href={base} className="mb-2 inline-flex items-center gap-1 text-label text-muted hover:text-ink">
        <ArrowLeft className="size-3.5" aria-hidden="true" />
        {course?.title ?? "코스로"}
      </Link>
      <PageHeader
        title="최종 퀴즈"
        className="mb-5"
        actions={
          attempt && !result && remaining !== null ? (
            <Badge tone={remaining < 60 ? "danger" : "neutral"} aria-live="off">
              <Timer className="size-3 stroke-[2]" aria-hidden="true" />
              <span className="tabular">{clock(remaining)}</span>
            </Badge>
          ) : undefined
        }
      />
    </>
  );

  if (problem && !attempt) {
    return (
      <div className="mx-auto max-w-[1120px]">
        {header}
        <Panel>
          <ErrorState title={problem.title} description={problem.detail ?? "코스 화면에서 응시 조건을 확인하세요."} traceId={problem.traceId} />
          <div className="flex justify-center pb-8">
            <Button asChild variant="secondary">
              <Link href={base}>코스로 돌아가기</Link>
            </Button>
          </div>
        </Panel>
      </div>
    );
  }

  if (!attempt) {
    return (
      <LoadingRegion label="퀴즈 불러오는 중" className="mx-auto grid max-w-[1120px] gap-4">
        <Skeleton className="h-8 w-40" />
        <Skeleton className="h-40 w-full" />
        <Skeleton className="h-40 w-full" />
      </LoadingRegion>
    );
  }

  const outcome = result ? new Map(result.questions.map((q) => [q.questionId, q.correct])) : null;

  return (
    <div className="mx-auto max-w-[1120px]">
      {header}
      {outline && course ? <CourseStepsFold course={course} {...outline} currentId={QUIZ_STEP} /> : null}
      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_300px]">
        <div className="grid min-w-0 gap-6">
          {result ? (
            <Panel>
              <div className="flex flex-col items-center gap-4 px-6 py-8 text-center" role="status">
                {result.courseCompleted ? <CompletionMark size="lg" /> : null}
                <div className="grid gap-1">
                  <p className="text-title font-semibold text-ink">
                    {result.passed ? "통과했습니다" : result.expired ? "제한 시간이 지나 0점 처리되었습니다" : "통과하지 못했습니다"}
                  </p>
                  <p className="text-body text-ink-2">
                    <span className="tabular">{result.score}점</span> · 통과 기준 {result.passScore}점
                    {result.attemptsLeft !== null && result.attemptsLeft !== undefined && !result.passed
                      ? ` · 남은 응시 ${result.attemptsLeft}회`
                      : ""}
                  </p>
                  {result.certificateSerial ? (
                    <p className="text-body-sm text-muted tabular">수료번호 {result.certificateSerial}</p>
                  ) : null}
                </div>
                <div className="flex flex-wrap justify-center gap-2">
                  {!result.passed && (result.attemptsLeft === null || result.attemptsLeft === undefined || result.attemptsLeft > 0) ? (
                    <Button onClick={start}>
                      <RotateCcw aria-hidden="true" />
                      다시 응시
                    </Button>
                  ) : null}
                  <Button asChild variant={result.passed ? "primary" : "secondary"}>
                    <Link href={result.passed ? "/app" : base}>{result.passed ? "내 교육으로" : "레슨 복습하기"}</Link>
                  </Button>
                </div>
              </div>
            </Panel>
          ) : (
            <Notice tone="info" title={`${attempt.attemptNumber}번째 응시${attempt.maxAttempts ? ` (최대 ${attempt.maxAttempts}회)` : ""}`}>
              {attempt.passScore}점 이상이면 통과합니다. 복수 선택 문항은 정답을 모두 골라야 점수를 받습니다.
              {attempt.deadlineAt ? " 제한 시간이 지나면 제출해도 0점입니다." : ""}
            </Notice>
          )}

          {problem ? <Notice tone="danger" title={problem.title}>{problem.detail}</Notice> : null}

          <form noValidate onSubmit={submit} className="grid gap-4">
            {attempt.questions.map((q, index) => {
              const chosen = answers[q.id] ?? [];
              const multiple = q.type === "MULTIPLE";
              const graded = outcome?.get(q.id);
              const isMissing = missing.includes(q.id);
              return (
                <Panel key={q.id}>
                  <fieldset
                    id={`q-${q.id}`}
                    tabIndex={-1}
                    aria-describedby={isMissing ? `q-${q.id}-missing` : undefined}
                    disabled={!!result}
                    className="grid gap-3 p-5 outline-none"
                  >
                    <legend className="contents">
                      <span className="flex items-start justify-between gap-3">
                        <span className="text-body font-medium text-ink">
                          {index + 1}. {q.prompt}
                        </span>
                        <span className="shrink-0 text-label text-muted">
                          {multiple ? "복수 선택 · " : ""}
                          {q.points}점
                        </span>
                      </span>
                    </legend>
                    <div className="grid gap-2">
                      {q.options.map((o) => {
                        const checked = chosen.includes(o.id);
                        return (
                          <label
                            key={o.id}
                            className={cn(
                              "flex cursor-pointer items-center gap-3 rounded-control border border-line px-3 py-2.5 text-body text-ink",
                              checked && "border-accent bg-accent/5",
                              result && "cursor-default",
                            )}
                          >
                            <input
                              type={multiple ? "checkbox" : "radio"}
                              name={q.id}
                              value={o.id}
                              checked={checked}
                              onChange={(e) =>
                                setAnswers((prev) => {
                                  const current = prev[q.id] ?? [];
                                  const next = multiple
                                    ? e.target.checked
                                      ? [...current, o.id]
                                      : current.filter((x) => x !== o.id)
                                    : [o.id];
                                  return { ...prev, [q.id]: next };
                                })
                              }
                              className="size-4 accent-[var(--accent)]"
                            />
                            {o.text}
                          </label>
                        );
                      })}
                    </div>
                    {isMissing && !result ? (
                      <p id={`q-${q.id}-missing`} className="text-label text-danger">
                        답을 고르세요.
                      </p>
                    ) : null}
                    {graded !== undefined ? (
                      <p className={cn("inline-flex items-center gap-1.5 text-label font-medium", graded ? "text-success" : "text-danger")}>
                        {graded ? <CircleCheck className="size-4" aria-hidden="true" /> : <CircleX className="size-4" aria-hidden="true" />}
                        {graded ? "정답" : "오답"}
                      </p>
                    ) : null}
                  </fieldset>
                </Panel>
              );
            })}

            {!result ? (
              <Panel>
                <PanelHeader title="제출" description="제출하면 바로 채점됩니다. 제출한 답은 바꿀 수 없습니다." />
                <PanelFooter className="justify-end">
                  <Button type="submit" loading={submitting}>
                    <Send aria-hidden="true" />
                    제출하고 채점하기
                  </Button>
                </PanelFooter>
              </Panel>
            ) : (
              <PanelBody className="px-0 text-label text-muted">
                정답 보기는 제공하지 않습니다. 틀린 문항은 레슨을 다시 보고 재응시하세요.
              </PanelBody>
            )}
          </form>
        </div>
        {outline && course ? <CourseStepsAside course={course} {...outline} currentId={QUIZ_STEP} /> : null}
      </div>
    </div>
  );
}

/** Seconds left until the deadline (null when there is no time limit). */
function useCountdown(deadlineIso: string | null): number | null {
  const [now, setNow] = React.useState(() => Date.now());
  React.useEffect(() => {
    if (!deadlineIso) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [deadlineIso]);
  if (!deadlineIso) return null;
  return Math.max(0, Math.round((new Date(deadlineIso).getTime() - now) / 1000));
}
