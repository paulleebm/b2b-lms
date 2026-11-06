"use client";

import Link from "next/link";
import * as React from "react";
import { ArrowLeft, CircleCheck, FilePen, MousePointerClick, Save, Undo2, Upload } from "lucide-react";
import type { CourseDetail, LessonView } from "@lms/api-client";
import {
  Badge,
  Button,
  CourseOutline,
  EmptyState,
  Field,
  FileUpload,
  Input,
  Notice,
  PageHeader,
  Panel,
  PanelBody,
  PanelFooter,
  PanelHeader,
  Textarea,
  type LessonStatus,
  type MoveDirection,
  type OutlineSection,
  type UploadItem,
} from "@lms/ui";

import { ClientApiError, api, describeError } from "@/lib/client-api";
import { courseStatus } from "@/lib/labels";
import { UploadCanceled, startUpload, type UploadHandle } from "@/lib/upload";

import { QuizEditor } from "./quiz-editor";

/**
 * Course editor wired to the API one change at a time. Every write sends the last ETag as
 * If-Match and gets the whole course back with a new one; a 412 means someone else changed the
 * course first — the local edits stay on screen and the person decides when to reload.
 */
type PublishProblem = { code: string; message: string; sectionId?: string | null; lessonId?: string | null };
type Problem = { title: string; detail?: string; traceId?: string; items?: string[] };

const VIDEO_TYPES = "video/mp4,video/quicktime,video/webm";
const DOCUMENT_TYPES =
  "application/pdf,application/vnd.openxmlformats-officedocument.presentationml.presentation,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,text/plain";
const LIMITS = { video: 5 * 1024 ** 3, document: 50 * 1024 ** 2 };

function lessonStatus(lesson: LessonView, upload: UploadItem | undefined): LessonStatus | undefined {
  if (upload && (upload.state === "queued" || upload.state === "uploading")) return "uploading";
  if (upload && upload.state === "verifying") return "processing";
  if (lesson.type === "TEXT") return undefined;
  switch (lesson.contentState) {
    case "READY":
      return "ready";
    case "PROCESSING":
      return "processing";
    case "FAILED":
      return "failed";
    default:
      return "missing-file";
  }
}

function swap<T extends { id: string }>(list: T[], id: string, direction: MoveDirection): T[] | null {
  const i = list.findIndex((x) => x.id === id);
  const j = direction === "up" ? i - 1 : i + 1;
  if (i < 0 || j < 0 || j >= list.length) return null;
  const copy = [...list];
  [copy[i], copy[j]] = [copy[j]!, copy[i]!];
  return copy;
}

const outlineBody = (sections: CourseDetail["sections"]) => ({
  sections: sections.map((s) => ({ id: s.id, lessonIds: s.lessons.map((l) => l.id) })),
});

