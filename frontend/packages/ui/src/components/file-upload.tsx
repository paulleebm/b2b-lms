"use client";

import * as React from "react";
import {
  CircleAlert,
  CircleCheck,
  CircleDashed,
  CircleSlash,
  CloudUpload,
  FileText,
  LoaderCircle,
  RotateCcw,
  Video,
  X,
  type LucideIcon,
} from "lucide-react";

import { cn } from "../lib/utils";
import { Button } from "./button";
import { Progress } from "./progress";
import { Badge } from "./status-badge";

/**
 * Lesson file upload (DESIGN.md §4, §9). Presentational: the caller owns the upload (S3
 * multipart, presigned parts) and feeds progress back through `items`. This component only
 * picks files, shows each file's state, and offers the next action for that state.
 *
 * - The dropzone IS a button: Tab reaches it, Enter/Space opens the native picker. Dragging
 *   is a shortcut, never the only way in.
 * - State changes (not every percent) are announced in a polite live region (§9).
 * - "processing" is the server transcoding a video: there is no honest percentage, so none is shown.
 */

type UploadState = "queued" | "uploading" | "verifying" | "processing" | "ready" | "failed" | "canceled";

type UploadItem = {
  id: string;
  name: string;
  sizeBytes: number;
  kind: "video" | "document";
  state: UploadState;
  /** 0–100, for "uploading". */
  progress?: number;
  /** Multipart progress, e.g. 4 of 12 parts. */
  partsDone?: number;
  partsTotal?: number;
  /** Failure cause in plain Korean: what happened and, if known, why. */
  error?: string;
};

type FileUploadProps = {
  /** Visible title of the dropzone, e.g. "영상 파일". */
  label?: string;
  /** Passed to the native input, e.g. "video/mp4,video/quicktime". */
  accept?: string;
  /** Human form of `accept`, e.g. "MP4, MOV". */
  acceptLabel?: string;
  /** e.g. "5 GB". The caller enforces it and reports violations through `error`. */
  maxSizeLabel?: string;
  multiple?: boolean;
  onFilesSelected: (files: File[]) => void;
  items: UploadItem[];
  onCancel?: (id: string) => void;
  onRetry?: (id: string) => void;
  onRemove?: (id: string) => void;
  disabled?: boolean;
  /** Shown under the dropzone while disabled. A disabled control always says why (§4). */
  disabledReason?: string;
  /** Dropzone-level error, e.g. a rejected file type. Replaces the hint. */
  error?: string;
  /** Hide the dropzone (e.g. single-file lesson that already has its file). */
  showDropzone?: boolean;
  className?: string;
};

const numberFormat = new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 1 });

/** 1_288_490_189 → "1.2 GB", 52_428_800 → "50 MB", 820_000 → "801 KB". Binary units, Korean grouping. */
function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) return "—";
  const KB = 1024;
  const MB = KB * 1024;
  const GB = MB * 1024;
  if (bytes >= GB) return `${numberFormat.format(bytes / GB)} GB`;
  if (bytes >= MB) return `${numberFormat.format(bytes / MB)} MB`;
  if (bytes >= KB) return `${Math.round(bytes / KB).toLocaleString("ko-KR")} KB`;
  return `${bytes.toLocaleString("ko-KR")} B`;
}

const stateView: Record<UploadState, { tone: "neutral" | "info" | "success" | "danger"; text: string; icon: LucideIcon; spin?: boolean }> = {
  queued: { tone: "neutral", text: "대기 중", icon: CircleDashed },
  uploading: { tone: "info", text: "업로드 중", icon: CloudUpload },
  verifying: { tone: "info", text: "확인 중", icon: LoaderCircle, spin: true },
  processing: { tone: "info", text: "영상 변환 중", icon: LoaderCircle, spin: true },
  ready: { tone: "success", text: "준비됨", icon: CircleCheck },
  failed: { tone: "danger", text: "실패", icon: CircleAlert },
  canceled: { tone: "neutral", text: "취소됨", icon: CircleSlash },
};

function announcementFor(item: UploadItem): string {
  switch (item.state) {
    case "queued":
      return `${item.name} 파일을 업로드 대기열에 추가했습니다.`;
    case "uploading":
      return `${item.name} 업로드를 시작했습니다.`;
    case "verifying":
      return `${item.name} 업로드를 마치고 파일을 확인하고 있습니다.`;
    case "processing":
      return `${item.name} 영상을 변환하고 있습니다. 몇 분 걸릴 수 있습니다.`;
    case "ready":
      return `${item.name} 파일이 준비되었습니다.`;
    case "failed":
      return `${item.name} 업로드에 실패했습니다. ${item.error ?? ""}`.trim();
    case "canceled":
      return `${item.name} 업로드를 취소했습니다.`;
  }
}

