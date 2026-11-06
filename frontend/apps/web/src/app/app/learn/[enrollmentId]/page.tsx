import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { AlignLeft, ArrowLeft, CircleCheck, ClipboardCheck, Download, FileText, Lock, PlayCircle } from "lucide-react";
import type { EnrollmentDetail, LessonState } from "@lms/api-client";
import {
  Badge,
  Button,
  ErrorState,
  Notice,
  PageHeader,
  Panel,
  PanelBody,
  PanelHeader,
  Progress,
  SealMark,
  StatusBadge,
} from "@lms/ui";

import { enrollmentStatus, koreanDate, seoulDate } from "@/lib/learning";
import { formatDateTime } from "@/lib/labels";
import { ApiError, apiFetch } from "@/server/api";

export const metadata: Metadata = { title: "코스" };

const lessonIcon = { VIDEO: PlayCircle, DOCUMENT: FileText, TEXT: AlignLeft } as const;
const lessonType = { VIDEO: "영상", DOCUMENT: "문서", TEXT: "텍스트" } as const;

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

  return (
    <div className="mx-auto max-w-[880px]">
      <PageHeader
        title={course.title}
        description={
          <Link href="/app" className="inline-flex items-center gap-1 text-label text-muted hover:text-ink">
            <ArrowLeft className="size-3.5" aria-hidden="true" />내 교육
          </Link>
        }
      />

      <div className="grid gap-6">
        {completed ? (
          <Panel aria-labelledby="done-title">
            <div className="flex flex-col items-center gap-4 px-6 py-10 text-center">
              <SealMark size="lg" completedAt={course.completedAt ? koreanDate(course.completedAt) : undefined} />
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
        ) : (
          <Panel>
            <div className="grid gap-5 p-6 md:grid-cols-[1fr_auto] md:items-end">
              <div className="grid gap-3">
                <div className="flex flex-wrap items-center gap-2">
                  <StatusBadge status={enrollmentStatus(course)} />
                  <span className="text-label text-muted">
                    {course.dueAt ? `마감 ${seoulDate(course.dueAt).replaceAll("-", ". ")}.` : "마감 없음"}
                  </span>
                </div>
                {course.description ? <p className="max-w-prose text-body text-ink-2">{course.description}</p> : null}
                <Progress
                  value={course.lessonsDone}
                  max={course.lessonsTotal}
                  label={`${course.title} 진도`}
                  valueText={`${course.lessonsDone}/${course.lessonsTotal} 레슨`}
                  className="max-w-md"
                />
              </div>
              {course.nextLesson ? (
                <Button asChild size="lg" className="w-full md:w-auto">
                  <Link href={`${base}/lessons/${course.nextLesson.id}`}>
                    <PlayCircle aria-hidden="true" />
                    {course.lessonsDone === 0 ? "학습 시작하기" : "이어서 학습하기"}
                  </Link>
                </Button>
              ) : quiz && !quiz.passed ? (
                <Button asChild size="lg" className="w-full md:w-auto">
                  <Link href={`${base}/quiz`}>
                    <ClipboardCheck aria-hidden="true" />
                    퀴즈 보기
                  </Link>
                </Button>
              ) : null}
            </div>
          </Panel>
        )}

        <Panel>
          <PanelHeader title="커리큘럼" description={`레슨 ${course.lessonsTotal}개`} />
          <div className="border-t border-line">
            {course.sections.map((section, si) => (
              <section key={section.id} aria-labelledby={`sec-${section.id}`}>
                <h3 id={`sec-${section.id}`} className="bg-surface-sunken px-5 py-2 text-label font-semibold text-ink-2">
                  {si + 1}. {section.title}
                </h3>
                <ul className="divide-y divide-line">
                  {section.lessons.map((lesson) => (
                    <LessonRow key={lesson.id} lesson={lesson} href={`${base}/lessons/${lesson.id}`} />
                  ))}
                </ul>
              </section>
            ))}
          </div>
        </Panel>

        {quiz ? (
          <Panel>
            <PanelHeader
              title="최종 퀴즈"
              description={`${quiz.questionCount}문항 · ${quiz.passScore}점 이상 통과${
                quiz.timeLimitSec ? ` · 제한 시간 ${Math.round(quiz.timeLimitSec / 60)}분` : ""
              }${quiz.maxAttempts ? ` · 응시 ${quiz.attemptsUsed}/${quiz.maxAttempts}회` : ""}`}
            />
            <PanelBody className="flex flex-wrap items-center justify-between gap-3">
              <p className="text-body-sm text-ink-2">
                {quiz.passed
                  ? `통과했습니다 (최고 ${quiz.bestScore}점).`
                  : quiz.unlocked
                    ? quiz.bestScore !== null && quiz.bestScore !== undefined
                      ? `최고 ${quiz.bestScore}점. 다시 응시할 수 있습니다.`
                      : "모든 레슨을 마쳤습니다. 퀴즈를 보면 수료합니다."
                    : "모든 레슨을 마치면 퀴즈를 볼 수 있습니다."}
              </p>
              {quiz.passed ? (
                <Badge tone="success">
                  <CircleCheck className="size-3 stroke-[2]" aria-hidden="true" />
                  통과
                </Badge>
              ) : quiz.unlocked ? (
                <Button asChild variant={course.nextLesson ? "secondary" : "primary"}>
                  <Link href={`${base}/quiz`}>{quiz.openAttemptId ? "이어서 응시" : "퀴즈 보기"}</Link>
                </Button>
              ) : (
                <Badge>
                  <Lock className="size-3 stroke-[2]" aria-hidden="true" />
                  잠김
                </Badge>
              )}
            </PanelBody>
          </Panel>
        ) : null}
      </div>
    </div>
  );
}

function LessonRow({ lesson, href }: { lesson: LessonState; href: string }) {
  const Icon = lessonIcon[lesson.type as keyof typeof lessonIcon] ?? AlignLeft;
  return (
    <li>
      <Link href={href} className="flex items-center gap-3 px-5 py-3 hover:bg-surface-sunken">
        <Icon className="size-4 shrink-0 text-muted" aria-hidden="true" />
        <span className="min-w-0 flex-1 truncate text-body text-ink">{lesson.title}</span>
        <span className="text-label text-muted">{lessonType[lesson.type as keyof typeof lessonType]}</span>
        {lesson.completed ? (
          <Badge tone="success">
            <CircleCheck className="size-3 stroke-[2]" aria-hidden="true" />
            완료
          </Badge>
        ) : lesson.percent ? (
          <span className="w-12 text-right text-label tabular text-ink-2">{lesson.percent}%</span>
        ) : null}
      </Link>
    </li>
  );
}
