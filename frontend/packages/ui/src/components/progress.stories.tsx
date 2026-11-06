import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";

import { Progress } from "./progress";

const meta = {
  title: "Components/Progress",
  component: Progress,
  args: { value: 40, label: "정보보안 기초 진도" },
  decorators: [(Story) => <div className="max-w-md">{Story()}</div>],
} satisfies Meta<typeof Progress>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Percent: Story = {
  play: async ({ canvasElement }) => {
    const bar = within(canvasElement).getByRole("progressbar", { name: "정보보안 기초 진도" });
    await expect(bar).toHaveAttribute("aria-valuenow", "40");
  },
};

export const Lessons: Story = { args: { value: 4, max: 6, valueText: "4/6 레슨" } };

/** Edge values must not break the layout (craft/state-coverage "Edge"). */
export const Edges: Story = {
  render: () => (
    <div className="grid gap-3">
      <Progress value={0} label="시작 전" />
      <Progress value={100} label="완료" />
      <Progress value={150} label="범위 초과 입력" />
      <Progress value={0} max={0} label="레슨 없음" valueText="레슨 없음" />
    </div>
  ),
};
