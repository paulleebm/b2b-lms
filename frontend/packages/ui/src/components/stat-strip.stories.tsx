import type { Meta, StoryObj } from "@storybook/react-vite";

import { StatStrip } from "./stat-strip";

const meta = {
  title: "Components/StatStrip",
  component: StatStrip,
  args: {
    stats: [
      { label: "대상자", value: 64, unit: "명", context: "필수 교육 3개" },
      { label: "수료", value: 45, unit: "명", context: "지난주 대비 +6명" },
      { label: "진행 중", value: 11, unit: "명", context: "평균 진도 52%" },
      { label: "마감 지남", value: 8, unit: "명", context: "리마인더 대상", emphasis: "danger" },
    ],
  },
} satisfies Meta<typeof StatStrip>;

export default meta;
type Story = StoryObj<typeof meta>;

/** One strip split by rules, every number with its context. Not four identical cards. */
export const Default: Story = {};

export const Loading: Story = { args: { loading: true } };

export const LargeNumbers: Story = {
  args: {
    stats: [
      { label: "대상자", value: 12480, unit: "명", context: "전 계열사" },
      { label: "수료", value: 11962, unit: "명", context: "95.8%" },
      { label: "마감 지남", value: 0, unit: "명", context: "없음" },
    ],
  },
};
