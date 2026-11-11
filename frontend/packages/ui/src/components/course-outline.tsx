"use client";

import * as React from "react";
import {
  AlignLeft,
  ArrowDown,
  ArrowUp,
  CircleAlert,
  CloudUpload,
  FileText,
  FileWarning,
  ListTree,
  LoaderCircle,
  Pencil,
  Plus,
  Trash2,
  Video,
  type LucideIcon,
} from "lucide-react";

import { cn } from "../lib/utils";
import { Button } from "./button";
import { Input } from "./field";
import { EmptyState, LoadingRegion, Skeleton } from "./states";
import { Badge } from "./status-badge";

/**
 * Course outline editor: sections → lessons (DESIGN.md §4, §7, §9). Presentational: every
 * change is a callback, the caller owns the data and the server is the source of truth.
 *
 * - Reordering is up/down buttons, not drag and drop: reachable by keyboard and touch with no
 *   extra dependency, and focus follows the moved item (WCAG 2.1.1, 2.4.3).
 * - Rename is inline: Enter saves, Escape cancels, blur saves (spreadsheet convention).
 * - Delete asks inline on the same row; no modal (§7 "인라인으로 먼저 해결").
 * - Lesson status uses icon + text badges; a ready lesson shows no badge (quiet by default).
 */

type LessonType = "VIDEO" | "DOCUMENT" | "TEXT";
type LessonStatus = "ready" | "uploading" | "processing" | "missing-file" | "failed";
type MoveDirection = "up" | "down";

type OutlineLesson = { id: string; title: string; type: LessonType; status?: LessonStatus };
type OutlineSection = { id: string; title: string; lessons: OutlineLesson[] };

type CourseOutlineProps = {
  sections: OutlineSection[];
  readOnly?: boolean;
  loading?: boolean;
  selectedLessonId?: string;
  onAddSection?: () => void;
  onRenameSection?: (id: string, title: string) => void;
  onDeleteSection?: (id: string) => void;
  onMoveSection?: (id: string, direction: MoveDirection) => void;
  onAddLesson?: (sectionId: string, type: LessonType) => void;
  onRenameLesson?: (id: string, title: string) => void;
  onDeleteLesson?: (id: string) => void;
  onMoveLesson?: (id: string, direction: MoveDirection) => void;
  onSelectLesson?: (id: string) => void;
  /** Section titles render as this heading level (the outline usually sits under a panel h2). */
  headingLevel?: 3 | 4;
  className?: string;
};

const lessonTypes: { type: LessonType; label: string; icon: LucideIcon }[] = [
  { type: "VIDEO", label: "영상", icon: Video },
  { type: "DOCUMENT", label: "문서", icon: FileText },
  { type: "TEXT", label: "텍스트", icon: AlignLeft },
];

const typeView = Object.fromEntries(lessonTypes.map((t) => [t.type, t])) as Record<
  LessonType,
  (typeof lessonTypes)[number]
>;

const statusView: Record<
  Exclude<LessonStatus, "ready">,
  { tone: "info" | "warning" | "danger"; text: string; icon: LucideIcon; spin?: boolean }
> = {
  uploading: { tone: "info", text: "업로드 중", icon: CloudUpload },
  processing: { tone: "info", text: "변환 중", icon: LoaderCircle, spin: true },
  "missing-file": { tone: "warning", text: "파일 없음", icon: FileWarning },
  failed: { tone: "danger", text: "변환 실패", icon: CircleAlert },
};

function LessonStatusBadge({ status }: { status?: LessonStatus }) {
  if (!status || status === "ready") return null;
  const view = statusView[status];
  const Icon = view.icon;
  return (
    <Badge tone={view.tone} className="shrink-0">
      <Icon className={cn("size-3 stroke-[2]", view.spin && "animate-spin")} aria-hidden="true" />
      {view.text}
    </Badge>
  );
}

type Target = { kind: "section" | "lesson"; id: string };
const same = (a: Target | null, kind: Target["kind"], id: string) => a?.kind === kind && a.id === id;

