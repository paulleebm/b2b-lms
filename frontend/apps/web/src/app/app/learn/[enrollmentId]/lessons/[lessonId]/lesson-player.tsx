"use client";

import Link from "next/link";
import * as React from "react";
import { ArrowLeft, ArrowRight, CircleCheck, Download, ExternalLink } from "lucide-react";
import type { LessonContent, ProgressResult } from "@lms/api-client";
import {
  Badge,
  Button,
  ErrorState,
  LoadingRegion,
  Notice,
  PageHeader,
  Panel,
  PanelBody,
  Progress,
  SealMark,
  Skeleton,
} from "@lms/ui";

import { api, describeError } from "@/lib/client-api";
import { clock } from "@/lib/learning";

/** Heartbeat cadence while playing (docs/05 §8: ~67 small UPDATEs/s for 1,000 concurrent learners). */
const HEARTBEAT_MS = 15_000;

type Problem = { title: string; detail?: string; traceId?: string };

export function LessonPlayer({ enrollmentId, lessonId }: { enrollmentId: string; lessonId: string }) {
  const [lesson, setLesson] = React.useState<LessonContent | null>(null);
  const [problem, setProblem] = React.useState<Problem | null>(null);
  const [progress, setProgress] = React.useState<ProgressResult | null>(null);
  const [marking, setMarking] = React.useState(false);
  const launched = React.useRef(false);
  const base = `/app/learn/${enrollmentId}`;

  React.useEffect(() => {
    // StrictMode runs effects twice in development; one launch = one xAPI 'launched'.
    if (launched.current) return;
    launched.current = true;
    api<LessonContent>("POST", `/v1/my/enrollments/${enrollmentId}/lessons/${lessonId}/launch`)
      .then(({ data }) => setLesson(data))
      .catch((error) => setProblem(describeError(error)));
  }, [enrollmentId, lessonId]);

  const completed = progress?.completed ?? lesson?.completed ?? false;

  async function markDone() {
    setMarking(true);
    try {
      const { data } = await api<ProgressResult>("POST", `/v1/my/enrollments/${enrollmentId}/lessons/${lessonId}/complete`);
      setProgress(data);
    } catch (error) {
      setProblem(describeError(error));
    } finally {
      setMarking(false);
    }
  }

  if (problem && !lesson) {
    return (
      <>
        <PageHeader title="레슨" />
        <Panel>
          <ErrorState title={problem.title} description={problem.detail ?? "잠시 후 다시 시도해 주세요."} traceId={problem.traceId} />
          <div className="flex justify-center pb-8">
            <Button asChild variant="secondary">
              <Link href={base}>코스로 돌아가기</Link>
            </Button>
          </div>
        </Panel>
      </>
    );
  }

  if (!lesson) {
    return (
      <LoadingRegion label="레슨 불러오는 중" className="mx-auto grid max-w-[960px] gap-6">
        <Skeleton className="h-8 w-1/2" />
        <Skeleton className="aspect-video w-full" />
      </LoadingRegion>
    );
  }

  return (
    <div className="mx-auto max-w-[960px]">
      <PageHeader
        title={lesson.title}
        description={
          <Link href={base} className="inline-flex items-center gap-1 text-label text-muted hover:text-ink">
            <ArrowLeft className="size-3.5" aria-hidden="true" />
            코스로
          </Link>
        }
        actions={
          completed ? (
            <Badge tone="success">
              <CircleCheck className="size-3 stroke-[2]" aria-hidden="true" />
              완료
            </Badge>
          ) : undefined
        }
      />

      <div className="grid gap-6">
        {problem ? <Notice tone="danger" title={problem.title}>{problem.detail}</Notice> : null}

        {progress?.courseCompleted ? (
          <Panel>
            <div className="flex flex-col items-center gap-4 px-6 py-8 text-center" role="status">
              <SealMark size="lg" stamp />
              <div className="grid gap-1">
                <p className="text-title font-semibold text-ink">코스를 수료했습니다</p>
                {progress.certificateSerial ? (
                  <p className="text-body-sm text-muted tabular">수료번호 {progress.certificateSerial}</p>
                ) : null}
              </div>
              <Button asChild>
                <Link href={base}>코스 보기</Link>
              </Button>
            </div>
          </Panel>
        ) : null}

        {lesson.type === "VIDEO" && lesson.video ? (
          <VideoLesson
            lesson={lesson}
            enrollmentId={enrollmentId}
            onProgress={setProgress}
            onError={setProblem}
            progress={progress}
          />
        ) : null}

        {lesson.type === "TEXT" ? (
          <Panel>
            <PanelBody className="pt-5">
              <article className="max-w-prose text-body leading-[1.8] text-ink">
                {(lesson.body ?? "").split(/\n{2,}/).map((paragraph, i) => (
                  <p key={i} className="mb-4 whitespace-pre-wrap last:mb-0">
                    {paragraph}
                  </p>
                ))}
              </article>
            </PanelBody>
          </Panel>
        ) : null}

        {lesson.type === "DOCUMENT" && lesson.document ? (
          <Panel>
            <PanelBody className="flex flex-wrap items-center justify-between gap-4">
              <div className="grid gap-0.5">
                <p className="font-medium text-ink">{lesson.document.fileName}</p>
                <p className="text-label text-muted">링크는 5분 동안 유효합니다. 만료되면 이 화면을 새로고침하세요.</p>
              </div>
              <Button asChild variant="secondary">
                <a href={lesson.document.url} target="_blank" rel="noopener noreferrer">
                  <Download aria-hidden="true" />
                  문서 열기
                  <ExternalLink className="size-3.5" aria-hidden="true" />
                </a>
              </Button>
            </PanelBody>
          </Panel>
        ) : null}

        {lesson.type !== "VIDEO" && !completed ? (
          <div className="flex justify-end">
            <Button onClick={markDone} loading={marking}>
              <CircleCheck aria-hidden="true" />
              {lesson.type === "TEXT" ? "다 읽었어요" : "확인했어요"}
            </Button>
          </div>
        ) : null}

        <nav aria-label="레슨 이동" className="flex flex-wrap justify-between gap-3 border-t border-line pt-4">
          {lesson.previous ? (
            <Button asChild variant="ghost">
              <Link href={`${base}/lessons/${lesson.previous.id}`}>
                <ArrowLeft aria-hidden="true" />
                <span className="max-w-[16rem] truncate">{lesson.previous.title}</span>
              </Link>
            </Button>
          ) : (
            <span />
          )}
          {lesson.next ? (
            <Button asChild variant={completed ? "primary" : "secondary"}>
              <Link href={`${base}/lessons/${lesson.next.id}`}>
                <span className="max-w-[16rem] truncate">{lesson.next.title}</span>
                <ArrowRight aria-hidden="true" />
              </Link>
            </Button>
          ) : (
            <Button asChild variant={completed ? "primary" : "secondary"}>
              <Link href={base}>
                코스로 돌아가기
                <ArrowRight aria-hidden="true" />
              </Link>
            </Button>
          )}
        </nav>
      </div>
    </div>
  );
}

