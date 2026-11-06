import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, userEvent, within } from "storybook/test";

import { Button } from "./button";
import { Field, Input, Select, Textarea } from "./field";
import { Notice } from "./notice";

const meta = {
  title: "Components/Field",
  component: Field,
  parameters: { layout: "padded" },
  decorators: [(Story) => <div className="max-w-sm">{Story()}</div>],
} satisfies Meta<typeof Field>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Untouched: Story = {
  args: {
    label: "회사 코드",
    hint: "영문 소문자, 숫자, 하이픈 3~30자. 나중에 바꿀 수 없습니다.",
    children: <Input placeholder="acme" />,
  },
};

/** Error replaces the hint, gets an icon, and is wired with aria-invalid + aria-describedby. */
export const Invalid: Story = {
  args: {
    label: "회사 코드",
    hint: "영문 소문자, 숫자, 하이픈 3~30자.",
    error: "대문자는 쓸 수 없습니다. 예: acme-korea",
    children: <Input defaultValue="ACME!" />,
  },
  play: async ({ canvasElement }) => {
    const input = within(canvasElement).getByLabelText("회사 코드");
    await expect(input).toHaveAttribute("aria-invalid", "true");
    await expect(input).toHaveAccessibleDescription("대문자는 쓸 수 없습니다. 예: acme-korea");
  },
};

export const Optional: Story = {
  args: { label: "부서", optional: true, children: <Input placeholder="예: 개발팀" /> },
};

export const Disabled: Story = {
  args: { label: "이메일", hint: "로그인한 계정의 이메일입니다.", children: <Input defaultValue="hana.kim@acme.test" disabled /> },
};

export const Multiline: Story = {
  args: { label: "코스 설명", hint: "학습자 목록에 두 줄까지 보입니다.", children: <Textarea /> },
};

/** Form-level error: summary at the top, fields keep their values (craft/form-validation). */
export const FormWithServerError: Story = {
  args: { label: "회사명", children: <Input /> },
  render: () => (
    <form className="grid gap-4" noValidate onSubmit={(e) => e.preventDefault()}>
      <Notice tone="danger" title="회사를 등록하지 못했습니다">
        이미 사용 중인 회사 코드입니다. 다른 코드를 입력해 주세요.
      </Notice>
      <Field label="회사명">
        <Input defaultValue="에이크미" />
      </Field>
      <Field label="회사 코드" error="이미 사용 중인 회사 코드입니다.">
        <Input defaultValue="acme" />
      </Field>
      <Field label="회사 이메일 도메인" hint="이 도메인의 직원은 회사 로그인으로 안내됩니다.">
        <Input defaultValue="acme.test" />
      </Field>
      <Button type="submit" className="justify-self-start">
        회사 등록
      </Button>
    </form>
  ),
};

/** Native select styled like Input; Field wires the label, hint and error the same way. */
export const SelectField: Story = {
  args: {
    label: "역할",
    hint: "나중에 멤버 표에서 바꿀 수 있습니다.",
    children: (
      <Select defaultValue="LEARNER">
        <option value="ORG_ADMIN">조직 관리자</option>
        <option value="INSTRUCTOR">강사</option>
        <option value="LEARNER">학습자</option>
      </Select>
    ),
  },
  play: async ({ canvasElement }) => {
    const select = within(canvasElement).getByRole("combobox", { name: "역할" });
    await expect(select).toHaveAccessibleDescription("나중에 멤버 표에서 바꿀 수 있습니다.");
    await userEvent.selectOptions(select, "INSTRUCTOR");
    await expect(select).toHaveValue("INSTRUCTOR");
  },
};

export const SelectInvalid: Story = {
  args: {
    label: "부서",
    error: "부서를 고르세요. 목록에 없으면 부서 화면에서 먼저 만드세요.",
    children: (
      <Select defaultValue="">
        <option value="">고르세요</option>
        <option value="d-dev">개발팀</option>
      </Select>
    ),
  },
};

export const SelectDisabled: Story = {
  args: {
    label: "역할",
    hint: "본인 계정의 역할은 바꿀 수 없습니다.",
    children: (
      <Select defaultValue="ORG_ADMIN" disabled>
        <option value="ORG_ADMIN">조직 관리자</option>
      </Select>
    ),
  },
};

/** 32px variant for table rows and toolbars. Long option text truncates inside the control. */
export const SelectSmall: Story = {
  args: { label: "", children: <span /> },
  render: () => (
    <div className="grid w-40 gap-2">
      <Select size="sm" aria-label="역할" defaultValue="INSTRUCTOR">
        <option value="ORG_ADMIN">조직 관리자</option>
        <option value="INSTRUCTOR">강사</option>
      </Select>
      <Select size="sm" aria-label="부서" defaultValue="long">
        <option value="long">글로벌 전략기획본부 디지털트랜스포메이션 추진단</option>
      </Select>
    </div>
  ),
};