function InlineRename({
  initial,
  label,
  onCommit,
  onCancel,
  className,
}: {
  initial: string;
  label: string;
  onCommit: (title: string) => void;
  onCancel: () => void;
  className?: string;
}) {
  const [value, setValue] = React.useState(initial);
  const [error, setError] = React.useState<string | null>(null);
  const inputRef = React.useRef<HTMLInputElement>(null);
  const settled = React.useRef(false);
  const reactId = React.useId();

  React.useEffect(() => {
    inputRef.current?.focus();
    inputRef.current?.select();
  }, []);

  const commit = () => {
    const title = value.trim();
    if (!title) {
      setError("제목을 입력하세요.");
      return;
    }
    settled.current = true;
    onCommit(title);
  };
  const cancel = () => {
    settled.current = true;
    onCancel();
  };

  return (
    <div className={cn("grid min-w-0 flex-1 gap-1", className)}>
      <Input
        ref={inputRef}
        value={value}
        maxLength={120}
        aria-label={label}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${reactId}-error` : `${reactId}-hint`}
        className="h-8"
        onChange={(e) => {
          setValue(e.target.value);
          if (error && e.target.value.trim()) setError(null);
        }}
        onKeyDown={(e) => {
          if (e.key === "Enter") {
            e.preventDefault();
            commit();
          } else if (e.key === "Escape") {
            e.preventDefault();
            e.stopPropagation();
            cancel();
          }
        }}
        onBlur={() => {
          if (settled.current) return;
          // Leaving an empty title behind would orphan the row: blur with nothing typed cancels.
          if (value.trim()) commit();
          else cancel();
        }}
      />
      {error ? (
        <p id={`${reactId}-error`} className="flex items-start gap-1.5 text-label text-danger">
          <CircleAlert className="mt-0.5 size-3.5 shrink-0 stroke-[1.75]" aria-hidden="true" />
          <span>{error}</span>
        </p>
      ) : (
        <span id={`${reactId}-hint`} className="sr-only">
          Enter를 누르면 저장하고 Esc를 누르면 취소합니다.
        </span>
      )}
    </div>
  );
}

function ConfirmDelete({
  question,
  detail,
  subject,
  onConfirm,
  onCancel,
}: {
  question: string;
  detail?: string;
  subject: string;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  const cancelRef = React.useRef<HTMLButtonElement>(null);
  const questionId = React.useId();
  // Focus lands on the safe choice; an accidental Enter does not delete.
  React.useEffect(() => cancelRef.current?.focus(), []);
  return (
    <div
      role="group"
      aria-labelledby={questionId}
      className="flex w-full flex-wrap items-center justify-end gap-x-2 gap-y-1 rounded-control bg-danger-soft px-2.5 py-1.5"
      onKeyDown={(e) => {
        if (e.key === "Escape") {
          e.preventDefault();
          onCancel();
        }
      }}
    >
      <p id={questionId} className="mr-auto min-w-0 text-label text-danger">
        <span className="font-semibold">{question}</span>
        {detail ? <span className="text-ink-2"> {detail}</span> : null}
      </p>
      <Button ref={cancelRef} variant="ghost" size="sm" onClick={onCancel}>
        취소
      </Button>
      <Button variant="danger" size="sm" onClick={onConfirm} aria-label={`${subject} 삭제`}>
        <Trash2 aria-hidden="true" />
        삭제
      </Button>
    </div>
  );
}

function RowAction({
  label,
  tooltip,
  icon: Icon,
  focusKey,
  ...props
}: Omit<React.ComponentProps<typeof Button>, "children"> & {
  label: string;
  tooltip: string;
  icon: LucideIcon;
  focusKey: string;
}) {
  return (
    <Button
      variant="ghost"
      size="icon-sm"
      className="size-7 text-muted hover:text-ink"
      aria-label={label}
      title={tooltip}
      data-focus-key={focusKey}
      {...props}
    >
      <Icon aria-hidden="true" />
    </Button>
  );
}

function CourseOutline({
  sections,
  readOnly = false,
  loading = false,
  selectedLessonId,
  onAddSection,
  onRenameSection,
  onDeleteSection,
  onMoveSection,
  onAddLesson,
  onRenameLesson,
  onDeleteLesson,
  onMoveLesson,
  onSelectLesson,
  headingLevel = 3,
  className,
}: CourseOutlineProps) {
  const Heading = headingLevel === 3 ? "h3" : "h4";
  const rootRef = React.useRef<HTMLDivElement>(null);
  const [editing, setEditing] = React.useState<Target | null>(null);
  const [confirming, setConfirming] = React.useState<Target | null>(null);

  /**
   * Focus to restore after the caller re-renders (a moved row's DOM node is re-inserted and
   * loses focus). Candidates are tried in order: e.g. "move down" falls back to "move up" once
   * the row reaches the bottom and its down button becomes disabled.
   */
  const pendingFocus = React.useRef<string[] | null>(null);
  React.useLayoutEffect(() => {
    const keys = pendingFocus.current;
    if (!keys) return;
    for (const key of keys) {
      const el = rootRef.current?.querySelector<HTMLElement>(`[data-focus-key="${key}"]`);
      if (el && !el.hasAttribute("disabled")) {
        el.focus();
        pendingFocus.current = null;
        return;
      }
    }
  });
  const focusAfter = (...keys: string[]) => {
    pendingFocus.current = keys;
  };

  /** A section or lesson added through this outline opens straight into rename. */
  const pendingNew = React.useRef<Target["kind"] | null>(null);
  const knownIds = React.useRef<Set<string> | null>(null);
  React.useEffect(() => {
    const ids = new Set<string>();
    sections.forEach((s) => {
      ids.add(`section:${s.id}`);
      s.lessons.forEach((l) => ids.add(`lesson:${l.id}`));
    });
    const prev = knownIds.current;
    knownIds.current = ids;
    const kind = pendingNew.current;
    if (!prev || !kind) return;
    const added = [...ids].find((id) => !prev.has(id) && id.startsWith(`${kind}:`));
    if (added) {
      pendingNew.current = null;
      pendingFocus.current = null;
      setEditing({ kind, id: added.slice(kind.length + 1) });
    }
  }, [sections]);

  const startEdit = (target: Target) => {
    pendingFocus.current = null;
    setConfirming(null);
    setEditing(target);
  };
  const startConfirm = (target: Target) => {
    pendingFocus.current = null;
    setEditing(null);
    setConfirming(target);
  };

  const editable = !readOnly;

  if (loading) {
    return (
      <LoadingRegion label="커리큘럼 불러오는 중" className={cn("grid gap-5", className)}>
        {Array.from({ length: 2 }, (_, s) => (
          <div key={s} className="grid gap-3">
            <Skeleton className="h-4 w-2/5" />
            {Array.from({ length: 3 }, (_, l) => (
              <div key={l} className="flex items-center gap-3 pl-7">
                <Skeleton className="size-4" />
                <Skeleton className="h-3.5 flex-1" />
              </div>
            ))}
          </div>
        ))}
      </LoadingRegion>
    );
  }

  if (sections.length === 0) {
    return (
      <div ref={rootRef} className={className}>
        <EmptyState
          icon={ListTree}
          title="아직 섹션이 없습니다"
          description={
            editable
              ? "섹션은 레슨을 주제별로 묶습니다. 첫 섹션을 만든 뒤 영상·문서·텍스트 레슨을 추가하세요."
              : "이 코스에는 아직 섹션과 레슨이 없습니다. 강사가 내용을 추가하면 여기에 보입니다."
          }
          action={
            editable ? (
              <Button
                variant="secondary"
                onClick={() => {
                  pendingNew.current = "section";
                  onAddSection?.();
                }}
              >
                <Plus aria-hidden="true" />첫 섹션 추가
              </Button>
            ) : undefined
          }
        />
      </div>
    );
  }

  return (
    <div ref={rootRef} data-slot="course-outline" className={cn("grid grid-cols-1 gap-4", className)}>
      <ol className="grid grid-cols-1 divide-y divide-line">
        {sections.map((section, sIndex) => {
          const headingId = `outline-section-${section.id}`;
          const isEditing = same(editing, "section", section.id);
          const isConfirming = same(confirming, "section", section.id);
          const first = sIndex === 0;
          const last = sIndex === sections.length - 1;
          return (
            <li key={section.id} className="py-3 first:pt-0 last:pb-0">
              <section aria-labelledby={headingId} className="grid grid-cols-1 gap-1">
                <div className="flex min-h-9 flex-wrap items-center gap-x-2 gap-y-1">
                  <span className="w-5 shrink-0 text-label text-muted tabular" aria-hidden="true">
                    {sIndex + 1}
                  </span>
                  {isEditing ? (
                    <InlineRename
                      initial={section.title}
                      label="섹션 제목"
                      onCommit={(title) => {
                        if (title !== section.title) onRenameSection?.(section.id, title);
                        setEditing(null);
                        focusAfter(`rename-section-${section.id}`);
                      }}
                      onCancel={() => {
                        setEditing(null);
                        focusAfter(`rename-section-${section.id}`);
                      }}
                    />
                  ) : (
                    <Heading
                      id={headingId}
                      className="min-w-0 flex-1 truncate text-body-sm font-semibold text-ink"
                      title={section.title}
                    >
                      <span className="sr-only">섹션 {sIndex + 1}. </span>
                      {section.title}
                    </Heading>
                  )}
                  {isEditing ? (
                    // Keep the landmark labelled while its heading is swapped for the input.
                    <span id={headingId} className="sr-only">
                      {section.title}
                    </span>
                  ) : (
                    <span className="shrink-0 text-caption text-muted tabular">레슨 {section.lessons.length}개</span>
                  )}
                  {editable && !isEditing && !isConfirming ? (
                    <div className="flex shrink-0 items-center">
                      <RowAction
                        icon={ArrowUp}
                        label={`${section.title} 섹션 위로 이동`}
                        tooltip="위로 이동"
                        focusKey={`move-section-${section.id}-up`}
                        disabled={first}
                        onClick={() => {
                          focusAfter(`move-section-${section.id}-up`, `move-section-${section.id}-down`);
                          onMoveSection?.(section.id, "up");
                        }}
                      />
                      <RowAction
                        icon={ArrowDown}
                        label={`${section.title} 섹션 아래로 이동`}
                        tooltip="아래로 이동"
                        focusKey={`move-section-${section.id}-down`}
                        disabled={last}
                        onClick={() => {
                          focusAfter(`move-section-${section.id}-down`, `move-section-${section.id}-up`);
                          onMoveSection?.(section.id, "down");
                        }}
                      />
                      <RowAction
                        icon={Pencil}
                        label={`${section.title} 섹션 이름 바꾸기`}
                        tooltip="이름 바꾸기"
                        focusKey={`rename-section-${section.id}`}
                        onClick={() => startEdit({ kind: "section", id: section.id })}
                      />
                      <RowAction
                        icon={Trash2}
                        label={`${section.title} 섹션 삭제`}
                        tooltip="삭제"
                        focusKey={`delete-section-${section.id}`}
                        onClick={() => startConfirm({ kind: "section", id: section.id })}
                      />
                    </div>
                  ) : null}
                  {isConfirming ? (
                    <ConfirmDelete
                      question="이 섹션을 삭제할까요?"
                      detail={
                        section.lessons.length > 0
                          ? `레슨 ${section.lessons.length}개와 올린 파일도 함께 삭제됩니다.`
                          : undefined
                      }
                      subject={`${section.title} 섹션`}
                      onCancel={() => {
                        setConfirming(null);
                        focusAfter(`delete-section-${section.id}`);
                      }}
                      onConfirm={() => {
                        setConfirming(null);
                        const neighbour = sections[sIndex + 1] ?? sections[sIndex - 1];
                        focusAfter(...(neighbour ? [`rename-section-${neighbour.id}`] : []), "add-section");
                        onDeleteSection?.(section.id);
                      }}
                    />
                  ) : null}
                </div>

                {section.lessons.length > 0 ? (
                  <ol aria-label={`${section.title} 레슨`} className="grid grid-cols-1 gap-0.5 pl-5">
                    {section.lessons.map((lesson, lIndex) => {
                      const type = typeView[lesson.type];
                      const TypeIcon = type.icon;
                      const selected = lesson.id === selectedLessonId;
                      const lEditing = same(editing, "lesson", lesson.id);
                      const lConfirming = same(confirming, "lesson", lesson.id);
                      const lFirst = lIndex === 0;
                      const lLast = lIndex === section.lessons.length - 1;
                      return (
                        <li
                          key={lesson.id}
                          className={cn(
                            "flex min-h-10 flex-wrap items-center gap-x-2 gap-y-1 rounded-control px-2 py-1 transition-colors duration-150",
                            selected ? "bg-accent-soft" : "hover:bg-surface-sunken",
                          )}
                        >
                          {lEditing ? (
                            <>
                              <TypeIcon className="size-4 shrink-0 stroke-[1.75] text-muted" aria-hidden="true" />
                              <InlineRename
                                initial={lesson.title}
                                label="레슨 제목"
                                onCommit={(title) => {
                                  if (title !== lesson.title) onRenameLesson?.(lesson.id, title);
                                  setEditing(null);
                                  focusAfter(`rename-lesson-${lesson.id}`);
                                }}
                                onCancel={() => {
                                  setEditing(null);
                                  focusAfter(`rename-lesson-${lesson.id}`);
                                }}
                              />
                            </>
                          ) : (
                            <button
                              type="button"
                              data-focus-key={`select-lesson-${lesson.id}`}
                              aria-current={selected ? "true" : undefined}
                              onClick={() => onSelectLesson?.(lesson.id)}
                              className="flex min-w-0 flex-1 items-center gap-2 rounded-badge py-1 text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
                            >
                              <TypeIcon className="size-4 shrink-0 stroke-[1.75] text-muted" aria-hidden="true" />
                              <span className="sr-only">{type.label} 레슨: </span>
                              <span
                                className={cn("truncate", selected ? "font-medium text-ink" : "text-ink-2")}
                                title={lesson.title}
                              >
                                {lesson.title}
                              </span>
                            </button>
                          )}
                          {!lEditing && !lConfirming ? <LessonStatusBadge status={lesson.status} /> : null}
                          {editable && !lEditing && !lConfirming ? (
                            <div className="flex shrink-0 items-center">
                              <RowAction
                                icon={ArrowUp}
                                label={`${lesson.title} 위로 이동`}
                                tooltip="위로 이동"
                                focusKey={`move-lesson-${lesson.id}-up`}
                                disabled={lFirst}
                                onClick={() => {
                                  focusAfter(`move-lesson-${lesson.id}-up`, `move-lesson-${lesson.id}-down`);
                                  onMoveLesson?.(lesson.id, "up");
                                }}
                              />
                              <RowAction
                                icon={ArrowDown}
                                label={`${lesson.title} 아래로 이동`}
                                tooltip="아래로 이동"
                                focusKey={`move-lesson-${lesson.id}-down`}
                                disabled={lLast}
                                onClick={() => {
                                  focusAfter(`move-lesson-${lesson.id}-down`, `move-lesson-${lesson.id}-up`);
                                  onMoveLesson?.(lesson.id, "down");
                                }}
                              />
                              <RowAction
                                icon={Pencil}
                                label={`${lesson.title} 이름 바꾸기`}
                                tooltip="이름 바꾸기"
                                focusKey={`rename-lesson-${lesson.id}`}
                                onClick={() => startEdit({ kind: "lesson", id: lesson.id })}
                              />
                              <RowAction
                                icon={Trash2}
                                label={`${lesson.title} 삭제`}
                                tooltip="삭제"
                                focusKey={`delete-lesson-${lesson.id}`}
                                onClick={() => startConfirm({ kind: "lesson", id: lesson.id })}
                              />
                            </div>
                          ) : null}
                          {lConfirming ? (
                            <ConfirmDelete
                              question="이 레슨을 삭제할까요?"
                              detail={lesson.type === "TEXT" ? undefined : "올린 파일도 함께 삭제됩니다."}
                              subject={lesson.title}
                              onCancel={() => {
                                setConfirming(null);
                                focusAfter(`delete-lesson-${lesson.id}`);
                              }}
                              onConfirm={() => {
                                setConfirming(null);
                                const neighbour = section.lessons[lIndex + 1] ?? section.lessons[lIndex - 1];
                                focusAfter(
                                  ...(neighbour ? [`select-lesson-${neighbour.id}`] : []),
                                  `add-lesson-${section.id}`,
                                );
                                onDeleteLesson?.(lesson.id);
                              }}
                            />
                          ) : null}
                        </li>
                      );
                    })}
                  </ol>
                ) : (
                  <p className="pl-7 text-label text-muted">
                    {editable
                      ? "레슨이 없습니다. 아래에서 영상·문서·텍스트 레슨을 추가하세요."
                      : "레슨이 없습니다."}
                  </p>
                )}

                {editable ? (
                  <div className="flex flex-wrap items-center gap-1 pt-1 pl-5">
                    <span className="mr-1 pl-2 text-label text-muted">레슨 추가</span>
                    {lessonTypes.map((t, i) => {
                      const Icon = t.icon;
                      return (
                        <Button
                          key={t.type}
                          variant="ghost"
                          size="sm"
                          data-focus-key={i === 0 ? `add-lesson-${section.id}` : undefined}
                          aria-label={`${section.title}에 ${t.label} 레슨 추가`}
                          onClick={() => {
                            pendingNew.current = "lesson";
                            onAddLesson?.(section.id, t.type);
                          }}
                        >
                          <Icon aria-hidden="true" />
                          {t.label}
                        </Button>
                      );
                    })}
                  </div>
                ) : null}
              </section>
            </li>
          );
        })}
      </ol>

      {editable ? (
        <div>
          <Button
            variant="secondary"
            size="sm"
            data-focus-key="add-section"
            onClick={() => {
              pendingNew.current = "section";
              onAddSection?.();
            }}
          >
            <Plus aria-hidden="true" />
            섹션 추가
          </Button>
        </div>
      ) : null}
    </div>
  );
}

export {
  CourseOutline,
  type CourseOutlineProps,
  type LessonStatus,
  type LessonType,
  type MoveDirection,
  type OutlineLesson,
  type OutlineSection,
};
