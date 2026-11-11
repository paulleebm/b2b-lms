import type { Meta, StoryObj } from "@storybook/react-vite";
import { PlayCircle } from "lucide-react";
import { expect, within } from "storybook/test";

import { Button } from "./button";
import { Panel } from "./panel";
import { StepList, type Step } from "./step-list";

// Synthetic course (the demo seed's 정보보안 기초): step 2 of 4 in progress.
const steps: Step[] = [
  { id: "l1", title: "피싱 메일 알아보기", kind: "VIDEO", state: "done", href: "#l1" },
  { id: "l2", title: "안전한 비밀번호", kind: "TEXT", state: "todo", href: "#l2" },
  { id: "l3", title: "사내 자료 반출 규칙", kind: "TEXT", state: "todo", href: "#l3" },
  { id: "quiz", title: "최종 퀴즈", kind: "QUIZ", state: "locked" },
];

const meta = {
  title: "Components/StepList",
  component: StepList,
  args: { steps, currentId: "l2" },
  decorators: [(Story) => <Panel className="max-w-xl overflow-hidden">{Story()}</Panel>],
} satisfies Meta<typeof StepList>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Beside a lesson: the current step is highlighted and is not a link. */
export const BesideLesson: Story = {
  play: async ({ canvasElement }) => {
    const list = within(canvasElement);
    const current = list.getByText("안전한 비밀번호").closest("li");
    await expect(current).toHaveAttribute("aria-current", "step");
    await expect(list.queryByRole("link", { name: /안전한 비밀번호/ })).toBeNull();
    await expect(list.getByRole("link", { name: /피싱 메일 알아보기/ })).toBeVisible();
    await expect(list.queryByRole("link", { name: /최종 퀴즈/ })).toBeNull();
  },
};

/** Course page: the current row holds the next action. */
export const WithAction: Story = {
  args: {
    action: (
      <Button>
        <PlayCircle aria-hidden="true" />
        이어서 학습하기
      </Button>
    ),
  },
};

export const VideoHalfWatched: Story = {
  args: {
    currentId: "l1",
    steps: [{ ...steps[0]!, state: "todo", percent: 45 }, ...steps.slice(1)],
  },
};

export const QuizIsNext: Story = {
  args: {
    currentId: "quiz",
    steps: steps.map((s) => (s.kind === "QUIZ" ? { ...s, state: "todo", href: "#quiz" } : { ...s, state: "done" })),
    action: <Button>퀴즈 보기</Button>,
  },
};

export const AllDone: Story = {
  args: { currentId: undefined, steps: steps.map((s) => ({ ...s, state: "done", href: `#${s.id}` })) },
};

/** Long titles truncate on one line; many steps stay a plain list. */
export const Edges: Story = {
  args: {
    currentId: "s7",
    steps: Array.from({ length: 12 }, (_, i) => ({
      id: `s${i + 1}`,
      title: i === 2 ? "산업안전보건법에 따른 사업주의 안전보건 교육 의무와 근로자가 알아야 할 권리 전반" : `레슨 ${i + 1}`,
      kind: (["VIDEO", "TEXT", "DOCUMENT"] as const)[i % 3]!,
      state: i < 6 ? ("done" as const) : ("todo" as const),
      href: `#s${i + 1}`,
    })),
  },
};
