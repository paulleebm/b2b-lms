"use client";

import * as React from "react";
import { CircleCheck, FilePen, MousePointerClick, Save, Upload } from "lucide-react";

import { AppShell, PageHeader } from "../components/app-shell";
import { Button } from "../components/button";
import {
  CourseOutline,
  type LessonStatus,
  type LessonType,
  type MoveDirection,
  type OutlineLesson,
  type OutlineSection,
} from "../components/course-outline";
import { Field, Input, Textarea } from "../components/field";
import { FileUpload, type UploadItem, type UploadState } from "../components/file-upload";
import { Notice } from "../components/notice";
import { Panel, PanelBody, PanelHeader } from "../components/panel";
import { Badge } from "../components/status-badge";
import { EmptyState, ErrorState, LoadingRegion, Skeleton } from "../components/states";
import { UPLOAD_PART_BYTES, courseReady, type CourseDraft } from "./fixtures";
import { AccountArea, WorkspaceSwitcher, instructorNav } from "./shell-presets";

/**
 * Instructor "코스 편집" (DESIGN.md §4, §9; docs/05 §6).
 * Course info on top, the outline on the left, the selected lesson on the right.
 *
 * - Publish rules mirror the server's (docs/05 §6 "게시 검증"): at least one lesson, every video
 *   transcoded, every file lesson has its file. The button is disabled only for these
 *   server-side blockers, and the reason is always visible and linked (aria-describedby).
 *   An empty title is a form error shown on the field, not a reason to disable (§4 Buttons).
 * - "conflict" is the optimistic-lock 409 (course.version): the edits stay on screen.
 * - Uploads are simulated so the demo moves; the real app drives FileUpload from S3 multipart.
 */
type ScreenState = "populated" | "loading" | "error" | "conflict";

type StudioCourseEditorScreenProps = {
  state?: ScreenState;
  course?: CourseDraft;
  onSave?: (course: { title: string; description: string; sections: OutlineSection[] }) => void;
  onPublish?: () => void;
};

const typeLabel: Record<LessonType, string> = { VIDEO: "영상", DOCUMENT: "문서", TEXT: "텍스트" };

/** The lesson badge follows its file, so the outline and the detail panel can never disagree. */
function lessonStatus(lesson: OutlineLesson, file: UploadItem | undefined): LessonStatus | undefined {
  if (lesson.type === "TEXT") return undefined;
  if (!file) return "missing-file";
  const map: Record<UploadState, LessonStatus> = {
    queued: "uploading",
    uploading: "uploading",
    verifying: "processing",
    processing: "processing",
    ready: "ready",
    failed: "failed",
    canceled: "missing-file",
  };
  return map[file.state];
}

function blockerText(lesson: OutlineLesson, status: LessonStatus | undefined): string | null {
  switch (status) {
    case "uploading":
      return "파일을 올리는 중입니다.";
    case "processing":
      return "영상이 아직 변환 중입니다. 변환이 끝나면 게시할 수 있습니다.";
    case "missing-file":
      return lesson.type === "VIDEO" ? "영상 파일이 없습니다." : "문서 파일이 없습니다.";
    case "failed":
      return "영상 변환에 실패했습니다. 파일을 다시 올려 주세요.";
    default:
      return null;
  }
}

let idSeq = 0;
const nextId = (prefix: string) => `${prefix}-new-${++idSeq}`;

function move<T extends { id: string }>(list: T[], id: string, direction: MoveDirection): T[] {
  const i = list.findIndex((x) => x.id === id);
  const j = direction === "up" ? i - 1 : i + 1;
  if (i < 0 || j < 0 || j >= list.length) return list;
  const copy = [...list];
  [copy[i], copy[j]] = [copy[j]!, copy[i]!];
  return copy;
}