export function CourseEditor({ initial, initialEtag }: { initial: CourseDetail; initialEtag: string }) {
  const [course, setCourse] = React.useState(initial);
  const etag = React.useRef(initialEtag);
  const [selectedId, setSelectedId] = React.useState<string | undefined>(initial.sections[0]?.lessons[0]?.id);
  const [conflict, setConflict] = React.useState(false);
  const [problem, setProblem] = React.useState<Problem | null>(null);
  const [publishProblems, setPublishProblems] = React.useState<PublishProblem[] | null>(null);
  const [pending, setPending] = React.useState<string | null>(null);
  const [announcement, setAnnouncement] = React.useState("");

  const [title, setTitle] = React.useState(initial.title);
  const [description, setDescription] = React.useState(initial.description);
  const [titleError, setTitleError] = React.useState<string | undefined>();

  const [uploads, setUploads] = React.useState<Record<string, UploadItem>>({});
  const handles = React.useRef<Record<string, UploadHandle>>({});
  const pickedFiles = React.useRef<Record<string, File>>({});

  const editable = course.status === "DRAFT";
  const lessons = course.sections.flatMap((s) => s.lessons.map((lesson) => ({ lesson, section: s })));
  const selected = lessons.find((x) => x.lesson.id === selectedId);
  const processing = lessons.some((x) => x.lesson.contentState === "PROCESSING");

  const apply = React.useCallback((data: CourseDetail, newEtag: string | null) => {
    setCourse(data);
    if (newEtag) etag.current = newEtag;
  }, []);

  /** One write. Returns the new course, or null when it failed (the reason is on screen). */
  async function write(
    method: "POST" | "PATCH" | "PUT" | "DELETE",
    path: string,
    body?: unknown,
    done?: string,
  ): Promise<CourseDetail | null> {
    setProblem(null);
    setPending(path + method);
    try {
      const { data, etag: next } = await api<CourseDetail>(method, `/v1/courses/${course.id}${path}` as `/v1/${string}`, {
        body,
        ifMatch: etag.current,
      });
      apply(data, next);
      if (done) setAnnouncement(done);
      return data;
    } catch (error) {
      if (error instanceof ClientApiError && (error.status === 412 || error.code === "CONCURRENT_UPDATE")) {
        setConflict(true);
      } else if (error instanceof ClientApiError && error.code === "COURSE_NOT_READY") {
        setPublishProblems((error.problem.errors ?? []) as PublishProblem[]);
      } else {
        // Validation problems (e.g. an invalid quiz) come back as a list: show each one.
        const items =
          error instanceof ClientApiError
            ? (error.problem.errors ?? []).map((x) => String(x.message ?? "")).filter(Boolean)
            : [];
        setProblem({ ...describeError(error), items });
      }
      return null;
    } finally {
      setPending(null);
    }
  }

  async function reload() {
    try {
      const { data, etag: next } = await api<CourseDetail>("GET", `/v1/courses/${course.id}`);
      apply(data, next);
      setTitle(data.title);
      setDescription(data.description);
      setConflict(false);
      setAnnouncement("최신 내용을 불러왔습니다.");
    } catch (error) {
      setProblem(describeError(error));
    }
  }

  // While a video is transcoding, re-read the course every few seconds (the badge turns to ready).
  React.useEffect(() => {
    if (!processing) return;
    const timer = window.setInterval(async () => {
      try {
        const { data, etag: next } = await api<CourseDetail>("GET", `/v1/courses/${course.id}`);
        apply(data, next);
      } catch {
        // a missed poll is harmless; the next one tries again
      }
    }, 2500);
    return () => window.clearInterval(timer);
  }, [processing, course.id, apply]);

  // --- uploads ------------------------------------------------------------------------------

  const setUpload = (lessonId: string, patch: Partial<UploadItem> | null) =>
    setUploads((prev) => {
      const next = { ...prev };
      if (patch === null) delete next[lessonId];
      else next[lessonId] = { ...next[lessonId]!, ...patch };
      return next;
    });

  async function upload(lesson: LessonView, file: File) {
    const kind = lesson.type === "VIDEO" ? "video" : "document";
    pickedFiles.current[lesson.id] = file;
    if (file.size > LIMITS[kind]) {
      setUploads((prev) => ({
        ...prev,
        [lesson.id]: {
          id: crypto.randomUUID(),
          name: file.name,
          sizeBytes: file.size,
          kind,
          state: "failed",
          error: kind === "video" ? "영상은 5GB까지 올릴 수 있습니다." : "문서는 50MB까지 올릴 수 있습니다.",
        },
      }));
      return;
    }
    setUploads((prev) => ({
      ...prev,
      [lesson.id]: { id: crypto.randomUUID(), name: file.name, sizeBytes: file.size, kind, state: "uploading", progress: 0 },
    }));
    const handle = startUpload(file, kind === "video" ? "VIDEO_SOURCE" : "DOCUMENT", (p) =>
      setUpload(lesson.id, { state: p.phase, progress: p.percent, partsDone: p.partsDone, partsTotal: p.partsTotal }),
    );
    handles.current[lesson.id] = handle;
    try {
      const result = await handle.done;
      const attached = await write(
        "PATCH",
        `/lessons/${lesson.id}`,
        kind === "video" ? { videoId: result.videoId } : { documentId: result.fileId },
        `${file.name}을(를) 레슨에 붙였습니다.`,
      );
      // From here the lesson's own state (from the API) tells the story: processing → ready.
      setUpload(lesson.id, attached ? null : { state: "failed", error: "파일은 올렸지만 레슨에 붙이지 못했습니다." });
    } catch (error) {
      if (error instanceof UploadCanceled) setUpload(lesson.id, { state: "canceled" });
      else setUpload(lesson.id, { state: "failed", error: describeError(error).title });
    } finally {
      delete handles.current[lesson.id];
    }
  }

  // --- outline callbacks --------------------------------------------------------------------

  const outline: OutlineSection[] = course.sections.map((s) => ({
    id: s.id,
    title: s.title,
    lessons: s.lessons.map((l) => ({ id: l.id, title: l.title, type: l.type, status: lessonStatus(l, uploads[l.id]) })),
  }));

  const moveSection = (id: string, direction: MoveDirection) => {
    const next = swap(course.sections, id, direction);
    if (next) void write("PUT", "/outline", outlineBody(next), "섹션 순서를 바꿨습니다.");
  };

  const moveLesson = (id: string, direction: MoveDirection) => {
    const next = course.sections.map((s) => {
      if (!s.lessons.some((l) => l.id === id)) return s;
      return { ...s, lessons: swap(s.lessons, id, direction) ?? s.lessons };
    });
    void write("PUT", "/outline", outlineBody(next), "레슨 순서를 바꿨습니다.");
  };

  const addLesson = async (sectionId: string, type: LessonView["type"]) => {
    const data = await write("POST", `/sections/${sectionId}/lessons`, { title: "새 레슨", type }, "레슨을 추가했습니다.");
    const added = data?.sections.find((s) => s.id === sectionId)?.lessons.at(-1);
    if (added) setSelectedId(added.id);
  };

  // --- course info --------------------------------------------------------------------------

  const infoChanged = title !== course.title || description !== course.description;
  const saveInfo = async (e: React.FormEvent) => {
    e.preventDefault();
    const error = title.trim() ? undefined : "코스 제목을 입력하세요.";
    setTitleError(error);
    if (error) return;
    await write("PATCH", "", { title: title.trim(), description }, "코스 정보를 저장했습니다.");
  };

  const status = courseStatus(course.status);
  const blockers = publishProblems ?? [];

  return (
    <>
      <PageHeader
        title={course.title}
        description={
          <Link href="/app/studio" className="inline-flex items-center gap-1 text-label text-muted hover:text-ink">
            <ArrowLeft className="size-3.5" aria-hidden="true" />
            코스 목록
          </Link>
        }
        actions={
          <>
            <Badge tone={status.tone}>
              {course.status === "PUBLISHED" ? (
                <CircleCheck className="size-3 stroke-[2]" aria-hidden="true" />
              ) : (
                <FilePen className="size-3 stroke-[2]" aria-hidden="true" />
              )}
              {status.label}
            </Badge>
            {course.status === "DRAFT" ? (
              <Button
                loading={pending === "/publishPOST"}
                aria-describedby={blockers.length ? "publish-blockers" : undefined}
                onClick={async () => {
                  setPublishProblems(null);
                  if (await write("POST", "/publish", undefined, "코스를 게시했습니다.")) setPublishProblems(null);
                }}
              >
                <Upload aria-hidden="true" />
                게시
              </Button>
            ) : course.status === "PUBLISHED" ? (
              <Button
                variant="secondary"
                loading={pending === "/unpublishPOST"}
                onClick={() => write("POST", "/unpublish", undefined, "게시를 취소했습니다. 다시 고칠 수 있습니다.")}
              >
                <Undo2 aria-hidden="true" />
                게시 취소
              </Button>
            ) : null}
          </>
        }
      />

      <p className="sr-only" role="status" aria-live="polite">
        {announcement}
      </p>

      <div className="grid gap-6">
        {conflict ? (
          <Notice
            tone="danger"
            title="다른 사람이 먼저 이 코스를 수정했습니다"
            action={
              <Button variant="secondary" size="sm" onClick={reload}>
                새로고침
              </Button>
            }
          >
            방금 바꾼 내용은 저장되지 않았습니다. 새로고침하면 최신 내용을 불러옵니다. 입력 중인 글은 먼저 옮겨 적어
            두세요.
          </Notice>
        ) : null}

        {problem ? (
          <Notice tone="danger" title={problem.title}>
            {problem.detail}
            {problem.items?.length ? (
              <ul className="mt-1 list-disc pl-5">
                {problem.items.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            ) : null}
            {problem.traceId ? <span className="block text-caption text-muted">추적 ID {problem.traceId}</span> : null}
          </Notice>
        ) : null}

        {blockers.length > 0 ? (
          <Notice id="publish-blockers" tone="warning" title={`게시하려면 ${blockers.length}가지를 해결하세요`}>
            <ul className="mt-1 grid gap-0.5">
              {blockers.map((b, i) => (
                <li key={`${b.code}-${b.lessonId ?? b.sectionId ?? i}`}>
                  {b.lessonId ? (
                    <button
                      type="button"
                      className="text-left font-medium text-ink underline underline-offset-3 hover:text-ink-2"
                      onClick={() => setSelectedId(b.lessonId ?? undefined)}
                    >
                      {b.message}
                    </button>
                  ) : (
                    b.message
                  )}
                </li>
              ))}
            </ul>
          </Notice>
        ) : null}

        {course.status === "PUBLISHED" ? (
          <Notice tone="info" title="게시된 코스는 목차가 잠겨 있습니다">
            코스 제목과 설명은 고칠 수 있습니다. 섹션이나 레슨을 바꾸려면 게시를 취소하세요.
          </Notice>
        ) : null}

        <Panel>
          <PanelHeader title="코스 정보" />
          <form noValidate onSubmit={saveInfo}>
            <PanelBody className="grid max-w-3xl gap-4">
              <Field label="코스 제목" error={titleError}>
                <Input
                  value={title}
                  maxLength={200}
                  disabled={course.status === "ARCHIVED"}
                  onChange={(e) => {
                    setTitle(e.target.value);
                    if (titleError) setTitleError(e.target.value.trim() ? undefined : "코스 제목을 입력하세요.");
                  }}
                />
              </Field>
              <Field label="코스 설명" optional hint="학습자 목록에 두 줄까지 보입니다.">
                <Textarea
                  rows={3}
                  value={description}
                  maxLength={5000}
                  disabled={course.status === "ARCHIVED"}
                  onChange={(e) => setDescription(e.target.value)}
                />
              </Field>
            </PanelBody>
            <PanelFooter className="justify-end">
              <Button type="submit" variant="secondary" disabled={!infoChanged} loading={pending === "PATCH"}>
                <Save aria-hidden="true" />
                저장
              </Button>
            </PanelFooter>
          </form>
        </Panel>

        <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]">
          <Panel>
            <PanelHeader
              title="커리큘럼"
              description={
                <span className="tabular">
                  섹션 {course.sections.length}개 · 레슨 {lessons.length}개
                </span>
              }
            />
            <PanelBody>
              <CourseOutline
                sections={outline}
                readOnly={!editable}
                selectedLessonId={selectedId}
                onSelectLesson={setSelectedId}
                onAddSection={() =>
                  write("POST", "/sections", { title: `${course.sections.length + 1}장` }, "섹션을 추가했습니다.")
                }
                onRenameSection={(id, value) => write("PATCH", `/sections/${id}`, { title: value }, "섹션 이름을 바꿨습니다.")}
                onDeleteSection={(id) => {
                  if (course.sections.find((s) => s.id === id)?.lessons.some((l) => l.id === selectedId)) {
                    setSelectedId(undefined);
                  }
                  void write("DELETE", `/sections/${id}`, undefined, "섹션을 삭제했습니다.");
                }}
                onMoveSection={moveSection}
                onAddLesson={addLesson}
                onRenameLesson={(id, value) => write("PATCH", `/lessons/${id}`, { title: value }, "레슨 이름을 바꿨습니다.")}
                onDeleteLesson={(id) => {
                  if (id === selectedId) setSelectedId(undefined);
                  handles.current[id]?.cancel();
                  void write("DELETE", `/lessons/${id}`, undefined, "레슨을 삭제했습니다.");
                }}
                onMoveLesson={moveLesson}
              />
            </PanelBody>
          </Panel>

          <Panel>
            <PanelHeader title="레슨 상세" />
            <PanelBody>
              {selected ? (
                <LessonDetail
                  key={selected.lesson.id}
                  lesson={selected.lesson}
                  sectionTitle={selected.section.title}
                  editable={editable}
                  upload={uploads[selected.lesson.id]}
                  onSave={(body, done) => write("PATCH", `/lessons/${selected.lesson.id}`, body, done)}
                  onFile={(file) => upload(selected.lesson, file)}
                  onCancelUpload={() => handles.current[selected.lesson.id]?.cancel()}
                  onRetryUpload={() => {
                    const file = pickedFiles.current[selected.lesson.id];
                    if (file) void upload(selected.lesson, file);
                  }}
                  onRemoveUpload={() => setUpload(selected.lesson.id, null)}
                />
              ) : (
                <EmptyState
                  icon={MousePointerClick}
                  title={lessons.length === 0 ? "아직 레슨이 없습니다" : "편집할 레슨을 고르세요"}
                  description={
                    lessons.length === 0
                      ? "왼쪽에서 섹션을 만들고 레슨을 추가하면, 여기에서 제목과 파일, 본문을 편집합니다."
                      : "왼쪽 커리큘럼에서 레슨을 고르면 제목과 파일, 본문을 여기에서 편집합니다."
                  }
                />
              )}
            </PanelBody>
          </Panel>
        </div>

        {/* Keyed by revision: a save (or a reload after a conflict) shows what the server stored. */}
        <QuizEditor
          key={course.quiz?.revision ?? "none"}
          quiz={course.quiz}
          editable={editable}
          onSave={(body) => write("PUT", "/quiz", body, "퀴즈를 저장했습니다.")}
          onDelete={() => write("DELETE", "/quiz", undefined, "퀴즈를 삭제했습니다.")}
        />
      </div>
    </>
  );
}

