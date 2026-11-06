import * as React from "react";
import {
  Award,
  BookOpen,
  Building2,
  ChevronsUpDown,
  ClipboardList,
  LayoutDashboard,
  LogOut,
  ScrollText,
  Users,
} from "lucide-react";

import type { NavSection } from "../components/app-shell";
import { Button } from "../components/button";

/** Navigation per role (DESIGN.md §4 Navigation). */
export const adminNav: NavSection[] = [
  {
    items: [
      { href: "/app/admin", label: "교육 현황", icon: LayoutDashboard, current: true },
      { href: "/app/admin/assignments", label: "교육 배정", icon: ClipboardList },
    ],
  },
  {
    label: "조직",
    items: [
      { href: "/app/admin/members", label: "멤버", icon: Users },
      { href: "/app/admin/departments", label: "부서", icon: Building2 },
    ],
  },
  { label: "기록", items: [{ href: "/app/admin/audit", label: "감사 로그", icon: ScrollText }] },
];

export const learnerNav: NavSection[] = [
  {
    items: [
      { href: "/app", label: "내 교육", icon: BookOpen, current: true },
      { href: "/app/certificates", label: "수료증", icon: Award },
    ],
  },
];

export function WorkspaceSwitcher({ name }: { name: string }) {
  return (
    <button
      type="button"
      className="inline-flex h-9 max-w-full items-center gap-2 rounded-control px-2 text-body-sm font-semibold text-ink hover:bg-surface-sunken"
      aria-label={`현재 회사 ${name}, 회사 전환`}
    >
      <span className="truncate">{name}</span>
      <ChevronsUpDown className="size-3.5 shrink-0 stroke-[1.75] text-muted" aria-hidden="true" />
    </button>
  );
}

export function AccountArea({ name, role }: { name: string; role: string }) {
  return (
    <>
      <span className="hidden text-right md:grid">
        <span className="text-body-sm font-medium text-ink">{name}</span>
        <span className="text-caption text-muted">{role}</span>
      </span>
      <Button variant="ghost" size="icon-sm" aria-label="로그아웃">
        <LogOut aria-hidden="true" />
      </Button>
    </>
  );
}