function StudioCourseEditorScreen({
  state = "populated",
  course = courseReady,
  onSave,
  onPublish,
}: StudioCourseEditorScreenProps) {
  const [title, setTitle] = React.useState(course.title);
  const [description, setDescription] = React.useState(course.description);
  const [titleError, setTitleError] = React.useState<string | undefined>();
  const [sections, setSections] = React.useState(course.sections);
  const [files, setFiles] = React.useState(course.files);
  const [texts, setTexts] = React.useState(course.texts);
  const [selectedId, setSelectedId] = React.useState<string | undefined>(course.sections[0]?.lessons[0]?.id);
  const [saving, setSaving] = React.useState(false);
  const [savedAt, setSavedAt] = React.useState<string | null>(null);
  const [conflict, setConflict] = React.useState(state === "conflict");
  const [announcement, setAnnouncement] = React.useState("");
  const titleRef = React.useRef<HTMLInputElement>(null);

  React.useEffect(() => {
    setTitle(course.title);
    setDescription(course.description);
    setSections(course.sections);
    setFiles(course.files);
    setTexts(course.texts);
    setSelectedId(course.sections[0]?.lessons[0]?.id);
  }, [course]);
  React.useEffect(() => setConflict(state === "conflict"), [state]);

  // Simulated multipart upload: a few ticks of progress, then verify, then (video) transcode.
  const uploading = Object.values(files).some((f) => f.state === "uploading" || f.state === "verifying");
  React.useEffect(() => {
    if (!uploading) return;
    const timer = window.setInterval(() => {
      setFiles((prev) => {
        const next = { ...prev };
        for (const [lessonId, f] of Object.entries(prev)) {
          if (f.state === "uploading") {
            const progress = Math.min((f.progress ?? 0) + 25, 100);
            const partsTotal = f.partsTotal ?? 1;
            next[lessonId] = {
              ...f,
              progress,
              partsDone: Math.round((partsTotal * progress) / 100),
              state: progress >= 100 ? "verifying" : "uploading",
            };
          } else if (f.state === "verifying") {
            next[lessonId] = { ...f, state: f.kind === "video" ? "processing" : "ready" };
          }
        }
        return next;
      });
    }, 500);
    return () => window.clearInterval(timer);
  }, [uploading]);

  const outline: OutlineSection[] = sections.map((s) => ({
    ...s,
    lessons: s.lessons.map((l) => ({ ...l, status: lessonStatus(l, files[l.id]) })),
  }));
  const allLessons = outline.flatMap((s) => s.lessons.map((l) => ({ lesson: l, section: s })));
  const selected = allLessons.find((x) => x.lesson.id === selectedId);

  const blockers: { lessonId?: string; title?: string; text: string }[] = [];
  if (allLessons.length === 0) blockers.push({ text: "레슨을 하나 이상 추가해야 게시할 수 있습니다." });
  for (const { lesson } of allLessons) {
    const text = blockerText(lesson, lesson.status);
    if (text) blockers.push({ lessonId: lesson.id, title: lesson.title, text });
  }
  const publishBlocked = blockers.length > 0;

  const renameLesson = (id: string, value: string) =>
    setSections((prev) => prev.map((s) => ({ ...s, lessons: s.lessons.map((l) => (l.id === id ? { ...l, title: value } : l)) })));

  const startUpload = (lessonId: string, file: File, kind: UploadItem["kind"]) => {
    setFiles((prev) => ({
      ...prev,
      [lessonId]: {
        id: nextId("file"),
        name: file.name,
        sizeBytes: file.size,
        kind,
        state: "uploading",
        progress: 0,
        partsDone: 0,
        partsTotal: kind === "video" ? Math.max(1, Math.ceil(file.size / UPLOAD_PART_BYTES)) : undefined,
      },
    }));
  };

  const validateTitle = (value: string) => (value.trim() ? undefined : "코스 제목을 입력하세요.");

  const save = () => {
    const error = validateTitle(title);
    setTitleError(error);
    if (error) {
      titleRef.current?.focus();
      return;
    }
    if (conflict) {
      setAnnouncement("다른 사람이 먼저 수정해서 저장하지 못했습니다.");
      return;
    }
    onSave?.({ title: title.trim(), description, sections });
    setSaving(true);
    window.setTimeout(() => {
      setSaving(false);
      setSavedAt("방금");
      setAnnouncement("코스를 저장했습니다.");
    }, 600);
  };

  const shell = (children: React.ReactNode) => (
    <AppShell
      workspace={<WorkspaceSwitcher name="에이크미" />}
      account={<AccountArea name="이도윤" role="강사" />}
      sections={instructorNav}
    >
      {children}
    </AppShell>
  );

  if (state === "error") {
    return shell(
      <>
        <PageHeader title="코스 편집" actions={<Badge tone="neutral">합성 데이터</Badge>} />
        <Panel>
          <ErrorState
            title="코스를 불러오지 못했습니다"
            description="서버와 연결이 잠시 끊겼습니다. 저장하지 않은 변경은 없으니 다시 시도해 주세요."
            traceId="0af7651916cd43dd8448eb211c80319c"
            onRetry={() => undefined}
          />
        </Panel>
      </>,
    );
  }

  if (state === "loading") {
    return shell(
      <>
        <PageHeader title="코스 편집" actions={<Badge tone="neutral">합성 데이터</Badge>} />
        <div className="grid gap-6">
          <Panel>
            <PanelHeader title="코스 정보" />
            <PanelBody>
              <LoadingRegion label="코스 정보 불러오는 중" className="grid gap-4">
                <Skeleton className="h-9 w-full max-w-xl" />
                <Skeleton className="h-24 w-full max-w-xl" />
              </LoadingRegion>
            </PanelBody>
          </Panel>
          <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]">
            <Panel>
              <PanelHeader title="커리큘럼" />
              <PanelBody>
                <CourseOutline sections={[]} loading />
              </PanelBody>
            </Panel>
            <Panel>
              <PanelHeader title="레슨 상세" />
              <PanelBody className="grid gap-4" aria-hidden="true">
                <Skeleton className="h-9 w-full" />
                <Skeleton className="h-32 w-full" />
              </PanelBody>
            </Panel>
          </div>
        </div>
      </>,
    );
  }

  const published = course.status === "PUBLISHED";

  let detail: React.ReactNode;
  if (!selected) {
    detail = (
      <EmptyState
        icon={MousePointerClick}
        title={allLessons.length === 0 ? "아직 레슨이 없습니다" : "편집할 레슨을 고르세요"}
        description={
          allLessons.length === 0
            ? "왼쪽에서 섹션을 만들고 레슨을 추가하면, 여기에서 제목과 파일, 본문을 편집합니다."
            : "왼쪽 커리큘럼에서 레슨을 고르면 제목과 파일, 본문을 여기에서 편집합니다."
        }
      />
    );
  } else {
    const { lesson, section } = selected;
    const file = files[lesson.id];
    const kind: UploadItem["kind"] = lesson.type === "VIDEO" ? "video" : "document";
    const canReplace = !file || file.state === "failed" || file.state === "canceled" || file.state === "ready";
    detail = (
      <div className="grid gap-5">
        <Field label="레슨 제목">
          <Input
            value={lesson.title}
            maxLength={120}
            onChange={(e) => renameLesson(lesson.id, e.target.value)}
          />
        </Field>
        {lesson.type === "TEXT" ? (
          <Field label="본문" hint="빈 줄로 문단을 나눕니다. 학습자 화면에서는 읽기 좋은 폭으로 줄을 바꿉니다.">
            <Textarea
              rows={10}
              value={texts[lesson.id] ?? ""}
              onChange={(e) => setTexts((prev) => ({ ...prev, [lesson.id]: e.target.value }))}
              className="text-body leading-[1.75]"
            />
          </Field>
        ) : (
          <div className="grid gap-1.5">
            <p className="text-label font-medium text-ink">{lesson.type === "VIDEO" ? "영상 파일" : "문서 파일"}</p>
            <FileUpload
              label={lesson.type === "VIDEO" ? (file ? "다른 영상" : "영상 파일") : file ? "다른 문서" : "문서 파일"}
              accept={lesson.type === "VIDEO" ? "video/mp4,video/quicktime,video/webm" : "application/pdf"}
              acceptLabel={lesson.type === "VIDEO" ? "MP4, MOV, WebM" : "PDF"}
              maxSizeLabel={lesson.type === "VIDEO" ? "5 GB" : "50 MB"}
              items={file ? [file] : []}
              showDropzone={canReplace}
              onFilesSelected={(selectedFiles) => {
                const f = selectedFiles[0];
                if (f) startUpload(lesson.id, f, kind);
              }}
              onCancel={() =>
                setFiles((prev) => ({ ...prev, [lesson.id]: { ...prev[lesson.id]!, state: "canceled" } }))
              }
              onRetry={() =>
                setFiles((prev) => ({
                  ...prev,
                  [lesson.id]: { ...prev[lesson.id]!, state: "uploading", progress: 0, partsDone: 0, error: undefined },
                }))
              }
              onRemove={() =>
                setFiles((prev) => {
                  const next = { ...prev };
                  delete next[lesson.id];
                  return next;
                })
              }
            />
          </div>
        )}
        <p className="text-label text-muted">
          {section.title} · {typeLabel[lesson.type]} 레슨
        </p>
      </div>
    );
  }

  const lessonCount = allLessons.length;

  return shell(
    <>
      <PageHeader
        title="코스 편집"
        description={savedAt ? `${savedAt} 저장했습니다.` : "코스 정보와 레슨 순서를 정리한 뒤 게시합니다. 게시하면 배정할 수 있습니다."}
        actions={
          <>
            <Badge tone="neutral">합성 데이터</Badge>
            {published ? (
              <Badge tone="success">
                <CircleCheck className="size-3 stroke-[2]" aria-hidden="true" />
                게시됨
              </Badge>
            ) : (
              <Badge tone="neutral">
                <FilePen className="size-3 stroke-[2]" aria-hidden="true" />
                초안
              </Badge>
            )}
            <Button variant="secondary" onClick={save} loading={saving}>
              <Save aria-hidden="true" />
              저장
            </Button>
            <Button
              disabled={publishBlocked}
              aria-describedby={publishBlocked ? "publish-blockers" : undefined}
              onClick={() => {
                onPublish?.();
                setAnnouncement("게시를 요청했습니다.");
              }}
            >
              <Upload aria-hidden="true" />
              {published ? "변경 사항 게시" : "게시"}
            </Button>
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
            title="다른 사람이 먼저 수정했습니다. 새로고침 후 다시 저장하세요."
            action={
              <Button variant="secondary" size="sm" onClick={() => setConflict(false)}>
                새로고침
              </Button>
            }
          >
            이 화면에서 바꾼 내용은 아직 저장되지 않았고, 화면에는 그대로 남아 있습니다. 새로고침하면 최신 버전을
            불러오므로 필요한 내용은 먼저 옮겨 적어 두세요.
          </Notice>
        ) : null}

        {publishBlocked ? (
          <Notice
            id="publish-blockers"
            tone="warning"
            title={`게시하려면 ${blockers.length}가지를 해결하세요`}
          >
            <ul className="mt-1 grid gap-0.5">
              {blockers.map((b, i) => (
                <li key={b.lessonId ?? i}>
                  {b.lessonId ? (
                    <>
                      <button
                        type="button"
                        className="font-medium text-ink underline underline-offset-3 hover:text-ink-2"
                        onClick={() => setSelectedId(b.lessonId)}
                      >
                        {b.title}
                      </button>
                      : {b.text}
                    </>
                  ) : (
                    b.text
                  )}
                </li>
              ))}
            </ul>
          </Notice>
        ) : null}

        <Panel>
          <PanelHeader title="코스 정보" />
          <PanelBody className="grid max-w-3xl gap-4">
            <Field label="코스 제목" error={titleError}>
              <Input
                ref={titleRef}
                value={title}
                maxLength={120}
                onChange={(e) => {
                  setTitle(e.target.value);
                  if (titleError) setTitleError(validateTitle(e.target.value));
                }}
                onBlur={() => setTitleError(validateTitle(title))}
              />
            </Field>
            <Field label="코스 설명" optional hint="학습자 목록에 두 줄까지 보입니다.">
              <Textarea rows={3} value={description} onChange={(e) => setDescription(e.target.value)} />
            </Field>
          </PanelBody>
        </Panel>

        <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]">
          <Panel>
            <PanelHeader
              title="커리큘럼"
              description={
                <span className="tabular">
                  섹션 {sections.length}개 · 레슨 {lessonCount}개
                </span>
              }
            />
            <PanelBody>
              <CourseOutline
                sections={outline}
                selectedLessonId={selectedId}
                onSelectLesson={setSelectedId}
                onAddSection={() =>
                  setSections((prev) => [...prev, { id: nextId("section"), title: "새 섹션", lessons: [] }])
                }
                onRenameSection={(id, value) =>
                  setSections((prev) => prev.map((s) => (s.id === id ? { ...s, title: value } : s)))
                }
                onDeleteSection={(id) => {
                  const removed = sections.find((s) => s.id === id);
                  if (removed?.lessons.some((l) => l.id === selectedId)) setSelectedId(undefined);
                  setSections((prev) => prev.filter((s) => s.id !== id));
                }}
                onMoveSection={(id, direction) => setSections((prev) => move(prev, id, direction))}
                onAddLesson={(sectionId, type) => {
                  const id = nextId("lesson");
                  setSections((prev) =>
                    prev.map((s) =>
                      s.id === sectionId ? { ...s, lessons: [...s.lessons, { id, title: "새 레슨", type }] } : s,
                    ),
                  );
                  setSelectedId(id);
                }}
                onRenameLesson={renameLesson}
                onDeleteLesson={(id) => {
                  if (id === selectedId) setSelectedId(undefined);
                  setSections((prev) => prev.map((s) => ({ ...s, lessons: s.lessons.filter((l) => l.id !== id) })));
                }}
                onMoveLesson={(id, direction) =>
                  setSections((prev) => prev.map((s) => ({ ...s, lessons: move(s.lessons, id, direction) })))
                }
              />
            </PanelBody>
          </Panel>

          <Panel>
            <PanelHeader title="레슨 상세" />
            <PanelBody>{detail}</PanelBody>
          </Panel>
        </div>
      </div>
    </>,
  );
}

export { StudioCourseEditorScreen, type StudioCourseEditorScreenProps };
