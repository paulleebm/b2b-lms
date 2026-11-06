import type { Meta, StoryObj } from "@storybook/react-vite";

import { Button } from "./button";
import { Notice } from "./notice";

const meta = {
  title: "Components/Notice",
  component: Notice,
  args: { title: "안내", tone: "info" },
  decorators: [(Story) => <div className="max-w-xl">{Story()}</div>],
} satisfies Meta<typeof Notice>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Tones: Story = {
  render: () => (
    <div className="grid gap-3">
      <Notice tone="info" title="영상을 변환하고 있습니다">
        보통 몇 분 걸립니다. 변환이 끝나면 코스를 게시할 수 있습니다.
      </Notice>
      <Notice tone="success" title="부서를 저장했습니다" />
      <Notice tone="warning" title="마감이 2일 남은 교육이 있습니다">
        정보보안 기초 · 11월 8일 마감
      </Notice>
      <Notice
        tone="danger"
        title="업로드에 실패했습니다"
        action={
          <Button size="sm" variant="secondary">
            다시 시도
          </Button>
        }
      >
        네트워크 연결이 끊겼습니다. 이미 올라간 부분은 유지되니 이어서 올릴 수 있습니다.
      </Notice>
    </div>
  ),
};

/** Long copy wraps; the icon stays on the first line. */
export const LongText: Story = {
  args: {
    tone: "warning",
    title: "2026년 하반기 전사 필수 정보보안 및 개인정보보호 통합 교육의 마감일이 다가오고 있습니다",
    children:
      "재이수 대상자 포함 64명 중 11명이 아직 시작하지 않았습니다. 교육 배정 화면에서 마감일을 조정하거나 리마인더를 보낼 수 있습니다.",
  },
};
