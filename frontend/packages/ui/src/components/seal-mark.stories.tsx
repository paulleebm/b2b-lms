import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";

import { SealMark } from "./seal-mark";

const meta = {
  title: "Components/SealMark",
  component: SealMark,
  args: { size: "lg", completedAt: "2026. 11. 6." },
  argTypes: { size: { control: "inline-radio", options: ["sm", "md", "lg"] } },
} satisfies Meta<typeof SealMark>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Large: Story = {
  play: async ({ canvasElement }) => {
    await expect(within(canvasElement).getByRole("img", { name: "수료 (2026. 11. 6.)" })).toBeVisible();
  },
};

export const Medium: Story = { args: { size: "md" } };

export const Small: Story = { args: { size: "sm" } };

/** The one authored motion in the product: settles from 1.12× in 200ms. Reduced motion: no movement. */
export const Stamping: Story = { args: { stamp: true } };

export const InContext: Story = {
  render: (args) => (
    <div className="flex max-w-md items-center gap-3 rounded-panel border border-line bg-surface p-4">
      <SealMark {...args} size="md" />
      <div>
        <p className="text-body font-medium">개인정보보호 교육</p>
        <p className="text-label text-muted">2026. 10. 28. 수료</p>
      </div>
    </div>
  ),
};
