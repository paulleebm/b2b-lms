import * as React from "react";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent, waitFor, within } from "storybook/test";

import { FileUpload, type FileUploadProps, type UploadItem } from "./file-upload";

const MB = 1024 * 1024;
const GB = 1024 * MB;

/** SYNTHETIC files. */
const items: Record<string, UploadItem> = {
  queued: { id: "q", name: "03_사무실_안전수칙.mp4", sizeBytes: 220 * MB, kind: "video", state: "queued" },
  uploading: {
    id: "u",
    name: "02_피싱메일_구별하기.mp4",
    sizeBytes: 1.3 * GB,
    kind: "video",
    state: "uploading",
    progress: 35,
    partsDone: 47,
    partsTotal: 134,
  },
  verifying: { id: "v", name: "01_왜_정보보안인가.mp4", sizeBytes: 486 * MB, kind: "video", state: "verifying" },
  processing: { id: "p", name: "04_사고_신고_절차.mp4", sizeBytes: 640 * MB, kind: "video", state: "processing" },
  ready: { id: "r", name: "보안정책_요약_v3.pdf", sizeBytes: 2.4 * MB, kind: "document", state: "ready" },
  failed: {
    id: "f",
    name: "사례4_원본.mp4",
    sizeBytes: 912 * MB,
    kind: "video",
    state: "failed",
    error: "네트워크 연결이 끊겨 조각 12개를 보내지 못했습니다. 다시 시도하면 남은 조각만 보냅니다.",
  },
  canceled: { id: "c", name: "05_임시본.mp4", sizeBytes: 75 * MB, kind: "video", state: "canceled" },
};

const meta = {
  title: "Components/FileUpload",
  component: FileUpload,
  args: {
    label: "영상 파일",
    accept: "video/mp4,video/quicktime,video/webm",
    acceptLabel: "MP4, MOV, WebM",
    maxSizeLabel: "5 GB",
    items: [],
    onFilesSelected: fn(),
    onCancel: fn(),
    onRetry: fn(),
    onRemove: fn(),
  },
  decorators: [(Story) => <div className="max-w-xl">{Story()}</div>],
} satisfies Meta<typeof FileUpload>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Idle. The dropzone is a real button: Tab reaches it, Enter opens the native picker. */
export const Idle: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    const picker = canvas.getByRole("button", { name: /영상 파일 선택/ });
    await expect(picker).toHaveAccessibleName(/^영상 파일 선택 또는 여기에 끌어다 놓기\s*MP4, MOV, WebM · 최대 5 GB$/);
    await userEvent.tab();
    await expect(picker).toHaveFocus();

    const input = canvasElement.querySelector<HTMLInputElement>('input[type="file"]')!;
    const file = new File(["synthetic"], "01_오리엔테이션.mp4", { type: "video/mp4" });
    await userEvent.upload(input, file);
    await expect(args.onFilesSelected).toHaveBeenCalledWith([file]);
  },
};

/** Every row state at once: queued, uploading (bytes + parts), verifying, transcoding, ready, failed, canceled. */
export const AllStates: Story = {
  args: { multiple: true, items: Object.values(items) },
};

/** Uploading rows offer 취소; the callback receives the item id. */
export const Uploading: Story = {
  args: { items: [items.uploading!, items.queued!], multiple: true },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await expect(canvas.getByRole("progressbar", { name: "02_피싱메일_구별하기.mp4 업로드 진행률" })).toHaveAttribute(
      "aria-valuenow",
      "35",
    );
    await userEvent.click(canvas.getByRole("button", { name: "02_피싱메일_구별하기.mp4 업로드 취소" }));
    await expect(args.onCancel).toHaveBeenCalledWith("u");
  },
};

/** The server is transcoding: no percentage exists, so none is shown. */
export const Processing: Story = {
  args: { items: [items.processing!], showDropzone: false },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await expect(canvas.getByText("영상 변환 중")).toBeVisible();
    await expect(canvas.queryByRole("progressbar")).toBeNull();
  },
};

/** Failure shows the cause in words, then 다시 시도. */
export const Failed: Story = {
  args: { items: [items.failed!] },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await expect(canvas.getByText(/조각 12개를 보내지 못했습니다/)).toBeVisible();
    await userEvent.click(canvas.getByRole("button", { name: "사례4_원본.mp4 다시 시도" }));
    await expect(args.onRetry).toHaveBeenCalledWith("f");
  },
};

/** Dropzone-level error (e.g. a rejected type). Replaces the hint and is wired as the description. */
export const RejectedFile: Story = {
  args: { error: "PDF는 영상 레슨에 올릴 수 없습니다. MP4, MOV, WebM 파일을 고르세요." },
  play: async ({ canvasElement }) => {
    const picker = within(canvasElement).getByRole("button", { name: /영상 파일 선택/ });
    await expect(picker).toHaveAccessibleDescription(
      "PDF는 영상 레슨에 올릴 수 없습니다. MP4, MOV, WebM 파일을 고르세요.",
    );
  },
};

/** Disabled always says why. */
export const Disabled: Story = {
  args: { disabled: true, disabledReason: "게시된 코스의 영상은 새 버전을 만든 뒤에 바꿀 수 있습니다." },
};

/** Edge: a 90-character file name, 4.8 GB, 490 parts. Name truncates with a title; numbers stay tabular. */
export const Edge: Story = {
  args: {
    items: [
      {
        id: "e",
        name: "2026_하반기_전사필수_정보보안_개인정보보호_통합교육_재이수자대상_협력사상주인력포함_최종_수정본_v12_진짜최종.mp4",
        sizeBytes: 4.8 * GB,
        kind: "video",
        state: "uploading",
        progress: 99,
        partsDone: 486,
        partsTotal: 492,
      },
    ],
  },
};

/** Stateful demo: picking a file adds a queued row, and the change is announced politely. */
function StatefulUpload(props: FileUploadProps) {
  const [list, setList] = React.useState<UploadItem[]>([]);
  return (
    <FileUpload
      {...props}
      items={list}
      onFilesSelected={(files) => {
        props.onFilesSelected(files);
        setList((prev) => [
          ...prev,
          ...files.map((f, i): UploadItem => ({
            id: `${f.name}-${i}`,
            name: f.name,
            sizeBytes: f.size,
            kind: "video",
            state: "queued",
          })),
        ]);
      }}
      onCancel={(id) => setList((prev) => prev.map((x) => (x.id === id ? { ...x, state: "canceled" } : x)))}
      onRemove={(id) => setList((prev) => prev.filter((x) => x.id !== id))}
    />
  );
}

export const AnnouncesChanges: Story = {
  render: (args) => <StatefulUpload {...args} />,
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const input = canvasElement.querySelector<HTMLInputElement>('input[type="file"]')!;
    await userEvent.upload(input, new File(["synthetic"], "06_마무리.mp4", { type: "video/mp4" }));
    const status = canvas.getByRole("status");
    await waitFor(() => expect(status).toHaveTextContent("06_마무리.mp4 파일을 업로드 대기열에 추가했습니다."));
    await userEvent.click(canvas.getByRole("button", { name: "06_마무리.mp4 업로드 취소" }));
    await waitFor(() => expect(status).toHaveTextContent("06_마무리.mp4 업로드를 취소했습니다."));
  },
};
