import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";

import { CompletionMark } from "./completion-mark";

const meta = {
  title: "Components/CompletionMark",
  component: CompletionMark,
  args: { size: "lg", completedAt: "2026. 11. 6." },
  argTypes: { size: { control: "inline-radio", options: ["md", "lg"] } },
} satisfies Meta<typeof CompletionMark>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Large: Story = {
  play: async ({ canvasElement }) => {
    await expect(within(canvasElement).getByRole("img", { name: "수료 (2026. 11. 6.)" })).toBeVisible();
  },
};

export const Medium: Story = { args: { size: "md" } };

export const InContext: Story = {
  render: (args) => (
    <div className="flex items-center gap-4 rounded-panel border border-line bg-surface px-5 py-4">
      <CompletionMark {...args} size="md" />
      <div className="grid gap-0.5">
        <p className="text-body font-medium text-ink">정보보안 기초</p>
        <p className="text-label text-muted">2026. 11. 6. 수료 · 수료번호 LMS-2026-XFR9FVPG</p>
      </div>
    </div>
  ),
};
