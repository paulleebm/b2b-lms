import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent, within } from "storybook/test";

import { courseBlocked, courseEdge, courseNew } from "./fixtures";
import { StudioCourseEditorScreen } from "./studio-course-editor";

/** Instructor "코스 편집" with SYNTHETIC data (labelled on screen). */
const meta = {
  title: "Screens/Studio Course Editor",
  component: StudioCourseEditorScreen,
  parameters: { layout: "fullscreen" },
  args: { onSave: fn(), onPublish: fn() },
} satisfies Meta<typeof StudioCourseEditorScreen>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Everything uploaded and transcoded: 게시 is enabled. Selecting a lesson opens its detail. */
export const Populated: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await expect(canvas.getByRole("heading", { level: 1, name: "코스 편집" })).toBeVisible();
    await userEvent.click(canvas.getByRole("button", { name: /비밀번호 관리 수칙$/ }));
    await expect(canvas.getByRole("textbox", { name: "본문" })).toBeVisible();
    const publish = canvas.getByRole("button", { name: "게시" });
    await expect(publish).toBeEnabled();
    await userEvent.click(publish);
    await expect(args.onPublish).toHaveBeenCalledOnce();
  },
};

/** A video still transcoding and a document without a file: 게시 is disabled and says why. */
export const PublishBlocked: Story = {
  args: { course: courseBlocked },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const publish = canvas.getByRole("button", { name: "게시" });
    await expect(publish).toBeDisabled();
    await expect(publish).toHaveAccessibleDescription(/게시하려면 2가지를 해결하세요/);
    // Each reason jumps to its lesson.
    await userEvent.click(canvas.getByRole("button", { name: "보안 서약서" }));
    await expect(canvas.getByRole("textbox", { name: "레슨 제목" })).toHaveValue("보안 서약서");
    await expect(canvas.getByRole("button", { name: /문서 파일 선택/ })).toBeVisible();
  },
};

/** Optimistic-lock 409: someone saved first. The edits stay on screen. */
export const Conflict: Story = {
  args: { state: "conflict" },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const message = canvas.getByText("다른 사람이 먼저 수정했습니다. 새로고침 후 다시 저장하세요.");
    await expect(message.closest('[role="alert"]')).not.toBeNull();
    await expect(canvas.getByRole("textbox", { name: "코스 제목" })).toHaveValue("정보보안 기초");
  },
};

export const Loading: Story = { args: { state: "loading" } };

/** A course created a minute ago: no sections yet, so the outline explains what a section is. */
export const Empty: Story = { args: { course: courseNew } };

export const Error: Story = { args: { state: "error" } };

/** Published course with an 80-character title, 12 lessons, long names and a failed transcode. */
export const Edge: Story = { args: { course: courseEdge } };

export const Mobile: Story = {
  args: { course: courseBlocked },
  globals: { viewport: { value: "mobile2", isRotated: false } },
};
