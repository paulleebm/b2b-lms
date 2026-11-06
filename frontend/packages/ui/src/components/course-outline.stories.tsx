import * as React from "react";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent, waitFor, within } from "storybook/test";

import { courseBlocked, courseEdge, courseReady } from "../screens/fixtures";
import { CourseOutline, type CourseOutlineProps, type MoveDirection, type OutlineSection } from "./course-outline";
import { Panel, PanelBody, PanelHeader } from "./panel";

function move<T extends { id: string }>(list: T[], id: string, direction: MoveDirection): T[] {
  const i = list.findIndex((x) => x.id === id);
  const j = direction === "up" ? i - 1 : i + 1;
  if (i < 0 || j < 0 || j >= list.length) return list;
  const copy = [...list];
  [copy[i], copy[j]] = [copy[j]!, copy[i]!];
  return copy;
}

let seq = 0;

/**
 * Stateful wrapper: applies each change locally (so focus-follows-move can be seen) and still
 * calls the story's spy callbacks, which the play tests assert on.
 */
function OutlineDemo(props: CourseOutlineProps) {
  const [sections, setSections] = React.useState<OutlineSection[]>(props.sections);
  const [selected, setSelected] = React.useState(props.selectedLessonId);
  React.useEffect(() => setSections(props.sections), [props.sections]);
  const mapLessons = (f: (l: OutlineSection["lessons"]) => OutlineSection["lessons"]) =>
    setSections((prev) => prev.map((s) => ({ ...s, lessons: f(s.lessons) })));
  return (
    <CourseOutline
      {...props}
      sections={sections}
      selectedLessonId={selected}
      onSelectLesson={(id) => {
        setSelected(id);
        props.onSelectLesson?.(id);
      }}
      onAddSection={() => {
        setSections((prev) => [...prev, { id: `ns${++seq}`, title: "새 섹션", lessons: [] }]);
        props.onAddSection?.();
      }}
      onRenameSection={(id, title) => {
        setSections((prev) => prev.map((s) => (s.id === id ? { ...s, title } : s)));
        props.onRenameSection?.(id, title);
      }}
      onDeleteSection={(id) => {
        setSections((prev) => prev.filter((s) => s.id !== id));
        props.onDeleteSection?.(id);
      }}
      onMoveSection={(id, d) => {
        setSections((prev) => move(prev, id, d));
        props.onMoveSection?.(id, d);
      }}
      onAddLesson={(sectionId, type) => {
        setSections((prev) =>
          prev.map((s) =>
            s.id === sectionId ? { ...s, lessons: [...s.lessons, { id: `nl${++seq}`, title: "새 레슨", type, status: type === "TEXT" ? undefined : "missing-file" }] } : s,
          ),
        );
        props.onAddLesson?.(sectionId, type);
      }}
      onRenameLesson={(id, title) => {
        mapLessons((ls) => ls.map((l) => (l.id === id ? { ...l, title } : l)));
        props.onRenameLesson?.(id, title);
      }}
      onDeleteLesson={(id) => {
        mapLessons((ls) => ls.filter((l) => l.id !== id));
        props.onDeleteLesson?.(id);
      }}
      onMoveLesson={(id, d) => {
        mapLessons((ls) => move(ls, id, d));
        props.onMoveLesson?.(id, d);
      }}
    />
  );
}

const meta = {
  title: "Components/CourseOutline",
  component: CourseOutline,
  args: {
    sections: courseReady.sections,
    selectedLessonId: "l1",
    onAddSection: fn(),
    onRenameSection: fn(),
    onDeleteSection: fn(),
    onMoveSection: fn(),
    onAddLesson: fn(),
    onRenameLesson: fn(),
    onDeleteLesson: fn(),
    onMoveLesson: fn(),
    onSelectLesson: fn(),
  },
  render: (args) => <OutlineDemo {...args} />,
  decorators: [
    (Story) => (
      <Panel className="max-w-md">
        <PanelHeader title="커리큘럼" />
        <PanelBody>{Story()}</PanelBody>
      </Panel>
    ),
  ],
} satisfies Meta<typeof CourseOutline>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Up/down buttons reorder; focus stays on the moved lesson's button so it can be pressed again. */
export const Populated: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    const down = canvas.getByRole("button", { name: "왜 정보보안인가 아래로 이동" });
    await userEvent.click(down);
    await expect(args.onMoveLesson).toHaveBeenCalledWith("l1", "down");
    const list = canvas.getByRole("list", { name: "보안의 기본 레슨" });
    await expect(within(list).getAllByRole("listitem")[1]).toHaveTextContent("왜 정보보안인가");
    await waitFor(() => expect(canvas.getByRole("button", { name: "왜 정보보안인가 아래로 이동" })).toHaveFocus());
    // The first lesson cannot move up, the last cannot move down.
    await expect(canvas.getByRole("button", { name: "회사 보안 정책 요약 위로 이동" })).toBeDisabled();
  },
};

