import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";

import { BrandMark } from "./brand-mark";

const meta = {
  title: "Components/BrandMark",
  component: BrandMark,
  args: { name: "LMS", tone: "paper" },
  argTypes: { tone: { control: "inline-radio", options: ["paper", "shell"] } },
} satisfies Meta<typeof BrandMark>;

export default meta;
type Story = StoryObj<typeof meta>;

/** On light pages: navy square, white mark. The mark is decorative; the name is the text. */
export const Paper: Story = {
  play: async ({ canvasElement }) => {
    await expect(within(canvasElement).getByText("LMS")).toBeVisible();
  },
};

/** On the navigation rail: light square, navy mark. */
export const Shell: Story = {
  args: { tone: "shell" },
  render: (args) => (
    <div className="inline-flex rounded-panel bg-shell px-5 py-4">
      <BrandMark {...args} />
    </div>
  ),
};