/**
 * The player reports its position; the server decides how much of it counts (credited watch
 * time against the server's duration). Seeking ahead is allowed, it just earns little.
 */
function VideoLesson({
  lesson,
  enrollmentId,
  progress,
  onProgress,
  onError,
}: {
  lesson: LessonContent;
  enrollmentId: string;
  progress: ProgressResult | null;
  onProgress: (p: ProgressResult) => void;
  onError: (p: Problem) => void;
}) {
  const video = React.useRef<HTMLVideoElement>(null);
  const [playing, setPlaying] = React.useState(false);
  const url = `/api/v1/my/enrollments/${enrollmentId}/lessons/${lesson.id}/progress`;
  const source = lesson.video!;

  const send = React.useCallback(
    async (keepalive = false) => {
      const el = video.current;
      if (!el) return;
      try {
        const response = await fetch(url, {
          method: "PUT",
          headers: { "Content-Type": "application/json", Accept: "application/json" },
          body: JSON.stringify({ positionSec: Math.floor(el.currentTime) }),
          credentials: "same-origin",
          keepalive,
        });
        if (response.ok) onProgress((await response.json()) as ProgressResult);
        else if (!keepalive) onError({ title: "시청 기록을 저장하지 못했습니다", detail: "재생은 계속할 수 있습니다." });
      } catch {
        // Offline for a moment: the next heartbeat carries the position anyway.
      }
    },
    [url, onProgress, onError],
  );

  // HLS (AWS: CloudFront) needs hls.js except in Safari, which plays it natively. MP4 plays as is.
  React.useEffect(() => {
    const el = video.current;
    if (!el) return;
    let destroy: (() => void) | undefined;
    if (source.format === "hls" && !el.canPlayType("application/vnd.apple.mpegurl")) {
      void import("hls.js").then(({ default: Hls }) => {
        if (!Hls.isSupported()) return;
        const hls = new Hls({
          xhrSetup: (xhr) => {
            xhr.withCredentials = true;
          },
        });   // CloudFront signed cookies
        hls.loadSource(source.url);
        hls.attachMedia(el);
        destroy = () => hls.destroy();
      });
    } else {
      el.src = source.url;
    }
    return () => destroy?.();
  }, [source.format, source.url]);

  React.useEffect(() => {
    if (!playing) return;
    const timer = window.setInterval(() => void send(), HEARTBEAT_MS);
    return () => window.clearInterval(timer);
  }, [playing, send]);

  React.useEffect(() => {
    const onHide = () => {
      if (document.visibilityState === "hidden") void send(true);
    };
    document.addEventListener("visibilitychange", onHide);
    return () => document.removeEventListener("visibilitychange", onHide);
  }, [send]);

  const duration = progress?.durationSeconds ?? source.durationSeconds ?? null;
  const watched = progress?.watchedSec ?? lesson.watchedSec;
  const completed = progress?.completed ?? lesson.completed;

  return (
    <Panel className="overflow-hidden">
      {/* Resume where the learner left off. */}
      <video
        ref={video}
        controls
        playsInline
        preload="metadata"
        className="aspect-video w-full bg-black"
        onLoadedMetadata={(e) => {
          if (lesson.lastPositionSec > 0 && !completed) e.currentTarget.currentTime = lesson.lastPositionSec;
        }}
        onPlay={() => setPlaying(true)}
        onPause={() => {
          setPlaying(false);
          void send();
        }}
        onEnded={() => {
          setPlaying(false);
          void send();
        }}
      >
        <track kind="captions" />
      </video>
      <PanelBody className="grid gap-2">
        {duration ? (
          <Progress
            value={Math.min(watched, duration)}
            max={duration}
            label="인정된 시청 시간"
            valueText={`${clock(watched)} / ${clock(duration)}`}
            tone={completed ? "neutral" : "accent"}
          />
        ) : null}
        <p className="text-label text-muted">
          {completed
            ? "시청을 마쳤습니다. 다시 봐도 기록은 그대로입니다."
            : "영상 길이의 90%를 시청하면 완료됩니다. 건너뛴 구간은 시청 시간으로 인정되지 않습니다."}
        </p>
      </PanelBody>
    </Panel>
  );
}
