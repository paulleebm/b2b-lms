import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent, waitFor, within } from "storybook/test";

import { AdminMembersScreen } from "./admin-members";
import { edgeDepartments, edgeMembers, selfMember } from "./fixtures";

/** Admin "멤버" with SYNTHETIC data (labelled on screen). Five states per docs/design/REVIEW.md. */
const meta = {
  title: "Screens/Admin Members",
  component: AdminMembersScreen,
  parameters: { layout: "fullscreen" },
  args: {
    onInvite: fn(),
    onRoleChange: fn(),
    onDeactivate: fn(),
    onReactivate: fn(),
    onRevokeInvite: fn(),
  },
} satisfies Meta<typeof AdminMembersScreen>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Role change fires the callback; the signed-in admin's own row is locked, with the reason shown. */
export const Populated: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await expect(canvas.getByRole("heading", { level: 1, name: "멤버" })).toBeVisible();

    const role = canvas.getByRole("combobox", { name: "박서준 역할" });
    await userEvent.selectOptions(role, "INSTRUCTOR");
    await expect(args.onRoleChange).toHaveBeenCalledWith("m3", "INSTRUCTOR");
    await expect(canvas.getByText("박서준님의 역할을 강사(으)로 바꿨습니다.")).toBeInTheDocument();

    const selfRole = canvas.getByRole("combobox", { name: "김하나 역할" });
    await expect(selfRole).toBeDisabled();
    await expect(selfRole).toHaveAccessibleDescription(/본인 계정\(김하나\)은 여기에서 비활성화하거나/);
  },
};

/** Submitting with nothing typed shows a field error and moves focus there; the button stays enabled. */
export const InviteValidation: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    const submit = canvas.getByRole("button", { name: "초대 보내기" });
    await userEvent.click(submit);
    const emails = canvas.getByRole("textbox", { name: "이메일" });
    await expect(emails).toHaveAttribute("aria-invalid", "true");
    await expect(emails).toHaveAccessibleDescription("초대할 이메일을 한 개 이상 입력하세요.");
    await expect(emails).toHaveFocus();
    await expect(args.onInvite).not.toHaveBeenCalled();

    // Typing re-validates while invalid; a good list clears the error and sends.
    await userEvent.type(emails, "new.hire@acme.test, second@acme.test");
    await expect(emails).not.toHaveAttribute("aria-invalid");
    await userEvent.click(submit);
    await expect(args.onInvite).toHaveBeenCalledWith({
      emails: ["new.hire@acme.test", "second@acme.test"],
      role: "LEARNER",
      departmentId: undefined,
    });
    await waitFor(() => expect(canvas.getByText("2명에게 초대 메일을 보냈습니다.")).toBeVisible(), { timeout: 2000 });
  },
};

/** Deactivation asks on the row first; Danger only appears at the confirming step. */
export const DeactivateConfirm: Story = {
  play: async ({ canvasElement, args }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByRole("button", { name: "이도윤 비활성화" }));
    await expect(args.onDeactivate).not.toHaveBeenCalled();
    await userEvent.click(canvas.getByRole("button", { name: "이도윤 비활성화 확정" }));
    await expect(args.onDeactivate).toHaveBeenCalledWith("m2");
  },
};

export const Loading: Story = { args: { state: "loading" } };

/** First use: nobody but the admin. The table still shows them; the empty state points to the invite box. */
export const Empty: Story = { args: { members: [selfMember] } };

export const Error: Story = { args: { state: "error" } };

/** 40 members, a 40-character name, a 70-character email, a long department, missing departments. */
export const Edge: Story = { args: { members: edgeMembers, departments: edgeDepartments } };

export const Mobile: Story = {
  globals: { viewport: { value: "mobile2", isRotated: false } },
};