/** Inline rename: Enter saves, Escape cancels; focus returns to the rename button. */
export const InlineRename: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole("button", { name: "비밀번호 관리 수칙 이름 바꾸기" }));
    const input = canvas.getByRole("textbox", { name: "레슨 제목" });
    await expect(input).toHaveFocus();
    await userEvent.clear(input);
    await userEvent.type(input, "비밀번호와 2단계 인증{Enter}");
    await expect(args.onRenameLesson).toHaveBeenCalledWith("l3", "비밀번호와 2단계 인증");
    await waitFor(() => expect(canvas.getByRole("button", { name: "비밀번호와 2단계 인증 이름 바꾸기" })).toHaveFocus());

    // Escape leaves the title as it was and calls nothing.
    await userEvent.click(canvas.getByRole("button", { name: "사고 신고 절차 이름 바꾸기" }));
    await userEvent.type(canvas.getByRole("textbox", { name: "레슨 제목" }), " 수정{Escape}");
    await expect(args.onRenameLesson).toHaveBeenCalledTimes(1);
    await expect(canvas.getByRole("button", { name: /사고 신고 절차$/ })).toBeVisible();
  },
};

/** An empty title is an error on the field, not a silent revert. */
export const RenameEmptyTitle: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole("button", { name: "보안의 기본 섹션 이름 바꾸기" }));
    const input = canvas.getByRole("textbox", { name: "섹션 제목" });
    await userEvent.clear(input);
    await userEvent.keyboard("{Enter}");
    await expect(input).toHaveAttribute("aria-invalid", "true");
    await expect(input).toHaveAccessibleDescription("제목을 입력하세요.");
    await expect(args.onRenameSection).not.toHaveBeenCalled();
  },
};

/** Delete asks on the same row first. Focus lands on 취소, the safe choice. */
export const DeleteConfirm: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole("button", { name: "회사 보안 정책 요약 삭제" }));
    await expect(args.onDeleteLesson).not.toHaveBeenCalled();
    const group = canvas.getByRole("group", { name: /이 레슨을 삭제할까요/ });
    await expect(within(group).getByRole("button", { name: "취소" })).toHaveFocus();
    await userEvent.click(within(group).getByRole("button", { name: "회사 보안 정책 요약 삭제" }));
    await expect(args.onDeleteLesson).toHaveBeenCalledWith("l2");
    await expect(canvas.queryByText("회사 보안 정책 요약")).toBeNull();
  },
};

/** Status badges: 변환 중 (processing), 파일 없음 (missing file). Ready lessons stay quiet. */
export const Statuses: Story = {
  args: { sections: courseBlocked.sections, selectedLessonId: "l4" },
};

/** Learner/preview mode: no edit controls, selection still works. */
export const ReadOnly: Story = {
  args: { readOnly: true },
};

export const Loading: Story = {
  args: { loading: true, sections: [] },
};

/** First use: what a section is, then the one next action. */
export const Empty: Story = {
  args: { sections: [] },
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole("button", { name: "첫 섹션 추가" }));
    await expect(args.onAddSection).toHaveBeenCalledOnce();
    // The new section opens straight into rename.
    await waitFor(() => expect(canvas.getByRole("textbox", { name: "섹션 제목" })).toHaveFocus());
  },
};

/** 12 lessons, 50-character titles, an empty section, a failed transcode. */
export const Edge: Story = {
  args: { sections: courseEdge.sections, selectedLessonId: "el1" },
};
