import type { Meta, StoryObj } from "@storybook/react-vite";
import { Plus, Trash2 } from "lucide-react";
import { expect, fn, userEvent, within } from "storybook/test";

import { Button } from "./button";

const meta = {
  title: "Components/Button",
  component: Button,
  args: { children: "교육 배정", onClick: fn() },
  argTypes: {
    variant: { control: "select", options: ["primary", "secondary", "ghost", "danger", "link"] },
    size: { control: "select", options: ["sm", "md", "lg"] },
  },
} satisfies Meta<typeof Button>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Primary: Story = {
  play: async ({ canvasElement, args }) => {
    const button = within(canvasElement).getByRole("button", { name: "교육 배정" });
    await userEvent.click(button);
    await expect(args.onClick).toHaveBeenCalledOnce();
  },
};

export const Variants: Story = {
  render: (args) => (
    <div className="flex flex-wrap items-center gap-3">
      <Button {...args} variant="primary">
        <Plus aria-hidden="true" />
        교육 배정
      </Button>
      <Button {...args} variant="secondary">
        취소
      </Button>
      <Button {...args} variant="ghost">
        필터 초기화
      </Button>
      <Button {...args} variant="danger">
        <Trash2 aria-hidden="true" />
        멤버 비활성화
      </Button>
      <Button {...args} variant="link">
        수료증 진위 확인
      </Button>
    </div>
  ),
};

export const Sizes: Story = {
  render: (args) => (
    <div className="flex items-center gap-3">
      <Button {...args} size="sm">
        작게
      </Button>
      <Button {...args} size="md">
        기본
      </Button>
      <Button {...args} size="lg">
        이어서 학습하기
      </Button>
    </div>
  ),
};

/** Label stays, width does not jump, clicks are locked, aria-busy announces work in progress. */
export const Loading: Story = {
  args: { loading: true, children: "저장 중" },
  play: async ({ canvasElement, args }) => {
    const button = within(canvasElement).getByRole("button", { name: "저장 중" });
    await expect(button).toBeDisabled();
    await expect(button).toHaveAttribute("aria-busy", "true");
    await userEvent.click(button, { pointerEventsCheck: 0 });
    await expect(args.onClick).not.toHaveBeenCalled();
  },
};

export const Disabled: Story = {
  render: (args) => (
    <div className="flex items-center gap-3">
      <Button {...args} disabled>
        게시
      </Button>
      <span className="text-label text-muted">영상 변환이 끝나면 게시할 수 있습니다</span>
    </div>
  ),
};

export const AsLink: Story = {
  render: () => (
    <Button asChild variant="secondary">
      <a href="#certificates">수료증 보기</a>
    </Button>
  ),
};
