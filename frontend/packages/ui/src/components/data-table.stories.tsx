import * as React from "react";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, userEvent, within } from "storybook/test";

import { edgeLearners, formatDueShort, pendingLearners, type LearnerRow } from "../screens/fixtures";
import { DataTable, type Column, type SortState } from "./data-table";
import { StatusBadge } from "./status-badge";

const columns: Column<LearnerRow>[] = [
  { key: "name", header: "이름", sortable: true, width: "w-[24%]", cell: (r) => <span title={r.name}>{r.name}</span> },
  { key: "department", header: "부서", secondary: true, width: "w-[22%]", cell: (r) => <span title={r.department}>{r.department}</span> },
  { key: "course", header: "교육", width: "w-[30%]", cell: (r) => <span title={r.course}>{r.course}</span> },
  { key: "status", header: "상태", width: "w-[108px]", cell: (r) => <StatusBadge status={r.status} /> },
  { key: "dueAt", header: "마감", sortable: true, align: "right", secondary: true, width: "w-[80px]", cell: (r) => formatDueShort(r.dueAt) },
];

function SortableTable(props: Partial<React.ComponentProps<typeof DataTable<LearnerRow>>>) {
  const [sort, setSort] = React.useState<SortState>(null);
  const rows = React.useMemo(() => {
    const base = props.rows ?? pendingLearners;
    if (!sort) return base;
    const dir = sort.direction === "asc" ? 1 : -1;
    return [...base].sort((a, b) =>
      sort.key === "dueAt" ? a.dueAt.localeCompare(b.dueAt) * dir : a.name.localeCompare(b.name, "ko") * dir,
    );
  }, [props.rows, sort]);
  return (
    <DataTable<LearnerRow>
      caption="처리가 필요한 직원"
      columns={columns}
      rowKey={(r) => r.id}
      sort={sort}
      onSortChange={setSort}
      {...props}
      rows={rows}
    />
  );
}

const meta = {
  title: "Components/DataTable",
  component: SortableTable,
  parameters: { layout: "padded" },
} satisfies Meta<typeof SortableTable>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Populated + sorting. Header buttons cycle asc → desc → off and expose aria-sort. */
export const Populated: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const header = canvas.getByRole("columnheader", { name: /이름/ });
    await expect(header).toHaveAttribute("aria-sort", "none");
    await userEvent.click(within(header).getByRole("button"));
    await expect(header).toHaveAttribute("aria-sort", "ascending");
    await userEvent.click(within(header).getByRole("button"));
    await expect(header).toHaveAttribute("aria-sort", "descending");
  },
};

export const Loading: Story = { args: { loading: true } };

export const Empty: Story = {
  args: {
    rows: [],
    empty: { title: "처리할 직원이 없습니다", description: "배정된 교육을 모두 마감 안에 수료했습니다." },
  },
};

export const Error: Story = {
  args: {
    error: {
      title: "목록을 불러오지 못했습니다",
      description: "서버와 연결이 잠시 끊겼습니다. 다시 시도해 주세요.",
      traceId: "4bf92f3577b34da6a3ce929d0e0e4736",
      onRetry: () => undefined,
    },
  },
};

/** 40 rows, a 25-character name, a long department and course: cells truncate with a title. */
export const Edge: Story = { args: { rows: edgeLearners } };
