import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, userEvent, within } from "storybook/test";

import { AccountArea, WorkspaceSwitcher, adminNav } from "../screens/shell-presets";
import { AppShell, PageHeader } from "./app-shell";

const meta = {
  title: "Components/AppShell",
  component: AppShell,
  parameters: { layout: "fullscreen" },
  args: {
    workspace: <WorkspaceSwitcher name="에이크미" />,
    account: <AccountArea name="김하나" role="조직 관리자" />,
    sections: adminNav,
    children: <PageHeader title="멤버" description="회사에 소속된 사람과 역할입니다." />,
  },
} satisfies Meta<typeof AppShell>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Desktop: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await expect(canvas.getByRole("link", { name: /교육 현황/ })).toHaveAttribute("aria-current", "page");
    await expect(canvas.getByRole("link", { name: "본문으로 건너뛰기" })).toBeInTheDocument();
  },
};

/** Below 1024px the rail becomes a sheet; Escape closes it. */
export const MobileMenu: Story = {
  globals: { viewport: { value: "mobile2", isRotated: false } },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    // Vitest browser mode runs at a phone-sized viewport, so the menu button is visible here.
    const open = canvas.getByRole("button", { name: "메뉴 열기" });
    await userEvent.click(open);
    const sheet = canvas.getByRole("dialog", { name: "주 메뉴" });
    await expect(sheet).toContainElement(document.activeElement as HTMLElement); // focus moved in
    await userEvent.keyboard("{Escape}");
    await expect(open).toHaveAttribute("aria-expanded", "false");
    await expect(open).toHaveFocus(); // focus returned to the opener
  },
};
