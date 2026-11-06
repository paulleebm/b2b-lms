import type { Meta, StoryObj } from "@storybook/react-vite";

import { Badge, StatusBadge } from "./status-badge";

const meta = {
  title: "Components/StatusBadge",
  component: StatusBadge,
  args: { status: { kind: "in-progress", percent: 40 } },
} satisfies Meta<typeof StatusBadge>;

export default meta;
type Story = StoryObj<typeof meta>;

/** The enrollment vocabulary used on every screen (DESIGN.md §2). Icon + text; colour is secondary. */
export const Vocabulary: Story = {
  render: () => (
    <div className="flex flex-wrap items-center gap-2">
      <StatusBadge status={{ kind: "not-started" }} />
      <StatusBadge status={{ kind: "in-progress", percent: 40 }} />
      <StatusBadge status={{ kind: "due-soon", daysLeft: 2 }} />
      <StatusBadge status={{ kind: "overdue" }} />
      <StatusBadge status={{ kind: "completed", completedAt: "2026. 11. 6." }} />
    </div>
  ),
};

export const Generic: Story = {
  render: () => (
    <div className="flex flex-wrap gap-2">
      <Badge>초안</Badge>
      <Badge tone="info">변환 중</Badge>
      <Badge tone="success">게시됨</Badge>
      <Badge tone="warning">변환 지연</Badge>
      <Badge tone="danger">변환 실패</Badge>
    </div>
  ),
};