function UploadStateBadge({ state }: { state: UploadState }) {
  const view = stateView[state];
  const Icon = view.icon;
  return (
    <Badge tone={view.tone}>
      <Icon className={cn("size-3 stroke-[2]", view.spin && "animate-spin")} aria-hidden="true" />
      {view.text}
    </Badge>
  );
}

function UploadRow({
  item,
  onCancel,
  onRetry,
  onRemove,
}: {
  item: UploadItem;
  onCancel?: (id: string) => void;
  onRetry?: (id: string) => void;
  onRemove?: (id: string) => void;
}) {
  const KindIcon = item.kind === "video" ? Video : FileText;
  const progress = Math.min(Math.max(item.progress ?? 0, 0), 100);
  const uploadedBytes = Math.round((item.sizeBytes * progress) / 100);
  const hasParts = item.partsTotal !== undefined && item.partsTotal > 0;

  let meta: React.ReactNode;
  switch (item.state) {
    case "uploading":
      meta = (
        <>
          {formatBytes(uploadedBytes)} / {formatBytes(item.sizeBytes)}
          {hasParts ? ` · 조각 ${item.partsDone ?? 0}/${item.partsTotal}` : null}
        </>
      );
      break;
    case "verifying":
      meta = <>{formatBytes(item.sizeBytes)} · 서버에서 파일을 확인하고 있습니다</>;
      break;
    case "processing":
      meta = <>{formatBytes(item.sizeBytes)} · 변환이 끝나야 학습자가 볼 수 있습니다. 보통 몇 분 걸립니다</>;
      break;
    default:
      meta = formatBytes(item.sizeBytes);
  }

  const canCancel = item.state === "queued" || item.state === "uploading";
  const canRemove = item.state === "ready" || item.state === "failed" || item.state === "canceled";

  return (
    <li data-state={item.state} className="grid grid-cols-[auto_minmax(0,1fr)_auto] items-start gap-x-3 gap-y-1 px-4 py-3">
      <KindIcon className="mt-0.5 size-4 shrink-0 stroke-[1.75] text-muted" aria-hidden="true" />
      <div className="grid min-w-0 gap-1">
        <div className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
          <p className="min-w-0 truncate font-medium text-ink" title={item.name}>
            {item.name}
          </p>
          <UploadStateBadge state={item.state} />
        </div>
        <p className="text-label text-muted tabular">{meta}</p>
        {item.state === "uploading" ? (
          <Progress value={progress} label={`${item.name} 업로드 진행률`} className="max-w-md" />
        ) : null}
        {item.state === "failed" && item.error ? (
          <p className="flex items-start gap-1.5 text-label text-danger">
            <CircleAlert className="mt-0.5 size-3.5 shrink-0 stroke-[1.75]" aria-hidden="true" />
            <span>{item.error}</span>
          </p>
        ) : null}
      </div>
      <div className="flex shrink-0 items-center gap-1">
        {canCancel && onCancel ? (
          <Button variant="ghost" size="sm" onClick={() => onCancel(item.id)} aria-label={`${item.name} 업로드 취소`}>
            취소
          </Button>
        ) : null}
        {item.state === "failed" && onRetry ? (
          <Button variant="secondary" size="sm" onClick={() => onRetry(item.id)} aria-label={`${item.name} 다시 시도`}>
            <RotateCcw aria-hidden="true" />
            다시 시도
          </Button>
        ) : null}
        {canRemove && onRemove ? (
          <Button
            variant="ghost"
            size="icon-sm"
            onClick={() => onRemove(item.id)}
            aria-label={`${item.name} 목록에서 지우기`}
            title="목록에서 지우기"
          >
            <X aria-hidden="true" />
          </Button>
        ) : null}
      </div>
    </li>
  );
}

