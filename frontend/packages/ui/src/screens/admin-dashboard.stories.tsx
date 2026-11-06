import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, userEvent, within } from "storybook/test";

import { AdminDashboardScreen } from "./admin-dashboard";
import { edgeLearners } from "./fixtures";

/**
 * Whole screens composed from @lms/ui with SYNTHETIC data (labelled on screen).
 * Each screen has the five states required by docs/design/REVIEW.md.
 */
const meta = {
  title: "Screens/Admin Dashboard",
  component: AdminDashboardScreen,
  parameters: { layout: "fullscreen" },
} satisfies Meta<typeof AdminDashboardScreen>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Summary strip → completion by department → people to act on, overdue first. */
export const Populated: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await expect(canvas.getByRole("heading", { level: 1, name: "교육 현황" })).toBeVisible();
    const remind = canvas.getByRole("button", { name: "박서준에게 리마인더 보내기" });
    await userEvent.click(remind);
    await expect(await canvas.findByText("보냄", {}, { timeout: 2000 })).toBeVisible();
    await expect(canvas.getByText("박서준님에게 리마인더를 보냈습니다.")).toBeInTheDocument();
  },
};

/** Same as Populated without the interaction test — a still frame for reviews and the README. */
export const Overview: Story = {};

export const Loading: Story = { args: { state: "loading" } };

export const Empty: Story = { args: { state: "empty" } };

export const Error: Story = { args: { state: "error" } };

export const Edge: Story = { args: { rows: edgeLearners } };

export const Mobile: Story = {
  globals: { viewport: { value: "mobile2", isRotated: false } },
};
