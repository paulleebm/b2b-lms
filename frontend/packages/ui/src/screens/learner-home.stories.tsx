import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";

import { LearnerHomeScreen } from "./learner-home";

const meta = {
  title: "Screens/Learner Home",
  component: LearnerHomeScreen,
  parameters: { layout: "fullscreen" },
} satisfies Meta<typeof LearnerHomeScreen>;

export default meta;
type Story = StoryObj<typeof meta>;

/** One next task on top (Hick's law), then the rest; completed courses carry the seal. */
export const Populated: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await expect(canvas.getByRole("button", { name: "이어서 학습하기" })).toBeVisible();
    await expect(canvas.getAllByRole("img", { name: /^수료/ }).length).toBeGreaterThan(0);
  },
};

/** The product's single authored moment: the seal is stamped once. */
export const JustCompleted: Story = { args: { state: "just-completed" } };

export const Loading: Story = { args: { state: "loading" } };

export const Empty: Story = { args: { state: "empty" } };

export const Error: Story = { args: { state: "error" } };

export const Edge: Story = {
  args: {
    courses: [
      {
        id: "x",
        title: "2026년 하반기 전사 필수 정보보안 및 개인정보보호 통합 교육 (재이수자 대상)",
        status: { kind: "overdue" },
        dueAt: "2026-11-01",
        lessonsDone: 0,
        lessonsTotal: 24,
        nextLesson: "1. 왜 다시 듣게 되었나요: 지난 교육 이후 바뀐 사내 보안 규정과 사고 사례",
      },
    ],
    completed: [],
  },
};

/** Everything assigned is done: no "next up", the completed list stays visible (regression: it used to show the empty state). */
export const AllCompleted: Story = {
  args: { courses: [] },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await expect(canvas.getByText("지금 들어야 할 교육이 없습니다")).toBeVisible();
    await expect(canvas.getByRole("heading", { name: "수료한 교육" })).toBeVisible();
  },
};
