import type { Meta, StoryObj } from "@storybook/react-vite";
import { ClipboardList, Plus } from "lucide-react";
import { expect, fn, userEvent, within } from "storybook/test";

import { Button } from "./button";
import { Panel } from "./panel";
import { EmptyState, ErrorState, LoadingRegion, Skeleton } from "./states";

const meta = {
  title: "Components/States",
  component: EmptyState,
  args: { title: "", description: "" },
  decorators: [(Story) => <Panel className="max-w-2xl">{Story()}</Panel>],
} satisfies Meta<typeof EmptyState>;

export default meta;
type Story = StoryObj<typeof meta>;

/** First-use empty is an onboarding moment: headline + why + the next action. */
export const FirstUseEmpty: Story = {
  args: {
    icon: ClipboardList,
    title: "아직 배정한 교육이 없습니다",
    description: "부서나 직원에게 교육을 배정하면 수료 현황과 마감이 지난 사람을 바로 볼 수 있습니다.",
    action: (
      <Button>
        <Plus aria-hidden="true" />첫 교육 배정하기
      </Button>
    ),
  },
};

/** No-results empty echoes the query and suggests a way out. */
export const NoResults: Story = {
  args: {
    title: "“재무본부”와 일치하는 부서가 없습니다",
    description: "부서 이름을 다시 확인하거나 필터를 초기화해 보세요.",
    action: <Button variant="secondary">필터 초기화</Button>,
  },
};

const onRetry = fn();

/** What happened → why → what to do, plus a support code. */
export const ErrorWithRetry: Story = {
  render: () => (
    <ErrorState
      title="멤버 목록을 불러오지 못했습니다"
      description="서버가 응답하지 않습니다. 잠시 후 다시 시도해 주세요."
      traceId="4bf92f3577b34da6a3ce929d0e0e4736"
      onRetry={onRetry}
    />
  ),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await expect(canvas.getByRole("alert")).toBeVisible();
    await userEvent.click(canvas.getByRole("button", { name: "다시 시도" }));
    await expect(onRetry).toHaveBeenCalled();
  },
};

export const Loading: Story = {
  render: () => (
    <LoadingRegion label="멤버 목록 불러오는 중" className="grid gap-3 p-5">
      <Skeleton className="h-4 w-1/3" />
      <Skeleton className="h-4 w-2/3" />
      <Skeleton className="h-4 w-1/2" />
    </LoadingRegion>
  ),
};

/** After ~15s the region says so instead of spinning forever (craft/state-coverage). */
export const LoadingSlow: Story = {
  render: () => (
    <LoadingRegion label="보고서 만드는 중" slow className="grid gap-3 p-5">
      <Skeleton className="h-4 w-1/3" />
      <Skeleton className="h-4 w-2/3" />
    </LoadingRegion>
  ),
};