const typeLabel = { VIDEO: "영상", DOCUMENT: "문서", TEXT: "텍스트" } as const;

function LessonDetail({
  lesson,
  sectionTitle,
  editable,
  upload,
  onSave,
  onFile,
  onCancelUpload,
  onRetryUpload,
  onRemoveUpload,
}: {
  lesson: LessonView;
  sectionTitle: string;
  editable: boolean;
  upload: UploadItem | undefined;
  onSave: (body: { title?: string; body?: string }, done: string) => Promise<CourseDetail | null>;
  onFile: (file: File) => void;
  onCancelUpload: () => void;
  onRetryUpload: () => void;
  onRemoveUpload: () => void;
}) {
  const [title, setTitle] = React.useState(lesson.title);
  const [body, setBody] = React.useState(lesson.body ?? "");
  const [saving, setSaving] = React.useState(false);
  const bodyChanged = body !== (lesson.body ?? "");

  const attached =
    lesson.type === "VIDEO"
      ? lesson.video && { name: lesson.video.fileName ?? "영상", state: lesson.contentState }
      : lesson.type === "DOCUMENT"
        ? lesson.document && { name: lesson.document.fileName, state: lesson.contentState }
        : null;
  const busy = upload && ["queued", "uploading", "verifying"].includes(upload.state);

  return (
    <div className="grid gap-5">
      <Field label="레슨 제목">
        <Input
          value={title}
          maxLength={200}
          disabled={!editable}
          onChange={(e) => setTitle(e.target.value)}
          onBlur={() => {
            if (title.trim() && title.trim() !== lesson.title) void onSave({ title: title.trim() }, "레슨 제목을 저장했습니다.");
          }}
        />
      </Field>

      {lesson.type === "TEXT" ? (
        <div className="grid gap-3">
          <Field label="본문" hint="빈 줄로 문단을 나눕니다. 학습자 화면에서는 읽기 좋은 폭으로 줄을 바꿉니다.">
            <Textarea
              rows={10}
              value={body}
              maxLength={100_000}
              disabled={!editable}
              onChange={(e) => setBody(e.target.value)}
              className="text-body leading-[1.75]"
            />
          </Field>
          {editable ? (
            <div className="flex justify-end">
              <Button
                variant="secondary"
                disabled={!bodyChanged}
                loading={saving}
                onClick={async () => {
                  setSaving(true);
                  await onSave({ body }, "본문을 저장했습니다.");
                  setSaving(false);
                }}
              >
                <Save aria-hidden="true" />
                본문 저장
              </Button>
            </div>
          ) : null}
        </div>
      ) : (
        <div className="grid gap-2">
          <p className="text-label font-medium text-ink">{lesson.type === "VIDEO" ? "영상 파일" : "문서 파일"}</p>
          {attached ? (
            <p className="text-body-sm text-ink-2">
              현재 파일: <span className="font-medium text-ink">{attached.name}</span>
              {attached.state === "PROCESSING" ? " · 변환 중" : attached.state === "FAILED" ? " · 변환 실패" : ""}
            </p>
          ) : null}
          <FileUpload
            label={lesson.type === "VIDEO" ? (attached ? "다른 영상" : "영상 파일") : attached ? "다른 문서" : "문서 파일"}
            accept={lesson.type === "VIDEO" ? VIDEO_TYPES : DOCUMENT_TYPES}
            acceptLabel={lesson.type === "VIDEO" ? "MP4, MOV, WebM" : "PDF, PPTX, DOCX, XLSX, TXT"}
            maxSizeLabel={lesson.type === "VIDEO" ? "5 GB" : "50 MB"}
            items={upload ? [upload] : []}
            showDropzone={!busy}
            disabled={!editable}
            disabledReason="게시된 코스는 파일을 바꿀 수 없습니다. 게시를 취소하세요."
            onFilesSelected={(files) => {
              const file = files[0];
              if (file) onFile(file);
            }}
            onCancel={onCancelUpload}
            onRetry={onRetryUpload}
            onRemove={onRemoveUpload}
          />
        </div>
      )}
      <p className="text-label text-muted">
        {sectionTitle} · {typeLabel[lesson.type]} 레슨
      </p>
    </div>
  );
}
