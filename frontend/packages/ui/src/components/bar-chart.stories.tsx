import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, userEvent, within } from "storybook/test";

import { departmentCompletion } from "../screens/fixtures";
import { BarChart } from "./bar-chart";
import { Panel, PanelBody, PanelHeader } from "./panel";

const meta = {
  title: "Components/BarChart",
  component: BarChart,
  args: { title: "부서별 수료율", data: departmentCompletion },
  decorators: [
    (Story) => (
      <Panel className="max-w-xl">
        <PanelHeader title="부서별 수료율" description="필수 교육 3개 합산 · 합성 데이터" />
        <PanelBody>{Story()}</PanelBody>
      </Panel>
    ),
  ],
} satisfies Meta<typeof BarChart>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

/** The same data as a real table, for screen readers and for copying into a report. */
export const TableView: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const toggle = canvas.getByRole("button", { name: "표로 보기" });
    await userEvent.click(toggle);
    await expect(canvas.getByRole("button", { name: "차트로 보기" })).toHaveAttribute("aria-pressed", "true");
    await expect(canvas.getByRole("table", { name: "부서별 수료율" })).toBeVisible();
    await expect(canvas.getByRole("rowheader", { name: "개발팀" })).toBeVisible();
  },
};

export const LongLabels: Story = {
  args: {
    data: [
      { label: "글로벌 전략기획본부 디지털트랜스포메이션 추진단", value: 64, overdue: 12, detail: "16/25" },
      { label: "R&D", value: 100, detail: "4/4" },
      { label: "신규 입사자", value: 0, overdue: 0, detail: "0/9" },
    ],
  },
};