function FileUpload({
  label = "파일",
  accept,
  acceptLabel,
  maxSizeLabel,
  multiple = false,
  onFilesSelected,
  items,
  onCancel,
  onRetry,
  onRemove,
  disabled = false,
  disabledReason,
  error,
  showDropzone = true,
  className,
}: FileUploadProps) {
  const inputRef = React.useRef<HTMLInputElement>(null);
  const reactId = React.useId();
  const errorId = `${reactId}-error`;
  const reasonId = `${reactId}-reason`;
  const [dragOver, setDragOver] = React.useState(false);
  const [announcement, setAnnouncement] = React.useState("");

  // Announce transitions only, never the initial list or every percent tick.
  const previous = React.useRef<Map<string, UploadState> | null>(null);
  React.useEffect(() => {
    const prev = previous.current;
    const next = new Map(items.map((i) => [i.id, i.state] as const));
    previous.current = next;
    if (!prev) return;
    const changed = items.filter((i) => prev.get(i.id) !== i.state);
    if (changed.length > 0) setAnnouncement(changed.map(announcementFor).join(" "));
  }, [items]);

  const emit = (list: FileList | null) => {
    if (disabled || !list || list.length === 0) return;
    const files = Array.from(list);
    // Type and size rules belong to the caller (and the server): it reports a rejected file
    // through `error` or a failed row, in words, instead of silently dropping it.
    onFilesSelected(multiple ? files : files.slice(0, 1));
  };

  const onDragOver = (e: React.DragEvent) => {
    if (disabled) return;
    e.preventDefault();
    e.dataTransfer.dropEffect = "copy";
    if (!dragOver) setDragOver(true);
  };
  const onDragLeave = (e: React.DragEvent) => {
    if (e.currentTarget.contains(e.relatedTarget as Node | null)) return;
    setDragOver(false);
  };
  const onDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setDragOver(false);
    emit(e.dataTransfer.files);
  };

  const hint = [acceptLabel, maxSizeLabel ? `최대 ${maxSizeLabel}` : null].filter(Boolean).join(" · ");
  // The hint sits inside the button, so it is already part of the accessible name; only text
  // outside the button (error, disabled reason) needs aria-describedby.
  const describedBy = [error ? errorId : null, disabled && disabledReason ? reasonId : null]
    .filter(Boolean)
    .join(" ");

  return (
    <div data-slot="file-upload" className={cn("grid gap-3", className)}>
      {showDropzone ? (
        <div className="grid gap-1.5">
          <button
            type="button"
            disabled={disabled}
            aria-describedby={describedBy || undefined}
            onClick={() => inputRef.current?.click()}
            onDragEnter={onDragOver}
            onDragOver={onDragOver}
            onDragLeave={onDragLeave}
            onDrop={onDrop}
            data-drag-over={dragOver || undefined}
            className={cn(
              "flex w-full flex-col items-center gap-1.5 rounded-panel border border-dashed border-field-border bg-surface px-6 py-6 text-center",
              "transition-colors duration-150 ease-standard hover:bg-surface-sunken",
              "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent",
              "disabled:cursor-not-allowed disabled:bg-surface-sunken disabled:hover:bg-surface-sunken",
              dragOver && "border-solid border-accent bg-accent-soft hover:bg-accent-soft",
              error && !dragOver && "border-danger",
            )}
          >
            <CloudUpload
              className={cn("size-5 stroke-[1.75]", dragOver ? "text-accent" : "text-muted")}
              aria-hidden="true"
            />
            <span className={cn("font-semibold", disabled ? "text-muted" : "text-ink")}>
              {dragOver ? (
                "놓으면 바로 올립니다"
              ) : (
                <>
                  {label} 선택{" "}
                  <span className="font-normal text-muted">또는 여기에 끌어다 놓기</span>
                </>
              )}
            </span>
            {hint && !error ? (
              <span className="text-label text-muted tabular">
                {hint}
              </span>
            ) : null}
          </button>
          {error ? (
            <p id={errorId} className="flex items-start gap-1.5 text-label text-danger">
              <CircleAlert className="mt-0.5 size-3.5 shrink-0 stroke-[1.75]" aria-hidden="true" />
              <span>{error}</span>
            </p>
          ) : null}
          {disabled && disabledReason ? (
            <p id={reasonId} className="text-label text-muted">
              {disabledReason}
            </p>
          ) : null}
          <input
            ref={inputRef}
            type="file"
            hidden
            tabIndex={-1}
            accept={accept}
            multiple={multiple}
            disabled={disabled}
            onChange={(e) => {
              emit(e.currentTarget.files);
              // Reset so choosing the same file again (after a failure) still fires change.
              e.currentTarget.value = "";
            }}
          />
        </div>
      ) : null}

      {items.length > 0 ? (
        <ul aria-label={`${label} 업로드 목록`} className="divide-y divide-line rounded-panel border border-line bg-surface">
          {items.map((item) => (
            <UploadRow key={item.id} item={item} onCancel={onCancel} onRetry={onRetry} onRemove={onRemove} />
          ))}
        </ul>
      ) : null}

      <p className="sr-only" role="status" aria-live="polite">
        {announcement}
      </p>
    </div>
  );
}

export { FileUpload, formatBytes, type FileUploadProps, type UploadItem, type UploadState };
